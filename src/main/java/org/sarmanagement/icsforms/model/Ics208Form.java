/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Editable ICS 208 Safety Message/Plan, including groundwork for ICS 208 HM.
 */
public class Ics208Form {
	/** ICS 208 message formats supported by safety workflows. */
	public enum MessageType {
		SAFETY_MESSAGE_PLAN,
		HAZARDOUS_MATERIALS_SITE_SAFETY_PLAN
	}

	/** Identifies the primary authoring role for future role-based views. */
	public enum AuthorRole {
		SAFETY_OFFICER,
		HAZARDOUS_MATERIALS_GROUP_SUPERVISOR
	}

	/** Selects free-text or structured hazard-and-precautions message content. */
	public enum MessageMode {
		FREE_TEXT,
		HAZARD_PRECAUTIONS
	}

	private MessageType messageType = MessageType.SAFETY_MESSAGE_PLAN;
	private AuthorRole authorRole = AuthorRole.SAFETY_OFFICER;
	private MessageMode messageMode = MessageMode.HAZARD_PRECAUTIONS;
	private String freeText = "";
	private String generalRequirements = "";
	private List<SafetyMessageBlock> hazardBlocks = new ArrayList<>();
	private Boolean siteSafetyPlanRequired;
	private SiteSafetyPlan siteSafetyPlan = new SiteSafetyPlan();
	private String preparedByName = "";
	private String preparedByPositionTitle = "";
	private String preparedBySignature = "";
	private LocalDateTime preparedDateTime;
	private boolean includeInIap;
	private String iapPage = "";

	/**
	 * Returns the ICS 208 document type.
	 *
	 * @return document type.
	 */
	public MessageType getMessageType() {
		return messageType == null ? MessageType.SAFETY_MESSAGE_PLAN : messageType;
	}

	/**
	 * Sets the ICS 208 document type.
	 *
	 * @param messageType document type; null selects the standard safety plan.
	 */
	public void setMessageType(MessageType messageType) {
		this.messageType = messageType == null ? MessageType.SAFETY_MESSAGE_PLAN : messageType;
	}

	/**
	 * Returns the primary authoring role.
	 *
	 * @return author role.
	 */
	public AuthorRole getAuthorRole() {
		return authorRole == null ? AuthorRole.SAFETY_OFFICER : authorRole;
	}

	/**
	 * Sets the primary authoring role.
	 *
	 * @param authorRole author role; null selects Safety Officer.
	 */
	public void setAuthorRole(AuthorRole authorRole) {
		this.authorRole = authorRole == null ? AuthorRole.SAFETY_OFFICER : authorRole;
	}

	/**
	 * Returns the message editing mode.
	 *
	 * @return editing mode.
	 */
	public MessageMode getMessageMode() {
		return messageMode == null ? MessageMode.HAZARD_PRECAUTIONS : messageMode;
	}

	/**
	 * Sets the message editing mode.
	 *
	 * @param messageMode editing mode; null selects structured hazards.
	 */
	public void setMessageMode(MessageMode messageMode) {
		this.messageMode = messageMode == null ? MessageMode.HAZARD_PRECAUTIONS : messageMode;
	}

	/**
	 * Returns free-text message content.
	 *
	 * @return free-text content.
	 */
	public String getFreeText() {
		return safe(freeText);
	}

	/**
	 * Sets free-text message content.
	 *
	 * @param freeText message content.
	 */
	public void setFreeText(String freeText) {
		this.freeText = safe(freeText);
	}

	/**
	 * Returns general requirements applying across hazard blocks.
	 *
	 * @return general requirements.
	 */
	public String getGeneralRequirements() {
		return safe(generalRequirements);
	}

	/**
	 * Sets general requirements.
	 *
	 * @param generalRequirements general requirements.
	 */
	public void setGeneralRequirements(String generalRequirements) {
		this.generalRequirements = safe(generalRequirements);
	}

	/**
	 * Returns the mutable hazard blocks.
	 *
	 * @return hazard blocks, never null.
	 */
	public List<SafetyMessageBlock> getHazardBlocks() {
		if (hazardBlocks == null) {
			hazardBlocks = new ArrayList<>();
		}
		return hazardBlocks;
	}

