/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * A single user-defined position entry in the organizational chart.
 *
 * <p>
 * These entries are stored in
 * {@link OrganizationalChart#getAdditionalPositions()} and represent roles that
 * the user adds via the "Add position" dialog on the Org Chart tab.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrgChartEntry {
	/** Chart section this position belongs to. */
	public enum Section {
		COMMAND_STAFF, OPERATIONS, PLANNING, LOGISTICS, FINANCE_ADMIN
	}

	private Section section = Section.OPERATIONS;
	private String id = UUID.randomUUID().toString();
	private String title = "";
	private String name = "";
	private String radio = "";
	private String phone = "";

	/**
	 * Returns this organizational position's stable identity.
	 *
	 * @return position UUID, never null or blank.
	 */
	public String getId() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
		return id;
	}

	/**
	 * Sets this organizational position's stable identity.
	 *
	 * @param id position UUID; null or blank generates a UUID.
	 */
	public void setId(String id) {
		this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
	}

	/**
	 * Returns the chart section containing the position.
	 *
	 * @return chart section.
	 */
	public Section getSection() {
		return section;
	}

	/**
	 * Sets the chart section containing the position.
	 *
	 * @param v section; null defaults to operations.
	 */
	public void setSection(Section v) {
		section = v == null ? Section.OPERATIONS : v;
	}

	/**
	 * Returns the position title.
	 *
	 * @return position title.
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Sets the position title.
	 *
	 * @param v title; null becomes blank.
	 */
	public void setTitle(String v) {
		title = v == null ? "" : v;
	}

	/**
	 * Returns the assigned person's name.
	 *
	 * @return assigned person's name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the assigned person's name.
	 *
	 * @param v name; null becomes blank.
	 */
	public void setName(String v) {
		name = v == null ? "" : v;
	}

	/**
	 * Returns the position radio channel label.
	 *
	 * @return radio channel label.
	 */
	public String getRadio() {
		return radio;
	}

	/**
	 * Sets the position radio channel label.
	 *
	 * @param v radio channel label; null becomes blank.
	 */
	public void setRadio(String v) {
		radio = v == null ? "" : v;
	}

	/**
	 * Returns the position phone number.
	 *
	 * @return phone number.
	 */
	public String getPhone() {
		return phone;
	}

	/**
	 * Sets the position phone number.
	 *
	 * @param v phone number; null becomes blank.
	 */
	public void setPhone(String v) {
		phone = v == null ? "" : v;
	}
}
