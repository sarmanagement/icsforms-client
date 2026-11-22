/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Identified;
import org.sarmanagement.icsforms.model.RecordDiff;
import org.sarmanagement.icsforms.model.RecordIndex;
import org.sarmanagement.icsforms.model.RootSettings;
import org.sarmanagement.icsforms.model.RecordIndex.RecordKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.sarmanagement.icsforms.persistence.RecordVersionStore.*;

/**
 * In-process, per-record versioned incident service with durable operation receipts.
 */
public final class IncidentService {
	private final IncidentStore incidents;
	private final RecordVersionStore versions;
	private final RecordDocumentBridge documentBridge = new RecordDocumentBridge();

	/**
	 * Creates a service using the supplied whole-document and record-history stores.
	 *
	 * @param incidents incident document store
	 * @param versions durable record history store
	 */
	public IncidentService(IncidentStore incidents, RecordVersionStore versions) {
		this.incidents = Objects.requireNonNull(incidents, "incidents");
		this.versions = Objects.requireNonNull(versions, "versions");
	}

	/**
	 * Ensures every referenced parent exists before applying the batch.
	 *
	 * @param batch submitted batch
	 * @param histories current record histories
	 */
	private void validateParents(RecordBatch batch, Map<RecordKey, RecordHistory> histories) {
		Map<RecordKey, RecordSubmission> submitted = new HashMap<>();
		batch.records().forEach(submission -> submitted.put(submission.key(), submission));
		for (RecordSubmission submission : batch.records()) {
			RecordKey parent = submission.parent();
			if (submission.operation() == Operation.CREATE
					&& (submission.parentField() == null || submission.parentField().isBlank())) {
				throw new StoreValidationException("Create submission must identify its parent field");
			}
			if (submission.operation() == Operation.CREATE) {
				documentBridge.validateCreate(submission.key(), submission.parent(), submission.parentField());
			}
			if (parent != null && !histories.containsKey(parent) && !submitted.containsKey(parent)) {
				throw new StoreValidationException("Record parent does not exist");
			}
		}
	}

	/**
	 * Reads the current record snapshots and their versions.
	 *
	 * @param incident incident identity
	 * @return detached snapshot with current ordered cursor
	 */
	public ReadResult read(IncidentId incident) {
		return incidents.withIncidentLock(incident, () -> {
			IncidentState current = state(incident);
			ReadResult result = readResult(incident, current);
			System.err.println("RESULT " + result.records().stream().map(VersionedRecord::content).toList());
			return result;
		});
	}

	/**
	 * Returns record changes strictly after the supplied cursor.
	 *
	 * @param incident incident identity
	 * @param cursor last cursor observed by the caller
	 * @return ordered changes and the current cursor
	 * @throws StoreValidationException when the cursor is negative or ahead of the service
	 */
	public ChangeResult changesSince(IncidentId incident, long cursor) {
		return incidents.withIncidentLock(incident, () -> {
			IncidentState state = state(incident);
			if (cursor < 0 || cursor > state.cursor()) {
				throw new StoreValidationException("Change cursor is outside the incident history");
			}
			List<RecordChange> changes = state.changes().stream().filter(change -> change.cursor() > cursor).toList();
			return new ChangeResult(state.cursor(), changes);
		});
	}

	/**
	 * Atomically applies a batch or returns its field-level conflicts.
	 *
	 * @param batch client-generated operation and record changes
	 * @return original result for a new or previously receipted operation
	 */
	public SubmissionResult submit(RecordBatch batch) {
		validateBatch(batch);
		return incidents.withIncidentLock(batch.incident(), () -> submitLocked(batch));
	}

