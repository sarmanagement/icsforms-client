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
		if (first == null || second == null || first.getNodeType() != second.getNodeType()) {
			return first != null && second != null && first.isNumber() && second.isNumber()
					&& first.decimalValue().compareTo(second.decimalValue()) == 0;
		}
		if (first.isNumber()) {
			return first.decimalValue().compareTo(second.decimalValue()) == 0;
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
	 * Prevents construction of this utility class.
	 */
	private RecordJson() {
	}
}
