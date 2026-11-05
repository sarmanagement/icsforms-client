/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RadioCheckStatus;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Covers console projections, draft retention and actual shared-editor check transactions. */
@Timeout(30)
class CommunicationsConsolePanelTest {
	@TempDir
	Path directory;

	@Test
	void logActionsBelongToRecordedStatusBoardAndClearFilterButtonIsAbsent() throws Exception {
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller(incident()));
			try {
				JList<?> tasks = accessible(panel, JList.class, "Communications task assignment board");
				JPanel board = (JPanel) tasks.getParent().getParent().getParent().getParent();
				assertEquals("Task assignment board — recorded status",
						((javax.swing.border.TitledBorder) board.getBorder()).getTitle());
				Component actions = ((BorderLayout) board.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
				assertSame(actions, button(panel, "Add Log Entry…").getParent());
				assertSame(actions, button(panel, "Communications Problem…").getParent());
				assertEquals(null, ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.NORTH));
				assertFalse(components(panel, JButton.class).stream()
						.anyMatch(button -> button.getText().startsWith("Clear task filter")));
				JPopupMenu menu = tasks.getComponentPopupMenu();
				assertNotNull(menu);
				assertEquals(List.of("Add Log Entry…", "Communications Problem…"),
						components(menu, JMenuItem.class).stream().map(JMenuItem::getText).toList());
			} finally {
				panel.dispose();
			}
		});
	}

	@Test
	void taskContextMenuSelectsClickedRowAndRecordsBothEventTypesWithoutChangingChannels() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			JFrame frame = new JFrame();
			frame.setContentPane(panel);
			frame.setSize(1200, 800);
			frame.setVisible(true);
			try {
				JList<?> tasks = accessible(panel, JList.class, "Communications task assignment board");
				JPopupMenu menu = tasks.getComponentPopupMenu();
				for (int index = 0; index < 2; index++) {
					tasks.setSelectedIndex(0);
					java.awt.Rectangle row = tasks.getCellBounds(1, 1);
					menu.show(tasks, row.x + 2, row.y + row.height / 2);
					assertTrue(menu.isVisible());
					assertEquals(data.getSarTaskAssignments().get(1).getAssignmentId(), panel.selectedAssignmentId());
					menu.setVisible(false);
					JMenuItem action = (JMenuItem) menu.getComponent(index);
					String eventType = index == 0 ? ActivityEventType.ID_FREE_TEXT
							: ActivityEventType.ID_COMMUNICATIONS_PROBLEM;
					withDialog("Add log entry", dialog -> {
						assertEquals(eventType, selectedEventType(dialog).getId());
						narrative(dialog).setText("Task-board contextual entry");
						button(dialog, "OK").doClick();
					}, action::doClick);
					ActivityLogEntry saved = data.getActivityLogs().get(0).getActivityLog().get(index);
					assertEquals(data.getSarTaskAssignments().get(1).getAssignmentId(), saved.getAssignmentId());
					assertEquals(eventType, saved.getEventTypeId());
				}
				menu.show(tasks, 2, tasks.getCellBounds(1, 1).y + tasks.getCellBounds(1, 1).height + 20);
				assertFalse(menu.isVisible());
				for (RadioChannel channel : data.getForm205().getChannels()) {
					assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
				}
			} finally {
				panel.dispose();
				frame.dispose();
			}
		});
	}

	@Test
	void noTaskSelectionShowsIncidentActivityAndChannelNamesRemainPlainText() throws Exception {
		AppData data = new AppData();
		Ics214Form log = new Ics214Form();
		log.getActivityLog().add(entry("Incident activity", "", "", "2026-11-04T04:10"));
		data.getActivityLogs().add(log);
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("<html><b>Recorded channel</b>");
		data.getForm205().getChannels().add(channel);
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			try {
				JList<?> logs = accessible(panel, JList.class, "Recent ICP log entries");
				assertEquals(1, logs.getModel().getSize());
				JComboBox<?> channels = accessible(panel, JComboBox.class, "Channel for communications check");
				javax.swing.DefaultListCellRenderer renderer =
						(javax.swing.DefaultListCellRenderer) channels.getRenderer();
				renderer.getListCellRendererComponent(new JList<>(), channel.getChannelName(), 0, false, false);
				assertEquals(Boolean.TRUE, renderer.getClientProperty("html.disable"));
				assertEquals(null, renderer.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
			} finally {
				panel.dispose();
			}
		});
	}

	@Test
	void explicitCheckAllowsOptionalNotesAndPrefillsItsEventTime() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			try {
				fillCheck(panel, RadioCheckStatus.PARTIAL, "COML", "2026-11-04 04:10", "");
				withDialog("Add log entry", dialog -> button(dialog, "OK").doClick(),
						() -> button(panel, "Review check + log entry…").doClick());
				RadioChannel channel = data.getForm205().getChannels().get(0);
				assertEquals(RadioCheckStatus.PARTIAL, channel.getRadioCheckStatus());
				assertEquals("", channel.getRadioCheckNotes());
				assertEquals(channel.getRadioCheckedAt(),
						data.getActivityLogs().get(0).getActivityLog().get(0).getTimestamp());
			} finally {
				panel.dispose();
			}
		});
	}

	@Test
	void openingAndRefreshingConsoleDoesNotMutateSharedModel() throws Exception {
		AppData data = incident();
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		AppController controller = controller(data);
		JsonNode before = mapper.valueToTree(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			panel.refreshFromModel();
			assertEquals(data.getSarTaskAssignments().get(0).getAssignmentId(), panel.selectedAssignmentId());
			assertEquals(data.getForm205().getChannels().get(0).getChannelId(), panel.selectedChannelId());
			assertEquals(before, mapper.valueToTree(data));
			assertFalse(controller.isDirty());
			panel.dispose();
		});
	}

	@Test
	void recentIcpProjectionUsesStableContextBeforeExactLegacyFallbackAndKeepsFullRows() {
		AppData data = incident();
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		SarTaskAssignment other = data.getSarTaskAssignments().get(1);
		ActivityLogEntry stable = entry("Stable\ncomplete narrative", task.getAssignmentId(),
				other.getResourceIdentifier(), "2026-11-04T04:30");
		stable.setStruckOut(true);
		stable.reclassifyEventType(ActivityEventType.ID_COMMUNICATIONS_PROBLEM, LocalDateTime.parse("2026-11-04T04:31"));
		ActivityLogEntry legacy = entry("Legacy label", "", SarTaskSupport.taskResourceDisplayLabel(task),
				"2026-11-04T03:30");
		ActivityLogEntry legacyResource = entry("Legacy resource", "", task.getResourceIdentifier(),
				"2026-11-04T03:00");
		ActivityLogEntry conflicting = entry("Other stable context", other.getAssignmentId(),
				task.getResourceIdentifier(), "2026-11-04T05:30");
		ActivityLogEntry undated = entry("Undated retained", task.getAssignmentId(), "", null);
		Ics214Form icp = new Ics214Form();
		icp.getActivityLog().addAll(List.of(legacyResource, undated, conflicting, legacy, stable));
		Ics214Form resourceLog = new Ics214Form();
		resourceLog.setLinkedSarTaskAssignmentId(task.getAssignmentId());
		resourceLog.getActivityLog().add(entry("Not ICP", task.getAssignmentId(), "", "2026-11-04T06:00"));
		data.getActivityLogs().addAll(List.of(icp, resourceLog));

		var rows = CommunicationsConsolePanel.recentEntries(data, task.getAssignmentId());

		assertEquals(List.of(stable, legacy, legacyResource, undated), rows.stream()
				.map(CommunicationsConsolePanel.LogChoice::entry).toList());
		assertEquals(5, CommunicationsConsolePanel.recentEntries(data, null).size());
		assertTrue(CommunicationsConsolePanel.recentEntries(data, "").isEmpty());
		assertSame(icp, rows.get(0).log());
		assertTrue(rows.get(0).toString().contains("[STRUCK OUT]"));
		assertTrue(rows.get(0).toString().contains("Stable\ncomplete narrative"));
		assertEquals(1, stable.getEventTypeChangeHistory().size());
		assertEquals("Stable\ncomplete narrative", stable.getNotableActivity());
	}

	@Test
	void resourceLookupShowsTCardContactsAndEveryExplicitResolutionStatus() throws Exception {
		AppData data = incident();
		RadioChannel first = data.getForm205().getChannels().get(0);
		RadioChannel duplicate = first.deepCopy();
		duplicate.setChannelName("Same identity");
		TCard ambiguous = card("Ambiguous", first.getChannelId(), "Original fallback");
		TCard missing = card("Missing", "missing-id", "Original missing");
		TCard unlinked = card("Unlinked", "", "Tactical");
		TCard linked = card("Linked", data.getForm205().getChannels().get(1).getChannelId(), "Old spelling");
		linked.setPhoneNumber("555-0101");
		linked.setLocation("Staging");
		linked.setStatus("Available");
		data.getTCards().addAll(List.of(ambiguous, missing, unlinked, linked));
		AppController controller = controller(data);
		data.getForm205().getChannels().add(duplicate);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			accessible(panel, JList.class, "Communications task assignment board").clearSelection();
			String text = accessible(panel, JTextArea.class, "Resource and contact lookup results").getText();
			for (String expected : List.of("[AMBIGUOUS]", "[MISSING]", "[UNLINKED]", "[LINKED]",
					"Original fallback", "Original missing", "555-0101", "Staging", "Available")) {
				assertTrue(text.contains(expected), expected + "\n" + text);
			}
			accessible(panel, JTextField.class, "Find communications resource or contact").setText("555-0101");
			String filtered = accessible(panel, JTextArea.class, "Resource and contact lookup results").getText();
			assertTrue(filtered.contains("Linked"));
			assertFalse(filtered.contains("Ambiguous"));
			assertEquals("", unlinked.getRadioChannelId());
			assertEquals("Tactical", unlinked.getRadioChannel());
			panel.dispose();
		});
	}

	@Test
	void contactsFollowUniqueRecordedTaskResourcesAndSourceRefsNotMatchingNames() throws Exception {
		AppData data = incident();
		AppController controller = controller(data);
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		TCard assigned = card("Same display name", data.getForm205().getChannels().get(0).getChannelId(), "Legacy");
		assigned.setPhoneNumber("555-assigned");
		TCard unrelated = card("Same display name", "", "Legacy");
		unrelated.setPhoneNumber("555-unrelated");
		TCard leader = card("Recorded leader", "", "Leader radio");
		leader.setPhoneNumber("555-leader");
		leader.setSourceRef("sar:" + task.getAssignmentId() + ":leader");
		TCard ambiguous = card("Ambiguous resource", "", "Unknown");
		ambiguous.setPhoneNumber("555-ambiguous");
		TCard duplicate = card("Duplicate identity", "", "Unknown");
		duplicate.setResourceId(ambiguous.getResourceId());
		TCard duplicateSource = card("Duplicate source one", "", "Unknown");
		duplicateSource.setSourceRef("sar:" + task.getAssignmentId() + ":r:99");
		duplicateSource.setPhoneNumber("555-duplicate-source");
		TCard duplicateSourceTwo = card("Duplicate source two", "", "Unknown");
		duplicateSourceTwo.setSourceRef(duplicateSource.getSourceRef());
		data.getTCards().addAll(List.of(assigned, unrelated, leader, ambiguous, duplicate,
				duplicateSource, duplicateSourceTwo));
		task.getResourcesAssigned().add(resource(assigned.getResourceId(), assigned.getPersonName()));
		task.getResourcesAssigned().add(resource(ambiguous.getResourceId(), ambiguous.getPersonName()));
		task.getResourcesAssigned().add(resource("", "Recorded without T-card"));
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			JTextArea contacts = accessible(panel, JTextArea.class, "Resource and contact lookup results");
			String scoped = contacts.getText();
			assertTrue(scoped.contains("555-assigned"));
			assertTrue(scoped.contains("555-leader"));
			assertTrue(scoped.contains("Recorded without T-card"));
			assertFalse(scoped.contains("555-unrelated"));
			assertFalse(scoped.contains("555-ambiguous"));
			assertFalse(scoped.contains("555-duplicate-source"));
			accessible(panel, JList.class, "Communications task assignment board").setSelectedIndex(1);
			assertFalse(contacts.getText().contains("555-assigned"));
			assertFalse(contacts.getText().contains("555-leader"));
			accessible(panel, JList.class, "Communications task assignment board").clearSelection();
			assertEquals("", panel.selectedAssignmentId());
			assertTrue(contacts.getText().contains("555-unrelated"));
			panel.refreshFromModel();
			assertEquals("", panel.selectedAssignmentId());
			assertTrue(contacts.getText().contains("555-unrelated"));
			panel.dispose();
		});
	}

	@Test
	void readingLegacyCardsNeverGeneratesMissingResourceIdentities() throws Exception {
		AppData data = incident();
		AppController controller = controller(data);
		TCard legacy = new TCard();
		legacy.setPersonName("Legacy responder");
		data.getTCards().add(legacy);
		assertEquals(null, legacy.peekResourceId());
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			accessible(panel, JList.class, "Communications task assignment board").clearSelection();
			panel.refreshFromModel();
			assertTrue(accessible(panel, JTextArea.class, "Resource and contact lookup results").getText()
					.contains("Legacy responder"));
			assertEquals(null, legacy.peekResourceId());
			assertFalse(controller.isDirty());
			panel.dispose();
		});
	}

	@Test
	void genericAssignmentsRemainSelectableWithoutSarScaffoldsAndCheckboxShowsAllIncidentLogs() throws Exception {
		AppData data = incident();
		AppController controller = controller(data);
		data.getSarTaskAssignments().clear();
		ResourceAssignment generic = data.getForm204().getResourcesAssigned().get(1);
		generic.setContact("Recorded generic phone");
		generic.setReportingLocation("Generic staging");
		Ics214Form log = new Ics214Form();
		log.getActivityLog().add(entry("First assignment", data.getForm204().getResourcesAssigned().get(0).getAssignmentId(),
				"", "2026-11-04T04:00"));
		log.getActivityLog().add(entry("Generic assignment", generic.getAssignmentId(), "", "2026-11-04T04:01"));
		data.getActivityLogs().add(log);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			JList<?> board = accessible(panel, JList.class, "Communications task assignment board");
			assertEquals(2, board.getModel().getSize());
			board.setSelectedIndex(1);
			assertEquals(generic.getAssignmentId(), panel.selectedAssignmentId());
			assertTrue(accessible(panel, JTextArea.class, "Selected task communications").getText()
					.contains("Generic staging"));
			assertTrue(accessible(panel, JTextArea.class, "Resource and contact lookup results").getText()
					.contains("Recorded generic phone"));
			JList<?> logs = accessible(panel, JList.class, "Recent ICP log entries");
			assertEquals(1, logs.getModel().getSize());
			accessible(panel, JCheckBox.class, "All incident ICP entries").doClick();
			assertEquals(2, logs.getModel().getSize());
			panel.refreshFromModel();
			assertEquals(generic.getAssignmentId(), panel.selectedAssignmentId());
			assertEquals(2, logs.getModel().getSize());
			panel.dispose();
		});
	}

	@Test
	void linkedContactIncludesRecordedCheckOutcomeTimeOperatorAndNotes() throws Exception {
		AppData data = incident();
		AppController controller = controller(data);
		RadioChannel channel = data.getForm205().getChannels().get(0);
		channel.setRadioCheckStatus(RadioCheckStatus.PARTIAL);
		channel.setRadioCheckedBy("Recorded COML");
		channel.setRadioCheckedAt(LocalDateTime.parse("2026-11-04T04:10"));
		channel.setRadioCheckNotes("Intermittent ridge coverage");
		TCard card = card("Recorded responder", channel.getChannelId(), "Legacy radio");
		data.getTCards().add(card);
		data.getSarTaskAssignments().get(0).getResourcesAssigned().add(resource(card.getResourceId(), card.getPersonName()));
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			String contact = accessible(panel, JTextArea.class, "Resource and contact lookup results").getText();
			for (String expected : List.of("[LINKED]", "Partial / intermittent", "Recorded COML",
					"2026-11-04T04:10", "Intermittent ridge coverage")) {
				assertTrue(contact.contains(expected), contact);
			}
			panel.dispose();
		});
	}

	@Test
	void scopedLinksUseTypedTaskIdsAndOwningFormIdsRatherThanAssignmentOrDisplayLabels() throws Exception {
		AppData data = incident();
		AppController controller = controller(data);
		data.getSarTaskAssignments().get(0).setTaskId("first-task-origin");
		data.getSarTaskAssignments().get(1).setTaskId("second-task-origin");
		data.getSarTaskAssignments().get(0).getCommunications().add(communication("Task radio", "First task contact"));
		data.getSarTaskAssignments().get(1).getCommunications().add(communication("Task radio", "Other task contact"));
		data.getForm204().getCommunications().add(communication("Shared form radio", "Owning form contact"));
		Ics204Form unrelated = new Ics204Form();
		unrelated.getCommunications().add(communication("Other form radio", "Unrelated form contact"));
		data.getAdditionalForms204().add(unrelated);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			JTextArea contacts = accessible(panel, JTextArea.class, "Resource and contact lookup results");
			assertTrue(contacts.getText().contains("First task contact"));
			assertTrue(contacts.getText().contains("Owning form contact"));
			assertTrue(contacts.getText().contains("[UNLINKED]"));
			assertFalse(contacts.getText().contains("Other task contact"));
			assertFalse(contacts.getText().contains("Unrelated form contact"));
			assertTrue(accessible(panel, JTextArea.class, "Selected task communications").getText()
					.contains("First task contact"));
			JList<?> choices = accessible(panel, JList.class, "Selectable communications resources and contacts");
			for (int index = 0; index < choices.getModel().getSize(); index++) {
				if (choices.getModel().getElementAt(index).toString().contains("First task contact")) {
					choices.setSelectedIndex(index);
				}
			}
			assertNotNull(choices.getSelectedValue());
			data.getSarTaskAssignments().get(0).getCommunications().get(0).setNameOrFunction("Renamed function");
			panel.refreshFromModel();
			assertNotNull(choices.getSelectedValue());
			assertTrue(choices.getSelectedValue().toString().contains("Renamed function"));
			accessible(panel, JList.class, "Communications task assignment board").setSelectedIndex(1);
			assertFalse(contacts.getText().contains("First task contact"));
			assertTrue(contacts.getText().contains("Other task contact"));
			assertTrue(contacts.getText().contains("Owning form contact"));
			accessible(panel, JList.class, "Communications task assignment board").clearSelection();
			assertTrue(contacts.getText().contains("Unrelated form contact"));
			panel.dispose();
		});
	}

	@Test
	void liveRefreshRetainsTaskChannelSearchAndIndependentPerChannelDrafts() throws Exception {
		AppData data = incident();
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			JList<?> tasks = accessible(panel, JList.class, "Communications task assignment board");
			JComboBox<?> channels = accessible(panel, JComboBox.class, "Channel for communications check");
			tasks.setSelectedIndex(1);
			fillCheck(panel, RadioCheckStatus.PARTIAL, "Operator one", "2026-11-04 04:10", "First draft");
			String taskId = panel.selectedAssignmentId();
			String firstId = panel.selectedChannelId();
			channels.setSelectedIndex(1);
			fillCheck(panel, RadioCheckStatus.FAILED, "Operator two", "2026-11-04 04:11", "Second draft");
			String secondId = panel.selectedChannelId();
			accessible(panel, JTextField.class, "Find communications resource or contact").setText("Retain filter");
			java.util.Collections.reverse(data.getSarTaskAssignments());
			java.util.Collections.reverse(data.getForm205().getChannels());
			controller.markDirty();
			panel.refreshFromModel();
			assertEquals(taskId, panel.selectedAssignmentId());
			assertEquals(secondId, panel.selectedChannelId());
			assertEquals("Second draft", accessible(panel, JTextArea.class, "Communications check notes").getText());
			assertEquals("Retain filter", accessible(panel, JTextField.class,
					"Find communications resource or contact").getText());
			channels.setSelectedIndex(1);
			assertEquals(firstId, panel.selectedChannelId());
			assertEquals("First draft", accessible(panel, JTextArea.class, "Communications check notes").getText());
			assertEquals("Operator one", accessible(panel, JTextField.class,
					"Communications check recorded by").getText());
			assertEquals(RadioCheckStatus.PARTIAL, accessible(panel, JComboBox.class,
					"Communications check outcome").getSelectedItem());
			panel.dispose();
		});
	}

	@Test
	void logScopeToggleAndLiveRefreshKeepSelectedSharedEntry() throws Exception {
		AppData data = incident();
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		ActivityLogEntry first = entry("Task first", task.getAssignmentId(), "", "2026-11-04T04:00");
		Ics214Form log = new Ics214Form();
		log.getActivityLog().add(first);
		log.getActivityLog().add(entry("Other task", data.getSarTaskAssignments().get(1).getAssignmentId(), "",
				"2026-11-04T04:01"));
		data.getActivityLogs().add(log);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller(data));
			JList<?> logs = accessible(panel, JList.class, "Recent ICP log entries");
			assertEquals(1, logs.getModel().getSize());
			logs.setSelectedIndex(0);
			assertTrue(button(panel, "View full entry details…").isEnabled());
			components(panel, JCheckBox.class).get(0).doClick();
			assertEquals(2, logs.getModel().getSize());
			assertSame(first, ((CommunicationsConsolePanel.LogChoice) logs.getSelectedValue()).entry());
			log.getActivityLog().add(entry("Newest", task.getAssignmentId(), "", "2026-11-04T04:02"));
			panel.refreshFromModel();
			assertEquals(3, logs.getModel().getSize());
			assertSame(first, ((CommunicationsConsolePanel.LogChoice) logs.getSelectedValue()).entry());
			panel.dispose();
		});
	}

	@Test
	void invalidMissingAndAmbiguousCheckInputsNeverOpenEditorOrMutateModel() throws Exception {
		AppData data = incident();
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller(data));
			button(panel, "Review check + log entry…").doClick();
			assertTrue(data.getActivityLogs().isEmpty());
			fillCheck(panel, RadioCheckStatus.SUCCESSFUL, "COML", "not a time", "Draft");
			button(panel, "Review check + log entry…").doClick();
			assertTrue(data.getActivityLogs().isEmpty());
			fillCheck(panel, RadioCheckStatus.SUCCESSFUL, "COML", "2026-02-30 04:10", "Draft");
			button(panel, "Review check + log entry…").doClick();
			assertTrue(data.getActivityLogs().isEmpty());
			fillCheck(panel, RadioCheckStatus.SUCCESSFUL, "COML", "2026-11-04 04:10", "Draft");
			data.getForm205().getChannels().add(data.getForm205().getChannels().get(0).deepCopy());
			button(panel, "Review check + log entry…").doClick();
			assertTrue(data.getActivityLogs().isEmpty());
			assertEquals(RadioCheckStatus.NOT_RECORDED, data.getForm205().getChannels().get(0).getRadioCheckStatus());
			assertEquals("Draft", accessible(panel, JTextArea.class, "Communications check notes").getText());
			panel.dispose();
		});
	}

	@Test
	void disposedAndReplacedIncidentConsolesCannotRefreshOrWrite() throws Exception {
		AppData original = incident();
		AppController controller = controller(original);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			String selected = panel.selectedAssignmentId();
			panel.dispose();
			original.getSarTaskAssignments().clear();
			panel.refreshFromModel();
			assertEquals(selected, panel.selectedAssignmentId());
			for (String label : List.of("Add Log Entry…", "Communications Problem…", "Review check + log entry…")) {
				button(panel, label).doClick();
			}
			assertTrue(original.getActivityLogs().isEmpty());
			CommunicationsConsolePanel replaced = new CommunicationsConsolePanel(controller);
			controller.setData(incident());
			button(replaced, "Add Log Entry…").doClick();
			button(replaced, "Communications Problem…").doClick();
			button(replaced, "Review check + log entry…").doClick();
			assertTrue(controller.getData().getActivityLogs().isEmpty());
			replaced.dispose();
		});
	}

	@Test
	void cancelingCheckEditorRetainsBothDraftsAcrossLiveRefreshAndChannelSwitch() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			fillCheck(panel, RadioCheckStatus.PARTIAL, "COML", "2026-11-04 04:10", "Check draft");
			withDialog("Add log entry", dialog -> {
				assertEquals(ActivityEventType.ID_COMMUNICATIONS_CHECK, selectedEventType(dialog).getId());
				narrative(dialog).setText("Entered shared log draft\nKeep all details");
				button(dialog, "Cancel").doClick();
			}, () -> button(panel, "Review check + log entry…").doClick());
			JComboBox<?> channels = accessible(panel, JComboBox.class, "Channel for communications check");
			channels.setSelectedIndex(1);
			channels.setSelectedIndex(0);
			controller.markDirty();
			panel.refreshFromModel();
			accessible(panel, JTextField.class, "Communications check UTC time yyyy-MM-dd HH:mm")
					.setText("2026-11-04 04:15");
			withDialog("Add log entry", dialog -> {
				assertEquals("Entered shared log draft\nKeep all details",
						narrative(dialog).getText());
				assertTrue(allText(dialog).contains("2026-11-04 04:15 UTC"));
				button(dialog, "Cancel").doClick();
			}, () -> button(panel, "Review check + log entry…").doClick());
			assertEquals("Check draft", accessible(panel, JTextArea.class, "Communications check notes").getText());
			assertEquals("COML", accessible(panel, JTextField.class, "Communications check recorded by").getText());
			assertTrue(data.getActivityLogs().isEmpty());
			assertEquals(RadioCheckStatus.NOT_RECORDED, data.getForm205().getChannels().get(0).getRadioCheckStatus());
			panel.dispose();
		});
	}

	@Test
	void oneConfirmedCheckPersistsOneSharedEntryAndPreservesConcurrentUnrelatedEdits() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		RadioChannel channel = data.getForm205().getChannels().get(0);
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			fillCheck(panel, RadioCheckStatus.SUCCESSFUL, "COML", "2026-11-04 04:10", "Clear signal");
			data.getForm202().setObjectives(List.of("Independent latest plan edit"));
			withDialog("Add log entry", dialog -> {
				assertEquals(ActivityEventType.ID_COMMUNICATIONS_CHECK, selectedEventType(dialog).getId());
				button(dialog, "OK").doClick();
			}, () -> button(panel, "Review check + log entry…").doClick());
			assertEquals(RadioCheckStatus.SUCCESSFUL, channel.getRadioCheckStatus());
			assertEquals("COML", channel.getRadioCheckedBy());
			assertEquals(LocalDateTime.parse("2026-11-04T04:10"), channel.getRadioCheckedAt());
			assertEquals("Clear signal", channel.getRadioCheckNotes());
			assertEquals(1, data.getActivityLogs().size());
			ActivityLogEntry entry = data.getActivityLogs().get(0).getActivityLog().get(0);
			assertEquals(ActivityEventType.ID_COMMUNICATIONS_CHECK, entry.getEventTypeId());
			assertTrue(entry.getNotableActivity().contains("Outcome: Successful"));
			assertTrue(entry.getNotableActivity().contains("Checked by: COML"));
			assertTrue(entry.getNotableActivity().contains("2026-11-04 04:10 UTC"));
			assertTrue(entry.getNotableActivity().contains("Notes: Clear signal"));
			assertEquals(data.getSarTaskAssignments().get(0).getAssignmentId(), entry.getAssignmentId());
			assertEquals(List.of("Independent latest plan edit"), data.getForm202().getObjectives());
			AppData restored = IncidentStoreTestFixtures.store(controller).load(controller.getActiveIncidentId());
			assertEquals(RadioCheckStatus.SUCCESSFUL, restored.getForm205().getChannels().get(0).getRadioCheckStatus());
			assertEquals(1, restored.getActivityLogs().get(0).getActivityLog().size());
			assertEquals(List.of("Independent latest plan edit"), restored.getForm202().getObjectives());
			panel.dispose();
		});
	}

	@Test
	void normalAndProblemActionsUseSelectedTaskAndNeverChangeChannelCheck() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		RadioChannel channel = data.getForm205().getChannels().get(0);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller(data));
			accessible(panel, JList.class, "Communications task assignment board").setSelectedIndex(1);
			for (String label : List.of("Add Log Entry…", "Communications Problem…")) {
				withDialog("Add log entry", dialog -> {
					String expected = label.startsWith("Communications") ? ActivityEventType.ID_COMMUNICATIONS_PROBLEM
							: ActivityEventType.ID_FREE_TEXT;
					assertEquals(expected, selectedEventType(dialog).getId());
					narrative(dialog).setText("Reported contact issue");
					button(dialog, "OK").doClick();
				}, () -> button(panel, label).doClick());
			}
			assertEquals(2, data.getActivityLogs().get(0).getActivityLog().size());
			for (ActivityLogEntry entry : data.getActivityLogs().get(0).getActivityLog()) {
				assertEquals(data.getSarTaskAssignments().get(1).getAssignmentId(), entry.getAssignmentId());
			}
			assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
			assertEquals(null, channel.getRadioCheckedAt());
			panel.dispose();
		});
	}

	@Test
	void selectedResourcePrefillsNormalProblemAndCheckEditorsWithoutLosingStableTaskContext() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		AppController controller = controller(data);
		TCard card = card("Robin", data.getForm205().getChannels().get(0).getChannelId(), "Recorded radio");
		data.getTCards().add(card);
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		task.getResourcesAssigned().add(resource(card.getResourceId(), card.getPersonName()));
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			JList<?> choices = accessible(panel, JList.class, "Selectable communications resources and contacts");
			for (int index = 0; index < choices.getModel().getSize(); index++) {
				if (choices.getModel().getElementAt(index).toString().startsWith("T-card Robin")) {
					choices.setSelectedIndex(index);
				}
			}
			assertNotNull(choices.getSelectedValue());
			panel.refreshFromModel();
			assertTrue(choices.getSelectedValue().toString().startsWith("T-card Robin"));
			for (String label : List.of("Add Log Entry…", "Communications Problem…", "Review check + log entry…")) {
				if (label.startsWith("Review")) {
					fillCheck(panel, RadioCheckStatus.SUCCESSFUL, "COML", "2026-11-04 04:10", "Clear");
				}
				withDialog("Add log entry", dialog -> {
					assertTrue(components(dialog, JComboBox.class).stream()
							.anyMatch(combo -> "Robin".equals(combo.getSelectedItem())));
					button(dialog, "OK").doClick();
				}, () -> button(panel, label).doClick());
			}
			assertEquals(3, data.getActivityLogs().get(0).getActivityLog().size());
			for (ActivityLogEntry entry : data.getActivityLogs().get(0).getActivityLog()) {
				assertEquals(task.getAssignmentId(), entry.getAssignmentId());
				assertEquals("Robin", entry.getResourceIdentifier());
			}
			panel.dispose();
		});
	}

	@Test
	void confirmedCheckCommitsExactlyTheReviewedAssertionDespiteLiveDraftChanges() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			fillCheck(panel, RadioCheckStatus.PARTIAL, "Reviewed COML", "2026-11-04 04:10", "Reviewed notes");
			withDialog("Add log entry", dialog -> {
				assertTrue(allText(dialog).contains("Reviewed notes"));
				fillCheck(panel, RadioCheckStatus.FAILED, "Changed draft", "2026-11-04 04:20", "Changed draft notes");
				panel.refreshFromModel();
				button(dialog, "OK").doClick();
			}, () -> button(panel, "Review check + log entry…").doClick());
			RadioChannel channel = data.getForm205().getChannels().get(0);
			assertEquals(RadioCheckStatus.PARTIAL, channel.getRadioCheckStatus());
			assertEquals("Reviewed COML", channel.getRadioCheckedBy());
			assertEquals("Reviewed notes", channel.getRadioCheckNotes());
			assertEquals(LocalDateTime.parse("2026-11-04T04:10"), channel.getRadioCheckedAt());
			String narrative = data.getActivityLogs().get(0).getActivityLog().get(0).getNotableActivity();
			assertTrue(narrative.contains("Reviewed notes"));
			assertFalse(narrative.contains("Changed draft notes"));
			panel.dispose();
		});
	}

	@Test
	void genericAssignmentContextReachesSharedNormalProblemAndAtomicCheckEditors() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		AppController controller = controller(data);
		data.getSarTaskAssignments().clear();
		ResourceAssignment assignment = data.getForm204().getResourcesAssigned().get(0);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			assertEquals(assignment.getAssignmentId(), panel.selectedAssignmentId());
			JList<?> resources = accessible(panel, JList.class, "Selectable communications resources and contacts");
			assertTrue(resources.getModel().getSize() > 0);
			resources.setSelectedIndex(0);
			assertTrue(resources.getSelectedValue().toString().startsWith("Assignment resource:"));
			for (String label : List.of("Add Log Entry…", "Communications Problem…", "Review check + log entry…")) {
				if (label.startsWith("Review")) {
					fillCheck(panel, RadioCheckStatus.SUCCESSFUL, "COML", "2026-11-04 04:10", "Generic check");
				}
				withDialog("Add log entry", dialog -> {
					assertTrue(components(dialog, JComboBox.class).stream()
							.anyMatch(combo -> assignment.getResourceIdentifier().equals(combo.getSelectedItem())));
					button(dialog, "OK").doClick();
				}, () -> button(panel, label).doClick());
			}
			assertEquals(3, data.getActivityLogs().get(0).getActivityLog().size());
			for (ActivityLogEntry entry : data.getActivityLogs().get(0).getActivityLog()) {
				assertEquals(assignment.getAssignmentId(), entry.getAssignmentId());
				assertEquals(assignment.getResourceIdentifier(), entry.getResourceIdentifier());
			}
			panel.dispose();
		});
	}

	@Test
	void actualPersistenceFailureKeepsBothDraftsAndNeverHalfCommits() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		AppController controller = fixture.controller();
		fixture.store().failNext(ControllableIncidentStore.Operation.SAVE,
				new IllegalStateException("Injected console save failure"));
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			fillCheck(panel, RadioCheckStatus.FAILED, "Radio operator", "2026-11-04 04:10", "No response");
			AtomicInteger confirmations = new AtomicInteger();
			withDialogs(dialog -> {
				if ("Unable to add log entry".equals(dialog.getTitle())) {
					button(dialog, "OK").doClick();
				} else if ("Add log entry".equals(dialog.getTitle())) {
					if (confirmations.getAndIncrement() == 0) {
						narrative(dialog).setText("Retain custom narrative after failure");
						button(dialog, "OK").doClick();
					} else {
						assertEquals("Retain custom narrative after failure",
								narrative(dialog).getText());
						button(dialog, "Cancel").doClick();
					}
				}
			}, () -> button(panel, "Review check + log entry…").doClick());
			assertTrue(data.getActivityLogs().isEmpty());
			assertEquals(RadioCheckStatus.NOT_RECORDED, data.getForm205().getChannels().get(0).getRadioCheckStatus());
			assertFalse(controller.isDirty());
			assertEquals("No response", accessible(panel, JTextArea.class, "Communications check notes").getText());
			assertEquals(RadioCheckStatus.NOT_RECORDED,
					fixture.store().load(fixture.incidentId()).getForm205().getChannels().get(0).getRadioCheckStatus());
			panel.dispose();
		});
	}

	@Test
	void concurrentChannelChangeRequiresDeliberateReloadAndKeepsBothDraftsForRetry() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		RadioChannel channel = data.getForm205().getChannels().get(0);
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			fillCheck(panel, RadioCheckStatus.PARTIAL, "COML", "2026-11-04 04:10", "Intermittent reception");
			channel.setRadioCheckNotes("Other operator's latest observation");
			AtomicInteger attempted = new AtomicInteger();
			withDialogs(dialog -> {
				if ("Unable to add log entry".equals(dialog.getTitle())) {
					button(dialog, "OK").doClick();
				} else if ("Add log entry".equals(dialog.getTitle())) {
					if (attempted.getAndIncrement() == 0) {
						narrative(dialog).setText("Retain my detailed log on stale failure");
						button(dialog, "OK").doClick();
					} else {
						button(dialog, "Cancel").doClick();
					}
				}
			}, () -> button(panel, "Review check + log entry…").doClick());
			assertTrue(data.getActivityLogs().isEmpty());
			assertEquals("Other operator's latest observation", channel.getRadioCheckNotes());
			assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
			button(panel, "Reload current channel baseline").doClick();
			withDialog("Add log entry", dialog -> {
				assertEquals("Retain my detailed log on stale failure", narrative(dialog).getText());
				button(dialog, "OK").doClick();
			}, () -> button(panel, "Review check + log entry…").doClick());
			assertEquals(RadioCheckStatus.PARTIAL, channel.getRadioCheckStatus());
			assertEquals("Intermittent reception", channel.getRadioCheckNotes());
			assertEquals(1, data.getActivityLogs().get(0).getActivityLog().size());
			assertTrue(data.getActivityLogs().get(0).getActivityLog().get(0).getNotableActivity()
					.endsWith("Retain my detailed log on stale failure"));
			panel.dispose();
		});
	}

	@Test
	void canceledProblemDraftSurvivesTaskSwitchAndLiveRefreshWithoutChangingChannels() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		AppController controller = controller(data);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller);
			withDialog("Add log entry", dialog -> {
				narrative(dialog).setText("Detailed unresolved communications problem");
				button(dialog, "Cancel").doClick();
			}, () -> button(panel, "Communications Problem…").doClick());
			JList<?> tasks = accessible(panel, JList.class, "Communications task assignment board");
			tasks.setSelectedIndex(1);
			tasks.setSelectedIndex(0);
			controller.markDirty();
			panel.refreshFromModel();
			withDialog("Add log entry", dialog -> {
				assertEquals("Detailed unresolved communications problem", narrative(dialog).getText());
				assertEquals(ActivityEventType.ID_COMMUNICATIONS_PROBLEM, selectedEventType(dialog).getId());
				button(dialog, "Cancel").doClick();
			}, () -> button(panel, "Communications Problem…").doClick());
			assertTrue(data.getActivityLogs().isEmpty());
			for (RadioChannel channel : data.getForm205().getChannels()) {
				assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
			}
			panel.dispose();
		});
	}

	@Test
	void consoleShowsFullSharedEntryDetailsIncludingClassificationAuditAndStruckStatus() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = incident();
		ActivityLogEntry entry = entry("Complete\nmulti-line narrative", data.getSarTaskAssignments().get(0).getAssignmentId(),
				"Responder", "2026-11-04T04:00");
		entry.reclassifyEventType(ActivityEventType.ID_COMMUNICATIONS_PROBLEM, LocalDateTime.parse("2026-11-04T04:01"));
		entry.setStruckOut(true);
		Ics214Form log = new Ics214Form();
		log.getActivityLog().add(entry);
		data.getActivityLogs().add(log);
		onEdt(() -> {
			CommunicationsConsolePanel panel = new CommunicationsConsolePanel(controller(data));
			accessible(panel, JList.class, "Recent ICP log entries").setSelectedIndex(0);
			withDialog("Activity entry details", dialog -> {
				String full = allText(dialog);
				assertTrue(full.contains("Complete\nmulti-line narrative"), full);
				assertTrue(full.toLowerCase(java.util.Locale.ROOT).contains("communications problem"), full);
				assertTrue(full.contains("Struck out") || full.contains("Struck"), full);
				assertTrue(full.contains("Note"), full);
				button(dialog, "Close").doClick();
			}, () -> button(panel, "View full entry details…").doClick());
			panel.dispose();
		});
	}

	/**
	 * Builds a small incident with two stable task and channel identities.
	 *
	 * @return test incident.
	 */
	private static AppData incident() {
		AppData data = new AppData();
		for (String name : List.of("Command", "Tactical")) {
			RadioChannel channel = new RadioChannel();
			channel.setChannelName(name);
			data.getForm205().getChannels().add(channel);
			SarTaskAssignment task = new SarTaskAssignment();
			task.setAssignmentId(name + "-assignment");
			task.setAssignmentTeamNumber(name);
			task.setResourceIdentifier(name + " team");
			task.setAssignment("Search " + name);
			task.setContact(name + " contact");
			data.getSarTaskAssignments().add(task);
			ResourceAssignment assignment = new ResourceAssignment();
			assignment.setAssignmentId(task.getAssignmentId());
			assignment.setResourceIdentifier(task.getResourceIdentifier());
			data.getForm204().getResourcesAssigned().add(assignment);
		}
		return data;
	}

	/**
	 * Creates one recorded contact with explicit channel context.
	 *
	 * @param name contact name.
	 * @param channelId stable channel link.
	 * @param fallback unchanged legacy contact.
	 * @return recorded T-card.
	 */
	private static TCard card(String name, String channelId, String fallback) {
		TCard card = new TCard();
		card.setPersonName(name);
		card.setRadioChannel(fallback);
		card.setRadioChannelId(channelId);
		return card;
	}

	/**
	 * Creates an explicitly recorded task resource without name-based linking.
	 *
	 * @param resourceId recorded stable resource identity, or blank.
	 * @param name recorded resource name.
	 * @return task resource.
	 */
	private static SarTaskResource resource(String resourceId, String name) {
		SarTaskResource resource = new SarTaskResource();
		resource.setResourceId(resourceId);
		resource.setName(name);
		return resource;
	}

	/**
	 * Creates a recorded unlinked communication without name-based channel inference.
	 *
	 * @param name contact function.
	 * @param contact exact legacy contact text.
	 * @return communication row.
	 */
	private static CommunicationEntry communication(String name, String contact) {
		CommunicationEntry entry = new CommunicationEntry();
		entry.setNameOrFunction(name);
		entry.setPrimaryContact(contact);
		return entry;
	}

	/**
	 * Creates a shared activity entry with optional timestamp.
	 *
	 * @param text original narrative.
	 * @param assignmentId stable context.
	 * @param resource legacy resource label.
	 * @param time ISO timestamp, or null.
	 * @return activity entry.
	 */
	private static ActivityLogEntry entry(String text, String assignmentId, String resource, String time) {
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setNotableActivity(text);
		entry.setAssignmentId(assignmentId);
		entry.setResourceIdentifier(resource);
		entry.setTimestamp(time == null ? null : LocalDateTime.parse(time));
		return entry;
	}

	/**
	 * Creates a controller storing its incident beneath this test's project-local directory.
	 *
	 * @param data test incident.
	 * @return controller.
	 */
	private AppController controller(AppData data) {
		return IncidentStoreTestFixtures.controller(data, new PdfExportService(), new IncidentValidator());
	}

	/**
	 * Fills only the explicit console check inputs.
	 *
	 * @param panel console under test.
	 * @param status explicit outcome.
	 * @param by recorded operator.
	 * @param time UTC time.
	 * @param text notes.
	 */
	private static void fillCheck(CommunicationsConsolePanel panel, RadioCheckStatus status, String by,
			String time, String text) {
		accessible(panel, JComboBox.class, "Communications check outcome").setSelectedItem(status);
		accessible(panel, JTextField.class, "Communications check recorded by").setText(by);
		accessible(panel, JTextField.class, "Communications check UTC time yyyy-MM-dd HH:mm").setText(time);
		accessible(panel, JTextArea.class, "Communications check notes").setText(text);
	}

	/**
	 * Runs a complete assertion block on Swing's event thread.
	 *
	 * @param action assertion block.
	 * @throws Exception when event dispatch fails.
	 */
	private static void onEdt(Runnable action) throws Exception {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			try {
				action.run();
			} catch (Throwable exception) {
				failure.set(exception);
			}
		});
		if (failure.get() != null) {
			throw new AssertionError("EDT assertion failed", failure.get());
		}
	}

	/**
	 * Finds a uniquely named accessible component.
	 *
	 * @param parent component tree.
	 * @param type component class.
	 * @param name accessible name.
	 * @param <T> component type.
	 * @return matching component.
	 */
	private static <T extends Component> T accessible(Container parent, Class<T> type, String name) {
		return find(parent, type, component -> component.getAccessibleContext() != null
				&& name.equals(component.getAccessibleContext().getAccessibleName()));
	}

	/**
	 * Finds a named button in a component tree.
	 *
	 * @param parent component tree.
	 * @param text displayed button text.
	 * @return matching button.
	 */
	private static JButton button(Container parent, String text) {
		return find(parent, JButton.class, candidate -> text.equals(candidate.getText()));
	}

	/**
	 * Finds a matching descendant or fails with a useful assertion.
	 *
	 * @param parent component tree.
	 * @param type component class.
	 * @param predicate identifying predicate.
	 * @param <T> component type.
	 * @return matching component.
	 */
	private static <T extends Component> T find(Container parent, Class<T> type, Predicate<T> predicate) {
		return components(parent, type).stream().filter(predicate).findFirst()
				.orElseThrow(() -> new AssertionError("No matching " + type.getSimpleName()));
	}

	/**
	 * Collects descendants of one component type.
	 *
	 * @param parent component tree.
	 * @param type component class.
	 * @param <T> component type.
	 * @return ordered matching descendants.
	 */
	private static <T extends Component> List<T> components(Container parent, Class<T> type) {
		List<T> found = new ArrayList<>();
		for (Component child : parent.getComponents()) {
			if (type.isInstance(child)) {
				found.add(type.cast(child));
			}
			if (child instanceof Container nested) {
				found.addAll(components(nested, type));
			}
		}
		return found;
	}

	/**
	 * Reads the shared editor's currently selected classification.
	 *
	 * @param dialog shared editor window.
	 * @return selected event type.
	 */
	private static ActivityEventType selectedEventType(JDialog dialog) {
		for (JComboBox<?> combo : components(dialog, JComboBox.class)) {
			if (combo.getSelectedItem() instanceof ActivityEventType type) {
				return type;
			}
		}
		throw new AssertionError("Shared event type selector missing");
	}

	/**
	 * Finds the shared editor's editable original narrative field.
	 *
	 * @param dialog shared editor window.
	 * @return editable narrative.
	 */
	private static JTextArea narrative(JDialog dialog) {
		return find(dialog, JTextArea.class, JTextArea::isEditable);
	}

	/**
	 * Drives one actual document-modal Swing dialog and verifies it was shown.
	 *
	 * @param title expected dialog title.
	 * @param handler dialog assertion and action.
	 * @param action initiating user action.
	 */
	private static void withDialog(String title, Consumer<JDialog> handler, Runnable action) {
		AtomicInteger shown = new AtomicInteger();
		withDialogs(dialog -> {
			if (title.equals(dialog.getTitle())) {
				shown.incrementAndGet();
				handler.accept(dialog);
			}
		}, action);
		assertEquals(1, shown.get(), "Expected one " + title + " dialog");
	}

	/**
	 * Drives nested actual Swing dialogs with bounded failure cleanup.
	 *
	 * @param handler dialog assertion and action.
	 * @param action initiating user action.
	 */
	private static void withDialogs(Consumer<JDialog> handler, Runnable action) {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
		Timer timer = new Timer(75, null);
		timer.setCoalesce(false);
		timer.addActionListener(event -> {
			for (Window window : Window.getWindows()) {
				if (window instanceof JDialog dialog && dialog.isShowing() && dialog.isActive()) {
					try {
						if (System.nanoTime() > deadline) {
							throw new AssertionError("Modal test deadline exceeded: " + dialog.getTitle());
						}
						handler.accept(dialog);
					} catch (Throwable exception) {
						failure.compareAndSet(null, exception);
						dialog.dispose();
					}
				}
			}
		});
		timer.start();
		try {
			action.run();
		} finally {
			timer.stop();
		}
		if (failure.get() != null) {
			throw new AssertionError("Dialog assertion failed", failure.get());
		}
	}

	/**
	 * Collects original detail text from the shared details viewer.
	 *
	 * @param parent details tree.
	 * @return displayed detail text.
	 */
	private static String allText(Container parent) {
		List<String> text = new ArrayList<>();
		for (JTextArea area : components(parent, JTextArea.class)) {
			text.add(area.getText());
		}
		for (javax.swing.JTextPane pane : components(parent, javax.swing.JTextPane.class)) {
			text.add(pane.getText());
		}
		for (javax.swing.JLabel label : components(parent, javax.swing.JLabel.class)) {
			text.add(label.getText());
		}
		return String.join("\n", text);
	}
}
