/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Read-only, image-rendered preview of a shared form PDF.
 */
public class FormPreviewPanel extends JPanel {
	private final JPanel pages = new JPanel();
	private final JLabel message = new JLabel("Form preview is not available.", SwingConstants.CENTER);
	private final PdfFormRenderer renderer;

	/**
	 * Creates a scrollable preview for a form renderer.
	 *
	 * @param renderer form PDF renderer.
	 */
	public FormPreviewPanel(PdfFormRenderer renderer) {
		super(new BorderLayout());
		this.renderer = renderer;
		pages.setLayout(new javax.swing.BoxLayout(pages, javax.swing.BoxLayout.Y_AXIS));
		JScrollPane scroll = new JScrollPane(pages);
		scroll.setPreferredSize(new Dimension(800, 620));
		add(scroll, BorderLayout.CENTER);
		add(message, BorderLayout.SOUTH);
	}

	/**
	 * Renders the current incident to temporary PDF storage and displays every page.
	 *
	 * @param data incident model.
	 */
	public void refreshFromModel(AppData data) {
		pages.removeAll();
		Path temporary = null;
		try {
			temporary = Files.createTempFile("ics-form-preview-", ".pdf");
			renderer.render(data, temporary);
			try (PDDocument document = Loader.loadPDF(temporary.toFile())) {
				PDFRenderer pdfRenderer = new PDFRenderer(document);
				for (int page = 0; page < document.getNumberOfPages(); page++) {
					Image image = pdfRenderer.renderImageWithDPI(page, 90);
					JLabel pageLabel = new JLabel(new ImageIcon(image));
					pageLabel.setAlignmentX(CENTER_ALIGNMENT);
					pages.add(pageLabel);
				}
			}
			message.setText(pages.getComponentCount() + " page(s) — preview only");
		} catch (IOException exception) {
			message.setText("Unable to render " + renderer.getFormKey() + " preview: " + exception.getMessage());
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
					message.setText("Preview rendered; temporary PDF could not be removed.");
				}
			}
		}
		pages.revalidate();
		pages.repaint();
	}
}
