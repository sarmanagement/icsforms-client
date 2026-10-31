/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.util.Comparator;
import java.util.List;

/**
 * Provides the canonical numerical order for printable incident forms.
 */
public final class FormOrder {
	private static final List<String> FORM_KEYS = List.of("ICS 201", "ICS 202", "ICS 203", "ICS 204",
			"SAR Task Assignment", "ICS 205", "ICS 205A", "ICS 206", "ICS 206CANINE", "ICS 207", "ICS 208", "ICS 214",
			"ICS 215", "ICS 215A", "ICS 233-CG",
			"Clue Log", "Clue Log (ICS 233-CG adapted)");

	/** Prevents instantiation. */
	private FormOrder() {
	}

	/**
	 * Returns the standard keys in form-number order.
	 *
	 * @return immutable ordered form keys.
	 */
	public static List<String> formKeys() {
		return FORM_KEYS;
	}

	/**
	 * Sorts registered form identifiers in canonical order while preserving the
	 * original order of unknown keys.
	 *
	 * @param formKeys registered or selected form identifiers.
	 * @return identifiers in canonical display and export order.
	 */
	public static List<String> sort(List<String> formKeys) {
		return formKeys.stream().sorted(Comparator.comparingInt(FormOrder::orderOf)).toList();
	}

	/**
	 * Returns the canonical sort position for a form key.
	 *
	 * @param formKey registered form identifier.
	 * @return canonical form position, or a trailing position for unknown keys.
	 */
	private static int orderOf(String formKey) {
		int index = FORM_KEYS.indexOf(formKey);
		return index < 0 ? FORM_KEYS.size() : index;
	}
}
