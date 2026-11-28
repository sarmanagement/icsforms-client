/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.sarmanagement.icsforms.model.AppData;

/**
 * Latest exact host receipt for each changed document property, historical field and activity log.
 *
 * @param properties latest receipts by root property
 * @param history latest receipts by archived period field
 * @param logs latest receipts by activity log identity
 */
public record AcceptedChanges(Map<String, Receipt> properties, Map<String, Receipt> history,
		Map<String, Receipt> logs) {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
	private static final Set<String> WRITABLE_PROPERTIES = MAPPER.getDeserializationConfig()
			.introspect(MAPPER.constructType(AppData.class)).findProperties().stream()
			.filter(property -> property.getSetter() != null || property.getField() != null)
			.map(com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition::getName)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());

	/**
	 * Identifies an applied host submission and its host receipt time.
	 *
	 * @param operationId exact host operation identity
	 * @param confirmedAt host receipt time
	 */
	public record Receipt(String operationId, Instant confirmedAt) {
	}

	/** Makes each exposed map immutable and accepts missing maps from older journals. */
	public AcceptedChanges {
		properties = properties == null ? Map.of() : Map.copyOf(properties);
		history = history == null ? Map.of() : Map.copyOf(history);
		logs = logs == null ? Map.of() : Map.copyOf(logs);
	}

	/**
	 * Returns an empty acceptance index.
	 *
	 * @return empty index
	 */
	public static AcceptedChanges empty() {
		return new AcceptedChanges(Map.of(), Map.of(), Map.of());
	}

	/**
	 * Records only changes belonging to the operation whose receipt was validated.
	 *
	 * @param operation applied operation
	 * @param confirmedAt host receipt time
	 * @return updated index
	 */
	AcceptedChanges withReceipt(RemoteIncidentStore.PendingOperation operation, Instant confirmedAt) {
		JsonNode before = MAPPER.valueToTree(operation.before());
		JsonNode after = MAPPER.valueToTree(operation.draft());
		Receipt receipt = new Receipt(operation.operationId(), confirmedAt);
		Map<String, Receipt> roots = new HashMap<>(properties);
		Map<String, Receipt> periods = new HashMap<>(history);
		Map<String, Receipt> logReceipts = new HashMap<>(logs);
		Set<String> names = fieldNames(before, after);
		names.retainAll(WRITABLE_PROPERTIES);
		names.removeAll(Set.of("activityLogs", "operationalPeriodHistory"));
		for (String name : names) {
			if (!before.path(name).equals(after.path(name))) {
				roots.put(name, receipt);
			}
		}
		Map<String, JsonNode> oldPeriods = indexed(before.path("operationalPeriodHistory"));
		Map<String, JsonNode> newPeriods = indexed(after.path("operationalPeriodHistory"));
		Set<String> periodIds = new HashSet<>(oldPeriods.keySet());
		periodIds.addAll(newPeriods.keySet());
		for (String id : periodIds) {
			JsonNode oldPeriod = oldPeriods.get(id);
			JsonNode newPeriod = newPeriods.get(id);
			for (String name : fieldNames(oldPeriod, newPeriod)) {
				if (oldPeriod == null || newPeriod == null
						|| !oldPeriod.path(name).equals(newPeriod.path(name))) {
					periods.put(name, receipt);
				}
			}
		}
		Map<String, JsonNode> oldLogs = indexed(before.path("activityLogs"));
		Map<String, JsonNode> newLogs = indexed(after.path("activityLogs"));
		Set<String> ids = new HashSet<>(oldLogs.keySet());
		ids.addAll(newLogs.keySet());
		for (String id : ids) {
			if (!java.util.Objects.equals(oldLogs.get(id), newLogs.get(id))) {
				logReceipts.put(id, receipt);
			}
		}
		return new AcceptedChanges(roots, periods, logReceipts);
	}

	/**
	 * Collects field names from two optional JSON objects.
	 *
	 * @param first first object
	 * @param second second object
	 * @return combined field names
	 */
	private static Set<String> fieldNames(JsonNode first, JsonNode second) {
		Set<String> names = new HashSet<>();
		if (first != null) {
			first.fieldNames().forEachRemaining(names::add);
		}
		if (second != null) {
			second.fieldNames().forEachRemaining(names::add);
		}
		return names;
	}

	/**
	 * Indexes a JSON array by its persistent identity.
	 *
	 * @param array serialized records
	 * @return records by nonblank ID
	 */
	private static Map<String, JsonNode> indexed(JsonNode array) {
		Map<String, JsonNode> result = new LinkedHashMap<>();
		for (JsonNode node : array) {
			String id = node.path("id").asText();
			if (!id.isBlank()) {
				result.put(id, node);
			}
		}
		return result;
	}
}
