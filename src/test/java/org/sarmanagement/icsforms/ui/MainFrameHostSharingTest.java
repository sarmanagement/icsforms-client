/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Close-while-hosting confirmation and host-editor conflict wording. */
class MainFrameHostSharingTest {
	@TempDir
	Path directory;

	@Test
	void conflictWhileHostingExplainsRemoteChangesAndKeepsEdits() {
		String hosting = MainFrame.conflictMessage(true, "stale revision");
		assertTrue(hosting.contains("Remote devices"));
		assertTrue(hosting.contains("still in this window"));
		assertTrue(hosting.contains("stale revision"));
		assertTrue(hosting.contains("Export your unsaved changes"));
		String standalone = MainFrame.conflictMessage(false, "stale revision");
		assertFalse(standalone.contains("Remote devices"));
		assertTrue(standalone.contains("stale revision"));
	}

	@Test
	void hostEditorSaveAfterRemoteWriteConflictsWithoutLosingRemoteChanges() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData original = new AppData();
			var id = store.create(original);
			AppData editorCopy = store.load(id);
			AppData other = store.load(id);
			other.getIncidentContext().setIncidentName("Remote change");
			store.save(id, other);
			editorCopy.getIncidentContext().setIncidentName("Operator edit");
			StoreConflictException conflict = org.junit.jupiter.api.Assertions
					.assertThrows(StoreConflictException.class, () -> store.save(id, editorCopy));
			assertEquals("Operator edit", editorCopy.getIncidentContext().getIncidentName());
			assertEquals("Remote change", store.load(id).getIncidentContext().getIncidentName());
			assertTrue(MainFrame.conflictMessage(true, conflict.getMessage()).contains("Nothing was lost"));
		}
	}

	@Test
	void closingWhileHostingAsksFirstCancelKeepsHostingConfirmStopsAndSavesBeforeClosing() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		FileIncidentStore store = new FileIncidentStore(directory);
		var id = store.create(data);
		AtomicReference<MainFrame> holder = new AtomicReference<>();
		AtomicReference<HostSharingDialog> dialogHolder = new AtomicReference<>();
		List<String> prompts = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(data, store, id, new PdfExportService(), new IncidentValidator(),
					directory);
			holder.set(frame);
			try {
				Object panel = field(frame, "incidentContextPanel");
				((JTextField) field(panel, "incidentNameField")).setText("Entered while hosting");
			} catch (RuntimeException failure) {
				throw new AssertionError(failure);
			}
			JMenu sharing = null;
			for (int i = 0; i < frame.getJMenuBar().getMenuCount(); i++) {
				if ("Sharing".equals(frame.getJMenuBar().getMenu(i).getText())) {
					sharing = frame.getJMenuBar().getMenu(i);
				}
			}
			JMenuItem host = sharing.getItem(1);
			host.doClick();
			for (Window window : Window.getWindows()) {
				if (window instanceof HostSharingDialog found && found.isVisible()) {
					dialogHolder.set(found);
				}
			}
			dialogHolder.get().configure("127.0.0.1", 0, 0);
			dialogHolder.get().startSharing();
		});
		MainFrame frame = holder.get();
		HostSharingDialog dialog = dialogHolder.get();
		long startDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
		boolean[] starting = {true};
		while (starting[0] && System.nanoTime() < startDeadline) {
			SwingUtilities.invokeAndWait(() -> starting[0] = dialog.isStarting());
			Thread.sleep(25);
		}
		try {
			assertTrue(dialog.isSharing());

			SwingUtilities.invokeAndWait(() -> {
				frame.setCloseWhileSharingConfirmation(message -> {
					prompts.add(message);
					return false;
				});
				frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
			});
			assertEquals(1, prompts.size());
			assertTrue(prompts.get(0).contains("not discarded"));
			assertTrue(dialog.isSharing());
			assertTrue(frame.isDisplayable());

			SwingUtilities.invokeAndWait(() -> {
				frame.setCloseWhileSharingConfirmation(message -> {
					prompts.add(message);
					return true;
				});
				frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
			});
			long deadline = System.currentTimeMillis() + 20000;
			while (frame.isDisplayable() && System.currentTimeMillis() < deadline) {
				Thread.sleep(100);
			}
			assertFalse(frame.isDisplayable());
			assertFalse(dialog.isSharing());
			try (FileIncidentStore check = new FileIncidentStore(directory)) {
				assertEquals("Entered while hosting", check.load(id).getIncidentContext().getIncidentName());
			}
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (frame.isDisplayable()) {
					((Timer) field(field(frame, "controller"), "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		}
	}

	/**
	 * Reads a private field reflectively.
	 *
	 * @param target object to read
	 * @param name field name
	 * @return field value
	 */
	private static Object field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}
}
