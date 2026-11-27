/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.FileRecordVersionStore;
import org.sarmanagement.icsforms.persistence.FullDocumentSnapshot;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.ui.OutboxReviewPanel.Choice;
import org.sarmanagement.icsforms.ui.OutboxReviewPanel.Entry;
import org.sarmanagement.icsforms.ui.OutboxReviewPanel.Status;

/** Exercises review and recovery using conflicts emitted by the actual incident service. */
class OutboxReviewPanelTest {
	@TempDir
	Path directory;

	@Test
	void displaysHostConflictAndOnlyDeliversExplicitDecisionsAndDraft() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId incident = store.create(new AppData());
			IncidentService service = new IncidentService(store, new FileRecordVersionStore(directory.resolve("incidents")));
			IncidentService.ReadResult initial = service.read(incident);
			IncidentService.VersionedRecord context = initial.records().stream()
					.filter(record -> record.key().type().equals(IncidentContext.class.getName()))
					.findFirst().orElseThrow();
			IncidentService.RecordBatch host = batch(incident, "host", context, "Host name");
			IncidentService.RecordBatch draft = batch(incident, "draft", context, "My name");
			assertTrue(service.submit(host).applied());
			IncidentService.SubmissionResult result = service.submit(draft);
			assertFalse(result.applied());
			assertEquals(1, result.conflicts().size());
			AppData draftDocument = new AppData();
			draftDocument.getIncidentContext().setIncidentName("My name");
			Entry entry = new Entry(draft.operationId(), new AppData(), draftDocument, draft,
					Status.CONFLICT, result.conflicts(), null);
			Recorder recorder = new Recorder();
			SwingUtilities.invokeAndWait(() -> {
				OutboxReviewPanel panel = new OutboxReviewPanel(recorder);
				panel.setEntries(List.of(entry));
				JList<?> list = find(panel, JList.class);
				list.setSelectedIndex(0);
				JTable table = find(panel, JTable.class);
				assertEquals("\"\"", table.getValueAt(0, 2));
				assertEquals("\"My name\"", table.getValueAt(0, 3));
				assertEquals("\"Host name\"", table.getValueAt(0, 4));
				JButton apply = button(panel, "Apply my choices");
				button(panel, "Refresh conflict values").doClick();
				assertEquals(entry, recorder.refreshed);
				assertEquals(0, recorder.resolutions);
				assertFalse(apply.isEnabled());
				button(panel, "Export / recover draft").doClick();
				assertEquals(draft, recorder.exported.batch());
				assertEquals("My name", recorder.exported.draft().getIncidentContext().getIncidentName());
				recorder.exported.draft().getIncidentContext().setIncidentName("Changed copy");
				assertEquals("My name", entry.draft().getIncidentContext().getIncidentName());
				table.setValueAt(Choice.MINE, 0, 5);
				assertTrue(apply.isEnabled());
				panel.setEntries(List.of(entry));
				assertEquals(Choice.MINE, table.getValueAt(0, 5));
				apply.doClick();
				assertEquals(entry, recorder.resolved);
				assertEquals(List.of(new OutboxReviewPanel.Decision(result.conflicts().get(0), Choice.MINE)),
						recorder.decisions);
				assertEquals(1, recorder.resolutions);
			});
			assertEquals("Host name", service.read(incident).records().stream()
					.filter(record -> record.key().equals(context.key())).findFirst().orElseThrow()
					.content().path("incidentName").asText());
			assertEquals(result, service.submit(draft));
		}
	}

	@Test
	void requiresEveryChoiceAndInvalidatesChoicesWhenConflictChanges() throws Exception {
		IncidentService.RecordBatch batch = new IncidentService.RecordBatch(IncidentId.newId(), "operation", List.of());
		var key = new org.sarmanagement.icsforms.model.RecordIndex.RecordKey("type", "id");
		var first = new IncidentService.FieldConflict(key, "one", null,
				com.fasterxml.jackson.databind.node.TextNode.valueOf("mine"),
				com.fasterxml.jackson.databind.node.TextNode.valueOf("host"));
		var second = new IncidentService.FieldConflict(key, "two", null, null,
				com.fasterxml.jackson.databind.node.NullNode.instance);
		Entry entry = new Entry(batch.operationId(), new AppData(), new AppData(), batch,
				Status.CONFLICT, List.of(first, second), null);
		SwingUtilities.invokeAndWait(() -> {
			Recorder recorder = new Recorder();
			OutboxReviewPanel panel = new OutboxReviewPanel(recorder);
			panel.setEntries(List.of(entry));
			find(panel, JList.class).setSelectedIndex(0);
			JTable table = find(panel, JTable.class);
			assertEquals("(missing)", table.getValueAt(1, 3));
			assertEquals("null", table.getValueAt(1, 4));
			JButton apply = button(panel, "Apply my choices");
			table.setValueAt(Choice.MINE, 0, 5);
			assertFalse(apply.isEnabled());
			table.setValueAt(Choice.THEIRS, 1, 5);
			assertTrue(apply.isEnabled());
			apply.doClick();
			assertEquals(List.of(new OutboxReviewPanel.Decision(first, Choice.MINE),
					new OutboxReviewPanel.Decision(second, Choice.THEIRS)), recorder.decisions);
			panel.setEntries(List.of(new Entry(batch.operationId(), new AppData(), new AppData(), batch,
					Status.CONFLICT, List.of(first), null)));
			assertEquals(Choice.UNDECIDED, table.getValueAt(0, 5));
			assertFalse(apply.isEnabled());
			assertThrows(IllegalArgumentException.class, () -> panel.setEntries(List.of(entry, entry)));
			assertEquals(1, table.getRowCount());
		});
	}

	@Test
	void keepAllButtonsDecideEveryRowAndTheListHidesTheInternalOperationPrefix() throws Exception {
		IncidentService.RecordBatch batch = new IncidentService.RecordBatch(IncidentId.newId(),
				"offline-1234abcd-0000", List.of());
		var key = new org.sarmanagement.icsforms.model.RecordIndex.RecordKey("type", "id");
		var first = new IncidentService.FieldConflict(key, "one", null,
				com.fasterxml.jackson.databind.node.TextNode.valueOf("mine"),
				com.fasterxml.jackson.databind.node.TextNode.valueOf("host"));
		var second = new IncidentService.FieldConflict(key, "two", null, null,
				com.fasterxml.jackson.databind.node.NullNode.instance);
		Entry entry = new Entry(batch.operationId(), new AppData(), new AppData(), batch, Status.CONFLICT,
				List.of(first, second), null);
		SwingUtilities.invokeAndWait(() -> {
			Recorder recorder = new Recorder();
			OutboxReviewPanel panel = new OutboxReviewPanel(recorder);
			panel.setEntries(List.of(entry));
			find(panel, JList.class).setSelectedIndex(0);
			assertEquals("CONFLICT — edit 1234abcd", entry.toString());
			button(panel, "Keep all stored copies").doClick();
			button(panel, "Apply my choices").doClick();
			assertEquals(List.of(new OutboxReviewPanel.Decision(first, Choice.THEIRS),
					new OutboxReviewPanel.Decision(second, Choice.THEIRS)), recorder.decisions);
		});
	}

	@Test
	void pendingAndRejectedDraftsRemainExportableWithoutResolution() throws Exception {
		var batch = new IncidentService.RecordBatch(IncidentId.newId(), "pending", List.of());
		Entry pending = new Entry(batch.operationId(), new AppData(), new AppData(), null, Status.PENDING, List.of(), null);
		Entry rejected = new Entry("rejected", new AppData(), new AppData(),
				new IncidentService.RecordBatch(batch.incident(), "rejected", List.of()), Status.REJECTED,
				List.of(), "Validation failed");
		SwingUtilities.invokeAndWait(() -> {
			Recorder recorder = new Recorder();
			OutboxReviewPanel panel = new OutboxReviewPanel(recorder);
			panel.setEntries(List.of(pending, rejected));
			JList<?> list = find(panel, JList.class);
			for (int i = 0; i < 2; i++) {
				list.setSelectedIndex(i);
				assertFalse(button(panel, "Apply my choices").isEnabled());
				assertFalse(button(panel, "Refresh conflict values").isEnabled());
				assertFalse(button(panel, "Approve reviewed changes for sync").isEnabled());
				button(panel, "Export / recover draft").doClick();
				assertEquals(i == 0 ? pending : rejected, recorder.exported);
			}
			assertEquals(0, recorder.resolutions);
		});
	}

	@Test
	void pendingEditShowsItsChangesAndTheFirstEntryIsSelectedWithABoundedSize() throws Exception {
		AppData before = new AppData();
		before.getIncidentContext().setIncidentName("Old");
		AppData draft = FullDocumentSnapshot.detach(before);
		draft.getIncidentContext().setIncidentName("New");
		Entry entry = new Entry("pending-1", before, draft, null, Status.PENDING, List.of(), null);
		SwingUtilities.invokeAndWait(() -> {
			OutboxReviewPanel panel = new OutboxReviewPanel(new Recorder());
			panel.setEntries(List.of(entry));
			JTable table = find(panel, JTable.class);
			assertEquals(1, table.getRowCount());
			assertEquals("\"New\"", table.getValueAt(0, 3));
			java.awt.Rectangle screen = java.awt.GraphicsEnvironment.isHeadless()
					? new java.awt.Rectangle(0, 0, 1280, 800)
					: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
			assertTrue(panel.getPreferredSize().width <= Math.max(960, screen.width));
		});
	}

	@Test
	void rebasedDraftRequiresReviewAndExplicitApprovalWithoutResubmission() throws Exception {
		AppData before = new AppData();
		before.getIncidentContext().setIncidentName("Host name");
		AppData draft = FullDocumentSnapshot.detach(before);
		draft.getIncidentContext().setIncidentName("Rebased name");
		Entry entry = new Entry("rebased", before, draft, null, Status.REVIEW_REQUIRED, List.of(), null);
		SwingUtilities.invokeAndWait(() -> {
			Recorder recorder = new Recorder();
			OutboxReviewPanel panel = new OutboxReviewPanel(recorder);
			panel.setEntries(List.of(entry));
			find(panel, JList.class).setSelectedIndex(0);
			JTable table = find(panel, JTable.class);
			assertEquals(1, table.getRowCount());
			assertEquals("incidentName", table.getValueAt(0, 1));
			assertEquals("\"Host name\"", table.getValueAt(0, 2));
			assertEquals("\"Rebased name\"", table.getValueAt(0, 3));
			assertFalse(table.isCellEditable(0, 5));
			table.setRowSelectionInterval(0, 0);
			assertTrue(find(panel, JTextArea.class).getText().contains("\"Rebased name\""));
			JButton approve = button(panel, "Approve reviewed changes for sync");
			assertFalse(approve.isEnabled());
			find(panel, JCheckBox.class).doClick();
			assertTrue(approve.isEnabled());
			panel.setEntries(List.of(entry));
			assertFalse(approve.isEnabled());
			find(panel, JCheckBox.class).doClick();
			assertTrue(approve.isEnabled());
			assertEquals(0, recorder.resolutions);
			approve.doClick();
			assertEquals(entry, recorder.confirmed);
			assertFalse(approve.isEnabled());
			assertEquals(0, recorder.resolutions);
		});
	}

	@Test
	void rebasedReviewShowsChangedIncidentWideSettingsAndRecordFields() throws Exception {
		AppData before = new AppData();
		before.setConfiguredTimeZoneId("America/New_York");
		before.setUseSystemTimeZone(true);
		before.getIncidentContext().setIncidentName("Host name");
		AppData draft = FullDocumentSnapshot.detach(before);
		draft.setConfiguredTimeZoneId("America/Denver");
		draft.setUseSystemTimeZone(false);
		draft.getIncidentContext().setIncidentName("Draft name");
		Entry entry = new Entry("rebased", before, draft, null, Status.REVIEW_REQUIRED, List.of(), null);
		SwingUtilities.invokeAndWait(() -> {
			OutboxReviewPanel panel = new OutboxReviewPanel(new Recorder());
			panel.setEntries(List.of(entry));
			find(panel, JList.class).setSelectedIndex(0);
			JTable table = find(panel, JTable.class);
			assertEquals(3, table.getRowCount());
			int zone = fieldRow(table, "configuredTimeZoneId");
			assertEquals("Incident-wide settings", table.getValueAt(zone, 0));
			assertEquals("\"America/New_York\"", table.getValueAt(zone, 2));
			assertEquals("\"America/Denver\"", table.getValueAt(zone, 3));
			int systemZone = fieldRow(table, "useSystemTimeZone");
			assertEquals("true", table.getValueAt(systemZone, 2));
			assertEquals("false", table.getValueAt(systemZone, 3));
			assertEquals("\"Draft name\"", table.getValueAt(fieldRow(table, "incidentName"), 3));
			table.setRowSelectionInterval(zone, zone);
			assertTrue(find(panel, JTextArea.class).getText().contains("\"America/Denver\""));
		});
	}

	@Test
	void rootOnlyRebasedReviewDisplaysChangesWithoutUnchangedSettings() throws Exception {
		AppData before = new AppData();
		before.setConfiguredTimeZoneId("UTC");
		AppData draft = FullDocumentSnapshot.detach(before);
		draft.setConfiguredTimeZoneId("Europe/London");
		Entry entry = new Entry("root-only", before, draft, null, Status.REVIEW_REQUIRED, List.of(), null);
		SwingUtilities.invokeAndWait(() -> {
			OutboxReviewPanel panel = new OutboxReviewPanel(new Recorder());
			panel.setEntries(List.of(entry));
			find(panel, JList.class).setSelectedIndex(0);
			JTable table = find(panel, JTable.class);
			assertEquals(1, table.getRowCount());
			assertEquals("configuredTimeZoneId", table.getValueAt(0, 1));
			assertEquals("\"UTC\"", table.getValueAt(0, 2));
			assertEquals("\"Europe/London\"", table.getValueAt(0, 3));
		});
	}

	/**
	 * Finds a review row by its field name.
	 *
	 * @param table review table
	 * @param field expected field name
	 * @return matching row index
	 */
	private static int fieldRow(JTable table, String field) {
		for (int row = 0; row < table.getRowCount(); row++) {
			if (field.equals(table.getValueAt(row, 1))) {
				return row;
			}
		}
		throw new AssertionError("Missing review field " + field);
	}

	/**
	 * Builds a real service submission using the same base record for competing writers.
	 *
	 * @param incident incident being edited
	 * @param id operation identity
	 * @param context initial context
	 * @param name submitted incident name
	 * @return one-record batch
	 */
	private static IncidentService.RecordBatch batch(IncidentId incident, String id,
			IncidentService.VersionedRecord context, String name) {
		ObjectNode content = (ObjectNode) context.content();
		content.put("incidentName", name);
		return new IncidentService.RecordBatch(incident, id, List.of(new IncidentService.RecordSubmission(context.key(),
				null, null, context.version(), IncidentService.Operation.UPDATE, content)));
	}

	/**
	 * Finds a component by type in the panel hierarchy.
	 *
	 * @param root container to search
	 * @param type expected type
	 * @param <T> component type
	 * @return matching component
	 */
	private static <T extends Component> T find(Container root, Class<T> type) {
		for (Component child : root.getComponents()) {
			if (type.isInstance(child)) {
				return type.cast(child);
			}
			if (child instanceof Container container) {
				T found = findOrNull(container, type);
				if (found != null) {
					return found;
				}
			}
		}
		throw new AssertionError("Missing component " + type);
	}

	/**
	 * Recursively finds an optional component.
	 *
	 * @param root container to search
	 * @param type expected type
	 * @param <T> component type
	 * @return matching component or null
	 */
	private static <T extends Component> T findOrNull(Container root, Class<T> type) {
		for (Component child : root.getComponents()) {
			if (type.isInstance(child)) {
				return type.cast(child);
			}
			if (child instanceof Container container) {
				T found = findOrNull(container, type);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * Finds an action button by its displayed label.
	 *
	 * @param root container to search
	 * @param label button label
	 * @return matching button
	 */
	private static JButton button(Container root, String label) {
		List<JButton> buttons = new ArrayList<>();
		collectButtons(root, buttons);
		return buttons.stream().filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
	}

	/**
	 * Collects buttons from a component hierarchy.
	 *
	 * @param root container to search
	 * @param buttons destination
	 */
	private static void collectButtons(Container root, List<JButton> buttons) {
		for (Component child : root.getComponents()) {
			if (child instanceof JButton button) {
				buttons.add(button);
			}
			if (child instanceof Container container) {
				collectButtons(container, buttons);
			}
		}
	}

	/** Records callbacks without performing persistence. */
	private static final class Recorder implements OutboxReviewPanel.Actions {
		private Entry exported;
		private Entry resolved;
		private Entry refreshed;
		private Entry confirmed;
		private List<OutboxReviewPanel.Decision> decisions;
		private int resolutions;

		/** {@inheritDoc} */
		@Override
		public void exportDraft(Entry entry) {
			exported = entry;
		}

		/** {@inheritDoc} */
		@Override
		public void resolve(Entry entry, List<OutboxReviewPanel.Decision> values) {
			resolved = entry;
			decisions = values;
			resolutions++;
		}

		/** {@inheritDoc} */
		@Override
		public void refreshConflict(Entry entry) {
			refreshed = entry;
		}

		/** {@inheritDoc} */
		@Override
		public void confirm(Entry entry) {
			confirmed = entry;
		}
	}
}
