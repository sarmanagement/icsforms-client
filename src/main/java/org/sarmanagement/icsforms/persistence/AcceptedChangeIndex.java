/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Indexes host receipts by changed document component without retaining incident snapshots.
 */
final class AcceptedChangeIndex {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	/**
	 * Adds the receipt to every component changed by an accepted operation.
	 *
	 * @param previous existing component receipts
	 * @param before document preceding the operation
	 * @param after document after the operation
	 * @param receipt host receipt identifying the accepted operation
	 * @return updated component receipts
	 */
	static Map<String, RemoteIncidentStore.AcceptedReceipt> update(
			Map<String, RemoteIncidentStore.AcceptedReceipt> previous, AppData before, AppData after,
			RemoteIncidentStore.AcceptedReceipt receipt) {
		Map<String, RemoteIncidentStore.AcceptedReceipt> result = new HashMap<>(previous);
		JsonNode oldDocument = MAPPER.valueToTree(before);
		JsonNode newDocument = MAPPER.valueToTree(after);
		Set<String> fields = fields(oldDocument, newDocument);
		fields.remove("activityLogs");
		fields.remove("operationalPeriodHistory");
		for (String field : fields) {
			if (!oldDocument.path(field).equals(newDocument.path(field))) {
				result.put("root/" + field, receipt);
			}
		}
		Map<String, JsonNode> oldLogs = byId(oldDocument.path("activityLogs"));
		Map<String, JsonNode> newLogs = byId(newDocument.path("activityLogs"));
		Set<String> ids = new HashSet<>(oldLogs.keySet());
		ids.addAll(newLogs.keySet());
		for (String id : ids) {
			if (!Objects.equals(oldLogs.get(id), newLogs.get(id))) {
				result.put("log/" + id, receipt);
			}
		}
		Map<String, JsonNode> oldPeriods = byId(oldDocument.path("operationalPeriodHistory"));
		Map<String, JsonNode> newPeriods = byId(newDocument.path("operationalPeriodHistory"));
		Set<String> periodIds = new HashSet<>(oldPeriods.keySet());
		periodIds.addAll(newPeriods.keySet());
		for (String id : periodIds) {
			JsonNode oldPeriod = oldPeriods.get(id);
			JsonNode newPeriod = newPeriods.get(id);
			for (String field : fields(oldPeriod, newPeriod)) {
				if (oldPeriod == null || newPeriod == null
						|| !oldPeriod.path(field).equals(newPeriod.path(field))) {
					result.put("history/" + field, receipt);
				}
			}
		}
		return Map.copyOf(result);
	}

	/**
	 * Collects field names from up to two objects.
	 *
	 * @param first first object, possibly null
	 * @param second second object, possibly null
	 * @return union of field names
	 */
	private static Set<String> fields(JsonNode first, JsonNode second) {
		Set<String> result = new HashSet<>();
		if (first != null) {
			first.fieldNames().forEachRemaining(result::add);
		}
		if (second != null) {
			second.fieldNames().forEachRemaining(result::add);
		}
		return result;
	}

	/**
	 * Indexes array elements by their persistent identifier.
	 *
	 * @param array serialized collection
	 * @return elements with nonblank identifiers
	 */
	private static Map<String, JsonNode> byId(JsonNode array) {
		Map<String, JsonNode> result = new HashMap<>();
		for (JsonNode item : array) {
			String id = item.path("id").asText();
			if (!id.isBlank()) {
				result.put(id, item);
			}
		}
		return result;
	}

	/** Prevents instantiation. */
	private AcceptedChangeIndex() {
	}
}
