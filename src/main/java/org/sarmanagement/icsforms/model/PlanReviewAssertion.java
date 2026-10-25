/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * Persisted human assertion that one content boundary was reviewed.
 *
 * <p>
 * This is not authentication, an electronic signature, or a replacement for
 * printed signature fields.
 * </p>
 */
public class PlanReviewAssertion {
	private String reviewedBy = "";
	private LocalDateTime reviewedAt;
	private String reviewedContentDigest = "";

	/** Creates an empty, not-yet-reviewed assertion. */
	public PlanReviewAssertion() {
	}

	/** @return person who made the assertion. */
	public String getReviewedBy() {
		return reviewedBy == null ? "" : reviewedBy;
	}

	/** @param reviewedBy person making the assertion. */
	public void setReviewedBy(String reviewedBy) {
		this.reviewedBy = reviewedBy == null ? "" : reviewedBy;
	}

	/** @return assertion time, or null when never reviewed. */
	public LocalDateTime getReviewedAt() {
		return reviewedAt;
	}

	/** @param reviewedAt assertion time. */
	public void setReviewedAt(LocalDateTime reviewedAt) {
		this.reviewedAt = reviewedAt;
	}

	/** @return digest of the reviewed substantive content boundary. */
	public String getReviewedContentDigest() {
		return reviewedContentDigest == null ? "" : reviewedContentDigest;
	}

	/** @param reviewedContentDigest digest of reviewed substantive content. */
	public void setReviewedContentDigest(String reviewedContentDigest) {
		this.reviewedContentDigest = reviewedContentDigest == null ? "" : reviewedContentDigest;
	}

	/**
	 * Returns whether any explicit assertion has been recorded.
	 *
	 * @return true when reviewer, time, and content boundary are all present.
	 */
	@JsonIgnore
	public boolean isRecorded() {
		return !getReviewedBy().isBlank() && reviewedAt != null && !getReviewedContentDigest().isBlank();
	}
}
