/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics215Form;
import org.sarmanagement.icsforms.model.Ics215ResourceReview;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional, scrollable editor for active- and next-period ICS 215 worksheets.
 *
 * <p>Resource counts are edited explicitly, and every selection is retained by
 * the worksheet's stable row and heading identities.</p>
 */
public class Ics215Panel extends JPanel {
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final String[] MANAGEMENT_CONTEXTS = {Ics204Form.MANAGEMENT_STAGING_AREA,
			Ics204Form.MANAGEMENT_BRANCH, Ics204Form.MANAGEMENT_DIVISION, Ics204Form.MANAGEMENT_GROUP};
	private final AppController controller;
	private final JComboBox<String> periodSelector = new JComboBox<>(new String[] {"Active period", "Next period"});
	private final JComboBox<Ics215Form.PaperSize> paperSize = new JComboBox<>(Ics215Form.PaperSize.values());
	private final JTextField targetStart = new JTextField(16);
	private final JTextField targetEnd = new JTextField(16);
	private final JLabel contextLabel = new JLabel();
	private final JLabel totalsLabel = new JLabel();
	private final JTable matrix;
	private final JScrollPane matrixScroll;
	private final JButton addAssignmentButton = new JButton("Add assignment");
	private final JButton addResourceHeadingButton = new JButton("Add first resource heading…");
	private final JButton editAssignmentButton = new JButton("Edit assignment…");
	private final JButton removeAssignmentButton = new JButton("Remove assignment");
	private final JButton moveAssignmentUpButton = new JButton("Move up");
	private final JButton moveAssignmentDownButton = new JButton("Move down");
	private final JButton editHeadingButton = new JButton("Edit heading…");
	private final JButton moveHeadingLeftButton = new JButton("Move heading left");
	private final JButton moveHeadingRightButton = new JButton("Move heading right");
	private final JButton removeHeadingButton = new JButton("Remove heading");
	private final JButton editCountsButton = new JButton("Edit counts…");
	private final JButton reviewTCardsButton = new JButton("Review T-cards…");
	private final JButton reviewAcceptedHaveButton = new JButton("Review accepted Have…");
	private final JButton createIcs204Button = new JButton("Create ICS 204 draft…");
	private final JMenuItem popupAddAssignment = new JMenuItem("Add assignment");
	private final JMenuItem popupAddHeading = new JMenuItem("Add resource heading…");
	private final JMenuItem popupEditAssignment = new JMenuItem("Edit assignment…");
	private final JMenuItem popupEditCounts = new JMenuItem("Edit counts…");
	private final JMenuItem popupEditHeading = new JMenuItem("Edit resource heading…");
	private final JMenuItem popupMoveAssignmentUp = new JMenuItem("Move assignment up");
	private final JMenuItem popupMoveAssignmentDown = new JMenuItem("Move assignment down");
	private final JMenuItem popupMoveHeadingLeft = new JMenuItem("Move heading left");
	private final JMenuItem popupMoveHeadingRight = new JMenuItem("Move heading right");
	private final JMenuItem popupReviewTCards = new JMenuItem("Review T-cards…");
	private final JMenuItem popupRemoveAssignment = new JMenuItem("Remove assignment");
	private final JMenuItem popupRemoveHeading = new JMenuItem("Remove resource heading");
	private final JTextArea detail = new JTextArea();
	private String selectedRowId = "";
	private int editorPeriodIndex;
	private boolean refreshing;

