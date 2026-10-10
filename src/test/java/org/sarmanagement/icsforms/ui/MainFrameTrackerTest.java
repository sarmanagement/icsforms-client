/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Display-backed smoke coverage for independent tracker tab visibility. */
class MainFrameTrackerTest {
	@Test
	void trackerSurvivesSarGenericDisableAndPersistence() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
		Path path = Path.of("target/main-frame-tracker-store");
		AppData data = new AppData();
		OpenActionItem item = new OpenActionItem();
		item.setNumber(1);
		item.setItem("Preserved action");
		data.setOpenActionItems(List.of(item));
		data.setOpenActionTrackerEnabled(true);
		try (FileIncidentStore store = new FileIncidentStore(path)) {
			IncidentId id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id,
						new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
								new SarTaskAssignmentPdfRenderer()),
						new IncidentValidator(), Path.of("target"));
				try {
					frame.setVisible(true);
					var tabField = MainFrame.class.getDeclaredField("tabs");
					tabField.setAccessible(true);
					JTabbedPane tabs = (JTabbedPane) tabField.get(frame);
					var refresh = MainFrame.class.getDeclaredMethod("refreshFromModel");
					refresh.setAccessible(true);
					assertTrue(tabs.indexOfTab("Open Action Tracker") >= 0);
					assertTrue(tabs.indexOfTab("Clue Log") >= 0);
					data.setIncidentMode(IncidentMode.GENERIC);
					refresh.invoke(frame);
					assertTrue(tabs.indexOfTab("Open Action Tracker") >= 0);
					assertEquals(-1, tabs.indexOfTab("Clue Log"));
					data.setOpenActionTrackerEnabled(false);
					refresh.invoke(frame);
					assertEquals(-1, tabs.indexOfTab("Open Action Tracker"));
					assertEquals("Preserved action", data.getOpenActionItems().get(0).getItem());
					data.setIncidentMode(IncidentMode.SAR);
					data.setOpenActionTrackerEnabled(true);
					refresh.invoke(frame);
					assertTrue(tabs.indexOfTab("Open Action Tracker") >= 0);
					assertTrue(tabs.indexOfTab("Clue Log") >= 0);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				} finally {
					frame.dispose();
				}
			});
			data.setOpenActionTrackerEnabled(false);
			store.save(id, data);
			AppData restored = store.load(id);
			assertFalse(restored.isOpenActionTrackerEnabled());
			assertEquals("Preserved action", restored.getOpenActionItems().get(0).getItem());
		} finally {
			if (Files.exists(path)) {
				try (var files = Files.walk(path)) {
					for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
						Files.deleteIfExists(file);
					}
				}
			}
		}
	}
}
