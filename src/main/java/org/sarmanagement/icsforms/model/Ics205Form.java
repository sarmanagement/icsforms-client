/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persisted ICS 205 communications plan and its complete radio-channel inventory.
 * Incident and operational-period context are supplied by the owning document.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ics205Form implements Identified {
	@JsonProperty("id")
	@JsonAlias("formId")
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
	@JsonIgnore
	public String getFormId() {
		return formId;
	}

	/** {@inheritDoc} */
	@Override
	public String getId() {
		return formId;
	}

	/** {@inheritDoc} */
	@Override
	public void setId(String id) {
		formId = id;
	}

	/**
	 * Sets the form identity for deserialization or persistence migration.
	 * @param formId identity, or null until legacy migration assigns one
	 */
	@JsonIgnore
	public void setFormId(String formId) {
		this.formId = formId;
	}

	/**
	 * Returns all inventory channels, including those excluded from form output.
	 * @return mutable ordered channel inventory, never null
	 */
	public List<RadioChannel> getChannels() {
		return channels;
	}

	/**
	 * Replaces the channel list, omitting null rows.
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
	 * Removes null channel rows. Identity migration repairs missing or duplicate IDs at document load.
	 */
	public void normalizeIdentity() {
		List<RadioChannel> normalized = new ArrayList<>();
		for (RadioChannel channel : channels) {
			if (channel == null) {
				continue;
			}
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
	 * Carries inventory and instructions to a new form without carrying approval or
	 * period-specific radio-check assertions. The copied form and channels receive
	 * new identities.
	 *
	 * @return independent new-period form with blank dates, signature, page, and
	 *         radio checks.
	 */
	public Ics205Form copyForNewPeriod() {
		Ics205Form copy = deepCopy();
		copy.setId(UUID.randomUUID().toString());
		for (RadioChannel channel : copy.getChannels()) {
			channel.setId(UUID.randomUUID().toString());
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
