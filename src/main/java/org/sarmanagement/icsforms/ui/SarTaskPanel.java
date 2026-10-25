/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.RowFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.EventObject;
import java.util.Set;

/**
 * Editable SAR task assignment and debriefing grid linked from ICS 204 resource
 * assignments.
 */
public class SarTaskPanel extends JPanel {
	private java.util.function.BiConsumer<Component, SarTaskAssignment> onAddLogEntryRequest;

	/**
	 * Installs contextual logging without discarding unsaved editor fields.
	 *
	 * @param handler callback receiving the launching editor and task.
	 */
	public void setOnAddLogEntryRequest(java.util.function.BiConsumer<Component, SarTaskAssignment> handler) {
		onAddLogEntryRequest = handler;
	}
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final int MAX_RESOURCE_ROWS = 18;
	/**
	 * Read-only columns in the summary table view: Status (0), Resource (4), Leader
	 * (5), People (6).
	 */
	private static final Set<Integer> READ_ONLY_COLUMNS = Set.of(0, 4, 5, 6);
	private static final int RESOURCE_EDITOR_WIDTH = 320;
	private static final int RESOURCE_EDITOR_VISIBLE_ROWS = 9;
	private static final int RESOURCE_EDITOR_PADDING = 8;
	private static final int CLUE_EDITOR_WIDTH = 720;
	private static final int SCORE_FIELD_WIDTH = 48;
	private static final List<String> CANINE_SEARCH_TYPE_OPTIONS = List.of("", "Wilderness air scent", "Tracking",
			"Trailing", "Tracking/Trailing", "HRD", "Article", "Patrol", "Water", "Other");
	private static final List<String> CANINE_IMPRINT_OPTIONS = List.of("", "Living human", "HRD", "Both live and HRD",
			"Article/Track");
	private static final String CANINE_WEATHER_FACTOR_NAME = "Weather/Temperature";

	private final AppController controller;
	private final SarTaskTableModel tableModel = new SarTaskTableModel();
	private final JTable table = new JTable(tableModel);
	private final TableRowSorter<SarTaskTableModel> rowSorter = new TableRowSorter<>(tableModel);
	private final JComboBox<String> statusFilterCombo = new JComboBox<>(
			new String[]{"All", "planned", "assigned", "returned"});
	private java.util.function.Consumer<SarTaskAssignment> on214Request;
	/**
	 * Callback that opens the "Create ICS 204 Assignment" dialog and returns the
	 * new assignment.
	 */
	private java.util.function.Supplier<ResourceAssignment> onNewAssignmentRequest;
	private java.util.function.Consumer<String> onEditIcs204AssignmentRequest;

	private static final String VIEW_TABLE = "table";
	private static final String VIEW_BOARD = "board";
	private final CardLayout viewCardLayout = new CardLayout();
	private final JPanel viewContainer = new JPanel(viewCardLayout);
	private final JScrollPane boardScroll = new JScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
	private final JButton toggleBoardBtn = new JButton("Board View");
	private String currentSarView = VIEW_TABLE;