	/**
	 * Submits a batch while the incident operation lock is held.
	 *
	 * @param batch client-generated operation and record changes
	 * @return original result for a new or previously receipted operation
	 */
	private SubmissionResult submitLocked(RecordBatch batch) {
		IncidentState current = versions.load(batch.incident());
		OperationReceipt prior = findReceipt(current, batch.operationId());
		if (prior != null) {
			if (prior.result().applied()) {
				try {
					project(batch.incident(), current);
				} catch (RuntimeException projectionFailure) {
					// The receipt is authoritative; a later read can retry projection repair.
				}
			}
			return prior.result();
		}
		if (current == null) {
			current = state(batch.incident());
		}

		Map<RecordKey, RecordHistory> histories = histories(current);
		validateParents(batch, histories);
		Map<RecordKey, JsonNode> working = currentRecords(histories);
		List<FieldConflict> conflicts = new ArrayList<>();
		List<RecordSubmission> ordered = batch.records().stream()
				.sorted(Comparator.comparing(RecordSubmission::key)).toList();
		for (RecordSubmission submission : ordered) {
			applySubmission(submission, histories, working, conflicts);
		}

		Instant receivedAt = Instant.now();
		if (!conflicts.isEmpty()) {
			SubmissionResult result = new SubmissionResult(batch.operationId(), false, current.cursor(),
					receivedAt, List.of(), conflicts);
			versions.save(batch.incident(), withReceipt(current, batch, result));
			return result;
		}

		long cursor = current.cursor();
		List<RecordHistory> updatedHistories = new ArrayList<>(histories.values());
		List<RecordChange> changes = new ArrayList<>(current.changes());
		List<VersionAssignment> newVersions = new ArrayList<>();
		for (RecordSubmission submission : ordered) {
			JsonNode result = working.get(submission.key());
			RecordHistory history = histories.get(submission.key());
			if (history != null && RecordJson.same(latest(history).content(), result)) {
				newVersions.add(new VersionAssignment(submission.key(), latest(history).version()));
				continue;
			}
			long version = history == null ? 1 : latest(history).version() + 1;
			cursor++;
			RecordVersion recordVersion = new RecordVersion(version, cursor, receivedAt, result);
			List<RecordVersion> recordVersions = history == null ? new ArrayList<>()
					: new ArrayList<>(history.versions());
			recordVersions.add(recordVersion);
			RecordKey parent = history == null ? submission.parent() : history.parent();
			String parentField = history == null ? submission.parentField() : history.parentField();
			RecordHistory nextHistory = new RecordHistory(submission.key(), parent, parentField, recordVersions);
			histories.put(submission.key(), nextHistory);
			replaceHistory(updatedHistories, nextHistory);
			changes.add(new RecordChange(cursor, submission.key(), version, receivedAt, result));
			newVersions.add(new VersionAssignment(submission.key(), version));
		}
		SubmissionResult result = new SubmissionResult(batch.operationId(), true, cursor, receivedAt,
				newVersions, List.of());
		IncidentState updated = new IncidentState(cursor, updatedHistories, changes,
				appendReceipt(current.receipts(), new OperationReceipt(batch.operationId(), batch, result)));
		System.err.println("UPDATED " + updatedHistories.stream().flatMap(history -> history.versions().stream())
				.map(RecordVersion::content).filter(content -> content.path("provenance").has("acceptedAt")).toList());
		versions.save(batch.incident(), updated);
		System.err.println("RELOADED " + versions.load(batch.incident()).histories().stream()
				.flatMap(history -> history.versions().stream()).map(RecordVersion::content)
				.filter(content -> content.path("provenance").has("acceptedAt")).toList());
		try {
			project(batch.incident(), updated);
		} catch (RuntimeException projectionFailure) {
			throw new StoreCommittedException(projectionFailure);
		}
		return result;
	}

	/**
	 * Synchronizes a successful standalone document save into the record history.
	 *
	 * @param incident incident identity
	 * @param data newly saved document
	 */
	public void synchronize(IncidentId incident, AppData data) {
		synchronize(incident, null, data);
	}

	/**
	 * Synchronizes a successful standalone save using its before-and-after snapshots.
	 *
	 * @param incident incident identity
	 * @param previous prior saved document, or null when creating
	 * @param data newly saved document
	 */
	public void synchronize(IncidentId incident, AppData previous, AppData data) {
		incidents.withIncidentLock(incident, () -> {
			synchronizeLocked(incident, previous, data);
			return null;
		});
	}

