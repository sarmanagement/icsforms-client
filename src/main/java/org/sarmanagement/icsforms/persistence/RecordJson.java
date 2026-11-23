/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Compares record JSON values independently of Jackson's numeric node subtype.
 */
final class RecordJson {
	/**
	 * Compares JSON values, treating numerically equal number nodes as equal.
	 *
	 * @param first first JSON value, possibly absent
	 * @param second second JSON value, possibly absent
	 * @return true when values are semantically equal
	 */
	static boolean same(JsonNode first, JsonNode second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null) {
			return false;
		}
		if (first.isNumber() || second.isNumber()) {
			return sameNumber(first, second);
		}
		if (first.getNodeType() != second.getNodeType()) {
			return false;
		}
		if (first.isObject()) {
			if (first.size() != second.size()) {
				return false;
			}
			Iterator<Map.Entry<String, JsonNode>> fields = first.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				if (!second.has(field.getKey()) || !same(field.getValue(), second.get(field.getKey()))) {
					return false;
				}
			}
			return true;
		}
		if (first.isArray()) {
			if (first.size() != second.size()) {
				return false;
			}
			for (int index = 0; index < first.size(); index++) {
				if (!same(first.get(index), second.get(index))) {
					return false;
				}
			}
			return true;
		}
		return Objects.equals(first, second);
	}

	/**
	 * Compares two values of which at least one is a number. NaN and infinite values have no decimal form, so they
	 * are compared by their text form, which also matches how they are stored in JSON ("NaN", "Infinity").
	 *
	 * @param first first value
	 * @param second second value
	 * @return true when both are numerically equal, or both are the same non-finite value
	 */
	private static boolean sameNumber(JsonNode first, JsonNode second) {
		if (first.isNumber() && second.isNumber() && isFinite(first) && isFinite(second)) {
			return first.decimalValue().compareTo(second.decimalValue()) == 0;
		}
		if ((first.isNumber() && isFinite(first)) || (second.isNumber() && isFinite(second))) {
			return false;
		}
		return first.asText().equals(second.asText());
	}

	/**
	 * Tells whether a number node has a finite value.
	 *
	 * @param number a number node
	 * @return false for NaN and infinite floating-point values
	 */
	private static boolean isFinite(JsonNode number) {
		return !(number.isDouble() || number.isFloat()) || Double.isFinite(number.doubleValue());
	}

	/**
	 * Prevents construction of this utility class.
	 */
	private RecordJson() {
	}
}
