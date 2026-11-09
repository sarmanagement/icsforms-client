/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.SarTaskAssignment;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Compact read-only version of the SAR task lifecycle board. Each task is one short, colour-coded
 * line in the column for its stage, and double-clicking a task opens it through the supplied
 * callback.
 */
final class SarTaskMiniBoard extends JPanel {
	private final Consumer<String> openTask;
	private final JPanel columns = new JPanel(new GridLayout(1, SarTaskStage.values().length, 4, 0));
	private List<Object> displayed;

	/**
	 * Creates an empty board.
	 *
	 * @param openTask called with the assignment identifier of a double-clicked task; may be null.
	 */
	SarTaskMiniBoard(Consumer<String> openTask) {
		super(new BorderLayout());
		this.openTask = openTask;
		getAccessibleContext().setAccessibleName("Compact SAR task board");
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(columns, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(wrapper, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		add(scroll, BorderLayout.CENTER);
	}

	/**
	 * Shows the supplied tasks, rebuilding the board only when the displayed content differs.
	 *
	 * @param tasks tasks in model order.
	 */
	void refresh(List<SarTaskAssignment> tasks) {
		Map<SarTaskStage, List<SarTaskAssignment>> byStage = new EnumMap<>(SarTaskStage.class);
		for (SarTaskStage stage : SarTaskStage.values()) {
			byStage.put(stage, new ArrayList<>());
		}
		for (SarTaskAssignment task : tasks) {
			byStage.get(SarTaskStage.of(task)).add(task);
		}
		List<Object> content = new ArrayList<>();
		for (SarTaskStage stage : SarTaskStage.values()) {
			content.add(stage);
			byStage.get(stage).forEach(task -> content.add(List.of(task.getAssignmentId(), taskLabel(task),
					taskToolTip(task))));
		}
		if (content.equals(displayed)) {
			return;
		}
		displayed = content;
		columns.removeAll();
		for (SarTaskStage stage : SarTaskStage.values()) {
			columns.add(buildColumn(stage, byStage.get(stage)));
		}
		columns.revalidate();
		columns.repaint();
	}

	/**
	 * Returns the number of task lines displayed in a stage column.
	 *
	 * @param stage board column.
	 * @return number of displayed tasks.
	 */
	int taskCount(SarTaskStage stage) {
		int index = stage.ordinal();
		JPanel column = (JPanel) columns.getComponent(index);
		int count = 0;
		for (Component child : column.getComponents()) {
			if (child instanceof JLabel label && label.getClientProperty("assignmentId") != null) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Builds one stage column.
	 *
	 * @param stage stage shown in the column.
	 * @param tasks tasks in the stage.
	 * @return column panel.
	 */
	private JPanel buildColumn(SarTaskStage stage, List<SarTaskAssignment> tasks) {
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		JLabel header = new JLabel(stage.label() + " (" + tasks.size() + ")");
		header.putClientProperty("html.disable", Boolean.TRUE);
		header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
		header.setOpaque(true);
		header.setBackground(stage.color());
		header.setForeground(Color.BLACK);
		header.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
		column.add(header);
		column.add(Box.createVerticalStrut(2));
		for (SarTaskAssignment task : tasks) {
			JLabel line = new JLabel(taskLabel(task));
			line.putClientProperty("html.disable", Boolean.TRUE);
			line.putClientProperty("assignmentId", task.getAssignmentId());
			line.setToolTipText(taskToolTip(task));
			line.setOpaque(true);
			line.setBackground(stage.color().brighter());
			line.setForeground(Color.BLACK);
			line.setFont(line.getFont().deriveFont(Font.PLAIN, 11f));
			line.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY),
					BorderFactory.createEmptyBorder(1, 4, 1, 4)));
			line.setAlignmentX(Component.LEFT_ALIGNMENT);
			line.setMinimumSize(new Dimension(0, line.getPreferredSize().height));
			line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
			line.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					if (event.getClickCount() == 2 && openTask != null) {
						openTask.accept(task.getAssignmentId());
					}
				}
			});
			column.add(line);
			column.add(Box.createVerticalStrut(2));
		}
		column.add(Box.createVerticalGlue());
		return column;
	}

	/**
	 * Builds the short line shown for a task.
	 *
	 * @param task task to describe.
	 * @return team number, or resource identifier when no team number is recorded.
	 */
	private static String taskLabel(SarTaskAssignment task) {
		String team = task.getAssignmentTeamNumber();
		if (team != null && !team.isBlank()) {
			return team;
		}
		String resource = task.getResourceIdentifier();
		return resource != null && !resource.isBlank() ? resource : "(no team number)";
	}

	/**
	 * Builds the tooltip describing a task in full.
	 *
	 * @param task task to describe.
	 * @return status and assignment text.
	 */
	private static String taskToolTip(SarTaskAssignment task) {
		return taskLabel(task) + " | " + task.getTaskLifecycleStatus() + " | " + task.getAssignment();
	}
}