	/**
	 * Creates the SAR task assignment panel.
	 *
	 * @param controller
	 *            application controller.
	 */
	public SarTaskPanel(AppController controller) {
		super(new BorderLayout());
		this.controller = controller;
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		table.setFillsViewportHeight(true);
		// Col 0 = Task Status (color-coded by lifecycle); col 1 = Team # (required
		// field highlight).
		table.getColumnModel().getColumn(0).setCellRenderer(new LifecycleStatusCellRenderer());
		table.getColumnModel().getColumn(1).setCellRenderer(new RequiredFieldCellRenderer());
		table.setRowSorter(rowSorter);
		installRowEditor();
		setBorder(BorderFactory.createTitledBorder("SAR Task Assignment / Debriefing"));
		JPanel tablePanel = new JPanel(new BorderLayout());
		tablePanel.add(buildFilterPanel(), BorderLayout.NORTH);
		tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);
		viewContainer.add(tablePanel, VIEW_TABLE);
		viewContainer.add(boardScroll, VIEW_BOARD);
		add(viewContainer, BorderLayout.CENTER);
		add(buildButtonPanel(), BorderLayout.SOUTH);
	}

	/** Builds the filter bar shown above the task table. */
	private JPanel buildFilterPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		panel.add(new JLabel("Filter by status:"));
		panel.add(statusFilterCombo);
		statusFilterCombo.addActionListener(e -> applyStatusFilter());
		return panel;
	}

	/** Applies the selected status filter to the table row sorter. */
	private void applyStatusFilter() {
		String selected = (String) statusFilterCombo.getSelectedItem();
		if (selected == null || selected.equals("All")) {
			rowSorter.setRowFilter(null);
		} else if ("assigned".equalsIgnoreCase(selected)) {
			rowSorter.setRowFilter(new RowFilter<>() {
				@Override
				public boolean include(Entry<? extends SarTaskTableModel, ? extends Integer> entry) {
					String status = String.valueOf(entry.getValue(0)).toLowerCase(java.util.Locale.ROOT);
					return status.startsWith("assigned -");
				}
			});
		} else {
			rowSorter.setRowFilter(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(selected) + "$", 0));
		}
	}

	/** Builds the bottom action button row. */
	private JPanel buildButtonPanel() {
		JButton addBtn = new JButton("Add Task");
		JButton editBtn = new JButton("Edit Assignment…");
		JButton debriefBtn = new JButton("Debrief…");
		JButton open214Btn = new JButton("Open ICS 214…");
		JButton open204Btn = new JButton("Edit ICS 204 Assignment…");
		JButton removeBtn = new JButton("Remove Task");

		editBtn.setEnabled(false);
		debriefBtn.setEnabled(false);
		open214Btn.setEnabled(false);
		open204Btn.setEnabled(false);
		removeBtn.setEnabled(false);

		table.getSelectionModel().addListSelectionListener(event -> {
			SarTaskAssignment selectedTask = selectedTaskFromTable();
			boolean selected = selectedTask != null;
			editBtn.setEnabled(selected);
			debriefBtn.setEnabled(selected && isReadyForDebrief(selectedTask));
			open214Btn.setEnabled(selected && on214Request != null);
			open204Btn.setEnabled(selected && onEditIcs204AssignmentRequest != null);
			removeBtn.setEnabled(selected);
		});

		addBtn.addActionListener(e -> addNewTask());
		editBtn.addActionListener(e -> openSelectedRowEditor(EditorMode.ASSIGNMENT));
		debriefBtn.addActionListener(e -> openSelectedRowEditor(EditorMode.DEBRIEFING));
		open214Btn.addActionListener(e -> openIcs214ForSelected());
		open204Btn.addActionListener(e -> openIcs204ForSelected());
		removeBtn.addActionListener(e -> removeSelectedTask());
		toggleBoardBtn.addActionListener(e -> toggleBoardView());

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		row.add(addBtn);
		row.add(editBtn);
		row.add(debriefBtn);
		row.add(open214Btn);
		row.add(open204Btn);
		row.add(removeBtn);
		row.add(toggleBoardBtn);
		return row;
	}

	/** Toggles between the table view and the task lifecycle board view. */
	private void toggleBoardView() {
		if (currentSarView.equals(VIEW_TABLE)) {
			currentSarView = VIEW_BOARD;
			toggleBoardBtn.setText("Table View");
			rebuildBoardView();
			viewCardLayout.show(viewContainer, VIEW_BOARD);
		} else {
			currentSarView = VIEW_TABLE;
			toggleBoardBtn.setText("Board View");
			viewCardLayout.show(viewContainer, VIEW_TABLE);
		}
	}

	/**
	 * Rebuilds the task lifecycle board view from the current task list.
	 *
	 * <p>
	 * Tasks are shown in four columns based on their lifecycle status:
	 * <ol>
	 * <li><b>Planned</b> — taskLifecycleStatus is "Planned"</li>
	 * <li><b>On Task</b> — taskLifecycleStatus is "On Task"</li>
	 * <li><b>Returned</b> — taskLifecycleStatus is "Returned" but no debriefing
	 * supervisor set yet</li>
	 * <li><b>Completed</b> — taskLifecycleStatus is "Returned" and debriefing data
	 * has already been entered, though the debrief remains editable</li>
	 * </ol>
	 * Clicking a task card opens the assignment editor for that task.
	 * </p>
	 */
	private void rebuildBoardView() {
		List<SarTaskAssignment> tasks = tableModel.getRows();

		List<SarTaskAssignment> planning = new ArrayList<>();
		List<SarTaskAssignment> onTask = new ArrayList<>();
		List<SarTaskAssignment> returned = new ArrayList<>();
		List<SarTaskAssignment> completed = new ArrayList<>();

		for (SarTaskAssignment task : tasks) {
			String lc = task.getTaskLifecycleStatus();
			String normalized = lc == null ? "" : lc.trim().toLowerCase(java.util.Locale.ROOT);
			if (normalized.startsWith("assigned -")) {
				onTask.add(task);
			} else if ("returned".equals(normalized)) {
				if (task.getDebriefingSupervisor() != null && !task.getDebriefingSupervisor().isBlank()) {
					completed.add(task);
				} else {
					returned.add(task);
				}
			} else {
				planning.add(task); // "Planned" or unknown
			}
		}

		JPanel board = new JPanel(new GridLayout(1, 4, 8, 0));
		board.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		board.add(buildBoardColumn("planned", new Color(173, 216, 230), planning));
		board.add(buildBoardColumn("assigned", new Color(255, 200, 100), onTask));
		board.add(buildBoardColumn("returned (not debriefed)", new Color(144, 238, 144), returned));
		board.add(buildBoardColumn("Completed", new Color(200, 200, 200), completed));

		// Override getPreferredSize so the wrapper never exceeds the viewport width,
		// allowing GridLayout to divide the available space into four equal columns.
		JPanel boardWrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension d = super.getPreferredSize();
				Container p = getParent();
				if (p != null && p.getWidth() > 0) {
					return new Dimension(p.getWidth(), d.height);
				}
				return d;
			}
		};
		boardWrapper.add(board, BorderLayout.NORTH);
		boardScroll.setViewportView(boardWrapper);
		boardScroll.revalidate();
		boardScroll.repaint();
	}

	/** Builds a single Kanban board column for a lifecycle status. */
	private JPanel buildBoardColumn(String title, Color headerColor, List<SarTaskAssignment> tasks) {
		JPanel col = new JPanel();
		col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));
		col.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(headerColor);
		header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel titleLabel = new JLabel(title + " (" + tasks.size() + ")");
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
		header.add(titleLabel, BorderLayout.CENTER);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		col.add(header);
		col.add(javax.swing.Box.createVerticalStrut(4));

		for (SarTaskAssignment task : tasks) {
			col.add(buildBoardTaskCard(task, headerColor));
			col.add(javax.swing.Box.createVerticalStrut(4));
		}
		col.add(javax.swing.Box.createVerticalGlue());
		return col;
	}

	/**
	 * Builds a task card with actions available on its label children as well.
	 *
	 * @param task task represented by this card.
	 * @param bgColor lifecycle column color.
	 * @return interactive task card.
	 */
	private JPanel buildBoardTaskCard(SarTaskAssignment task, Color bgColor) {
		JPanel p = new JPanel(new BorderLayout(4, 2));
		p.setBackground(bgColor.brighter());
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY),
				BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

		String teamNum = task.getAssignmentTeamNumber();
		String resId = task.getResourceIdentifier();
		String leader = task.getLeader();
		int resourceCount = task.getResourcesAssigned() == null ? 0 : task.getResourcesAssigned().size();

		String icon = lifecycleStateIcon(task.getTaskLifecycleStatus());
		String heading = teamNum.isBlank() ? "(no team number)" : teamNum;
		JLabel topLabel = new JLabel((icon.isBlank() ? "" : icon + " ") + heading);
		topLabel.setFont(topLabel.getFont().deriveFont(Font.BOLD, 11f));

		StringBuilder detail = new StringBuilder();
		if (!resId.isBlank())
			detail.append(resId);
		if (!leader.isBlank()) {
			if (!detail.isEmpty())
				detail.append(" · ");
			detail.append("Leader: ").append(leader);
		}
		if (resourceCount > 0) {
			if (!detail.isEmpty())
				detail.append(" · ");
			detail.append(resourceCount).append(" resource(s)");
		}

		p.add(topLabel, BorderLayout.NORTH);
		if (!detail.isEmpty()) {
			JLabel detailLabel = new JLabel("<html><small>" + detail + "</small></html>");
			p.add(detailLabel, BorderLayout.CENTER);
		}

		// Right-click context menu (mirrors the table popup and bottom buttons).
		JPopupMenu cardMenu = new JPopupMenu();
		JMenuItem editItem = new JMenuItem("Edit assignment…");
		JMenuItem debriefItem = new JMenuItem("Debrief…");
		JMenuItem changeStatusItem = new JMenuItem("Change task status…");
		JMenuItem open214Item = new JMenuItem("Open ICS 214 for Task…");
		editItem.addActionListener(e -> openBoardTaskEditor(task, EditorMode.ASSIGNMENT));
		debriefItem.addActionListener(e -> openBoardTaskEditor(task, EditorMode.DEBRIEFING));
		debriefItem.setEnabled(isReadyForDebrief(task));
		changeStatusItem.addActionListener(e -> openQuickStatusDialog(task));
		open214Item.addActionListener(e -> {
			if (on214Request != null)
				on214Request.accept(task);
		});
		cardMenu.add(editItem);
		cardMenu.add(debriefItem);
		if (isPlannedOrAssigned(task.getTaskLifecycleStatus())) {
			cardMenu.add(changeStatusItem);
		}
		cardMenu.add(open214Item);
		JMenuItem addLogEntryItem = new JMenuItem("Add log entry…");
		addLogEntryItem.setEnabled(onAddLogEntryRequest != null);
		addLogEntryItem.addActionListener(event -> {
			if (onAddLogEntryRequest != null) {
				onAddLogEntryRequest.accept(p, task);
			}
		});
		cardMenu.add(addLogEntryItem);
		p.setComponentPopupMenu(cardMenu);

		// Double-click opens edit dialog; hover highlights border.
		MouseAdapter cardMouseListener = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					int modelRow = tableModel.getRows().indexOf(task);
					if (modelRow >= 0) {
						int viewRow = table.convertRowIndexToView(modelRow);
						if (viewRow >= 0) {
							table.setRowSelectionInterval(viewRow, viewRow);
						}
					}
				}
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					openBoardTaskEditor(task, EditorMode.ASSIGNMENT);
				}
			}
			@Override
			public void mouseEntered(MouseEvent e) {
				p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0, 80, 180), 2),
						BorderFactory.createEmptyBorder(4, 7, 4, 7)));
			}
			@Override
			public void mouseExited(MouseEvent e) {
				p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY),
						BorderFactory.createEmptyBorder(5, 8, 5, 8)));
			}
		};
		installBoardCardMouseActions(p, cardMouseListener);
		return p;
	}

	/**
	 * Installs card interaction and inherited popups on every child component.
	 *
	 * @param component card or child component.
	 * @param listener shared card mouse listener.
	 */
	private void installBoardCardMouseActions(Component component, MouseAdapter listener) {
		component.addMouseListener(listener);
		if (component instanceof javax.swing.JComponent child) {
			child.setInheritsPopupMenu(true);
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				installBoardCardMouseActions(child, listener);
			}
		}
	}

	/**
	 * Opens the assignment or debrief editor for a task clicked in the board view.
	 */
	private void openBoardTaskEditor(SarTaskAssignment task, EditorMode mode) {
		int modelRow = tableModel.getRows().indexOf(task);
		if (modelRow >= 0) {
			editRow(modelRow, mode);
			if (currentSarView.equals(VIEW_BOARD)) {
				rebuildBoardView();
			}
		}
	}

	/**
	 * Creates a persisted backing ICS 204 assignment, then opens its linked task.
	 */
	private void addNewTask() {
		if (onNewAssignmentRequest == null) {
			return;
		}
		ResourceAssignment linkedAssignment = onNewAssignmentRequest.get();
		if (linkedAssignment != null) {
			refreshFromModel();
			openAssignmentEditorById(linkedAssignment.getAssignmentId());
		}
	}

	/** Removes the currently selected SAR task row after confirmation. */
	private void removeSelectedTask() {
		int viewRow = table.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int modelRow = table.convertRowIndexToModel(viewRow);
		if (modelRow < 0 || modelRow >= tableModel.getRows().size()) {
			return;
		}
		SarTaskAssignment task = tableModel.getRows().get(modelRow);
		String label = task.getAssignmentTeamNumber().isBlank() ? "this task" : task.getAssignmentTeamNumber();
		int result = UiSupport.showResizableOptionDialog(this, "Remove Task", new JLabel("Remove " + label + "?"),
				null, new Object[]{"Remove", "Cancel"}, "Cancel");
		if (result != 0) {
			return;
		}
		List<SarTaskAssignment> rows = new ArrayList<>(controller.getData().getSarTaskAssignments());
		rows.removeIf(row -> task.getAssignmentId().equals(row.getAssignmentId()));
		controller.getData().setSarTaskAssignments(rows);
		tableModel.setRows(rows, controller.getData().getClueLogEntries(),
				controller.getAssignmentResources());
		controller.markDirty();
	}

	/**
	 * Reloads table rows from the latest synced SAR task assignments.
	 */
	public void refreshFromModel() {
		controller.syncSarTasks();
		tableModel.setRows(controller.getData().getSarTaskAssignments(), controller.getData().getClueLogEntries(),
				controller.getAssignmentResources());
		if (currentSarView.equals(VIEW_BOARD)) {
			rebuildBoardView();
		}
	}

	/**
	 * Commits inline edits without replacing newer canonical tasks with stale rows.
	 * Reconciles additions and deletions by assignment identity; separate modal
	 * editor fields are not refreshed or discarded.
	 */
	public void pushToModel() {
		if (table.isEditing() && table.getCellEditor() != null && !table.getCellEditor().stopCellEditing()) {
			throw new IllegalStateException("Finish editing the selected SAR task before saving.");
		}
		controller.syncIcs204ResourcesFromSarTasks();
		controller.syncSarTasks();
		tableModel.setRows(controller.getData().getSarTaskAssignments(), controller.getData().getClueLogEntries(),
				controller.getAssignmentResources());
	}

	/**
	 * Sets the callback invoked when the user requests to open/show the ICS 214 for
	 * a task.
	 *
	 * @param handler
	 *            callback accepting the selected {@link SarTaskAssignment}.
	 */
	public void setOn214Request(java.util.function.Consumer<SarTaskAssignment> handler) {
		this.on214Request = handler;
	}

	/**
	 * Sets the callback invoked when the user adds a new task, to first create a
	 * backing ICS 204 resource assignment record. The supplier opens the "Create
	 * ICS 204 Assignment" dialog and returns the new {@link ResourceAssignment}, or
	 * {@code null} if cancelled.
	 *
	 * @param handler
	 *            supplier that creates and returns a new
	 *            {@link ResourceAssignment}.
	 */
	public void setOnNewAssignmentRequest(java.util.function.Supplier<ResourceAssignment> handler) {
		this.onNewAssignmentRequest = handler;
	}

	public void setOnEditIcs204AssignmentRequest(java.util.function.Consumer<String> handler) {
		this.onEditIcs204AssignmentRequest = handler;
	}

	public boolean openAssignmentEditorById(String assignmentId) {
		if (assignmentId == null || assignmentId.isBlank()) {
			return false;
		}
		for (int i = 0; i < tableModel.getRows().size(); i++) {
			SarTaskAssignment row = tableModel.getRows().get(i);
			if (assignmentId.equals(row.getAssignmentId())) {
				int viewRow = table.convertRowIndexToView(i);
				if (viewRow >= 0) {
					table.setRowSelectionInterval(viewRow, viewRow);
				}
				editRow(i, EditorMode.ASSIGNMENT);
				return true;
			}
		}
		return false;
	}

	private void openIcs214ForSelected() {
		int viewRow = table.getSelectedRow();
		if (viewRow < 0 || on214Request == null) {
			return;
		}
		int modelRow = table.convertRowIndexToModel(viewRow);
		if (modelRow >= 0 && modelRow < tableModel.getRows().size()) {
			on214Request.accept(tableModel.getRows().get(modelRow));
		}
	}

	private void openIcs204ForSelected() {
		int viewRow = table.getSelectedRow();
		if (viewRow < 0 || onEditIcs204AssignmentRequest == null) {
			return;
		}
		int modelRow = table.convertRowIndexToModel(viewRow);
		if (modelRow >= 0 && modelRow < tableModel.getRows().size()) {
			onEditIcs204AssignmentRequest.accept(tableModel.getRows().get(modelRow).getAssignmentId());
		}
	}

	private static boolean isReadyForDebrief(SarTaskAssignment task) {
		if (task == null) {
			return false;
		}
		String lifecycle = safeValue(task.getTaskLifecycleStatus()).trim().toLowerCase(java.util.Locale.ROOT);
		return "returned".equals(lifecycle);
	}

	static String formatDateTimeValue(LocalDateTime value) {
		return value == null ? "" : DATE_TIME_FORMATTER.format(value);
	}

	static LocalDateTime parseDateTimeValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
		} catch (DateTimeParseException exception) {
			return null;
		}
	}

	private static String lifecycleStateIcon(String lifecycle) {
		if (lifecycle == null) {
			return "";
		}
		String value = lifecycle.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (value) {
			case "assigned - enroute to assignment" -> "🡺";
			case "assigned - on task" -> "●";
			case "assigned - returning from assignment" -> "↩";
			default -> "";
		};
	}

	private static boolean isAssignedState(String lifecycle) {
		return lifecycle != null && lifecycle.trim().toLowerCase(java.util.Locale.ROOT).startsWith("assigned -");
	}

	private static boolean isPlannedOrAssigned(String lifecycle) {
		if (lifecycle == null) {
			return false;
		}
		String normalized = lifecycle.trim().toLowerCase(java.util.Locale.ROOT);
		return "planned".equals(normalized) || normalized.startsWith("assigned -");
	}

	/**
	 * Collects returned-resource statuses without mutating the live model.
	 *
	 * @param task returning task.
	 * @return chosen statuses by resource identity, or an empty map when cancelled.
	 */
	private java.util.Map<String, String> promptResourceDispositionOnReturn(SarTaskAssignment task) {
		if (task == null || task.getResourcesAssigned() == null || task.getResourcesAssigned().isEmpty()) {
			return java.util.Map.of();
		}
		JPanel form = UiSupport.formPanel();
		java.util.Map<SarTaskResource, JComboBox<String>> statusByResource = new java.util.LinkedHashMap<>();
		int row = 0;
		for (SarTaskResource resource : task.getResourcesAssigned()) {
			JComboBox<String> statusCombo = new JComboBox<>(new String[]{"Available", "Out of Service"});
			statusByResource.put(resource, statusCombo);
			String name = resource.getName();
			if (name == null || name.isBlank()) {
				name = resource.getFunction();
			}
			UiSupport.addRow(form, row++, name == null || name.isBlank() ? "Resource" : name, statusCombo);
		}
		JScrollPane pane = new JScrollPane(form);
		pane.setBorder(BorderFactory.createEmptyBorder());
		if (!UiSupport.showResizableConfirmDialog(this, "Set returned resource status", pane,
				new Dimension(620, 360))) {
			return java.util.Map.of();
		}
		java.util.Map<String, String> updates = new java.util.HashMap<>();
		for (var entry : statusByResource.entrySet()) {
			SarTaskResource res = entry.getKey();
			String status = String.valueOf(entry.getValue().getSelectedItem());
			String mapped = "Out of Service".equalsIgnoreCase(status) ? "Out of Service" : "Available";
			if (res.getResourceId() != null && !res.getResourceId().isBlank()) {
				updates.put(res.getResourceId(), mapped);
			}
		}
		return updates;
	}

	/** Installs sorted-row editor, lifecycle and contextual logging actions. */
	private void installRowEditor() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem editAssignmentItem = new JMenuItem("Edit assignment…");
		editAssignmentItem.addActionListener(event -> openSelectedRowEditor(EditorMode.ASSIGNMENT));
		menu.add(editAssignmentItem);
		JMenuItem editDebriefingItem = new JMenuItem("Debrief…");
		editDebriefingItem.addActionListener(event -> openSelectedRowEditor(EditorMode.DEBRIEFING));
		menu.add(editDebriefingItem);
		JMenuItem changeStatusItem = new JMenuItem("Change task status…");
		changeStatusItem.addActionListener(event -> openQuickStatusForSelectedRow());
		menu.add(changeStatusItem);
		JMenuItem open214Item = new JMenuItem("Open ICS 214 for Task…");
		open214Item.addActionListener(event -> openIcs214ForSelected());
		menu.add(open214Item);
		JMenuItem addLogEntryItem = new JMenuItem("Add log entry…");
		addLogEntryItem.addActionListener(event -> addLogEntryForSelectedTask());
		menu.add(addLogEntryItem);
		menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
				SarTaskAssignment selected = selectedTaskFromTable();
				editDebriefingItem.setEnabled(selected != null && isReadyForDebrief(selected));
				changeStatusItem.setEnabled(selected != null && isPlannedOrAssigned(selected.getTaskLifecycleStatus()));
				addLogEntryItem.setEnabled(onAddLogEntryRequest != null && selected != null);
			}

			@Override
			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
			}

			@Override
			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
			}
		});
		menu.addSeparator();
		JMenuItem removeItem = new JMenuItem("Remove Task");
		removeItem.addActionListener(event -> removeSelectedTask());
		menu.add(removeItem);
		table.setComponentPopupMenu(menu);
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				selectRowAtEvent(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				selectRowAtEvent(event);
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
					selectRowAtEvent(event);
					openSelectedRowEditor(EditorMode.ASSIGNMENT);
				}
			}
		});
	}

	/** Launches contextual logging for the selected task, respecting sorting. */
	private void addLogEntryForSelectedTask() {
		SarTaskAssignment selected = selectedTaskFromTable();
		if (onAddLogEntryRequest != null && selected != null) {
			onAddLogEntryRequest.accept(table, selected);
		}
	}

	private void selectRowAtEvent(MouseEvent event) {
		int viewRow = table.rowAtPoint(event.getPoint());
		if (viewRow >= 0) {
			table.setRowSelectionInterval(viewRow, viewRow);
		}
	}

	private void openSelectedRowEditor(EditorMode mode) {
		int viewRow = table.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		editRow(table.convertRowIndexToModel(viewRow), mode);
	}

	private void openQuickStatusForSelectedRow() {
		SarTaskAssignment task = selectedTaskFromTable();
		if (task == null) {
			return;
		}
		openQuickStatusDialog(task);
	}

	/**
	 * Opens the established status workflow for a linked assignment.
	 *
	 * @param assignmentId stable assignment identifier.
	 */
	public void openTaskStatusDialogByAssignmentId(String assignmentId) {
		if (assignmentId == null || assignmentId.isBlank()) {
			return;
		}
		for (SarTaskAssignment task : tableModel.getRows()) {
			if (assignmentId.equals(task.getAssignmentId())) {
				openQuickStatusDialog(task);
				return;
			}
		}
	}

	private void openTaskDebriefDialog(String assignmentId, String assignmentTeamNumber) {
		List<SarTaskAssignment> tasks = tableModel.getRows();
		for (int i = 0; i < tasks.size(); i++) {
			SarTaskAssignment task = tasks.get(i);
			if (assignmentId != null && !assignmentId.isBlank() && assignmentId.equals(task.getAssignmentId())) {
				editRow(i, EditorMode.DEBRIEFING);
				return;
			}

			if ((assignmentId == null || assignmentId.isBlank()) && assignmentTeamNumber != null
					&& assignmentTeamNumber.equals(task.getAssignmentTeamNumber())) {
				editRow(i, EditorMode.DEBRIEFING);
				return;
			}
		}

	}

	/**
	 * Opens the established debrief workflow for a linked assignment.
	 *
	 * @param assignmentId stable assignment identifier.
	 */
	public void openDebriefByAssignmentId(String assignmentId) {
		for (SarTaskAssignment task : tableModel.getRows()) {
			if (assignmentId != null && assignmentId.equals(task.getAssignmentId())) {
				openTaskDebriefDialog(task.getAssignmentId(), task.getAssignmentTeamNumber());
				return;
			}
		}
	}

	/**
	 * Changes task status without replacing canonical rows with a stale table list.
	 *
	 * @param task task whose lifecycle is being edited.
	 */
	private void openQuickStatusDialog(SarTaskAssignment task) {
		if (task == null || !isPlannedOrAssigned(task.getTaskLifecycleStatus())) {
			return;
		}
		JComboBox<String> statusField = new JComboBox<>(SarTaskEditor.LIFECYCLE_OPTIONS);
		statusField.setSelectedItem(task.getTaskLifecycleStatus());
		statusField.setEditable(false);
		JPanel panel = UiSupport.formPanel();
		int row = 0;
		UiSupport.addRow(panel, row++, "Assignment/Team #", new JLabel(safeValue(task.getAssignmentTeamNumber())));
		UiSupport.addRow(panel, row++, "Resource", new JLabel(safeValue(task.getResourceIdentifier())));
		UiSupport.addRow(panel, row++, "Leader", new JLabel(safeValue(task.getLeader())));
		UiSupport.addRow(panel, row++, "Work assignment", new JLabel(shortAssignmentSummary(task.getAssignment())));
		UiSupport.addRow(panel, row, "Task status", statusField);
		if (!UiSupport.showResizableConfirmDialog(this, "Change Task Status", panel, new Dimension(700, 320))) {
			return;
		}
		String previous = task.getTaskLifecycleStatus();
		String updated = String.valueOf(statusField.getSelectedItem());
		if (java.util.Objects.equals(previous, updated)) {
			return;
		}
		if (isAssignedState(previous) && "returned".equalsIgnoreCase(updated.trim())) {
			controller.applyReturnedResourceStatuses(task, promptResourceDispositionOnReturn(task));
		}
		AppController.TaskLifecycleChangeResult result = controller.recordTaskLifecycleTransition(task, previous, updated,
				LocalDateTime.now().withSecond(0).withNano(0));
		showLifecycleWarning(result);
		controller.syncIcs204ResourcesFromSarTasks();
		tableModel.setRows(controller.getData().getSarTaskAssignments(), controller.getData().getClueLogEntries(),
				controller.getAssignmentResources());
		controller.markDirty();
		rebuildBoardView();
	}

	private static String shortAssignmentSummary(String assignment) {
		String text = safeValue(assignment).replace('\n', ' ').trim();
		if (text.length() <= 90) {
			return text;
		}
		return text.substring(0, 87) + "...";
	}

	private SarTaskAssignment selectedTaskFromTable() {
		int viewRow = table.getSelectedRow();
		if (viewRow < 0) {
			return null;
		}
		int modelRow = table.convertRowIndexToModel(viewRow);
		if (modelRow < 0 || modelRow >= tableModel.getRows().size()) {
			return null;
		}
		return tableModel.getRows().get(modelRow);
	}

	/**
	 * Opens an editor that must save successfully before closing or navigating.
	 *
	 * @param rowIndex task row index.
	 * @param mode assignment or debrief mode.
	 */
	private void editRow(int rowIndex, EditorMode mode) {
		if (rowIndex < 0 || rowIndex >= tableModel.getRowCount()) {
			return;
		}
		if (table.isEditing() && table.getCellEditor() != null) {
			table.getCellEditor().stopCellEditing();
		}
		SarTaskAssignment row = tableModel.getRows().get(rowIndex);
		SarTaskEditor editor = new SarTaskEditor(row, mode, controller.getData().getClueLogEntries(),
				defaultResourceEditorRowCount(row), handlerName -> controller.findEquipmentForHandler(handlerName),
				controller.getAvailableResourceNames(), controller.getAvailableTCards(),
				controller.getOnTaskResourceIds(), controller);
		JPanel content = sarTaskEditorDialogContent(editor.panel);
		String title = mode.dialogTitle(row.getAssignmentTeamNumber());
		Dimension size = mode == EditorMode.ASSIGNMENT ? new Dimension(1100, 680) : new Dimension(1100, 620);
		while (true) {
			java.util.List<Object> options = new ArrayList<>();
			if (mode == EditorMode.ASSIGNMENT && isReadyForDebrief(row)) {
				options.add("Save and open debrief…");
			}
			if (onEditIcs204AssignmentRequest != null && row.getAssignmentId() != null
					&& !row.getAssignmentId().isBlank()) {
				options.add("Save and open linked ICS 204 assignment…");
			}
			if (onAddLogEntryRequest != null) {
				JButton logButton = new JButton("Add log entry…");
				logButton.addActionListener(event -> onAddLogEntryRequest.accept(content, row));
				options.add(logButton);
			}
			int okIndex = options.size();
			options.add("Save");
			int cancelIndex = options.size();
			options.add("Cancel");
			int choice = UiSupport.showResizableOptionDialog(this, title, content, size, options.toArray(), "Save");
			if (choice < 0 || choice == cancelIndex) {
				return;
			}
			Object selected = options.get(choice);
			if (mode == EditorMode.ASSIGNMENT) {
				List<String> validationErrors = assignmentEditorValidationErrors(editor);
				if (!validationErrors.isEmpty()) {
					showValidationErrors(validationErrors);
					continue;
				}
			}
			try {
				saveTaskEditor(rowIndex, row, editor, mode);
			} catch (RuntimeException exception) {
				UiSupport.showDocumentMessage(content, exception.getMessage(), "Unable to save assignment",
						javax.swing.JOptionPane.ERROR_MESSAGE);
				continue;
			}
			if (choice == okIndex) {
				return;
			}
			if ("Save and open debrief…".equals(selected)) {
				openTaskDebriefDialog(row.getAssignmentId(), row.getAssignmentTeamNumber());
				return;
			}
			if ("Save and open linked ICS 204 assignment…".equals(selected) && onEditIcs204AssignmentRequest != null
					&& row.getAssignmentId() != null && !row.getAssignmentId().isBlank()) {
				onEditIcs204AssignmentRequest.accept(row.getAssignmentId());
				return;
			}
		}
	}

	/**
	 * Applies editor changes back to the selected task row and refreshes dependent
	 * UI/model state.
	 *
	 * @param rowIndex
	 *            edited model row index.
	 * @param row
	 *            task assignment being edited.
	 * @param editor
	 *            editor supplying current field values.
	 * @param mode editor mode.
	 * @param returnedStatuses statuses collected before the atomic save attempt.
	 * @return transition feedback, or null when no lifecycle transition occurred.
	 */
	private AppController.TaskLifecycleChangeResult applyEditorChanges(int rowIndex, SarTaskAssignment row,
			SarTaskEditor editor, EditorMode mode, java.util.Map<String, String> returnedStatuses) {
		String previousLifecycleStatus = row.getTaskLifecycleStatus();
		controller.getData().setClueLogEntries(editor.applyTo(row, controller.getData().getClueLogEntries()));
		String newLifecycleStatus = row.getTaskLifecycleStatus();
		AppController.TaskLifecycleChangeResult result = null;
		if (!java.util.Objects.equals(previousLifecycleStatus, newLifecycleStatus)) {
			if (isAssignedState(previousLifecycleStatus) && "returned".equalsIgnoreCase(newLifecycleStatus.trim())) {
				controller.applyReturnedResourceStatuses(row, returnedStatuses);
			}
			result = controller.recordTaskLifecycleTransition(row, previousLifecycleStatus, newLifecycleStatus,
					LocalDateTime.now().withSecond(0).withNano(0));
		}
		if (mode == EditorMode.ASSIGNMENT) {
			updateLinkedResourcePersonCount(row, editor.resourceCount());
		}
		controller.syncIcs204ResourcesFromSarTasks();
		tableModel.setRows(tableModel.getRows(), controller.getData().getClueLogEntries(),
				controller.getAssignmentResources());
		int viewRow = table.convertRowIndexToView(rowIndex);
		if (viewRow >= 0 && viewRow < table.getRowCount()) {
			table.setRowSelectionInterval(viewRow, viewRow);
		}
		return result;
	}

	/**
	 * Collects modal input first, then atomically applies and persists editor fields.
	 *
	 * @param rowIndex selected task row.
	 * @param row live task.
	 * @param editor entered fields retained on failure.
	 * @param mode editor mode.
	 * @throws RuntimeException when persistence fails after restoring model state.
	 */
	private void saveTaskEditor(int rowIndex, SarTaskAssignment row, SarTaskEditor editor, EditorMode mode) {
		java.util.Map<String, String> statuses = java.util.Map.of();
		if (mode == EditorMode.ASSIGNMENT
				&& java.util.Objects.equals(editor.initialLifecycleStatus, row.getTaskLifecycleStatus())
				&& isAssignedState(row.getTaskLifecycleStatus())
				&& "returned".equalsIgnoreCase(String.valueOf(editor.taskLifecycleField.getSelectedItem()).trim())) {
			statuses = promptResourceDispositionOnReturn(row);
		}
		String lifecycleBaseline = editor.initialLifecycleStatus;
		Set<String> clueBaseline = new java.util.HashSet<>(editor.initialClueIds);
		java.util.Map<String, String> followUpBaseline = new java.util.HashMap<>(editor.initialClueFollowUp);
		java.util.Map<String, String> returnedStatuses = statuses;
		AppController.TaskLifecycleChangeResult[] feedback = new AppController.TaskLifecycleChangeResult[1];
		try {
			controller.persistEditorChanges(() ->
					feedback[0] = applyEditorChanges(rowIndex, row, editor, mode, returnedStatuses));
		} catch (RuntimeException exception) {
			editor.initialLifecycleStatus = lifecycleBaseline;
			editor.initialClueIds.clear();
			editor.initialClueIds.addAll(clueBaseline);
			editor.initialClueFollowUp.clear();
			editor.initialClueFollowUp.putAll(followUpBaseline);
			tableModel.setRows(controller.getData().getSarTaskAssignments(), controller.getData().getClueLogEntries(),
					controller.getAssignmentResources());
			throw exception;
		}
		showLifecycleWarning(feedback[0]);
	}

	/**
	 * Returns assignment-editor validation errors that should block the debrief
	 * handoff.
	 *
	 * @param editor
	 *            assignment editor to validate.
	 * @return ordered validation errors.
	 */
	private List<String> assignmentEditorValidationErrors(SarTaskEditor editor) {
		if (editor == null) {
			return List.of("Unable to validate the SAR task editor.");
		}
		return editor.assignmentValidationErrors();
	}

	/**
	 * Shows task-editor validation errors.
	 *
	 * @param errors
	 *            validation errors to display.
	 */
	private void showValidationErrors(List<String> errors) {
		if (errors == null || errors.isEmpty()) {
			return;
		}
		StringBuilder builder = new StringBuilder("Please resolve the following before opening debrief:\n\n");
		for (String error : errors) {
			builder.append("- ").append(error).append('\n');
		}
		UiSupport.showDocumentMessage(this, builder.toString(), "Validation required", JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Shows a lifecycle warning returned by the controller.
	 *
	 * @param result
	 *            lifecycle change result to inspect.
	 */
	private void showLifecycleWarning(AppController.TaskLifecycleChangeResult result) {
		if (result == null || !result.hasWarning()) {
			return;
		}
		UiSupport.showDocumentMessage(this, result.warningMessage(), "SAR Task Status", JOptionPane.WARNING_MESSAGE);
	}

	private JPanel sarTaskEditorDialogContent(JPanel editorPanel) {
		JScrollPane scrollPane = new JScrollPane(editorPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.setOpaque(false);
		content.add(scrollPane, BorderLayout.CENTER);
		return content;
	}

	/**
	 * Updates the person count on the backing resource in any assignment form.
	 *
	 * @param row edited task.
	 * @param resourceCount assigned person count.
	 */
	private void updateLinkedResourcePersonCount(SarTaskAssignment row, int resourceCount) {
		ResourceAssignment assignment = SarTaskTableModel.findLinkedAssignment(row,
				controller.getAssignmentResources());
		if (assignment != null) {
			assignment.setNumberOfPersons(resourceCount);
		}
	}

	/**
	 * Determines initial resource rows using the linked assignment's person count.
	 *
	 * @param row task being edited.
	 * @return bounded initial resource row count.
	 */
	private int defaultResourceEditorRowCount(SarTaskAssignment row) {
		int existing = editableResources(row).size();
		int persons = 0;
		for (ResourceAssignment assignment : controller.getAssignmentResources()) {
			if (assignment == null) {
				continue;
			}
			if (!row.getAssignmentId().isBlank() && row.getAssignmentId().equals(assignment.getAssignmentId())) {
				persons = assignment.getNumberOfPersons();
				break;
			}
			if (row.getAssignmentId().isBlank() && !row.getAssignmentTeamNumber().isBlank()
					&& row.getAssignmentTeamNumber().equals(assignment.getAssignmentTeamNumber())) {
				persons = assignment.getNumberOfPersons();
				break;
			}
		}
		int minimum = persons > 0 ? persons : 1;
		return Math.min(MAX_RESOURCE_ROWS, Math.max(existing, minimum));
	}

	private static JTextField textField(String value, boolean editable) {
		return textField(value, editable, 24);
	}

	private static JTextField textField(String value, boolean editable, int columns) {
		JTextField field = UiSupport.textField();
		field.setColumns(columns);
		field.setText(value == null ? "" : value);
		field.setEditable(editable);
		return field;
	}

	private static JComboBox<String> comboBox(List<String> options, String value) {
		JComboBox<String> field = new JComboBox<>(options.toArray(String[]::new));
		field.setEditable(true);
		field.setSelectedItem(value == null ? "" : value);
		return field;
	}

	private static JSpinner dateTimeSpinner(LocalDateTime value, int width) {
		JSpinner spinner = UiSupport.dateTimeSpinner();
		if (width > 0) {
			spinner.setPreferredSize(new Dimension(width, spinner.getPreferredSize().height));
		}
		if (value == null) {
			spinnerTextField(spinner).setText("");
		} else {
			spinner.setValue(AppController.toDate(value));
		}
		return spinner;
	}

	private static JTextField spinnerTextField(JSpinner spinner) {
		return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
	}

	private static LocalDateTime spinnerDateTimeValue(JSpinner spinner) {
		String text = spinnerTextField(spinner).getText().trim();
		if (text.isBlank()) {
			return null;
		}
		try {
			spinner.commitEdit();
			return AppController.toLocalDateTime((Date) spinner.getValue());
		} catch (ParseException exception) {
			return null;
		}
	}

	private static JScrollPane textArea(String value, int rows, boolean editable) {
		JTextArea area = UiSupport.textArea(rows);
		area.setText(value == null ? "" : value);
		area.setEditable(editable);
		return new JScrollPane(area);
	}

	private static JTextArea textAreaFrom(JScrollPane scrollPane) {
		if (!(scrollPane.getViewport().getView() instanceof JTextArea area)) {
			throw new IllegalArgumentException("Expected a JTextArea inside the scroll pane");
		}
		return area;
	}

	/**
	 * Builds resource assignment controls with document-scoped nested pickers.
	 *
	 * @param row backing task.
	 * @param model editable resource rows.
	 * @param minimumRows minimum initial row count.
	 * @param availableNames resource autocomplete choices.
	 * @param canineNamesFirst canine names to place first.
	 * @param availableTCards available resource cards.
	 * @param onTaskResourceIds committed card identities.
	 * @param parentComponent owning assignment editor.
	 * @param controller resource creation controller.
	 * @return resource editor panel.
	 */
	private static JPanel resourceEditorPanel(SarTaskAssignment row, ResourceEntriesTableModel model, int minimumRows,
			List<String> availableNames, List<String> canineNamesFirst, List<TCard> availableTCards,
			Set<String> onTaskResourceIds, java.awt.Component parentComponent, AppController controller) {
		model.setRows(editableResources(row, canineNamesFirst), minimumRows);
		JTable table = new JTable(model);
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		table.setFillsViewportHeight(true);

		// Fix width of the non-editable Type column.
		table.getColumnModel().getColumn(0).setPreferredWidth(90);
		table.getColumnModel().getColumn(0).setMaxWidth(120);

		// Install picker-based cell editor on the Name column (index 2).
		if (availableNames != null && !availableNames.isEmpty()) {
			JTextField nameEditorField = UiSupport.textField();
			UiSupport.installNameAutocomplete(nameEditorField, () -> availableNames, nameEditorField::setText,
					proposedName -> {
						if (controller == null) {
							return "";
						}
						TCard created = controller.createPersonnelCardViaDialog(proposedName, "", "");
						return created == null ? "" : created.getPersonName();
					});
			javax.swing.DefaultCellEditor nameEditor = new javax.swing.DefaultCellEditor(nameEditorField) {
				private transient EventObject triggerEvent;

				{
					setClickCountToStart(1);
				}

				@Override
				public boolean isCellEditable(EventObject event) {
					triggerEvent = event;
					return super.isCellEditable(event);
				}

				@Override
				public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
						int column) {
					Component editorComponent = super.getTableCellEditorComponent(table, value, isSelected, row,
							column);
					if (editorComponent instanceof JTextField field && (triggerEvent instanceof MouseEvent
							|| triggerEvent instanceof java.awt.event.KeyEvent)) {
						SwingUtilities.invokeLater(() -> UiSupport.openInstalledNamePicker(field));
					}
					return editorComponent;
				}

				@Override
				public Object getCellEditorValue() {
					Object selected = nameEditorField.getText();
					return selected == null ? "" : selected.toString().trim();
				}
			};
			table.getColumnModel().getColumn(2).setCellEditor(nameEditor);
		}

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setPreferredSize(
				new Dimension(RESOURCE_EDITOR_WIDTH, table.getRowHeight() * RESOURCE_EDITOR_VISIBLE_ROWS
						+ table.getTableHeader().getPreferredSize().height + RESOURCE_EDITOR_PADDING));

		JButton addButton = new JButton("Add…");
		addButton.addActionListener(event -> {
			if (table.isEditing() && table.getCellEditor() != null) {
				table.getCellEditor().stopCellEditing();
			}
			List<SarTaskResource> existingOnTask = existingResourcesOnTask(row, model);
			List<SarTaskResource> picked = showResourcePickerDialog(parentComponent, availableTCards,
					onTaskResourceIds, existingOnTask);
			if (picked != null) {
				for (SarTaskResource res : picked) {
					model.addRow(res);
				}
			}
		});
		JButton removeButton = new JButton("Remove");
		removeButton.setEnabled(false);
		table.getSelectionModel()
				.addListSelectionListener(event -> removeButton.setEnabled(table.getSelectedRow() >= 0));
		removeButton.addActionListener(event -> {
			if (table.isEditing() && table.getCellEditor() != null) {
				table.getCellEditor().stopCellEditing();
			}
			int selected = table.getSelectedRow();
			if (selected < 0) {
				return;
			}
			SarTaskResource resource = model.getRows().get(selected);
			String label = resource.getName().isBlank() ? resource.getFunction() : resource.getName();
			int confirm = UiSupport.showResizableOptionDialog(parentComponent, "Remove Resource",
					new JLabel("Remove resource '" + (label.isBlank() ? ("row " + (selected + 1)) : label)
							+ "' from this task?"),
					null, new Object[]{"Remove", "Cancel"}, "Cancel");
			if (confirm == 0) {
				model.removeRow(selected);
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		buttons.add(addButton);
		buttons.add(removeButton);

		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		panel.add(scrollPane, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Collects resources currently assigned to the task, including in-progress editor
	 * rows, the task leader, and the primary task resource identifier.
	 *
	 * @param row task being edited, or {@code null}.
	 * @param model live table model containing edited resources, or {@code null}.
	 * @return list of existing assigned resources; never {@code null}.
	 */
	private static List<SarTaskResource> existingResourcesOnTask(SarTaskAssignment row,
			ResourceEntriesTableModel model) {
		List<SarTaskResource> list = new ArrayList<>();
		if (model != null) {
			for (SarTaskResource res : model.getRows()) {
				if (res != null) {
					String rid = res.getResourceId();
					String name = res.getName();
					if ((rid != null && !rid.isBlank()) || (name != null && !name.isBlank())) {
						list.add(res);
					}
				}
			}
		}
		if (row != null) {
			String resId = row.getResourceIdentifier();
			if (resId != null && !resId.isBlank()) {
				SarTaskResource primary = new SarTaskResource();
				primary.setName(resId.trim());
				list.add(primary);
			}
			String leader = row.getLeader();
			if (leader != null && !leader.isBlank()) {
				SarTaskResource leaderRes = new SarTaskResource();
				leaderRes.setName(leader.trim());
				list.add(leaderRes);
			}
		}
		return list;
	}

	/**
	 * Checks whether a T-card matches any resource already on the task.
	 *
	 * @param card card to test.
	 * @param taskResourceIds set of resource IDs already on the task.
	 * @param taskResourceNames set of lowercase resource names already on the task.
	 * @return {@code true} if the card is already on this task.
	 */
	private static boolean isCardOnThisTask(TCard card, Set<String> taskResourceIds,
			Set<String> taskResourceNames) {
		if (card == null) {
			return false;
		}
		String cardId = card.getResourceId();
		if (cardId != null && !cardId.isBlank() && taskResourceIds != null
				&& taskResourceIds.contains(cardId.trim())) {
			return true;
		}
		if (taskResourceNames == null || taskResourceNames.isEmpty()) {
			return false;
		}
		String personName = card.getPersonName();
		if (personName != null && !personName.isBlank()
				&& taskResourceNames.contains(personName.trim().toLowerCase(java.util.Locale.ROOT))) {
			return true;
		}
		String resourceIdentifier = card.getResourceIdentifier();
		if (resourceIdentifier != null && !resourceIdentifier.isBlank()
				&& taskResourceNames.contains(resourceIdentifier.trim().toLowerCase(java.util.Locale.ROOT))) {
			return true;
		}
		String effectiveName = card.getCardType() != TCardType.PERSONNEL
				? (resourceIdentifier == null ? "" : resourceIdentifier.trim())
				: (personName == null ? "" : personName.trim());
		if (!effectiveName.isBlank()
				&& taskResourceNames.contains(effectiveName.toLowerCase(java.util.Locale.ROOT))) {
			return true;
		}
		return false;
	}

	/**
	 * Opens a dialog to pick one or more T-card resources or manually enter a new
	 * one.
	 *
	 * <p>
	 * By default only resources that are not currently committed to an active ("On
	 * Task") assignment are shown. A "Show all resources" checkbox reveals all
	 * T-cards so that a subsequent task can be planned for resources still on task.
	 * Multiple rows may be selected using the standard Ctrl/Shift click
	 * conventions; all selected cards are added to the task in a single action.
	 * </p>
	 *
	 * @param parent
	 *            parent component for the dialog.
	 * @param availableTCards
	 *            full list of T-cards (filtered internally based on the checkbox).
	 * @param onTaskResourceIds
	 *            set of resource IDs currently committed to an active task; cards
	 *            with these IDs are hidden in the default (non-show-all) view.
	 * @return a non-empty list of populated {@link SarTaskResource} objects, or
	 *         {@code null} if the user cancelled or made no valid selection.
	 */
	private static List<SarTaskResource> showResourcePickerDialog(java.awt.Component parent,
			List<TCard> availableTCards, Set<String> onTaskResourceIds) {
		return showResourcePickerDialog(parent, availableTCards, onTaskResourceIds, List.of());
	}

	/**
	 * Opens a dialog to pick one or more T-card resources or manually enter a new
	 * one, excluding resources already assigned to this task.
	 *
	 * <p>
	 * Resources already on this task are hidden in the default view and disabled when
	 * showing all resources to prevent duplicate assignments on the same task.
	 * </p>
	 *
	 * @param parent
	 *            parent component for the dialog.
	 * @param availableTCards
	 *            full list of T-cards (filtered internally based on the checkbox).
	 * @param onTaskResourceIds
	 *            set of resource IDs currently committed to an active task; cards
	 *            with these IDs are hidden in the default (non-show-all) view.
	 * @param existingTaskResources
	 *            resources already assigned to this task that must not be duplicated.
	 * @return a non-empty list of populated {@link SarTaskResource} objects, or
	 *         {@code null} if the user cancelled or made no valid selection.
	 */
	private static List<SarTaskResource> showResourcePickerDialog(java.awt.Component parent,
			List<TCard> availableTCards, Set<String> onTaskResourceIds,
			List<SarTaskResource> existingTaskResources) {
		List<TCard> allCards = availableTCards == null ? List.<TCard>of() : availableTCards;
		Set<String> busyIds = onTaskResourceIds == null ? Set.of() : onTaskResourceIds;
		Set<String> taskResourceIds = new HashSet<>();
		Set<String> taskResourceNames = new HashSet<>();
		if (existingTaskResources != null) {
			for (SarTaskResource res : existingTaskResources) {
				if (res != null) {
					String rid = res.getResourceId();
					if (rid != null && !rid.isBlank()) {
						taskResourceIds.add(rid.trim());
					}
					String name = res.getName();
					if (name != null && !name.isBlank()) {
						taskResourceNames.add(name.trim().toLowerCase(java.util.Locale.ROOT));
					}
				}
			}
		}

		// Build a table model for the T-card picker.
		String[] cols = {"Type", "Name / Identifier", "Agency", "Location", "Status"};
		javax.swing.table.DefaultTableModel pickerModel = new javax.swing.table.DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};

		// currentCardList tracks which cards are currently shown so row indices stay
		// valid.
		java.util.concurrent.atomic.AtomicReference<List<TCard>> currentCardList = new java.util.concurrent.atomic.AtomicReference<>(
				new ArrayList<>(allCards));

		java.util.function.Consumer<Boolean> rebuildModel = (showAll) -> {
			pickerModel.setRowCount(0);
			List<TCard> filtered = showAll
					? new ArrayList<>(allCards)
					: allCards.stream().filter(c -> {
						if (isCardOnThisTask(c, taskResourceIds, taskResourceNames)) {
							return false;
						}
						return c.getResourceId().isBlank() || !busyIds.contains(c.getResourceId());
					}).collect(java.util.stream.Collectors.toList());
			currentCardList.set(filtered);
			for (TCard card : filtered) {
				String effectiveName = card.getCardType() != TCardType.PERSONNEL
						? card.getResourceIdentifier().trim()
						: card.getPersonName().trim();
				boolean onThisTask = isCardOnThisTask(card, taskResourceIds, taskResourceNames);
				String status = card.getStatus() == null ? "" : card.getStatus().trim();
				if (onThisTask) {
					status = status.isEmpty() ? "On this task" : status + " (on this task)";
				}
				pickerModel.addRow(new Object[]{
						card.getCardType() == null ? TCardType.PERSONNEL.getLabel() : card.getCardType().getLabel(),
						effectiveName, card.getHomeAgency() == null ? "" : card.getHomeAgency().trim(),
						card.getLocation() == null ? "" : card.getLocation().trim(),
						status});
			}
		};
		rebuildModel.accept(false);

		java.util.function.Predicate<Integer> isRowOnThisTask = (rowIdx) -> {
			List<TCard> list = currentCardList.get();
			if (rowIdx < 0 || rowIdx >= list.size()) {
				return false;
			}
			return isCardOnThisTask(list.get(rowIdx), taskResourceIds, taskResourceNames);
		};

		DefaultListSelectionModel selectionModel = new DefaultListSelectionModel() {
			@Override
			public void setSelectionInterval(int index0, int index1) {
				int min = Math.min(index0, index1);
				int max = Math.max(index0, index1);
				clearSelection();
				for (int i = min; i <= max; i++) {
					if (!isRowOnThisTask.test(i)) {
						super.addSelectionInterval(i, i);
					}
				}
			}

			@Override
			public void addSelectionInterval(int index0, int index1) {
				int min = Math.min(index0, index1);
				int max = Math.max(index0, index1);
				for (int i = min; i <= max; i++) {
					if (!isRowOnThisTask.test(i)) {
						super.addSelectionInterval(i, i);
					}
				}
			}
		};
		selectionModel.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		JTable pickerTable = new JTable(pickerModel) {
			@Override
			public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
				if (isRowOnThisTask.test(rowIndex)) {
					return;
				}
				super.changeSelection(rowIndex, columnIndex, toggle, extend);
			}

			@Override
			public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);
				if (isRowOnThisTask.test(row)) {
					c.setForeground(java.awt.Color.GRAY);
					if (c instanceof JComponent jc) {
						jc.setToolTipText("Already assigned to this task");
					}
				} else {
					if (!isRowSelected(row)) {
						c.setForeground(getForeground());
					}
					if (c instanceof JComponent jc) {
						jc.setToolTipText(null);
					}
				}
				return c;
			}
		};
		pickerTable.setSelectionModel(selectionModel);
		pickerTable.getTableHeader().setReorderingAllowed(false);
		java.util.concurrent.atomic.AtomicBoolean addViaDoubleClick = new java.util.concurrent.atomic.AtomicBoolean(
				false);
		pickerTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
					int row = pickerTable.rowAtPoint(e.getPoint());
					if (row >= 0 && isRowOnThisTask.test(row)) {
						return;
					}
					if (row >= 0 && !pickerTable.isRowSelected(row)) {
						pickerTable.setRowSelectionInterval(row, row);
					}
					if (pickerTable.getSelectedRowCount() > 0) {
						addViaDoubleClick.set(true);
						Window window = SwingUtilities.getWindowAncestor(pickerTable);
						if (window != null) {
							window.dispose();
						}
					}
				}
			}
		});
		JScrollPane pickerScroll = new JScrollPane(pickerTable);
		pickerScroll.setPreferredSize(new Dimension(520, 220));

		JLabel hint = new JLabel("Select one or more resources (Ctrl/Shift+click for multiple):");
		hint.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 2, 0));

		JCheckBox showAllCheckBox = new JCheckBox("Show all resources (including those currently on task)");
		showAllCheckBox.addActionListener(e -> {
			pickerTable.clearSelection();
			rebuildModel.accept(showAllCheckBox.isSelected());
		});

		JPanel topPanel = new JPanel(new BorderLayout(0, 2));
		topPanel.add(hint, BorderLayout.NORTH);
		topPanel.add(showAllCheckBox, BorderLayout.SOUTH);

		// Manual-entry fields shown below the table for creating a new resource.
		JTextField manualNameField = new JTextField(16);
		JTextField manualFunctionField = new JTextField(12);
		JComboBox<TCardType> manualTypeField = new JComboBox<>(
				new TCardType[]{TCardType.PERSONNEL, TCardType.CREW, TCardType.EQUIPMENT, TCardType.MISC_EQUIPMENT,
						TCardType.FIXED_WING, TCardType.HELICOPTER, TCardType.GENERIC});

		JPanel manualPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		manualPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Or enter manually:"));
		manualPanel.add(new JLabel("Type:"));
		manualPanel.add(manualTypeField);
		manualPanel.add(new JLabel("Name:"));
		manualPanel.add(manualNameField);
		manualPanel.add(new JLabel("Function:"));
		manualPanel.add(manualFunctionField);

		JPanel dialogPanel = new JPanel(new BorderLayout(0, 6));
		dialogPanel.add(topPanel, BorderLayout.NORTH);
		dialogPanel.add(pickerScroll, BorderLayout.CENTER);
		dialogPanel.add(manualPanel, BorderLayout.SOUTH);

		while (true) {
			int result = UiSupport.showResizableOptionDialog(parent, "Add Resource", dialogPanel,
					new Dimension(820, 520), new Object[]{"Add Selected", "Close"}, "Add Selected");
			if (result != 0 && !addViaDoubleClick.get()) {
				return null;
			}
			List<TCard> cardList = currentCardList.get();

			// Build results from all selected table rows.
			int[] selectedRows = pickerTable.getSelectedRows();
			if (selectedRows.length > 0) {
				List<SarTaskResource> results = new ArrayList<>();
				for (int selectedRow : selectedRows) {
					if (selectedRow < 0 || selectedRow >= cardList.size()) {
						continue;
					}
					TCard card = cardList.get(selectedRow);
					if (isCardOnThisTask(card, taskResourceIds, taskResourceNames)) {
						continue;
					}
					SarTaskResource res = new SarTaskResource();
					res.setCardType(card.getCardType());
					res.setResourceId(card.getResourceId());
					boolean isNonPersonnel = card.getCardType() != TCardType.PERSONNEL;
					String resourceName = card.getResourceIdentifier().trim();
					res.setName(isNonPersonnel ? resourceName : card.getPersonName().trim());
					res.setHomeAgency(card.getHomeAgency() == null ? "" : card.getHomeAgency().trim());
					results.add(res);
				}
				if (!results.isEmpty()) {
					return results;
				}
			}

			// Otherwise use the manual entry fields.
			String manualName = manualNameField.getText().trim();
			if (!manualName.isBlank()) {
				String lowerManual = manualName.toLowerCase(java.util.Locale.ROOT);
				if (taskResourceNames.contains(lowerManual)) {
					UiSupport.showDocumentMessage(parent,
							"Resource '" + manualName + "' is already assigned to this task.",
							"Duplicate Resource", JOptionPane.WARNING_MESSAGE);
					continue;
				}
				SarTaskResource res = new SarTaskResource();
				res.setName(manualName);
				res.setFunction(manualFunctionField.getText().trim());
				res.setCardType((TCardType) manualTypeField.getSelectedItem());
				return List.of(res);
			}

			if (addViaDoubleClick.get()) {
				addViaDoubleClick.set(false);
				return null;
			}
			UiSupport.showDocumentMessage(parent, "Select one or more resources, or enter a manual name.",
					"Add Resource", JOptionPane.WARNING_MESSAGE);
		}
	}

	private static JPanel compactFieldRowPanel(LabeledComponent... fields) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		panel.setOpaque(false);
		for (LabeledComponent field : fields) {
			panel.add(new JLabel(field.label()));
			panel.add(field.component());
		}
		return panel;
	}

	private static List<SarTaskResource> editableResources(SarTaskAssignment row) {
		return editableResources(row, List.of());
	}

	private static List<SarTaskResource> editableResources(SarTaskAssignment row, List<String> canineNamesFirst) {
		List<SarTaskResource> resources = new ArrayList<>();
		if (row == null || row.getResourcesAssigned() == null) {
			return resources;
		}
		for (SarTaskResource resource : row.getResourcesAssigned()) {
			if (resource == null || isPrimaryTaskResource(row, resource)) {
				continue;
			}
			resources.add(resource);
		}
		if (canineNamesFirst != null && !canineNamesFirst.isEmpty()) {
			resources.sort((a, b) -> {
				boolean aCanine = canineNamesFirst.contains(a.getName());
				boolean bCanine = canineNamesFirst.contains(b.getName());
				if (aCanine == bCanine) {
					return 0;
				}
				return aCanine ? -1 : 1;
			});
		}
		return resources;
	}

	/**
	 * Builds detached communications fields while retaining explicit channel references.
	 *
	 * @param communications existing communications.
	 * @param fields destination for editable rows.
	 * @return communications editor.
	 */
	private static JPanel communicationEditorPanel(List<CommunicationEntry> communications,
			List<CommunicationEntryFields> fields) {
		JPanel panel = new JPanel(new GridLayout(0, 3, 6, 3));
		panel.setOpaque(false);
		panel.add(new JLabel("Name"));
		panel.add(new JLabel("Function"));
		panel.add(new JLabel("Primary contact"));
		int rowCount = Math.max(3, (communications == null ? 0 : communications.size()) + 1);
		for (int i = 0; i < rowCount; i++) {
			CommunicationEntryFields entry = new CommunicationEntryFields();
			if (communications != null && i < communications.size()) {
				entry.nameField.setText(communications.get(i).getName());
				entry.functionField.setText(communications.get(i).getFunction());
				entry.primaryContactField.setText(communications.get(i).getPrimaryContact());
				entry.originalPrimaryContact = communications.get(i).getPrimaryContact();
				entry.originalRadioChannelId = communications.get(i).getRadioChannelId();
			}
			fields.add(entry);
			panel.add(entry.nameField);
			panel.add(entry.functionField);
			panel.add(entry.primaryContactField);
		}
		return panel;
	}

	private static JPanel clueEditorPanel(ClueEntriesTableModel model, List<ClueLogEntry> clues) {
		model.setRows(clues);
		JTable table = new JTable(model);
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		table.setFillsViewportHeight(true);
		JScrollPane scrollPane = new JScrollPane(table);
		updateClueEditorSize(scrollPane, table, model.getRowCount());
		model.addTableModelListener(event -> updateClueEditorSize(scrollPane, table, model.getRowCount()));

		JButton addButton = new JButton("Add");
		addButton.addActionListener(event -> model.addRow());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		buttons.add(addButton);

		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		panel.add(scrollPane, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.SOUTH);
		return panel;
	}

	private static void updateClueEditorSize(JScrollPane scrollPane, JTable table, int rowCount) {
		int visibleRows = Math.max(1, rowCount);
		Insets insets = scrollPane.getInsets();
		int height = table.getRowHeight() * visibleRows + table.getTableHeader().getPreferredSize().height + insets.top
				+ insets.bottom;
		Dimension size = new Dimension(CLUE_EDITOR_WIDTH, height);
		scrollPane.setPreferredSize(size);
		scrollPane.setMinimumSize(size);
		scrollPane.revalidate();
		scrollPane.repaint();
	}

	private static List<SarTaskResource> resourceValuesFrom(ResourceEntriesTableModel model) {
		List<SarTaskResource> resources = new ArrayList<>();
		for (SarTaskResource entry : model.getRows()) {
			String function = entry.getFunction() == null ? "" : entry.getFunction().trim();
			String name = entry.getName() == null ? "" : entry.getName().trim();
			if (function.isBlank() && name.isBlank()) {
				continue;
			}
			if (resources.size() >= MAX_RESOURCE_ROWS) {
				break;
			}
			SarTaskResource resource = new SarTaskResource();
			resource.setFunction(function);
			resource.setName(name);
			resource.setCardType(entry.getCardType());
			resource.setResourceId(entry.getResourceId());
			resources.add(resource);
		}
		return resources;
	}

	private static boolean isPrimaryTaskResource(SarTaskAssignment row, SarTaskResource resource) {
		if (row == null || resource == null) {
			return false;
		}
		String resourceName = resource.getName() == null ? "" : resource.getName().trim();
		String resourceFunction = resource.getFunction() == null ? "" : resource.getFunction().trim();
		String taskResourceIdentifier = row.getResourceIdentifier() == null ? "" : row.getResourceIdentifier().trim();
		return !taskResourceIdentifier.isBlank() && resourceName.equalsIgnoreCase(taskResourceIdentifier)
				&& (resourceFunction.isBlank() || "Resource".equalsIgnoreCase(resourceFunction));
	}

	private static JPanel inlineSummaryPanel(int columns, String... items) {
		JPanel panel = new JPanel(new GridLayout(0, columns, 8, 4));
		panel.setOpaque(false);
		for (String item : items) {
			panel.add(new JLabel(item));
		}
		return panel;
	}

	private static JPanel inlineFieldPanel(LabeledComponent... fields) {
		JPanel panel = new JPanel(new GridLayout(1, fields.length, 8, 0));
		panel.setOpaque(false);
		for (LabeledComponent field : fields) {
			JPanel cell = new JPanel(new BorderLayout(0, 2));
			cell.setOpaque(false);
			cell.add(new JLabel(field.label()), BorderLayout.NORTH);
			cell.add(field.component(), BorderLayout.CENTER);
			panel.add(cell);
		}
		return panel;
	}

	/**
	 * Rebuilds communications without discarding links whose fallback text is unchanged.
	 *
	 * @param fields edited communications rows.
	 * @return detached communications with changed contact references cleared.
	 */
	private static List<CommunicationEntry> communicationValuesFrom(List<CommunicationEntryFields> fields) {
		List<CommunicationEntry> communications = new ArrayList<>();
		for (CommunicationEntryFields entry : fields) {
			if (entry.nameField.getText().isBlank() && entry.functionField.getText().isBlank()
					&& entry.primaryContactField.getText().isBlank()) {
				continue;
			}
			CommunicationEntry communication = new CommunicationEntry();
			communication.setName(entry.nameField.getText().trim());
			communication.setFunction(entry.functionField.getText().trim());
			communication.setPrimaryContact(entry.primaryContactField.getText().trim());
			if (communication.getPrimaryContact().equals(entry.originalPrimaryContact)) {
				communication.setRadioChannelId(entry.originalRadioChannelId);
			}
			communications.add(communication);
		}
		return communications;
	}

	/**
	 * Builds clue records from debrief rows while retaining follow-up metadata.
	 * @param entries edited clue rows
	 * @param row linked SAR assignment
	 * @param detectingTask display label of the detecting task
	 * @return updated clue records
	 */
	private static List<ClueLogEntry> clueValuesFrom(List<ClueLogEntry> entries, SarTaskAssignment row,
			String detectingTask) {
		List<ClueLogEntry> clues = new ArrayList<>();
		for (ClueLogEntry entry : entries) {
			if (entry == null) {
				continue;
			}
			if (formatDateTimeValue(entry.getDateTimeCollected()).isBlank() && entry.getLocation().isBlank()
					&& entry.getDescription().isBlank() && entry.getFollowUp().isBlank()) {
				continue;
			}
			ClueLogEntry clue = new ClueLogEntry();
			clue.setAssignmentId(row.getAssignmentId());
			clue.setDetectingTask(detectingTask);
			clue.setDateTimeCollected(entry.getDateTimeCollected());
			clue.setLocation(entry.getLocation().trim());
			clue.setDescription(entry.getDescription().trim());
			clue.setImmediateAction(entry.getImmediateAction().trim());
			clue.setPossibleDuplicate(entry.isPossibleDuplicate());
			clue.setFollowUp(entry.getFollowUp().trim());
			copyFollowUp(entry, clue);
			clues.add(clue);
		}
		return clues;
	}

	/**
	 * Copies stable identity and follow-up fields untouched by the debrief editor.
	 * @param source source clue
	 * @param target destination clue
	 */
	private static void copyFollowUp(ClueLogEntry source, ClueLogEntry target) {
		target.setEntryId(source.getEntryId());
		target.setDetectedBy(source.getDetectedBy());
		target.setFollowUpAssignments(source.getFollowUpAssignments());
		target.setFollowUpBriefed(source.isFollowUpBriefed());
		target.setFollowUpTargetDate(source.getFollowUpTargetDate());
		target.setFollowUpCompletedDate(source.getFollowUpCompletedDate());
		target.setFollowUpStatus(source.getFollowUpStatus());
	}

	private static List<ClueLogEntry> cluesForTask(SarTaskAssignment row, List<ClueLogEntry> clues) {
		List<ClueLogEntry> matches = new ArrayList<>();
		if (clues == null) {
			return matches;
		}
		for (ClueLogEntry clue : clues) {
			if (clue == null) {
				continue;
			}
			if (!row.getAssignmentId().isBlank() && row.getAssignmentId().equals(clue.getAssignmentId())) {
				matches.add(clue);
				continue;
			}
			if (row.getAssignmentId().isBlank() && !row.getAssignmentTeamNumber().isBlank()
					&& row.getAssignmentTeamNumber().equals(clue.getDetectingTask())) {
				matches.add(clue);
			}
		}
		return matches;
	}

	private static String safeValue(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}

	private record LabeledComponent(String label, JComponent component) {
	}

	/** Detached communications controls with the original reference and fallback. */
	private static class CommunicationEntryFields {
		private final JTextField nameField = UiSupport.textField();
		private final JTextField functionField = UiSupport.textField();
		private final JTextField primaryContactField = UiSupport.textField();
		private String originalPrimaryContact = "";
		private String originalRadioChannelId = "";
	}

	private static class ClueEntriesTableModel extends AbstractTableModel {
		private final String[] columns = {"Date/Time", "Location", "Description", "Immediate Action", "Poss. Dup",
				"Follow Up"};
		private final List<ClueLogEntry> rows = new ArrayList<>();

		/**
		 * Copies clue rows so cancelled debrief edits do not change stored clues.
		 * @param clues clues for the current task
		 */
		private void setRows(List<ClueLogEntry> clues) {
			rows.clear();
			if (clues != null) {
				for (ClueLogEntry clue : clues) {
					ClueLogEntry copy = new ClueLogEntry();
					copy.setAssignmentId(clue.getAssignmentId());
					copy.setDetectingTask(clue.getDetectingTask());
					copy.setDateTimeCollected(clue.getDateTimeCollected());
					copy.setLocation(clue.getLocation());
					copy.setDescription(clue.getDescription());
					copy.setImmediateAction(clue.getImmediateAction());
					copy.setPossibleDuplicate(clue.isPossibleDuplicate());
					copy.setFollowUp(clue.getFollowUp());
					copyFollowUp(clue, copy);
					rows.add(copy);
				}
			}
			if (rows.isEmpty()) {
				rows.add(new ClueLogEntry());
			}
			fireTableDataChanged();
		}

		private List<ClueLogEntry> getRows() {
			return rows;
		}

		private void addRow() {
			rows.add(new ClueLogEntry());
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return columns.length;
		}

		@Override
		public String getColumnName(int column) {
			return columns[column];
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return true;
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnIndex == 4 ? Boolean.class : String.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			ClueLogEntry row = rows.get(rowIndex);
			return switch (columnIndex) {
				case 0 -> formatDateTimeValue(row.getDateTimeCollected());
				case 1 -> row.getLocation();
				case 2 -> row.getDescription();
				case 3 -> row.getImmediateAction();
				case 4 -> row.isPossibleDuplicate();
				default -> row.getFollowUp();
			};
		}

		@Override
		public void setValueAt(Object value, int rowIndex, int columnIndex) {
			ClueLogEntry row = rows.get(rowIndex);
			switch (columnIndex) {
				case 0 -> row.setDateTimeCollected(parseDateTimeValue(value == null ? "" : value.toString().trim()));
				case 1 -> row.setLocation(value == null ? "" : value.toString().trim());
				case 2 -> row.setDescription(value == null ? "" : value.toString().trim());
				case 3 -> row.setImmediateAction(value == null ? "" : value.toString().trim());
				case 4 -> row.setPossibleDuplicate(Boolean.TRUE.equals(value));
				default -> row.setFollowUp(value == null ? "" : value.toString().trim());
			}
			fireTableCellUpdated(rowIndex, columnIndex);
		}
	}

	private static class PodFactorEntryFields {
		private final String factorName;
		private final int maxScore;
		private final JTextField scoreField = textField("", true, 2);
		private final JTextField descriptionField = UiSupport.textField();

		private PodFactorEntryFields(PodFactorRating rating, boolean descriptionAllowed) {
			this.factorName = rating.getName();
			this.maxScore = rating.getMaxScore();
			this.scoreField.setText(rating.getScore() == null ? "" : String.valueOf(rating.getScore()));
			this.scoreField.setHorizontalAlignment(JTextField.RIGHT);
			Dimension preferredSize = this.scoreField.getPreferredSize();
			this.scoreField.setPreferredSize(new Dimension(SCORE_FIELD_WIDTH, preferredSize.height));
			this.descriptionField.setText(rating.getDescription());
			this.descriptionField.setEditable(descriptionAllowed);
		}
	}

	private static class ResourceEntriesTableModel extends AbstractTableModel {
		private final String[] columns = {"Type", "Function", "Name"};
		private final List<SarTaskResource> rows = new ArrayList<>();

		/**
		 * Checks whether a candidate resource matches any existing resource entry in this model.
		 *
		 * @param resource resource to check.
		 * @return {@code true} if a duplicate resource already exists.
		 */
		private boolean isDuplicateResource(SarTaskResource resource) {
			if (resource == null) {
				return false;
			}
			String rid = resource.getResourceId();
			boolean hasRid = rid != null && !rid.isBlank();
			String name = resource.getName();
			boolean hasName = name != null && !name.isBlank();
			if (!hasRid && !hasName) {
				return false;
			}
			for (SarTaskResource existing : rows) {
				if (existing == null) {
					continue;
				}
				String exRid = existing.getResourceId();
				if (hasRid && exRid != null && !exRid.isBlank() && rid.trim().equalsIgnoreCase(exRid.trim())) {
					return true;
				}
				String exName = existing.getName();
				if (hasName && exName != null && !exName.isBlank()
						&& name.trim().equalsIgnoreCase(exName.trim())) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Populates the table model from a list of resources up to the maximum row count,
		 * ignoring duplicates.
		 *
		 * @param resources initial resources to populate.
		 * @param minimumRows minimum number of rows to maintain.
		 */
		private void setRows(List<SarTaskResource> resources, int minimumRows) {
			rows.clear();
			if (resources != null) {
				for (SarTaskResource resource : resources) {
					if (rows.size() >= MAX_RESOURCE_ROWS) {
						break;
					}
					if (isDuplicateResource(resource)) {
						continue;
					}
					SarTaskResource copy = new SarTaskResource();
					copy.setFunction(resource.getFunction());
					copy.setName(resource.getName());
					copy.setCardType(resource.getCardType());
					copy.setResourceId(resource.getResourceId());
					rows.add(copy);
				}
			}
			while (rows.size() < Math.max(1, minimumRows) && rows.size() < MAX_RESOURCE_ROWS) {
				rows.add(new SarTaskResource());
			}
			fireTableDataChanged();
		}

		/**
		 * Returns the list of rows in this model.
		 *
		 * @return live list of resource entries.
		 */
		private List<SarTaskResource> getRows() {
			return rows;
		}

		/**
		 * Appends a resource to the table model or replaces an empty placeholder row,
		 * unless it is a duplicate.
		 *
		 * @param resource resource to add.
		 */
		private void addRow(SarTaskResource resource) {
			if (rows.size() >= MAX_RESOURCE_ROWS) {
				return;
			}
			if (isDuplicateResource(resource)) {
				return;
			}
			for (int i = 0; i < rows.size(); i++) {
				SarTaskResource r = rows.get(i);
				if (r != null && (r.getName() == null || r.getName().isBlank())
						&& (r.getFunction() == null || r.getFunction().isBlank())
						&& (r.getResourceId() == null || r.getResourceId().isBlank())) {
					rows.set(i, resource);
					fireTableRowsUpdated(i, i);
					return;
				}
			}
			rows.add(resource);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}

		/**
		 * Removes the row at the given index.
		 *
		 * @param rowIndex index of the row to remove.
		 */
		private void removeRow(int rowIndex) {
			if (rowIndex < 0 || rowIndex >= rows.size()) {
				return;
			}
			rows.remove(rowIndex);
			fireTableRowsDeleted(rowIndex, rowIndex);
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return columns.length;
		}

		@Override
		public String getColumnName(int column) {
			return columns[column];
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return columnIndex != 0; // Type column is not editable
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			SarTaskResource row = rows.get(rowIndex);
			return switch (columnIndex) {
				case 0 -> {
					TCardType ct = row.getCardType();
					yield ct == null ? TCardType.PERSONNEL.getLabel() : ct.getLabel();
				}
				case 1 -> row.getFunction();
				case 2 -> row.getName();
				default -> "";
			};
		}

		@Override
		public void setValueAt(Object value, int rowIndex, int columnIndex) {
			SarTaskResource row = rows.get(rowIndex);
			String text = value == null ? "" : value.toString().trim();
			if (columnIndex == 1) {
				row.setFunction(text);
			} else if (columnIndex == 2) {
				row.setName(text);
			}
			fireTableCellUpdated(rowIndex, columnIndex);
		}
	}

	private enum EditorMode {
		ASSIGNMENT("Edit SAR Task", "Edit SAR Task "), DEBRIEFING("Debrief SAR Task", "Debrief SAR Task ");

		private final String untitledDialog;
		private final String titledDialogPrefix;

		EditorMode(String untitledDialog, String titledDialogPrefix) {
			this.untitledDialog = untitledDialog;
			this.titledDialogPrefix = titledDialogPrefix;
		}

		private String dialogTitle(String assignmentTeamNumber) {
			return assignmentTeamNumber == null || assignmentTeamNumber.isBlank()
					? untitledDialog
					: titledDialogPrefix + assignmentTeamNumber;
		}
	}

	private static class SarTaskEditor {
		private static final String[] LIFECYCLE_OPTIONS = {"planned", "assigned - enroute to assignment",
				"assigned - on task", "assigned - returning from assignment", "returned"};

		private final JPanel panel = UiSupport.formPanel();
		private final EditorMode mode;
		private final JTextField assignmentTeamNumberField;
		private final JComboBox<String> resourceTypeField;
		private final JComboBox<String> taskTypeField;
		private final JComboBox<String> taskLifecycleField;
		private final JTextField incidentNameField;
		private final JTextField resourceIdentifierField;
		private final JTextField leaderRoleField;
		private final JTextField leaderField;
		private final JTextField contactField;
		private final JPanel assignmentSummaryField;
		private final JScrollPane operationsField;
		private final JScrollPane contextField;
		private final JPanel resourcesAssignedField;
		private final ResourceEntriesTableModel resourceEntryTableModel = new ResourceEntriesTableModel();
		private final JScrollPane assignmentField;
		private final JScrollPane transportationField;
		private final JTextField taskMapField;
		private final JScrollPane specialEquipmentField;
		private final JTextField debriefingSupervisorField;
		private final JButton pickDebriefingSupervisorButton = new JButton("Pick…");
		private final JSpinner assignmentStartField;
		private final JSpinner assignmentEndField;
		private final JTextField vehicleMilesField;
		private final JTextField reportedPodField;
		private final JScrollPane debriefNotesField;
		private final JPanel clueEntriesField;
		private final ClueEntriesTableModel clueEntryTableModel = new ClueEntriesTableModel();
		private final JPanel podFactorsField = new JPanel(new GridBagLayout());
		private final List<PodFactorEntryFields> podFactorEntryFields = new ArrayList<>();
		private final JComboBox<String> canineSearchTypeField;
		private final JComboBox<String> canineImprintField;
		private final JTextField canineSunAngleField;
		private final JTextField canineDayNightField;
		private final JTextField canineCloudCoverField;
		private final JTextField canineWindSpeedField;
		private final JScrollPane areasNotCoveredField;
		private final JScrollPane hazardsObservedField;
		private final List<TCard> availableTCards;
		private final AppController controller;
		private String debriefingSupervisorResourceId = "";
		private String initialLifecycleStatus;
		private final Set<String> initialClueIds = new java.util.HashSet<>();
		private final java.util.Map<String, String> initialClueFollowUp = new java.util.HashMap<>();
		/**
		 * Resource IDs of cards currently committed to an active ("On Task") SAR task.
		 */
		private final Set<String> onTaskResourceIds;
		/** Pre-built name→TCard lookup used by {@link #resourceCount()}. */
		private final java.util.Map<String, TCard> tCardByDisplayName;

		/**
		 * Creates editor fields and captures merge baselines for detached logging.
		 *
		 * @param row live assignment.
		 * @param mode editor mode.
		 * @param clueLogEntries shared clue log.
		 * @param resourceRowCount initial resource rows.
		 * @param canineForHandler canine name lookup.
		 * @param availableResourceNames resource choices.
		 * @param availableTCards available cards.
		 * @param onTaskResourceIds committed resource identities.
		 */
		private SarTaskEditor(SarTaskAssignment row, EditorMode mode, List<ClueLogEntry> clueLogEntries,
				int resourceRowCount, java.util.function.Function<String, java.util.List<String>> canineForHandler,
				List<String> availableResourceNames, List<TCard> availableTCards, Set<String> onTaskResourceIds) {
			this(row, mode, clueLogEntries, resourceRowCount, canineForHandler, availableResourceNames, availableTCards,
					onTaskResourceIds, null);
		}

		/**
		 * Creates editor fields with controller-backed resource selection.
		 *
		 * @param row live assignment.
		 * @param mode editor mode.
		 * @param clueLogEntries shared clue log.
		 * @param resourceRowCount initial resource rows.
		 * @param canineForHandler canine name lookup.
		 * @param availableResourceNames resource choices.
		 * @param availableTCards available cards.
		 * @param onTaskResourceIds committed resource identities.
		 * @param controller application controller, or null for standalone editing.
		 */
		private SarTaskEditor(SarTaskAssignment row, EditorMode mode, List<ClueLogEntry> clueLogEntries,
				int resourceRowCount, java.util.function.Function<String, java.util.List<String>> canineForHandler,
				List<String> availableResourceNames, List<TCard> availableTCards, Set<String> onTaskResourceIds,
				AppController controller) {
			this.mode = mode;
			this.controller = controller;
			initialLifecycleStatus = row.getTaskLifecycleStatus();
			for (ClueLogEntry clue : cluesForTask(row, clueLogEntries == null ? List.of() : clueLogEntries)) {
				initialClueIds.add(clue.getEntryId());
				initialClueFollowUp.put(clue.getEntryId(), clue.getFollowUp());
			}
			this.availableTCards = availableTCards == null ? List.of() : availableTCards;
			this.onTaskResourceIds = onTaskResourceIds == null ? Set.of() : onTaskResourceIds;
			java.util.Map<String, TCard> nameMap = new java.util.HashMap<>();
			for (TCard tc : this.availableTCards) {
				String key = tc.getDisplayLabel().trim().toLowerCase(java.util.Locale.ROOT);
				if (!key.isBlank()) {
					nameMap.putIfAbsent(key, tc);
				}
			}
			this.tCardByDisplayName = java.util.Collections.unmodifiableMap(nameMap);
			boolean isCanineTask = SarTaskSupport.usesCanineFactors(row.getResourceType());
			assignmentTeamNumberField = textField(row.getAssignmentTeamNumber(), true, 10);
			resourceTypeField = new JComboBox<>(SarTaskSupport.resourceTypes().toArray(String[]::new));
			resourceTypeField.setEditable(true);
			resourceTypeField.setSelectedItem(row.getResourceType());
			UiSupport.configureDialogComboBox(resourceTypeField, 170);
			taskTypeField = new JComboBox<>(SarTaskSupport.taskTypes().toArray(String[]::new));
			taskTypeField.setEditable(true);
			taskTypeField.setSelectedItem(row.getTaskType());
			UiSupport.configureDialogComboBox(taskTypeField, 170);
			taskLifecycleField = new JComboBox<>(LIFECYCLE_OPTIONS);
			taskLifecycleField.setSelectedItem(row.getTaskLifecycleStatus());
			UiSupport.configureDialogComboBox(taskLifecycleField, 220);
			incidentNameField = textField(row.getIncidentName(), false);
			resourceIdentifierField = textField(row.getResourceIdentifier(), false);
			leaderRoleField = textField(row.getLeaderRole(), false);
			leaderField = textField(row.getLeader(), false);
			contactField = textField(row.getContact(), false);
			if (controller != null && contactField.isEditable()) {
				RadioChannelPicker.install(contactField, controller);
			}

			// When the leader field loses focus and the resource type is Canine, check
			// whether that handler has a linked canine T-card and offer to pre-fill the
			// resource identifier if the field is still blank.
			if (canineForHandler != null) {
				leaderField.addFocusListener(new java.awt.event.FocusAdapter() {
					@Override
					public void focusLost(java.awt.event.FocusEvent e) {
						if (!SarTaskSupport.usesCanineFactors(selectedComboValue(resourceTypeField))) {
							return;
						}
						if (!resourceIdentifierField.getText().trim().isEmpty()) {
							return;
						}
						String handler = leaderField.getText().trim();
						if (handler.isEmpty()) {
							return;
						}
						java.util.List<String> linked = canineForHandler.apply(handler);
						if (linked.isEmpty()) {
							return;
						}
						// Single linked canine: auto-fill silently.
						if (linked.size() == 1) {
							resourceIdentifierField.setText(linked.get(0));
							return;
						}
						// Multiple linked canines: show a chooser.
						JComboBox<String> canineChoices = new JComboBox<>(linked.toArray(String[]::new));
						if (UiSupport.showResizableConfirmDialog(leaderField,
								"Select canine for " + handler, canineChoices, new Dimension(420, 140))) {
							resourceIdentifierField.setText((String) canineChoices.getSelectedItem());
						}
					}
				});
			}

			String leaderLabel = isCanineTask ? "Leader/Handler: " : "Leader: ";
			assignmentSummaryField = inlineSummaryPanel(2, "Resource: " + safeValue(row.getResourceIdentifier()),
					leaderLabel + safeValue(row.getLeader()), "Leader Role: " + safeValue(row.getLeaderRole()),
					"Leader Contact: " + safeValue(row.getContact()));
			operationsField = textArea(SarTaskTableModel.joinOperations(row), 2, false);
			contextField = textArea(SarTaskTableModel.joinContext(row), 2, false);
			List<String> leaderCanines = isCanineTask && canineForHandler != null
					? canineForHandler.apply(row.getLeader())
					: List.of();
			resourcesAssignedField = resourceEditorPanel(row, resourceEntryTableModel, resourceRowCount,
					availableResourceNames, leaderCanines == null ? List.of() : leaderCanines, availableTCards,
					onTaskResourceIds, panel, this.controller);
			assignmentField = textArea(row.getAssignment(), 3, true);
			transportationField = textArea(row.getTransportationInstructions(), 2, true);
			taskMapField = textField(row.getTaskMap(), true, 14);
			specialEquipmentField = textArea(row.getSpecialEquipment(), 2, true);
			debriefingSupervisorResourceId = initialDebriefingSupervisorResourceId(row);
			debriefingSupervisorField = textField(initialDebriefingSupervisorName(row), true, 12);
			configureDebriefingSupervisorField();
			assignmentStartField = dateTimeSpinner(row.getAssignmentStart(), 132);
			assignmentEndField = dateTimeSpinner(row.getAssignmentEnd(), 132);
			vehicleMilesField = textField(row.getVehicleMiles(), true, 6);
			reportedPodField = textField(row.getReportedPod(), true, 2);
			debriefNotesField = textArea(row.getDebriefNotes(), 4, true);
			clueEntriesField = clueEditorPanel(clueEntryTableModel, cluesForTask(row, clueLogEntries));
			canineSearchTypeField = comboBox(CANINE_SEARCH_TYPE_OPTIONS, row.getCanineSearchType());
			canineImprintField = comboBox(CANINE_IMPRINT_OPTIONS, row.getCanineImprint());
			UiSupport.configureDialogComboBox(canineSearchTypeField, 170);
			UiSupport.configureDialogComboBox(canineImprintField, 170);
			canineSunAngleField = textField(row.getCanineSunAngle(), true, 8);
			canineDayNightField = textField(row.getCanineDayNight(), true, 8);
			canineCloudCoverField = textField(row.getCanineCloudCover(), true, 8);
			canineWindSpeedField = textField(row.getCanineWindSpeed(), true, 8);
			areasNotCoveredField = textArea(row.getAreasNotCovered(), 3, true);
			hazardsObservedField = textArea(row.getHazardsObserved(), 3, true);

			podFactorsField.setOpaque(false);
			rebuildPodFactorFields(row.getResourceType(), row.getQualitativePodFactors());
			resourceTypeField.addActionListener(
					event -> rebuildPodFactorFields(selectedComboValue(resourceTypeField), existingFactorValues()));

			int rowIndex = 0;
			UiSupport.addRequiredRow(panel, rowIndex++, "Assignment/Team #", assignmentTeamNumberField);
			UiSupport.addRow(panel, rowIndex++, "Inherited task data", assignmentSummaryField);
			UiSupport.addRow(panel, rowIndex++, "Task setup",
					inlineFieldPanel(new LabeledComponent("Resource type", resourceTypeField),
							new LabeledComponent("Task Geometry", taskTypeField)));
			if (mode == EditorMode.ASSIGNMENT) {
				UiSupport.addRow(panel, rowIndex++, "Task status", taskLifecycleField);
				UiSupport.addRow(panel, rowIndex++, "Operations personnel", operationsField);
				UiSupport.addRow(panel, rowIndex++, "Context", contextField);
				UiSupport.addRow(panel, rowIndex++, "Resources assigned", resourcesAssignedField);
				UiSupport.addRow(panel, rowIndex++, "Work assignment", assignmentField);
				UiSupport.addRow(panel, rowIndex++, "Field details",
						inlineFieldPanel(new LabeledComponent("Transportation", transportationField),
								new LabeledComponent("Task map", taskMapField)));
				UiSupport.addRow(panel, rowIndex++, "Special equipment", specialEquipmentField);
				return;
			}
			UiSupport.addRow(panel, rowIndex++, "Debrief details",
					inlineFieldPanel(new LabeledComponent("Debrief supervisor", debriefingSupervisorPickerField()),
							new LabeledComponent("Reported POD (%)", reportedPodField),
							new LabeledComponent("Vehicle miles", vehicleMilesField)));
			UiSupport.addRow(panel, rowIndex++, "Time on assignment",
					inlineFieldPanel(new LabeledComponent("Assignment start", assignmentStartField),
							new LabeledComponent("Assignment end", assignmentEndField)));
			UiSupport.addRow(panel, rowIndex++, "Debriefing", debriefNotesField);
			UiSupport.addRow(panel, rowIndex++, "Clues detected", clueEntriesField);
			UiSupport.addWideRow(panel, rowIndex++, podFactorsField);
			UiSupport.addRow(panel, rowIndex++, "Areas not covered", areasNotCoveredField);
			UiSupport.addRow(panel, rowIndex, "Hazards observed", hazardsObservedField);
		}

		private void rebuildPodFactorFields(String resourceType, List<PodFactorRating> existing) {
			podFactorsField.removeAll();
			podFactorEntryFields.clear();
			boolean canine = SarTaskSupport.usesCanineFactors(resourceType);
			boolean canineResourceDetailsAdded = false;
			boolean canineWeatherDetailsAdded = false;
			addPodFactorCell(new JLabel("<html>Qualitative POD Factors<br>Factor</html>"), 0, 0, 0.0,
					GridBagConstraints.NONE);
			addPodFactorCell(new JLabel("Score"), 1, 0, 0.0, GridBagConstraints.NONE);
			addPodFactorCell(new JLabel("Description"), 2, 0, 1.0, GridBagConstraints.HORIZONTAL);
			int rowIndex = 1;
			for (PodFactorRating rating : SarTaskSupport.factorRatings(resourceType, existing)) {
				boolean descriptionAllowed = SarTaskSupport.allowsDescription(resourceType, rating.getName());
				PodFactorEntryFields fields = new PodFactorEntryFields(rating, descriptionAllowed);
				podFactorEntryFields.add(fields);
				addPodFactorCell(new JLabel(rating.getName() + " (1-" + rating.getMaxScore() + ")"), 0, rowIndex, 0.0,
						GridBagConstraints.NONE);
				addPodFactorCell(fields.scoreField, 1, rowIndex, 0.0, GridBagConstraints.NONE);
				addPodFactorCell(fields.descriptionField, 2, rowIndex, 1.0, GridBagConstraints.HORIZONTAL);
				rowIndex++;
				if (canine && SarTaskSupport.CANINE_HANDLER_CERTIFICATION_FACTOR_NAME.equals(rating.getName())) {
					rowIndex = addCanineResourceDetailRow(rowIndex);
					canineResourceDetailsAdded = true;
				}
				if (canine && CANINE_WEATHER_FACTOR_NAME.equals(rating.getName())) {
					rowIndex = addCanineWeatherRows(rowIndex);
					canineWeatherDetailsAdded = true;
				}
			}
			if (canine && !canineResourceDetailsAdded) {
				rowIndex = addCanineResourceDetailRow(rowIndex);
			}
			if (canine && !canineWeatherDetailsAdded) {
				addCanineWeatherRows(rowIndex);
			}
			podFactorsField.revalidate();
			podFactorsField.repaint();
		}

		private void addPodFactorCell(Component component, int column, int row, double weightx, int fill) {
			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridx = column;
			constraints.gridy = row;
			constraints.weightx = weightx;
			constraints.anchor = GridBagConstraints.NORTHWEST;
			constraints.fill = fill;
			constraints.insets = new Insets(0, 0, 3, column == 2 ? 0 : 6);
			podFactorsField.add(component, constraints);
		}

		private void addPodFactorDetailCell(Component component, int row) {
			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridx = 1;
			constraints.gridy = row;
			constraints.gridwidth = 2;
			constraints.weightx = 1.0;
			constraints.anchor = GridBagConstraints.NORTHWEST;
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.insets = new Insets(0, 0, 3, 0);
			podFactorsField.add(component, constraints);
		}

		private int addCanineResourceDetailRow(int rowIndex) {
			addPodFactorDetailCell(
					compactFieldRowPanel(new LabeledComponent("Canine resource type", canineSearchTypeField),
							new LabeledComponent("Dog imprinted on", canineImprintField)),
					rowIndex++);
			return rowIndex;
		}

		private int addCanineWeatherRows(int rowIndex) {
			addPodFactorDetailCell(compactFieldRowPanel(new LabeledComponent("Sun angle", canineSunAngleField),
					new LabeledComponent("Day/night", canineDayNightField)), rowIndex++);
			addPodFactorDetailCell(compactFieldRowPanel(new LabeledComponent("Cloud cover", canineCloudCoverField),
					new LabeledComponent("Wind speed", canineWindSpeedField)), rowIndex++);
			return rowIndex;
		}

		private List<PodFactorRating> existingFactorValues() {
			List<PodFactorRating> ratings = new ArrayList<>();
			for (PodFactorEntryFields entry : podFactorEntryFields) {
				PodFactorRating rating = new PodFactorRating();
				rating.setName(entry.factorName);
				rating.setMaxScore(entry.maxScore);
				rating.setScore(parseInteger(entry.scoreField.getText()));
				rating.setDescription(entry.descriptionField.getText().trim());
				ratings.add(rating);
			}
			return ratings;
		}

		/**
		 * Applies edited fields while preserving concurrently logged clues and status.
		 *
		 * @param row live assignment.
		 * @param clueLogEntries current shared clue log.
		 * @return merged clue log retaining entries added outside this editor.
		 */
		private List<ClueLogEntry> applyTo(SarTaskAssignment row, List<ClueLogEntry> clueLogEntries) {
			if (mode == EditorMode.ASSIGNMENT) {
				row.setAssignmentTeamNumber(assignmentTeamNumberField.getText().trim());
				row.setResourceType(selectedComboValue(resourceTypeField));
				row.setTaskType(selectedComboValue(taskTypeField));
				if (java.util.Objects.equals(initialLifecycleStatus, row.getTaskLifecycleStatus())) {
					row.setTaskLifecycleStatus((String) taskLifecycleField.getSelectedItem());
					initialLifecycleStatus = row.getTaskLifecycleStatus();
				}
				row.setResourcesAssigned(resourceValuesFrom(resourceEntryTableModel));
				row.setAssignment(textAreaFrom(assignmentField).getText().trim());
				row.setTransportationInstructions(textAreaFrom(transportationField).getText().trim());
				row.setTaskMap(taskMapField.getText().trim());
				row.setSpecialEquipment(textAreaFrom(specialEquipmentField).getText().trim());
				return clueLogEntries == null ? new ArrayList<>() : new ArrayList<>(clueLogEntries);
			}
			String supervisorName = debriefingSupervisorField.getText().trim();
			row.setDebriefingSupervisor(supervisorName);
			row.setDebriefingSupervisorResourceId(resolveDebriefingSupervisorResourceId(supervisorName));
			row.setAssignmentStart(spinnerDateTimeValue(assignmentStartField));
			row.setAssignmentEnd(spinnerDateTimeValue(assignmentEndField));
			row.setVehicleMiles(vehicleMilesField.getText().trim());
			row.setReportedPod(reportedPodField.getText().trim());
			row.setDebriefNotes(textAreaFrom(debriefNotesField).getText().trim());
			row.setQualitativePodFactors(existingFactorValues());
			row.setCanineSearchType(selectedComboValue(canineSearchTypeField));
			row.setCanineImprint(selectedComboValue(canineImprintField));
			row.setCanineSunAngle(canineSunAngleField.getText().trim());
			row.setCanineDayNight(canineDayNightField.getText().trim());
			row.setCanineCloudCover(canineCloudCoverField.getText().trim());
			row.setCanineWindSpeed(canineWindSpeedField.getText().trim());
			row.setAreasNotCovered(textAreaFrom(areasNotCoveredField).getText().trim());
			row.setHazardsObserved(textAreaFrom(hazardsObservedField).getText().trim());

			List<ClueLogEntry> updatedClues = clueLogEntries == null
					? new ArrayList<>()
					: new ArrayList<>(clueLogEntries);
			List<ClueLogEntry> editedClues = clueValuesFrom(clueEntryTableModel.getRows(), row,
					row.getAssignmentTeamNumber());
			for (ClueLogEntry edited : editedClues) {
				boolean externalFollowUp = false;
				for (ClueLogEntry current : updatedClues) {
					if (edited.getEntryId().equals(current.getEntryId())) {
						copyFollowUp(current, edited);
						if (!java.util.Objects.equals(initialClueFollowUp.get(current.getEntryId()),
								current.getFollowUp())) {
							edited.setFollowUp(current.getFollowUp());
							externalFollowUp = true;
						}
						break;
					}
				}
				if (!externalFollowUp) {
					initialClueFollowUp.put(edited.getEntryId(), edited.getFollowUp());
				}
				initialClueIds.add(edited.getEntryId());
			}
			updatedClues.removeIf(clue -> initialClueIds.contains(clue.getEntryId()));
			Set<String> currentIds = updatedClues.stream().map(ClueLogEntry::getEntryId)
					.collect(java.util.stream.Collectors.toSet());
			for (ClueLogEntry edited : editedClues) {
				if (currentIds.add(edited.getEntryId())) {
					updatedClues.add(edited);
				}
			}
			return updatedClues;
		}

		/**
		 * Installs picker-backed personnel selection behavior for the debriefing
		 * supervisor field.
		 */
		private void configureDebriefingSupervisorField() {
			UiSupport.installNameAutocomplete(debriefingSupervisorField, this::availablePersonnelSupervisorNames,
					this::syncDebriefingSupervisorSelection, proposedName -> {
						if (controller == null) {
							return "";
						}
						TCard created = controller.createPersonnelCardViaDialog(proposedName, "", "");
						if (created != null) {
							debriefingSupervisorResourceId = created.getResourceId();
							return created.getPersonName();
						}
						return "";
					});
			pickDebriefingSupervisorButton.setToolTipText("Select from known personnel (T-cards)");
			pickDebriefingSupervisorButton.addActionListener(
					event -> SwingUtilities.invokeLater(() -> UiSupport.openInstalledNamePicker(debriefingSupervisorField)));
			pickDebriefingSupervisorButton
					.setEnabled(!availablePersonnelSupervisorNames().isEmpty() || controller != null);
		}

		/**
		 * Builds the debriefing supervisor editor control with a dedicated picker
		 * button.
		 *
		 * @return composite field component.
		 */
		private JPanel debriefingSupervisorPickerField() {
			JPanel panel = new JPanel(new BorderLayout(4, 0));
			panel.setOpaque(false);
			panel.add(debriefingSupervisorField, BorderLayout.CENTER);
			panel.add(pickDebriefingSupervisorButton, BorderLayout.EAST);
			return panel;
		}

		/**
		 * Returns available personnel-card names that can be chosen as debriefing
		 * supervisors.
		 *
		 * @return sorted supervisor name list.
		 */
		private List<String> availablePersonnelSupervisorNames() {
			List<String> names = new ArrayList<>();
			for (TCard card : availableTCards) {
				if (card == null || card.getCardType() != TCardType.PERSONNEL) {
					continue;
				}
				String displayName = card.getDisplayLabel().trim();
				if (!displayName.isBlank() && !names.contains(displayName)) {
					names.add(displayName);
				}
			}
			names.sort(String.CASE_INSENSITIVE_ORDER);
			return names;
		}

		/**
		 * Resolves the supervisor name to display when the editor opens.
		 *
		 * @param row
		 *            task being edited.
		 * @return canonical personnel display name, or the stored fallback text.
		 */
		private String initialDebriefingSupervisorName(SarTaskAssignment row) {
			TCard linkedCard = findPersonnelCardByResourceId(row.getDebriefingSupervisorResourceId());
			if (linkedCard != null) {
				return linkedCard.getDisplayLabel();
			}
			return row.getDebriefingSupervisor();
		}

		/**
		 * Resolves the initial linked personnel identifier for the supervisor field.
		 *
		 * @param row
		 *            task being edited.
		 * @return linked personnel resource identifier, or blank when unresolved.
		 */
		private String initialDebriefingSupervisorResourceId(SarTaskAssignment row) {
			String linkedResourceId = row.getDebriefingSupervisorResourceId();
			if (!linkedResourceId.isBlank()) {
				return linkedResourceId;
			}
			TCard matchedCard = findPersonnelCardByDisplayName(row.getDebriefingSupervisor());
			return matchedCard == null ? "" : matchedCard.getResourceId();
		}

		/**
		 * Updates the stored linked personnel identifier after picker selection.
		 *
		 * @param selectedName
		 *            selected supervisor display name.
		 */
		private void syncDebriefingSupervisorSelection(String selectedName) {
			TCard selectedCard = findPersonnelCardByDisplayName(selectedName);
			debriefingSupervisorResourceId = selectedCard == null ? "" : selectedCard.getResourceId();
		}

		/**
		 * Resolves the linked personnel identifier that should be saved with the
		 * supervisor field.
		 *
		 * @param supervisorName
		 *            supervisor display name entered or picked by the operator.
		 * @return linked personnel resource identifier, or blank for free-form text.
		 */
		private String resolveDebriefingSupervisorResourceId(String supervisorName) {
			String normalizedName = supervisorName == null ? "" : supervisorName.trim();
			if (normalizedName.isBlank()) {
				debriefingSupervisorResourceId = "";
				return "";
			}
			TCard linkedCard = findPersonnelCardByResourceId(debriefingSupervisorResourceId);
			if (linkedCard != null && normalizedName.equalsIgnoreCase(linkedCard.getDisplayLabel().trim())) {
				return linkedCard.getResourceId();
			}
			if (linkedCard == null && !debriefingSupervisorResourceId.isBlank()) {
				return debriefingSupervisorResourceId;
			}
			TCard matchedCard = findPersonnelCardByDisplayName(normalizedName);
			debriefingSupervisorResourceId = matchedCard == null ? "" : matchedCard.getResourceId();
			return debriefingSupervisorResourceId;
		}

		/**
		 * Finds a personnel T-card by stable resource identifier.
		 *
		 * @param resourceId
		 *            personnel resource identifier.
		 * @return matching personnel T-card, or {@code null}.
		 */
		private TCard findPersonnelCardByResourceId(String resourceId) {
			String normalizedId = resourceId == null ? "" : resourceId.trim();
			if (normalizedId.isBlank()) {
				return null;
			}
			for (TCard card : availableTCards) {
				if (card != null && card.getCardType() == TCardType.PERSONNEL
						&& normalizedId.equals(card.getResourceId())) {
					return card;
				}
			}
			return null;
		}

		/**
		 * Finds a personnel T-card by display name.
		 *
		 * @param displayName
		 *            personnel display name.
		 * @return matching personnel T-card, or {@code null}.
		 */
		private TCard findPersonnelCardByDisplayName(String displayName) {
			String normalizedName = displayName == null ? "" : displayName.trim();
			if (normalizedName.isBlank()) {
				return null;
			}
			for (TCard card : availableTCards) {
				if (card != null && card.getCardType() == TCardType.PERSONNEL
						&& normalizedName.equalsIgnoreCase(card.getDisplayLabel().trim())) {
					return card;
				}
			}
			return null;
		}

		/**
		 * Returns assignment-editor validation errors that should block save/open
		 * actions.
		 *
		 * @return ordered validation errors.
		 */
		private List<String> assignmentValidationErrors() {
			List<String> errors = new ArrayList<>();
			if (mode != EditorMode.ASSIGNMENT) {
				return errors;
			}
			if (assignmentTeamNumberField.getText().trim().isBlank()) {
				errors.add("Assignment/Team # is required.");
			}
			Set<String> seenResourceIds = new HashSet<>();
			Set<String> seenResourceNames = new HashSet<>();
			for (SarTaskResource res : resourceValuesFrom(resourceEntryTableModel)) {
				String rid = res.getResourceId();
				if (rid != null && !rid.isBlank()) {
					if (!seenResourceIds.add(rid.trim())) {
						String label = res.getName() != null && !res.getName().isBlank() ? res.getName().trim() : rid.trim();
						errors.add("Duplicate resource assigned to this task: " + label);
						continue;
					}
				}
				String name = res.getName();
				if (name != null && !name.isBlank()) {
					String norm = name.trim().toLowerCase(java.util.Locale.ROOT);
					if (!seenResourceNames.add(norm)) {
						errors.add("Duplicate resource assigned to this task: " + name.trim());
					}
				}
			}
			return errors;
		}

		/**
		 * Calculates the total number of persons represented by resources assigned to this task.
		 *
		 * @return total person count.
		 */
		private int resourceCount() {
			// Sum personCount() contributions from matched T-cards.
			// PERSONNEL: 1 each; CREW: numberOfPersons (default 1); others: 0.
			int total = 0;
			for (SarTaskResource res : resourceValuesFrom(resourceEntryTableModel)) {
				String key = res.getName().trim().toLowerCase(java.util.Locale.ROOT);
				TCard matched = tCardByDisplayName.get(key);
				if (matched != null) {
					total += matched.personCount();
				} else if (res.getCardType() == TCardType.PERSONNEL) {
					// Resource not yet backed by a T-card; count as 1 person.
					total += 1;
				}
				// Non-PERSONNEL resources with no matched card contribute 0.
			}
			return total;
		}
	}

	private static String selectedComboValue(JComboBox<String> comboBox) {
		Object selected = comboBox.getEditor().getItem();
		return selected == null ? "" : selected.toString().trim();
	}

	private static Integer parseInteger(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	/**
	 * Table model for editable SAR task rows.
	 *
	 * <p>
	 * The table shows only the essential summary columns. All other fields (work
	 * assignment, transportation, special equipment, clues, POD factors, debriefing
	 * notes, etc.) are accessible exclusively through the edit dialogs.
	 * </p>
	 */
	private static class SarTaskTableModel extends AbstractTableModel {
		private final String[] columns = {"Task Status", "Assignment/Team # (required)", "Resource Type",
				"Task Geometry", "Resource", "Leader", "People", "Time On Start", "Time On End"};
		private List<SarTaskAssignment> rows = new ArrayList<>();
		private List<ClueLogEntry> clueLogEntries = new ArrayList<>();
		private List<ResourceAssignment> resourceAssignments = new ArrayList<>();

		void setRows(List<SarTaskAssignment> rows, List<ClueLogEntry> clueLogEntries,
				List<ResourceAssignment> resourceAssignments) {
			this.rows = rows == null ? new ArrayList<>() : rows;
			this.clueLogEntries = clueLogEntries == null ? new ArrayList<>() : clueLogEntries;
			this.resourceAssignments = resourceAssignments == null ? new ArrayList<>() : resourceAssignments;
			fireTableDataChanged();
		}

		List<SarTaskAssignment> getRows() {
			return rows;
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}
		@Override
		public int getColumnCount() {
			return columns.length;
		}
		@Override
		public String getColumnName(int column) {
			return columns[column];
		}
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return !READ_ONLY_COLUMNS.contains(columnIndex);
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			SarTaskAssignment row = rows.get(rowIndex);
			return switch (columnIndex) {
				case 0 -> row.getTaskLifecycleStatus();
				case 1 -> row.getAssignmentTeamNumber();
				case 2 -> row.getResourceType();
				case 3 -> row.getTaskType();
				case 4 -> row.getResourceIdentifier();
				case 5 -> row.getLeader();
				case 6 -> linkedPeople(row, resourceAssignments);
				case 7 -> formatDateTimeValue(row.getAssignmentStart());
				case 8 -> formatDateTimeValue(row.getAssignmentEnd());
				default -> "";
			};
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			SarTaskAssignment row = rows.get(rowIndex);
			String value = aValue == null ? "" : aValue.toString();
			switch (columnIndex) {
				case 1 -> row.setAssignmentTeamNumber(value);
				case 2 -> row.setResourceType(value);
				case 3 -> row.setTaskType(value);
				case 7 -> row.setAssignmentStart(parseDateTimeValue(value));
				case 8 -> row.setAssignmentEnd(parseDateTimeValue(value));
				default -> {
					return;
				}
			}
			fireTableCellUpdated(rowIndex, columnIndex);
		}

		private static int linkedPeople(SarTaskAssignment row, List<ResourceAssignment> resourceAssignments) {
			ResourceAssignment assignment = findLinkedAssignment(row, resourceAssignments);
			if (assignment != null) {
				return assignment.getNumberOfPersons();
			}
			return row.getResourcesAssigned().size();
		}

		private static ResourceAssignment findLinkedAssignment(SarTaskAssignment row,
				List<ResourceAssignment> resourceAssignments) {
			for (ResourceAssignment assignment : resourceAssignments) {
				if (assignment == null) {
					continue;
				}
				if (!row.getAssignmentId().isBlank() && row.getAssignmentId().equals(assignment.getAssignmentId())) {
					return assignment;
				}
				if (row.getAssignmentId().isBlank() && !row.getAssignmentTeamNumber().isBlank()
						&& row.getAssignmentTeamNumber().equals(assignment.getAssignmentTeamNumber())) {
					return assignment;
				}
			}
			return null;
		}

		private static String joinOperations(SarTaskAssignment row) {
			List<String> values = new ArrayList<>();
			if (!row.getOperationsSectionChiefName().isBlank()) {
				values.add("Ops: " + row.getOperationsSectionChiefName()
						+ (row.getOperationsSectionChiefContact().isBlank()
								? ""
								: " (" + row.getOperationsSectionChiefContact() + ")"));
			}
			if (!row.getSecondaryManagementRoleLabel().isBlank() && !row.getSecondaryManagementName().isBlank()) {
				values.add(row.getSecondaryManagementRoleLabel() + ": " + row.getSecondaryManagementName()
						+ (row.getSecondaryManagementContact().isBlank()
								? ""
								: " (" + row.getSecondaryManagementContact() + ")"));
			}
			return String.join(" | ", values);
		}

		private static String joinContext(SarTaskAssignment row) {
			List<String> values = new ArrayList<>();
			addIfPresent(values, "Branch", row.getBranch());
			addIfPresent(values, "Division", row.getDivision());
			addIfPresent(values, "Group", row.getGroup());
			addIfPresent(values, "Staging", row.getStagingArea());
			return String.join(" | ", values);
		}

		private static void addIfPresent(List<String> values, String label, String value) {
			if (value != null && !value.isBlank()) {
				values.add(label + ": " + value);
			}
		}

		private static String formatCommunications(List<CommunicationEntry> communications) {
			List<String> lines = new ArrayList<>();
			for (CommunicationEntry communication : communications) {
				if (communication.getName().isBlank() && communication.getFunction().isBlank()
						&& communication.getPrimaryContact().isBlank()) {
					continue;
				}
				String left = communication.getNameOrFunction();
				lines.add(left + (communication.getPrimaryContact().isBlank()
						? ""
						: ": " + communication.getPrimaryContact()));
			}
			return String.join(" ; ", lines);
		}

		private static List<CommunicationEntry> parseCommunications(String value) {
			List<CommunicationEntry> communications = new ArrayList<>();
			for (String entry : splitEntries(value)) {
				CommunicationEntry communication = new CommunicationEntry();
				if (entry.contains(":")) {
					String[] parts = entry.split(":", 2);
					communication.setNameOrFunction(parts[0].trim());
					communication.setPrimaryContact(parts[1].trim());
				} else {
					communication.setNameOrFunction(entry.trim());
				}
				communications.add(communication);
			}
			return communications;
		}

		private static String formatClues(SarTaskAssignment row, List<ClueLogEntry> clueLogEntries) {
			List<String> descriptions = new ArrayList<>();
			if (clueLogEntries == null) {
				return "";
			}
			for (ClueLogEntry clue : clueLogEntries) {
				if (clue == null || clue.getDescription().isBlank()) {
					continue;
				}
				boolean matchesAssignment = !row.getAssignmentId().isBlank()
						&& row.getAssignmentId().equals(clue.getAssignmentId());
				boolean matchesTeamNumber = row.getAssignmentId().isBlank() && !row.getAssignmentTeamNumber().isBlank()
						&& row.getAssignmentTeamNumber().equals(clue.getDetectingTask());
				if (matchesAssignment || matchesTeamNumber) {
					descriptions.add(clue.getDescription());
				}
			}
			return String.join(" ; ", descriptions);
		}

		private static String formatPodFactors(List<PodFactorRating> factors) {
			List<String> values = new ArrayList<>();
			for (PodFactorRating factor : factors) {
				if (factor == null || factor.getScore() == null) {
					continue;
				}
				values.add(factor.getName() + " " + factor.getScore() + "/" + factor.getMaxScore());
			}
			return String.join(" ; ", values);
		}

		private static List<String> splitEntries(String value) {
			List<String> entries = new ArrayList<>();
			for (String raw : value.split("\\s*;\\s*")) {
				String trimmed = raw.trim();
				if (!trimmed.isBlank()) {
					entries.add(trimmed);
				}
			}
			return entries;
		}
	}

	/**
	 * Cell renderer for the "Task Status" column: colour-codes each cell to match
	 * the board-view column colours (Planned=blue, On Task=orange, Returned=green,
	 * Completed=grey).
	 */
	private static class LifecycleStatusCellRenderer extends DefaultTableCellRenderer {
		private static final Color COLOR_PLANNING = new Color(173, 216, 230);
		private static final Color COLOR_ON_TASK = new Color(255, 200, 100);
		private static final Color COLOR_RETURNED = new Color(144, 238, 144);
		private static final Color COLOR_COMPLETED = new Color(200, 200, 200);

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (!isSelected) {
				String status = value == null ? "" : value.toString();
				component.setBackground(switch (status) {
					case "Planning", "Planned", "planned" -> COLOR_PLANNING;
					case "assigned - enroute to assignment", "assigned - on task",
							"assigned - returning from assignment", "On Task", "on task" ->
						COLOR_ON_TASK;
					case "Returned", "returned" -> COLOR_RETURNED;
					case "Completed" -> COLOR_COMPLETED;
					default -> table.getBackground();
				});
				component.setForeground(Color.BLACK);
			}
			return component;
		}
	}

	private static class RequiredFieldCellRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (isSelected) {
				component.setBackground(table.getSelectionBackground());
			} else {
				component.setBackground(UiSupport.REQUIRED_FIELD_BACKGROUND);
			}
			return component;
		}
	}
}
