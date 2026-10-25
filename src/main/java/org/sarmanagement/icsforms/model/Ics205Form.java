/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persisted ICS 205 communications plan and its complete radio-channel inventory.
 * Incident and operational-period context are supplied by the owning document.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ics205Form {
	private String formId = UUID.randomUUID().toString();
	private List<RadioChannel> channels = new ArrayList<>();
	private String specialInstructions = "";
	private String preparedByName = "";
	private String preparedBySignature = "";
	private LocalDateTime datePrepared;
	private LocalDateTime preparedDateTime;
	private String iapPage = "";

	/**
	 * Returns the form identity.
	 * @return stable form UUID string
	 */
	public String getFormId() {
		return formId;
	}

	/**
	 * Sets the form identity.
	 * @param formId identity; null or blank generates a UUID
	 */
	public void setFormId(String formId) {
		this.formId = formId == null || formId.isBlank() ? UUID.randomUUID().toString() : formId;
	}

	/**
	 * Returns all inventory channels, including those excluded from form output.
	 * @return mutable ordered channel inventory, never null
	 */
	public List<RadioChannel> getChannels() {
		return channels;
	}

	/**
	 * Replaces the channel list, omitting null rows and repairing duplicate IDs.
	 * @param channels channel inventory; null becomes empty
	 */
	public void setChannels(List<RadioChannel> channels) {
		this.channels = new ArrayList<>();
		if (channels != null) {
			for (RadioChannel channel : channels) {
				if (channel != null) {
					this.channels.add(channel);
				}
			}
		}
		normalizeIdentity();
	}

	/**
	 * Returns the special communications instructions.
	 * @return instructions
	 */
	public String getSpecialInstructions() {
		return specialInstructions;
	}

	/**
	 * Sets the special communications instructions.
	 * @param specialInstructions instructions; null becomes empty
	 */
	public void setSpecialInstructions(String specialInstructions) {
		this.specialInstructions = specialInstructions == null ? "" : specialInstructions;
	}

	/**
	 * Returns the preparer's entered name, not a signature.
	 * @return preparer name
	 */
	public String getPreparedByName() {
		return preparedByName;
	}

	/**
	 * Sets the preparer's entered name without synthesizing a signature.
	 * @param preparedByName preparer name; null becomes empty
	 */
	public void setPreparedByName(String preparedByName) {
		this.preparedByName = preparedByName == null ? "" : preparedByName;
	}

	/**
	 * Returns the explicitly entered preparer signature.
	 * @return signature, empty when unsigned
	 */
	public String getPreparedBySignature() {
		return preparedBySignature;
	}

	/**
	 * Sets an explicitly entered signature.
	 * @param preparedBySignature signature; null becomes empty
	 */
	public void setPreparedBySignature(String preparedBySignature) {
		this.preparedBySignature = preparedBySignature == null ? "" : preparedBySignature;
	}

	/**
	 * Returns the date prepared shown in the form header.
	 * @return entered date/time, or null when unset
	 */
	public LocalDateTime getDatePrepared() {
		return datePrepared;
	}

	/**
	 * Sets the form-header preparation date.
	 * @param datePrepared entered date/time, or null to clear
	 */
	public void setDatePrepared(LocalDateTime datePrepared) {
		this.datePrepared = datePrepared;
	}

	/**
	 * Returns the preparation date/time accompanying the signature block.
	 * @return entered date/time, or null when unset
	 */
	public LocalDateTime getPreparedDateTime() {
		return preparedDateTime;
	}

	/**
	 * Sets the preparation date/time accompanying the signature block.
	 * @param preparedDateTime entered date/time, or null to clear
	 */
	public void setPreparedDateTime(LocalDateTime preparedDateTime) {
		this.preparedDateTime = preparedDateTime;
	}

	/**
	 * Returns the assigned IAP page.
	 * @return page label
	 */
	public String getIapPage() {
		return iapPage;
	}

	/**
	 * Sets the assigned IAP page.
	 * @param iapPage page label; null becomes empty
	 */
	public void setIapPage(String iapPage) {
		this.iapPage = iapPage == null ? "" : iapPage;
	}

	/**
	 * Repairs missing or duplicate channel identities within this plan. Identical
	 * channel IDs in different period snapshots deliberately remain unchanged.
	 */
	public void normalizeIdentity() {
		setFormId(formId);
		Set<String> ids = new HashSet<>();
		List<RadioChannel> normalized = new ArrayList<>();
		for (RadioChannel channel : channels) {
			if (channel == null) {
				continue;
			}
			if (channel.getChannelId() == null || channel.getChannelId().isBlank()
					|| ids.contains(channel.getChannelId())) {
				channel = channel.deepCopy();
				channel.setChannelId(null);
			}
			ids.add(channel.getChannelId());
			normalized.add(channel);
		}
		channels = normalized;
	}

	/**
	 * Copies this form, including nested inventory and programming metadata.
	 * @return fully independent copy retaining form and channel identities
	 */
	public Ics205Form deepCopy() {
		Ics205Form copy = new Ics205Form();
		copy.formId = formId;
		for (RadioChannel channel : channels) {
			if (channel != null) {
				copy.channels.add(channel.deepCopy());
			}
		}
		copy.specialInstructions = specialInstructions;
		copy.preparedByName = preparedByName;
		copy.preparedBySignature = preparedBySignature;
		copy.datePrepared = datePrepared;
		copy.preparedDateTime = preparedDateTime;
		copy.iapPage = iapPage;
		return copy;
	}

	/**
	 * Carries inventory and instructions to a new form without carrying approval.
	 * Stable channel identities survive so external links continue to resolve.
	 * @return independent new-period form with blank dates, signature, and page
	 */
	public Ics205Form copyForNewPeriod() {
		Ics205Form copy = deepCopy();
		copy.setFormId(null);
		for (RadioChannel channel : copy.getChannels()) {
			channel.setRadioCheckStatus(RadioCheckStatus.NOT_RECORDED);
			channel.setRadioCheckedBy("");
			channel.setRadioCheckedAt(null);
			channel.setRadioCheckNotes("");
		}
		copy.datePrepared = null;
		copy.preparedDateTime = null;
		copy.preparedBySignature = "";
		copy.iapPage = "";
		return copy;
	}
}
