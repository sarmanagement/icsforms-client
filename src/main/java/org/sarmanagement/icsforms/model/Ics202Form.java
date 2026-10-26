/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * First-cut Incident Objectives (ICS 202) content captured by the desktop
 * editor.
 */
public class Ics202Form {
	private List<String> objectives = new ArrayList<>();
	private List<IncidentObjective> structuredObjectives = new ArrayList<>();
	private String commandEmphasis = "";
	private String situationalAwareness = "";
	private boolean siteSafetyPlanRequired;
	private List<String> incidentActionPlanAttachments = new ArrayList<>();
	private String preparedByName = "";
	private String preparedByPositionTitle = "";
	private String preparedBySignature = "";
	private String approvedByIncidentCommanderName = "";
	private String approvedBySignature = "";
	private LocalDateTime approvedDateTime;
	private String approvedObjectivesDigest = "";
	private String formNumber = "ICS 202";
	private String iapPage = "";

	/**
	 * Returns the incident objectives.
	 *
	 * @return objectives list.
	 */
	public List<String> getObjectives() {
		normalizeObjectives();
		return objectives;
	}

	/**
	 * Sets the incident objectives.
	 *
	 * @param objectives
	 *            objectives list.
	 */
	public void setObjectives(List<String> objectives) {
		List<String> values = objectives == null ? new ArrayList<>() : new ArrayList<>(objectives);
		normalizeObjectives();
		for (int index = 0; index < values.size(); index++) {
			if (index < structuredObjectives.size()) {
				structuredObjectives.get(index).setText(values.get(index));
			} else {
				structuredObjectives.add(new IncidentObjective(values.get(index), index));
			}
		}
		while (structuredObjectives.size() > values.size()) {
			structuredObjectives.remove(structuredObjectives.size() - 1);
		}
		normalizeObjectiveOrder();
	}

	/**
	 * Returns stable objective entities, migrating legacy objective strings without
	 * guessing assignment links.
	 *
	 * @return mutable ordered objective list.
	 */
	public List<IncidentObjective> getStructuredObjectives() {
		normalizeObjectives();
		return structuredObjectives;
	}

	/**
	 * Replaces stable objective entities and normalizes their display order.
	 *
	 * @param structuredObjectives objective entities; null becomes empty.
	 */
	public void setStructuredObjectives(List<IncidentObjective> structuredObjectives) {
		this.structuredObjectives = structuredObjectives == null ? new ArrayList<>() : structuredObjectives;
		normalizeObjectiveOrder();
	}

	/**
	 * Migrates legacy strings and keeps the legacy PDF projection synchronized.
	 */
	public void normalizeObjectives() {
		if (structuredObjectives == null) {
			structuredObjectives = new ArrayList<>();
		}
		if (structuredObjectives.isEmpty() && objectives != null) {
			for (int index = 0; index < objectives.size(); index++) {
				structuredObjectives.add(new IncidentObjective(objectives.get(index), index));
			}
		}
		normalizeObjectiveOrder();
	}

	/**
	 * Applies list order and rebuilds the backward-compatible text projection.
	 */
	private void normalizeObjectiveOrder() {
		if (structuredObjectives == null) {
			structuredObjectives = new ArrayList<>();
		}
		objectives = new ArrayList<>();
		for (int index = 0; index < structuredObjectives.size(); index++) {
			IncidentObjective objective = structuredObjectives.get(index);
			if (objective == null) {
				objective = new IncidentObjective("", index);
				structuredObjectives.set(index, objective);
			}
			objective.getObjectiveId();
			objective.setDisplayOrder(index);
			objectives.add(objective.getText());
		}
	}

	/**
	 * Returns command emphasis text.
	 *
	 * @return command emphasis.
	 */
	public String getCommandEmphasis() {
		return commandEmphasis;
	}

	/**
	 * Sets command emphasis text.
	 *
	 * @param commandEmphasis
	 *            command emphasis.
	 */
	public void setCommandEmphasis(String commandEmphasis) {
		this.commandEmphasis = commandEmphasis;
	}

