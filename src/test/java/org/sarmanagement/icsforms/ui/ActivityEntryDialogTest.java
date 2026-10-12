// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Exercises contextual selections, transactionality, and independent desktop workflows. */
class ActivityEntryDialogTest {
	@TempDir
	Path directory;

	@Test
	void persistedIcpEntryRestoresStableAssignmentBeforeConflictingResourceHeuristic() throws Exception {
		AppData data = new AppData();
		SarTaskAssignment originalTask = new SarTaskAssignment();
		originalTask.setAssignmentId("stable-original-task");
		originalTask.setResourceIdentifier("Search Team");
		SarTaskAssignment conflictingTask = new SarTaskAssignment();
		conflictingTask.setAssignmentId("other-task");
		conflictingTask.setResourceIdentifier("Robin");
		data.getSarTaskAssignments().addAll(List.of(originalTask, conflictingTask));
		AppController controller = controller(data);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller,
				new LogEntryContext(new JPanel(), "", originalTask.getAssignmentId(), "Robin"), null);
		ActivityLogEntry original = editor(dialog).toEntry();
		original.setNotableActivity("Earlier activity");
		assertTrue(dialog.saveEntry(new JPanel(), original, candidate -> true));
		assertEquals(originalTask.getAssignmentId(), original.getAssignmentId());
		String entryId = original.getEntryId();
		LocalRepository repository = new LocalRepository(directory.resolve("persisted-context.json"));
		repository.save(data);
		AppData restored = repository.loadOrDefault();
		AppController reloadedController = new AppController(restored, repository,
				new PdfExportService(), new IncidentValidator());
		Ics214Form reloadedLog = restored.getActivityLogs().get(0);
		ActivityLogEntry reloadedEntry = reloadedLog.getActivityLog().get(0);
		ActivityEntryDialog followUp = new ActivityEntryDialog(reloadedController,
				ActivityEntryDialog.followUpContext(new JPanel(), reloadedLog, reloadedEntry), reloadedLog);
		assertEquals(entryId, reloadedEntry.getEntryId());
		assertEquals("stable-original-task", followUp.selectedAssignmentId());
		assertEquals("Robin", editor(followUp).toEntry().getResourceIdentifier());
		assertEquals("", editor(followUp).toEntry().getNotableActivity());
		assertSame(reloadedLog, followUp.selectedLog());
	}

	@Test
	void stableEntryContextTakesPrecedenceOverLinkedLogContext() {
		Ics214Form log = new Ics214Form();
		log.setLinkedSarTaskAssignmentId("log-task");
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setAssignmentId("entry-task");
		entry.setResourceIdentifier("Robin");
		LogEntryContext context = ActivityEntryDialog.followUpContext(new JPanel(), log, entry);
		assertEquals("entry-task", context.assignmentId());
		assertEquals("Robin", context.resourceId());
	}

	@Test
	void legacyFollowUpRetainsLinkedLogContextWithoutChangingEntryIdentity() {
		Ics214Form log = new Ics214Form();
		log.setLinkedSarTaskAssignmentId("legacy-linked-task");
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setEntryId("legacy-entry-id");
		entry.setResourceIdentifier("Robin");
		LogEntryContext context = ActivityEntryDialog.followUpContext(new JPanel(), log, entry);
		assertEquals("legacy-linked-task", context.assignmentId());
		assertEquals("legacy-entry-id", entry.getEntryId());
		assertEquals("", entry.getAssignmentId());
	}

	@Test
	void disposedSaveAndLogInitiatorFallsBackToLiveDocumentRoot() throws Exception {
		assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			javax.swing.JFrame main = new javax.swing.JFrame("Main");
			javax.swing.JFrame detached = new javax.swing.JFrame("Detached");
			javax.swing.JDialog editor = new javax.swing.JDialog(detached, "Assignment editor");
			JPanel content = new JPanel();
			editor.add(content);
			try {
				main.setSize(300, 200);
				detached.setSize(300, 200);
				editor.setSize(300, 200);
				main.setVisible(true);
				detached.setVisible(true);
				editor.setVisible(true);
				assertSame(content, ActivityEntryDialog.resolveOwner(content, main));
				editor.dispose();
				assertSame(detached, ActivityEntryDialog.resolveOwner(content, main));
				detached.dispose();
				assertSame(main, ActivityEntryDialog.resolveOwner(content, main));
				assertSame(main, ActivityEntryDialog.resolveOwner(new JPanel(), main));
			} finally {
				editor.dispose();
				detached.dispose();
				main.dispose();
			}
		});
	}

	@Test
	void genericSelectorIncludesPrimaryAndAdditionalAssignmentsWithoutSarTasks() throws Exception {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.GENERIC);
		ResourceAssignment primary = new ResourceAssignment();
		primary.setAssignmentId("generic-primary");
		primary.setResourceIdentifier("Crew One");
		data.getForm204().getResourcesAssigned().add(primary);
		ResourceAssignment additional = new ResourceAssignment();
		additional.setAssignmentId("generic-additional");
		additional.setResourceIdentifier("Crew Two");
		org.sarmanagement.icsforms.model.Ics204Form additionalForm = new org.sarmanagement.icsforms.model.Ics204Form();
		additionalForm.getResourcesAssigned().add(additional);
		data.getAdditionalForms204().add(additionalForm);
		AppController controller = controller(data);
		data.getSarTaskAssignments().clear();
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller,
				new LogEntryContext(new JPanel(), "", additional.getAssignmentId(), "Robin"), null);
		JComboBox<?> assignments = (JComboBox<?>) field(dialog, "task");
		assertEquals(3, assignments.getItemCount());
		assertEquals(additional.getAssignmentId(), dialog.selectedAssignmentId());
		assertEquals("Robin", editor(dialog).toEntry().getResourceIdentifier());
		assignments.setSelectedIndex(1);
		assertEquals(primary.getAssignmentId(), dialog.selectedAssignmentId());
		assertEquals("Crew One", editor(dialog).toEntry().getResourceIdentifier());
		assignments.setSelectedIndex(2);
		editor(dialog).resourceIdentifierField.setSelectedItem("Casey");
		ActivityLogEntry entry = editor(dialog).toEntry();
		entry.setEventTypeId(ActivityEventType.ID_RESOURCE_ON_TASK);
		assertTrue(dialog.saveEntry(new JPanel(), entry, candidate -> true));
		assertEquals(additional.getAssignmentId(), dialog.selectedAssignmentId());
		assertEquals(additional.getAssignmentId(), entry.getAssignmentId());
		assertEquals("Casey", dialog.selectedLog().getActivityLog().get(0).getResourceIdentifier());
		assertTrue(data.getClueLogEntries().isEmpty());
		LocalRepository repository = new LocalRepository(directory.resolve("generic-context.json"));
		repository.save(data);
		AppData restored = repository.loadOrDefault();
		AppController restoredController = new AppController(restored, repository,
				new PdfExportService(), new IncidentValidator());
		restored.getSarTaskAssignments().clear();
		Ics214Form restoredLog = restored.getActivityLogs().get(0);
		ActivityLogEntry restoredEntry = restoredLog.getActivityLog().get(0);
		ActivityEntryDialog followUp = new ActivityEntryDialog(restoredController,
				ActivityEntryDialog.followUpContext(new JPanel(), restoredLog, restoredEntry), restoredLog);
		assertEquals(additional.getAssignmentId(), followUp.selectedAssignmentId());
		assertEquals("Casey", editor(followUp).toEntry().getResourceIdentifier());
		assertSame(restoredLog, followUp.selectedLog());
	}

	@Test
	void assignmentsWithCorrespondingSarTasksAreNotDuplicated() throws Exception {
		AppData data = new AppData();
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("deduplicated");
		data.getForm204().getResourcesAssigned().add(assignment);
		AppController controller = controller(data);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller,
				new LogEntryContext(new JPanel(), "", assignment.getAssignmentId(), "Robin"), null);
		assertEquals(2, ((JComboBox<?>) field(dialog, "task")).getItemCount());
		assertEquals(assignment.getAssignmentId(), dialog.selectedAssignmentId());
	}

	@Test
	void incidentSwitchDuringNestedClueWorkflowCannotCommitToEitherIncident() {
		AppData original = new AppData();
		original.setIncidentMode(IncidentMode.SAR);
		AppController controller = controller(original);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller,
				new LogEntryContext(new JPanel(), "", "", ""), null);
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setEventTypeId(ActivityEventType.ID_CLUE_DETECTED);
		assertFalse(dialog.saveEntry(new JPanel(), entry, candidate -> {
			controller.setData(new AppData());
			return true;
		}));
		assertTrue(dialog.selectedLog().getActivityLog().isEmpty());
		assertTrue(original.getActivityLogs().isEmpty());
		assertTrue(original.getClueLogEntries().isEmpty());
		assertTrue(controller.getData().getActivityLogs().isEmpty());
		assertTrue(controller.getData().getClueLogEntries().isEmpty());
	}

	@Test
	void selectedTaskLifecycleUsesAssignmentContextWhileKeepingIndividualResource() {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-lifecycle");
		task.setResourceIdentifier("Search Team");
		task.setTaskLifecycleStatus("assigned - enroute to assignment");
		data.getSarTaskAssignments().add(task);
		AppController controller = controller(data);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller,
				new LogEntryContext(new JPanel(), task.getTaskId(), task.getAssignmentId(), "Robin"), null);
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setTimestamp(java.time.LocalDateTime.of(2026, 1, 1, 9, 30));
		entry.setEventTypeId(ActivityEventType.ID_RESOURCE_ON_TASK);
		entry.setResourceIdentifier("Robin");
		assertSame(task, dialog.selectedTask());
		assertTrue(dialog.saveEntry(new JPanel(), entry, candidate -> true));
		assertEquals("Robin", dialog.selectedLog().getActivityLog().get(0).getResourceIdentifier());
		assertEquals("assigned - on task", task.getTaskLifecycleStatus());
		assertEquals(entry.getTimestamp(), task.getAssignmentStart());
		assertTrue(data.getClueLogEntries().isEmpty());
	}

	@Test
	void detachedLoggingWorksWhileMainTaskEditorRetainsPendingEditsAndClueCancellationIsAtomic() throws Exception {
		assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.SAR);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-desktop");
		task.setResourceIdentifier("Search Team");
		task.setTaskLifecycleStatus("assigned - enroute to assignment");
		data.getSarTaskAssignments().add(task);
		Ics214Form log = new Ics214Form();
		log.setLinkedSarTaskAssignmentId(task.getAssignmentId());
		data.getActivityLogs().add(log);
		AppController controller = controller(data);
		java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			javax.swing.JFrame main = new javax.swing.JFrame("Main assignment document");
			javax.swing.JFrame detached = new javax.swing.JFrame("Independent activity document");
			SarTaskPanel tasks = new SarTaskPanel(controller);
			tasks.refreshFromModel();
			Ics214Panel activity = new Ics214Panel(controller);
			activity.loadFromModel(log, data);
			main.add(tasks);
			detached.add(activity);
			main.setSize(800, 600);
			detached.setSize(800, 600);
			UiSupport.registerIncidentWindow(detached, data);
			java.util.concurrent.atomic.AtomicInteger stage = new java.util.concurrent.atomic.AtomicInteger();
			java.util.concurrent.atomic.AtomicReference<javax.swing.JDialog> mainEditor =
					new java.util.concurrent.atomic.AtomicReference<>();
			java.util.concurrent.atomic.AtomicReference<javax.swing.JTextArea> pending =
					new java.util.concurrent.atomic.AtomicReference<>();
			long started = System.currentTimeMillis();
			javax.swing.Timer timer = new javax.swing.Timer(100, null);
			timer.setCoalesce(false);
			timer.addActionListener(event -> {
				try {
					if (System.currentTimeMillis() - started > 30000) {
						throw new AssertionError("Concurrent desktop workflow timed out at stage " + stage.get());
					}
					for (java.awt.Window window : java.awt.Window.getWindows()) {
						if (!(window instanceof javax.swing.JDialog dialog) || !dialog.isVisible()) {
							continue;
						}
						if (stage.get() == 0 && dialog.getTitle().startsWith("Edit SAR Task")) {
							mainEditor.set(dialog);
							pending.set(component(dialog, javax.swing.JTextArea.class));
							pending.get().setText("Pending assignment editor text");
							stage.set(1);
							button(activity, "Add Entry").doClick();
							return;
						}
						if ((stage.get() == 1 || stage.get() == 3) && dialog.getTitle().equals("Add log entry")) {
							assertSame(detached, UiSupport.documentRoot(dialog));
							assertTrue(mainEditor.get().isVisible());
							assertEquals("Pending assignment editor text", pending.get().getText());
							selectEvent(dialog, stage.get() == 1
									? ActivityEventType.ID_RESOURCE_ON_TASK : ActivityEventType.ID_CLUE_DETECTED);
							stage.incrementAndGet();
							component(dialog, javax.swing.JOptionPane.class).setValue(javax.swing.JOptionPane.OK_OPTION);
							return;
						}
						if (stage.get() == 4 && dialog.getTitle().equals("Capture clue details")) {
							assertSame(detached, UiSupport.documentRoot(dialog));
							stage.set(5);
							component(dialog, javax.swing.JOptionPane.class).setValue(javax.swing.JOptionPane.CANCEL_OPTION);
							return;
						}
					}
					if (stage.get() == 2) {
						assertEquals(1, log.getActivityLog().size());
						assertEquals("Robin", log.getActivityLog().get(0).getResourceIdentifier());
						assertEquals("assigned - on task", task.getTaskLifecycleStatus());
						assertEquals(log.getActivityLog().get(0).getTimestamp(), task.getAssignmentStart());
						stage.set(3);
						button(activity, "Add Entry").doClick();
					} else if (stage.get() == 5) {
						assertEquals(1, log.getActivityLog().size());
						assertTrue(data.getClueLogEntries().isEmpty());
						assertEquals("Pending assignment editor text", pending.get().getText());
						stage.set(6);
						mainEditor.get().dispose();
						timer.stop();
					}
				} catch (Throwable exception) {
					failure.compareAndSet(null, exception);
					timer.stop();
					detached.dispose();
					main.dispose();
				}
			});
			try {
				main.setVisible(true);
				detached.setVisible(true);
				timer.start();
				tasks.openAssignmentEditorById(task.getAssignmentId());
				assertEquals(6, stage.get());
			} catch (Throwable exception) {
				failure.compareAndSet(null, exception);
			} finally {
				timer.stop();
				detached.dispose();
				main.dispose();
			}
		});
		if (failure.get() != null) {
			throw new AssertionError("Concurrent desktop logging failed", failure.get());
		}
	}

	/**
	 * Selects the event type and individual resource in an actual activity editor.
	 *
	 * @param root activity dialog
	 * @param eventId event identifier
	 */
	private static void selectEvent(java.awt.Container root, String eventId) {
		for (java.awt.Component child : root.getComponents()) {
			if (child instanceof JComboBox<?> combo) {
				if (combo.isEditable()) {
					combo.setSelectedItem("Robin");
				}
				for (int index = 0; index < combo.getItemCount(); index++) {
					if (combo.getItemAt(index) instanceof ActivityEventType type && eventId.equals(type.getId())) {
						combo.setSelectedIndex(index);
					}
				}
			}
			if (child instanceof java.awt.Container nested) {
				selectEvent(nested, eventId);
			}
		}
	}

	/**
	 * Finds a labelled action in a component tree.
	 *
	 * @param root component tree
	 * @param text button label
	 * @return action button, or null when absent
	 */
	private static javax.swing.JButton button(java.awt.Container root, String text) {
		for (java.awt.Component child : root.getComponents()) {
			if (child instanceof javax.swing.JButton action && text.equals(action.getText())) {
				return action;
			}
			if (child instanceof java.awt.Container nested) {
				javax.swing.JButton found = button(nested, text);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * Finds a component by type in a desktop fixture.
	 *
	 * @param root fixture root
	 * @param type desired component type
	 * @param <T> component type
	 * @return matching component, or null when absent
	 */
	private static <T> T component(java.awt.Container root, Class<T> type) {
		if (type.isInstance(root)) {
			return type.cast(root);
		}
		for (java.awt.Component child : root.getComponents()) {
			if (type.isInstance(child)) {
				return type.cast(child);
			}
			if (child instanceof java.awt.Container nested) {
				T found = component(nested, type);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	@Test
	void incidentSwitchPreventsDetachedEntryCommit() {
		AppData original = new AppData();
		AppController controller = controller(original);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller,
				new LogEntryContext(new JPanel(), "", "", ""), null);
		controller.setData(new AppData());
		assertFalse(dialog.saveEntry(new JPanel(), new ActivityLogEntry(), candidate -> true));
		assertTrue(original.getActivityLogs().isEmpty());
		assertTrue(controller.getData().getActivityLogs().isEmpty());
	}

	@Test
	void cancelingClueCaptureLeavesNoPartialActivityOrLog() {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.SAR);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", "", ""), null);
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setEventTypeId(ActivityEventType.ID_CLUE_DETECTED);
		assertFalse(dialog.saveEntry(new JPanel(), entry, candidate -> false));
		assertTrue(dialog.selectedLog().getActivityLog().isEmpty());
		assertTrue(data.getActivityLogs().isEmpty());
		assertTrue(data.getClueLogEntries().isEmpty());
	}

	@Test
	void acceptedActivityCreatesDestinationAndAddsExactlyOneEntry() {
		AppData data = new AppData();
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", "", ""), null);
		ActivityLogEntry entry = new ActivityLogEntry();
		assertTrue(dialog.saveEntry(new JPanel(), entry, candidate -> true));
		assertEquals(1, data.getActivityLogs().size());
		assertEquals(List.of(entry), dialog.selectedLog().getActivityLog());
	}

	@Test
	void contextDefaultsToIcpWhileKeepingTaskAndResource() throws Exception {
		AppData data = new AppData();
		SarTaskAssignment assignment = new SarTaskAssignment();
		assignment.setAssignmentId("assignment-1");
		assignment.setResourceIdentifier("Search Team");
		data.getSarTaskAssignments().add(assignment);
		Ics214Form taskLog = new Ics214Form();
		taskLog.setLinkedSarTaskAssignmentId(assignment.getAssignmentId());
		Ics214Form icp = new Ics214Form();
		data.getActivityLogs().addAll(List.of(taskLog, icp));
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", assignment.getAssignmentId(), "Search Team"), null);

		assertSame(icp, dialog.selectedLog());
		assertSame(assignment, dialog.selectedTask());
		assertEquals("Search Team", editor(dialog).toEntry().getResourceIdentifier());
		assertTrue(icp.getActivityLog().isEmpty());
		assertTrue(data.getClueLogEntries().isEmpty());
	}

	@Test
	void noLogsCreatesOnlyDetachedDefaultUntilAcceptance() {
		AppData data = new AppData();
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", "", ""), null);

		assertEquals("ICP", dialog.selectedLog().getName());
		assertTrue(data.getActivityLogs().isEmpty());
	}

	@Test
	void destinationsAndTasksRemainChangeable() throws Exception {
		AppData data = new AppData();
		Ics214Form first = new Ics214Form();
		Ics214Form second = new Ics214Form();
		data.getActivityLogs().addAll(List.of(first, second));
		SarTaskAssignment assignment = new SarTaskAssignment();
		assignment.setResourceIdentifier("Team Two");
		data.getSarTaskAssignments().add(assignment);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", "", "Original"), first);

		((JComboBox<?>) field(dialog, "destination")).setSelectedIndex(1);
		((JComboBox<?>) field(dialog, "task")).setSelectedIndex(1);
		assertSame(second, dialog.selectedLog());
		assertSame(assignment, dialog.selectedTask());
		assertEquals("Team Two", editor(dialog).toEntry().getResourceIdentifier());
		assertEquals(2, data.getActivityLogs().size());
	}

	@Test
	void clueEventsAreExcludedOutsideSar() throws Exception {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.GENERIC);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", "", ""), null);
		JComboBox<?> events = (JComboBox<?>) field(editor(dialog), "eventTypeField");
		for (int index = 0; index < events.getItemCount(); index++) {
			String id = ((ActivityEventType) events.getItemAt(index)).getId();
			assertFalse(ActivityEventType.ID_CLUE_DETECTED.equals(id));
			assertFalse(ActivityEventType.ID_CLUE_REPORTED.equals(id));
		}
	}

	@Test
	void followUpUsesPreferredLogAndResourceWithoutCopyingPriorActivity() throws Exception {
		AppData data = new AppData();
		Ics214Form log = new Ics214Form();
		ActivityLogEntry original = new ActivityLogEntry();
		original.setResourceIdentifier("Robin");
		original.setNotableActivity("Earlier activity");
		log.getActivityLog().add(original);
		data.getActivityLogs().add(log);
		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", "", original.getResourceIdentifier()), log);

		ActivityLogEntry followUp = editor(dialog).toEntry();
		assertSame(log, dialog.selectedLog());
		assertEquals("Robin", followUp.getResourceIdentifier());
		assertEquals("", followUp.getNotableActivity());
		assertEquals(1, log.getActivityLog().size());
	}

	@Test
	void clueDetectedLabelAdaptsBetweenIcpAndTaskDestination() throws Exception {
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.SAR);
		Ics214Form icpLog = new Ics214Form();
		icpLog.setName("ICP");
		Ics214Form taskLog = new Ics214Form();
		taskLog.setName("Task 101");
		taskLog.setLinkedSarTaskAssignmentId("assignment-101");
		data.getActivityLogs().addAll(List.of(icpLog, taskLog));

		ActivityEntryDialog dialog = new ActivityEntryDialog(controller(data),
				new LogEntryContext(new JPanel(), "", "assignment-101", "Search Team"), null);

		JComboBox<ActivityEventType> events = editor(dialog).eventTypeField;
		JComboBox<?> destinations = (JComboBox<?>) field(dialog, "destination");

		ActivityEventType clueDetected = null;
		for (int i = 0; i < events.getItemCount(); i++) {
			if (ActivityEventType.ID_CLUE_DETECTED.equals(events.getItemAt(i).getId())) {
				clueDetected = events.getItemAt(i);
				break;
			}
		}
		org.junit.jupiter.api.Assertions.assertNotNull(clueDetected);

		javax.swing.ListCellRenderer<? super ActivityEventType> renderer = events.getRenderer();
		java.awt.Component icpRendered = renderer.getListCellRendererComponent(
				new javax.swing.JList<>(), clueDetected, 0, false, false);
		assertTrue(icpRendered instanceof JLabel);
		assertEquals("Clue detected by this assignment", ((JLabel) icpRendered).getText());

		destinations.setSelectedIndex(1);
		java.awt.Component taskRendered = events.getRenderer().getListCellRendererComponent(
				new javax.swing.JList<>(), clueDetected, 0, false, false);
		assertTrue(taskRendered instanceof JLabel);
		assertEquals("Clue detected by my assignment", ((JLabel) taskRendered).getText());

		destinations.setSelectedIndex(0);
		java.awt.Component backToIcp = events.getRenderer().getListCellRendererComponent(
				new javax.swing.JList<>(), clueDetected, 0, false, false);
		assertEquals("Clue detected by this assignment", ((JLabel) backToIcp).getText());
	}

	@Test
	void editorRendersClueDetectedBasedOnIcpScopeSupplier() {
		List<ActivityEventType> types = List.of(
				new ActivityEventType(ActivityEventType.ID_CLUE_DETECTED, "Clue Detected", true),
				new ActivityEventType(ActivityEventType.ID_CLUE_REPORTED, "Clue Reported", true));
		java.util.concurrent.atomic.AtomicBoolean isIcp = new java.util.concurrent.atomic.AtomicBoolean(true);
		Ics214Panel.ActivityEntryEditor editor = new Ics214Panel.ActivityEntryEditor(
				types, "", List.of(), isIcp::get);
		ActivityEventType type = types.get(0);
		javax.swing.ListCellRenderer<? super ActivityEventType> renderer = editor.eventTypeField.getRenderer();
		JLabel label = (JLabel) renderer.getListCellRendererComponent(new javax.swing.JList<>(), type, 0, false, false);
		assertEquals("Clue detected by this assignment", label.getText());

		isIcp.set(false);
		editor.refreshEventTypeDisplay();
		label = (JLabel) renderer.getListCellRendererComponent(new javax.swing.JList<>(), type, 0, false, false);
		assertEquals("Clue detected by my assignment", label.getText());
	}

	/**
	 * Builds a controller backed by the test directory.
	 *
	 * @param data incident fixture
	 * @return test controller
	 */
	private AppController controller(AppData data) {
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			ResourceAssignment assignment = new ResourceAssignment();
			assignment.setAssignmentId(task.getAssignmentId());
			assignment.setResourceIdentifier(task.getResourceIdentifier());
			data.getForm204().getResourcesAssigned().add(assignment);
		}
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")),
				new PdfExportService(), new IncidentValidator());
	}

	/**
	 * Returns the detached shared editor.
	 *
	 * @param dialog contextual dialog
	 * @return editor fields
	 * @throws ReflectiveOperationException if the editor cannot be accessed
	 */
	private static Ics214Panel.ActivityEntryEditor editor(ActivityEntryDialog dialog) throws ReflectiveOperationException {
		return (Ics214Panel.ActivityEntryEditor) field(dialog, "editor");
	}

	/**
	 * Accesses private Swing fixtures.
	 *
	 * @param object fixture object
	 * @param name field name
	 * @return field value
	 * @throws ReflectiveOperationException if the field cannot be accessed
	 */
	private static Object field(Object object, String name) throws ReflectiveOperationException {
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}
}
