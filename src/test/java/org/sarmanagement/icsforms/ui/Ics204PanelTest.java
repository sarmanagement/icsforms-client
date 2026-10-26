/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics204PanelTest {
	private static final int COMPONENTS_PER_COMPACT_CONTACT_ROW = 4;

	@Test
	void supportingObjectivesAreBlankUnlessLinkedAndAppearLastWithExplicitActions() throws Exception {
		AppController controller = sampleController();
		IncidentObjective objective = new IncidentObjective("Support this", 0);
		controller.getData().getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment assignment = controller.getAssignmentResources().get(0);
		Object editor = assignmentEditor(assignment, controller);
		var list = (javax.swing.JList<?>) fieldValue(editor, "objectivesField");
		assertEquals(0, list.getModel().getSize());
		JPanel panel = (JPanel) fieldValue(editor, "panel");
		assertNotNull(findButton(panel, "Add from list…"));
		assertNotNull(findButton(panel, "Remove"));
		var layout = (java.awt.GridBagLayout) panel.getLayout();
		int lastRow = java.util.Arrays.stream(panel.getComponents())
				.filter(component -> component instanceof javax.swing.JLabel)
				.mapToInt(component -> layout.getConstraints(component).gridy).max().orElseThrow();
		assertTrue(java.util.Arrays.stream(panel.getComponents())
				.anyMatch(component -> component instanceof javax.swing.JLabel label
						&& "Supporting Incident Objective(s)".equals(label.getText())
						&& layout.getConstraints(component).gridy == lastRow));
		assignment.setObjectiveIds(List.of(objective.getObjectiveId()));
		editor = assignmentEditor(assignment, controller);
		list = (javax.swing.JList<?>) fieldValue(editor, "objectivesField");
		assertEquals(1, list.getModel().getSize());
		assertTrue(list.getModel().getElementAt(0).toString().contains("Support this"));
	}

	@Test
	void objectiveDraftSupportsZeroManyDuplicatePreventionAndRemoveWithoutMutatingUntilSave() throws Exception {
		AppController controller = sampleController();
		IncidentObjective first = new IncidentObjective("First", 0);
		IncidentObjective second = new IncidentObjective("Second", 1);
		controller.getData().getForm202().setStructuredObjectives(List.of(first, second));
		ResourceAssignment assignment = controller.getAssignmentResources().get(0);
		Object editor = assignmentEditor(assignment, controller);
		var add = editor.getClass().getDeclaredMethod("addObjectives", List.class);
		add.setAccessible(true);
		add.invoke(editor, List.of());
		add.invoke(editor, List.of(first, second, first));
		assertTrue(assignment.getObjectiveIds().isEmpty());
		var list = (javax.swing.JList<?>) fieldValue(editor, "objectivesField");
		assertEquals(2, list.getModel().getSize());
		saveEditor(new Ics204Panel(controller), assignment, editor);
		assertEquals(List.of(first.getObjectiveId(), second.getObjectiveId()), assignment.getObjectiveIds());
		list.setSelectionInterval(0, 1);
		var remove = editor.getClass().getDeclaredMethod("removeSelectedObjectives");
		remove.setAccessible(true);
		remove.invoke(editor);
		assertEquals(2, assignment.getObjectiveIds().size(), "Removing from a canceled draft cannot alter links");
		saveEditor(new Ics204Panel(controller), assignment, editor);
		assertTrue(assignment.getObjectiveIds().isEmpty());
	}

	@Test
	void assignmentSaveKeepsStableObjectiveLinksAcrossReorderAndDoesNotResurrectDeletedLinks() throws Exception {
		AppController controller = sampleController();
		IncidentObjective first = new IncidentObjective("First", 0);
		IncidentObjective second = new IncidentObjective("Second", 1);
		controller.getData().getForm202().setStructuredObjectives(List.of(first, second));
		ResourceAssignment assignment = controller.getAssignmentResources().get(0);
		assignment.setObjectiveIds(List.of(first.getObjectiveId(), second.getObjectiveId(), "imported-unavailable"));
		Object editor = assignmentEditor(assignment, controller);
		var linked = (javax.swing.JList<?>) fieldValue(editor, "objectivesField");
		assertEquals("Unavailable objective (imported-unavailable)", linked.getModel().getElementAt(2));
		controller.getData().getForm202().setStructuredObjectives(List.of(second, first));
		saveEditor(new Ics204Panel(controller), assignment, editor);
		assertEquals(List.of(first.getObjectiveId(), second.getObjectiveId(), "imported-unavailable"),
				assignment.getObjectiveIds());
		controller.getData().removeObjective(first.getObjectiveId(), "", true);
		saveEditor(new Ics204Panel(controller), assignment, editor);
		assertEquals(List.of(second.getObjectiveId(), "imported-unavailable"), assignment.getObjectiveIds());
	}

	@Test
	void staleAssignmentEditorCannotSaveIntoAnotherIncident() throws Exception {
		AppController controller = sampleController();
		ResourceAssignment assignment = controller.getAssignmentResources().get(0);
		Object editor = assignmentEditor(assignment, controller);
		((javax.swing.JTextField) fieldValue(editor, "assignmentTeamNumberField")).setText("Stale edit");
		controller.setData(new AppData());
		assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> saveEditor(new Ics204Panel(controller), assignment, editor));
		assertEquals("A-1", assignment.getAssignmentTeamNumber());
		assertTrue(controller.getAssignmentResources().isEmpty());
	}

	@Test
	void supportingObjectivePickerCancelAddAndStaleGuard() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		controller.getData().getForm202().setObjectives(List.of("First", "Second"));
		ResourceAssignment assignment = controller.getAssignmentResources().get(0);
		SwingUtilities.invokeAndWait(() -> {
			try {
				Object editor = assignmentEditor(assignment, controller);
				var panel = (JPanel) fieldValue(editor, "panel");
				runObjectivePicker(panel, dialog -> {
					findList(dialog).setSelectionInterval(0, 1);
					findButton(dialog, "Cancel").doClick();
				});
				var linked = (javax.swing.JList<?>) fieldValue(editor, "objectivesField");
				assertEquals(0, linked.getModel().getSize());
				runObjectivePicker(panel, dialog -> {
					findList(dialog).setSelectionInterval(0, 1);
					findButton(dialog, "Add").doClick();
				});
				assertEquals(2, linked.getModel().getSize());
				assertTrue(assignment.getObjectiveIds().isEmpty());
				linked.setSelectionInterval(0, 1);
				findButton(panel, "Remove").doClick();
				assertEquals(0, linked.getModel().getSize());
				runObjectivePicker(panel, dialog -> {
					findList(dialog).setSelectionInterval(0, 1);
					controller.setData(new AppData());
					findButton(dialog, "Add").doClick();
				});
				assertEquals(0, linked.getModel().getSize());
				assertTrue(assignment.getObjectiveIds().isEmpty());
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		});
	}

	@Test
	void actualSortedAssignmentEditorCancelPreservesLinksAndSavePersistsMultiplePickerSelections() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		IncidentObjective first = new IncidentObjective("First", 0);
		IncidentObjective second = new IncidentObjective("Second", 1);
		IncidentObjective third = new IncidentObjective("Third", 2);
		controller.getData().getForm202().setStructuredObjectives(List.of(first, second, third));
		ResourceAssignment original = controller.getAssignmentResources().get(0);
		ResourceAssignment target = new ResourceAssignment();
		target.setAssignmentTeamNumber("Z-2");
		target.setObjectiveIds(List.of(first.getObjectiveId()));
		controller.getData().getForm204().setResourcesAssigned(new java.util.ArrayList<>(List.of(original, target)));
		SwingUtilities.invokeAndWait(() -> {
			Ics204Panel panel = new Ics204Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "resourceTable");
			table.setAutoCreateRowSorter(true);
			table.getRowSorter().toggleSortOrder(0);
			table.getRowSorter().toggleSortOrder(0);
			assertEquals("Z-2", table.getValueAt(0, 0));
			table.setRowSelectionInterval(0, 0);
			Runnable launch = () -> ((JMenuItem) table.getComponentPopupMenu().getComponent(1)).doClick();
			runDialog(launch, "Edit assignment Z-2", dialog -> {
				javax.swing.JList<?> linked = findList(dialog);
				assertEquals(1, linked.getModel().getSize());
				linked.setSelectedIndex(0);
				findButton(dialog, "Remove").doClick();
				runObjectivePicker(dialog, picker -> {
					findList(picker).setSelectionInterval(1, 2);
					findButton(picker, "Add").doClick();
				});
				assertEquals(2, linked.getModel().getSize());
				assertEquals(List.of(first.getObjectiveId()), target.getObjectiveIds());
				findButton(dialog, "Cancel").doClick();
			});
			assertEquals(List.of(first.getObjectiveId()), target.getObjectiveIds());
			assertTrue(original.getObjectiveIds().isEmpty());
			runDialog(launch, "Edit assignment Z-2", dialog -> {
				javax.swing.JList<?> linked = findList(dialog);
				linked.setSelectedIndex(0);
				findButton(dialog, "Remove").doClick();
				runObjectivePicker(dialog, picker -> {
					findList(picker).setSelectionInterval(1, 2);
					findButton(picker, "Add").doClick();
				});
				findButton(dialog, "Save").doClick();
			});
			assertEquals(List.of(second.getObjectiveId(), third.getObjectiveId()), target.getObjectiveIds());
			assertTrue(original.getObjectiveIds().isEmpty());
		});
	}

	private static Object assignmentEditor(ResourceAssignment row, AppController controller)
			throws ReflectiveOperationException {
		Class<?> type = Class.forName(Ics204Panel.class.getName() + "$ResourceAssignmentEditor");
		var constructor = type.getDeclaredConstructor(ResourceAssignment.class, AppController.class);
		constructor.setAccessible(true);
		return constructor.newInstance(row, controller);
	}

	private static void saveEditor(Ics204Panel panel, ResourceAssignment row, Object editor)
			throws ReflectiveOperationException {
		var save = Ics204Panel.class.getDeclaredMethod("saveAssignmentEditor", ResourceAssignment.class,
				editor.getClass());
		save.setAccessible(true);
		save.invoke(panel, row, editor);
	}

	private static void runObjectivePicker(Component panel, java.util.function.Consumer<javax.swing.JDialog> action) {
		runDialog(() -> findButton(panel, "Add from list…").doClick(), "Add supporting incident objectives", action);
	}

	private static void runDialog(Runnable launch, String title,
			java.util.function.Consumer<javax.swing.JDialog> action) {
		var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		var found = new java.util.concurrent.atomic.AtomicBoolean();
		var timer = new javax.swing.Timer(80, event -> {
			for (java.awt.Window window : java.awt.Window.getWindows()) {
				if (window instanceof javax.swing.JDialog dialog && dialog.isVisible()
						&& title.equals(dialog.getTitle())) {
					((javax.swing.Timer) event.getSource()).stop();
					found.set(true);
					try {
						action.accept(dialog);
					} catch (Throwable exception) {
						failure.set(exception);
						dialog.dispose();
					}
				}
			}
		});
		timer.start();
		try {
			launch.run();
		} finally {
			timer.stop();
		}
		assertTrue(found.get());
		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}
	}

	private static javax.swing.JList<?> findList(Component component) {
		if (component instanceof javax.swing.JList<?> list) {
			return list;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				var list = findList(child);
				if (list != null) {
					return list;
				}
			}
		}
		return null;
	}

	@Test
	void staleSarPushPreservesNewLoggedTaskIdentityStatusTimeAndInlineEdits() throws Exception {
		AppController controller = sampleController();
		SarTaskPanel staleSarPanel = new SarTaskPanel(controller);
		staleSarPanel.refreshFromModel();
		Ics204Panel assignmentPanel = new Ics204Panel(controller);
		ResourceAssignment added = new ResourceAssignment();
		Class<?> editorType = Class.forName(Ics204Panel.class.getName() + "$ResourceAssignmentEditor");
		var constructor = editorType.getDeclaredConstructor(ResourceAssignment.class, AppController.class);
		constructor.setAccessible(true);
		Object editor = constructor.newInstance(added, controller);
		((javax.swing.JTextField) fieldValue(editor, "assignmentTeamNumberField")).setText("NEW-2");
		var save = Ics204Panel.class.getDeclaredMethod("saveAssignmentEditor", ResourceAssignment.class, editorType);
		save.setAccessible(true);
		save.invoke(assignmentPanel, added, editor);
		var canonical = controller.getData().getSarTaskAssignments().get(1);
		String taskId = canonical.getTaskId();
		canonical.setTaskLifecycleStatus("assigned - enroute to assignment");
		var log = new org.sarmanagement.icsforms.model.Ics214Form();
		log.setLinkedSarTaskAssignmentId(added.getAssignmentId());
		var entry = new org.sarmanagement.icsforms.model.ActivityLogEntry();
		entry.setEventTypeId(org.sarmanagement.icsforms.model.ActivityEventType.ID_RESOURCE_ON_TASK);
		LocalDateTime start = LocalDateTime.parse("2026-01-01T03:15:00");
		entry.setTimestamp(start);
		controller.applyTaskLifecycleFromLogEntry(log, entry);
		JTable staleTable = (JTable) fieldValue(staleSarPanel, "table");
		org.junit.jupiter.api.Assertions.assertTrue(staleTable.editCellAt(0, 1));
		((javax.swing.JTextField) staleTable.getEditorComponent()).setText("Pending inline edit");
		staleSarPanel.pushToModel();
		org.junit.jupiter.api.Assertions.assertSame(canonical, controller.getData().getSarTaskAssignments().get(1));
		assertEquals(taskId, canonical.getTaskId());
		assertEquals("assigned - on task", canonical.getTaskLifecycleStatus());
		assertEquals(start, canonical.getAssignmentStart());
		assertEquals("Pending inline edit", controller.getData().getForm204().getResourcesAssigned().get(0)
				.getAssignmentTeamNumber());
		controller.getData().getForm204().setResourcesAssigned(List.of(added));
		controller.syncSarTasks();
		staleSarPanel.pushToModel();
		assertEquals(1, controller.getData().getSarTaskAssignments().size());
		assertEquals(taskId, controller.getData().getSarTaskAssignments().get(0).getTaskId());
		assertEquals(start, controller.getData().getSarTaskAssignments().get(0).getAssignmentStart());
	}

	@Test
	void addLogEntryPopupTargetsRightClickedSortedResource() throws Exception {
		AppController controller = sampleController();
		ResourceAssignment second = new ResourceAssignment();
		second.setAssignmentTeamNumber("Z-2");
		var resources = new java.util.ArrayList<>(controller.getAssignmentResources());
		resources.add(second);
		controller.getData().getForm204().setResourcesAssigned(resources);
		Ics204Panel panel = new Ics204Panel(controller);
		panel.refreshFromModel();
		JTable table = (JTable) fieldValue(panel, "resourceTable");
		java.util.concurrent.atomic.AtomicReference<ResourceAssignment> logged = new java.util.concurrent.atomic.AtomicReference<>();
		panel.setOnAddLogEntryRequest((launching, assignment) -> {
			org.junit.jupiter.api.Assertions.assertSame(table, launching);
			logged.set(assignment);
		});
		table.setAutoCreateRowSorter(true);
		table.getRowSorter().toggleSortOrder(0);
		table.getRowSorter().toggleSortOrder(0);
		table.setSize(900, 300);
		table.setRowSelectionInterval(1, 1);
		var click = new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, 5, 5, 1, true, java.awt.event.MouseEvent.BUTTON3);
		for (var listener : table.getMouseListeners()) {
			if (listener.getClass().getEnclosingClass() == Ics204Panel.class) {
				listener.mousePressed(click);
			}
		}
		for (var component : table.getComponentPopupMenu().getComponents()) {
			if (component instanceof JMenuItem item && "Add log entry…".equals(item.getText())) {
				item.doClick();
			}
		}
		org.junit.jupiter.api.Assertions.assertSame(second, logged.get());
	}

	@Test
	void assignmentSaveValidatesPersistsAndDoesNotDuplicateOnRetry() throws Exception {
		LocalRepository repository = new LocalRepository(
				Files.createTempDirectory("icsforms-assignment-save").resolve("incident.json"));
		AppController controller = sampleController(repository);
		Ics204Panel panel = new Ics204Panel(controller);
		panel.refreshFromModel();
		ResourceAssignment row = new ResourceAssignment();
		Class<?> editorType = Class.forName(Ics204Panel.class.getName() + "$ResourceAssignmentEditor");
		var constructor = editorType.getDeclaredConstructor(ResourceAssignment.class, AppController.class);
		constructor.setAccessible(true);
		Object editor = constructor.newInstance(row, controller);
		var save = Ics204Panel.class.getDeclaredMethod("saveAssignmentEditor", ResourceAssignment.class, editorType);
		save.setAccessible(true);
		assertThrows(java.lang.reflect.InvocationTargetException.class, () -> save.invoke(panel, row, editor));
		assertEquals(1, controller.getAssignmentResources().size());
		((javax.swing.JTextField) fieldValue(editor, "assignmentTeamNumberField")).setText("A-2");
		save.invoke(panel, row, editor);
		save.invoke(panel, row, editor);
		assertEquals(2, controller.getAssignmentResources().size());
		assertEquals(2, controller.getData().getSarTaskAssignments().size());
		assertEquals(2, repository.loadOrDefault().getSarTaskAssignments().size());
		assertEquals(false, controller.isDirty());
	}

	@Test
	void additionalFormsSyncByStableIdentityWithoutLosingDebrief() throws Exception {
		AppController controller = sampleController();
		Ics204Form additional = new Ics204Form();
		ResourceAssignment extra = new ResourceAssignment();
		extra.setAssignmentTeamNumber("B-1");
		additional.setResourcesAssigned(List.of(extra));
		controller.getData().setAdditionalForms204(List.of(additional));
		controller.syncSarTasks();
		var task = controller.getData().getSarTaskAssignments().get(1);
		task.setDebriefNotes("Retain this debrief");
		task.setAssignmentTeamNumber("B-2");
		controller.syncIcs204ResourcesFromSarTasks();
		controller.syncSarTasks();
		controller.syncSarTasks();
		assertEquals(2, controller.getData().getSarTaskAssignments().size());
		assertEquals("B-2", extra.getAssignmentTeamNumber());
		assertEquals("Retain this debrief", controller.getData().getSarTaskAssignments().get(1).getDebriefNotes());
		Ics204Form duplicateForm = new Ics204Form();
		duplicateForm.setResourcesAssigned(List.of(extra));
		controller.getData().setAdditionalForms204(List.of(additional, duplicateForm));
		controller.syncSarTasks();
		assertEquals(2, controller.getData().getSarTaskAssignments().size());
		Ics204Panel panel = new Ics204Panel(controller);
		Class<?> editorType = Class.forName(Ics204Panel.class.getName() + "$ResourceAssignmentEditor");
		var constructor = editorType.getDeclaredConstructor(ResourceAssignment.class, AppController.class);
		constructor.setAccessible(true);
		Object editor = constructor.newInstance(extra, controller);
		var assignmentArea = (javax.swing.JTextArea) ((javax.swing.JScrollPane) fieldValue(editor, "assignmentField"))
				.getViewport().getView();
		assignmentArea.setText("Revised additional-form instructions");
		var save = Ics204Panel.class.getDeclaredMethod("saveAssignmentEditor", ResourceAssignment.class, editorType);
		save.setAccessible(true);
		save.invoke(panel, extra, editor);
		assertEquals("Revised additional-form instructions", task.getAssignment());
		assertEquals("Retain this debrief", task.getDebriefNotes());
		assertEquals(1, controller.getData().getForm204().getResourcesAssigned().size());
	}

	@Test
	void failedPersistenceRollsBackInsertionRetainsFieldsAndAllowsRetry() throws Exception {
		var blockedParent = Files.createTempFile("icsforms-blocked-parent", ".file");
		AppController controller = sampleController(new LocalRepository(blockedParent.resolve("incident.json")));
		Ics204Panel panel = new Ics204Panel(controller);
		panel.refreshFromModel();
		ResourceAssignment row = new ResourceAssignment();
		Class<?> editorType = Class.forName(Ics204Panel.class.getName() + "$ResourceAssignmentEditor");
		var constructor = editorType.getDeclaredConstructor(ResourceAssignment.class, AppController.class);
		constructor.setAccessible(true);
		Object editor = constructor.newInstance(row, controller);
		var field = (javax.swing.JTextField) fieldValue(editor, "assignmentTeamNumberField");
		field.setText("Retained entry");
		var save = Ics204Panel.class.getDeclaredMethod("saveAssignmentEditor", ResourceAssignment.class, editorType);
		save.setAccessible(true);
		assertThrows(java.lang.reflect.InvocationTargetException.class, () -> save.invoke(panel, row, editor));
		assertThrows(java.lang.reflect.InvocationTargetException.class, () -> save.invoke(panel, row, editor));
		assertEquals("Retained entry", field.getText());
		assertEquals(1, controller.getAssignmentResources().size());
		assertEquals(false, controller.isDirty());
		Files.delete(blockedParent);
		Files.createDirectory(blockedParent);
		save.invoke(panel, row, editor);
		assertEquals(2, controller.getAssignmentResources().size());
		assertEquals("Retained entry", controller.getAssignmentResources().get(1).getAssignmentTeamNumber());
	}

	@Test
	void resourcesGridHidesAssignmentIdAndOffersPopupEditor() throws Exception {
		AppController controller = sampleController();
		Ics204Panel panel = new Ics204Panel(controller);
		SwingUtilities.invokeAndWait(panel::refreshFromModel);

		JTable table = (JTable) fieldValue(panel, "resourceTable");
		JPopupMenu menu = table.getComponentPopupMenu();

		assertEquals("Assignment/Team #", table.getColumnName(0));
		assertEquals("Resource Type", table.getColumnName(1));
		assertNotNull(menu);
		assertEquals("Add assignment…", ((JMenuItem) menu.getComponent(0)).getText());
	}

	@Test
	void managementContactsUseCompactSingleRowPerRole() throws Exception {
		AppController controller = sampleController();
		Ics204Panel panel = new Ics204Panel(controller);
		SwingUtilities.invokeAndWait(panel::refreshFromModel);

		JPanel contactsPanel = (JPanel) fieldValue(panel, "managementContactsPanel");

		assertEquals(2 * COMPONENTS_PER_COMPACT_CONTACT_ROW, contactsPanel.getComponentCount());
	}

	@Test
	void inlineAddLogEntryButtonInExistingAssignmentEditor() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		Ics204Panel panel = new Ics204Panel(controller);
		panel.refreshFromModel();
		ResourceAssignment assignment = controller.getAssignmentResources().get(0);
		var logged = new java.util.concurrent.atomic.AtomicReference<ResourceAssignment>();
		panel.setOnAddLogEntryRequest((launching, row) -> logged.set(row));
		var buttonFound = new java.util.concurrent.atomic.AtomicBoolean();
		var buttonInline = new java.util.concurrent.atomic.AtomicBoolean();
		var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		SwingUtilities.invokeAndWait(() -> {
			var main = new javax.swing.JFrame("Main Frame");
			main.add(panel);
			main.setSize(800, 600);
			main.setVisible(true);
			JTable table = (JTable) fieldValue(panel, "resourceTable");
			table.setRowSelectionInterval(0, 0);
			var timer = new javax.swing.Timer(100, event -> {
				try {
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (window instanceof javax.swing.JDialog dialog && dialog.isVisible()
								&& dialog.getTitle().startsWith("Edit ")) {
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
				Method openEditor = Ics204Panel.class.getDeclaredMethod("openSelectedResourceEditor");
				openEditor.setAccessible(true);
				openEditor.invoke(panel);
			} catch (Exception ex) {
				failure.compareAndSet(null, ex);
			} finally {
				timer.stop();
				main.dispose();
			}
		});
		assertNull(failure.get());
		assertTrue(buttonFound.get(), "Log button should be found in dialog");
		assertTrue(buttonInline.get(), "Log button should be inline with Save button");
		assertSame(assignment, logged.get());
	}

	private static JButton findButton(Component component, String text) {
		if (component instanceof JButton button && text.equals(button.getText())) {
			return button;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton button = findButton(child, text);
				if (button != null) {
					return button;
				}
			}
		}
		return null;
	}

	private static Object fieldValue(Object instance, String fieldName) {
		try {
			Field field = instance.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(instance);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/**
	 * Creates a controller backed by a disposable repository.
	 *
	 * @return sample controller.
	 * @throws Exception if the repository path cannot be created.
	 */
	private static AppController sampleController() throws Exception {
		return sampleController(new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")));
	}

	/**
	 * Creates an assignment fixture with configurable persistence.
	 *
	 * @param repository repository used when saving.
	 * @return sample controller.
	 */
	private static AppController sampleController(LocalRepository repository) {
		IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"),
				LocalDateTime.parse("2026-01-01T12:00:00"), "Planner", "Planning Section Chief");
		Ics204Form form204 = new Ics204Form();
		form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
		form204.setDivision("Division A");
		form204.setOperationsSectionChiefName("Ops Chief");
		form204.setOperationsSectionChiefContact("Tac 1");
		form204.setDivisionGroupSupervisorName("Supervisor");
		form204.setDivisionGroupSupervisorContact("Tac 2");
		ResourceAssignment resource = new ResourceAssignment();
		resource.setAssignmentTeamNumber("A-1");
		resource.setResourceType("Ground");
		resource.setTaskType("Area");
		resource.setResourceIdentifier("Team 1");
		resource.setLeader("Leader A");
		resource.setNumberOfPersons(3);
		resource.setAssignment("Search trail");
		form204.setResourcesAssigned(List.of(resource));
		AppData data = new AppData(context, new Ics202Form(), form204, List.of());
		return new AppController(data,
				repository,
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()),
				new IncidentValidator());
	}
}
