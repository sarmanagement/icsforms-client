/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.util.UUID;

/**
 * Qualitative POD factor rating collected during SAR task debriefing.
 */
public class PodFactorRating {
	private static final int DEFAULT_MAX_SCORE = 10;

	private String id = UUID.randomUUID().toString();
	private String name = "";
	private int maxScore = DEFAULT_MAX_SCORE;
	private Integer score;
	private String description = "";

	/**
	 * Returns this point-of-debriefing factor's stable identity.
	 *
	 * @return factor UUID, never null or blank
	 */
	public String getId() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
		return id;
	}

	/**
	 * Sets this factor's stable identity.
	 *
	 * @param id factor UUID; null or blank generates a UUID
	 */
	public void setId(String id) {
		this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
	}

	/**
	 * Returns the factor label.
	 *
	 * @return factor label, never null
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the factor label.
	 *
	 * @param name factor label; null becomes empty
	 */
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	/**
	 * Returns the maximum rating score.
	 *
	 * @return positive maximum score
	 */
	public int getMaxScore() {
		return maxScore;
	}

	/**
	 * Sets the maximum rating score.
	 *
	 * @param maxScore positive maximum; non-positive values use the default
	 */
	public void setMaxScore(int maxScore) {
		this.maxScore = maxScore <= 0 ? DEFAULT_MAX_SCORE : maxScore;
	}

	/**
	 * Returns the entered rating.
	 *
	 * @return score, or null when unset
	 */
	public Integer getScore() {
		return score;
	}

	/**
	 * Sets the entered rating.
	 *
	 * @param score score, or null when unset
	 */
	public void setScore(Integer score) {
		this.score = score;
	}

	/**
	 * Returns the factor notes.
	 *
	 * @return notes, never null
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the factor notes.
	 *
	 * @param description notes; null becomes empty
	 */
	public void setDescription(String description) {
		this.description = description == null ? "" : description;
	}
}