	/**
	 * Synchronizes a standalone save while the incident operation lock is held.
	 *
	 * @param incident incident identity
	 * @param previous prior saved document, or null when creating
	 * @param data newly saved document
	 */
	private void synchronizeLocked(IncidentId incident, AppData previous, AppData data) {
		Objects.requireNonNull(data, "data");
		if (!incident.value().equals(data.getIncidentId())) {
			throw new StoreValidationException("Document identity does not match incident");
		}
		if (previous != null && !incident.value().equals(previous.getIncidentId())) {
			throw new StoreValidationException("Prior document identity does not match incident");
		}
		IncidentState state = versions.load(incident);
		if (state == null) {
			state = initialState(data);
			versions.save(incident, state);
			return;
		}
		Map<RecordKey, RecordHistory> histories = histories(state);
		long cursor = state.cursor();
		Instant receivedAt = Instant.now();
		List<RecordChange> changes = new ArrayList<>(state.changes());
		RecordIndex index = RecordIndex.of(data);
		java.util.Set<RecordKey> changed = new java.util.TreeSet<>();
		if (previous == null) {
			changed.addAll(index.records().keySet());
		} else {
			RecordDiff diff = RecordDiff.between(previous, data);
			changed.addAll(diff.created());
			changed.addAll(diff.changed().keySet());
			changed.addAll(diff.struckOut());
		}
		for (Map.Entry<RecordKey, JsonNode> entry : index.records().entrySet()) {
			if (!changed.contains(entry.getKey())) {
				continue;
			}
			RecordHistory history = histories.get(entry.getKey());
			if (history != null && RecordJson.same(latest(history).content(), entry.getValue())) {
				continue;
			}
			long version = history == null ? 1 : latest(history).version() + 1;
			cursor++;
			RecordVersion next = new RecordVersion(version, cursor, receivedAt, entry.getValue());
			List<RecordVersion> retained = history == null ? new ArrayList<>()
					: new ArrayList<>(history.versions());
			retained.add(next);
			histories.put(entry.getKey(), new RecordHistory(entry.getKey(), index.parentOf(entry.getKey()),
					index.parentFieldOf(entry.getKey()), retained));
			changes.add(new RecordChange(cursor, entry.getKey(), version, receivedAt, entry.getValue()));
		}
		RecordHistory rootHistory = histories.get(RootSettings.key());
		JsonNode rootSnapshot = RootSettings.snapshot(data);
		if (rootHistory == null || !RecordJson.same(latest(rootHistory).content(), rootSnapshot)) {
			long version = rootHistory == null ? 1 : latest(rootHistory).version() + 1;
			cursor++;
			List<RecordVersion> retained = rootHistory == null ? new ArrayList<>()
					: new ArrayList<>(rootHistory.versions());
			retained.add(new RecordVersion(version, cursor, receivedAt, rootSnapshot));
			histories.put(RootSettings.key(), new RecordHistory(RootSettings.key(), null, null, retained));
			changes.add(new RecordChange(cursor, RootSettings.key(), version, receivedAt, rootSnapshot));
		}
		versions.save(incident, new IncidentState(cursor, new ArrayList<>(histories.values()), changes,
				state.receipts()));
	}

	/**
	 * Loads or initializes history from the stored document and repairs its projection.
	 * Callers must hold the incident operation lock.
	 *
	 * @param incident incident identity
	 * @return durable incident record state
	 */
	private IncidentState state(IncidentId incident) {
		Objects.requireNonNull(incident, "incident");
		StoredIncident stored = incidents.loadSnapshot(incident);
		IncidentState state = versions.load(incident);
		if (state == null) {
			state = initialState(stored.data());
			versions.save(incident, state);
			return state;
		}
		project(stored, state);
		IncidentState projected = versions.load(incident);
		IncidentState current = projected == null ? state : projected;
		IncidentState seeded = withRoot(current, stored.data());
		if (seeded != current) {
			versions.save(incident, seeded);
		}
		return seeded;
	}

	/**
	 * Projects the current service state into the whole-document store.
	 *
	 * @param incident incident identity
	 * @param state authoritative versioned state
	 */
	private void project(IncidentId incident, IncidentState state) {
		project(incidents.loadSnapshot(incident), state);
	}

