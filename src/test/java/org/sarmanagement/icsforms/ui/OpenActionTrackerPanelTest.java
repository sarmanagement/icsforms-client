/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.PocAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Headless Swing regressions for tracker controls and follow-up edits. */
class OpenActionTrackerPanelTest {
	@Test
	void regularPocTextEditClearsBriefingAndAllowsExplicitRebrief() {
		JTextField poc = new JTextField("First");
		JCheckBox briefed = new JCheckBox("Briefed", true);
		OpenActionTrackerPanel.resetBriefingOnPocEdit(poc, briefed);
		poc.setText("Second");
		assertFalse(briefed.isSelected());
		briefed.setSelected(true);
		assertTrue(briefed.isSelected());
		poc.setText("");
		assertFalse(briefed.isSelected());
	}

	@Test
	void bothTrackersHighlightDueSoonAndExcludeCompletedTargets() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		OpenActionItem action = new OpenActionItem();
		action.setTargetDate(now.plusHours(12));
		assertTrue(OpenActionTrackerPanel.isDueSoon(action));
		action.setTargetDate(now.plusHours(25));
		assertFalse(OpenActionTrackerPanel.isDueSoon(action));
		action.setTargetDate(now.minusHours(1));
		assertFalse(OpenActionTrackerPanel.isDueSoon(action));
		action.setTargetDate(now.plusHours(12));
		action.setStatus("completed");
		assertFalse(OpenActionTrackerPanel.isDueSoon(action));
		ClueLogEntry clue = new ClueLogEntry();
		clue.setFollowUpTargetDate(now.plusHours(12));
		assertEquals(new java.awt.Color(255, 245, 180),
				ClueLogPanel.followUpTargetColor(clue, java.awt.Color.WHITE));
		clue.setFollowUpTargetDate(now.minusHours(1));
		assertEquals(new java.awt.Color(255, 225, 210),
				ClueLogPanel.followUpTargetColor(clue, java.awt.Color.WHITE));
		clue.setFollowUpStatus(" FOLLOWED UP ");
		assertEquals(java.awt.Color.WHITE, ClueLogPanel.followUpTargetColor(clue, java.awt.Color.WHITE));
		clue.setFollowUpStatus("");
		clue.setFollowUpTargetDate(now.plusHours(25));
		assertEquals(java.awt.Color.WHITE, ClueLogPanel.followUpTargetColor(clue, java.awt.Color.WHITE));
	}

	@Test
	void exportDefaultsFollowPersistedFlagsAndPreserveExistingSelections() {
		AppData data = new AppData();
		assertFalse(MainFrame.isExportFormVisible("ICS 233-CG", data));
		assertFalse(MainFrame.isExportFormSelectedByDefault("ICS 233-CG", data));
		assertFalse(MainFrame.isExportFormSelectedByDefault("Clue Log (ICS 233-CG adapted)", data));
		assertTrue(MainFrame.isExportFormSelectedByDefault("Clue Log", data));
		assertTrue(MainFrame.isExportFormSelectedByDefault("ICS 202", data));
		assertTrue(MainFrame.isExportFormVisible("Clue Log (ICS 233-CG adapted)", data));
		data.setOpenActionTrackerEnabled(true);
		data.setClueLogIcs233Enabled(true);
		data.setIncidentMode(IncidentMode.GENERIC);
		assertTrue(MainFrame.isExportFormVisible("ICS 233-CG", data));
		assertTrue(MainFrame.isExportFormSelectedByDefault("ICS 233-CG", data));
		assertTrue(MainFrame.isExportFormSelectedByDefault("Clue Log (ICS 233-CG adapted)", data));
		assertFalse(MainFrame.isExportFormSelectedByDefault("Clue Log", data));
		assertTrue(MainFrame.isExportFormSelectedByDefault("ICS 202", data));
	}

	@Test
	void filtersRetainAllItemsAndNumbersCloseGaps() {
		AppController controller = controller(new AppData());
		OpenActionTrackerPanel panel = new OpenActionTrackerPanel(controller);
		OpenActionItem open = new OpenActionItem();
		open.setPoc("Current POC");
		OpenActionItem completed = new OpenActionItem();
		completed.setActualDate(LocalDateTime.now(ZoneOffset.UTC));
		panel.addItem(open);
		panel.addItem(completed);
		JTable table = (JTable) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
		JComboBox<?> filter = (JComboBox<?>) ((JPanel) panel.getComponent(0)).getComponent(1);
		assertEquals(1, table.getRowCount());
		assertEquals("Current POC", table.getValueAt(0, 2));
		filter.setSelectedItem("Completed");
		assertEquals(1, table.getRowCount());
		assertEquals(2, table.getValueAt(0, 0));
		filter.setSelectedItem("All");
		assertEquals(2, table.getRowCount());
		controller.getData().setIncidentMode(IncidentMode.GENERIC);
		controller.getData().setOpenActionTrackerEnabled(false);
		panel.refreshFromModel();
		assertEquals(2, controller.getData().getOpenActionItems().size());
		panel.removeItem(open);
		assertEquals(1, completed.getNumber());
		assertEquals(1, table.getRowCount());
		assertTrue(controller.isDirty());
	}

	@Test
	void dueHighlightExcludesCompletedItemsAndFormatsConfiguredTimezone() {
		OpenActionItem item = new OpenActionItem();
		assertFalse(OpenActionTrackerPanel.isOverdue(item));
		item.setTargetDate(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
		assertTrue(OpenActionTrackerPanel.isOverdue(item));
		item.setActualDate(LocalDateTime.now(ZoneOffset.UTC));
		assertFalse(OpenActionTrackerPanel.isOverdue(item));
		item.setActualDate(null);
		item.setStatus(" COMPLETED ");
		assertFalse(OpenActionTrackerPanel.isOverdue(item));
		ZoneId original = UiSupport.getDateTimeDisplayZone();
		try {
			UiSupport.setDateTimeDisplayZone(ZoneId.of("America/New_York"));
			assertEquals("2026-01-01 07:00", ActionDateField.format(LocalDateTime.parse("2026-01-01T12:00")));
			assertEquals("", ActionDateField.format(null));
			assertNull(new ActionDateField(null).getValue());
			assertEquals(LocalDateTime.parse("2026-01-01T12:00"),
					new ActionDateField(LocalDateTime.parse("2026-01-01T12:00")).getValue());
		} catch (java.text.ParseException exception) {
			fail(exception);
		} finally {
			UiSupport.setDateTimeDisplayZone(original);
		}
	}

	@Test
	void settingsApplyIndependentlyAndNeverDeleteRecords() {
		AppData data = new AppData();
		data.setOpenActionItems(List.of(new OpenActionItem()));
		data.setClueLogEntries(List.of(new ClueLogEntry()));
		ActionTrackerSettingsPanel panel = new ActionTrackerSettingsPanel(data);
		JPanel form = (JPanel) panel.getComponent(0);
		for (java.awt.Component component : form.getComponents()) {
			if (component instanceof JCheckBox check) {
				check.setSelected(true);
			} else if (component instanceof JComboBox<?> combo) {
				combo.setSelectedIndex(1);
			}
		}
		panel.applyTo(data);
		assertTrue(data.isOpenActionTrackerEnabled());
		assertTrue(data.isClueLogIcs233Enabled());
		assertTrue(data.isIcs233Portrait());
		assertEquals(1, data.getOpenActionItems().size());
		assertEquals(1, data.getClueLogEntries().size());
	}

	@Test
	void followUpSaveAppendsPocOnlyWhenChanged() throws Exception {
		ClueLogEntry clue = new ClueLogEntry();
		LocalDateTime time = LocalDateTime.parse("2026-01-01T12:00");
		clue.setFollowUpAssignments(List.of(new PocAssignment("First", time)));
		clue.setFollowUpBriefed(true);
		ClueFollowUpPanel panel = new ClueFollowUpPanel(clue);
		panel.saveTo(clue);
		assertEquals(1, clue.getFollowUpAssignments().size());
		JPanel form = (JPanel) panel.getComponent(0);
		for (java.awt.Component component : form.getComponents()) {
			if (component instanceof JTextField field) {
				assertTrue(field.getToolTipText().contains("point of contact"));
				field.setText("Second");
			} else if (component instanceof JCheckBox check) {
				assertTrue(check.getToolTipText().contains("briefed"));
			}
		}
		panel.saveTo(clue);
		assertEquals(2, clue.getFollowUpAssignments().size());
		assertEquals("Second", clue.getFollowUpAssignedTo());
		assertFalse(clue.isFollowUpBriefed());
		assertEquals(time, clue.getFollowUpAssignments().get(0).getAssignedAt());
		panel.saveTo(clue);
		assertEquals(2, clue.getFollowUpAssignments().size());
		for (java.awt.Component component : form.getComponents()) {
			if (component instanceof JCheckBox check) {
				check.setSelected(true);
			}
		}
		panel.saveTo(clue);
		assertTrue(clue.isFollowUpBriefed());
		assertEquals(2, clue.getFollowUpAssignments().size());
	}

	@Test
	void sarDebriefCopiesRetainFollowUpFields() throws Exception {
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription("Clue");
		clue.setFollowUpAssignments(List.of(new PocAssignment("POC", LocalDateTime.parse("2026-01-01T12:00"))));
		clue.setFollowUpBriefed(true);
		clue.setFollowUpTargetDate(LocalDateTime.parse("2026-01-02T12:00"));
		clue.setFollowUpCompletedDate(LocalDateTime.parse("2026-01-03T12:00"));
		clue.setFollowUpStatus("Checked");
		Class<?> type = Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$ClueEntriesTableModel");
		var constructor = type.getDeclaredConstructor();
		constructor.setAccessible(true);
		Object model = constructor.newInstance();
		var setRows = type.getDeclaredMethod("setRows", List.class);
		setRows.setAccessible(true);
		setRows.invoke(model, List.of(clue));
		var getRows = type.getDeclaredMethod("getRows");
		getRows.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<ClueLogEntry> copies = (List<ClueLogEntry>) getRows.invoke(model);
		var convert = SarTaskPanel.class.getDeclaredMethod("clueValuesFrom", List.class,
				org.sarmanagement.icsforms.model.SarTaskAssignment.class, String.class);
		convert.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<ClueLogEntry> restored = (List<ClueLogEntry>) convert.invoke(null, copies,
				new org.sarmanagement.icsforms.model.SarTaskAssignment(), "Task");
		assertEquals("POC", restored.get(0).getFollowUpAssignedTo());
		assertTrue(restored.get(0).isFollowUpBriefed());
		assertEquals(clue.getFollowUpTargetDate(), restored.get(0).getFollowUpTargetDate());
		assertEquals(clue.getFollowUpCompletedDate(), restored.get(0).getFollowUpCompletedDate());
		assertEquals("Checked", restored.get(0).getFollowUpStatus());
		assertNotSame(clue, copies.get(0));
	}

	/**
	 * Creates a controller without creating any filesystem artifacts.
	 * @param data incident data
	 * @return test controller
	 */
	private static AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(Path.of("target/tracker-test-unused.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()), new IncidentValidator());
	}
}
