/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.model.RecordDiff;
import org.sarmanagement.icsforms.model.RecordIndex;
import org.sarmanagement.icsforms.model.RecordIndex.RecordKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * Production {@link IncidentStore} that works against a host's record service. It is deliberately distinct from
 * any in-memory test double: it holds no incident data of its own beyond a bounded cache of the snapshots whose
 * tokens it has issued, exposes no file paths, and offers no advisory locks.
 *
 * <p>Each {@link StoredIncident} is a detached snapshot rebuilt from versioned records. Its
 * {@link RevisionToken} identifies that snapshot only: it is derived from the host's committed cursor and the
 * versions of the records the snapshot contains, so equal states give equal tokens and a token never describes
 * data the caller does not hold. Saving translates the edited document into record submissions using
 * {@link RecordDiff} against the snapshot named by the expected token, sends the base version of each changed
 * record, and lets the host merge or report field conflicts. A conflict is reported as
 * {@link StoreConflictException}; it is never resubmitted blindly.
 *
 * <p>The store serves one shared incident. Creating, deleting and importing incidents are administrative actions on
 * the host and fail explicitly with {@link UnsupportedOperationException}. Records cannot be physically removed
 * through the service, so a save that removes a record fails with {@link StoreValidationException}; strike out
 * instead. Every method that talks to the host performs blocking network I/O and must not be called on the Swing
 * event-dispatch thread.
 */
public final class RemoteIncidentStore implements IncidentStore {
	private static final int MAX_CACHED_BASES = 16;
	private static final int MAX_BATCH_RECORDS = 100;
	private static final int MAX_BATCH_BYTES = 900_000;
	private static final String CONTRACT_MESSAGE = "This operation is an administrative action on the host and "
			+ "is not available to a connected workstation.";
	private static final ObjectMapper EXPORT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.addMixIn(Provenance.class, Provenance.LocalStorageMixin.class);

	/**
	 * The snapshot a token names: the document handed to the caller, the host records it was built from and the
	 * committed cursor of that state.
	 *
	 * @param document detached document matching the token
	 * @param records host record versions held by the document, which are the bases for later submissions
	 * @param cursor host cursor at the time of the read
	 */
	private record Base(AppData document, Map<RecordKey, IncidentService.VersionedRecord> records, long cursor) {
	}

