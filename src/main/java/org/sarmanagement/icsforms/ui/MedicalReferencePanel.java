/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewSupport;
import org.sarmanagement.icsforms.model.PlanReviewStatus;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.pdf.Ics206CaninePdfRenderer;
import org.sarmanagement.icsforms.pdf.Ics206PdfRenderer;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Read-only structured medical reference content shared by the two strictly
 * separate reference windows.
 */
class MedicalReferencePanel extends JPanel {
	private static final DateTimeFormatter PERIOD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private final AppController controller;
	private final boolean canine;
	private final Consumer<WorkspaceDestination> navigator;
	private final AppController.IncidentBinding binding;
	private final JLabel banner = new JLabel();
	private final JLabel completion = new JLabel();
	private final JTextArea instructions = readOnlyArea();
	private final JTextArea personnel = readOnlyArea();
	private final JTextArea transport = readOnlyArea();
	private final DefaultListModel<FacilityChoice> facilityModel = new DefaultListModel<>();
	private final JList<FacilityChoice> facilities = new JList<>(facilityModel);
	private final JTextArea facilityDetail = readOnlyArea();
	private final JComboBox<TaskChoice> taskContext = new JComboBox<>();
	private final JTextArea taskDetail = readOnlyArea();
	private final AsyncFormPreviewPanel preview;
	private final AppController.IncidentChangeSubscription subscription;

