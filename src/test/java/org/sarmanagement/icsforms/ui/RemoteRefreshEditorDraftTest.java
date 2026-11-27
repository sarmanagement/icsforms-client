/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that a remote refresh never replaces input that an editor holds but has not committed to the model:
 * it defers, keeps the draft and the old token, and applies once the editor input is resolved.
 */
class RemoteRefreshEditorDraftTest {
	@TempDir
	Path directory;

	/**
	 * Runs a scenario with one displayed connected window and a second store that makes remote changes.
	 */
	private interface Scenario {
		/**
		 * Executes the scenario.
		 *
		 * @param frame connected window under test.
		 * @param controller its controller.
		 * @param remote store of another device.
		 * @param host fixture hosting the incident.
		 * @throws Exception when the scenario fails.
		 */
		void run(MainFrame frame, AppController controller, RemoteIncidentStore remote, HostedIncidentFixture host)
				throws Exception;
	}

	@Test
	void uncommittedTextInputDefersRemoteRefreshUntilItMatchesTheModelAgain() throws Exception {
		scenario((frame, controller, remote, host) -> {
			JTextComponent text = onEdt(() -> firstEditableText(frame));
			String original = onEdt(text::getText);
			SwingUtilities.invokeAndWait(() -> text.setText(original + "x"));
			changeRemotely(remote, host);
			assertEquals(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED, controller.refreshFromRemote());
			assertEquals(original + "x", onEdt(text::getText));
			assertEquals(IapPhase.PRE_OP, onEdt(() -> controller.getData().getIapPhase()));
			SwingUtilities.invokeAndWait(() -> text.setText(original));
			assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
			assertEquals(IapPhase.DURING_OP, onEdt(() -> controller.getData().getIapPhase()));
		});
	}

	@Test
	void editedTextFieldLosingFocusIsCommittedByAutosave() throws Exception {
		scenario((frame, controller, remote, host) -> {
			JTextComponent text = onEdt(() -> firstEditableText(frame));
			String original = onEdt(text::getText);
			SwingUtilities.invokeAndWait(() -> text.setText(original + "x"));
			assertTrue(onEdt(frame::hasPendingEditorInput));
			SwingUtilities.invokeAndWait(() -> frame.focusOwnerChanged(text, null));
			long deadline = System.currentTimeMillis() + 10_000;
			while (onEdt(frame::hasPendingEditorInput) && System.currentTimeMillis() < deadline) {
				Thread.sleep(100);
			}
			assertFalse(onEdt(frame::hasPendingEditorInput));
			assertEquals(original + "x", onEdt(text::getText));
		});
	}

	@Test
	void keystrokeInEditedTextFieldIsCommittedByAutosave() throws Exception {
		scenario((frame, controller, remote, host) -> {
			JTextComponent text = onEdt(() -> firstEditableText(frame));
			String original = onEdt(text::getText);
			SwingUtilities.invokeAndWait(() -> text.setText(original + "k"));
			assertTrue(onEdt(frame::hasPendingEditorInput));
			SwingUtilities.invokeAndWait(() -> frame.entryEvent(new java.awt.event.KeyEvent(text,
					java.awt.event.KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
					java.awt.event.KeyEvent.VK_K, 'k')));
			long deadline = System.currentTimeMillis() + 10_000;
			while (onEdt(frame::hasPendingEditorInput) && System.currentTimeMillis() < deadline) {
				Thread.sleep(100);
			}
			assertFalse(onEdt(frame::hasPendingEditorInput));
			assertEquals(original + "k", onEdt(text::getText));
		});
	}

	@Test
	void uncommittedSpinnerTextDefersRemoteRefresh() throws Exception {
		scenario((frame, controller, remote, host) -> {
			JFormattedTextField field = onEdt(() -> descendants(frame, JFormattedTextField.class).stream()
					.filter(JFormattedTextField::isEditable).findFirst().orElse(null));
			assumeTrue(field != null, "no formatted field in this window");
			String original = onEdt(field::getText);
			SwingUtilities.invokeAndWait(() -> field.setText(original + "9"));
			assumeTrue(onEdt(() -> MainFrame.hasUncommittedFormattedText(field)), "field did not report an edit");
			changeRemotely(remote, host);
			assertEquals(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED, controller.refreshFromRemote());
			assertEquals(original + "9", onEdt(field::getText));
			SwingUtilities.invokeAndWait(() -> {
				field.setText(original);
				try {
					field.commitEdit();
				} catch (java.text.ParseException ignored) {
					// the original text parses; nothing to commit otherwise
				}
			});
			assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
		});
	}