	/**
	 * Projects the current service state into a detached document snapshot.
	 *
	 * @param stored current document and matching revision
	 * @param state authoritative versioned state
	 */
	private void project(StoredIncident stored, IncidentState state) {
		if (documentBridge.apply(stored.data(), state.histories())) {
			incidents.save(IncidentId.of(stored.data().getIncidentId()), stored.data(), stored.revision());
		}
	}

	/**
	 * Builds a first history from the current whole-document snapshot.
	 *
	 * @param data incident document
	 * @return initial state with deterministic cursors
	 */
	private IncidentState initialState(AppData data) {
		long cursor = 0;
		Instant receivedAt = Instant.now();
		List<RecordHistory> histories = new ArrayList<>();
		List<RecordChange> changes = new ArrayList<>();
		RecordIndex index = RecordIndex.of(data);
		for (Map.Entry<RecordKey, JsonNode> entry : index.records().entrySet()) {
			cursor++;
			RecordVersion first = new RecordVersion(1, cursor, receivedAt, entry.getValue());
			histories.add(new RecordHistory(entry.getKey(), index.parentOf(entry.getKey()),
					index.parentFieldOf(entry.getKey()), List.of(first)));
			changes.add(new RecordChange(cursor, entry.getKey(), 1, receivedAt, entry.getValue()));
		}
		cursor++;
		RecordVersion root = new RecordVersion(1, cursor, receivedAt, RootSettings.snapshot(data));
		histories.add(new RecordHistory(RootSettings.key(), null, null, List.of(root)));
		changes.add(new RecordChange(cursor, RootSettings.key(), 1, receivedAt, root.content()));
		return new IncidentState(cursor, histories, changes, List.of());
	}

	/**
	 * Adds the incident-wide settings record to a state that predates it, seeding it from the stored document.
	 *
	 * @param state authoritative state
	 * @param data stored document the state was projected into
	 * @return the same state when it already holds the record, otherwise a state with its first version
	 */
	private IncidentState withRoot(IncidentState state, AppData data) {
		if (histories(state).containsKey(RootSettings.key())) {
			return state;
		}
		long cursor = state.cursor() + 1;
		Instant receivedAt = Instant.now();
		RecordVersion root = new RecordVersion(1, cursor, receivedAt, RootSettings.snapshot(data));
		List<RecordHistory> histories = new ArrayList<>(state.histories());
		histories.add(new RecordHistory(RootSettings.key(), null, null, List.of(root)));
		List<RecordChange> changes = new ArrayList<>(state.changes());
		changes.add(new RecordChange(cursor, RootSettings.key(), 1, receivedAt, root.content()));
		return new IncidentState(cursor, histories, changes, state.receipts());
	}

	/**
	 * Validates batch identity, operation IDs, and record payload structure.
	 *
	 * @param batch batch to validate
	 */
	private void validateBatch(RecordBatch batch) {
		Objects.requireNonNull(batch, "batch");
		if (batch.operationId() == null || batch.operationId().isBlank() || batch.records().isEmpty()) {
			throw new StoreValidationException("Operation ID and at least one record are required");
		}
		Map<RecordKey, RecordSubmission> submitted = new HashMap<>();
		for (RecordSubmission submission : batch.records()) {
			if (submitted.put(submission.key(), submission) != null) {
				throw new StoreValidationException("A batch cannot submit one record more than once");
			}
			validateKey(submission.key());
			if (submission.content() == null || !submission.content().isObject()
					|| !submission.key().id().equals(submission.content().path("id").asText(null))) {
				throw new StoreValidationException("Record ID is missing or immutable");
			}
			if (submission.baseVersion() < 0) {
				throw new StoreValidationException("Record base version cannot be negative");
			}
			if (submission.operation() == Operation.STRIKE_OUT) {
				JsonNode struckOut = submission.content().has("struckOut")
						? submission.content().get("struckOut") : submission.content().get("isStruckOut");
				if (struckOut == null || !struckOut.asBoolean(false)) {
					throw new StoreValidationException("Strike-out submission must retain the record as struck out");
				}
			}
		}
	}