	/**
	 * Replaces the hazard blocks.
	 *
	 * @param hazardBlocks hazard blocks; null becomes empty.
	 */
	public void setHazardBlocks(List<SafetyMessageBlock> hazardBlocks) {
		this.hazardBlocks = hazardBlocks == null ? new ArrayList<>() : hazardBlocks;
	}

	/**
	 * Returns whether a site safety plan is required, or null if unanswered.
	 *
	 * @return requirement answer.
	 */
	public Boolean getSiteSafetyPlanRequired() {
		return siteSafetyPlanRequired;
	}

	/**
	 * Sets whether a site safety plan is required.
	 *
	 * @param siteSafetyPlanRequired requirement answer; null remains unanswered.
	 */
	public void setSiteSafetyPlanRequired(Boolean siteSafetyPlanRequired) {
		this.siteSafetyPlanRequired = siteSafetyPlanRequired;
	}

	/**
	 * Returns site-plan details.
	 *
	 * @return site-plan metadata and sections.
	 */
	public SiteSafetyPlan getSiteSafetyPlan() {
		if (siteSafetyPlan == null) {
			siteSafetyPlan = new SiteSafetyPlan();
		}
		return siteSafetyPlan;
	}

	/**
	 * Sets site-plan details.
	 *
	 * @param siteSafetyPlan site-plan metadata; null becomes empty.
	 */
	public void setSiteSafetyPlan(SiteSafetyPlan siteSafetyPlan) {
		this.siteSafetyPlan = siteSafetyPlan == null ? new SiteSafetyPlan() : siteSafetyPlan;
	}

	/**
	 * Returns the preparer's name.
	 *
	 * @return preparer name.
	 */
	public String getPreparedByName() {
		return safe(preparedByName);
	}

	/**
	 * Sets the preparer's name.
	 *
	 * @param preparedByName preparer name.
	 */
	public void setPreparedByName(String preparedByName) {
		this.preparedByName = safe(preparedByName);
	}

	/**
	 * Returns the preparer's position.
	 *
	 * @return preparer position.
	 */
	public String getPreparedByPositionTitle() {
		return safe(preparedByPositionTitle);
	}

	/**
	 * Sets the preparer's position.
	 *
	 * @param preparedByPositionTitle preparer position.
	 */
	public void setPreparedByPositionTitle(String preparedByPositionTitle) {
		this.preparedByPositionTitle = safe(preparedByPositionTitle);
	}

	/**
	 * Returns the preparer's signature.
	 *
	 * @return preparer signature.
	 */
	public String getPreparedBySignature() {
		return safe(preparedBySignature);
	}

	/**
	 * Sets the preparer's signature.
	 *
	 * @param preparedBySignature preparer signature.
	 */
	public void setPreparedBySignature(String preparedBySignature) {
		this.preparedBySignature = safe(preparedBySignature);
	}

	/**
	 * Returns the preparation date/time.
	 *
	 * @return preparation date/time.
	 */
	public LocalDateTime getPreparedDateTime() {
		return preparedDateTime;
	}

	/**
	 * Sets the preparation date/time.
	 *
	 * @param preparedDateTime preparation date/time.
	 */
	public void setPreparedDateTime(LocalDateTime preparedDateTime) {
		this.preparedDateTime = preparedDateTime;
	}

	/**
	 * Returns whether ICS 208 is selected for the IAP bundle.
	 *
	 * @return true when included.
	 */
	public boolean isIncludeInIap() {
		return includeInIap;
	}

	/**
	 * Sets whether ICS 208 is selected for the IAP bundle.
	 *
	 * @param includeInIap true to include.
	 */
	public void setIncludeInIap(boolean includeInIap) {
		this.includeInIap = includeInIap;
	}

	/**
	 * Returns the assigned IAP page label.
	 *
	 * @return IAP page label.
	 */
	public String getIapPage() {
		return safe(iapPage);
	}

	/**
	 * Sets the assigned IAP page label.
	 *
	 * @param iapPage IAP page label.
	 */
	public void setIapPage(String iapPage) {
		this.iapPage = safe(iapPage);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
