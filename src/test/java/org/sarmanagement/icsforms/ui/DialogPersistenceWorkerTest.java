/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore.Operation;
import org.sarmanagement.icsforms.persistence.IncidentSummary;
import org.sarmanagement.icsforms.persistence.IncidentStore;

import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Deterministic gates prove incident dialogs dispatch events while persistence waits. */
class DialogPersistenceWorkerTest {
	@Test
	void constructorsWaitForCompleteListingWithoutBlockingSwingDispatch() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path root = fixtureDirectory();
		try (ControllableIncidentStore store = new ControllableIncidentStore(root)) {
			store.create(named());
			for (boolean picker : new boolean[]{false, true}) {
				for (Operation operation : new Operation[]{Operation.LIST, Operation.DESCRIBE}) {
					AtomicReference<JDialog> created = new AtomicReference<>();
					delayed(store, operation, () -> created.set(picker
							? new IncidentPickerDialog(null, store)
							: new IncidentManagerDialog(null, store, null, new Prompts())),
							() -> assertNull(created.get(), "Constructor must not publish incomplete rows"));
					SwingUtilities.invokeAndWait(() -> {
						assertNotNull(created.get());
						created.get().dispose();
					});
				}
			}
		} finally {
			deleteTree(root);
		}
	}

	@Test
	void managerRefreshBackupDeleteAndImportWaitWithoutPrematureSelectionOrClose() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path root = fixtureDirectory();
		try (ControllableIncidentStore store = new ControllableIncidentStore(root.resolve("store"))) {
			var original = store.create(named());
			Path source = root.resolve("source.json");
			store.exportFile(original, source);
			for (Operation operation : new Operation[]{Operation.LIST, Operation.LOAD, Operation.BACKUP,
					Operation.DELETE, Operation.IMPORT}) {
				Prompts prompts = new Prompts();
				prompts.source = source;
				prompts.target = root.resolve("backup.json");
				AtomicReference<IncidentManagerDialog> created = new AtomicReference<>();
				SwingUtilities.invokeAndWait(() -> {
					var dialog = new IncidentManagerDialog(null, store, null, prompts);
					table(dialog).setRowSelectionInterval(0, 0);
					created.set(dialog);
				});
				IncidentManagerDialog dialog = created.get();
				Timer closeReport = new Timer(20, event -> {
					for (Window window : Window.getWindows()) {
						if (window instanceof JDialog report && report.isVisible()
								&& "Incident import report".equals(report.getTitle())) {
							report.dispose();
						}
					}
				});
				try {
					SwingUtilities.invokeAndWait(closeReport::start);
					String method = switch (operation) {
						case BACKUP -> "backupSelected";
						case DELETE -> "deleteSelected";
						case IMPORT -> "importIncident";
						default -> "refresh";
					};
					delayed(store, operation, () -> invoke(dialog, method), () -> {
						assertTrue(dialog.isDisplayable(), "No premature dialog close");
						assertNull(dialog.getChosenIncidentId(), "No premature import selection");
						assertNull(dialog.getChosenAction());
						assertEquals(1, table(dialog).getRowCount(), "Rows must remain until I/O completes");
						assertEquals(0, table(dialog).getSelectedRow());
						assertEquals(0, prompts.messages);
						if (operation == Operation.BACKUP) {
							assertFalse(Files.exists(prompts.target));
						}
					});
					SwingUtilities.invokeAndWait(() -> {
						if (operation == Operation.DELETE) {
							assertEquals(0, table(dialog).getRowCount());
						} else if (operation == Operation.IMPORT) {
							assertNotNull(dialog.getChosenIncidentId());
							assertEquals(StartupDialog.StartupAction.OPEN_EXISTING, dialog.getChosenAction());
							assertFalse(dialog.isDisplayable());
						} else {
							assertEquals(1, table(dialog).getRowCount());
						}
					});
					if (operation == Operation.DELETE) {
						store.create(named());
					}
					if (operation == Operation.BACKUP) {
						assertTrue(Files.exists(prompts.target));
					}
				} finally {
					SwingUtilities.invokeAndWait(() -> {
						closeReport.stop();
						dialog.dispose();
					});
				}
			}
		} finally {
			deleteTree(root);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void pickerImportWaitsForPersistenceBeforeChoosingOrClosing(boolean valid) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path root = fixtureDirectory();
		try (ControllableIncidentStore store = new ControllableIncidentStore(root.resolve("store"))) {
			var original = store.create(named());
			Path source = root.resolve("source.json");
			store.exportFile(original, source);
			if (!valid) {
				Files.writeString(source, "{}");
			}
			AtomicReference<IncidentPickerDialog> created = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> created.set(new IncidentPickerDialog(null, store)));
			IncidentPickerDialog picker = created.get();
			Timer closeReport = new Timer(20, event -> {
				for (Window window : Window.getWindows()) {
					if (window instanceof JDialog report && report.isVisible()
							&& "Incident import report".equals(report.getTitle())) {
						report.dispose();
					}
				}
			});
			try {
				SwingUtilities.invokeAndWait(closeReport::start);
				delayed(store, Operation.IMPORT, () -> {
					try {
						var method = IncidentPickerDialog.class.getDeclaredMethod("importIncident",
								IncidentStore.class, Path.class);
						method.setAccessible(true);
						method.invoke(picker, store, source);
					} catch (ReflectiveOperationException exception) {
						throw new AssertionError(exception);
					}
				}, () -> {
					assertTrue(picker.isDisplayable());
					assertNull(picker.getChosenIncidentId());
				});
				SwingUtilities.invokeAndWait(() -> {
					if (valid) {
						assertNotNull(picker.getChosenIncidentId());
						assertNotEquals(original, picker.getChosenIncidentId());
						assertFalse(picker.isDisplayable());
					} else {
						assertNull(picker.getChosenIncidentId());
						assertTrue(picker.isDisplayable(), "Validation rejection must keep the picker open");
					}
				});
				assertEquals(valid ? 2 : 1, store.list().size());
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					closeReport.stop();
					picker.dispose();
				});
			}
		} finally {
			deleteTree(root);
		}
	}

	/**
	 * Creates a recognizable incident that passes strict import validation.
	 *
	 * @return named fixture document.
	 */
	private static AppData named() {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName("Worker responsiveness incident");
		return data;
	}

	/**
	 * Runs an action behind a store gate, checking the EDT heartbeat before releasing it.
	 *
	 * @param store controlled store.
	 * @param operation blocked operation.
	 * @param action synchronous dialog workflow.
	 * @param pending assertions executed on Swing while I/O is pending.
	 * @throws Exception if dispatch or gate coordination fails.
	 */
	private static void delayed(ControllableIncidentStore store, Operation operation, Runnable action,
			Runnable pending) throws Exception {
		var gate = store.blockNext(operation);
		CountDownLatch finished = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeLater(() -> {
			try {
				action.run();
			} catch (Throwable exception) {
				failure.set(exception);
			} finally {
				finished.countDown();
			}
		});
		try {
			assertTrue(gate.awaitStarted(5, TimeUnit.SECONDS), "Worker reached persistence gate");
			CountDownLatch heartbeat = new CountDownLatch(1);
			SwingUtilities.invokeLater(() -> {
				try {
					assertEquals(1, finished.getCount(), "Action must wait for persistence completion");
					assertTrue(java.util.Arrays.stream(Window.getWindows()).anyMatch(window ->
							window instanceof JDialog blocker && blocker.isVisible() && blocker.isModal()
									&& "Working with saved incidents".equals(blocker.getTitle())
									&& blocker.getDefaultCloseOperation() == JDialog.DO_NOTHING_ON_CLOSE),
							"Pending persistence must block operator input modally");
					pending.run();
				} catch (Throwable exception) {
					failure.set(exception);
				} finally {
					heartbeat.countDown();
				}
			});
			assertTrue(heartbeat.await(5, TimeUnit.SECONDS), "EDT heartbeat while persistence is blocked");
		} finally {
			gate.release();
			assertTrue(finished.await(5, TimeUnit.SECONDS), "Action finishes after persistence release");
		}
		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}
	}

	/**
	 * Invokes an existing synchronous private management action.
	 *
	 * @param dialog manager.
	 * @param name action method name.
	 */
	private static void invoke(IncidentManagerDialog dialog, String name) {
		try {
			var method = IncidentManagerDialog.class.getDeclaredMethod(name);
			method.setAccessible(true);
			method.invoke(dialog);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Finds the manager table recursively.
	 *
	 * @param parent container to inspect.
	 * @return saved incident table.
	 */
	private static JTable table(Container parent) {
		for (Component child : parent.getComponents()) {
			if (child instanceof JTable result) {
				return result;
			}
			if (child instanceof Container container) {
				JTable result = table(container);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Creates a project-local fixture without writing outside the repository.
	 *
	 * @return fresh store root.
	 * @throws java.io.IOException if creation fails.
	 */
	private static Path fixtureDirectory() throws java.io.IOException {
		return Files.createDirectories(Path.of("target", "dialog-worker-" + UUID.randomUUID()));
	}

	/**
	 * Removes fixture files after store handles close.
	 *
	 * @param root fixture root.
	 * @throws java.io.IOException if cleanup fails.
	 */
	private static void deleteTree(Path root) throws java.io.IOException {
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	/** Non-interactive prompts keep tests focused on persistence completion. */
	private static final class Prompts implements IncidentManagerDialog.Dialogs {
		private Path source;
		private Path target;
		private int messages;

		/** Creates deterministic management prompts. */
		private Prompts() {
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseImport(Window owner) {
			return source;
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseBackup(Window owner, IncidentSummary summary) {
			return target;
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmDelete(Window owner, String question) {
			return true;
		}

		/** {@inheritDoc} */
		@Override
		public void message(Window owner, String message, boolean error) {
			assertFalse(error, message);
			messages++;
		}
	}
}
