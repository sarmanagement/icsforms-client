/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ObjectiveAssessment;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics202PdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics204PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.pdf.SarTaskAssignmentPdfRenderer;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JDialog;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Ics202PanelTest {

	@Test
	void compactTableCommitsUntrimmedTextAndRetainsAssessmentAndIdentity() throws Exception {
		AppController controller = sampleController();
		IncidentObjective objective = new IncidentObjective("Original", 0);
		objective.setAssessment(ObjectiveAssessment.values()[0]);
		objective.setAssessmentNote("Private assessment note");
		controller.getData().getForm202().setStructuredObjectives(List.of(objective));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "objectivesTable");
			assertEquals(2, table.getColumnCount());
			assertEquals("Objective", table.getColumnName(1));
			assertFalse(table.isCellEditable(0, 0));
			assertTrue(table.isCellEditable(0, 1));
			assertTrue(table.getPreferredScrollableViewportSize().height <= 120);
			table.setSize(table.getPreferredScrollableViewportSize());
			table.doLayout();
			assertEquals(45, table.getColumnModel().getColumn(0).getWidth());
			assertEquals(435, table.getColumnModel().getColumn(1).getWidth());
			assertTrue(table.editCellAt(0, 1));
			((JTextField) table.getEditorComponent()).setText("  Revised \u96EA objective\nsecond line  ");
			assertTrue(((javax.swing.JLabel) fieldValue(panel, "objectiveWarning")).getText().contains("U+96EA"));
			assertEquals("Original", objective.getText());
			panel.pushToModel();
			assertFalse(table.isEditing());
			assertEquals("  Revised \u96EA objective\nsecond line  ", objective.getText());
			assertEquals("Private assessment note", objective.getAssessmentNote());
			assertSame(objective, controller.getData().getForm202().getStructuredObjectives().get(0));
			assertTrue(((javax.swing.JLabel) fieldValue(panel, "objectiveWarning")).getText().contains("U+96EA"));
			table.setValueAt("Corrected", 0, 1);
			assertEquals(" ", ((javax.swing.JLabel) fieldValue(panel, "objectiveWarning")).getText());
		});
	}

	@Test
	void enteringAndCommittingExistingMultilineObjectiveWithoutTypingPreservesItsExactText() throws Exception {
		AppController controller = sampleController();
		String multiline = "  First objective line\nSecond objective line\n\tThird line  ";
		IncidentObjective objective = new IncidentObjective(multiline, 0);
		controller.getData().getForm202().setStructuredObjectives(List.of(objective));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "objectivesTable");
			for (int attempt = 0; attempt < 2; attempt++) {
				assertTrue(table.editCellAt(0, 1));
				JTextField editor = (JTextField) table.getEditorComponent();
				assertEquals(Boolean.FALSE, editor.getDocument().getProperty("filterNewlines"));
				assertEquals(multiline, editor.getText());
				if (attempt == 0) {
					assertTrue(table.getCellEditor().stopCellEditing());
				} else {
					panel.pushToModel();
				}
				assertFalse(table.isEditing());
				assertEquals(multiline, objective.getText());
				assertEquals(multiline, controller.getData().getForm202().getStructuredObjectives().get(0).getText());
				panel.refreshFromModel();
			}
		});
	}

	@Test
	void activeEditSurvivesReorderAndUnlinkedDeleteWithoutChangingLinks() throws Exception {
		AppController controller = sampleController();
		IncidentObjective first = new IncidentObjective("First", 0);
		IncidentObjective second = new IncidentObjective("Second", 1);
		controller.getData().getForm202().setStructuredObjectives(List.of(first, second));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setObjectiveIds(List.of(first.getObjectiveId()));
		controller.getData().getForm204().setResourcesAssigned(List.of(assignment));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "objectivesTable");
			table.setRowSelectionInterval(0, 0);
			assertTrue(table.editCellAt(0, 1));
			((JTextField) table.getEditorComponent()).setText("Edited before move");
			invoke(panel, "moveSelectedObjective", new Class<?>[]{int.class}, 1);
			assertSame(first, controller.getData().getForm202().getStructuredObjectives().get(1));
			assertEquals("Edited before move", first.getText());
			assertEquals(List.of(first.getObjectiveId()), assignment.getObjectiveIds());
			table.setRowSelectionInterval(0, 0);
			invoke(panel, "removeSelectedObjective", new Class<?>[0]);
			assertEquals(List.of(first), controller.getData().getForm202().getStructuredObjectives());
			assertEquals(List.of(first.getObjectiveId()), assignment.getObjectiveIds());
		});
	}

	@Test
	void rejectedEditBlocksSaveAndReorderWhileRefreshAndIncidentSwitchRemainSafe() throws Exception {
		AppController controller = sampleController();
		controller.getData().getForm202().setObjectives(List.of("First", "Second"));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "objectivesTable");
			table.getColumnModel().getColumn(1).setCellEditor(new javax.swing.DefaultCellEditor(new JTextField()) {
				@Override
				public boolean stopCellEditing() {
					return false;
				}
			});
			table.setRowSelectionInterval(0, 0);
			assertTrue(table.editCellAt(0, 1));
			((JTextField) table.getEditorComponent()).setText("Rejected");
			assertThrows(IllegalStateException.class, panel::pushToModel);
			invoke(panel, "moveSelectedObjective", new Class<?>[]{int.class}, 1);
			assertThrows(IllegalStateException.class, panel::refreshFromModel);
			assertTrue(table.isEditing());
			assertEquals(List.of("First", "Second"), controller.getData().getForm202().getObjectives());
			AppData replacement = new AppData();
			replacement.getForm202().setObjectives(List.of("Other incident"));
			controller.setData(replacement);
			panel.pushToModel();
			panel.refreshFromModel();
			assertFalse(table.isEditing());
			assertEquals("Other incident", table.getValueAt(0, 1));
			assertEquals(List.of("Other incident"), replacement.getForm202().getObjectives());
		});
	}

	@Test
	void assignmentSelectionGroupsContextsSupportsZeroManyAndPreservesOtherLinks() throws Exception {
		AppController controller = sampleController();
		IncidentObjective objective = new IncidentObjective("Selected", 0);
		ResourceAssignment first = new ResourceAssignment();
		first.setAssignmentTeamNumber("A");
		first.setObjectiveIds(List.of("other", objective.getObjectiveId()));
		controller.getData().getForm204().setBranch("West");
		controller.getData().getForm204().setResourcesAssigned(List.of(first));
		Ics204Form extra = new Ics204Form();
		extra.setBranch("East");
		extra.setGroup("Search");
		ResourceAssignment second = new ResourceAssignment();
		second.setAssignmentTeamNumber("B");
		second.setObjectiveIds(List.of("another"));
		extra.setResourcesAssigned(List.of(second));
		controller.getData().setAdditionalForms204(List.of(extra));
		SwingUtilities.invokeAndWait(() -> {
			var selection = new Ics202Panel.AssignmentSelection(controller.getData(), objective.getObjectiveId());
			JTable table = (JTable) fieldValue(selection, "table");
			assertTrue(table.getValueAt(0, 1).toString().contains("East"));
			assertTrue(table.getValueAt(0, 1).toString().contains("Search"));
			table.setValueAt(true, 0, 0);
			assertEquals(List.of("another"), second.getObjectiveIds(), "Draft must not mutate links on cancel");
			selection.apply(objective.getObjectiveId());
			assertEquals(List.of("another", objective.getObjectiveId()), second.getObjectiveIds());
			assertEquals(List.of("other", objective.getObjectiveId()), first.getObjectiveIds());
			table.setValueAt(false, 0, 0);
			table.setValueAt(false, 1, 0);
			selection.apply(objective.getObjectiveId());
			assertEquals(List.of("other"), first.getObjectiveIds());
			assertEquals(List.of("another"), second.getObjectiveIds());
		});
	}

	@Test
	void objectiveDialogCancelAndSaveAndStaleIncidentGuard() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		IncidentObjective objective = new IncidentObjective("Original", 0);
		controller.getData().getForm202().setStructuredObjectives(List.of(objective));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			showAndAct(panel, "editSelectedObjective", "Edit incident objective", dialog -> {
				((javax.swing.JTextArea) fieldValue(panel, "objectiveTextArea")).setText("Cancelled");
				findButton(dialog, "Cancel").doClick();
			});
			panel.pushToModel();
			assertEquals("Original", objective.getText());
			showAndAct(panel, "editSelectedObjective", "Edit incident objective", dialog -> {
				((javax.swing.JTextArea) fieldValue(panel, "objectiveTextArea")).setText("Saved");
				((javax.swing.JTextArea) fieldValue(panel, "objectiveAssessmentNote")).setText("Assessment detail");
				((javax.swing.JComboBox<?>) fieldValue(panel, "objectiveAssessment")).setSelectedIndex(1);
				findButton(dialog, "Save").doClick();
			});
			assertEquals("Saved", objective.getText());
			assertEquals("Assessment detail", objective.getAssessmentNote());
			assertEquals(ObjectiveAssessment.values()[1], objective.getAssessment());
			assertEquals("Planner", objective.getAssessedBy());
			assertTrue(objective.getAssessedAt() != null);
			showAndAct(panel, "editSelectedObjective", "Edit incident objective", dialog -> {
				((javax.swing.JTextArea) fieldValue(panel, "objectiveTextArea")).setText("Stale");
				controller.setData(new AppData());
				findButton(dialog, "Save").doClick();
			});
			assertEquals("Saved", objective.getText());
			assertTrue(controller.getData().getForm202().getStructuredObjectives().isEmpty());
		});
	}

	@Test
	void applyDialogCancelAndIncidentSwitchNeverMutateAssignmentLinks() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		IncidentObjective objective = new IncidentObjective("Objective", 0);
		controller.getData().getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment assignment = new ResourceAssignment();
		controller.getData().getForm204().setResourcesAssigned(List.of(assignment));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			showAndAct(panel, "applySelectedObjective", "Apply objective to assignments", dialog -> {
				findTable(dialog).setValueAt(true, 0, 0);
				findButton(dialog, "Cancel").doClick();
			});
			assertTrue(assignment.getObjectiveIds().isEmpty());
			showAndAct(panel, "applySelectedObjective", "Apply objective to assignments", dialog -> {
				findTable(dialog).setValueAt(true, 0, 0);
				findButton(dialog, "Apply").doClick();
			});
			assertEquals(List.of(objective.getObjectiveId()), assignment.getObjectiveIds());
			showAndAct(panel, "applySelectedObjective", "Apply objective to assignments", dialog -> {
				findTable(dialog).setValueAt(false, 0, 0);
				controller.setData(new AppData());
				findButton(dialog, "Apply").doClick();
			});
			assertEquals(List.of(objective.getObjectiveId()), assignment.getObjectiveIds());
		});
	}

	@Test
	void linkedObjectiveDeleteRequiresConfirmationPreservesOtherLinksAndGuardsIncidentSwitch() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		IncidentObjective first = new IncidentObjective("First", 0);
		IncidentObjective second = new IncidentObjective("Second", 1);
		controller.getData().getForm202().setStructuredObjectives(List.of(first, second));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setObjectiveIds(List.of(first.getObjectiveId(), second.getObjectiveId()));
		controller.getData().getForm204().setResourcesAssigned(List.of(assignment));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			showAndAct(panel, "removeSelectedObjective", "Resolve objective links",
					dialog -> findButton(dialog, "Cancel").doClick());
			assertEquals(2, controller.getData().getForm202().getStructuredObjectives().size());
			assertEquals(2, assignment.getObjectiveIds().size());
			showAndAct(panel, "removeSelectedObjective", "Resolve objective links",
					dialog -> findButton(dialog, "OK").doClick());
			assertEquals(List.of(second), controller.getData().getForm202().getStructuredObjectives());
			assertEquals(List.of(second.getObjectiveId()), assignment.getObjectiveIds());
			showAndAct(panel, "removeSelectedObjective", "Resolve objective links", dialog -> {
				controller.setData(new AppData());
				findButton(dialog, "OK").doClick();
			});
			assertEquals(List.of(second.getObjectiveId()), assignment.getObjectiveIds());
			assertTrue(controller.getData().getForm202().getStructuredObjectives().isEmpty());
		});
	}

	@Test
	void actualApplyAndMoveButtonsFlushSortedObjectiveEditsAndLinkMultipleGroupedAssignmentRows() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		IncidentObjective first = new IncidentObjective("Zulu objective", 0);
		IncidentObjective second = new IncidentObjective("Alpha objective", 1);
		controller.getData().getForm202().setStructuredObjectives(List.of(first, second));
		ResourceAssignment west = new ResourceAssignment();
		west.setAssignmentTeamNumber("Zulu team");
		west.setResourceIdentifier("Resource label is not a grouping key");
		west.setObjectiveIds(List.of(second.getObjectiveId()));
		controller.getData().getForm204().setBranch("West branch");
		controller.getData().getForm204().setGroup("Rescue group");
		controller.getData().getForm204().setResourcesAssigned(List.of(west));
		ResourceAssignment east = new ResourceAssignment();
		east.setAssignmentTeamNumber("Alpha team");
		Ics204Form additional = new Ics204Form();
		additional.setBranch("East branch");
		additional.setDivision("Search division");
		additional.setResourcesAssigned(List.of(east));
		controller.getData().setAdditionalForms204(List.of(additional));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "objectivesTable");
			table.setAutoCreateRowSorter(true);
			table.getRowSorter().toggleSortOrder(1);
			assertEquals("Alpha objective", table.getValueAt(0, 1));
			table.setRowSelectionInterval(1, 1);
			assertTrue(table.editCellAt(1, 1));
			((JTextField) table.getEditorComponent()).setText("Zulu edited before apply");
			runDialogAction(() -> findButton(panel, "Apply to assignments…").doClick(), "Apply objective to assignments",
					dialog -> {
						assertEquals("Zulu edited before apply", first.getText());
						JTable assignments = findTable(dialog);
						assignments.setAutoCreateRowSorter(true);
						assignments.getRowSorter().toggleSortOrder(2);
						assertEquals("Alpha team", assignments.getValueAt(0, 2));
						assertEquals("Branch: East branch / Division: Search division", assignments.getValueAt(0, 1));
						assertEquals("Branch: West branch / Group: Rescue group", assignments.getValueAt(1, 1));
						clickLinkCheckbox(assignments, 0);
						clickLinkCheckbox(assignments, 1);
						findButton(dialog, "Apply").doClick();
					});
			assertFalse(table.isEditing());
			assertEquals(List.of(second.getObjectiveId(), first.getObjectiveId()), west.getObjectiveIds());
			assertEquals(List.of(first.getObjectiveId()), east.getObjectiveIds());
			findButton(panel, "Move down").doClick();
			assertSame(first, controller.getData().getForm202().getStructuredObjectives().get(1));
			assertSame(first, controller.getData().getForm202().getStructuredObjectives()
					.get(table.convertRowIndexToModel(table.getSelectedRow())));
			runDialogAction(() -> findButton(panel, "Apply to assignments…").doClick(), "Apply objective to assignments",
					dialog -> {
						JTable assignments = findTable(dialog);
						clickLinkCheckbox(assignments, 0);
						clickLinkCheckbox(assignments, 1);
						findButton(dialog, "Cancel").doClick();
					});
			assertEquals(List.of(second.getObjectiveId(), first.getObjectiveId()), west.getObjectiveIds());
			assertEquals(List.of(first.getObjectiveId()), east.getObjectiveIds());
			runDialogAction(() -> findButton(panel, "Apply to assignments…").doClick(), "Apply objective to assignments",
					dialog -> {
						JTable assignments = findTable(dialog);
						clickLinkCheckbox(assignments, 0);
						clickLinkCheckbox(assignments, 1);
						findButton(dialog, "Apply").doClick();
					});
			assertEquals(List.of(second.getObjectiveId()), west.getObjectiveIds());
			assertTrue(east.getObjectiveIds().isEmpty());
		});
	}

	@Test
	@EnabledIfSystemProperty(named = "icsforms.desktopTests", matches = "true")
	void defaultWindowWidthDevotesObjectiveGridToTextWithoutHorizontalScrolling() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		controller.getData().getForm202().setObjectives(List.of("An objective with editable text"));
		var directory = Files.createTempDirectory("objective-default-width");
		var repository = new LocalRepository(directory.resolve("incident.json"));
		SwingUtilities.invokeAndWait(() -> {
			MainFrame window = new MainFrame(controller.getData(), repository, new PdfExportService(),
					new IncidentValidator(), directory);
			try {
				window.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_202));
				window.setVisible(true);
				window.validate();
				Ics202Panel panel = (Ics202Panel) fieldValue(window, "ics202Panel");
				JTable table = (JTable) fieldValue(panel, "objectivesTable");
				javax.swing.JScrollPane formScroll = (javax.swing.JScrollPane) panel.getComponent(0);
				assertFalse(formScroll.getHorizontalScrollBar().isVisible());
				assertFalse(formScroll.getVerticalScrollBar().isVisible());
				assertEquals(45, table.getColumnModel().getColumn(0).getWidth());
				assertTrue(table.getColumnModel().getColumn(1).getWidth() >= 600);
				assertEquals(table.getWidth() - 45, table.getColumnModel().getColumn(1).getWidth());
				JButton apply = findButton(panel, "Apply to assignments…");
				java.awt.Container actions = apply.getParent();
				for (java.awt.Component component : actions.getComponents()) {
					if (component instanceof JButton button) {
						assertEquals(apply.getY(), button.getY(), button.getText());
						assertTrue(button.getX() >= 0 && button.getY() >= 0, button.getText());
						assertTrue(button.getBounds().getMaxX() <= actions.getWidth(), button.getText());
						assertTrue(button.getBounds().getMaxY() <= actions.getHeight(), button.getText());
					}
				}
			} finally {
				((javax.swing.Timer) fieldValue(fieldValue(window, "controller"), "autosaveTimer")).stop();
				window.dispose();
			}
		});
	}

	@Test
	void refreshCommitsAnAcceptedPartialEditAndPreservesItsObjectiveSelection() throws Exception {
		AppController controller = sampleController();
		controller.getData().getForm202().setObjectives(List.of("First", "Second"));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "objectivesTable");
			table.setRowSelectionInterval(1, 1);
			String selectedId = controller.getData().getForm202().getStructuredObjectives().get(1).getObjectiveId();
			assertTrue(table.editCellAt(1, 1));
			((JTextField) table.getEditorComponent()).setText("Partial draft before refresh");
			panel.refreshFromModel();
			assertFalse(table.isEditing());
			assertEquals("Partial draft before refresh", table.getValueAt(table.getSelectedRow(), 1));
			assertEquals(selectedId, controller.getData().getForm202().getStructuredObjectives()
					.get(table.convertRowIndexToModel(table.getSelectedRow())).getObjectiveId());
			assertEquals("Partial draft before refresh", controller.getData().getForm202().getObjectives().get(1));
		});
	}

	@Test
	void noteOnlyAssessmentSaveUpdatesAuditAndKeepsStableSelectionAfterSortedTextChange() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		IncidentObjective first = new IncidentObjective("Zulu", 0);
		IncidentObjective second = new IncidentObjective("Bravo", 1);
		LocalDateTime previous = LocalDateTime.parse("2026-01-01T00:00:00");
		first.setAssessedBy("Previous assessor");
		first.setAssessedAt(previous);
		first.setAssessmentNote("Original note");
		controller.getData().getForm202().setStructuredObjectives(List.of(first, second));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) fieldValue(panel, "objectivesTable");
			table.setAutoCreateRowSorter(true);
			((javax.swing.table.TableRowSorter<?>) table.getRowSorter()).setSortsOnUpdates(true);
			table.getRowSorter().toggleSortOrder(1);
			table.setRowSelectionInterval(1, 1);
			runDialogAction(() -> findButton(panel, "Edit objective…").doClick(), "Edit incident objective", dialog -> {
				((javax.swing.JTextArea) fieldValue(panel, "objectiveAssessmentNote")).setText("Changed note only");
				findButton(dialog, "Save").doClick();
			});
			assertEquals("Planner", first.getAssessedBy());
			assertTrue(first.getAssessedAt().isAfter(previous));
			assertEquals(ObjectiveAssessment.NOT_ASSESSED, first.getAssessment());
			assertEquals("Changed note only", first.getAssessmentNote());
			runDialogAction(() -> findButton(panel, "Edit objective…").doClick(), "Edit incident objective", dialog -> {
				((javax.swing.JTextArea) fieldValue(panel, "objectiveTextArea")).setText("Alpha");
				findButton(dialog, "Save").doClick();
			});
			assertEquals("Alpha", table.getValueAt(table.getSelectedRow(), 1));
			assertEquals(first.getObjectiveId(), controller.getData().getForm202().getStructuredObjectives()
					.get(table.convertRowIndexToModel(table.getSelectedRow())).getObjectiveId());
		});
	}

	@Test
	void rejectedAssignmentCheckboxDraftReopensPickerUntilItCanCommit() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		AppController controller = sampleController();
		IncidentObjective objective = new IncidentObjective("Objective", 0);
		controller.getData().getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment assignment = new ResourceAssignment();
		controller.getData().getForm204().setResourcesAssigned(List.of(assignment));
		SwingUtilities.invokeAndWait(() -> {
			Ics202Panel panel = new Ics202Panel(controller);
			panel.refreshFromModel();
			var accepted = new java.util.concurrent.atomic.AtomicBoolean();
			var appearances = new java.util.concurrent.atomic.AtomicInteger();
			var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
			var seen = new java.util.HashSet<JDialog>();
			var timer = new javax.swing.Timer(80, event -> {
				for (java.awt.Window window : java.awt.Window.getWindows()) {
					if (window instanceof JDialog dialog && dialog.isVisible()
							&& "Apply objective to assignments".equals(dialog.getTitle()) && seen.add(dialog)) {
						try {
							JTable assignments = findTable(dialog);
							if (appearances.incrementAndGet() == 1) {
								assignments.getColumnModel().getColumn(0)
										.setCellEditor(new javax.swing.DefaultCellEditor(new javax.swing.JCheckBox()) {
											@Override
											public boolean stopCellEditing() {
												return accepted.get() && super.stopCellEditing();
											}
										});
								assertTrue(assignments.editCellAt(0, 0));
								((javax.swing.JCheckBox) assignments.getEditorComponent()).setSelected(true);
							} else {
								assertTrue(assignment.getObjectiveIds().isEmpty());
								assertTrue(assignments.isEditing());
								assertTrue(((javax.swing.JCheckBox) assignments.getEditorComponent()).isSelected());
								accepted.set(true);
							}
							findButton(dialog, "Apply").doClick();
						} catch (Throwable exception) {
							failure.set(exception);
							dialog.dispose();
						}
					}
				}
			});
			timer.start();
			try {
				findButton(panel, "Apply to assignments…").doClick();
			} finally {
				timer.stop();
			}
			if (failure.get() != null) {
				throw new AssertionError(failure.get());
			}
			assertEquals(2, appearances.get());
			assertEquals(List.of(objective.getObjectiveId()), assignment.getObjectiveIds());
		});
	}

	private static void clickLinkCheckbox(JTable table, int viewRow) {
		assertTrue(table.editCellAt(viewRow, 0));
		((javax.swing.JCheckBox) table.getEditorComponent()).doClick();
	}

	private static void showAndAct(Object panel, String method, String title,
			java.util.function.Consumer<JDialog> action) {
		runDialogAction(() -> invoke(panel, method, new Class<?>[0]), title, action);
	}

	private static void runDialogAction(Runnable launch, String title,
			java.util.function.Consumer<JDialog> action) {
		var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		var found = new java.util.concurrent.atomic.AtomicBoolean();
		var timer = new javax.swing.Timer(80, event -> {
			for (java.awt.Window window : java.awt.Window.getWindows()) {
				if (window instanceof JDialog dialog && dialog.isVisible() && title.equals(dialog.getTitle())) {
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
		assertTrue(found.get(), "Dialog must open");
		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}
	}

	private static JButton findButton(java.awt.Component component, String text) {
		if (component instanceof JButton button && text.equals(button.getText())) {
			return button;
		}
		if (component instanceof java.awt.Container container) {
			for (var child : container.getComponents()) {
				JButton found = findButton(child, text);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static JTable findTable(java.awt.Component component) {
		if (component instanceof JTable table) {
			return table;
		}
		if (component instanceof java.awt.Container container) {
			for (var child : container.getComponents()) {
				JTable found = findTable(child);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static Object invoke(Object instance, String name, Class<?>[] types, Object... arguments) {
		try {
			var method = instance.getClass().getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method.invoke(instance, arguments);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Test
	void sarTaskAssignmentIncludedFormRoundTripsThroughPanel() throws Exception {
		AppController controller = sampleController();
		controller.getData().getForm202().setIncidentActionPlanAttachments(List.of("SAR Task Assignment"));
		Ics202Panel panel = new Ics202Panel(controller);

		JCheckBox[] checkbox = new JCheckBox[1];
		SwingUtilities.invokeAndWait(() -> {
			panel.refreshFromModel();
			checkbox[0] = (JCheckBox) fieldValue(panel, "includeSarTaskAssignment");
		});

		assertTrue(checkbox[0].isSelected());

		SwingUtilities.invokeAndWait(() -> {
			checkbox[0].setSelected(false);
			panel.pushToModel();
		});

		assertFalse(
				controller.getData().getForm202().getIncidentActionPlanAttachments().contains("SAR Task Assignment"));
	}

	@Test
	void safetyAttachmentsRoundTripAndDriveExportDialogDefaults() throws Exception {
		AppController controller = sampleController();
		controller.getData().getForm202().setIncidentActionPlanAttachments(List.of("ICS 215A"));
		Ics202Panel panel = new Ics202Panel(controller);
		JCheckBox[] checkbox = new JCheckBox[1];
		SwingUtilities.invokeAndWait(() -> {
			panel.refreshFromModel();
			checkbox[0] = (JCheckBox) fieldValue(panel, "includeIcs215a");
		});

		assertTrue(checkbox[0].isSelected());
		assertTrue(MainFrame.isExportFormSelectedByDefault("ICS 215A", controller.getData()));
		assertFalse(MainFrame.isExportFormSelectedByDefault("ICS 208", controller.getData()));
		assertEquals("ICS 208 – Safety Message / Plan", MainFrame.exportFormDisplayLabel("ICS 208"));
		assertEquals("ICS 215A – Incident Action Plan Safety Analysis",
				MainFrame.exportFormDisplayLabel("ICS 215A"));
		assertEquals(List.of("ICS 204", "SAR Task Assignment", "ICS 205A", "ICS 207", "ICS 208", "ICS 214", "ICS 215A"),
				MainFrame.sortExportFormKeys(
						List.of("ICS 215A", "SAR Task Assignment", "ICS 208", "ICS 214", "ICS 207", "ICS 205A", "ICS 204")));
		assertEquals(List.of("ICS 204", "ICS 208", "ICS 215A"),
				MainFrame.mergeSelectedFormsWithIcs202Attachments(List.of("ICS 204", "ICS 208"),
						List.of("ICS 215A", "ICS 208")));

		SwingUtilities.invokeAndWait(() -> {
			checkbox[0].setSelected(false);
			panel.pushToModel();
		});
		assertFalse(controller.getData().getForm202().getIncidentActionPlanAttachments().contains("ICS 215A"));
	}

	@Test
	void medicalPlanInclusionControlsAreDrivenByIcs202Attachments() throws Exception {
		AppController controller = sampleController();
		Ics202Panel panel = new Ics202Panel(controller);

		SwingUtilities.invokeAndWait(() -> {
			((JCheckBox) fieldValue(panel, "includeIcs206")).setSelected(true);
			((JCheckBox) fieldValue(panel, "includeCanine206")).setSelected(true);
			panel.pushToModel();
		});

		assertTrue(controller.getData().getMedicalPlan().isActive());
		assertTrue(controller.getData().getCanineMedicalPlan().isActive());
		assertTrue(controller.getData().getForm202().getIncidentActionPlanAttachments().contains("ICS 206"));
		assertTrue(controller.getData().getForm202().getIncidentActionPlanAttachments().contains("ICS 206CANINE"));

		SwingUtilities.invokeAndWait(() -> {
			((JCheckBox) fieldValue(panel, "includeIcs206")).setSelected(false);
			panel.pushToModel();
		});

		assertFalse(controller.getData().getMedicalPlan().isActive());
		assertTrue(controller.getData().getCanineMedicalPlan().isActive());
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

	private static AppController sampleController() throws Exception {
		IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"),
				LocalDateTime.parse("2026-01-01T12:00:00"), "Planner", "Planning Section Chief");
		AppData data = new AppData(context, new Ics202Form(), new Ics204Form(), List.of());
		return new AppController(data,
				new LocalRepository(Files.createTempDirectory("icsforms-ui").resolve("incident.json")),
				new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
						new SarTaskAssignmentPdfRenderer()),
				new IncidentValidator());
	}
}
