/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.Provenance;

/**
 * Formats retained clue identity and duplicate-review history for both exports.
 */
final class ClueReviewPdfText {
	/** Prevents instantiation of this utility. */
	private ClueReviewPdfText() {
	}

	/**
	 * Describes a clue and any explicit strike-out decision without removing evidence.
	 * Times are shown as UTC instants and absent attribution is labelled.
	 * @param clue source clue
	 * @return printable history text
	 */
	static String history(ClueLogEntry clue) {
		StringBuilder text = new StringBuilder("Clue ID: ").append(clue.getEntryId());
		text.append("\nEntry provenance: ").append(provenance(clue.getProvenance()));
		if (clue.isStruckOut()) {
			text.append("\nSTRUCK OUT");
		}
		ClueLogEntry.DuplicateConfirmation decision = clue.getDuplicateConfirmation();
		if (decision != null) {
			text.append("\nConfirmed duplicate of clue ID: ").append(decision.duplicateOfId())
					.append("\nConfirmed at UTC: ").append(decision.confirmedAt())
					.append("\nReviewer: ").append(provenance(decision.provenance()));
		}
		return text.toString();
	}

	/**
	 * Formats optional record attribution for human review.
	 * @param value attribution or null
	 * @return printable attribution
	 */
	private static String provenance(Provenance value) {
		if (value == null) {
			return "unavailable";
		}
		return PdfText.normalize(value.operatorName()) + " / "
				+ PdfText.normalize(value.stationId()) + " / entered UTC " + value.enteredAt()
				+ (value.acceptedAt() == null ? "" : " / accepted UTC " + value.acceptedAt());
	}
}
