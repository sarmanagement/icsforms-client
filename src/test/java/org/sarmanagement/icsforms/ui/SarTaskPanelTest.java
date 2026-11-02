/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.Component;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SarTaskPanelTest {

	@Test
	void failedSavePreservesDetachedUpdatesMadeDuringNestedDispositionPrompt() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppData data = sampleController().getData();
		var blockedParent = Files.createTempFile("icsforms-nested-rollback", ".file");
		AppController controller = new AppController(data,
				new LocalRepository(blockedParent.resolve("incident.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()), new IncidentValidator());
		SarTaskPanel panel = new SarTaskPanel(controller);
		panel.refreshFromModel();
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		task.setTaskLifecycleStatus("assigned - returning from assignment");
		var log = new org.sarmanagement.icsforms.model.Ics214Form();
		var activity = new org.sarmanagement.icsforms.model.ActivityLogEntry();
		log.setActivityLog(new ArrayList<>());
		data.setActivityLogs(new ArrayList<>(List.of(log)));
		data.setClueLogEntries(new ArrayList<>(data.getClueLogEntries()));
		var clue = new ClueLogEntry();
		clue.setAssignmentId(task.getAssignmentId());
		clue.setDescription("Clue logged during disposition");
		Object editor = createEditor(task, "ASSIGNMENT", 1, List.of(), controller, data.getClueLogEntries());
		Field lifecycle = editor.getClass().getDeclaredField("taskLifecycleField");
		lifecycle.setAccessible(true);
		((JComboBox<?>) lifecycle.get(editor)).setSelectedItem("returned");
		Class<?> modeClass = Class.forName(SarTaskPanel.class.getName() + "$EditorMode");
		Method save = SarTaskPanel.class.getDeclaredMethod("saveTaskEditor", int.class, SarTaskAssignment.class,
				editor.getClass(), modeClass);
		save.setAccessible(true);
		var observedPrompt = new java.util.concurrent.atomic.AtomicBoolean();
		var saveFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		SwingUtilities.invokeAndWait(() -> {
			var main = new javax.swing.JFrame("Main task document");
			main.add(panel);
			main.setSize(500, 300);
			main.setVisible(true);
			var outerEditor = new javax.swing.JDialog(main, "Assignment editor",
					java.awt.Dialog.ModalityType.DOCUMENT_MODAL);
			outerEditor.setSize(400, 250);
			var beginSave = new javax.swing.Timer(100, event -> {
				try {
					save.invoke(panel, 0, task, editor, enumConstant(modeClass, "ASSIGNMENT"));
				} catch (ReflectiveOperationException exception) {
					saveFailure.set(exception);
				} finally {
					outerEditor.dispose();
				}
			});
			var logWhilePromptOpen = new javax.swing.Timer(600, event -> {
				for (java.awt.Window window : java.awt.Window.getWindows()) {
					if (window instanceof javax.swing.JDialog dialog
							&& "Set returned resource status".equals(dialog.getTitle()) && dialog.isShowing()) {
						observedPrompt.set(true);
						log.getActivityLog().add(activity);
						data.getClueLogEntries().add(clue);
						task.setTaskLifecycleStatus("returned");
						for (Component content : dialog.getContentPane().getComponents()) {
							if (content instanceof javax.swing.JOptionPane optionPane) {
								optionPane.setValue(javax.swing.JOptionPane.OK_OPTION);
							}
						}
						dialog.dispose();
					}
				}
				outerEditor.dispose();
			});
			beginSave.setRepeats(false);
			logWhilePromptOpen.setRepeats(false);
			beginSave.start();
			logWhilePromptOpen.start();
			try {
				outerEditor.setVisible(true);
			} finally {
				beginSave.stop();
				logWhilePromptOpen.stop();
				outerEditor.dispose();
				main.dispose();
			}
		});
		assertTrue(observedPrompt.get());
		assertNotNull(saveFailure.get());
		assertSame(log, data.getActivityLogs().get(0));
		assertSame(activity, log.getActivityLog().get(0));
		assertTrue(data.getClueLogEntries().contains(clue));
		assertEquals("returned", task.getTaskLifecycleStatus());
	}

	@Test
	void failedLifecyclePersistenceRestoresStatusAndExistingLogCollections() throws Exception {
		AppData data = sampleController().getData();
		var blockedParent = Files.createTempFile("icsforms-blocked-lifecycle", ".file");
		AppController controller = new AppController(data,
				new LocalRepository(blockedParent.resolve("incident.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()), new IncidentValidator());
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		task.setTaskLifecycleStatus("planned");
		var logs = new ArrayList<>(data.getActivityLogs());
		data.setActivityLogs(logs);
		org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
				controller.persistEditorChanges(() -> controller.recordTaskLifecycleTransition(task,
						"assigned - enroute", LocalDateTime.parse("2026-01-01T02:00:00"))));
		assertEquals("planned", task.getTaskLifecycleStatus());
		assertSame(logs, data.getActivityLogs());
		assertEquals(0, logs.size());
	}

	@Test
	void failedDebriefSaveRestoresLiveIdentitiesAndRetainsEnteredText() throws Exception {
		AppData data = sampleController().getData();
		var blockedParent = Files.createTempFile("icsforms-blocked-debrief", ".file");
		AppController controller = new AppController(data,
				new LocalRepository(blockedParent.resolve("incident.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()), new IncidentValidator());
		SarTaskPanel panel = new SarTaskPanel(controller);
		panel.refreshFromModel();
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		ClueLogEntry concurrent = new ClueLogEntry();
		concurrent.setAssignmentId(task.getAssignmentId());
		concurrent.setDescription("Previously logged from detached window");
		var clues = new ArrayList<>(data.getClueLogEntries());
		clues.add(concurrent);
		data.setClueLogEntries(clues);
		var log = new org.sarmanagement.icsforms.model.Ics214Form();
		data.setActivityLogs(new ArrayList<>(List.of(log)));
		Object editor = createEditor(task, "DEBRIEFING", 1, List.of(), controller, clues);
		Field notesField = editor.getClass().getDeclaredField("debriefNotesField");
		notesField.setAccessible(true);
		var notes = (javax.swing.JTextArea) ((JScrollPane) notesField.get(editor)).getViewport().getView();
		String original = task.getDebriefNotes();
		notes.setText("Unsaved debrief retained after failure");
		Class<?> modeClass = Class.forName(SarTaskPanel.class.getName() + "$EditorMode");
		Method save = SarTaskPanel.class.getDeclaredMethod("saveTaskEditor", int.class, SarTaskAssignment.class,
				editor.getClass(), modeClass);
		save.setAccessible(true);
		org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> save.invoke(panel, 0, task, editor, enumConstant(modeClass, "DEBRIEFING")));
		assertSame(data, controller.getData());
		assertSame(task, data.getSarTaskAssignments().get(0));
		assertSame(log, data.getActivityLogs().get(0));
		assertSame(clues, data.getClueLogEntries());
		assertSame(concurrent, data.getClueLogEntries().get(1));
		assertEquals(original, task.getDebriefNotes());
		assertEquals("Unsaved debrief retained after failure", notes.getText());
		Files.delete(blockedParent);
		Files.createDirectory(blockedParent);
		save.invoke(panel, 0, task, editor, enumConstant(modeClass, "DEBRIEFING"));
		assertEquals("Unsaved debrief retained after failure", task.getDebriefNotes());
		assertEquals(2, data.getClueLogEntries().size());
	}

	@Test
	void nestedResourcePickerUsesDocumentModality() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		Method picker = SarTaskPanel.class.getDeclaredMethod("showResourcePickerDialog", Component.class,
				List.class, Set.class);
		picker.setAccessible(true);
		var modality = new java.util.concurrent.atomic.AtomicReference<java.awt.Dialog.ModalityType>();
		var correctRoot = new java.util.concurrent.atomic.AtomicBoolean();
		var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		SwingUtilities.invokeAndWait(() -> {
			var main = new javax.swing.JFrame("Main assignment document");
			var editor = new javax.swing.JDialog(main, "Open assignment editor",
					java.awt.Dialog.ModalityType.DOCUMENT_MODAL);
			main.setSize(300, 200);
			editor.setSize(300, 200);
			main.setVisible(true);
			var openPicker = new javax.swing.Timer(100, event -> {
				try {
					picker.invoke(null, editor, List.of(), Set.of());
				} catch (ReflectiveOperationException exception) {
					failure.set(exception);
				} finally {
					editor.dispose();
				}
			});
			var dismissPicker = new javax.swing.Timer(600, event -> {
				for (java.awt.Window window : java.awt.Window.getWindows()) {
					if (window instanceof javax.swing.JDialog dialog
							&& "Add Resource".equals(dialog.getTitle()) && dialog.isShowing()) {
						modality.set(dialog.getModalityType());
						correctRoot.set(main == UiSupport.documentRoot(dialog));
						dialog.dispose();
					}
				}
				editor.dispose();
			});
			openPicker.setRepeats(false);
			dismissPicker.setRepeats(false);
			openPicker.start();
			dismissPicker.start();
			try {
				editor.setVisible(true);
			} finally {
				openPicker.stop();
				dismissPicker.stop();
				editor.dispose();
				main.dispose();
			}
		});
		assertNull(failure.get());
		assertEquals(java.awt.Dialog.ModalityType.DOCUMENT_MODAL, modality.get());
		assertTrue(correctRoot.get());
	}

	@Test
	void addLogEntryPopupResolvesSortedTaskAndBoardLabelChildren() throws Exception {
		AppController controller = sampleController();
		ResourceAssignment second = new ResourceAssignment();
		second.setAssignmentTeamNumber("Z-2");
		var resources = new ArrayList<>(controller.getAssignmentResources());
		resources.add(second);
		controller.getData().getForm204().setResourcesAssigned(resources);
		SarTaskPanel panel = new SarTaskPanel(controller);
		panel.refreshFromModel();
		Field tableField = SarTaskPanel.class.getDeclaredField("table");
		tableField.setAccessible(true);
		JTable table = (JTable) tableField.get(panel);
		var logged = new java.util.concurrent.atomic.AtomicReference<SarTaskAssignment>();
		var quickLog = new java.util.concurrent.atomic.AtomicReference<SarTaskPanel.QuickLogRequest>();
		panel.setOnAddLogEntryRequest((launching, assignment) -> logged.set(assignment));
		panel.setOnQuickLogRequest((launching, request) -> quickLog.set(request));
		table.setAutoCreateRowSorter(true);
		table.getRowSorter().toggleSortOrder(1);
		table.getRowSorter().toggleSortOrder(1);
		table.setSize(900, 300);
		table.setRowSelectionInterval(1, 1);
		var popupClick = new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, 5, 5, 1, true, java.awt.event.MouseEvent.BUTTON3);
		for (var listener : table.getMouseListeners()) {
			if (listener.getClass().getEnclosingClass() == SarTaskPanel.class) {
				listener.mousePressed(popupClick);
			}
		}
		for (Component component : table.getComponentPopupMenu().getComponents()) {
			if (component instanceof JMenuItem item && "Add log entry…".equals(item.getText())) {
				item.doClick();
			}
		}
		SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(1);
		assertSame(task, logged.get());
		JMenu quickMenu = java.util.Arrays.stream(table.getComponentPopupMenu().getComponents())
				.filter(JMenu.class::isInstance).map(JMenu.class::cast)
				.filter(item -> "Quick log entry".equals(item.getText())).findFirst().orElseThrow();
		for (var listener : table.getComponentPopupMenu().getPopupMenuListeners()) {
			listener.popupMenuWillBecomeVisible(
					new javax.swing.event.PopupMenuEvent(table.getComponentPopupMenu()));
		}
		assertTrue(quickMenu.getItemCount() > 0);
		assertTrue(quickMenu.getItem(0).getText().startsWith("Log "));
		quickMenu.getItem(0).doClick();
		assertSame(task, quickLog.get().task());
		assertEquals(org.sarmanagement.icsforms.model.ActivityEventType.ID_RESOURCE_ON_TASK,
				quickLog.get().eventTypeId());
		assertTrue(quickLog.get().activity().contains("going on task"));
		Method buildCard = SarTaskPanel.class.getDeclaredMethod("buildBoardTaskCard", SarTaskAssignment.class,
				java.awt.Color.class);
		buildCard.setAccessible(true);
		JPanel card = (JPanel) buildCard.invoke(panel, task, java.awt.Color.GRAY);
		var label = (javax.swing.JComponent) card.getComponent(0);
		assertSame(card.getComponentPopupMenu(), label.getComponentPopupMenu());
		logged.set(null);
		for (Component component : label.getComponentPopupMenu().getComponents()) {
			if (component instanceof JMenuItem item && "Add log entry…".equals(item.getText())) {
				item.doClick();
			}
		}
		assertSame(task, logged.get());
		table.clearSelection();
		var click = new java.awt.event.MouseEvent(label, java.awt.event.MouseEvent.MOUSE_CLICKED,
				System.currentTimeMillis(), 0, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
		for (var listener : label.getMouseListeners()) {
			listener.mouseClicked(click);
		}
		assertEquals(0, table.getSelectedRow());
	}

	@Test
	void staleAssignmentEditorPreservesExternallyChangedLifecycle() throws Exception {
		SarTaskAssignment task = sampleTask();
		task.setTaskLifecycleStatus("Assigned");
		Object editor = createEditor(task, "ASSIGNMENT", 1);
		task.setTaskLifecycleStatus("Returned");
		Method apply = editor.getClass().getDeclaredMethod("applyTo", SarTaskAssignment.class, List.class);
		apply.setAccessible(true);
		apply.invoke(editor, task, List.of());
		assertEquals("returned", task.getTaskLifecycleStatus());
	}

	@Test
	void debriefSavePreservesConcurrentClueAndFollowUp() throws Exception {
		SarTaskAssignment task = sampleTask();
		ClueLogEntry original = new ClueLogEntry();
		original.setAssignmentId(task.getAssignmentId());
		original.setDescription("Original clue");
		Object editor = createEditor(task, "DEBRIEFING", 1, List.of(), null, List.of(original));
		original.setFollowUp("Updated externally");
		ClueLogEntry concurrent = new ClueLogEntry();
		concurrent.setAssignmentId(task.getAssignmentId());
		concurrent.setDescription("Logged while debrief open");
		task.setAssignmentTeamNumber("Externally renamed");
		Method apply = editor.getClass().getDeclaredMethod("applyTo", SarTaskAssignment.class, List.class);
		apply.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<ClueLogEntry> merged = (List<ClueLogEntry>) apply.invoke(editor, task, List.of(original, concurrent));
		assertEquals(2, merged.size());
		assertTrue(merged.contains(concurrent));
		assertEquals("Updated externally", merged.stream()
				.filter(clue -> clue.getEntryId().equals(original.getEntryId())).findFirst().orElseThrow().getFollowUp());
		assertEquals("Externally renamed", task.getAssignmentTeamNumber());
	}

	@Test
	void debriefRetryUpdatesItsOwnNewClueWithoutDuplicatingConcurrentEntries() throws Exception {
		SarTaskAssignment task = sampleTask();
		Object editor = createEditor(task, "DEBRIEFING", 1);
		Field modelField = editor.getClass().getDeclaredField("clueEntryTableModel");
		modelField.setAccessible(true);
		AbstractTableModel clues = (AbstractTableModel) modelField.get(editor);
		clues.setValueAt("First attempt", 0, 2);
		Method apply = editor.getClass().getDeclaredMethod("applyTo", SarTaskAssignment.class, List.class);
		apply.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<ClueLogEntry> first = (List<ClueLogEntry>) apply.invoke(editor, task, List.of());
		ClueLogEntry concurrent = new ClueLogEntry();
		concurrent.setAssignmentId(task.getAssignmentId());
		concurrent.setDescription("Concurrent clue");
		first.add(concurrent);
		clues.setValueAt("Corrected after save failure", 0, 2);
		@SuppressWarnings("unchecked")
		List<ClueLogEntry> retry = (List<ClueLogEntry>) apply.invoke(editor, task, first);
		assertEquals(2, retry.size());
		assertTrue(retry.contains(concurrent));
		assertTrue(retry.stream().anyMatch(clue -> "Corrected after save failure".equals(clue.getDescription())));
	}

	@Test
	void sarTaskEditorMarksRequiredAssignmentFieldAccessibly() throws Exception {
		SarTaskAssignment task = sampleTask();
		Object editor = createEditor(task, "ASSIGNMENT", 1);
		Class<?> editorClass = editor.getClass();

		Field panelField = editorClass.getDeclaredField("panel");
		panelField.setAccessible(true);
		Field assignmentField = editorClass.getDeclaredField("assignmentTeamNumberField");
		assignmentField.setAccessible(true);

		JLabel[] labelHolder = new JLabel[1];
		JTextField[] fieldHolder = new JTextField[1];
		SwingUtilities.invokeAndWait(() -> {
			fieldHolder[0] = (JTextField) getFieldValue(assignmentField, editor);
			labelHolder[0] = findLabel((Component) getFieldValue(panelField, editor), "Assignment/Team # (required)");
		});

		assertNotNull(labelHolder[0]);
		assertNotNull(fieldHolder[0]);
		assertSame(fieldHolder[0], labelHolder[0].getLabelFor());
		assertEquals(UiSupport.REQUIRED_FIELD_BACKGROUND, fieldHolder[0].getBackground());
		assertTrue(fieldHolder[0].getAccessibleContext().getAccessibleDescription().contains("required field"));
	}

	@Test
	void debriefEditorShowsDebriefFieldsWithoutAssignmentOnlyFields() throws Exception {
		Object editor = createEditor(sampleTask(), "DEBRIEFING", 1);
		Field panelField = editor.getClass().getDeclaredField("panel");
		Field assignmentSummaryField = editor.getClass().getDeclaredField("assignmentSummaryField");
		panelField.setAccessible(true);
		assignmentSummaryField.setAccessible(true);

		JLabel[] debriefLabel = new JLabel[1];
		JLabel[] reportedPodLabel = new JLabel[1];
		JLabel[] resourcesLabel = new JLabel[1];
		JLabel[] inheritedLabel = new JLabel[1];
		JLabel[] taskSetupLabel = new JLabel[1];
		JLabel[] taskSummaryLabel = new JLabel[1];
		JPanel[] summaryPanel = new JPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			Component panel = (Component) getFieldValue(panelField, editor);
			debriefLabel[0] = findLabel(panel, "Debriefing");
			reportedPodLabel[0] = findLabel(panel, "Reported POD (%)");
			resourcesLabel[0] = findLabel(panel, "Resources assigned");
			inheritedLabel[0] = findLabel(panel, "Inherited task data");
			taskSetupLabel[0] = findLabel(panel, "Task setup");
			taskSummaryLabel[0] = findLabel(panel, "Task summary");
			summaryPanel[0] = (JPanel) getFieldValue(assignmentSummaryField, editor);
		});

		assertNotNull(debriefLabel[0]);
		assertNotNull(reportedPodLabel[0]);
		assertNull(resourcesLabel[0]);
		assertNotNull(inheritedLabel[0]);
		assertEquals(1, gridY(inheritedLabel[0]));
		assertEquals(2, gridY(taskSetupLabel[0]));
		assertNull(taskSummaryLabel[0]);
		assertEquals("Resource: Team 1", ((JLabel) summaryPanel[0].getComponent(0)).getText());
		assertNull(findLabel(summaryPanel[0], "Incident: Test Incident"));
	}

	@Test
	void sarTaskGridMarksRequiredAssignmentColumn() throws Exception {
		AppController controller = sampleController();
		SarTaskPanel panel = new SarTaskPanel(controller);
		SwingUtilities.invokeAndWait(panel::refreshFromModel);

		Field tableField = SarTaskPanel.class.getDeclaredField("table");
		tableField.setAccessible(true);
		JTable table = (JTable) tableField.get(panel);

		// Column layout: 0=Task Status, 1=Team#(required), 2=Resource Type, 3=Task
		// Geometry,
		// 4=Resource, 5=Leader, 6=People, 7=Start, 8=End
		assertEquals("Task Status", table.getColumnName(0));
		assertEquals("Assignment/Team # (required)", table.getColumnName(1));
		assertEquals("Resource Type", table.getColumnName(2));
		assertEquals("Task Geometry", table.getColumnName(3));
		assertEquals("People", table.getColumnName(6));
		assertEquals(3, table.getValueAt(0, 6));
		// Col 1 (Team #) must be rendered with the required-field background.
		Component component = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
		assertEquals(UiSupport.REQUIRED_FIELD_BACKGROUND, component.getBackground());
	}

	@Test
	void sarTaskGridPopupOffersAssignmentAndDebriefingEditors() throws Exception {
		AppController controller = sampleController();
		SarTaskPanel panel = new SarTaskPanel(controller);
		SwingUtilities.invokeAndWait(panel::refreshFromModel);

		Field tableField = SarTaskPanel.class.getDeclaredField("table");
		tableField.setAccessible(true);
		JTable table = (JTable) tableField.get(panel);
		JPopupMenu menu = table.getComponentPopupMenu();

		assertNotNull(menu);
		assertEquals("Edit assignment…", ((JMenuItem) menu.getComponent(0)).getText());
		assertEquals("Debrief…", ((JMenuItem) menu.getComponent(1)).getText());
	}

	@Test
	void returnedTaskWithExistingDebriefRemainsEligibleForDebriefEditing() throws Exception {
		SarTaskAssignment task = sampleTask();
		task.setTaskLifecycleStatus("returned");
		task.setDebriefingSupervisor("Debrief Lead");
		Method method = SarTaskPanel.class.getDeclaredMethod("isReadyForDebrief", SarTaskAssignment.class);
		method.setAccessible(true);

		assertTrue((Boolean) method.invoke(null, task));
	}

	@Test
	void debriefEditorProvidesSupervisorPickerAndPersistsLinkedPersonnelId() throws Exception {
		SarTaskAssignment task = sampleTask();
		task.setDebriefingSupervisor("Legacy Name");
		TCard supervisor = new TCard();
		supervisor.setCardType(TCardType.PERSONNEL);
		supervisor.setPersonName("Debrief Lead");
		task.setDebriefingSupervisorResourceId(supervisor.getResourceId());
		Object editor = createEditor(task, "DEBRIEFING", 1, List.of(supervisor), sampleControllerWithCards(supervisor), List.of());
		Field supervisorField = editor.getClass().getDeclaredField("debriefingSupervisorField");
		supervisorField.setAccessible(true);
		Field pickButtonField = editor.getClass().getDeclaredField("pickDebriefingSupervisorButton");
		pickButtonField.setAccessible(true);
		Method applyTo = editor.getClass().getDeclaredMethod("applyTo", SarTaskAssignment.class, List.class);
		applyTo.setAccessible(true);

		JTextField[] fieldHolder = new JTextField[1];
		JButton[] buttonHolder = new JButton[1];
		SwingUtilities.invokeAndWait(() -> {
			fieldHolder[0] = (JTextField) getFieldValue(supervisorField, editor);
			buttonHolder[0] = (JButton) getFieldValue(pickButtonField, editor);
		});

		assertEquals("Debrief Lead", fieldHolder[0].getText());
		assertEquals("Pick…", buttonHolder[0].getText());
		SwingUtilities.invokeAndWait(() -> fieldHolder[0].setText("Debrief Lead"));

		applyTo.invoke(editor, task, List.of());

		assertEquals("Debrief Lead", task.getDebriefingSupervisor());
		assertEquals(supervisor.getResourceId(), task.getDebriefingSupervisorResourceId());
	}

	@Test
	void assignmentEditorUsesCompactResourceListWithoutPrimaryTaskResource() throws Exception {
		SarTaskAssignment task = sampleTask();
		List<SarTaskResource> resources = new ArrayList<>(task.getResourcesAssigned());
		SarTaskResource primary = new SarTaskResource();
		primary.setFunction("Resource");
		primary.setName(task.getResourceIdentifier());
		resources.add(primary);
		SarTaskResource medic = new SarTaskResource();
		medic.setFunction("Medic");
		medic.setName("Alex");
		resources.add(medic);
		task.setResourcesAssigned(resources);

		Object editor = createEditor(task, "ASSIGNMENT", 2);
		Field panelField = editor.getClass().getDeclaredField("panel");
		panelField.setAccessible(true);
		Field resourceModelField = editor.getClass().getDeclaredField("resourceEntryTableModel");
		resourceModelField.setAccessible(true);

		JLabel[] communicationsLabel = new JLabel[1];
		SwingUtilities
				.invokeAndWait(() -> communicationsLabel[0] = findLabel((Component) getFieldValue(panelField, editor),
						"Communications"));

		AbstractTableModel resourceModel = (AbstractTableModel) getFieldValue(resourceModelField, editor);
		assertNull(communicationsLabel[0]);
		assertEquals(2, resourceModel.getRowCount());
		assertEquals("Leader/Handler", resourceModel.getValueAt(0, 1));
		assertEquals("Leader A", resourceModel.getValueAt(0, 2));
		assertEquals("Medic", resourceModel.getValueAt(1, 1));
		assertEquals("Alex", resourceModel.getValueAt(1, 2));
		for (int rowIndex = 0; rowIndex < resourceModel.getRowCount(); rowIndex++) {
			assertNotEquals("Team 1", resourceModel.getValueAt(rowIndex, 2));
		}
	}

	@Test
	void assignmentEditorPlacesInheritedTaskDataBelowAssignmentAndShowsResourceFirst() throws Exception {
		Object editor = createEditor(sampleTask(), "ASSIGNMENT", 1);
		Field panelField = editor.getClass().getDeclaredField("panel");
		panelField.setAccessible(true);
		Field assignmentSummaryField = editor.getClass().getDeclaredField("assignmentSummaryField");
		assignmentSummaryField.setAccessible(true);

		JLabel[] inheritedLabel = new JLabel[1];
		JLabel[] taskSetupLabel = new JLabel[1];
		JPanel[] summaryPanel = new JPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			Component panel = (Component) getFieldValue(panelField, editor);
			inheritedLabel[0] = findLabel(panel, "Inherited task data");
			taskSetupLabel[0] = findLabel(panel, "Task setup");
			summaryPanel[0] = (JPanel) getFieldValue(assignmentSummaryField, editor);
		});

		assertEquals(1, gridY(inheritedLabel[0]));
		assertEquals(2, gridY(taskSetupLabel[0]));
		assertNotNull(findLabel(summaryPanel[0], "Resource: Team 1"));
		assertNull(findLabel(summaryPanel[0], "Incident: Test Incident"));
		assertEquals("Resource: Team 1", ((JLabel) summaryPanel[0].getComponent(0)).getText());
	}

	@Test
	void assignmentEditorDefaultsResourceRowsFromIcs204PersonCount() throws Exception {
		AppController controller = sampleController();
		SarTaskPanel panel = new SarTaskPanel(controller);
		Method method = SarTaskPanel.class.getDeclaredMethod("defaultResourceEditorRowCount", SarTaskAssignment.class);
		method.setAccessible(true);

		int rowCount = (int) method.invoke(panel, controller.getData().getSarTaskAssignments().get(0));

		assertEquals(3, rowCount);
	}

	@Test
	void debriefEditorUsesCompactPodFactorHeaderAndScoreWidth() throws Exception {
		Object editor = createEditor(sampleTask(), "DEBRIEFING", 1);
		Field podFactorsField = editor.getClass().getDeclaredField("podFactorsField");
		podFactorsField.setAccessible(true);
		Field podFactorEntriesField = editor.getClass().getDeclaredField("podFactorEntryFields");
		podFactorEntriesField.setAccessible(true);

		JLabel[] header = new JLabel[1];
		SwingUtilities
				.invokeAndWait(() -> header[0] = (JLabel) ((java.awt.Container) getFieldValue(podFactorsField, editor))
						.getComponent(0));

		List<?> entries = (List<?>) getFieldValue(podFactorEntriesField, editor);
		assertTrue(header[0].getText().contains("Qualitative POD Factors"));
		assertTrue(header[0].getText().contains("Factor"));
		assertEquals(2, textFieldColumns(entries.get(0), "scoreField"));
	}

	@Test
	void debriefEditorUsesGridCluesAndInlineCanineDetails() throws Exception {
		Object editor = createEditor(sampleTask(), "DEBRIEFING", 1);
		Field panelField = editor.getClass().getDeclaredField("panel");
		panelField.setAccessible(true);
		Field clueEntriesFieldField = editor.getClass().getDeclaredField("clueEntriesField");
		clueEntriesFieldField.setAccessible(true);
		Field clueTableModelField = editor.getClass().getDeclaredField("clueEntryTableModel");
		clueTableModelField.setAccessible(true);
		Field podFactorsFieldField = editor.getClass().getDeclaredField("podFactorsField");
		podFactorsFieldField.setAccessible(true);
		Field canineSearchTypeField = editor.getClass().getDeclaredField("canineSearchTypeField");
		canineSearchTypeField.setAccessible(true);
		Field canineImprintField = editor.getClass().getDeclaredField("canineImprintField");
		canineImprintField.setAccessible(true);

		JLabel[] canineHeading = new JLabel[1];
		JLabel[] resourceTypeLabel = new JLabel[1];
		JLabel[] imprintLabel = new JLabel[1];
		Component[] resourceTypeRow = new Component[1];
		Component[] sunAngleRow = new Component[1];
		Component[] cloudCoverRow = new Component[1];
		Component[] handlerCertificationRow = new Component[1];
		Component[] lightRow = new Component[1];
		SwingUtilities.invokeAndWait(() -> canineHeading[0] = findLabel((Component) getFieldValue(panelField, editor),
				"Canine assignment details"));
		SwingUtilities.invokeAndWait(() -> {
			JPanel podFactors = (JPanel) getFieldValue(podFactorsFieldField, editor);
			resourceTypeLabel[0] = findLabel(podFactors, "Canine resource type");
			imprintLabel[0] = findLabel(podFactors, "Dog imprinted on");
			resourceTypeRow[0] = childContainingLabel(podFactors, "Canine resource type");
			sunAngleRow[0] = childContainingLabel(podFactors, "Sun angle");
			cloudCoverRow[0] = childContainingLabel(podFactors, "Cloud cover");
			handlerCertificationRow[0] = childContainingLabel(podFactors, "Handler/K-9 Certification (1-5)");
			lightRow[0] = childContainingLabel(podFactors, "Light (1-5)");
		});

		AbstractTableModel clueModel = (AbstractTableModel) getFieldValue(clueTableModelField, editor);
		JPanel[] cluePanel = new JPanel[1];
		SwingUtilities.invokeAndWait(() -> cluePanel[0] = (JPanel) getFieldValue(clueEntriesFieldField, editor));
		assertNull(canineHeading[0]);
		assertNotNull(resourceTypeLabel[0]);
		assertNotNull(imprintLabel[0]);
		assertNotNull(resourceTypeRow[0]);
		assertNotNull(sunAngleRow[0]);
		assertNotNull(cloudCoverRow[0]);
		assertNotNull(handlerCertificationRow[0]);
		assertNotNull(lightRow[0]);
		assertEquals(4, ((JPanel) resourceTypeRow[0]).getComponentCount());
		assertEquals(1, gridX(resourceTypeRow[0]));
		assertEquals(1, gridX(sunAngleRow[0]));
		assertEquals(1, gridX(cloudCoverRow[0]));
		assertTrue(gridY(resourceTypeRow[0]) > gridY(handlerCertificationRow[0]));
		assertTrue(gridY(resourceTypeRow[0]) < gridY(lightRow[0]));
		assertEquals("Date/Time", clueModel.getColumnName(0));
		assertEquals("Location", clueModel.getColumnName(1));
		JScrollPane scrollPane = findScrollPane(cluePanel[0]);
		assertNotNull(scrollPane);
		int initialHeight = scrollPane.getPreferredSize().height;
		assertEquals(1, clueModel.getRowCount());
		JComboBox<?> searchTypeCombo = (JComboBox<?>) getFieldValue(canineSearchTypeField, editor);
		JComboBox<?> imprintCombo = (JComboBox<?>) getFieldValue(canineImprintField, editor);
		assertEquals(List.of("", "Wilderness air scent", "Tracking", "Trailing", "Tracking/Trailing", "HRD", "Article",
				"Patrol", "Water", "Other"), comboItems(searchTypeCombo));
		assertEquals(List.of("", "Living human", "HRD", "Both live and HRD", "Article/Track"),
				comboItems(imprintCombo));

		Method addRow = clueModel.getClass().getDeclaredMethod("addRow");
		addRow.setAccessible(true);
		SwingUtilities.invokeAndWait(() -> {
			try {
				addRow.invoke(clueModel);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
		});

		assertEquals(2, clueModel.getRowCount());
		assertTrue(scrollPane.getPreferredSize().height > initialHeight);
	}

	private static Object createEditor(SarTaskAssignment task, String modeName) throws Exception {
		return createEditor(task, modeName, 1);
	}

	private static Object createEditor(SarTaskAssignment task, String modeName, int resourceRows) throws Exception {
		return createEditor(task, modeName, resourceRows, List.of(), null, List.of());
	}

	private static Object createEditor(SarTaskAssignment task, String modeName, int resourceRows, List<TCard> availableTCards,
			AppController controller, List<ClueLogEntry> clueLogEntries) throws Exception {
		Class<?> editorClass = Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$SarTaskEditor");
		Class<?> modeClass = Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$EditorMode");
		Object mode = enumConstant(modeClass, modeName);
		Constructor<?> constructor = editorClass.getDeclaredConstructor(SarTaskAssignment.class, modeClass, List.class,
				int.class, java.util.function.Function.class, List.class, List.class, Set.class, AppController.class);
		constructor.setAccessible(true);
		List<String> availableNames = availableTCards.stream().map(TCard::getDisplayLabel).toList();
		return constructor.newInstance(task, mode, clueLogEntries, resourceRows, null, availableNames, availableTCards,
				Set.of(), controller);
	}

	private static Object enumConstant(Class<?> enumClass, String name) {
		for (Object constant : enumClass.getEnumConstants()) {
			if (name.equals(((Enum<?>) constant).name())) {
				return constant;
			}
		}
		throw new IllegalArgumentException("Missing enum constant " + name);
	}

	private static Object getFieldValue(Field field, Object instance) {
		try {
			return field.get(instance);
		} catch (IllegalAccessException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static JLabel findLabel(Component component, String text) {
		if (component instanceof JLabel label && text.equals(label.getText())) {
			return label;
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				JLabel label = findLabel(child, text);
				if (label != null) {
					return label;
				}
			}
		}
		return null;
	}

	private static JScrollPane findScrollPane(Component component) {
		if (component instanceof JScrollPane scrollPane) {
			return scrollPane;
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				JScrollPane scrollPane = findScrollPane(child);
				if (scrollPane != null) {
					return scrollPane;
				}
			}
		}
		return null;
	}

	private static Component childContainingLabel(JPanel panel, String text) {
		for (Component child : panel.getComponents()) {
			if (findLabel(child, text) != null) {
				return child;
			}
		}
		return null;
	}

	private static JButton findButton(Component component, String text) {
		if (component instanceof JButton button && text.equals(button.getText())) {
			return button;
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				JButton button = findButton(child, text);
				if (button != null) {
					return button;
				}
			}
		}
		return null;
	}

	@Test
	void inlineAddLogEntryButtonInTaskEditor() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		SarTaskAssignment task = controller.getData().getSarTaskAssignments().get(0);
		SarTaskPanel panel = new SarTaskPanel(controller);
		panel.refreshFromModel();
		var logged = new java.util.concurrent.atomic.AtomicReference<SarTaskAssignment>();
		panel.setOnAddLogEntryRequest((launching, assignment) -> logged.set(assignment));
		var buttonFound = new java.util.concurrent.atomic.AtomicBoolean();
		var buttonInline = new java.util.concurrent.atomic.AtomicBoolean();
		var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		SwingUtilities.invokeAndWait(() -> {
			var main = new javax.swing.JFrame("Main Frame");
			main.add(panel);
			main.setSize(800, 600);
			main.setVisible(true);
			var timer = new javax.swing.Timer(100, event -> {
				try {
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (window instanceof javax.swing.JDialog dialog && dialog.isVisible()
								&& dialog.getTitle().startsWith("Edit SAR Task")) {
							JButton logButton = findButton(dialog, "Add log entry…");
							if (logButton != null) {
								buttonFound.set(true);
								JButton saveButton = findButton(dialog, "Save");
								if (saveButton != null && logButton.getParent() == saveButton.getParent()) {
									buttonInline.set(true);
								}
								logButton.doClick();
								dialog.dispose();
								return;
							}
						}
					}
				} catch (Throwable exception) {
					failure.set(exception);
				}
			});
			timer.setRepeats(true);
			timer.start();
			try {
				panel.openAssignmentEditorById(task.getAssignmentId());
			} finally {
				timer.stop();
				main.dispose();
			}
		});
		assertNull(failure.get());
		assertTrue(buttonFound.get(), "Log button should be found in dialog");
		assertTrue(buttonInline.get(), "Log button should be inline with Save button");
		assertSame(task, logged.get());
	}

	@Test
	void existingResourcesOnTaskAndIsCardOnThisTask() throws Exception {
		SarTaskAssignment task = sampleTask();
		task.setResourceIdentifier("Primary Team");
		task.setLeader("Leader Alice");

		Method existingMethod = SarTaskPanel.class.getDeclaredMethod("existingResourcesOnTask",
				SarTaskAssignment.class,
				Class.forName("org.sarmanagement.icsforms.ui.SarTaskPanel$ResourceEntriesTableModel"));
		existingMethod.setAccessible(true);

		@SuppressWarnings("unchecked")
		List<SarTaskResource> existing = (List<SarTaskResource>) existingMethod.invoke(null, task, null);
		List<String> names = existing.stream().map(SarTaskResource::getName).toList();
		assertTrue(names.contains("Primary Team"));
		assertTrue(names.contains("Leader Alice"));

		Method isCardOnTaskMethod = SarTaskPanel.class.getDeclaredMethod("isCardOnThisTask",
				TCard.class, Set.class, Set.class);
		isCardOnTaskMethod.setAccessible(true);

		TCard cardById = new TCard();
		cardById.setResourceId("id-123");
		cardById.setPersonName("Someone");

		TCard cardByName = new TCard();
		cardByName.setResourceId("id-999");
		cardByName.setPersonName("Leader Alice");

		TCard cardOther = new TCard();
		cardOther.setResourceId("id-456");
		cardOther.setPersonName("Bob");

		Set<String> taskIds = Set.of("id-123");
		Set<String> taskNames = Set.of("leader alice", "primary team");

		assertTrue((Boolean) isCardOnTaskMethod.invoke(null, cardById, taskIds, taskNames));
		assertTrue((Boolean) isCardOnTaskMethod.invoke(null, cardByName, taskIds, taskNames));
		assertFalse((Boolean) isCardOnTaskMethod.invoke(null, cardOther, taskIds, taskNames));
	}

	@Test
	void resourceEntriesTableModelPreventsDuplicateRows() throws Exception {
		Object editor = createEditor(sampleTask(), "ASSIGNMENT", 1);
		Field modelField = editor.getClass().getDeclaredField("resourceEntryTableModel");
		modelField.setAccessible(true);
		Object model = modelField.get(editor);

		Method addRow = model.getClass().getDeclaredMethod("addRow", SarTaskResource.class);
		addRow.setAccessible(true);
		Method getRows = model.getClass().getDeclaredMethod("getRows");
		getRows.setAccessible(true);

		SarTaskResource res1 = new SarTaskResource();
		res1.setResourceId("res-1");
		res1.setName("Alice");
		res1.setFunction("Searcher");

		SarTaskResource dupById = new SarTaskResource();
		dupById.setResourceId("res-1");
		dupById.setName("Alice Clone");
		dupById.setFunction("Other");

		SarTaskResource dupByName = new SarTaskResource();
		dupByName.setResourceId("res-2");
		dupByName.setName("Alice");
		dupByName.setFunction("Other");

		SarTaskResource res2 = new SarTaskResource();
		res2.setResourceId("res-3");
		res2.setName("Bob");
		res2.setFunction("Tracker");

		addRow.invoke(model, res1);
		addRow.invoke(model, dupById);
		addRow.invoke(model, dupByName);
		addRow.invoke(model, res2);

		@SuppressWarnings("unchecked")
		List<SarTaskResource> rows = (List<SarTaskResource>) getRows.invoke(model);
		long aliceCount = rows.stream().filter(r -> "Alice".equalsIgnoreCase(r.getName())).count();
		long bobCount = rows.stream().filter(r -> "Bob".equalsIgnoreCase(r.getName())).count();
		assertEquals(1, aliceCount, "Alice should only appear once");
		assertEquals(1, bobCount, "Bob should appear once");
	}

	@Test
	void assignmentValidationErrorsDetectsDuplicates() throws Exception {
		SarTaskAssignment task = sampleTask();
		Object editor = createEditor(task, "ASSIGNMENT", 1);
		Field modelField = editor.getClass().getDeclaredField("resourceEntryTableModel");
		modelField.setAccessible(true);
		Object model = modelField.get(editor);

		Field rowsField = model.getClass().getDeclaredField("rows");
		rowsField.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<SarTaskResource> rows = (List<SarTaskResource>) rowsField.get(model);
		rows.clear();

		SarTaskResource res1 = new SarTaskResource();
		res1.setResourceId("id-dup");
		res1.setName("Duplicate Person");
		SarTaskResource res2 = new SarTaskResource();
		res2.setResourceId("id-dup");
		res2.setName("Duplicate Person");
		rows.add(res1);
		rows.add(res2);

		Method validationErrors = editor.getClass().getDeclaredMethod("assignmentValidationErrors");
		validationErrors.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) validationErrors.invoke(editor);
		assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate resource assigned to this task")),
				"Expected duplicate validation error");
	}

	@Test
	void resourcePickerDialogExcludesAndDisallowsDuplicatesOnThisTask() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		Method pickerMethod = SarTaskPanel.class.getDeclaredMethod("showResourcePickerDialog", Component.class,
				List.class, Set.class, List.class);
		pickerMethod.setAccessible(true);

		TCard cardOnThisTask = new TCard();
		cardOnThisTask.setResourceId("card-this-task");
		cardOnThisTask.setCardType(TCardType.PERSONNEL);
		cardOnThisTask.setPersonName("Member On Task");

		TCard cardOnOtherTask = new TCard();
		cardOnOtherTask.setResourceId("card-other-task");
		cardOnOtherTask.setCardType(TCardType.PERSONNEL);
		cardOnOtherTask.setPersonName("Member Other Task");

		TCard cardAvailable = new TCard();
		cardAvailable.setResourceId("card-available");
		cardAvailable.setCardType(TCardType.PERSONNEL);
		cardAvailable.setPersonName("Available Member");

		List<TCard> allCards = List.of(cardOnThisTask, cardOnOtherTask, cardAvailable);
		Set<String> onOtherTaskIds = Set.of("card-other-task");
		SarTaskResource existingOnTask = new SarTaskResource();
		existingOnTask.setResourceId("card-this-task");
		existingOnTask.setName("Member On Task");
		List<SarTaskResource> existingTaskResources = List.of(existingOnTask);

		var defaultRowCount = new java.util.concurrent.atomic.AtomicInteger();
		var showAllRowCount = new java.util.concurrent.atomic.AtomicInteger();
		var taskRowSelectable = new java.util.concurrent.atomic.AtomicBoolean();
		var otherTaskRowSelectable = new java.util.concurrent.atomic.AtomicBoolean();
		var finished = new java.util.concurrent.atomic.AtomicBoolean();
		var latch = new java.util.concurrent.CountDownLatch(1);

		SwingUtilities.invokeAndWait(() -> {
			var main = new javax.swing.JFrame("Test Parent");
			main.setSize(400, 300);
			main.setVisible(true);

			var openPicker = new javax.swing.Timer(100, event -> {
				try {
					pickerMethod.invoke(null, main, allCards, onOtherTaskIds, existingTaskResources);
				} catch (ReflectiveOperationException exception) {
					// Disposed
				} finally {
					finished.set(true);
				}
			});
			openPicker.setRepeats(false);

			var inspectAndDismiss = new javax.swing.Timer(500, event -> {
				try {
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (window instanceof javax.swing.JDialog dialog
								&& "Add Resource".equals(dialog.getTitle()) && dialog.isShowing()) {
							JTable table = findComponent(dialog, JTable.class);
							JCheckBox showAll = findComponent(dialog, JCheckBox.class);
							if (table != null && showAll != null) {
								defaultRowCount.set(table.getRowCount());
								showAll.doClick();
								showAllRowCount.set(table.getRowCount());

								// Locate rows in show-all view
								int thisTaskRow = -1;
								int otherTaskRow = -1;
								for (int r = 0; r < table.getRowCount(); r++) {
									String val = String.valueOf(table.getValueAt(r, 1));
									if ("Member On Task".equals(val)) {
										thisTaskRow = r;
									} else if ("Member Other Task".equals(val)) {
										otherTaskRow = r;
									}
								}
								if (thisTaskRow >= 0) {
									table.setRowSelectionInterval(thisTaskRow, thisTaskRow);
									taskRowSelectable.set(table.isRowSelected(thisTaskRow));
								}
								if (otherTaskRow >= 0) {
									table.setRowSelectionInterval(otherTaskRow, otherTaskRow);
									otherTaskRowSelectable.set(table.isRowSelected(otherTaskRow));
								}
							}
							dialog.dispose();
						}
					}
					main.dispose();
				} finally {
					latch.countDown();
				}
			});
			inspectAndDismiss.setRepeats(false);

			openPicker.start();
			inspectAndDismiss.start();
		});

		assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "Picker test timed out");
		assertEquals(1, defaultRowCount.get(), "Default view should only show available card");
		assertEquals(3, showAllRowCount.get(), "Show all view should show all 3 cards");
		assertFalse(taskRowSelectable.get(), "Card on this task should not be selectable");
		assertTrue(otherTaskRowSelectable.get(), "Card on other task should be selectable");
	}

	/**
	 * Finds a component of the specified class within a container hierarchy.
	 *
	 * @param <T> component type.
	 * @param component root component to search.
	 * @param targetClass target component class.
	 * @return matching component, or {@code null} if not found.
	 */
	private static <T extends Component> T findComponent(Component component, Class<T> targetClass) {
		if (targetClass.isInstance(component)) {
			return targetClass.cast(component);
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				T found = findComponent(child, targetClass);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static int gridX(Component component) {
		return gridBagConstraints(component).gridx;
	}

	private static int gridY(Component component) {
		return gridBagConstraints(component).gridy;
	}

	private static java.awt.GridBagConstraints gridBagConstraints(Component component) {
		return ((java.awt.GridBagLayout) component.getParent().getLayout()).getConstraints(component);
	}

	private static List<String> comboItems(JComboBox<?> comboBox) {
		return IntStream.range(0, comboBox.getItemCount()).mapToObj(index -> String.valueOf(comboBox.getItemAt(index)))
				.toList();
	}

	private static SarTaskAssignment sampleTask() {
		ResourceAssignment resource = new ResourceAssignment();
		resource.setAssignmentId("assign-1");
		resource.setAssignmentTeamNumber("A-1");
		resource.setResourceType(SarTaskSupport.RESOURCE_TYPE_CANINE);
		resource.setTaskType(SarTaskSupport.TASK_TYPE_AREA);
		resource.setResourceIdentifier("Team 1");
		resource.setLeaderRole("Leader/Handler");
		resource.setLeader("Leader A");
		resource.setContact("555-0101");
		resource.setAssignment("Search trail segment");
		resource.setSpecialEquipment("ATV");
		resource.setSupplies("Medical kit");

		CommunicationEntry communicationEntry = new CommunicationEntry();
		communicationEntry.setName("Team 1 Lead");
		communicationEntry.setFunction("Medical");
		communicationEntry.setPrimaryContact("Tac 1");

		IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"),
				LocalDateTime.parse("2026-01-01T12:00:00"), "Planner", "Planning Section Chief");
		context.setTaskMap("Map-42");

		Ics204Form form204 = new Ics204Form();
		form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
		form204.setDivision("Division A");
		form204.setOperationsSectionChiefName("Ops Chief");
		form204.setOperationsSectionChiefContact("555-0199");
		form204.setDivisionGroupSupervisorName("Supervisor");
		form204.setDivisionGroupSupervisorContact("Tac 2");
		form204.setCommunications(List.of(communicationEntry));
		form204.setSharedWorkAssignment("Shared assignment");
		form204.setSpecialInstructions("Maintain radio discipline");
		form204.setPreparedDateTime(LocalDateTime.parse("2026-01-01T02:00:00"));

		SarTaskAssignment task = SarTaskAssignment.fromResourceAssignment(resource, context, form204);
		task.setReportedPod("60");
		List<PodFactorRating> ratings = SarTaskSupport.factorRatings(task.getResourceType(), List.of());
		task.setQualitativePodFactors(ratings);
		return task;
	}

	private static int textFieldColumns(Object instance, String fieldName) {
		try {
			Field field = instance.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return ((JTextField) field.get(instance)).getColumns();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static AppController sampleController() throws Exception {
		return sampleControllerWithCards();
	}

	private static AppController sampleControllerWithCards(TCard... cards) throws Exception {
		SarTaskAssignment task = sampleTask();
		ResourceAssignment resource = new ResourceAssignment();
		resource.setAssignmentId(task.getAssignmentId());
		resource.setAssignmentTeamNumber(task.getAssignmentTeamNumber());
		resource.setResourceType(task.getResourceType());
		resource.setTaskType(task.getTaskType());
		resource.setResourceIdentifier(task.getResourceIdentifier());
		resource.setLeaderRole(task.getLeaderRole());
		resource.setLeader(task.getLeader());
		resource.setNumberOfPersons(3);
		resource.setContact(task.getContact());
		resource.setAssignment(task.getAssignment());

		Ics204Form form204 = new Ics204Form();
		form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
		form204.setDivision(task.getDivision());
		form204.setOperationsSectionChiefName(task.getOperationsSectionChiefName());
		form204.setOperationsSectionChiefContact(task.getOperationsSectionChiefContact());
		form204.setDivisionGroupSupervisorName(task.getSecondaryManagementName());
		form204.setDivisionGroupSupervisorContact(task.getSecondaryManagementContact());
		form204.setResourcesAssigned(List.of(resource));
		form204.setCommunications(task.getCommunications());
		form204.setSharedWorkAssignment(task.getAssignment());

		IncidentContext context = new IncidentContext(task.getIncidentName(),
				LocalDateTime.parse("2026-01-01T00:00:00"), LocalDateTime.parse("2026-01-01T12:00:00"), "Planner",
				"Planning Section Chief");
		context.setTaskMap(task.getTaskMap());

		AppData data = new AppData(context, new Ics202Form(), form204, List.of(task));
		data.setTCards(List.of(cards));
		ClueLogEntry clue = new ClueLogEntry();
		clue.setAssignmentId(task.getAssignmentId());
		clue.setDetectingTask(task.getAssignmentTeamNumber());
		clue.setDescription("Footprint");
		data.setClueLogEntries(List.of(clue));
		return new AppController(data,
				new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()),
				new IncidentValidator());
	}
}