	@Test
	void tableCellBeingEditedDefersRemoteRefresh() throws Exception {
		scenario((frame, controller, remote, host) -> {
			JTable table = onEdt(() -> descendants(frame, JTable.class).stream()
					.filter(candidate -> candidate.getModel() instanceof javax.swing.table.DefaultTableModel
							&& candidate.getColumnCount() > 0)
					.filter(candidate -> {
						var model = (javax.swing.table.DefaultTableModel) candidate.getModel();
						if (model.getRowCount() == 0) {
							model.addRow(new Object[model.getColumnCount()]);
						}
						return candidate.isCellEditable(0, 0);
					}).findFirst().orElse(null));
			assumeTrue(table != null, "no editable default-model table in this window");
			boolean editing = onEdt(() -> table.editCellAt(0, 0));
			assumeTrue(editing, "cell did not enter edit mode");
			changeRemotely(remote, host);
			assertEquals(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED, controller.refreshFromRemote());
			assertTrue(onEdt(frame::hasPendingEditorInput));
			SwingUtilities.invokeAndWait(() -> table.getCellEditor().cancelCellEditing());
			assertFalse(onEdt(frame::hasPendingEditorInput));
		});
	}

	@Test
	void openEditorWindowDefersRemoteRefreshUntilItCloses() throws Exception {
		scenario((frame, controller, remote, host) -> {
			AtomicReference<JDialog> dialog = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				JDialog editor = new JDialog(frame, "Editor", false);
				UiSupport.registerIncidentWindow(editor, controller.getData());
				editor.setVisible(true);
				dialog.set(editor);
			});
			changeRemotely(remote, host);
			assertEquals(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED, controller.refreshFromRemote());
			SwingUtilities.invokeAndWait(() -> dialog.get().dispose());
			assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
		});
	}

	@Test
	void keptDraftIsNeverSavedOverTheRemoteChange() throws Exception {
		scenario((frame, controller, remote, host) -> {
			JTextComponent text = onEdt(() -> firstEditableText(frame));
			SwingUtilities.invokeAndWait(() -> {
				text.setText(text.getText() + "x");
				controller.markDirty();
			});
			changeRemotely(remote, host);
			assertEquals(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED, controller.refreshFromRemote());
			assertThrows(RuntimeException.class, controller::save);
			assertEquals(IapPhase.DURING_OP, remote.loadSnapshot(host.incident()).data().getIapPhase());
		});
	}

	/**
	 * Opens a displayed connected window without a monitor, so refreshes happen only when the test asks.
	 *
	 * @param scenario the scenario to run.
	 * @throws Exception when setup or the scenario fails.
	 */
	private void scenario(Scenario scenario) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore store = host.pair();
			var data = store.loadSnapshot(host.incident()).data();
			AtomicReference<MainFrame> created = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, host.incident(), new PdfExportService(),
						new IncidentValidator(), directory);
				frame.setVisible(true);
				created.set(frame);
			});
			MainFrame frame = created.get();
			try {
				AppController controller = (AppController) Ics205PanelTest.field(frame, "controller");
				scenario.run(frame, controller, host.pair(), host);
			} finally {
				SwingUtilities.invokeAndWait(frame::dispose);
			}
		}
	}

	/**
	 * Commits a change to the incident from another device.
	 *
	 * @param remote the other device's store.
	 * @param host fixture hosting the incident.
	 * @throws Exception when the save fails.
	 */
	private static void changeRemotely(RemoteIncidentStore remote, HostedIncidentFixture host) throws Exception {
		var snapshot = remote.loadSnapshot(host.incident());
		snapshot.data().setIapPhase(IapPhase.DURING_OP);
		remote.save(host.incident(), snapshot.data(), snapshot.revision());
	}

	/**
	 * Finds the first editable text input outside tables.
	 *
	 * @param frame window to search.
	 * @return an editable text component.
	 */
	private static JTextComponent firstEditableText(MainFrame frame) {
		return descendants(frame, JTextComponent.class).stream()
				.filter(text -> text.isEditable() && !(text instanceof JFormattedTextField)
						&& SwingUtilities.getAncestorOfClass(JTable.class, text) == null)
				.findFirst().orElseThrow();
	}

	/**
	 * Collects descendants of a type.
	 *
	 * @param root container to walk.
	 * @param type component type.
	 * @param <T> component type.
	 * @return matches in tree order.
	 */
	private static <T extends Component> List<T> descendants(Container root, Class<T> type) {
		List<T> found = new ArrayList<>();
		for (Component child : root.getComponents()) {
			if (type.isInstance(child)) {
				found.add(type.cast(child));
			}
			if (child instanceof Container container) {
				found.addAll(descendants(container, type));
			}
		}
		return found;
	}

	/**
	 * Evaluates on the event thread.
	 *
	 * @param supplier computation.
	 * @param <T> result type.
	 * @return the result.
	 * @throws Exception when interrupted or the computation fails.
	 */
	private static <T> T onEdt(java.util.concurrent.Callable<T> supplier) throws Exception {
		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			try {
				result.set(supplier.call());
			} catch (Exception e) {
				failure.set(e);
			}
		});
		if (failure.get() != null) {
			throw failure.get();
		}
		return result.get();
	}
}