	/**
	 * Returns situational awareness text.
	 *
	 * @return situational awareness.
	 */
	public String getSituationalAwareness() {
		return situationalAwareness;
	}

	/**
	 * Sets situational awareness text.
	 *
	 * @param situationalAwareness
	 *            situational awareness.
	 */
	public void setSituationalAwareness(String situationalAwareness) {
		this.situationalAwareness = situationalAwareness;
	}

	/**
	 * Returns whether a site safety plan is required.
	 *
	 * @return {@code true} when required.
	 */
	public boolean isSiteSafetyPlanRequired() {
		return siteSafetyPlanRequired;
	}

	/**
	 * Sets whether a site safety plan is required.
	 *
	 * @param siteSafetyPlanRequired
	 *            site safety plan flag.
	 */
	public void setSiteSafetyPlanRequired(boolean siteSafetyPlanRequired) {
		this.siteSafetyPlanRequired = siteSafetyPlanRequired;
	}

	/**
	 * Returns IAP attachment labels or notes.
	 *
	 * @return attachment list.
	 */
	public List<String> getIncidentActionPlanAttachments() {
		return incidentActionPlanAttachments;
	}

	/**
	 * Sets IAP attachment labels or notes.
	 *
	 * @param incidentActionPlanAttachments
	 *            attachment list.
	 */
	public void setIncidentActionPlanAttachments(List<String> incidentActionPlanAttachments) {
		this.incidentActionPlanAttachments = incidentActionPlanAttachments == null
				? new ArrayList<>()
				: incidentActionPlanAttachments;
	}

	/**
	 * Returns the preparer name.
	 *
	 * @return preparer name.
	 */
	public String getPreparedByName() {
		return preparedByName;
	}

	/**
	 * Sets the preparer name.
	 *
	 * @param preparedByName
	 *            preparer name.
	 */
	public void setPreparedByName(String preparedByName) {
		this.preparedByName = preparedByName;
	}

	/**
	 * Returns the preparer position/title.
	 *
	 * @return preparer title.
	 */
	public String getPreparedByPositionTitle() {
		return preparedByPositionTitle;
	}

	/**
	 * Sets the preparer position/title.
	 *
	 * @param preparedByPositionTitle
	 *            preparer title.
	 */
	public void setPreparedByPositionTitle(String preparedByPositionTitle) {
		this.preparedByPositionTitle = preparedByPositionTitle;
	}

	/**
	 * Returns the preparer signature text.
	 *
	 * @return preparer signature.
	 */
	public String getPreparedBySignature() {
		return preparedBySignature;
	}

	/**
	 * Sets the preparer signature text.
	 *
	 * @param preparedBySignature
	 *            preparer signature.
	 */
	public void setPreparedBySignature(String preparedBySignature) {
		this.preparedBySignature = preparedBySignature;
	}

	/**
	 * Returns the incident commander approver name.
	 *
	 * @return approver name.
	 */
	public String getApprovedByIncidentCommanderName() {
		return approvedByIncidentCommanderName;
	}

	/**
	 * Sets the incident commander approver name.
	 *
	 * @param approvedByIncidentCommanderName
	 *            approver name.
	 */
	public void setApprovedByIncidentCommanderName(String approvedByIncidentCommanderName) {
		this.approvedByIncidentCommanderName = approvedByIncidentCommanderName;
	}

	/**
	 * Returns the incident commander signature text.
	 *
	 * @return signature text.
	 */
	public String getApprovedBySignature() {
		return approvedBySignature;
	}

	/**
	 * Sets the incident commander signature text.
	 *
	 * @param approvedBySignature
	 *            signature text.
	 */
	public void setApprovedBySignature(String approvedBySignature) {
		this.approvedBySignature = approvedBySignature;
	}

	/**
	 * Returns approval date/time.
	 *
	 * @return approval time.
	 */
	public LocalDateTime getApprovedDateTime() {
		return approvedDateTime;
	}

	/**
	 * Sets approval date/time.
	 *
	 * @param approvedDateTime
	 *            approval time.
	 */
	public void setApprovedDateTime(LocalDateTime approvedDateTime) {
		this.approvedDateTime = approvedDateTime;
	}

