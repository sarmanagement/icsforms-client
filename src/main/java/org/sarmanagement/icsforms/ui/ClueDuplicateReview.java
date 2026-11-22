/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.ClueLogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Identifies review candidates, not duplicate decisions. Two active clues are
 * candidates when both their nonblank locations and descriptions match after
 * trimming, collapsing whitespace, and case folding. Task, source and time
 * are deliberately not required because separate logs can report one clue.
 */
final class ClueDuplicateReview {
	/** Prevents instantiation of this utility. */
	private ClueDuplicateReview() {
	}

	/**
	 * Lists each matching pair once in log order without changing any entry.
	 * @param clues current clue log
	 * @return candidate pairs
	 */
	static List<Candidate> candidates(List<ClueLogEntry> clues) {
		List<Candidate> result = new ArrayList<>();
		for (int first = 0; first < clues.size(); first++) {
			ClueLogEntry a = clues.get(first);
			if (a == null || a.isStruckOut()) {
				continue;
			}
			String location = normalize(a.getLocation());
			String description = normalize(a.getDescription());
			if (location.isEmpty() || description.isEmpty()) {
				continue;
			}
			for (int second = first + 1; second < clues.size(); second++) {
				ClueLogEntry b = clues.get(second);
				if (b != null && !b.isStruckOut() && !a.getEntryId().equals(b.getEntryId())
						&& location.equals(normalize(b.getLocation()))
						&& description.equals(normalize(b.getDescription()))) {
					result.add(new Candidate(a, b));
				}
			}
		}
		return result;
	}

	/**
	 * Normalizes text for the deliberately exact candidate rule.
	 * @param text source text
	 * @return normalized text
	 */
	private static String normalize(String text) {
		return text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/**
	 * A pair of independently retained clue records.
	 * @param first first clue in log order
	 * @param second second clue in log order
	 */
	record Candidate(ClueLogEntry first, ClueLogEntry second) {
		/** @return concise pair label for selection */
		@Override
		public String toString() {
			return first.getLocation() + " — " + first.getEntryId() + " / " + second.getEntryId();
		}
	}
}
