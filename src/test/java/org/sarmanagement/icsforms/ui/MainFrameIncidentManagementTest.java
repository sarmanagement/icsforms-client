/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.DefaultCellEditor;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** File-menu management saves pending edits and refuses rejected table edits. */
class MainFrameIncidentManagementTest {
	@TempDir
	Path directory;

	@Test
	void menuFlushesPendingEditsBeforeOpeningManager() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory.resolve("store"))) {
			AppData data = new AppData();
			var id = store.create(data);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			AtomicBoolean opened = new AtomicBoolean();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
						new IncidentValidator(), directory);
				Timer close = new Timer(150, event -> {
					for (Window window : Window.getWindows()) {
						if (window instanceof IncidentManagerDialog manager && manager.isVisible()) {
							opened.set(true);
							try {
								assertEquals("Pending incident name",
										store.load(id).getIncidentContext().getIncidentName());
								assertTrue(store.load(id).getRevision() > 0);
							} catch (Throwable problem) {
								failure.set(problem);
							} finally {
								manager.dispose();
							}
						}
					}
				});
				close.setRepeats(false);
				try {
					Object panel = field(frame, "incidentContextPanel");
					((JTextField) field(panel, "incidentNameField")).setText("Pending incident name");
					close.start();
					manageItem(frame).doClick();
					assertTrue(opened.get());
					assertEquals("Pending incident name", store.load(id).getIncidentContext().getIncidentName());
				} finally {
					close.stop();
					stop(frame);
				}
			});
			if (failure.get() != null) {
				throw new AssertionError(failure.get());
			}
		}
	}

	@Test
	void rejectedInlineEditAbortsManagerAndKeepsEditorAndStoredDocument() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory.resolve("store"))) {
			AppData data = new AppData();
			data.getForm202().setObjectives(java.util.List.of("Existing objective"));
			var id = store.create(data);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
						new IncidentValidator(), directory);
				Timer dismissError = new Timer(150, event -> {
					for (Window window : Window.getWindows()) {
						if (window instanceof JDialog dialog && dialog.isVisible()) {
							try {
								assertFalse(dialog instanceof IncidentManagerDialog);
							} catch (Throwable problem) {
								failure.set(problem);
							} finally {
								dialog.dispose();
							}
						}
					}
				});
				dismissError.setRepeats(false);
				try {
					frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_202));
					JTable table = (JTable) field(field(frame, "ics202Panel"), "objectivesTable");
					table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JTextField()) {
						/**
						 * Simulates an editor whose input fails validation.
						 *
						 * @return false to retain the edit.
						 */
						@Override
						public boolean stopCellEditing() {
							return false;
						}
					});
					assertTrue(table.editCellAt(0, 1));
					((JTextField) table.getEditorComponent()).setText("Rejected pending objective");
					long revision = store.load(id).getRevision();
					dismissError.start();
					manageItem(frame).doClick();
					assertTrue(table.isEditing());
					assertEquals("Rejected pending objective", ((JTextField) table.getEditorComponent()).getText());
					assertEquals(revision, store.load(id).getRevision());
					assertTrue(java.util.Arrays.stream(Window.getWindows())
							.noneMatch(window -> window instanceof IncidentManagerDialog && window.isVisible()));
					table.getCellEditor().cancelCellEditing();
				} finally {
					dismissError.stop();
					stop(frame);
				}
			});
			if (failure.get() != null) {
				throw new AssertionError(failure.get());
			}
		}
	}

	/**
	 * Finds the File-menu management entry.
	 *
	 * @param frame editor frame.
	 * @return management action.
	 */
	private static JMenuItem manageItem(MainFrame frame) {
		JMenu file = frame.getJMenuBar().getMenu(0);
		for (int index = 0; index < file.getItemCount(); index++) {
			JMenuItem item = file.getItem(index);
			if (item != null && "Manage Incidents…".equals(item.getText())) {
				return item;
			}
		}
		throw new AssertionError("Manage Incidents action missing");
	}

	/**
	 * Reads an existing editor field for integration assertions.
	 *
	 * @param target containing object.
	 * @param name declared field name.
	 * @return field value.
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

	/**
	 * Stops autosave before disposing an integration-test editor.
	 *
	 * @param frame editor to release.
	 */
	private static void stop(MainFrame frame) {
		((Timer) field(field(frame, "controller"), "autosaveTimer")).stop();
		frame.dispose();
	}
}