	/**
	 * Returns the content digest captured with the objective approval.
	 *
	 * @return SHA-256 digest, or blank for untracked legacy approval.
	 */
	public String getApprovedObjectivesDigest() {
		return approvedObjectivesDigest == null ? "" : approvedObjectivesDigest;
	}

	/**
	 * Sets the content digest captured with the objective approval.
	 *
	 * @param approvedObjectivesDigest SHA-256 digest; null becomes blank.
	 */
	public void setApprovedObjectivesDigest(String approvedObjectivesDigest) {
		this.approvedObjectivesDigest = approvedObjectivesDigest == null ? "" : approvedObjectivesDigest;
	}

	/**
	 * Records an explicit Incident Commander approval of the current ordered
	 * objectives.
	 *
	 * @param approver Incident Commander name.
	 * @param signature typed signature, which may be blank.
	 * @param approvedAt approval time.
	 * @throws IllegalArgumentException when approver, time, or substantive
	 *             objectives are absent.
	 */
	public void recordObjectiveApproval(String approver, String signature, LocalDateTime approvedAt) {
		if (approver == null || approver.isBlank()) {
			throw new IllegalArgumentException("Enter the approving Incident Commander.");
		}
		if (approvedAt == null) {
			throw new IllegalArgumentException("Enter an objective approval time.");
		}
		if (getStructuredObjectives().stream().noneMatch(objective -> !objective.getText().isBlank())) {
			throw new IllegalArgumentException("Enter at least one objective before approval.");
		}
		setApprovedByIncidentCommanderName(approver.trim());
		setApprovedBySignature(signature);
		setApprovedDateTime(approvedAt);
		setApprovedObjectivesDigest(currentObjectivesDigest());
	}

	/**
	 * Returns whether the approval matches current objective wording and order.
	 *
	 * @return objective approval status.
	 */
	@JsonIgnore
	public ObjectiveApprovalStatus getObjectiveApprovalStatus() {
		boolean approvalRecorded = approvedDateTime != null
				|| (approvedByIncidentCommanderName != null && !approvedByIncidentCommanderName.isBlank())
				|| (approvedBySignature != null && !approvedBySignature.isBlank());
		if (!approvalRecorded) {
			return ObjectiveApprovalStatus.NOT_APPROVED;
		}
		if (getApprovedObjectivesDigest().isBlank()) {
			return ObjectiveApprovalStatus.LEGACY_UNVERIFIED;
		}
		return getApprovedObjectivesDigest().equals(currentObjectivesDigest())
				? ObjectiveApprovalStatus.APPROVED_CURRENT
				: ObjectiveApprovalStatus.CONTENT_CHANGED;
	}

	/**
	 * Computes the stable digest of ordered objective identities and wording.
	 *
	 * @return lowercase SHA-256 hexadecimal digest.
	 */
	private String currentObjectivesDigest() {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (IncidentObjective objective : getStructuredObjectives()) {
				updateDigest(digest, objective.getObjectiveId());
				updateDigest(digest, objective.getText());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	/**
	 * Adds one length-delimited value to an objective digest.
	 *
	 * @param digest digest receiving the value.
	 * @param value value to add.
	 */
	private static void updateDigest(MessageDigest digest, String value) {
		byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
		digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}

	/**
	 * Returns the form number label.
	 *
	 * @return form number label.
	 */
	public String getFormNumber() {
		return formNumber;
	}

	/**
	 * Sets the form number label.
	 *
	 * @param formNumber
	 *            form number label.
	 */
	public void setFormNumber(String formNumber) {
		this.formNumber = formNumber;
	}

	/**
	 * Returns the IAP page indicator.
	 *
	 * @return IAP page value.
	 */
	public String getIapPage() {
		return iapPage;
	}

	/**
	 * Sets the IAP page indicator.
	 *
	 * @param iapPage
	 *            IAP page value.
	 */
	public void setIapPage(String iapPage) {
		this.iapPage = iapPage;
	}
}
