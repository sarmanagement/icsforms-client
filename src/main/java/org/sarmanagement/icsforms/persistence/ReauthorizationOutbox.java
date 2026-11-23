/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.sarmanagement.icsforms.model.RecordDiff;
import org.sarmanagement.icsforms.model.RecordIndex;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describes a device's pending outbox for administrator review during re-authorization.
 *
 * <p>The description is bounded so a large offline backlog can still be submitted. It always lists every
 * operation identifier, which the host uses to attribute recovered work, and degrades in detail rather than
 * failing: first exact submitted record content, then record identities only, then operation identifiers only.
 */
public final class ReauthorizationOutbox {
	/** Largest serialized description, leaving headroom below the host's 900,000 character outbox limit. */
	public static final int MAX_DESCRIPTION_CHARS = 600_000;
	/** Level of detail included for each operation. */
	private static final int FULL = 0;
	/** Record identities without content. */
	private static final int IDENTITIES = 1;
	/** Operation identifiers and counts only. */
	private static final int IDENTIFIERS = 2;
	/** Shared mapper for the description. */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Prevents instantiation of this utility. */
	private ReauthorizationOutbox() {
	}

	/**
	 * Builds the review description, using the most detail that fits the size limit.
	 *
	 * @param operations pending operations in outbox order
	 * @return array with one object per operation, always containing its {@code operationId}
	 */
	public static ArrayNode describe(List<RemoteIncidentStore.PendingOperation> operations) {
		return describe(operations, MAX_DESCRIPTION_CHARS);
	}

	/**
	 * Builds the review description for an explicit size limit.
	 *
	 * @param operations pending operations in outbox order
	 * @param maxChars largest acceptable serialized size
	 * @return array with one object per operation, always containing its {@code operationId}
	 */
	static ArrayNode describe(List<RemoteIncidentStore.PendingOperation> operations, int maxChars) {
		ArrayNode result = build(operations, FULL);
		for (int level = IDENTITIES; level <= IDENTIFIERS && result.toString().length() > maxChars; level++) {
			result = build(operations, level);
		}
		return result;
	}

	/**
	 * Builds the description at one level of detail.
	 *
	 * @param operations pending operations
	 * @param level detail level
	 * @return description array
	 */
	private static ArrayNode build(List<RemoteIncidentStore.PendingOperation> operations, int level) {
		ArrayNode array = MAPPER.createArrayNode();
		for (RemoteIncidentStore.PendingOperation operation : operations) {
			ObjectNode item = array.addObject();
			item.put("operationId", operation.operationId());
			item.put("status", operation.status().name());
			if (operation.rejection() != null) {
				item.put("rejection", operation.rejection());
			}
			if (operation.batch() == null) {
				describeUnprepared(item, operation, level);
				continue;
			}
			List<IncidentService.RecordSubmission> records = operation.batch().records();
			item.put("description", records.size() + " record change(s)");
			item.put("recordCount", records.size());
			if (level == IDENTIFIERS) {
				continue;
			}
			ArrayNode listed = item.putArray("records");
			for (IncidentService.RecordSubmission record : records) {
				ObjectNode entry = listed.addObject();
				entry.put("type", record.key().type());
				entry.put("id", record.key().id());
				entry.put("operation", record.operation().name());
				if (level == FULL && record.content() != null) {
					entry.set("content", record.content());
				}
			}
		}
		return array;
	}

	/**
	 * Describes an edit whose wire batch has not been built yet from the difference between its documents.
	 *
	 * @param item description object to fill
	 * @param operation pending operation without a batch
	 * @param level detail level
	 */
	private static void describeUnprepared(ObjectNode item, RemoteIncidentStore.PendingOperation operation,
			int level) {
		RecordDiff diff;
		RecordIndex index;
		try {
			diff = RecordDiff.between(operation.before(), operation.draft());
			index = RecordIndex.of(operation.draft());
		} catch (RuntimeException failure) {
			item.put("description", "Edit not yet prepared for submission");
			return;
		}
		int count = diff.created().size() + diff.changed().size() + diff.removed().size()
				+ diff.struckOut().size();
		item.put("description", count + " record change(s), not yet prepared for submission");
		item.put("recordCount", count);
		if (level == IDENTIFIERS) {
			return;
		}
		ArrayNode listed = item.putArray("records");
		for (RecordIndex.RecordKey key : diff.created()) {
			addRecord(listed, key, "CREATE", null, index, level);
		}
		for (Map.Entry<RecordIndex.RecordKey, Set<String>> changed : diff.changed().entrySet()) {
			addRecord(listed, changed.getKey(), "UPDATE", changed.getValue(), index, level);
		}
		for (RecordIndex.RecordKey key : diff.struckOut()) {
			addRecord(listed, key, "STRIKE_OUT", null, index, level);
		}
		for (RecordIndex.RecordKey key : diff.removed()) {
			addRecord(listed, key, "REMOVE", null, index, level);
		}
	}

	/**
	 * Adds one record entry to an unprepared edit description.
	 *
	 * @param listed array to append to
	 * @param key record identity
	 * @param operation operation name
	 * @param fields changed field names, or null
	 * @param index draft record index supplying current content
	 * @param level detail level
	 */
	private static void addRecord(ArrayNode listed, RecordIndex.RecordKey key, String operation, Set<String> fields,
			RecordIndex index, int level) {
		ObjectNode entry = listed.addObject();
		entry.put("type", key.type());
		entry.put("id", key.id());
		entry.put("operation", operation);
		if (fields != null) {
			ArrayNode names = entry.putArray("changedFields");
			fields.stream().sorted().forEach(names::add);
		}
		if (level == FULL) {
			JsonNode content = index.record(key);
			if (content != null) {
				entry.set("content", content);
			}
		}
	}
}
