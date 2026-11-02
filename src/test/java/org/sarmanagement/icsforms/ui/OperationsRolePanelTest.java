/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies Operations context, selection retention, and canonical resource assignment. */
class OperationsRolePanelTest {
	@TempDir
	Path directory;

	@Test
	void showsObjectiveAndDebriefContextAndPreservesSelection() throws Exception {
		AppData data = new AppData();
		IncidentObjective objective = new IncidentObjective("Search the north sector", 0);
		data.getForm202().setStructuredObjectives(List.of(objective));
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("assignment-1");
		assignment.setObjectiveIds(List.of(objective.getObjectiveId()));
		data.getForm204().getResourcesAssigned().add(assignment);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-1");
		task.setAssignmentTeamNumber("Team 1");
		task.setAssignment("North sector");
		task.setDebriefNotes("Area searched");
		data.getSarTaskAssignments().add(task);
		AppController controller = controller(data);
		OperationsRolePanel[] panel = new OperationsRolePanel[1];

		SwingUtilities.invokeAndWait(() -> panel[0] = createPanel(controller));
		assertEquals("assignment-1", panel[0].selectedAssignmentId());
		assertTrue(((JTextArea) field(panel[0], "detail")).getText().contains("Search the north sector"));
		assertTrue(((JTextArea) field(panel[0], "detail")).getText().contains("Area searched"));

		SarTaskAssignment second = new SarTaskAssignment();
		second.setAssignmentId("assignment-2");
		data.getSarTaskAssignments().add(second);
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("assignment-1", panel[0].selectedAssignmentId());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void selectedRecordedResourcesUseCanonicalTaskOperationWithoutDuplicates() {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-1");
		data.getSarTaskAssignments().add(task);
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("assignment-1");
		data.getForm204().getResourcesAssigned().add(assignment);
		TCard card = new TCard();
		card.setPersonName("Responder One");
		card.setStatus("Available");
		card.setLocation("Staging");
		data.getTCards().add(card);
		AppController controller = controller(data);

		controller.assignRecordedResources("assignment-1", List.of(card.getResourceId(), card.getResourceId()));

		assertEquals(1, task.getResourcesAssigned().size());
		assertEquals(card.getResourceId(), task.getResourcesAssigned().get(0).getResourceId());
		assertTrue(card.getStatus().isBlank() || "Available".equalsIgnoreCase(card.getStatus()));
	}

	@Test
	void operationalPlanningActionOpensTheSharedWorksheetCallback() throws Exception {
		AppController controller = controller(new AppData());
		AtomicBoolean opened = new AtomicBoolean();
		OperationsRolePanel[] panel = new OperationsRolePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new OperationsRolePanel(controller, () -> null, ignored -> {
			}, ignored -> {
			}, ignored -> {
			}, () -> {
			}, ignored -> {
			}, () -> {
			}, () -> {
			}, () -> opened.set(true));
			JButton action = findButton(panel[0], "Operational planning / ICS 215");
			assertTrue(action != null);
			action.doClick();
		});
		assertTrue(opened.get());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	@Test
	void availableTcardsAppearAndTaskMenuRestrictsDebriefUntilReturned() throws Exception {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-1");
		task.setTaskLifecycleStatus("on task");
		data.getSarTaskAssignments().add(task);
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("assignment-1");
		data.getForm204().getResourcesAssigned().add(assignment);
		TCard card = new TCard();
		card.setPersonName("Responder One");
		card.setStatus("Available");
		data.getTCards().add(card);
		AppController controller = controller(data);
		OperationsRolePanel[] panel = new OperationsRolePanel[1];

		SwingUtilities.invokeAndWait(() -> panel[0] = createPanel(controller));
		JList<?> resources = (JList<?>) field(panel[0], "availableResources");
		assertEquals(1, resources.getModel().getSize());
		assertEquals("assignment-1", panel[0].selectedAssignmentId());
		JList<?> taskBoard = (JList<?>) field(panel[0], "taskBoard");
		JPopupMenu popup = taskBoard.getComponentPopupMenu();
		assertTrue(menuItem(popup, "Add log entry…") != null);
		assertFalse(findButton(panel[0], "Debrief task…").isEnabled());
		for (javax.swing.event.PopupMenuListener listener : popup.getPopupMenuListeners()) {
			listener.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(popup));
		}
		assertFalse(menuItem(popup, "Debrief task…").isEnabled());

		task.setTaskLifecycleStatus("returned");
		assertEquals("returned", task.getTaskLifecycleStatus());
		SwingUtilities.invokeAndWait(panel[0]::refreshFromModel);
		for (javax.swing.event.PopupMenuListener listener : popup.getPopupMenuListeners()) {
			listener.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(popup));
		}
		assertTrue(menuItem(popup, "Debrief task…").isEnabled());
		SwingUtilities.invokeAndWait(panel[0]::dispose);
	}

	private OperationsRolePanel createPanel(AppController controller) {
		return new OperationsRolePanel(controller, () -> null, ignored -> {
		}, ignored -> {
		}, ignored -> {
		}, () -> {
		}, ignored -> {
		}, () -> {
		}, () -> {
		});
	}

	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}

	private Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	/**
	 * Finds a role-panel action by its visible label.
	 *
	 * @param component component tree.
	 * @param label requested button label.
	 * @return matching button or null.
	 */
	private JButton findButton(Component component, String label) {
		if (component instanceof JButton button && label.equals(button.getText())) {
			return button;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton result = findButton(child, label);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	private JMenuItem menuItem(JPopupMenu menu, String label) {
		for (Component component : menu.getComponents()) {
			if (component instanceof JMenuItem item && label.equals(item.getText())) {
				return item;
			}
		}
		return null;
	}
}
