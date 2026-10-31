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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		add(buildHeader(), BorderLayout.NORTH);
		add(buildMatrix(), BorderLayout.CENTER);
		add(buildDetail(), BorderLayout.SOUTH);
		periodSelector.addActionListener(event -> changePeriod());
		matrix.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		matrix.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		matrix.setFillsViewportHeight(true);
		matrix.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				selectRowFromTable();
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
			restoreSelection(form);
			refreshDetail(form);
			refreshTotals(form);
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
		panel.add(new JScrollPane(matrix), BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
		addButton(actions, "Add assignment", this::addRow);
		addButton(actions, "Edit assignment…", this::editSelectedRow);
		addButton(actions, "Remove assignment", this::removeRow);
		addButton(actions, "Move up", () -> moveRow(-1));
		addButton(actions, "Move down", () -> moveRow(1));
		addButton(actions, "Add resource heading…", this::addHeading);
		addButton(actions, "Edit heading…", this::editHeading);
		addButton(actions, "Move heading left", () -> moveHeading(-1));
		addButton(actions, "Move heading right", () -> moveHeading(1));
		addButton(actions, "Remove heading", this::removeHeading);
		addButton(actions, "Edit counts…", this::editCounts);
		addButton(actions, "Review T-cards…", this::reviewTCards);
		addButton(actions, "Review accepted Have…", this::reviewAcceptedHave);
		addButton(actions, "Create ICS 204 draft…", this::createIcs204Draft);
		addButton(actions, "Preparation details…", this::editPreparationDetails);
		panel.add(new JScrollPane(actions), BorderLayout.SOUTH);
		return panel;
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
		JButton button = new JButton(label);
		button.addActionListener(event -> runEditorAction(action));
		panel.add(button);
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
		Ics215Form.WorkRow row = selectedRow(selectedForm());
		if (row == null) {
			return;
		}
		JTextField branch = new JTextField(row.branch, 24);
		JComboBox<String> context = new JComboBox<>(MANAGEMENT_CONTEXTS);
		context.setSelectedItem(row.managementContext);
		JTextField contextValue = new JTextField(selectedContextValue(row), 24);
		JTextField assignment = new JTextField(row.assignment, 24);
		JTextField overhead = new JTextField(row.overheadPositions, 24);
		JTextField equipment = new JTextField(row.specialEquipment, 24);
		JTextField supplies = new JTextField(row.supplies, 24);
		JTextField reporting = new JTextField(row.reportingLocation, 24);
		JTextField arrival = new JTextField(formatDate(row.requestedArrival), 24);
		JTextArea instructions = new JTextArea(row.specialInstructions, 5, 24);
		JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
		form.add(new JLabel("Branch:"));
		form.add(branch);
		form.add(new JLabel("Division/Group/Other/Staging:"));
		form.add(context);
		form.add(new JLabel("Management-context value:"));
		form.add(contextValue);
		form.add(new JLabel("Work assignment:"));
		form.add(assignment);
		form.add(new JLabel("Overhead positions:"));
		form.add(overhead);
		form.add(new JLabel("Special equipment:"));
		form.add(equipment);
		form.add(new JLabel("Supplies / aviation support:"));
		form.add(supplies);
		form.add(new JLabel("Reporting location:"));
		form.add(reporting);
		form.add(new JLabel("Requested arrival (yyyy-MM-dd HH:mm):"));
		form.add(arrival);
		form.add(new JLabel("Special instructions:"));
		form.add(new JScrollPane(instructions));
		if (JOptionPane.showConfirmDialog(this, form, "Edit ICS 215 work assignment",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		row.branch = branch.getText();
		row.managementContext = (String) context.getSelectedItem();
		setSelectedContextValue(row, contextValue.getText());
		row.assignment = assignment.getText();
		row.overheadPositions = overhead.getText();
		row.specialEquipment = equipment.getText();
		row.supplies = supplies.getText();
		row.reportingLocation = reporting.getText();
		row.requestedArrival = parseDate(arrival.getText(), "requested arrival");
		row.specialInstructions = instructions.getText();
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
		JPanel fields = new JPanel(new GridLayout(0, 2, 4, 4));
		fields.add(new JLabel("Category:"));
		fields.add(category);
		fields.add(new JLabel("Kind:"));
		fields.add(kind);
		fields.add(new JLabel("Type:"));
		fields.add(type);
		fields.add(new JLabel("Counting unit (explicit):"));
		fields.add(unit);
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
	 * Removes a heading and its cell allocations after explicit confirmation.
	 */
	private void removeHeading() {
		Ics215Form form = selectedForm();
		Ics215Form.ResourceHeading heading = selectedHeading();
		if (heading == null || JOptionPane.showConfirmDialog(this,
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
		JPanel fields = countEditor(reqSingles, reqTeams, haveSingles, haveTeams);
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
		return new JTextField(value == null ? "" : value.toString(), 8);
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
		int headingIndex = column - 4;
		List<Ics215Form.ResourceHeading> headings = selectedForm().getResourceHeadings();
		return headingIndex < 0 || headingIndex >= headings.size() ? null : headings.get(headingIndex);
	}

	/**
	 * Returns the selected heading column index.
	 *
	 * @return heading index or -1.
	 */
	private int selectedHeadingIndex() {
		return matrix.getSelectedColumn() - 4;
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
				+ (totals.isEmpty() ? "add resource headings" : String.join("<br>", totals)) + "</html>");
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
			return 4 + selectedForm().getResourceHeadings().size();
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
				return "5. Work Assignment";
			}
			if (column == 3) {
				return "Selected row details";
			}
			Ics215Form.ResourceHeading heading = selectedForm().getResourceHeadings().get(column - 4);
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
				case 3 -> row.specialInstructions;
				default -> cellText(row, selectedForm().getResourceHeadings().get(columnIndex - 4));
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