	/**
	 * Creates the editor bound to the controller's current incident.
	 *
	 * @param controller incident controller.
	 */
	public Ics215Panel(AppController controller) {
		super(new BorderLayout(6, 6));
		this.controller = controller;
		this.matrix = new JTable(new MatrixModel());
		this.matrixScroll = new JScrollPane(matrix);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		add(buildHeader(), BorderLayout.NORTH);
		add(buildMatrix(), BorderLayout.CENTER);
		add(buildDetail(), BorderLayout.SOUTH);
		periodSelector.addActionListener(event -> changePeriod());
		matrix.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		matrix.setCellSelectionEnabled(true);
		matrix.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		matrix.setFillsViewportHeight(true);
		matrix.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				selectRowFromTable();
			}
		});
		matrix.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				refreshActionAvailability();
			}
		});
		matrix.setComponentPopupMenu(buildMatrixPopupMenu());
		matrix.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				selectPopupCell(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				selectPopupCell(event);
			}
		});
		matrixScroll.getViewport().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent event) {
				configureMatrixColumns();
			}
		});
		refreshFromModel();
	}

	/**
	 * Flushes editor values to the active or next-period worksheet.
	 *
	 * @throws IllegalStateException when dates or a pending table edit are invalid.
	 */
	public void pushToModel() {
		UiSupport.commitTableEdits(matrix);
		if (refreshing) {
			return;
		}
		Ics215Form form = selectedForm();
		form.setTargetPeriodStart(parseDate(targetStart.getText(), "target start"));
		form.setTargetPeriodEnd(parseDate(targetEnd.getText(), "target end"));
		form.setPaperSize((Ics215Form.PaperSize) paperSize.getSelectedItem());
		Ics215Form.WorkRow row = selectedRow(form);
		if (row != null) {
			row.specialInstructions = detail.getText();
		}
		List<String> errors = form.validate();
		if (!errors.isEmpty()) {
			throw new IllegalStateException(String.join(" ", errors));
		}
	}

	/** Reloads the editor while preserving the selected stable row identity. */
	public void refreshFromModel() {
		refreshing = true;
		try {
			AppData data = controller.getData();
			contextLabel.setText("Incident: " + data.getIncidentContext().getIncidentName()
					+ " — planning dates are independent of Shared operational dates");
			Ics215Form form = selectedForm();
			targetStart.setText(formatDate(form.getTargetPeriodStart() == null
					? data.getIncidentContext().getOperationalPeriodStart() : form.getTargetPeriodStart()));
			targetEnd.setText(formatDate(form.getTargetPeriodEnd() == null
					? data.getIncidentContext().getOperationalPeriodEnd() : form.getTargetPeriodEnd()));
			paperSize.setSelectedItem(form.getPaperSize());
			detail.setText("");
			MatrixModel model = (MatrixModel) matrix.getModel();
			model.fireTableStructureChanged();
			configureMatrixColumns();
			restoreSelection(form);
			refreshDetail(form);
			refreshTotals(form);
			refreshActionAvailability();
		} finally {
			refreshing = false;
		}
	}

	/**
	 * Builds the incident and target-period heading.
	 *
	 * @return heading panel.
	 */
	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(4, 4));
		JPanel context = new JPanel(new GridLayout(0, 1, 2, 2));
		context.add(new JLabel("ICS 215 — Operational Planning Worksheet"));
		context.add(contextLabel);
		header.add(context, BorderLayout.NORTH);
		JPanel period = new JPanel(new FlowLayout(FlowLayout.LEADING));
		period.add(new JLabel("Worksheet:"));
		period.add(periodSelector);
		period.add(new JLabel("Target Date/Time From (24-hour):"));
		period.add(targetStart);
		period.add(new JLabel("To:"));
		period.add(targetEnd);
		period.add(new JLabel("PDF paper:"));
		period.add(paperSize);
		header.add(period, BorderLayout.SOUTH);
		return header;
	}

	/**
	 * Builds the scrollable, dynamically headed assignment/resource matrix.
	 *
	 * @return matrix with row and resource controls.
	 */
	private JPanel buildMatrix() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setBorder(BorderFactory.createTitledBorder("Assignments and resource counts (Req / Have / Need)"));
		panel.add(totalsLabel, BorderLayout.NORTH);
		panel.add(matrixScroll, BorderLayout.CENTER);
		JPanel actions = new JPanel(new GridLayout(0, 4, 4, 4));
		addButton(actions, addResourceHeadingButton, this::addHeading);
		addButton(actions, addAssignmentButton, this::addRow);
		addButton(actions, editAssignmentButton, this::editSelectedRow);
		addButton(actions, removeAssignmentButton, this::removeRow);
		addButton(actions, moveAssignmentUpButton, () -> moveRow(-1));
		addButton(actions, moveAssignmentDownButton, () -> moveRow(1));
		addButton(actions, editHeadingButton, this::editHeading);
		addButton(actions, moveHeadingLeftButton, () -> moveHeading(-1));
		addButton(actions, moveHeadingRightButton, () -> moveHeading(1));
		addButton(actions, removeHeadingButton, this::removeHeading);
		addButton(actions, editCountsButton, this::editCounts);
		addButton(actions, reviewTCardsButton, this::reviewTCards);
		addButton(actions, reviewAcceptedHaveButton, this::reviewAcceptedHave);
		addButton(actions, createIcs204Button, this::createIcs204Draft);
		addButton(actions, "Preparation details…", this::editPreparationDetails);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Creates contextual actions for adding and editing worksheet matrix content.
	 *
	 * @return matrix popup menu.
	 */
	private JPopupMenu buildMatrixPopupMenu() {
		JPopupMenu popup = new JPopupMenu();
		addPopupItem(popupAddHeading, this::addHeading, popup);
		addPopupItem(popupAddAssignment, this::addRow, popup);
		popup.addSeparator();
		addPopupItem(popupEditAssignment, this::editSelectedRow, popup);
		addPopupItem(popupMoveAssignmentUp, () -> moveRow(-1), popup);
		addPopupItem(popupMoveAssignmentDown, () -> moveRow(1), popup);
		addPopupItem(popupRemoveAssignment, this::removeRow, popup);
		popup.addSeparator();
		addPopupItem(popupEditCounts, this::editCounts, popup);
		addPopupItem(popupReviewTCards, this::reviewTCards, popup);
		addPopupItem(popupEditHeading, this::editHeading, popup);
		addPopupItem(popupMoveHeadingLeft, () -> moveHeading(-1), popup);
		addPopupItem(popupMoveHeadingRight, () -> moveHeading(1), popup);
		addPopupItem(popupRemoveHeading, this::removeHeading, popup);
		popup.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
				refreshActionAvailability();
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent event) {
			}
		});
		return popup;
	}

	/**
	 * Adds an action to the matrix popup menu.
	 *
	 * @param item popup item.
	 * @param action action to execute.
	 * @param popup destination popup.
	 */
	private void addPopupItem(JMenuItem item, Runnable action, JPopupMenu popup) {
		item.addActionListener(event -> runEditorAction(action));
		popup.add(item);
	}

	/**
	 * Selects the matrix cell under a popup-triggering pointer event.
	 *
	 * @param event mouse event.
	 */
	private void selectPopupCell(MouseEvent event) {
		if (!event.isPopupTrigger()) {
			return;
		}
		int row = matrix.rowAtPoint(event.getPoint());
		int column = matrix.columnAtPoint(event.getPoint());
		if (row >= 0 && column >= 0) {
			matrix.changeSelection(row, column, false, false);
		}
	}

	/**
	 * Builds the selected-row detail editor for long assignment text.
	 *
	 * @return detail editor.
	 */
	private JPanel buildDetail() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setBorder(BorderFactory.createTitledBorder("Selected work assignment and special instructions"));
		detail.setLineWrap(true);
		detail.setWrapStyleWord(true);
		detail.setRows(4);
		panel.add(new JScrollPane(detail), BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Adds one button to an action panel.
	 *
	 * @param panel panel receiving the button.
	 * @param label visible action name.
	 * @param action action to execute.
	 */
	private void addButton(JPanel panel, String label, Runnable action) {
		addButton(panel, new JButton(label), action);
	}

	/**
	 * Adds a reusable action button to the worksheet controls.
	 *
	 * @param panel panel receiving the button.
	 * @param button button whose enabled state follows matrix selection.
	 * @param action action to execute.
	 */
	private void addButton(JPanel panel, JButton button, Runnable action) {
		button.addActionListener(event -> runEditorAction(action));
		panel.add(button);
	}

	/**
	 * Fits the assignment column to remaining viewport width while retaining readable resource columns.
	 */
	private void configureMatrixColumns() {
		if (matrix.getColumnModel().getColumnCount() == 0) {
			return;
		}
		int fixedWidth = 90 + 160 + selectedForm().getResourceHeadings().size() * 130;
		int assignmentWidth = Math.max(380, matrixScroll.getViewport().getExtentSize().width - fixedWidth);
		matrix.getColumnModel().getColumn(0).setPreferredWidth(90);
		matrix.getColumnModel().getColumn(0).setWidth(90);
		matrix.getColumnModel().getColumn(1).setPreferredWidth(160);
		matrix.getColumnModel().getColumn(1).setWidth(160);
		matrix.getColumnModel().getColumn(2).setPreferredWidth(assignmentWidth);
		matrix.getColumnModel().getColumn(2).setWidth(assignmentWidth);
		for (int column = 3; column < matrix.getColumnModel().getColumnCount(); column++) {
			matrix.getColumnModel().getColumn(column).setPreferredWidth(130);
			matrix.getColumnModel().getColumn(column).setWidth(130);
		}
		int tableWidth = Math.max(matrixScroll.getViewport().getExtentSize().width, fixedWidth + assignmentWidth);
		matrix.setPreferredScrollableViewportSize(new Dimension(tableWidth,
				matrix.getPreferredScrollableViewportSize().height));
		matrix.revalidate();
	}

	/**
	 * Enables each contextual action only when its required selected row or resource heading exists.
	 */
	private void refreshActionAvailability() {
		Ics215Form form = selectedForm();
		boolean hasHeadings = !form.getResourceHeadings().isEmpty();
		boolean hasRow = selectedRow(form) != null;
		int headingIndex = selectedHeadingIndex();
		boolean hasHeading = headingIndex >= 0 && headingIndex < form.getResourceHeadings().size();
		boolean hasCell = hasRow && hasHeading;
		addAssignmentButton.setEnabled(hasHeadings);
		addAssignmentButton.setToolTipText(hasHeadings ? null
				: "Add a resource heading before creating assignment rows.");
		addResourceHeadingButton.setText(hasHeadings ? "Add resource heading…" : "Add first resource heading…");
		editAssignmentButton.setEnabled(hasRow);
		removeAssignmentButton.setEnabled(hasRow);
		moveAssignmentUpButton.setEnabled(hasRow && indexOfSelectedRow(form) > 0);
		moveAssignmentDownButton.setEnabled(hasRow && indexOfSelectedRow(form) < form.getWorkRows().size() - 1);
		editHeadingButton.setEnabled(hasHeading);
		moveHeadingLeftButton.setEnabled(hasHeading && headingIndex > 0);
		moveHeadingRightButton.setEnabled(hasHeading && headingIndex < form.getResourceHeadings().size() - 1);
		removeHeadingButton.setEnabled(hasHeading);
		editCountsButton.setEnabled(hasCell);
		reviewTCardsButton.setEnabled(hasHeading);
		reviewAcceptedHaveButton.setEnabled(hasCell);
		createIcs204Button.setEnabled(hasRow);
		detail.setEnabled(hasRow);
		popupAddAssignment.setEnabled(hasHeadings);
		popupAddHeading.setEnabled(true);
		popupEditAssignment.setEnabled(hasRow);
		popupEditCounts.setEnabled(hasCell);
		popupEditHeading.setEnabled(hasHeading);
		popupMoveAssignmentUp.setEnabled(hasRow && indexOfSelectedRow(form) > 0);
		popupMoveAssignmentDown.setEnabled(hasRow && indexOfSelectedRow(form) < form.getWorkRows().size() - 1);
		popupMoveHeadingLeft.setEnabled(hasHeading && headingIndex > 0);
		popupMoveHeadingRight.setEnabled(hasHeading && headingIndex < form.getResourceHeadings().size() - 1);
		popupReviewTCards.setEnabled(hasHeading);
		popupRemoveAssignment.setEnabled(hasRow);
		popupRemoveHeading.setEnabled(hasHeading);
	}

	/**
	 * Commits current values before changing worksheet or row selection.
	 *
	 * @param action editor operation.
	 */
	private void runEditorAction(Runnable action) {
		try {
			pushToModel();
			action.run();
			controller.markDirty();
			refreshFromModel();
		} catch (RuntimeException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "ICS 215", JOptionPane.WARNING_MESSAGE);
		}
	}

	/**
	 * Flushes before changing active/next worksheet context.
	 */
	private void changePeriod() {
		if (refreshing) {
			return;
		}
		int requestedPeriod = periodSelector.getSelectedIndex();
		if (requestedPeriod == editorPeriodIndex) {
			return;
		}
		refreshing = true;
		periodSelector.setSelectedIndex(editorPeriodIndex);
		refreshing = false;
		try {
			pushToModel();
			editorPeriodIndex = requestedPeriod;
			refreshing = true;
			periodSelector.setSelectedIndex(editorPeriodIndex);
			refreshing = false;
			selectedRowId = "";
			refreshFromModel();
		} catch (RuntimeException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "ICS 215", JOptionPane.WARNING_MESSAGE);
		}
	}

	/**
	 * Creates a new work row and selects it.
	 */
	private void addRow() {
		Ics215Form form = selectedForm();
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		form.getWorkRows().add(row);
		selectedRowId = row.id;
	}

	/**
	 * Opens selected-row details, retaining long work and instruction text.
	 */
	private void editSelectedRow() {
		Ics215Form formData = selectedForm();
		Ics215Form.WorkRow row = selectedRow(formData);
		if (row == null) {
			return;
		}
		JTextField branch = new JTextField(row.branch, 18);
		JComboBox<String> context = new JComboBox<>(MANAGEMENT_CONTEXTS);
		context.setSelectedItem(row.managementContext);
		JTextField contextValue = new JTextField(selectedContextValue(row), 18);
		JTextField assignment = new JTextField(row.assignment, 18);
		JTextField overhead = new JTextField(row.overheadPositions, 18);
		JTextField equipment = new JTextField(row.specialEquipment, 18);
		JTextField supplies = new JTextField(row.supplies, 18);
		JTextField reporting = new JTextField(row.reportingLocation, 18);
		JTextField arrival = new JTextField(formatDate(row.requestedArrival), 18);
		JTextArea instructions = new JTextArea(row.specialInstructions, 3, 18);
		instructions.setLineWrap(true);
		instructions.setWrapStyleWord(true);
		JPanel fields = new JPanel(new GridBagLayout());
		int line = 0;
		line = addAssignmentField(fields, line, "Branch:", branch);
		line = addAssignmentField(fields, line, "Division / Group / Other / Staging:", context);
		line = addAssignmentField(fields, line, "Management-context value:", contextValue);
		line = addAssignmentField(fields, line, "Work assignment:", assignment);
		line = addAssignmentField(fields, line, "Overhead positions:", overhead);
		line = addAssignmentField(fields, line, "Special equipment:", equipment);
		line = addAssignmentField(fields, line, "Supplies / aviation support:", supplies);
		line = addAssignmentField(fields, line, "Reporting location:", reporting);
		line = addAssignmentField(fields, line, "Requested arrival (yyyy-MM-dd HH:mm):", arrival);
		line = addAssignmentField(fields, line, "Special instructions:", new JScrollPane(instructions));

		Map<String, List<JTextField>> countFields = new LinkedHashMap<>();
		line = addResourceCountEditors(fields, line, formData, row, countFields);
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = 0;
		filler.gridy = line;
		filler.gridwidth = 2;
		filler.weightx = 1;
		filler.weighty = 1;
		filler.fill = GridBagConstraints.BOTH;
		fields.add(new JPanel(), filler);
		JScrollPane editorScroll = new JScrollPane(fields);
		if (!UiSupport.showResizableConfirmDialog(this, "Edit assignment and resource counts — ICS 215",
				editorScroll, assignmentEditorPreferredSize(
						GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getSize()))) {
			return;
		}
		LocalDateTime requestedArrival = parseDate(arrival.getText(), "requested arrival");
		Map<String, Ics215Form.CountPair[]> parsedCounts = new LinkedHashMap<>();
		for (Map.Entry<String, List<JTextField>> entry : countFields.entrySet()) {
			List<JTextField> values = entry.getValue();
			parsedCounts.put(entry.getKey(), new Ics215Form.CountPair[] {
					parsePair(values.get(0), values.get(1)), parsePair(values.get(2), values.get(3))
			});
		}
		row.branch = branch.getText();
		row.managementContext = (String) context.getSelectedItem();
		setSelectedContextValue(row, contextValue.getText());
		row.assignment = assignment.getText();
		row.overheadPositions = overhead.getText();
		row.specialEquipment = equipment.getText();
		row.supplies = supplies.getText();
		row.reportingLocation = reporting.getText();
		row.requestedArrival = requestedArrival;
		row.specialInstructions = instructions.getText();
		for (Map.Entry<String, Ics215Form.CountPair[]> entry : parsedCounts.entrySet()) {
			Ics215Form.ResourceAllocation allocation = row.resourceAllocations.computeIfAbsent(
					entry.getKey(), ignored -> new Ics215Form.ResourceAllocation());
			Ics215Form.CountPair[] values = entry.getValue();
			allocation.required = values[0];
			if (!sameCounts(allocation.acceptedHave, values[1])) {
				allocation.acceptedHave = values[1];
				allocation.manualSupplemental = new Ics215Form.CountPair(
						values[1].singleResources, values[1].teams);
				allocation.linkedResourceIds.clear();
				allocation.linkedResources.clear();
				allocation.acceptedAt = LocalDateTime.now(ZoneOffset.UTC);
				allocation.manualOverride = true;
			}
		}
	}

	/**
	 * Chooses a larger initial editor size that leaves room for window decorations.
	 *
	 * @param available maximum usable screen dimensions.
	 * @return preferred scroll-pane size.
	 */
	static Dimension assignmentEditorPreferredSize(Dimension available) {
		return new Dimension(Math.max(1, Math.min(1100, available.width - 48)),
				Math.max(1, Math.min(760, available.height - 80)));
	}

	/**
	 * Adds editable Required and Have fields for every ordered resource heading.
	 *
	 * @param fields assignment dialog content.
	 * @param line next grid row.
	 * @param formData worksheet supplying resource headings.
	 * @param row work assignment supplying existing quantities.
	 * @param countFields receives fields keyed by stable heading identity.
	 * @return next free grid row.
	 */
	private int addResourceCountEditors(JPanel fields, int line, Ics215Form formData, Ics215Form.WorkRow row,
			Map<String, List<JTextField>> countFields) {
		if (formData.getResourceHeadings().isEmpty()) {
			return addAssignmentField(fields, line, "",
					new JLabel("Add a resource heading before creating assignment rows."));
		}
		JPanel counts = new JPanel(new GridLayout(0, 7, 4, 4));
		counts.add(new JLabel("Resource heading"));
		counts.add(new JLabel("Req. resources"));
		counts.add(new JLabel("Req. teams"));
		counts.add(new JLabel("Have resources"));
		counts.add(new JLabel("Have teams"));
		counts.add(new JLabel("Need resources"));
		counts.add(new JLabel("Need teams"));
		for (Ics215Form.ResourceHeading heading : formData.getResourceHeadings()) {
			Ics215Form.ResourceAllocation allocation = row.resourceAllocations.get(heading.id);
			Ics215Form.CountPair required = allocation == null || allocation.required == null
					? new Ics215Form.CountPair() : allocation.required;
			Ics215Form.CountPair have = allocation == null || allocation.acceptedHave == null
					? new Ics215Form.CountPair() : allocation.acceptedHave;
			JTextField requiredSingles = countField(required.singleResources);
			JTextField requiredTeams = countField(required.teams);
			JTextField haveSingles = countField(have.singleResources);
			JTextField haveTeams = countField(have.teams);
			JTextField needSingles = calculatedCountField();
			JTextField needTeams = calculatedCountField();
			countFields.put(heading.id,
					List.of(requiredSingles, requiredTeams, haveSingles, haveTeams, needSingles, needTeams));
			updateNeedFields(requiredSingles, requiredTeams, haveSingles, haveTeams, needSingles, needTeams);
			listenForNeedUpdates(requiredSingles, requiredTeams, haveSingles, haveTeams, needSingles, needTeams);
			counts.add(new JLabel(heading.category + " / " + heading.kind + " / " + heading.type
					+ " (" + heading.countingUnit + ")"));
			counts.add(requiredSingles);
			counts.add(requiredTeams);
			counts.add(haveSingles);
			counts.add(haveTeams);
			counts.add(needSingles);
			counts.add(needTeams);
		}
		counts.setBorder(BorderFactory.createTitledBorder(
				"Resource counts by heading — same Required/Have fields as Edit counts"));
		line = addAssignmentField(fields, line, "Required / Have / calculated Need:", counts);
		return addAssignmentField(fields, line, "",
				new JLabel("<html>Leave blank for unknown; Need is calculated. Have entries are manual snapshots.<br>"
						+ "Changing Have replaces any linked T-card snapshot with these manual counts.</html>"));
	}

	/**
	 * Creates a non-editable field for a calculated resource quantity.
	 *
	 * @return calculated quantity display.
	 */
	private JTextField calculatedCountField() {
		JTextField field = new JTextField(4);
		field.setEditable(false);
		return field;
	}

	/**
	 * Updates calculated single-resource and team needs as count inputs change.
	 *
	 * @param requiredSingles required individual resources.
	 * @param requiredTeams required Strike Teams / Task Forces.
	 * @param haveSingles accepted individual resources.
	 * @param haveTeams accepted Strike Teams / Task Forces.
	 * @param needSingles calculated individual-resource need.
	 * @param needTeams calculated team need.
	 */
	private void updateNeedFields(JTextField requiredSingles, JTextField requiredTeams, JTextField haveSingles,
			JTextField haveTeams, JTextField needSingles, JTextField needTeams) {
		needSingles.setText(needComponent(requiredSingles.getText(), haveSingles.getText()));
		needTeams.setText(needComponent(requiredTeams.getText(), haveTeams.getText()));
	}

	/**
	 * Recalculates displayed Need whenever any Required or Have quantity changes.
	 *
	 * @param requiredSingles required individual resources.
	 * @param requiredTeams required Strike Teams / Task Forces.
	 * @param haveSingles accepted individual resources.
	 * @param haveTeams accepted Strike Teams / Task Forces.
	 * @param needSingles calculated individual-resource need.
	 * @param needTeams calculated team need.
	 */
	private void listenForNeedUpdates(JTextField requiredSingles, JTextField requiredTeams, JTextField haveSingles,
			JTextField haveTeams, JTextField needSingles, JTextField needTeams) {
		javax.swing.event.DocumentListener listener = new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent event) {
				updateNeedFields(requiredSingles, requiredTeams, haveSingles, haveTeams, needSingles, needTeams);
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent event) {
				updateNeedFields(requiredSingles, requiredTeams, haveSingles, haveTeams, needSingles, needTeams);
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent event) {
				updateNeedFields(requiredSingles, requiredTeams, haveSingles, haveTeams, needSingles, needTeams);
			}
		};
		requiredSingles.getDocument().addDocumentListener(listener);
		requiredTeams.getDocument().addDocumentListener(listener);
		haveSingles.getDocument().addDocumentListener(listener);
		haveTeams.getDocument().addDocumentListener(listener);
	}

	/**
	 * Calculates one signed requirement-minus-have component, leaving unknown inputs unknown.
	 *
	 * @param required required quantity text.
	 * @param have accepted Have quantity text.
	 * @return signed difference or question mark when either value is unknown/invalid.
	 */
	private String needComponent(String required, String have) {
		Integer requiredValue = previewCount(required);
		Integer haveValue = previewCount(have);
		if (requiredValue == null || haveValue == null) {
			return "?";
		}
		return Integer.toString(requiredValue - haveValue);
	}

	/**
	 * Parses a count for live preview without rejecting temporarily incomplete input.
	 *
	 * @param text count field contents.
	 * @return parsed nonnegative count or null.
	 */
	private Integer previewCount(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			int value = Integer.parseInt(text.trim());
			return value < 0 ? null : value;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	/**
	 * Adds one compact label and editor row to the assignment dialog.
	 *
	 * @param panel dialog content.
	 * @param row grid row.
	 * @param label visible field label.
	 * @param editor field component.
	 * @return next free grid row.
	 */
	private int addAssignmentField(JPanel panel, int row, String label, java.awt.Component editor) {
		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = row;
		labelConstraints.anchor = GridBagConstraints.LINE_START;
		labelConstraints.insets = new Insets(3, 4, 3, 8);
		panel.add(new JLabel(label), labelConstraints);
		GridBagConstraints editorConstraints = new GridBagConstraints();
		editorConstraints.gridx = 1;
		editorConstraints.gridy = row;
		editorConstraints.weightx = 1;
		editorConstraints.fill = GridBagConstraints.HORIZONTAL;
		editorConstraints.insets = new Insets(3, 4, 3, 4);
		panel.add(editor, editorConstraints);
		return row + 1;
	}

	/**
	 * Compares count pairs while treating absent pairs as unspecified components.
	 *
	 * @param first first quantity pair.
	 * @param second second quantity pair.
	 * @return whether both pairs represent the same quantities.
	 */
	private boolean sameCounts(Ics215Form.CountPair first, Ics215Form.CountPair second) {
		return Objects.equals(first == null ? null : first.singleResources, second.singleResources)
				&& Objects.equals(first == null ? null : first.teams, second.teams);
	}

	/**
	 * Removes the selected row without affecting other worksheet content.
	 */
	private void removeRow() {
		Ics215Form form = selectedForm();
		Ics215Form.WorkRow row = selectedRow(form);
		if (row != null) {
			form.getWorkRows().remove(row);
			selectedRowId = "";
		}
	}

	/**
	 * Reorders the selected row while preserving its identity.
	 *
	 * @param offset one position upward or downward.
	 */
	private void moveRow(int offset) {
		Ics215Form form = selectedForm();
		int index = indexOfSelectedRow(form);
		int destination = index + offset;
		if (index < 0 || destination < 0 || destination >= form.getWorkRows().size()) {
			return;
		}
		Ics215Form.WorkRow row = form.getWorkRows().remove(index);
		form.getWorkRows().add(destination, row);
	}

	/**
	 * Adds an operator-defined ordered resource heading.
	 */
	private void addHeading() {
		Ics215Form.ResourceHeading heading = editHeading(new Ics215Form.ResourceHeading());
		if (heading != null) {
			selectedForm().getResourceHeadings().add(heading);
		}
	}

	/**
	 * Edits the selected heading's category, type, and explicit counting unit.
	 */
	private void editHeading() {
		Ics215Form.ResourceHeading heading = selectedHeading();
		if (heading != null) {
			Ics215Form.ResourceHeading edited = editHeading(heading);
			if (edited != null) {
				int index = selectedHeadingIndex();
				selectedForm().getResourceHeadings().set(index, edited);
			}
		}
	}

	/**
	 * Reorders resource headings without changing their stable identities.
	 *
	 * @param offset one position left or right.
	 */
	private void moveHeading(int offset) {
		Ics215Form form = selectedForm();
		int index = selectedHeadingIndex();
		int destination = index + offset;
		if (index < 0 || destination < 0 || destination >= form.getResourceHeadings().size()) {
			return;
		}
		Ics215Form.ResourceHeading heading = form.getResourceHeadings().remove(index);
		form.getResourceHeadings().add(destination, heading);
	}

	/**
	 * Edits one resource heading without discarding its stable identity or evidence.
	 *
	 * @param heading source heading.
	 * @return edited heading, or null when cancelled.
	 */
	private Ics215Form.ResourceHeading editHeading(Ics215Form.ResourceHeading heading) {
		JTextField category = new JTextField(heading.category, 20);
		JTextField kind = new JTextField(heading.kind, 20);
		JTextField type = new JTextField(heading.type, 20);
		JTextField unit = new JTextField(heading.countingUnit, 20);
		JPanel fields = new JPanel(new BorderLayout(4, 8));
		fields.add(resourceHeadingGuidance(), BorderLayout.NORTH);
		JPanel inputs = new JPanel(new GridLayout(0, 2, 4, 4));
		inputs.add(new JLabel("Category:"));
		inputs.add(category);
		inputs.add(new JLabel("Kind:"));
		inputs.add(kind);
		inputs.add(new JLabel("Type:"));
		inputs.add(type);
		inputs.add(new JLabel("Counting unit (explicit):"));
		inputs.add(unit);
		fields.add(inputs, BorderLayout.CENTER);
		if (JOptionPane.showConfirmDialog(this, fields, "Resource heading",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return null;
		}
		heading.category = category.getText();
		heading.kind = kind.getText();
		heading.type = type.getText();
		heading.countingUnit = unit.getText();
		return heading;
	}

	/**
	 * Provides examples for distinguishing resource category, kind, type, and counting unit.
	 *
	 * @return embedded resource-heading guidance.
	 */
	private JLabel resourceHeadingGuidance() {
		return new JLabel("<html><b>Define one ordered resource column.</b><br>"
				+ "Category is the broad resource group, such as <b>Ground</b> or <b>Air</b>.<br>"
				+ "Kind names the resource, such as <b>Engine</b> or <b>Hand Crew</b>; type describes its capability, "
				+ "such as <b>Type 3</b>.<br>"
				+ "Examples: Ground / Engine / Type 3 (count by engines); "
				+ "Ground / Hand Crew / Type 2 (count by crews).<br>"
				+ "Choose the unit that matches the heading. Individual resources and Strike Teams / Task Forces "
				+ "are counted separately.</html>");
	}

	/**
	 * Removes a heading and its cell allocations after explicit confirmation.
	 */
	private void removeHeading() {
		Ics215Form form = selectedForm();
		Ics215Form.ResourceHeading heading = selectedHeading();
		if (heading == null) {
			return;
		}
		if (form.getResourceHeadings().size() == 1 && !form.getWorkRows().isEmpty()) {
			JOptionPane.showMessageDialog(this, "Keep at least one resource heading while assignment rows exist.",
					"Remove heading", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (JOptionPane.showConfirmDialog(this,
				"Remove this resource heading and its row counts?", "Remove heading",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		form.getResourceHeadings().remove(heading);
		form.getWorkRows().forEach(row -> row.resourceAllocations.remove(heading.id));
	}

	/**
	 * Edits Required, accepted Have, and manual supplemental quantities for one cell.
	 */
	private void editCounts() {
		Ics215Form form = selectedForm();
		Ics215Form.WorkRow row = selectedRow(form);
		Ics215Form.ResourceHeading heading = selectedHeading();
		if (row == null || heading == null) {
			return;
		}
		Ics215Form.ResourceAllocation allocation = row.resourceAllocations.computeIfAbsent(
				heading.id, ignored -> new Ics215Form.ResourceAllocation());
		Ics215Form.CountPair required = allocation.required == null ? new Ics215Form.CountPair()
				: allocation.required;
		Ics215Form.CountPair have = allocation.acceptedHave == null ? new Ics215Form.CountPair()
				: allocation.acceptedHave;
		JTextField reqSingles = countField(required.singleResources);
		JTextField reqTeams = countField(required.teams);
		JTextField haveSingles = countField(have.singleResources);
		JTextField haveTeams = countField(have.teams);
		JTextField needSingles = calculatedCountField();
		JTextField needTeams = calculatedCountField();
		updateNeedFields(reqSingles, reqTeams, haveSingles, haveTeams, needSingles, needTeams);
		listenForNeedUpdates(reqSingles, reqTeams, haveSingles, haveTeams, needSingles, needTeams);
		JPanel fields = countEditor(reqSingles, reqTeams, haveSingles, haveTeams, needSingles, needTeams);
		fields.setBorder(BorderFactory.createTitledBorder("Required / Have / calculated Need — "
				+ heading.category + " / " + heading.kind + " / " + heading.type
				+ " (" + heading.countingUnit + ")"));
		if (JOptionPane.showConfirmDialog(this, fields, "Required and accepted Have",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		allocation.required = parsePair(reqSingles, reqTeams);
		allocation.acceptedHave = parsePair(haveSingles, haveTeams);
		allocation.manualSupplemental = new Ics215Form.CountPair(
				allocation.acceptedHave.singleResources, allocation.acceptedHave.teams);
		allocation.linkedResourceIds.clear();
		allocation.linkedResources.clear();
		allocation.acceptedAt = LocalDateTime.now(ZoneOffset.UTC);
		allocation.manualOverride = true;
	}

	/**
	 * Edits ICS 215 block 14 preparation metadata.
	 */
	private void editPreparationDetails() {
		Ics215Form form = selectedForm();
		JTextField name = new JTextField(form.getPreparerName(), 24);
		JTextField position = new JTextField(form.getPreparerPositionTitle(), 24);
		JTextField signature = new JTextField(form.getPreparerSignature(), 24);
		JTextField prepared = new JTextField(formatDate(form.getPreparedDateTime()), 24);
		JPanel fields = new JPanel(new GridLayout(0, 2, 4, 4));
		fields.add(new JLabel("Name:"));
		fields.add(name);
		fields.add(new JLabel("Position/Title:"));
		fields.add(position);
		fields.add(new JLabel("Signature representation:"));
		fields.add(signature);
		fields.add(new JLabel("Date/Time (yyyy-MM-dd HH:mm):"));
		fields.add(prepared);
		if (JOptionPane.showConfirmDialog(this, fields, "Prepared by",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		form.setPreparerName(name.getText());
		form.setPreparerPositionTitle(position.getText());
		form.setPreparerSignature(signature.getText());
		form.setPreparedDateTime(parseDate(prepared.getText(), "preparation date/time"));
	}

	/**
	 * Opens operator-confirmed T-card selection and accepts a unique linked snapshot.
	 */
	private void reviewTCards() {
		Ics215Form form = selectedForm();
		Ics215Form.ResourceHeading heading = selectedHeading();
		Ics215Form.WorkRow row = selectedRow(form);
		if (heading == null) {
			JOptionPane.showMessageDialog(this, "Add or select a resource heading first.",
					"Review T-cards", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<Ics215ResourceReview.Candidate> candidates = Ics215ResourceReview.inspect(controller.getData());
		List<Ics215ResourceReview.Candidate> usable = candidates.stream()
				.filter(candidate -> !candidate.missingIdentity() && !candidate.ambiguousIdentity()
						&& !candidate.crewMemberOverlapNeedsReview()
						&& !candidate.cardCategory().equals(org.sarmanagement.icsforms.model.TCardType.HEADER.getLabel()))
				.toList();
		if (usable.isEmpty()) {
			JOptionPane.showMessageDialog(this, candidateWarnings(candidates),
					"Review T-cards", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String[] options = usable.stream().map(candidate -> candidate.resourceId() + " — "
				+ candidate.cardCategory() + " — " + candidate.displayName() + " — "
				+ candidate.status() + " / " + candidate.location() + " — " + candidate.assignmentContext())
				.toArray(String[]::new);
		JComboBox<String> targetRow = new JComboBox<>();
		targetRow.addItem("Incident-wide on hand (includes unallocated)");
		for (Ics215Form.WorkRow candidate : form.getWorkRows()) {
			targetRow.addItem(candidate.id + " — " + candidate.assignment);
		}
		JComboBox<Ics215Form.ResourceUnit> unit = new JComboBox<>(Ics215Form.ResourceUnit.values());
		JTextField manualSingles = countField(0);
		JTextField manualTeams = countField(0);
		JPanel review = new JPanel(new GridLayout(0, 1, 4, 4));
		JListWithSelection selection = new JListWithSelection(options);
		review.add(new JLabel("<html>Select identities; type/status do not assign resource kind or readiness.<br>"
				+ candidateWarnings(candidates) + "</html>"));
		review.add(new JScrollPane(selection.list()));
		review.add(targetRow);
		review.add(unit);
		review.add(new JLabel("Supplemental manual counts (separate from linked identities):"));
		review.add(countEditor(manualSingles, manualTeams, new JTextField(""), new JTextField(""), false));
		if (JOptionPane.showConfirmDialog(this, review, "Review T-cards for Have",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		List<Ics215Form.ResourceLink> links = new ArrayList<>();
		for (int index : selection.selectedIndices()) {
			Ics215Form.ResourceLink link = new Ics215Form.ResourceLink();
			link.resourceId = usable.get(index).resourceId();
			link.unit = (Ics215Form.ResourceUnit) unit.getSelectedItem();
			links.add(link);
		}
		Ics215Form.CountPair manual = parsePair(manualSingles, manualTeams);
		String target = (String) targetRow.getSelectedItem();
		if (target.startsWith("Incident-wide")) {
			Ics215ResourceReview.acceptOnHand(controller.getData(), form, heading.id, links, manual,
					LocalDateTime.now(ZoneOffset.UTC));
		} else {
			String rowId = target.substring(0, target.indexOf(" — "));
			Ics215ResourceReview.acceptRowHave(controller.getData(), form, rowId, heading.id, links,
					manual, LocalDateTime.now(ZoneOffset.UTC));
		}
	}

	/**
	 * Displays current discrepancies without silently changing an accepted Have snapshot.
	 */
	private void reviewAcceptedHave() {
		Ics215Form form = selectedForm();
		Ics215Form.WorkRow row = selectedRow(form);
		Ics215Form.ResourceHeading heading = selectedHeading();
		if (row == null || heading == null) {
			return;
		}
		Ics215Form.ResourceAllocation allocation = form.allocation(row.id, heading.id);
		if (allocation == null) {
			JOptionPane.showMessageDialog(this, "No Have snapshot exists for this row and heading.",
					"Review accepted Have", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Ics215ResourceReview.RefreshResult result = Ics215ResourceReview.reviewRowHave(
				controller.getData(), form, row.id, heading.id);
		String discrepancies = result.discrepancies().isEmpty()
				? "No linked identity discrepancy detected." : String.join("\n", result.discrepancies());
		JOptionPane.showMessageDialog(this, "Accepted snapshot: " + pairText(allocation.acceptedHave)
				+ "\nCurrently linked: " + pairText(result.observed()) + "\n"
				+ (result.manualOverrideRetained() ? "Manual override retained.\n" : "")
				+ discrepancies + "\n\nReview does not update the accepted snapshot.",
				"Review accepted Have", JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Summarizes excluded candidates that need an explicit identity or overlap resolution.
	 *
	 * @param candidates inspected cards.
	 * @return concise review warnings.
	 */
	private String candidateWarnings(List<Ics215ResourceReview.Candidate> candidates) {
		long missing = candidates.stream().filter(Ics215ResourceReview.Candidate::missingIdentity).count();
		long ambiguous = candidates.stream().filter(Ics215ResourceReview.Candidate::ambiguousIdentity).count();
		long overlap = candidates.stream()
				.filter(Ics215ResourceReview.Candidate::crewMemberOverlapNeedsReview).count();
		return "Missing IDs: " + missing + "; ambiguous IDs: " + ambiguous
				+ "; possible crew/member overlaps requiring resolution: " + overlap
				+ ". Recorded status is not readiness.";
	}

	/**
	 * Previews a selected work row and appends a non-destructive ICS 204 draft.
	 */
	private void createIcs204Draft() {
		Ics215Form form = selectedForm();
		Ics215Form.WorkRow row = selectedRow(form);
		if (row == null) {
			return;
		}
		if (hasExistingDraft(row)) {
			JOptionPane.showMessageDialog(this, "An ICS 204 draft already exists for this worksheet row.",
					"Create ICS 204 draft", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String preview = "Management context: " + row.managementContext + "\n"
				+ selectedContextValue(row) + "\nAssignment: " + row.assignment
				+ "\nInstructions: " + row.specialInstructions
				+ "\n\nICS 215-only information retained here: resource quantities, overhead, equipment/supplies, "
				+ "reporting details, requested arrival, and preparation evidence.";
		if (JOptionPane.showConfirmDialog(this, preview, "Preview unapproved ICS 204 draft",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		Ics204Form draft = Ics215ResourceReview.createIcs204Draft(form, row);
		row.ics204DraftFormId = draft.getFormId();
		controller.getData().getAdditionalForms204().add(draft);
	}

	/**
	 * Tests whether the linked ICS 204 draft is already present.
	 *
	 * @param row work row.
	 * @return true when the draft has already been created.
	 */
	private boolean hasExistingDraft(Ics215Form.WorkRow row) {
		String id = row.ics204DraftFormId;
		if (id == null || id.isBlank()) {
			return false;
		}
		AppData data = controller.getData();
		return data.getForm204().getFormId().equals(id)
				|| data.getAdditionalForms204().stream().anyMatch(form -> form.getFormId().equals(id));
	}

	/**
	 * Creates a numeric field for an optional nonnegative count.
	 *
	 * @param value initial value.
	 * @return formatted field.
	 */
	private JTextField countField(Integer value) {
		return new JTextField(value == null ? "" : value.toString(), 4);
	}

	/**
	 * Builds a pair of required/have integer controls.
	 *
	 * @param requiredSingles single-resource requirement.
	 * @param requiredTeams Strike Team/Task Force requirement.
	 * @param haveSingles accepted single-resource Have quantity.
	 * @param haveTeams accepted team Have quantity.
	 * @return count editor.
	 */
	private JPanel countEditor(JTextField requiredSingles, JTextField requiredTeams,
			JTextField haveSingles, JTextField haveTeams) {
		return countEditor(requiredSingles, requiredTeams, haveSingles, haveTeams, true);
	}

	/**
	 * Builds the Required / Have / calculated Need grid for one resource heading.
	 *
	 * @param requiredSingles required individual resources.
	 * @param requiredTeams required Strike Teams / Task Forces.
	 * @param haveSingles accepted individual resources.
	 * @param haveTeams accepted Strike Teams / Task Forces.
	 * @param needSingles calculated individual-resource need.
	 * @param needTeams calculated team need.
	 * @return count grid.
	 */
	private JPanel countEditor(JTextField requiredSingles, JTextField requiredTeams,
			JTextField haveSingles, JTextField haveTeams, JTextField needSingles, JTextField needTeams) {
		JPanel fields = new JPanel(new GridLayout(0, 3, 4, 4));
		fields.add(new JLabel(""));
		fields.add(new JLabel("Single resources"));
		fields.add(new JLabel("Strike Teams / Task Forces"));
		fields.add(new JLabel("Required"));
		fields.add(requiredSingles);
		fields.add(requiredTeams);
		fields.add(new JLabel("Have"));
		fields.add(haveSingles);
		fields.add(haveTeams);
		fields.add(new JLabel("Need"));
		fields.add(needSingles);
		fields.add(needTeams);
		return fields;
	}

	/**
	 * Builds controls for counts, optionally including Required and Have labels.
	 *
	 * @param firstSingles first single-resource field.
	 * @param firstTeams first team field.
	 * @param secondSingles second single-resource field.
	 * @param secondTeams second team field.
	 * @param includeRequiredHave true to show Required and Have rows.
	 * @return count controls.
	 */
	private JPanel countEditor(JTextField firstSingles, JTextField firstTeams,
			JTextField secondSingles, JTextField secondTeams, boolean includeRequiredHave) {
		JPanel fields = new JPanel(new GridLayout(0, 3, 4, 4));
		fields.add(new JLabel(""));
		fields.add(new JLabel("Single resources"));
		fields.add(new JLabel("Strike Teams / Task Forces"));
		fields.add(new JLabel(includeRequiredHave ? "Required" : "Manual supplemental"));
		fields.add(firstSingles);
		fields.add(firstTeams);
		if (includeRequiredHave) {
			fields.add(new JLabel("Have"));
			fields.add(secondSingles);
			fields.add(secondTeams);
		}
		return fields;
	}

	/**
	 * Parses two count fields while preserving blank as unknown.
	 *
	 * @param singles individual-resource count.
	 * @param teams team count.
	 * @return parsed count pair.
	 */
	private Ics215Form.CountPair parsePair(JTextField singles, JTextField teams) {
		Integer singleValue = parseCount(singles.getText());
		Integer teamValue = parseCount(teams.getText());
		return new Ics215Form.CountPair(singleValue, teamValue);
	}

	/**
	 * Parses an optional nonnegative integer.
	 *
	 * @param text field value.
	 * @return null for an unspecified field.
	 */
	private Integer parseCount(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			int value = Integer.parseInt(text.trim());
			if (value < 0) {
				throw new NumberFormatException();
			}
			return value;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Resource counts must be nonnegative whole numbers.");
		}
	}

	/**
	 * Parses an optional 24-hour date/time.
	 *
	 * @param value displayed value.
	 * @param label field label.
	 * @return parsed date/time or null when blank.
	 */
	private LocalDateTime parseDate(String value, String label) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			LocalDateTime displayed = LocalDateTime.parse(value.trim(), DATE_TIME);
			return LocalDateTime.ofInstant(displayed.atZone(UiSupport.getDateTimeDisplayZone()).toInstant(),
					ZoneOffset.UTC);
		} catch (DateTimeParseException exception) {
			throw new IllegalStateException("Enter " + label + " as yyyy-MM-dd HH:mm.");
		}
	}

	/**
	 * Formats optional date/time in 24-hour form.
	 *
	 * @param value date/time value.
	 * @return blank for unknown values.
	 */
	private String formatDate(LocalDateTime value) {
		if (value == null) {
			return "";
		}
		Instant instant = value.toInstant(ZoneOffset.UTC);
		return DATE_TIME.format(LocalDateTime.ofInstant(instant, UiSupport.getDateTimeDisplayZone()));
	}

	/**
	 * Returns the worksheet selected by the context selector.
	 *
	 * @return active- or next-period worksheet.
	 */
	private Ics215Form selectedForm() {
		return editorPeriodIndex == 0
				? controller.getData().getForm215() : controller.getData().getNextPeriodForm215();
	}

	/**
	 * Returns the selected row by stable identity.
	 *
	 * @param form selected worksheet.
	 * @return selected row or null.
	 */
	private Ics215Form.WorkRow selectedRow(Ics215Form form) {
		if (selectedRowId.isBlank()) {
			return null;
		}
		return form.getWorkRows().stream()
				.filter(row -> row != null && selectedRowId.equals(row.id)).findFirst().orElse(null);
	}

	/**
	 * Returns the matrix-selected resource heading, if any.
	 *
	 * @return selected resource heading or null.
	 */
	private Ics215Form.ResourceHeading selectedHeading() {
		int column = matrix.getSelectedColumn();
		int headingIndex = column - 3;
		List<Ics215Form.ResourceHeading> headings = selectedForm().getResourceHeadings();
		return headingIndex < 0 || headingIndex >= headings.size() ? null : headings.get(headingIndex);
	}

	/**
	 * Returns the selected heading column index.
	 *
	 * @return heading index or -1.
	 */
	private int selectedHeadingIndex() {
		return matrix.getSelectedColumn() - 3;
	}

	/**
	 * Locates the selected row in the ordered row list.
	 *
	 * @param form selected worksheet.
	 * @return row index or -1.
	 */
	private int indexOfSelectedRow(Ics215Form form) {
		for (int index = 0; index < form.getWorkRows().size(); index++) {
			Ics215Form.WorkRow row = form.getWorkRows().get(index);
			if (row != null && selectedRowId.equals(row.id)) {
				return index;
			}
		}
		return -1;
	}

	/**
	 * Restores JTable selection from the stable row identity.
	 *
	 * @param form selected worksheet.
	 */
	private void restoreSelection(Ics215Form form) {
		int rowIndex = indexOfSelectedRow(form);
		if (rowIndex >= 0) {
			matrix.setRowSelectionInterval(rowIndex, rowIndex);
		}
	}

	/**
	 * Saves selected-row identity and text when the user selects another matrix row.
	 */
	private void selectRowFromTable() {
		if (refreshing) {
			return;
		}
		Ics215Form form = selectedForm();
		Ics215Form.WorkRow previous = selectedRow(form);
		if (previous != null) {
			previous.specialInstructions = detail.getText();
		}
		int rowIndex = matrix.getSelectedRow();
		selectedRowId = rowIndex < 0 || rowIndex >= form.getWorkRows().size()
				? "" : form.getWorkRows().get(rowIndex).id;
		refreshDetail(form);
		refreshActionAvailability();
	}

	/**
	 * Loads selected-row text into the detail editor.
	 *
	 * @param form selected worksheet.
	 */
	private void refreshDetail(Ics215Form form) {
		Ics215Form.WorkRow row = selectedRow(form);
		detail.setText(row == null ? "" : row.specialInstructions);
	}

	/**
	 * Returns the field value selected by a row's management-context key.
	 *
	 * @param row selected work row.
	 * @return selected value.
	 */
	private String selectedContextValue(Ics215Form.WorkRow row) {
		return switch (row.managementContext) {
			case Ics204Form.MANAGEMENT_BRANCH -> row.branch;
			case Ics204Form.MANAGEMENT_DIVISION -> row.division;
			case Ics204Form.MANAGEMENT_GROUP -> row.group;
			default -> row.stagingArea;
		};
	}

	/**
	 * Stores the selected management value without collapsing the context choice.
	 *
	 * @param row selected work row.
	 * @param value chosen management value.
	 */
	private void setSelectedContextValue(Ics215Form.WorkRow row, String value) {
		switch (row.managementContext) {
			case Ics204Form.MANAGEMENT_BRANCH -> row.branch = value;
			case Ics204Form.MANAGEMENT_DIVISION -> row.division = value;
			case Ics204Form.MANAGEMENT_GROUP -> row.group = value;
			default -> row.stagingArea = value;
		}
	}

	/**
	 * Formats the currently selected resource-cell quantities.
	 *
	 * @param row worksheet row.
	 * @param heading resource heading.
	 * @return Req/Have/Need summary with slash notation.
	 */
	private String cellText(Ics215Form.WorkRow row, Ics215Form.ResourceHeading heading) {
		Ics215Form.ResourceAllocation allocation = row.resourceAllocations.get(heading.id);
		if (allocation == null) {
			return "—";
		}
		return "R " + pairText(allocation.required) + " / H " + pairText(allocation.acceptedHave)
				+ " / N " + pairText(selectedForm().need(row.id, heading.id));
	}

	/**
	 * Formats a count pair with slash-separated team quantities.
	 *
	 * @param pair count pair.
	 * @return single/team display.
	 */
	private String pairText(Ics215Form.CountPair pair) {
		return pair == null ? "?/?" : (pair.singleResources == null ? "?" : pair.singleResources)
				+ "/" + (pair.teams == null ? "?" : pair.teams);
	}

	/**
	 * Refreshes the worksheet totals for each ordered resource heading.
	 *
	 * @param form worksheet to summarize.
	 */
	private void refreshTotals(Ics215Form form) {
		List<String> totals = new ArrayList<>();
		for (Ics215Form.ResourceHeading heading : form.getResourceHeadings()) {
			totals.add(heading.category + " / " + heading.kind + " / " + heading.type
					+ " — Required " + pairText(form.totalRequired(heading.id))
					+ " / On Hand " + pairText(form.totalOnHand(heading.id))
					+ " / Need To Order " + pairText(form.totalNeedToOrder(heading.id)));
		}
		totalsLabel.setText("<html>Blocks 11–13 totals: "
				+ (totals.isEmpty() ? "add the first resource heading before adding assignments"
						: String.join("<br>", totals)) + "</html>");
	}

	/**
	 * Matrix view of assignment details and dynamically ordered resource headings.
	 */
	private final class MatrixModel extends AbstractTableModel {
		/** {@inheritDoc} */
		@Override
		public int getRowCount() {
			return selectedForm().getWorkRows().size();
		}

		/** {@inheritDoc} */
		@Override
		public int getColumnCount() {
			return 3 + selectedForm().getResourceHeadings().size();
		}

		/** {@inheritDoc} */
		@Override
		public String getColumnName(int column) {
			if (column == 0) {
				return "3. Branch";
			}
			if (column == 1) {
				return "4. Division / Group / Other";
			}
			if (column == 2) {
				return "5. Work Assignment / Instructions";
			}
			Ics215Form.ResourceHeading heading = selectedForm().getResourceHeadings().get(column - 3);
			return "6. " + heading.category + " / " + heading.kind + " / " + heading.type
					+ " (" + heading.countingUnit + ")";
		}

		/** {@inheritDoc} */
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Ics215Form.WorkRow row = selectedForm().getWorkRows().get(rowIndex);
			return switch (columnIndex) {
				case 0 -> row.branch;
				case 1 -> selectedContextValue(row);
				case 2 -> row.assignment;
				default -> cellText(row, selectedForm().getResourceHeadings().get(columnIndex - 3));
			};
		}

		/** {@inheritDoc} */
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false;
		}
	}

	/**
	 * Holds a multi-select candidate list and exposes selection indices.
	 */
	private static final class JListWithSelection {
		private final javax.swing.JList<String> list;

		/**
		 * Creates a candidate list.
		 *
		 * @param values displayed candidates.
		 */
		private JListWithSelection(String[] values) {
			list = new javax.swing.JList<>(values);
			list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			list.setVisibleRowCount(Math.min(8, values.length));
		}

		/**
		 * Returns the candidate list component.
		 *
		 * @return selectable list.
		 */
		private javax.swing.JList<String> list() {
			return list;
		}

		/**
		 * Returns selected candidate indices.
		 *
		 * @return selected indices.
		 */
		private int[] selectedIndices() {
			return list.getSelectedIndices();
		}
	}
}
