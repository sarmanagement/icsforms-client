/*
 * Copyright (C) 2026 SAR Management
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See <https://www.gnu.org/licenses/>.
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Supplemental ICS 203 Organization Assignment List data, alongside the shared chart.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ics203Form {
	/** Supplemental roles not already represented by the shared organizational chart. */
	public enum Position {
		DEPUTY_INCIDENT_COMMANDER, DEPUTY_PLANNING_CHIEF, DEPUTY_LOGISTICS_CHIEF,
		DEPUTY_OPERATIONS_CHIEF, DEPUTY_FINANCE_CHIEF, SUPPORT_BRANCH_DIRECTOR,
		SERVICE_BRANCH_DIRECTOR, MEDICAL_UNIT_LEADER, AIR_OPERATIONS_BRANCH_DIRECTOR
	}

	private Map<Position, String> positions = new EnumMap<>(Position.class);
	private List<OrgChartEntry> agencyRepresentatives = new ArrayList<>();
	private List<Branch> branches = new ArrayList<>();
	private boolean includeInIap = true;
	private String iapPage = "";
	private String preparedByName = "";
	private String preparedByPositionTitle = "";
	private String preparedBySignature = "";
	private LocalDateTime preparedDateTime;

	/**
	 * Returns a supplemental role's assigned name.
	 * @param position role, or null.
	 * @return assigned name, or an empty string.
	 */
	public String getPosition(Position position) {
		return position == null ? "" : text(positions.get(position));
	}

	/**
	 * Assigns a supplemental role.
	 * @param position role; null is ignored.
	 * @param name assigned name; null becomes blank.
	 */
	public void setPosition(Position position, String name) {
		if (position != null) {
			positions.put(position, text(name));
		}
	}

	/**
	 * Returns the mutable supplemental position map for serialization.
	 * @return non-null position map.
	 */
	public Map<Position, String> getPositions() {
		return positions;
	}

	/**
	 * Replaces supplemental roles with a null-safe enum map.
	 * @param values role assignments, or null for no assignments.
	 */
	public void setPositions(Map<Position, String> values) {
		positions = new EnumMap<>(Position.class);
		if (values != null) {
			values.forEach(this::setPosition);
		}
	}

	/**
	 * Returns agency representatives; entry titles hold agencies and names hold representatives.
	 * @return mutable, non-null representatives.
	 */
	public List<OrgChartEntry> getAgencyRepresentatives() {
		return agencyRepresentatives;
	}

	/**
	 * Replaces the agency representative list.
	 * @param values representatives, or null for an empty list.
	 */
	public void setAgencyRepresentatives(List<OrgChartEntry> values) {
		agencyRepresentatives = values == null ? new ArrayList<>() : new ArrayList<>(values);
	}

	/**
	 * Returns operations branches and their grouped divisions.
	 * @return mutable, non-null branches.
	 */
	public List<Branch> getBranches() {
		return branches;
	}

	/**
	 * Replaces the operations branch list.
	 * @param values branches, or null for an empty list.
	 */
	public void setBranches(List<Branch> values) {
		branches = values == null ? new ArrayList<>() : new ArrayList<>(values);
	}

	/**
	 * Returns whether this optional form is included in an IAP.
	 * @return inclusion preference.
	 */
	public boolean isIncludeInIap() {
		return includeInIap;
	}

	/**
	 * Sets optional IAP inclusion without changing assignments.
	 * @param value inclusion preference.
	 */
	public void setIncludeInIap(boolean value) {
		includeInIap = value;
	}

	/**
	 * Returns the IAP page number.
	 * @return page label, possibly blank.
	 */
	public String getIapPage() {
		return iapPage;
	}

	/**
	 * Sets the IAP page number.
	 * @param value page label; null becomes blank.
	 */
	public void setIapPage(String value) {
		iapPage = text(value);
	}

	/**
	 * Returns the preparer's name.
	 * @return name, possibly blank.
	 */
	public String getPreparedByName() {
		return preparedByName;
	}

	/**
	 * Sets the preparer's name.
	 * @param value name; null becomes blank.
	 */
	public void setPreparedByName(String value) {
		preparedByName = text(value);
	}

	/**
	 * Returns the preparer's position/title.
	 * @return title, possibly blank.
	 */
	public String getPreparedByPositionTitle() {
		return preparedByPositionTitle;
	}

	/**
	 * Sets the preparer's position/title.
	 * @param value title; null becomes blank.
	 */
	public void setPreparedByPositionTitle(String value) {
		preparedByPositionTitle = text(value);
	}

	/**
	 * Returns the typed preparer signature.
	 * @return signature, possibly blank.
	 */
	public String getPreparedBySignature() {
		return preparedBySignature;
	}

	/**
	 * Sets the typed preparer signature.
	 * @param value signature; null becomes blank.
	 */
	public void setPreparedBySignature(String value) {
		preparedBySignature = text(value);
	}

	/**
	 * Returns the preparation timestamp stored in UTC.
	 * @return timestamp, or null when unspecified.
	 */
	public LocalDateTime getPreparedDateTime() {
		return preparedDateTime;
	}

	/**
	 * Sets the preparation timestamp stored in UTC.
	 * @param value timestamp, or null when unspecified.
	 */
	public void setPreparedDateTime(LocalDateTime value) {
		preparedDateTime = value;
	}

	/**
	 * Detects assignment content, excluding preparer, page and inclusion metadata.
	 * @return true if any supplemental role, agency, branch or division has content.
	 */
	@JsonIgnore
	public boolean hasAssignments() {
		return positions.values().stream().anyMatch(value -> !text(value).isBlank())
				|| agencyRepresentatives.stream().anyMatch(Ics203Form::hasEntry)
				|| branches.stream().anyMatch(branch -> branch != null
						&& (!branch.getName().isBlank() || !branch.getDirector().isBlank()
						|| !branch.getDeputy().isBlank()
						|| branch.getDivisions().stream().anyMatch(Ics203Form::hasEntry)));
	}

	/**
	 * Tests organizational labels and assigned names without counting contacts.
	 * @param entry organizational entry, possibly null.
	 * @return true when the title or name is nonblank.
	 */
	private static boolean hasEntry(OrgChartEntry entry) {
		return entry != null && (!text(entry.getTitle()).isBlank() || !text(entry.getName()).isBlank());
	}

	/**
	 * Normalizes nullable strings.
	 * @param value source string.
	 * @return source string, or empty when null.
	 */
	private static String text(String value) {
		return value == null ? "" : value;
	}

	/** An operations branch with divisions/groups kept under its director and deputy. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Branch {
		private String name = "";
		private String director = "";
		private String deputy = "";
		private List<OrgChartEntry> divisions = new ArrayList<>();

		/**
		 * Returns the branch label.
		 * @return branch label, possibly blank.
		 */
		public String getName() {
			return name;
		}

		/**
		 * Sets the branch label.
		 * @param value label; null becomes blank.
		 */
		public void setName(String value) {
			name = text(value);
		}

		/**
		 * Returns the director's name.
		 * @return director name, possibly blank.
		 */
		public String getDirector() {
			return director;
		}

		/**
		 * Sets the director's name.
		 * @param value name; null becomes blank.
		 */
		public void setDirector(String value) {
			director = text(value);
		}

		/**
		 * Returns the deputy's name.
		 * @return deputy name, possibly blank.
		 */
		public String getDeputy() {
			return deputy;
		}

		/**
		 * Sets the deputy's name.
		 * @param value name; null becomes blank.
		 */
		public void setDeputy(String value) {
			deputy = text(value);
		}

		/**
		 * Returns divisions/groups with entry titles as labels and names as supervisors.
		 * @return mutable, non-null divisions.
		 */
		public List<OrgChartEntry> getDivisions() {
			return divisions;
		}

		/**
		 * Replaces divisions/groups.
		 * @param values divisions, or null for an empty list.
		 */
		public void setDivisions(List<OrgChartEntry> values) {
			divisions = values == null ? new ArrayList<>() : new ArrayList<>(values);
		}
	}
}
