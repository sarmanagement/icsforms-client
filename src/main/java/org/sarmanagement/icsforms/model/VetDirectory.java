/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Incident-owned reusable regional veterinary records and import provenance.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VetDirectory {
	private List<VetFacility> facilities = new ArrayList<>();
	private List<Source> sources = new ArrayList<>();

	/** Creates an empty directory that does not depend on source files. */
	public VetDirectory() {
	}

	/**
	 * Returns all regional records, not only records selected for a plan.
	 *
	 * @return all directory facilities.
	 */
	public List<VetFacility> getFacilities() {
		return facilities;
	}

	/**
	 * Replaces the regional records.
	 *
	 * @param facilities facilities; null becomes empty.
	 */
	public void setFacilities(List<VetFacility> facilities) {
		this.facilities = facilities == null ? new ArrayList<>() : facilities;
	}

	/**
	 * Returns source descriptions for import provenance.
	 *
	 * @return source descriptions.
	 */
	public List<Source> getSources() {
		return sources;
	}

	/**
	 * Replaces source descriptions.
	 *
	 * @param sources sources; null becomes empty.
	 */
	public void setSources(List<Source> sources) {
		this.sources = sources == null ? new ArrayList<>() : sources;
	}

	/**
	 * Non-dependent display metadata for a local regional file.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Source {
		private String name = "";
		private String format = "";
		private Instant importedAt;

		/** Creates empty source metadata. */
		public Source() {
		}

		/** @return source file display name, not a runtime path. */
		public String getName() {
			return name;
		}

		/** @param name source file display name. */
		public void setName(String name) {
			this.name = name == null ? "" : name;
		}

		/** @return format such as JSON or CSV. */
		public String getFormat() {
			return format;
		}

		/** @param format format such as JSON or CSV. */
		public void setFormat(String format) {
			this.format = format == null ? "" : format;
		}

		/** @return import time, or null. */
		public Instant getImportedAt() {
			return importedAt;
		}

		/** @param importedAt import time. */
		public void setImportedAt(Instant importedAt) {
			this.importedAt = importedAt;
		}
	}
}
