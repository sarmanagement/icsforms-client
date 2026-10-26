/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Desktop smoke coverage for opening and painting the imported documentation example. */
class MapleRidgeUiTest {
	@TempDir
	Path directory;

	@Test
	void importedExampleOpensAndPaintsInTheEditor() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			var id = store.importFile(Path.of("docs/examples/maple-ridge-dementia-op1.json")).orElseThrow();
			var data = store.load(id);
			SwingUtilities.invokeAndWait(() -> {
				var previousZone = UiSupport.getDateTimeDisplayZone();
				try {
					MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
							new IncidentValidator(), directory);
					try {
						frame.setVisible(true);
						assertTrue(frame.isShowing());
						BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(),
								BufferedImage.TYPE_INT_RGB);
						var graphics = image.createGraphics();
						try {
							frame.paint(graphics);
						} finally {
							graphics.dispose();
						}
					} finally {
						frame.dispose();
					}
				} finally {
					UiSupport.setDateTimeDisplayZone(previousZone);
				}
			});
		}
	}
}