	/**
	 * Rejects record identities outside the application's identified model types.
	 *
	 * @param key record identity
	 */
	private void validateKey(RecordKey key) {
		try {
			Class<?> type = Class.forName(key.type(), false, IncidentService.class.getClassLoader());
			if (!type.getPackageName().equals(AppData.class.getPackageName())
					|| !Identified.class.isAssignableFrom(type)) {
				throw new StoreValidationException("Unsupported record type");
			}
		} catch (ClassNotFoundException exception) {
			throw new StoreValidationException("Unknown record type", exception);
		}
		if (key.id().isBlank()) {
			throw new StoreValidationException("Record ID cannot be blank");
		}
	}

	/**
	 * Applies one record submission to the tentative batch state.
	 *
	 * @param submission submitted record
	 * @param histories known versions
	 * @param working tentative current content
	 * @param conflicts field-level conflict accumulator
	 */
	private void applySubmission(RecordSubmission submission, Map<RecordKey, RecordHistory> histories,
			Map<RecordKey, JsonNode> working, List<FieldConflict> conflicts) {
		RecordHistory history = histories.get(submission.key());
		if (submission.operation() == Operation.CREATE) {
			if (history != null) {
				conflicts.add(new FieldConflict(submission.key(), "$record", null, submission.content(),
						latest(history).content()));
				return;
			}
			if (submission.baseVersion() != 0) {
				throw new StoreValidationException("New records must use base version zero");
			}
			working.put(submission.key(), submission.content().deepCopy());
			return;
		}
		if (history == null) {
			throw new StoreValidationException("Unknown record");
		}
		RecordVersion base = history.versions().stream()
				.filter(version -> version.version() == submission.baseVersion()).findFirst()
				.orElseThrow(() -> new StoreValidationException("Unknown base record version"));
		JsonNode theirs = latest(history).content();
		JsonNode mine = submission.content();
		if (base.content().equals(theirs)) {
			working.put(submission.key(), mine.deepCopy());
			return;
		}
		ObjectNode merged = theirs.deepCopy();
		java.util.Set<String> names = new java.util.TreeSet<>();
		base.content().fieldNames().forEachRemaining(names::add);
		mine.fieldNames().forEachRemaining(names::add);
		theirs.fieldNames().forEachRemaining(names::add);
		for (String name : names) {
			if ("id".equals(name)) {
				continue;
			}
			JsonNode baseValue = base.content().get(name);
			JsonNode mineValue = mine.get(name);
			JsonNode theirsValue = theirs.get(name);
			if (same(baseValue, mineValue)) {
				continue;
			}
			if (same(baseValue, theirsValue) || same(mineValue, theirsValue)) {
				if (mineValue == null) {
					merged.remove(name);
				} else {
					merged.set(name, mineValue.deepCopy());
				}
			} else {
				conflicts.add(new FieldConflict(submission.key(), name, baseValue, mineValue, theirsValue));
			}
		}
		working.put(submission.key(), merged);
	}

	/**
	 * Compares JSON values while distinguishing absent fields from explicit null.
	 *
	 * @param first first value
	 * @param second second value
	 * @return true when values are semantically identical
	 */
	private boolean same(JsonNode first, JsonNode second) {
		return RecordJson.same(first, second);
	}

