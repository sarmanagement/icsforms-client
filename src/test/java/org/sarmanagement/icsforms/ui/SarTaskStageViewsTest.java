/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import javax.swing.AbstractButton;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies shared SAR stage colour coding and the views that use it. */
class SarTaskStageViewsTest {
	@Test
	void classifiesTasksByLifecycleAndDebrief() {
		assertEquals(SarTaskStage.PLANNED, SarTaskStage.of(task("a", "planned", null)));
		assertEquals(SarTaskStage.ASSIGNED, SarTaskStage.of(task("a", "assigned - on task", null)));
		assertEquals(SarTaskStage.RETURNED, SarTaskStage.of(task("a", "returned", " ")));
		assertEquals(SarTaskStage.COMPLETED, SarTaskStage.of(task("a", "returned", "Pat")));
	}

	@Test
	void miniBoardGroupsTasksAndSkipsUnchangedRefresh() {
		assumeFalse(GraphicsEnvironment.isHeadless());
		SarTaskMiniBoard board = new SarTaskMiniBoard(null);
		List<SarTaskAssignment> tasks = List.of(task("1", "planned", null), task("2", "assigned - on task", null),
				task("3", "assigned - enroute to assignment", null));
		board.refresh(tasks);
		assertEquals(1, board.taskCount(SarTaskStage.PLANNED));
		assertEquals(2, board.taskCount(SarTaskStage.ASSIGNED));
		Component column = firstColumn(board);
		board.refresh(tasks);
		assertTrue(column == firstColumn(board));
	}

	@Test
	void operationsListRowsUseStageColours() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		data.getSarTaskAssignments().add(task("1", "assigned - on task", null));
		AppController controller = IncidentStoreTestFixtures.controller(data);
		controller.getData().getSarTaskAssignments().clear();
		controller.getData().getSarTaskAssignments().add(task("1", "assigned - on task", null));
		OperationsRolePanel[] panel = new OperationsRolePanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new OperationsRolePanel(controller, () -> null, ignored -> {
		}, ignored -> {
		}, ignored -> {
		}, () -> {
		}, ignored -> {
		}, () -> {
		}, () -> {
		}));
		try {
			SwingUtilities.invokeAndWait(panel[0]::refreshFromModel);
			JList<?> list = (JList<?>) field(panel[0], "taskBoard");
			Component row = renderRow(list);
			assertEquals(SarTaskStage.ASSIGNED.color(), row.getBackground());
		} finally {
			SwingUtilities.invokeAndWait(panel[0]::dispose);
		}
	}

	@Test
	void commandPanelOffersTaskBoardOnlyInSarMode() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		AppData data = new AppData();
		data.setIncidentMode(IncidentMode.SAR);
		data.getSarTaskAssignments().add(task("1", "planned", null));
		AppController controller = IncidentStoreTestFixtures.controller(data);
		controller.getData().setIncidentMode(IncidentMode.SAR);
		controller.getData().getSarTaskAssignments().clear();
		controller.getData().getSarTaskAssignments().add(task("1", "planned", null));
		IncidentCommandRolePanel[] panel = new IncidentCommandRolePanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new IncidentCommandRolePanel(controller, ignored -> {
		}));
		try {
			SwingUtilities.invokeAndWait(panel[0]::refreshFromModel);
			AbstractButton toggle = (AbstractButton) field(panel[0], "resourceBoardToggle");
			assertTrue(toggle.isVisible());
			SwingUtilities.invokeAndWait(toggle::doClick);
			SarTaskMiniBoard board = (SarTaskMiniBoard) field(panel[0], "resourceTaskBoard");
			assertEquals(1, board.taskCount(SarTaskStage.PLANNED));
			JTable table = (JTable) field(panel[0], "resources");
			for (int column = 1; column < table.getColumnCount(); column++) {
				assertTrue(table.getColumnModel().getColumn(column).getPreferredWidth() <= 90);
			}
		} finally {
			SwingUtilities.invokeAndWait(panel[0]::dispose);
		}
	}

	private static Component renderRow(JList<?> list) {
		@SuppressWarnings("unchecked")
		JList<Object> typed = (JList<Object>) list;
		return typed.getCellRenderer().getListCellRendererComponent(typed, typed.getModel().getElementAt(0), 0,
				false, false);
	}

	private static Component firstColumn(Container board) {
		List<Component> found = new ArrayList<>();
		collectPanels(board, found);
		return found.stream().filter(c -> c instanceof javax.swing.JPanel p
				&& p.getLayout() instanceof java.awt.GridLayout).findFirst().orElseThrow();
	}

	private static void collectPanels(Container container, List<Component> found) {
		for (Component child : container.getComponents()) {
			found.add(child);
			if (child instanceof Container nested) {
				collectPanels(nested, found);
			}
		}
	}

	private static SarTaskAssignment task(String id, String status, String supervisor) {
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId(id);
		task.setTaskLifecycleStatus(status);
		task.setDebriefingSupervisor(supervisor);
		return task;
	}

	private static Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
