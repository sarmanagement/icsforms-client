/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Communication row for ICS 204 or linked SAR task context.
 */
public class CommunicationEntry {
	private String name = "";
	private String function = "";
	private String primaryContact = "";
	private String radioChannelId = "";

	/** Creates an unlinked communications row. */
	public CommunicationEntry() {
	}

	/**
	 * Restores the channel label and optional identity together, independently of
	 * JSON property order.
	 * @param primaryContact persisted fallback contact; null becomes empty
	 * @param radioChannelId persisted channel identity; null becomes empty
	 */
	@JsonCreator
	public CommunicationEntry(@JsonProperty("primaryContact") String primaryContact,
			@JsonProperty("radioChannelId") String radioChannelId) {
		this.primaryContact = primaryContact == null ? "" : primaryContact;
		setRadioChannelId(radioChannelId);
	}

	/**
	 * Returns the contact name.
	 *
	 * @return contact name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the contact name.
	 *
	 * @param name
	 *            contact name.
	 */
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	/**
	 * Returns the function/role label.
	 *
	 * @return function or role.
	 */
	public String getFunction() {
		return function;
	}

	/**
	 * Sets the function/role label.
	 *
	 * @param function
	 *            function or role.
	 */
	public void setFunction(String function) {
		this.function = function == null ? "" : function;
	}

	/**
	 * Returns the combined name/function label used by legacy callers.
	 *
	 * @return name or function.
	 */
	public String getNameOrFunction() {
		if (!name.isBlank() && !function.isBlank()) {
			return name + " / " + function;
		}
		return !name.isBlank() ? name : function;
	}

	/**
	 * Sets the legacy combined name/function label.
	 *
	 * @param nameOrFunction
	 *            name or function.
	 */
	public void setNameOrFunction(String nameOrFunction) {
		String value = nameOrFunction == null ? "" : nameOrFunction.trim();
		if (value.contains("/")) {
			String[] parts = value.split("/", 2);
			setName(parts[0].trim());
			setFunction(parts[1].trim());
		} else {
			setName(value);
			setFunction("");
		}
	}

	/**
	 * Returns the primary contact channel.
	 *
	 * @return primary contact.
	 */
	public String getPrimaryContact() {
		return primaryContact;
	}

	/**
	 * Sets the primary contact channel. A changed label clears its optional plan
	 * reference; assigning the identical label preserves that reference.
	 *
	 * @param primaryContact
	 *            primary contact; null becomes empty.
	 */
	public void setPrimaryContact(String primaryContact) {
		String value = primaryContact == null ? "" : primaryContact;
		if (!this.primaryContact.equals(value)) {
			radioChannelId = "";
		}
		this.primaryContact = value;
	}

	/**
	 * Returns the optional stable ICS 205 channel reference.
	 * @return channel identity, or empty when the contact is free text
	 */
	public String getRadioChannelId() {
		return radioChannelId;
	}

	/**
	 * Sets an optional channel reference without altering the fallback label.
	 * Assign the label before the identity when selecting a different channel.
	 * @param radioChannelId stable channel identity; null becomes empty
	 */
	public void setRadioChannelId(String radioChannelId) {
		this.radioChannelId = radioChannelId == null ? "" : radioChannelId;
	}
}
