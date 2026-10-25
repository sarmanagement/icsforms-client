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
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<BufferedImage>> eldest) {
			return size() > MAX_CACHE_ENTRIES;
		}
	};
	private final PdfFormRenderer renderer;
	private final JPanel pages = new JPanel();
	private final JLabel state = new JLabel("Preview not requested.", SwingConstants.CENTER);
	private SwingWorker<List<BufferedImage>, Void> worker;
	private long requestGeneration;

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
	 * Marks a displayed preview older than the latest structured reference.
	 */
	public void markOutOfDate() {
		if (pages.getComponentCount() > 0) {
			state.setText("Structured information changed — displayed preview is older.");
		}
	}

	/**
	 * Starts or reuses an asynchronous preview for a detached incident snapshot.
	 *
	 * @param source live incident from which a detached snapshot is taken.
	 * @param contentKey digest of content relevant to this form.
	 */
	public void requestPreview(AppData source, String contentKey) {
		cancel();
		long generation = ++requestGeneration;
		String key = renderer.getFormKey() + ":" + contentKey;
		List<BufferedImage> cached;
		synchronized (CACHE) {
			cached = CACHE.get(key);
		}
		if (cached != null) {
			showImages(cached, "Cached preview — " + cached.size() + " page(s)");
			return;
		}
		AppData snapshot;
		try {
			snapshot = COPIER.readValue(COPIER.writeValueAsBytes(source), AppData.class);
		} catch (IOException exception) {
			state.setText("Unable to snapshot preview content: " + exception.getMessage());
			return;
		}
		state.setText("Rendering detached snapshot…");
		worker = new SwingWorker<>() {
			@Override
			protected List<BufferedImage> doInBackground() throws Exception {
				return render(snapshot);
			}

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
					showImages(images, images.size() + " page(s) — detached preview");
				} catch (CancellationException exception) {
					state.setText("Preview cancelled; structured reference remains available.");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					state.setText("Preview interrupted; structured reference remains available.");
				} catch (ExecutionException exception) {
					Throwable cause = exception.getCause();
					state.setText("Preview unavailable: " + (cause == null ? exception.getMessage() : cause.getMessage()));
				}
			}
		};
		worker.execute();
	}

	/** Cancels pending work and invalidates any delayed completion. */
	public void cancel() {
		requestGeneration++;
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
	 * Renders a detached snapshot using project-local scratch storage.
	 *
	 * @param snapshot detached incident snapshot.
	 * @return rendered pages.
	 * @throws IOException when PDF generation or loading fails.
	 */
	private List<BufferedImage> render(AppData snapshot) throws IOException {
		Path directory = Path.of("target", "role-panel-preview");
		Files.createDirectories(directory);
		Path pdf = Files.createTempFile(directory, "preview-", ".pdf");
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
