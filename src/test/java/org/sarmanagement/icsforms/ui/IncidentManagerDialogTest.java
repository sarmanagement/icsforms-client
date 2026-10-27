/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentSummary;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Management actions use injected prompts and never require a desktop chooser. */
class IncidentManagerDialogTest {
	@TempDir
	Path directory;

	@Test
	void noSelectionAndCancelledPromptsLeaveSelectionAndStoreUnchanged() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (TestStore store = new TestStore(directory.resolve("store"))) {
			IncidentId id = store.create(named());
			SwingUtilities.invokeAndWait(() -> {
				Prompts prompts = new Prompts();
				IncidentManagerDialog dialog = new IncidentManagerDialog(null, store, null, prompts);
				try {
					assertFalse(button(dialog, "Backup JSON…").isEnabled());
					assertFalse(button(dialog, "Delete…").isEnabled());
					JTable table = component(dialog, JTable.class);
					assertTrue(table.getValueAt(0, 0).toString().contains("Ridge search"));
					assertEquals(id.value(), table.getValueAt(0, 1));
					assertTrue(table.getValueAt(0, 2).toString().contains("2026-10-27T09:00"));
					assertNotNull(table.getValueAt(0, 3));
					assertEquals(store.list().get(0).path(), table.getValueAt(0, 4));
					assertFalse(table.isCellEditable(0, 0));
					table.setRowSelectionInterval(0, 0);
					button(dialog, "Backup JSON…").doClick();
					button(dialog, "Delete…").doClick();
					assertEquals(0, store.backups);
					assertEquals(0, store.deletions);
					assertEquals(0, table.getSelectedRow());
					assertTrue(prompts.question.contains("Ridge search"));
					assertTrue(prompts.question.contains(id.value()));
					assertTrue(prompts.question.contains("back up first"));
					assertTrue(prompts.question.contains(".bak"));
					assertTrue(prompts.question.contains("not secure erasure"));
					assertTrue(prompts.messages.isEmpty());
					assertEquals(1, store.list().size());
				} finally {
					dialog.dispose();
				}
			});
		}
	}

	@Test
	void backupDeleteErrorsAndSuccessPreserveSelectionUntilDeletion() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (TestStore store = new TestStore(directory.resolve("store"))) {
			IncidentId id = store.create(named());
			Path target = directory.resolve("backup.json");
			SwingUtilities.invokeAndWait(() -> {
				Prompts prompts = new Prompts();
				prompts.target = target;
				prompts.approve = true;
				IncidentManagerDialog dialog = new IncidentManagerDialog(null, store, null, prompts);
				try {
					JTable table = component(dialog, JTable.class);
					table.setRowSelectionInterval(0, 0);
					button(dialog, "Backup JSON…").doClick();
					assertTrue(Files.exists(target));
					assertFalse(prompts.lastError);
					button(dialog, "Backup JSON…").doClick();
					assertTrue(prompts.lastError);
					assertTrue(prompts.messages.get(prompts.messages.size() - 1).contains("choose a new filename"));
					assertEquals(0, table.getSelectedRow());
					store.failDelete = true;
					button(dialog, "Delete…").doClick();
					assertTrue(prompts.lastError);
					assertEquals(0, table.getSelectedRow());
					assertEquals(id.value(), table.getValueAt(0, 1));
					store.failDelete = false;
					button(dialog, "Delete…").doClick();
					assertEquals(0, table.getRowCount());
					assertTrue(store.list().isEmpty());
					assertFalse(button(dialog, "Backup JSON…").isEnabled());
					assertFalse(button(dialog, "Delete…").isEnabled());
					assertTrue(labels(dialog).contains("No saved incidents."));
					assertTrue(Files.exists(target));
				} finally {
					dialog.dispose();
				}
			});
		}
	}

	@Test
	void refreshKeepsSelectionAndRowsOnFailureAndReconcilesExternalChanges() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (TestStore store = new TestStore(directory.resolve("store"))) {
			IncidentId id = store.create(named());
			SwingUtilities.invokeAndWait(() -> {
				Prompts prompts = new Prompts();
				IncidentManagerDialog dialog = new IncidentManagerDialog(null, store, null, prompts);
				try {
					JTable table = component(dialog, JTable.class);
					table.setRowSelectionInterval(0, 0);
					store.failList = true;
					button(dialog, "Refresh").doClick();
					assertTrue(prompts.lastError);
					assertEquals(1, table.getRowCount());
					assertEquals(0, table.getSelectedRow());
					store.failList = false;
					store.create(named());
					button(dialog, "Refresh").doClick();
					assertEquals(2, table.getRowCount());
					assertEquals(id.value(), table.getValueAt(table.getSelectedRow(), 1));
					store.delete(id);
					button(dialog, "Refresh").doClick();
					assertEquals(1, table.getRowCount());
					assertEquals(-1, table.getSelectedRow());
					assertFalse(button(dialog, "Delete…").isEnabled());
				} finally {
					dialog.dispose();
				}
			});
		}
	}

	@Test
	void activeIncidentIsRejectedBeforeConfirmationAndUnsupportedBackupsAreDisabled() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (TestStore store = new TestStore(directory.resolve("store"))) {
			IncidentId id = store.create(named());
			store.backupSupported = false;
			SwingUtilities.invokeAndWait(() -> {
				Prompts prompts = new Prompts();
				prompts.approve = true;
				IncidentManagerDialog dialog = new IncidentManagerDialog(null, store, id, prompts);
				try {
					component(dialog, JTable.class).setRowSelectionInterval(0, 0);
					assertFalse(button(dialog, "Backup JSON…").isEnabled());
					button(dialog, "Delete…").doClick();
					assertNull(prompts.question);
					assertEquals(0, store.deletions, "Active guard must not rely on backend locks");
					assertTrue(prompts.messages.get(0).contains("Switch to another incident"));
					assertEquals(1, store.list().size());
				} finally {
					dialog.dispose();
				}
			});
		}
	}

	@Test
	void pickerManagerRespectsLocalLockAndRefreshesItsSummaryMappingOnClose() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (TestStore store = new TestStore(directory.resolve("store"))) {
			IncidentId active = store.create(named());
			store.lock(active);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				IncidentPickerDialog picker = new IncidentPickerDialog(null, store);
				Timer closeManager = new Timer(150, event -> {
					for (Window window : Window.getWindows()) {
						if (window instanceof IncidentManagerDialog manager && manager.isVisible()) {
							try {
								assertThrows(RuntimeException.class, () -> store.delete(active));
								store.unlock(active);
								store.delete(active);
								store.create(named());
							} catch (Throwable problem) {
								failure.set(problem);
							} finally {
								manager.dispose();
							}
						}
					}
				});
				closeManager.setRepeats(false);
				try {
					component(picker, JList.class).setSelectedIndex(0);
					closeManager.start();
					button(picker, "Manage Incidents…").doClick();
					assertEquals(1, component(picker, JList.class).getModel().getSize());
					assertEquals(-1, component(picker, JList.class).getSelectedIndex());
					assertFalse(button(picker, "Open").isEnabled());
					component(picker, JList.class).setSelectedIndex(0);
					button(picker, "Open").doClick();
					assertEquals(store.list().get(0).incidentId(), picker.getChosenIncidentId());
					assertNotEquals(active, picker.getChosenIncidentId());
				} finally {
					closeManager.stop();
					picker.dispose();
				}
			});
			if (failure.get() != null) {
				throw new AssertionError(failure.get());
			}
		}
	}

	/**
	 * Creates a named incident with an identifiable operational period.
	 *
	 * @return test document.
	 */
	private static AppData named() {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Ridge search");
		data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.of(2026, 10, 27, 9, 0));
		return data;
	}

	/**
	 * Finds the first component of a type in a dialog.
	 *
	 * @param parent container to inspect.
	 * @param type expected type.
	 * @param <T> component type.
	 * @return matching component.
	 */
	private static <T extends Component> T component(Container parent, Class<T> type) {
		return descendants(parent).stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
	}

	/**
	 * Finds a labeled action button.
	 *
	 * @param parent dialog container.
	 * @param text button text.
	 * @return matching button.
	 */
	private static JButton button(Container parent, String text) {
		return descendants(parent).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
				.filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
	}

	/**
	 * Collects label texts for empty-state assertions.
	 *
	 * @param parent dialog container.
	 * @return label texts.
	 */
	private static List<String> labels(Container parent) {
		return descendants(parent).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
				.map(JLabel::getText).toList();
	}

	/**
	 * Traverses nested Swing components.
	 *
	 * @param parent container to traverse.
	 * @return descendants in traversal order.
	 */
	private static List<Component> descendants(Container parent) {
		List<Component> result = new ArrayList<>();
		for (Component child : parent.getComponents()) {
			result.add(child);
			if (child instanceof Container container) {
				result.addAll(descendants(container));
			}
		}
		return result;
	}

	/** Scripted chooser, confirmations and feedback without native windows. */
	private static final class Prompts implements IncidentManagerDialog.Dialogs {
		private Path target;
		private boolean approve;
		private String question;
		private boolean lastError;
		private final List<String> messages = new ArrayList<>();

		/** Creates cancelled prompts by default. */
		private Prompts() {
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseBackup(Window owner, IncidentSummary summary) {
			return target;
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmDelete(Window owner, String question) {
			this.question = question;
			return approve;
		}

		/** {@inheritDoc} */
		@Override
		public void message(Window owner, String message, boolean error) {
			messages.add(message);
			lastError = error;
		}
	}

	/** File store with deterministic backend errors and management call counters. */
	private static final class TestStore extends FileIncidentStore {
		private boolean failDelete;
		private boolean failList;
		private boolean backupSupported = true;
		private int backups;
		private int deletions;

		/**
		 * Opens an isolated test store.
		 *
		 * @param root test store directory.
		 */
		private TestStore(Path root) {
			super(root);
		}

		/** {@inheritDoc} */
		@Override
		public boolean supportsBackup() {
			return backupSupported;
		}

		/** {@inheritDoc} */
		@Override
		public void backupFile(IncidentId id, Path target) {
			backups++;
			super.backupFile(id, target);
		}

		/** {@inheritDoc} */
		@Override
		public void delete(IncidentId id) {
			deletions++;
			if (failDelete) {
				throw new IllegalStateException("Backend deletion failed");
			}
			super.delete(id);
		}

		/** {@inheritDoc} */
		@Override
		public List<IncidentSummary> list() {
			if (failList) {
				throw new IllegalStateException("Backend listing failed");
			}
			return super.list();
		}
	}
}