	/**
	 * Creates one reference type with no runtime type toggle.
	 *
	 * @param controller incident controller.
	 * @param canine true only for the canine reference.
	 * @param navigator typed full-workspace navigation callback.
	 */
	MedicalReferencePanel(AppController controller, boolean canine, Consumer<WorkspaceDestination> navigator) {
		super(new BorderLayout(8, 8));
		this.controller = controller;
		this.canine = canine;
		this.navigator = navigator;
		binding = controller.captureIncidentBinding();
		preview = new AsyncFormPreviewPanel(canine ? new Ics206CaninePdfRenderer() : new Ics206PdfRenderer());
		banner.getAccessibleContext().setAccessibleName(referenceTitle() + " incident and review status");
		add(buildHeader(), BorderLayout.NORTH);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildStructuredReference(), preview);
		split.setResizeWeight(0.46);
		split.setDividerLocation(560);
		add(split, BorderLayout.CENTER);
		refreshFromModel();
		subscription = controller.addIncidentChangeListener(change -> {
			if (controller.isCurrent(binding)) {
				refreshFromModel();
			} else {
				preview.cancel();
			}
		});
	}

	/**
	 * Releases the live listener and pending preview worker.
	 */
	void dispose() {
		subscription.close();
		preview.cancel();
	}

	/**
	 * Returns the permanent, unambiguous reference title.
	 *
	 * @return reference title.
	 */
	String referenceTitle() {
		return canine ? "Canine Medical Reference — adapted ICS 206CANINE" : "Human Medical Reference — ICS 206";
	}

	/**
	 * Returns currently displayed emergency instructions for focused separation tests.
	 *
	 * @return reference-specific instructions.
	 */
	String displayedInstructions() {
		return instructions.getText();
	}

	/**
	 * Returns currently displayed personnel details for focused linkage tests.
	 *
	 * @return reference-specific personnel details.
	 */
	String displayedPersonnel() {
		return personnel.getText();
	}

	/**
	 * Returns the number of visible accepted and candidate facility rows.
	 *
	 * @return facility row count.
	 */
	int displayedFacilityCount() {
		return facilityModel.size();
	}

	/** Refreshes structured data without changing logs, tasks, routes, or review. */
	void refreshFromModel() {
		if (!controller.isCurrent(binding)) {
			return;
		}
		AppData data = controller.getData();
		String start = data.getIncidentContext().getOperationalPeriodStart() == null
				? "unspecified"
				: PERIOD_TIME.format(data.getIncidentContext().getOperationalPeriodStart());
		String end = data.getIncidentContext().getOperationalPeriodEnd() == null
				? "unspecified"
				: PERIOD_TIME.format(data.getIncidentContext().getOperationalPeriodEnd());
		PlanReviewArea area = canine ? PlanReviewArea.CANINE_MEDICAL : PlanReviewArea.HUMAN_MEDICAL;
		banner.setText("<html><b>" + referenceTitle() + "</b> | Incident: "
				+ html(data.getIncidentContext().getIncidentName()) + " | Period: " + start + " — " + end
				+ "</html>");
		PlanReviewStatus status = data.getPlanReviewStatus(area);
		boolean recorded = IncidentCommandSummary.recordedContent(data, area) > 0;
		boolean approved = recorded && status == PlanReviewStatus.REVIEWED_APPROVED;
		completion.setText((approved ? "✓ Content recorded / reviewed-approved — " : "○ Incomplete — ")
				+ (recorded ? "" : "No content recorded; ") + status.displayLabel());
		completion.setToolTipText("Recorded content with a current content-bound approval only — "
				+ "not plan sufficiency or operational readiness.");
		completion.getAccessibleContext().setAccessibleName(referenceTitle() + " completion and approval");
		if (canine) {
			refreshCanine(data);
		} else {
			refreshHuman(data);
		}
		refreshTasks(data);
		requestPreview();
	}

	/**
	 * Builds the incident banner and explicit actions.
	 *
	 * @return header.
	 */
	private JPanel buildHeader() {
		JPanel panel = new JPanel(new BorderLayout(6, 4));
		panel.add(banner, BorderLayout.CENTER);
		panel.add(completion, BorderLayout.SOUTH);
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING));
		JButton editor = new JButton("Open full editor");
		editor.addActionListener(event -> navigator.accept(
				canine ? WorkspaceDestination.canineMedicalPlan() : WorkspaceDestination.humanMedicalPlan()));
		JButton log = new JButton(canine ? "Log canine medical event…" : "Log responder medical event…");
		log.addActionListener(event -> logMedicalEvent());
		actions.add(editor);
		actions.add(log);
		panel.add(actions, BorderLayout.EAST);
		return panel;
	}

	/**
	 * Builds structured reference information that remains usable without a preview.
	 *
	 * @return structured reference panel.
	 */
	private JPanel buildStructuredReference() {
		JPanel text = new JPanel(new GridLayout(3, 1, 4, 4));
		text.add(titled("Emergency instructions", instructions));
		text.add(titled("Personnel and aid stations", personnel));
		text.add(titled("Transportation", transport));
		facilities.setVisibleRowCount(6);
		facilities.getAccessibleContext().setAccessibleName(referenceTitle() + " facilities");
		facilities.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				FacilityChoice choice = facilities.getSelectedValue();
				facilityDetail.setText(choice == null ? "" : choice.detail());
				facilityDetail.setCaretPosition(0);
			}
		});
		JSplitPane facilitySplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(facilities),
				new JScrollPane(facilityDetail));
		facilitySplit.setResizeWeight(0.45);
		JPanel facilityPanel = new JPanel(new BorderLayout());
		facilityPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Facilities — operator chooses"));
		facilityPanel.add(facilitySplit, BorderLayout.CENTER);
		taskContext.addActionListener(event -> refreshTaskDetail());
		JPanel taskPanel = new JPanel(new BorderLayout(4, 4));
		taskPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Optional task context"));
		taskPanel.add(taskContext, BorderLayout.NORTH);
		taskPanel.add(new JScrollPane(taskDetail), BorderLayout.CENTER);
		JSplitPane lower = new JSplitPane(JSplitPane.VERTICAL_SPLIT, facilityPanel, taskPanel);
		lower.setResizeWeight(0.65);
		JSplitPane structured = new JSplitPane(JSplitPane.VERTICAL_SPLIT, text, lower);
		structured.setResizeWeight(0.45);
		structured.setPreferredSize(new Dimension(560, 680));
		JPanel root = new JPanel(new BorderLayout());
		root.add(structured, BorderLayout.CENTER);
		return root;
	}

	/**
	 * Creates a titled scrollable text panel.
	 *
	 * @param title border title.
	 * @param area read-only text area.
	 * @return titled panel.
	 */
	private JPanel titled(String title, JTextArea area) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(javax.swing.BorderFactory.createTitledBorder(title));
		panel.add(new JScrollPane(area), BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Loads only standard human-plan content.
	 *
	 * @param data active incident.
	 */
	private void refreshHuman(AppData data) {
		MedicalPlan plan = data.getMedicalPlan();
		instructions.setText(join(plan.getCall911Procedure(), plan.getEmergencyProcedures(),
				plan.isAviationAssetsUsed() ? plan.getAirOperationsReminder() : ""));
		List<String> people = new ArrayList<>();
		people.add("Medical Unit Leader: " + data.getMedicalUnitLeaderAssignment().getName() + " | "
				+ join(data.getMedicalUnitLeaderAssignment().getRadioChannel(),
						data.getMedicalUnitLeaderAssignment().getPhoneNumber()));
		people.add("Designated: " + plan.getDesignatedMedicalUnitRole() + " — "
				+ plan.getDesignatedMedicalUnitName() + " | " + plan.getDesignatedMedicalUnitContact());
		plan.getAidStations().forEach(station -> people.add("Aid station: " + station.getName() + " | "
				+ station.getLocation() + " | " + station.getContactNumbers() + " | " + station.getFrequency()));
		personnel.setText(String.join("\n", people));
		transport.setText(plan.getTransportServices().stream()
				.map(service -> service.getService() + " | " + service.getLocation() + " | "
						+ service.getContactNumbers() + " | " + service.getFrequency() + " | "
						+ service.getAirOrGround() + " | " + service.getLevelOfService())
				.collect(java.util.stream.Collectors.joining("\n")));
		facilityModel.clear();
		for (MedicalPlan.Hospital hospital : plan.getHospitals()) {
			String detail = hospital.getName() + "\n" + hospital.getAddress() + "\nContacts: "
					+ hospital.getContactNumbers() + "\nFrequency: " + hospital.getFrequency()
					+ "\nGround travel: " + hospital.getGroundTravelTime() + " ("
					+ hospital.getGroundTravelProvenance() + ")\nAir travel: " + hospital.getAirTravelTime() + " ("
					+ hospital.getAirTravelProvenance() + ")\nTrauma center: " + value(hospital.getTraumaCenter())
					+ " " + hospital.getTraumaLevel() + "\nBurn center: " + value(hospital.getBurnCenter())
					+ "\nHelipad: " + value(hospital.getHelipad());
			facilityModel.addElement(new FacilityChoice("[Accepted plan] " + hospital.getName(), detail));
		}
		selectFirstFacility();
	}

	/**
	 * Loads only canine-plan and veterinary-directory content.
	 *
	 * @param data active incident.
	 */
	private void refreshCanine(AppData data) {
		CanineMedicalPlan plan = data.getCanineMedicalPlan();
		instructions.setText(join(plan.getEmergencyProcedures(), "Poison control: " + plan.getPoisonControlContact(),
				plan.isAviationAssetsUsed() ? "Coordinate canine evacuation with Air Operations." : ""));
		List<String> people = new ArrayList<>();
		people.add("Medical Unit Leader: " + data.getMedicalUnitLeaderAssignment().getName() + " | "
				+ join(data.getMedicalUnitLeaderAssignment().getRadioChannel(),
						data.getMedicalUnitLeaderAssignment().getPhoneNumber()));
		people.add("Legacy veterinary text: " + plan.getOnsiteVeterinarian() + " " + plan.getVeterinarianDvm());
		for (String id : plan.getVeterinaryPersonnelResourceIds()) {
			TCard card = data.getTCards().stream().filter(candidate -> id.equals(candidate.peekResourceId()))
					.findFirst().orElse(null);
			if (card == null) {
				people.add("Linked veterinary personnel unavailable (missing resource " + id + ")");
			} else {
				people.add("Linked veterinary personnel: " + card.getPersonName() + " | recorded status "
						+ card.getStatus() + " | assignment " + assignmentFor(data, id) + " | recorded location "
						+ card.getLocation());
			}
		}
		people.add("Aid stations: " + plan.getAidStationInformation());
		personnel.setText(String.join("\n", people));
		String structuredTransport = plan.getTransportServiceEntries().stream()
				.map(service -> service.getService() + " | " + service.getLocation() + " | "
						+ service.getContactNumbers() + " | " + service.getFrequency() + " | "
						+ service.getAirOrGround() + " | " + service.getLevelOfService())
				.collect(java.util.stream.Collectors.joining("\n"));
		transport.setText(join(structuredTransport, plan.getTransportServices()));
		facilityModel.clear();
		List<VetFacility> accepted = new ArrayList<>(plan.getSelectedFacilities());
		accepted.sort(Comparator.comparingDouble(MedicalReferencePanel::sortableDistance));
		for (VetFacility facility : accepted) {
			facilityModel.addElement(facilityChoice(facility, "[Accepted plan]", data));
		}
		List<String> acceptedIds = accepted.stream().map(VetFacility::getId).toList();
		List<VetFacility> candidates = data.getVetDirectory().getFacilities().stream()
				.filter(facility -> !acceptedIds.contains(facility.getId()))
				.sorted(Comparator.comparingDouble(MedicalReferencePanel::sortableDistance)).toList();
		for (VetFacility facility : candidates) {
			facilityModel.addElement(facilityChoice(facility, "[Directory candidate]", data));
		}
		selectFirstFacility();
	}

	/**
	 * Builds a veterinary facility summary with route provenance and age.
	 *
	 * @param facility facility snapshot or directory candidate.
	 * @param source source label.
	 * @param data active incident.
	 * @return display choice.
	 */
	private FacilityChoice facilityChoice(VetFacility facility, String source, AppData data) {
		String distance = Double.isFinite(facility.getDistanceMeters())
				? String.format("%.1f mi", facility.getDistanceMeters() / 1609.344)
				: "unrouted";
		String duration = Double.isFinite(facility.getDurationSeconds())
				? Math.round(facility.getDurationSeconds() / 60d) + " min"
				: "duration unknown";
		String age = facility.getCalculatedAt() == null
				? "age unknown"
				: Duration.between(facility.getCalculatedAt(), Instant.now()).toHours() + " h old";
		String current = facility.hasCurrentDistance(data.getStagingLocation().getGeneration())
				? "current origin generation"
				: "stale/other origin";
		String origin = data.getStagingLocation().getLabel() + " — " + data.getStagingLocation().getAddress();
		String detail = source + "\n" + facility.getName() + "\n" + facility.getAddress() + ", "
				+ facility.getTown() + ", " + facility.getState() + " " + facility.getPostalCode()
				+ "\nContacts: " + String.join(", ", facility.getContactNumbers()) + "\nAvailability: "
				+ value(facility.getAvailable24Hours()) + " 24-hour; " + facility.getAvailabilityNotes()
				+ "\nCapabilities: " + facility.getCapabilities() + "\nVerified: " + facility.getVerifiedAt()
				+ "\nOrigin: " + origin + "\nRoute: " + distance + ", " + duration + " | "
				+ facility.getDistanceMethod() + " | " + facility.getDistanceProvenance() + " | " + age + " | "
				+ current;
		return new FacilityChoice(source + " " + facility.getName() + " — " + distance, detail);
	}

	/**
	 * Returns recorded assignment text for a linked personnel resource.
	 *
	 * @param data active incident.
	 * @param resourceId stable T-card identifier.
	 * @return recorded assignment, or "none recorded".
	 */
	private String assignmentFor(AppData data, String resourceId) {
		return data.getSarTaskAssignments().stream()
				.filter(task -> task.getResourcesAssigned().stream()
						.anyMatch(resource -> resourceId.equals(resource.getResourceId())))
				.map(SarTaskAssignment::getAssignment).filter(value -> value != null && !value.isBlank()).findFirst()
				.orElse("none recorded");
	}

	/**
	 * Refreshes optional task choices while preserving selection by assignment ID.
	 *
	 * @param data active incident.
	 */
	private void refreshTasks(AppData data) {
		TaskChoice selected = (TaskChoice) taskContext.getSelectedItem();
		String selectedId = selected == null ? "" : selected.assignmentId();
		taskContext.removeAllItems();
		taskContext.addItem(new TaskChoice(null));
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			TaskChoice choice = new TaskChoice(task);
			taskContext.addItem(choice);
			if (selectedId.equals(choice.assignmentId())) {
				taskContext.setSelectedItem(choice);
			}
		}
		refreshTaskDetail();
	}

	/** Updates optional task detail without changing reference type. */
	private void refreshTaskDetail() {
		TaskChoice choice = (TaskChoice) taskContext.getSelectedItem();
		SarTaskAssignment task = choice == null ? null : choice.task();
		if (task == null) {
			taskDetail.setText("No task context selected.");
			return;
		}
		String communications = task.getCommunications().stream()
				.map(entry -> entry.getName() + ": " + entry.getPrimaryContact())
				.collect(java.util.stream.Collectors.joining(", "));
		taskDetail.setText("Team: " + task.getAssignmentTeamNumber() + "\nAssignment: " + task.getAssignment()
				+ "\nLeader/resources: " + SarTaskSupport.taskResourceDisplayLabel(task) + " / "
				+ task.getResourcesAssigned().size() + " recorded resource(s)\nCommunications: " + communications);
	}

	/** Requests the latest PDF for this reference type without altering review. */
	private void requestPreview() {
		PlanReviewArea area = canine ? PlanReviewArea.CANINE_MEDICAL : PlanReviewArea.HUMAN_MEDICAL;
		preview.requestPreview(controller.getData(), PlanReviewSupport.contentDigest(controller.getData(), area));
	}

	/** Runs the explicit, confirmed medical-event logging operation. */
	private void logMedicalEvent() {
		JTextArea note = new JTextArea(3, 32);
		note.getAccessibleContext().setAccessibleName("Brief medical event operational note");
		note.setLineWrap(true);
		note.setWrapStyleWord(true);
		JTextField location = new JTextField(32);
		location.getAccessibleContext().setAccessibleName("Optional medical event location");
		JComboBox<TaskChoice> eventTask = new JComboBox<>();
		eventTask.getAccessibleContext().setAccessibleName("Optional medical event task context");
		for (int index = 0; index < taskContext.getItemCount(); index++) {
			eventTask.addItem(taskContext.getItemAt(index));
		}
		eventTask.setSelectedItem(taskContext.getSelectedItem());
		JPanel fields = new JPanel(new GridLayout(0, 1, 4, 4));
		fields.add(new JLabel("Brief operational note; omit unnecessary clinical details:"));
		fields.add(new JScrollPane(note));
		fields.add(new JLabel("Optional task context:"));
		fields.add(eventTask);
		fields.add(new JLabel("Optional location context:"));
		fields.add(location);
		if (JOptionPane.showConfirmDialog(this, fields,
				canine ? "Response canine medical event" : "Responder medical event",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION
				|| note.getText().isBlank() || !controller.isCurrent(binding)) {
			return;
		}
		String category = canine ? "Response canine medical event" : "Responder medical event";
		if (JOptionPane.showConfirmDialog(this, "Create one \"" + category + "\" entry in the ICP log?",
				"Confirm medical event log", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		TaskChoice choice = (TaskChoice) eventTask.getSelectedItem();
		recordMedicalEvent(note.getText(), choice == null ? "" : choice.assignmentId(), location.getText());
	}

	/**
	 * Records a confirmed event with optional task and location context.
	 *
	 * @param note minimal operational note.
	 * @param assignmentId optional task identifier.
	 * @param location optional operator-entered location.
	 * @throws IllegalArgumentException when the operational note is blank.
	 */
	void recordMedicalEvent(String note, String assignmentId, String location) {
		if (controller.isCurrent(binding)) {
			if (note == null || note.isBlank()) {
				throw new IllegalArgumentException("A brief event note is required.");
			}
			String taskNote = assignmentId == null || assignmentId.isBlank() ? ""
					: controller.getData().getSarTaskAssignments().stream()
							.filter(task -> assignmentId.equals(task.getAssignmentId()))
							.map(task -> "\nTask: " + task.getTaskId() + " / " + task.getAssignmentTeamNumber()
									+ " — " + task.getAssignment())
							.findFirst().orElse("\nTask assignment: " + assignmentId);
			String context = location == null || location.isBlank() ? "" : "\nLocation: " + location.trim();
			controller.recordMedicalEvent(canine, note + taskNote + context, assignmentId);
		}
	}

	/**
	 * Returns the content-bound completion and approval indicator.
	 *
	 * @return current indicator text.
	 */
	String completionText() {
		return completion.getText();
	}

	/** Selects the first facility without choosing it operationally. */
	private void selectFirstFacility() {
		if (!facilityModel.isEmpty()) {
			facilities.setSelectedIndex(0);
		} else {
			facilityDetail.setText("No facilities recorded for this reference type.");
		}
	}

	/**
	 * Returns a sort key that leaves unrouted facilities visible after routed ones.
	 *
	 * @param facility facility.
	 * @return finite distance or positive infinity.
	 */
	private static double sortableDistance(VetFacility facility) {
		return Double.isFinite(facility.getDistanceMeters()) ? facility.getDistanceMeters() : Double.POSITIVE_INFINITY;
	}

	/** @return configured read-only wrapping text area. */
	private static JTextArea readOnlyArea() {
		JTextArea area = new JTextArea();
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		return area;
	}

	/**
	 * Joins nonblank content without fallback across reference types.
	 *
	 * @param values values from one plan only.
	 * @return newline-delimited content.
	 */
	private static String join(String... values) {
		return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank())
				.collect(java.util.stream.Collectors.joining("\n"));
	}

	/**
	 * Formats a tri-state value.
	 *
	 * @param value value.
	 * @return yes, no, or unknown.
	 */
	private static String value(Boolean value) {
		return value == null ? "Unknown" : value ? "Yes" : "No";
	}

	/**
	 * Escapes banner text.
	 *
	 * @param value text.
	 * @return basic HTML-safe text.
	 */
	private static String html(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Immutable facility display row.
	 *
	 * @param label list label.
	 * @param detail selected detail.
	 */
	private record FacilityChoice(String label, String detail) {
		/** @return list label. */
		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * Optional task-context choice.
	 *
	 * @param task task, or null for no context.
	 */
	private record TaskChoice(SarTaskAssignment task) {
		/** @return stable assignment identifier, or blank. */
		private String assignmentId() {
			return task == null ? "" : task.getAssignmentId();
		}

		/** @return operator-facing task label. */
		@Override
		public String toString() {
			return task == null ? "No task context" : task.getAssignmentTeamNumber() + " — " + task.getAssignment();
		}
	}
}