	private final RemoteIncidentGateway gateway;
	private final IncidentId incident;
	private final RecordDocumentBridge bridge = new RecordDocumentBridge();
	private final Object cacheLock = new Object();
	private final Map<RevisionToken, Base> bases = new LinkedHashMap<>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		/**
		 * Evicts the least recently used snapshot beyond the bound.
		 *
		 * @param eldest oldest entry
		 * @return true when the entry should be evicted
		 */
		@Override
		protected boolean removeEldestEntry(Map.Entry<RevisionToken, Base> eldest) {
			return size() > MAX_CACHED_BASES;
		}
	};
	private final Map<AppData, RevisionToken> issued = new WeakHashMap<>();
	private volatile boolean closed;

	/**
	 * Creates a store for the one incident a host shares.
	 *
	 * @param gateway transport to the host
	 * @param incident incident shared by the host
	 */
	public RemoteIncidentStore(RemoteIncidentGateway gateway, IncidentId incident) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.incident = Objects.requireNonNull(incident, "incident");
	}

	/**
	 * Returns the shared incident this store serves.
	 *
	 * @return incident identity
	 */
	public IncidentId incident() {
		return incident;
	}

	/**
	 * Records that a caller-held document corresponds to a token this store issued, for hosts that create an
	 * incident administratively and then hand the original object to a remote handle.
	 *
	 * @param data caller-held document
	 * @param token token issued by this store
	 */
	void associate(AppData data, RevisionToken token) {
		synchronized (cacheLock) {
			issued.put(data, token);
		}
	}

	/**
	 * Extracts the host cursor embedded in a token issued by this store, so polling can start from the state a
	 * snapshot represents. The cursor is only an ordering hint; tokens are otherwise opaque.
	 *
	 * @param token token issued by a remote store
	 * @return the cursor, or empty when the token was not issued by a remote store
	 */
	public static java.util.OptionalLong cursorOf(RevisionToken token) {
		String[] parts = token.value().split("/");
		if (parts.length != 3 || !"remote".equals(parts[0])) {
			return java.util.OptionalLong.empty();
		}
		try {
			return java.util.OptionalLong.of(Long.parseLong(parts[1]));
		} catch (NumberFormatException failure) {
			return java.util.OptionalLong.empty();
		}
	}

	/**
	 * Returns the transport, for connection monitoring.
	 *
	 * @return gateway
	 */
	public RemoteIncidentGateway gateway() {
		return gateway;
	}

	/**
	 * Remote stores never create incidents; the host owns the shared incident.
	 *
	 * @param data ignored
	 * @return never returns
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public IncidentId create(AppData data) {
		ensureOpen();
		throw new UnsupportedOperationException("Creating an incident: " + CONTRACT_MESSAGE);
	}

	/**
	 * Reads the shared incident and rebuilds a detached document.
	 *
	 * @param id incident identity
	 * @return snapshot and its snapshot-specific token
	 * @throws IncidentNotFoundException when the identity is not the shared incident
	 * @throws StoreUnavailableException when the host cannot be reached or the store is closed
	 */
	@Override
	public StoredIncident loadSnapshot(IncidentId id) {
		ensureOpen();
		requireShared(id);
		IncidentService.ReadResult read = gateway.read(incident);
		Map<RecordKey, IncidentService.VersionedRecord> records = recordMap(read);
		AppData built = build(read);
		RevisionToken token = token(read.cursor(), records);
		return remember(token, built, records, read.cursor());
	}

	/**
	 * Saves an edited document by submitting changed records with their base versions.
	 *
	 * @param id incident identity
	 * @param data edited document
	 * @param expected token of the snapshot the edit was based on
	 * @return token of the state the caller now holds
	 * @throws StoreConflictException when the host holds conflicting or newer data for the edit
	 * @throws StoreValidationException when the document or token is invalid, or records were removed
	 * @throws StoreUnavailableException when the host cannot be reached
	 */
	@Override
	public RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
		ensureOpen();
		requireShared(id);
		if (data == null || expected == null || !id.value().equals(data.getIncidentId())) {
			throw new StoreValidationException("Identity or expected revision is invalid");
		}
		if (data.getSchemaVersion() > AppData.CURRENT_SCHEMA_VERSION) {
			throw new StoreValidationException("Unsupported document schema");
		}
		Base base = baseFor(expected);
		RecordDiff diff = RecordDiff.between(base.document(), data);
		if (!diff.removed().isEmpty()) {
			throw new StoreValidationException("The host cannot remove records; strike them out instead ("
					+ diff.removed().size() + " removed)");
		}
		List<IncidentService.RecordSubmission> submissions = submissions(base, data, diff);
		if (submissions.isEmpty()) {
			IncidentService.ChangeResult state = gateway.changesSince(incident, base.cursor());
			if (state.cursor() != base.cursor()) {
				throw conflict("The incident changed on the host", expected);
			}
			synchronized (cacheLock) {
				issued.put(data, expected);
			}
			return expected;
		}
		IncidentService.RecordBatch batch = batch(expected, submissions);
		IncidentService.SubmissionResult result = gateway.submit(batch);
		if (!result.applied()) {
			throw conflict("The edit conflicts with newer changes to " + result.conflicts().size() + " field(s)",
					expected);
		}
		IncidentService.ReadResult after = gateway.read(incident);
		Map<RecordKey, IncidentService.VersionedRecord> records = hybridRecords(base, submissions, after);
		RevisionToken token = token(after.cursor(), records);
		AppData held = FullDocumentSnapshot.detach(data);
		synchronized (cacheLock) {
			bases.put(token, new Base(held, records, after.cursor()));
			issued.put(data, token);
		}
		return token;
	}

	/**
	 * Lists the single shared incident.
	 *
	 * @return one summary, or an empty list when the host no longer shares the incident
	 * @throws StoreUnavailableException when the host cannot be reached or the store is closed
	 */
	@Override
	public List<IncidentSummary> list() {
		ensureOpen();
		IncidentService.ReadResult read;
		try {
			read = gateway.read(incident);
		} catch (IncidentNotFoundException missing) {
			return List.of();
		}
		AppData built = build(read);
		Instant modified = read.records().stream().map(IncidentService.VersionedRecord::receivedAt)
				.max(Comparator.naturalOrder()).orElse(null);
		RevisionToken token = token(read.cursor(), recordMap(read));
		return List.of(new IncidentSummary(gateway.location(), built.getIncidentContext().getIncidentName(),
				built.getIapPhase(), modified, incident, built.getForm201().getIncidentNumber(), token));
	}

	/**
	 * Remote stores never delete the shared incident.
	 *
	 * @param id ignored
	 * @param expected ignored
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public void delete(IncidentId id, RevisionToken expected) {
		ensureOpen();
		throw new UnsupportedOperationException("Deleting an incident: " + CONTRACT_MESSAGE);
	}

	/**
	 * Returns the token issued for a snapshot the caller holds.
	 *
	 * @param data document previously returned by or saved through this store
	 * @return that snapshot's token
	 * @throws StoreValidationException when the store never issued a token for the object
	 */
	@Override
	public RevisionToken revisionToken(AppData data) {
		synchronized (cacheLock) {
			RevisionToken token = issued.get(data);
			if (token == null) {
				throw new StoreValidationException("Caller snapshot has no remote revision");
			}
			return token;
		}
	}

	/**
	 * Remote stores do not import into the shared incident.
	 *
	 * @param jsonFile ignored
	 * @return never returns
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public Optional<IncidentId> importFile(Path jsonFile) {
		throw new UnsupportedOperationException("Importing into the shared incident: " + CONTRACT_MESSAGE);
	}

	/**
	 * Writes the current shared snapshot to a file on this workstation.
	 *
	 * @param id incident identity
	 * @param target local destination
	 * @throws StorePersistenceException when the local file cannot be written
	 * @throws StoreUnavailableException when the host cannot be reached
	 */
	@Override
	public void exportFile(IncidentId id, Path target) {
		ensureOpen();
		StoredIncident snapshot = loadSnapshot(id);
		try {
			EXPORT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(validateLocalExportTarget(target).toFile(),
					snapshot.data());
		} catch (IOException failure) {
			throw new StorePersistenceException("Cannot export incident: " + id, failure);
		}
	}

	/**
	 * Describes the remote store without exposing host paths.
	 *
	 * @return descriptor naming the host address
	 */
	@Override
	public StoreDescriptor describe() {
		return new StoreDescriptor("Remote", gateway.location());
	}

	/**
	 * Closes the transport and rejects later operations. Idempotent.
	 */
	@Override
	public void close() {
		closed = true;
		gateway.close();
	}

	/**
	 * Rejects use after close.
	 *
	 * @throws StoreUnavailableException when closed
	 */
	private void ensureOpen() {
		if (closed) {
			throw new StoreUnavailableException("Remote store handle is closed");
		}
	}

	/**
	 * Requires the identity of the shared incident.
	 *
	 * @param id requested identity
	 * @throws IncidentNotFoundException when it differs
	 */
	private void requireShared(IncidentId id) {
		if (id == null || !incident.equals(id)) {
			throw new IncidentNotFoundException("Missing or unshared incident: " + id);
		}
	}

	/**
	 * Finds the snapshot a token names.
	 *
	 * @param token expected token
	 * @return cached snapshot
	 * @throws StoreValidationException when the token was not issued by this store or was evicted
	 */
	private Base baseFor(RevisionToken token) {
		synchronized (cacheLock) {
			Base base = bases.get(token);
			if (base == null) {
				throw new StoreValidationException("The snapshot for this revision is no longer held; reload "
						+ "the incident before saving");
			}
			return base;
		}
	}

	/**
	 * Caches a freshly read snapshot and hands the caller its own copy.
	 *
	 * @param token snapshot token
	 * @param built document rebuilt from the records
	 * @param records host records held
	 * @param cursor host cursor of the read
	 * @return detached snapshot for the caller
	 */
	private StoredIncident remember(RevisionToken token, AppData built, Map<RecordKey,
			IncidentService.VersionedRecord> records, long cursor) {
		AppData caller = FullDocumentSnapshot.detach(built);
		synchronized (cacheLock) {
			bases.put(token, new Base(built, records, cursor));
			issued.put(caller, token);
		}
		return new StoredIncident(caller, token);
	}

	/**
	 * Indexes a read result by record identity.
	 *
	 * @param read host read
	 * @return records by key
	 */
	private static Map<RecordKey, IncidentService.VersionedRecord> recordMap(IncidentService.ReadResult read) {
		Map<RecordKey, IncidentService.VersionedRecord> records = new HashMap<>();
		read.records().forEach(record -> records.put(record.key(), record));
		return records;
	}

	/**
	 * Rebuilds a document from versioned records.
	 *
	 * @param read host read
	 * @return document carrying the shared identity and the records
	 */
	private AppData build(IncidentService.ReadResult read) {
		AppData data = new AppData();
		data.setIncidentId(incident.value());
		List<RecordVersionStore.RecordHistory> histories = new ArrayList<>();
		Instant created = null;
		Instant modified = null;
		for (IncidentService.VersionedRecord record : read.records()) {
			histories.add(new RecordVersionStore.RecordHistory(record.key(), record.parent(), record.parentField(),
					List.of(new RecordVersionStore.RecordVersion(record.version(), record.cursor(),
							record.receivedAt(), record.content()))));
			if (record.receivedAt() != null) {
				created = created == null || record.receivedAt().isBefore(created) ? record.receivedAt() : created;
				modified = modified == null || record.receivedAt().isAfter(modified) ? record.receivedAt() : modified;
			}
		}
		try {
			bridge.apply(data, histories, true);
		} catch (StoreValidationException failure) {
			throw new StorePersistenceException("The host returned records that cannot form a document", failure);
		}
		data.setIncidentId(incident.value());
		data.setCreatedAt(created);
		data.setUpdatedAt(modified);
		return data;
	}

	/**
	 * Derives the snapshot token from the host cursor and the record versions held.
	 *
	 * @param cursor host cursor
	 * @param records record versions held
	 * @return deterministic token
	 */
	private static RevisionToken token(long cursor, Map<RecordKey, IncidentService.VersionedRecord> records) {
		Map<RecordKey, Long> versions = new TreeMap<>();
		records.forEach((key, record) -> versions.put(key, record.version()));
		StringBuilder canonical = new StringBuilder();
		versions.forEach((key, version) -> canonical.append(key.type()).append('|').append(key.id()).append('|')
				.append(version).append('\n'));
		return new RevisionToken("remote/" + cursor + "/" + digest(canonical.toString()).substring(0, 32));
	}

	/**
	 * Computes a SHA-256 hex digest.
	 *
	 * @param text text to digest
	 * @return lower-case hex digest
	 */
	private static String digest(String text) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable", failure);
		}
	}

	/**
	 * Translates a document difference into record submissions carrying base versions.
	 *
	 * @param base snapshot the edit started from
	 * @param edited edited document
	 * @param diff record-level difference
	 * @return submissions ordered deterministically
	 */
	private List<IncidentService.RecordSubmission> submissions(Base base, AppData edited, RecordDiff diff) {
		RecordIndex index = RecordIndex.of(edited);
		List<IncidentService.RecordSubmission> result = new ArrayList<>();
		for (RecordKey key : diff.created()) {
			result.add(new IncidentService.RecordSubmission(key, index.parentOf(key), index.parentFieldOf(key), 0,
					IncidentService.Operation.CREATE, index.record(key)));
		}
		for (Map.Entry<RecordKey, java.util.Set<String>> entry : diff.changed().entrySet()) {
			RecordKey key = entry.getKey();
			IncidentService.VersionedRecord held = base.records().get(key);
			if (held == null) {
				throw new StoreValidationException("The edited record is not part of the base snapshot: " + key);
			}
			ObjectNode content = (ObjectNode) held.content();
			JsonNode after = index.record(key);
			for (String field : entry.getValue()) {
				JsonNode value = after.get(field);
				if (value == null) {
					content.remove(field);
				} else {
					content.set(field, value.deepCopy());
				}
			}
			IncidentService.Operation operation = diff.struckOut().contains(key)
					? IncidentService.Operation.STRIKE_OUT : IncidentService.Operation.UPDATE;
			result.add(new IncidentService.RecordSubmission(key, held.parent(), held.parentField(), held.version(),
					operation, content));
		}
		result.sort(Comparator.comparing(IncidentService.RecordSubmission::key));
		return result;
	}

	/**
	 * Builds the idempotent batch. The operation ID is a digest of the expected token and the canonical batch, so a
	 * retry after an ambiguous failure replays the host's receipt instead of applying twice.
	 *
	 * @param expected token the edit was based on
	 * @param submissions record submissions
	 * @return batch
	 * @throws StoreValidationException when the batch exceeds the protocol limits
	 */
	private IncidentService.RecordBatch batch(RevisionToken expected,
			List<IncidentService.RecordSubmission> submissions) {
		if (submissions.size() > MAX_BATCH_RECORDS) {
			throw new StoreValidationException("The edit changes " + submissions.size()
					+ " records, more than one batch allows (" + MAX_BATCH_RECORDS + ")");
		}
		ArrayNode canonical = new ObjectMapper().createArrayNode();
		for (IncidentService.RecordSubmission submission : submissions) {
			ObjectNode node = canonical.addObject();
			node.put("type", submission.key().type());
			node.put("id", submission.key().id());
			node.put("base", submission.baseVersion());
			node.put("op", submission.operation().name());
			node.set("content", submission.content());
		}
		String text = canonical.toString();
		if (text.length() > MAX_BATCH_BYTES) {
			throw new StoreValidationException("The edit is larger than one batch allows");
		}
		String operationId = "remote-" + digest(expected.value() + '\n' + text).substring(0, 40);
		return new IncidentService.RecordBatch(incident, operationId, submissions);
	}

	/**
	 * Computes the record bases for the state the caller holds after an applied save. Records the host holds
	 * exactly as submitted adopt their new version. Records merged with, or changed by, other writers keep the old
	 * base so later submissions are still merged against what the caller actually saw.
	 *
	 * @param base snapshot the edit started from
	 * @param submissions submitted records
	 * @param after host state after the save
	 * @return records the caller's document now corresponds to
	 */
	private static Map<RecordKey, IncidentService.VersionedRecord> hybridRecords(Base base,
			List<IncidentService.RecordSubmission> submissions, IncidentService.ReadResult after) {
		Map<RecordKey, IncidentService.RecordSubmission> submitted = new HashMap<>();
		submissions.forEach(submission -> submitted.put(submission.key(), submission));
		Map<RecordKey, IncidentService.VersionedRecord> result = new HashMap<>();
		for (IncidentService.VersionedRecord record : after.records()) {
			IncidentService.RecordSubmission submission = submitted.get(record.key());
			if (submission != null) {
				result.put(record.key(), RecordJson.same(record.content(), submission.content()) ? record
						: base.records().getOrDefault(record.key(), record));
			} else if (base.records().containsKey(record.key())) {
				result.put(record.key(), base.records().get(record.key()));
			}
		}
		return result;
	}

	/**
	 * Builds a conflict carrying the caller's token and the host's current token.
	 *
	 * @param message operator-facing description
	 * @param expected token the edit was based on
	 * @return exception to throw
	 */
	private StoreConflictException conflict(String message, RevisionToken expected) {
		IncidentService.ReadResult read = gateway.read(incident);
		return new StoreConflictException(message, expected, token(read.cursor(), recordMap(read)));
	}
}