	/**
	 * Creates a detached public read result.
	 *
	 * @param incident incident identity
	 * @param state persisted state
	 * @return current record snapshots and versions
	 */
	private ReadResult readResult(IncidentId incident, IncidentState state) {
		List<VersionedRecord> records = new ArrayList<>();
		System.err.println("HISTORY KEYS " + state.histories().stream().map(RecordHistory::key).toList());
		histories(state).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			RecordVersion version = latest(entry.getValue());
			if (version.content().path("description").asText().contains("Accepted after approval")) {
				System.err.println("SELECTED HISTORY " + entry.getKey() + " "
						+ version.content().path("provenance"));
			}
			records.add(new VersionedRecord(entry.getKey(), version.version(), version.cursor(),
					version.receivedAt(), version.content(), entry.getValue().parent(),
					entry.getValue().parentField()));
		});
		return new ReadResult(incident, state.cursor(), records);
	}

	/**
	 * Builds an ordered mutable history lookup.
	 *
	 * @param state incident state
	 * @return record histories keyed by identity
	 */
	private Map<RecordKey, RecordHistory> histories(IncidentState state) {
		Map<RecordKey, RecordHistory> result = new HashMap<>();
		state.histories().forEach(history -> result.put(history.key(), history));
		return result;
	}

	/**
	 * Gets the latest retained version.
	 *
	 * @param history record history
	 * @return newest version
	 */
	private RecordVersion latest(RecordHistory history) {
		return history.versions().get(history.versions().size() - 1);
	}

	/**
	 * Extracts current content from histories.
	 *
	 * @param histories record histories
	 * @return current record snapshots
	 */
	private Map<RecordKey, JsonNode> currentRecords(Map<RecordKey, RecordHistory> histories) {
		Map<RecordKey, JsonNode> result = new HashMap<>();
		histories.forEach((key, history) -> result.put(key, latest(history).content()));
		return result;
	}

	/**
	 * Finds a prior operation receipt.
	 *
	 * @param state incident state, possibly null
	 * @param operationId operation identity
	 * @return receipt, or null
	 */
	private OperationReceipt findReceipt(IncidentState state, String operationId) {
		if (state == null) {
			return null;
		}
		return state.receipts().stream().filter(receipt -> receipt.operationId().equals(operationId))
				.findFirst().orElse(null);
	}

	/**
	 * Returns state with one durable receipt appended.
	 *
	 * @param state previous state
	 * @param operationId operation identity
	 * @param result original result
	 * @return state containing the receipt
	 */
	private IncidentState withReceipt(IncidentState state, RecordBatch batch, SubmissionResult result) {
		return new IncidentState(state.cursor(), state.histories(), state.changes(),
				appendReceipt(state.receipts(), new OperationReceipt(batch.operationId(), batch, result)));
	}

	/**
	 * Appends a receipt without replacing earlier receipts.
	 *
	 * @param receipts existing receipts
	 * @param receipt new receipt
	 * @return ordered receipts
	 */
	private List<OperationReceipt> appendReceipt(List<OperationReceipt> receipts, OperationReceipt receipt) {
		List<OperationReceipt> result = new ArrayList<>(receipts);
		result.add(receipt);
		return result;
	}

	/**
	 * Replaces one history in an ordered list.
	 *
	 * @param histories existing ordered histories
	 * @param replacement replacement history
	 */
	private void replaceHistory(List<RecordHistory> histories, RecordHistory replacement) {
		for (int index = 0; index < histories.size(); index++) {
			if (histories.get(index).key().equals(replacement.key())) {
				histories.set(index, replacement);
				return;
			}
		}
		histories.add(replacement);
	}

	/**
	 * Record mutation kind.
	 */
	public enum Operation {
		CREATE, UPDATE, STRIKE_OUT
	}

	/**
	 * One record mutation in an atomic submission.
	 *
	 * @param key stable record identity
	 * @param parent parent record, or null for a top-level record
	 * @param parentField Java field containing the new record, required for creation
	 * @param baseVersion version last observed by the client; zero for creation
	 * @param operation mutation kind
	 * @param content submitted record snapshot
	 */
	public record RecordSubmission(RecordKey key, RecordKey parent, String parentField, long baseVersion,
			Operation operation, JsonNode content) {
		/**
		 * Requires identity, operation, and detached record content.
		 */
		public RecordSubmission {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(operation, "operation");
			content = content == null ? null : content.deepCopy();
		}

		/**
		 * Returns a detached record snapshot.
		 *
		 * @return copy of submitted content, or null
		 */
		@Override
		public JsonNode content() {
			return content == null ? null : content.deepCopy();
		}
	}

	/**
	 * Atomic multi-record operation.
	 *
	 * @param incident incident identity
	 * @param operationId client-generated idempotency key
	 * @param records record mutations
	 */
	public record RecordBatch(IncidentId incident, String operationId, List<RecordSubmission> records) {
		/**
		 * Detaches the mutation list.
		 */
		public RecordBatch {
			Objects.requireNonNull(incident, "incident");
			records = List.copyOf(records);
		}
	}

	/**
	 * Current incident record snapshot.
	 *
	 * @param incident incident identity
	 * @param cursor current incident-wide cursor
	 * @param records records keyed by stable identity
	 */
	public record ReadResult(IncidentId incident, long cursor, List<VersionedRecord> records) {
		/**
		 * Copies the ordered record map.
		 */
		public ReadResult {
			records = List.copyOf(records);
		}
	}

	/**
	 * One current record and its version metadata.
	 *
	 * @param key stable record identity
	 * @param version per-record version
	 * @param cursor latest incident-wide cursor
	 * @param receivedAt host-assigned timestamp
	 * @param content detached record snapshot
	 * @param parent containing record, or null when attached directly to the incident root
	 * @param parentField containing Java field name, used to rebuild the document tree
	 */
	public record VersionedRecord(RecordKey key, long version, long cursor, Instant receivedAt, JsonNode content,
			RecordKey parent, String parentField) {
		/**
		 * Detaches the JSON content.
		 */
		public VersionedRecord {
			content = content.deepCopy();
		}

		/**
		 * Creates a record snapshot without placement metadata.
		 *
		 * @param key stable record identity
		 * @param version per-record version
		 * @param cursor latest incident-wide cursor
		 * @param receivedAt host-assigned timestamp
		 * @param content detached record snapshot
		 */
		public VersionedRecord(RecordKey key, long version, long cursor, Instant receivedAt, JsonNode content) {
			this(key, version, cursor, receivedAt, content, null, null);
		}

		/**
		 * Returns a detached record snapshot.
		 *
		 * @return copy of current record content
		 */
		@Override
		public JsonNode content() {
			return content.deepCopy();
		}
	}

	/**
	 * Cursor-ordered changes after a caller's last cursor.
	 *
	 * @param cursor current incident-wide cursor
	 * @param changes matching changes
	 */
	public record ChangeResult(long cursor, List<RecordChange> changes) {
		/**
		 * Copies the change list.
		 */
		public ChangeResult {
			changes = List.copyOf(changes);
		}
	}

	/**
	 * Original result of a submitted batch.
	 *
	 * @param operationId client-generated operation identity
	 * @param applied whether all submitted records were committed
	 * @param cursor current incident-wide cursor
	 * @param receivedAt host-assigned receipt timestamp
	 * @param versions newly committed per-record versions
	 * @param conflicts field-level merge conflicts
	 */
	public record SubmissionResult(String operationId, boolean applied, long cursor, Instant receivedAt,
			List<VersionAssignment> versions, List<FieldConflict> conflicts) {
		/**
		 * Copies result collections.
		 */
		public SubmissionResult {
			versions = List.copyOf(versions);
			conflicts = List.copyOf(conflicts);
		}
	}

	/**
	 * Per-record version returned by an accepted batch.
	 *
	 * @param key stable record identity
	 * @param version newly current version
	 */
	public record VersionAssignment(RecordKey key, long version) {
	}

	/**
	 * One field-level three-way merge conflict.
	 *
	 * @param key conflicting record identity
	 * @param field field name
	 * @param base value last seen by the client
	 * @param mine submitted value
	 * @param theirs current host value
	 */
	public record FieldConflict(RecordKey key, String field, JsonNode base, JsonNode mine, JsonNode theirs) {
		/**
		 * Detaches values participating in the conflict.
		 */
		public FieldConflict {
			base = base == null ? null : base.deepCopy();
			mine = mine == null ? null : mine.deepCopy();
			theirs = theirs == null ? null : theirs.deepCopy();
		}

		/**
		 * Returns the detached base value.
		 *
		 * @return base JSON value, or null when absent
		 */
		@Override
		public JsonNode base() {
			return base == null ? null : base.deepCopy();
		}

		/**
		 * Returns the detached submitted value.
		 *
		 * @return submitted JSON value, or null when absent
		 */
		@Override
		public JsonNode mine() {
			return mine == null ? null : mine.deepCopy();
		}

		/**
		 * Returns the detached host value.
		 *
		 * @return host JSON value, or null when absent
		 */
		@Override
		public JsonNode theirs() {
			return theirs == null ? null : theirs.deepCopy();
		}
	}
}
