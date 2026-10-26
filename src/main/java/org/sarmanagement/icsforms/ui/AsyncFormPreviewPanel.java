/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfFormRenderer;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Asynchronously renders detached incident snapshots with bounded image caching.
 */
public class AsyncFormPreviewPanel extends JPanel {
	private static final ObjectMapper COPIER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	private static final int MAX_CACHE_ENTRIES = 8;
	private static final Map<String, List<BufferedImage>> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
		/** {@inheritDoc} */
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<BufferedImage>> eldest) {
			return size() > MAX_CACHE_ENTRIES;
		}
	};
	private final PdfFormRenderer renderer;
	private final JPanel pages = new JPanel();
	private final JLabel state = new JLabel("PDF not loaded.", SwingConstants.CENTER);
	private SwingWorker<List<BufferedImage>, Void> worker;
	private long requestGeneration;
	private String currentKey;

	/**
	 * Creates a preview surface for one standard form renderer.
	 *
	 * @param renderer standard PDF renderer.
	 */
	public AsyncFormPreviewPanel(PdfFormRenderer renderer) {
		super(new BorderLayout());
		this.renderer = renderer;
		pages.setLayout(new javax.swing.BoxLayout(pages, javax.swing.BoxLayout.Y_AXIS));
		JScrollPane scroll = new JScrollPane(pages);
		scroll.setPreferredSize(new Dimension(620, 480));
		add(scroll, BorderLayout.CENTER);
		add(state, BorderLayout.SOUTH);
		state.getAccessibleContext().setAccessibleName(renderer.getFormKey() + " preview state");
	}

	/**
	 * Clears obsolete output and invalidates pending work after a content change.
	 */
	public void markOutOfDate() {
		cancel();
		showImages(List.of(), "Structured information changed — PDF update pending.");
	}

	/**
	 * Starts or reuses an asynchronous preview for a detached incident snapshot.
	 *
	 * @param source live incident from which a detached snapshot is taken.
	 * @param contentKey digest of content relevant to this form.
	 */
	public void requestPreview(AppData source, String contentKey) {
		AppData snapshot;
		String key;
		try {
			byte[] content = COPIER.writeValueAsBytes(source);
			key = renderer.getFormKey() + ":" + contentKey + ":"
					+ java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
			if (key.equals(currentKey)) {
				return;
			}
			snapshot = COPIER.readValue(content, AppData.class);
		} catch (IOException | NoSuchAlgorithmException exception) {
			cancel();
			showImages(List.of(), "Unable to load PDF content: " + exception.getMessage());
			return;
		}
		cancel();
		currentKey = key;
		long generation = ++requestGeneration;
		List<BufferedImage> cached;
		synchronized (CACHE) {
			cached = CACHE.get(key);
		}
		if (cached != null) {
			showImages(cached, "Current PDF — " + cached.size() + " page(s)");
			return;
		}
		showImages(List.of(), "Rendering current PDF…");
		worker = new SwingWorker<>() {
			/** {@inheritDoc} */
			@Override
			protected List<BufferedImage> doInBackground() throws Exception {
				return render(snapshot);
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				if (isCancelled() || generation != requestGeneration) {
					return;
				}
				try {
					List<BufferedImage> images = get();
					synchronized (CACHE) {
						CACHE.put(key, images);
					}
					showImages(images, "Current PDF — " + images.size() + " page(s)");
				} catch (CancellationException exception) {
					currentKey = null;
					state.setText("PDF loading cancelled; structured reference remains available.");
				} catch (InterruptedException exception) {
					currentKey = null;
					Thread.currentThread().interrupt();
					state.setText("PDF loading interrupted; structured reference remains available.");
				} catch (ExecutionException exception) {
					currentKey = null;
					Throwable cause = exception.getCause();
					state.setText("PDF unavailable: " + (cause == null ? exception.getMessage() : cause.getMessage()));
				}
			}
		};
		worker.execute();
	}

	/** Cancels pending work and invalidates any delayed completion. */
	public void cancel() {
		requestGeneration++;
		currentKey = null;
		if (worker != null && !worker.isDone()) {
			worker.cancel(true);
		}
		worker = null;
	}

	/**
	 * Returns preview state text for accessibility and focused UI tests.
	 *
	 * @return current preview state.
	 */
	String previewStateText() {
		return state.getText();
	}

	/**
	 * Returns displayed preview page count.
	 *
	 * @return displayed page count.
	 */
	int displayedPageCount() {
		return pages.getComponentCount();
	}

	/**
	 * Renders a detached snapshot using operating-system temporary storage.
	 *
	 * @param snapshot detached incident snapshot.
	 * @return rendered pages.
	 * @throws IOException when PDF generation or loading fails.
	 */
	private List<BufferedImage> render(AppData snapshot) throws IOException {
		Path pdf = Files.createTempFile("icsforms-preview-", ".pdf");
		try {
			renderer.render(snapshot, pdf);
			try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
				PDFRenderer pdfRenderer = new PDFRenderer(document);
				List<BufferedImage> images = new ArrayList<>();
				for (int page = 0; page < document.getNumberOfPages(); page++) {
					if (Thread.currentThread().isInterrupted()) {
						throw new CancellationException("Preview cancelled");
					}
					images.add(pdfRenderer.renderImageWithDPI(page, 90));
				}
				return images;
			}
		} finally {
			Files.deleteIfExists(pdf);
		}
	}

	/**
	 * Displays successfully rendered pages, replacing only prior preview output.
	 *
	 * @param images rendered pages.
	 * @param message preview state.
	 */
	private void showImages(List<BufferedImage> images, String message) {
		pages.removeAll();
		for (BufferedImage image : images) {
			JLabel label = new JLabel(new ImageIcon(image));
			label.setAlignmentX(CENTER_ALIGNMENT);
			pages.add(label);
		}
		state.setText(message);
		pages.revalidate();
		pages.repaint();
	}
}
