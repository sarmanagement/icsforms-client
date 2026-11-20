/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import org.sarmanagement.icsforms.model.RecordIndex;

import java.time.Instant;
import java.util.List;

/**
 * Durable storage for per-record version history, ordered changes, and operation receipts.
 */
public interface RecordVersionStore {
	/**
	 * Loads the state for an incident.
	 *
	 * @param incident incident identity
	 * @return persisted state, or {@code null} when no state exists
	 */
	IncidentState load(IncidentId incident);

	/**
	 * Atomically replaces an incident's complete service state.
	 *
	 * @param incident incident identity
	 * @param state complete updated state
	 */
	void save(IncidentId incident, IncidentState state);

	/**
	 * Immutable service state whose records and receipts are committed together.
	 *
	 * @param cursor highest assigned change cursor
	 * @param histories version history for every record
	 * @param changes ordered record changes
	 * @param receipts idempotency receipts
	 */
	record IncidentState(long cursor, List<RecordHistory> histories, List<RecordChange> changes,
			List<OperationReceipt> receipts) {
		/**
		 * Copies state collections to prevent mutation after persistence.
		 */
		public IncidentState {
			histories = List.copyOf(histories);
			changes = List.copyOf(changes);
			receipts = List.copyOf(receipts);
		}
	}

	/**
	 * All retained versions of one stable record.
	 *
	 * @param key record identity
	 * @param parent parent record, or null for a top-level record
	 * @param parentField containing Java field name
	 * @param versions retained versions in ascending order
	 */
	record RecordHistory(RecordIndex.RecordKey key, RecordIndex.RecordKey parent, String parentField,
			List<RecordVersion> versions) {
		/**
		 * Copies retained versions to prevent mutation after persistence.
		 */
		public RecordHistory {
			versions = List.copyOf(versions);
		}
	}

	/**
	 * One immutable record version.
	 *
	 * @param version positive per-record version
	 * @param cursor incident-wide ordered change cursor
	 * @param receivedAt host-assigned receipt time
	 * @param content detached record snapshot
	 */
	record RecordVersion(long version, long cursor, Instant receivedAt, JsonNode content) {
		/**
		 * Detaches the stored JSON snapshot.
		 */
		public RecordVersion {
			content = content.deepCopy();
		}

		/**
		 * Returns a detached version snapshot.
		 *
		 * @return copy of record content
		 */
		@Override
		public JsonNode content() {
			return content.deepCopy();
		}
	}

	/**
	 * One cursor-ordered record update.
	 *
	 * @param cursor incident-wide ordered change cursor
	 * @param key changed record identity
	 * @param version committed per-record version
	 * @param receivedAt host-assigned receipt time
	 * @param content resulting record snapshot
	 */
	record RecordChange(long cursor, RecordIndex.RecordKey key, long version, Instant receivedAt, JsonNode content) {
		/**
		 * Detaches the changed record snapshot.
		 */
		public RecordChange {
			content = content.deepCopy();
		}

		/**
		 * Returns a detached change snapshot.
		 *
		 * @return copy of record content
		 */
		@Override
		public JsonNode content() {
			return content.deepCopy();
		}
	}

	/**
	 * Idempotency receipt for the original operation result.
	 *
	 * @param operationId client-generated operation identity
	 * @param batch original submitted batch
	 * @param result original submission result
	 */
	record OperationReceipt(String operationId, IncidentService.RecordBatch batch,
			IncidentService.SubmissionResult result) {
	}
}
