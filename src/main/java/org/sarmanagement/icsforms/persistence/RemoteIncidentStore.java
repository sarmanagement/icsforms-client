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
import org.sarmanagement.icsforms.host.HostProtocol;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.model.RecordDiff;
import org.sarmanagement.icsforms.model.RecordIndex;
import org.sarmanagement.icsforms.model.RecordIndex.RecordKey;
import org.sarmanagement.icsforms.model.RootSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
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
import java.util.UUID;

/**
 * Production {@link IncidentStore} that works against a host's record service. The ordinary constructor keeps a
 * bounded in-memory snapshot cache. The opt-in disconnected constructor instead maintains a private durable
 * snapshot and ordered outbox, partitioned by pinned host authority and incident. It exposes no host file paths
 * and offers no advisory locks.
 *
 * <p>Each {@link StoredIncident} is a detached snapshot rebuilt from versioned records. Its
 * {@link RevisionToken} identifies that snapshot only: it is derived from the host's committed cursor and the
 * versions of the records the snapshot contains, so equal states give equal tokens and a token never describes
 * data the caller does not hold. Saving translates the edited document into record submissions using
 * {@link RecordDiff} against the snapshot named by the expected token, sends the base version of each changed
 * record, and lets the host merge or report field conflicts. A conflict is reported as
 * {@link StoreConflictException}; it is never resubmitted blindly.
 *
 * <p>Disconnected saves commit the local draft and outbox atomically without claiming host acceptance. Explicit
 * {@link #replayPending()} submits the queue in order; conflicts and rejections remain available for recovery.
 * Network and local journal operations are synchronized and must run off the Swing event-dispatch thread.
 *
 * <p>The store serves one shared incident. Creating, deleting and importing incidents are administrative actions on
 * the host and fail explicitly with {@link UnsupportedOperationException}. Removed records are submitted as
 * record removals to the host. Every method that talks to the host performs blocking network I/O and must not be
 * called on the Swing event-dispatch thread.
 */
public final class RemoteIncidentStore implements IncidentStore {
	/** Message of the validation failure raised when the host moved on after a conflict was recorded. */
	public static final String HOST_CHANGED = "The host changed since conflict review; review again";
	private static final int MAX_CACHED_BASES = 16;
	private static final int MAX_BATCH_RECORDS = 100;
	private static final int MAX_BATCH_BYTES = 900_000;
	private static final String CONTRACT_MESSAGE = "This operation is an administrative action on the host and "
			+ "is not available to a connected workstation.";
	private static final ObjectMapper EXPORT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.addMixIn(Provenance.class, Provenance.LocalStorageMixin.class);
	private static final ObjectMapper WIRE_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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

	/** State of an ordered disconnected operation. */
	public enum PendingStatus {
		/** Waiting for submission or an ambiguous submission retry. */
		PENDING,
		/** The host reported conflicting field changes. */
		CONFLICT,
		/** The host rejected this operation. */
		REJECTED,
		/** A conflict disappeared and the newly rebased operation awaits explicit confirmation. */
		REVIEW_REQUIRED
	}

	/** Explicit choice for one conflicting field. */
	public enum ConflictChoice {
		/** Keep the local value. */
		MINE,
		/** Keep the current host value. */
		THEIRS
	}

	/**
	 * Identifies one field within a conflicting record.
	 *
	 * @param key record identity
	 * @param field field name, or {@code $record} for a creation collision
	 */
	public record ConflictField(RecordKey key, String field) {
	}

	/**
	 * Durable local edit and, once materialized, its exact immutable wire batch.
	 *
	 * @param operationId stable client-generated operation identifier
	 * @param before document immediately preceding this edit
	 * @param draft document after this edit
	 * @param batch exact submitted batch, or null until predecessors are acknowledged
	 * @param status current replay status
	 * @param conflicts retained field conflicts
	 * @param rejection rejection explanation, or null
	 * @param reviewBase original operator-observed base for detecting unseen remote changes
	 * @param definitivelyRejected a first-attempt validation rejection with no earlier ambiguous submission
	 */
	public record PendingOperation(String operationId, AppData before, AppData draft,
			IncidentService.RecordBatch batch, PendingStatus status, List<IncidentService.FieldConflict> conflicts,
			String rejection, AppData reviewBase, boolean definitivelyRejected) {
		/**
		 * Detaches mutable documents and conflict collection.
		 */
		public PendingOperation {
			before = FullDocumentSnapshot.detach(before);
			draft = FullDocumentSnapshot.detach(draft);
			reviewBase = FullDocumentSnapshot.detach(reviewBase == null ? before : reviewBase);
			conflicts = List.copyOf(conflicts);
		}

		/**
		 * Creates a new operation based on the document the operator observed.
		 *
		 * @param operationId stable operation identifier
		 * @param before document preceding the edit
		 * @param draft edited document
		 * @param batch materialized batch, or null
		 * @param status replay status
		 * @param conflicts retained conflicts
		 * @param rejection rejection explanation, or null
		 */
		public PendingOperation(String operationId, AppData before, AppData draft,
				IncidentService.RecordBatch batch, PendingStatus status, List<IncidentService.FieldConflict> conflicts,
				String rejection) {
			this(operationId, before, draft, batch, status, conflicts, rejection, before, false);
		}

		/**
		 * Creates an operation without a definitive first-attempt rejection.
		 *
		 * @param operationId stable operation identifier
		 * @param before document preceding the edit
		 * @param draft edited document
		 * @param batch materialized batch, or null
		 * @param status replay status
		 * @param conflicts retained conflicts
		 * @param rejection rejection explanation, or null
		 * @param reviewBase original operator-observed base
		 */
		public PendingOperation(String operationId, AppData before, AppData draft,
				IncidentService.RecordBatch batch, PendingStatus status, List<IncidentService.FieldConflict> conflicts,
				String rejection, AppData reviewBase) {
			this(operationId, before, draft, batch, status, conflicts, rejection, reviewBase, false);
		}

		/** @return detached document preceding the edit */
		@Override
		public AppData before() {
			return FullDocumentSnapshot.detach(before);
		}

		/** @return detached edited document */
		@Override
		public AppData draft() {
			return FullDocumentSnapshot.detach(draft);
		}

		/** @return detached original operator-observed base */
		@Override
		public AppData reviewBase() {
			return FullDocumentSnapshot.detach(reviewBase);
		}
	}

	/**
	 * Identifies an available local cache without including credentials.
	 *
	 * @param authorityFingerprint pinned host authority SHA-256 fingerprint
	 * @param incident cached incident identity
	 */
	public record CachedIncident(String authorityFingerprint, IncidentId incident) {
	}

	private RemoteIncidentGateway gateway;
	private final IncidentId incident;
	private DisconnectedStateFile stateFile;
	private DisconnectedStateFile.State state;
	private DisconnectedStateFile.Cutoff cutoff = new DisconnectedStateFile.Cutoff(null, List.of(), List.of());
	private String authorityFingerprint;
	private String storageNotice;
	private final RecordDocumentBridge bridge = new RecordDocumentBridge();
	private final Object cacheLock = new Object();
	private final Map<RevisionToken, Base> bases = new LinkedHashMap<>(16, 0.75f, true);
	private final Map<AppData, RevisionToken> issued = new WeakHashMap<>();
	private final Map<AppData, RevisionToken> loadedSnapshots = new WeakHashMap<>();
	private RevisionToken latestSavedRevision;
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
	 * Enables durable disconnected operation using the gateway's pinned authority.
	 *
	 * @param gateway authenticated host transport
	 * @param incident shared incident
	 * @param root private local cache root
	 */
	public RemoteIncidentStore(RemoteIncidentGateway gateway, IncidentId incident, Path root) {
		this(gateway, incident);
		openState(root, gateway.authorityFingerprint().orElseThrow(
				() -> new StoreValidationException("A pinned host authority is required for disconnected storage")));
	}

	/**
	 * Enables durable disconnected operation, failing over between candidate cache locations. A location that
	 * already holds this incident's journal is preferred; otherwise the first location whose file system can be
	 * restricted to the owner is used, so that a FAT or exFAT drive does not cost the operator offline editing.
	 * When a candidate cannot be secured, {@link #disconnectedStorageNotice()} reports use of a protected fallback.
	 * Only when no candidate can be secured does the store continue connected-only.
	 *
	 * @param gateway authenticated host transport
	 * @param incident shared incident
	 * @param roots candidate private cache roots in order of preference
	 * @return a store with disconnected storage, or a connected-only store with a notice
	 */
	public static RemoteIncidentStore connect(RemoteIncidentGateway gateway, IncidentId incident, List<Path> roots) {
		String fingerprint = gateway.authorityFingerprint().orElseThrow(
				() -> new StoreValidationException("A pinned host authority is required for disconnected storage"));
		List<Path> ordered = new ArrayList<>(roots.stream()
				.filter(root -> Files.exists(journalPath(root, fingerprint, incident))).toList());
		roots.stream().filter(root -> !ordered.contains(root)).forEach(ordered::add);
		StorageNotSecurableException last = null;
		for (Path root : ordered) {
			try {
				RemoteIncidentStore store = new RemoteIncidentStore(gateway, incident, root);
				boolean fallback = !root.toAbsolutePath().normalize()
						.equals(roots.get(0).toAbsolutePath().normalize());
				if (fallback) {
					store.storageNotice = "Offline storage is using a protected fallback location instead of the "
							+ "preferred location.";
				} else if (last != null) {
					store.storageNotice = "Another offline storage location could not be secured; a different "
							+ "protected location is in use.";
				}
				return store;
			} catch (StorageNotSecurableException failure) {
				last = failure;
			}
		}
		RemoteIncidentStore degraded = new RemoteIncidentStore(gateway, incident);
		degraded.storageNotice = last == null ? "No location is available for offline storage" : last.getMessage();
		return degraded;
	}

	/**
	 * Enables durable disconnected operation in one location, or continues connected-only when it cannot be
	 * protected.
	 *
	 * @param gateway authenticated host transport
	 * @param incident shared incident
	 * @param root private local cache root
	 * @return a store with disconnected storage, or a connected-only store with a notice
	 */
	public static RemoteIncidentStore connect(RemoteIncidentGateway gateway, IncidentId incident, Path root) {
		return connect(gateway, incident, List.of(root));
	}

	/**
	 * Names where a journal lives under a cache root.
	 *
	 * @param root cache root
	 * @param fingerprint pinned authority fingerprint
	 * @param incident incident identity
	 * @return journal file path
	 */
	private static Path journalPath(Path root, String fingerprint, IncidentId incident) {
		return root.toAbsolutePath().normalize().resolve(fingerprint).resolve(incident.value() + ".json");
	}

	/**
	 * Opens a cached incident from whichever candidate location holds its journal.
	 *
	 * @param roots candidate cache roots
	 * @param authorityFingerprint pinned host authority SHA-256 fingerprint
	 * @param incident cached incident identity
	 * @return offline-capable store
	 * @throws IncidentNotFoundException when no location holds the journal
	 */
	public static RemoteIncidentStore openOffline(List<Path> roots, String authorityFingerprint, IncidentId incident) {
		for (Path root : roots) {
			if (Files.exists(journalPath(root, authorityFingerprint, incident))) {
				return openOffline(root, authorityFingerprint, incident);
			}
		}
		throw new IncidentNotFoundException("No disconnected snapshot exists for incident: " + incident);
	}

	/**
	 * Finds locally cached incidents across several candidate locations, without duplicates.
	 *
	 * @param roots candidate cache roots
	 * @return cached identities
	 */
	public static List<CachedIncident> discoverCached(List<Path> roots) {
		java.util.Set<CachedIncident> found = new java.util.LinkedHashSet<>();
		for (Path root : roots) {
			found.addAll(discoverCached(root));
		}
		return List.copyOf(found);
	}

	/**
	 * Explains a storage fallback or why disconnected storage was not enabled, if applicable.
	 *
	 * @return operator-facing notice, or empty
	 */
	public Optional<String> disconnectedStorageNotice() {
		return Optional.ofNullable(storageNotice);
	}

	/**
	 * Opens a previously cached incident without a gateway or credential.
	 *
	 * @param root private local cache root
	 * @param authorityFingerprint pinned host authority SHA-256 fingerprint
	 * @param incident cached incident identity
	 * @return offline-capable store
	 */
	public static RemoteIncidentStore openOffline(Path root, String authorityFingerprint, IncidentId incident) {
		RemoteIncidentStore store = new RemoteIncidentStore(incident);
		store.openState(root, authorityFingerprint);
		if (store.state == null) {
			store.close();
			throw new IncidentNotFoundException("No disconnected snapshot exists for incident: " + incident);
		}
		return store;
	}

	/**
	 * Creates an unattached store for restoring an existing cache.
	 *
	 * @param incident cached identity
	 */
	private RemoteIncidentStore(IncidentId incident) {
		this.incident = Objects.requireNonNull(incident, "incident");
	}

	/**
	 * Finds locally cached incidents, grouped by the pinned authority fingerprint.
	 *
	 * @param root cache root
	 * @return cached identities
	 */
	public static List<CachedIncident> discoverCached(Path root) {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		List<CachedIncident> result = new ArrayList<>();
		try (var directories = Files.list(root)) {
			for (Path directory : directories.filter(Files::isDirectory).toList()) {
				String fingerprint = directory.getFileName().toString();
				if (!fingerprint.matches("[0-9a-f]{64}")) {
					continue;
				}
				try (var files = Files.list(directory)) {
					for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
						String name = file.getFileName().toString();
						try {
							result.add(new CachedIncident(fingerprint, IncidentId.of(name.substring(0, name.length() - 5))));
						} catch (IllegalArgumentException ignored) {
							// Ignore files that are not incident journals.
						}
					}
				}
			}
		} catch (IOException failure) {
			throw new StorePersistenceException("Cannot discover disconnected incidents", failure);
		}
		return List.copyOf(result);
	}

	/**
	 * Attaches a re-paired transport only when it pins the same host authority.
	 *
	 * @param replacement newly paired gateway
	 */
	public synchronized void attachGateway(RemoteIncidentGateway replacement) {
		ensureOpen();
		Objects.requireNonNull(replacement, "replacement");
		String expectedAuthority = stateFile == null
				? gateway == null ? "" : gateway.authorityFingerprint().orElse("")
				: authorityFingerprint;
		if (expectedAuthority.isEmpty() || !expectedAuthority.equals(replacement.authorityFingerprint().orElse(""))) {
			throw new StoreValidationException("Gateway authority does not match the disconnected cache");
		}
		if (gateway != null && gateway != replacement) {
			gateway.close();
		}
		gateway = replacement;
	}

	/**
	 * Records an observed credential cutoff without changing or discarding local work.
	 *
	 * @param reason observed access failure
	 */
	public synchronized void recordCredentialCutoff(RemoteAccessException.Reason reason) {
		ensureOpen();
		if (reason != RemoteAccessException.Reason.CREDENTIAL_EXPIRED
				&& reason != RemoteAccessException.Reason.CREDENTIAL_REVOKED) {
			return;
		}
		if (cutoff.reason() == reason) {
			return;
		}
		DisconnectedStateFile.Cutoff next = new DisconnectedStateFile.Cutoff(reason, cutoff.blocked(),
				cutoff.reviewed());
		cutoff = next;
		if (stateFile != null) {
			stateFile.saveCutoff(next);
		}
	}

	/**
	 * Freezes replay and snapshots the operations presented for administrator approval.
	 *
	 * @param reason expired or revoked credential
	 * @return detached operations in order
	 */
	public synchronized List<PendingOperation> beginReauthorization(RemoteAccessException.Reason reason) {
		if (reason != RemoteAccessException.Reason.CREDENTIAL_EXPIRED
				&& reason != RemoteAccessException.Reason.CREDENTIAL_REVOKED) {
			throw new IllegalArgumentException("Expected an expired or revoked credential");
		}
		recordCredentialCutoff(reason);
		return pendingOperations();
	}

	/**
	 * Attaches an approved credential and releases only work included in the approval request.
	 * Edits made while the administrator was reviewing remain locally blocked and exportable.
	 *
	 * @param replacement approved replacement transport
	 * @param reviewed operation identifiers submitted for approval
	 */
	public synchronized void completeReauthorization(RemoteIncidentGateway replacement, List<String> reviewed) {
		ensureOpen();
		if (cutoff.reason() == null) {
			throw new StoreValidationException("No credential cutoff awaits approval");
		}
		Objects.requireNonNull(reviewed, "reviewed");
		List<String> blocked = state == null ? List.of() : state.pending().stream()
				.map(PendingOperation::operationId).filter(id -> !reviewed.contains(id)).toList();
		attachGateway(replacement);
		DisconnectedStateFile.Cutoff next = new DisconnectedStateFile.Cutoff(null, blocked, reviewed);
		if (stateFile != null) {
			stateFile.saveCutoff(next);
		}
		cutoff = next;
	}

	/**
	 * Transfers ownership of the current transport to the caller without closing it.
	 * No operation may be in flight when transferring the transport.
	 *
	 * @return formerly attached gateway
	 */
	public synchronized RemoteIncidentGateway detachGateway() {
		ensureOpen();
		RemoteIncidentGateway detached = requireGateway();
		gateway = null;
		return detached;
	}

	/**
	 * Returns the last time this cache was confirmed by its host.
	 *
	 * @return confirmation time, or empty before the first confirmation
	 */
	public synchronized Optional<Instant> lastConfirmedAt() {
		ensureOpen();
		return Optional.ofNullable(state == null ? null : state.confirmedAt());
	}

	/**
	 * Returns the durable number of applied host receipts recorded by this cache.
	 *
	 * @return count of accepted operations, excluding local no-ops
	 */
	public synchronized long acceptedReceiptCount() {
		ensureOpen();
		return state == null ? 0 : state.acceptedReceipts();
	}

	/**
	 * Returns durable attribution from exact applied host receipts.
	 *
	 * @return latest receipt for each changed property, historical field and log
	 */
	public synchronized AcceptedChanges acceptedChanges() {
		ensureOpen();
		return state == null ? AcceptedChanges.empty() : state.acceptedChanges();
	}

	/**
	 * Opens and verifies the local journal namespace.
	 *
	 * @param root cache root
	 * @param fingerprint pinned host fingerprint
	 */
	private void openState(Path root, String fingerprint) {
		if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
			throw new StoreValidationException("Host authority must be a lowercase SHA-256 fingerprint");
		}
		authorityFingerprint = fingerprint;
		stateFile = new DisconnectedStateFile(root, fingerprint, incident);
		try {
			state = stateFile.load();
			cutoff = stateFile.loadCutoff();
		} catch (RuntimeException failure) {
			stateFile.close();
			throw failure;
		}
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
	 * Reports whether this handle has a durable disconnected journal.
	 *
	 * @return true when disconnected storage was configured
	 */
	public boolean disconnectedStorageEnabled() {
		return stateFile != null;
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
			loadedSnapshots.put(data, token);
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
	public synchronized StoredIncident loadSnapshot(IncidentId id) {
		ensureOpen();
		requireShared(id);
		if (stateFile != null) {
			if (state != null && (!state.pending().isEmpty() || gateway == null)) {
				return localSnapshot();
			}
			try {
				IncidentService.ReadResult read = requireGateway().read(incident);
				AppData built = build(read);
				RevisionToken revision = token(read.cursor(), recordMap(read));
				commitState(new DisconnectedStateFile.State(built, read.records(), read.cursor(), built, revision,
						Instant.now(), state == null ? 0 : state.acceptedReceipts(),
						state == null ? AcceptedChanges.empty() : state.acceptedChanges(), List.of()));
				return localSnapshot();
			} catch (RemoteAccessException failure) {
				recordCredentialCutoff(failure.reason());
				throw failure;
			} catch (StoreUnavailableException failure) {
				if (state == null) {
					throw failure;
				}
				return localSnapshot();
			}
		}
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
	 * @throws StoreValidationException when the document or token is invalid
	 * @throws StoreUnavailableException when the host cannot be reached
	 */
	@Override
	public synchronized RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
		ensureOpen();
		requireShared(id);
		if (data == null || expected == null || !id.value().equals(data.getIncidentId())) {
			throw new StoreValidationException("Identity or expected revision is invalid");
		}
		if (data.getSchemaVersion() > AppData.CURRENT_SCHEMA_VERSION) {
			throw new StoreValidationException("Unsupported document schema");
		}
		if (stateFile != null) {
			return saveDisconnected(data, expected);
		}
		Base base = baseFor(expected);
		RecordDiff diff = RecordDiff.between(base.document(), data);
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
			cacheBase(token, new Base(held, records, after.cursor()));
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
	public synchronized List<IncidentSummary> list() {
		ensureOpen();
		if (stateFile != null && state != null) {
			StoredIncident local = loadSnapshot(incident);
			AppData built = local.data();
			return List.of(new IncidentSummary(gateway == null ? "Disconnected" : gateway.location(),
					built.getIncidentContext().getIncidentName(), built.getIapPhase(), built.getUpdatedAt(), incident,
					built.getForm201().getIncidentNumber(), local.revision()));
		}
		IncidentService.ReadResult read;
		try {
			read = requireGateway().read(incident);
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
	 * Exports every pending edit, including bases, drafts, conflict details and exact materialized batches.
	 *
	 * @param target external recovery destination
	 */
	public synchronized void exportOutbox(Path target) {
		ensureOpen();
		if (stateFile == null || state == null) {
			throw new StoreValidationException("No disconnected outbox is available");
		}
		try {
			stateFile.export(validateLocalExportTarget(target), state.pending());
		} catch (IOException failure) {
			throw new StorePersistenceException("Cannot export the disconnected outbox", failure);
		}
	}

	/**
	 * Rejects transfer destinations inside the private cache or symlink aliases.
	 *
	 * @param target requested destination
	 * @return canonical external destination
	 */
	@Override
	public Path validateLocalExportTarget(Path target) {
		if (stateFile == null) {
			return IncidentStore.super.validateLocalExportTarget(target);
		}
		try {
			Path absolute = target.toAbsolutePath().normalize();
			if (Files.isSymbolicLink(absolute)) {
				throw new StoreValidationException("A recovery destination cannot be a symbolic link");
			}
			Path resolved = absolute.getParent().toRealPath().resolve(absolute.getFileName());
			Path root = stateFile.realRoot();
			if (resolved.startsWith(root) || aliasesManagedFile(resolved, root)) {
				throw new StoreValidationException("Recovery exports cannot overwrite disconnected storage");
			}
			return resolved;
		} catch (IOException failure) {
			throw new StorePersistenceException("Cannot validate recovery destination", failure);
		}
	}

	/**
	 * Detects hard-link aliases to existing managed files.
	 *
	 * @param destination resolved transfer destination
	 * @param root private cache root
	 * @return true when the destination names a managed inode
	 * @throws IOException when filesystem identity cannot be checked
	 */
	private static boolean aliasesManagedFile(Path destination, Path root) throws IOException {
		if (!Files.exists(destination)) {
			return false;
		}
		try (var paths = Files.walk(root)) {
			for (Path managed : paths.filter(Files::isRegularFile).toList()) {
				if (Files.isSameFile(destination, managed)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Reports that a device cannot create incidents; the host does that administratively.
	 *
	 * @return {@code false}
	 */
	@Override
	public boolean supportsCreate() {
		return false;
	}

	/**
	 * Reports that a device cannot delete incidents.
	 *
	 * @return {@code false}
	 */
	@Override
	public boolean supportsDelete() {
		return false;
	}

	/**
	 * Reports that a device cannot import incidents into the host.
	 *
	 * @return {@code false}
	 */
	@Override
	public boolean supportsImport() {
		return false;
	}

	/**
	 * Describes the remote store without exposing host paths.
	 *
	 * @return descriptor naming the host address
	 */
	@Override
	public StoreDescriptor describe() {
		return new StoreDescriptor("Remote", gateway == null ? "Disconnected" : gateway.location());
	}

	/**
	 * Closes the transport and rejects later operations. Idempotent.
	 */
	@Override
	public synchronized void close() {
		if (!closed) {
			closed = true;
			try {
				if (gateway != null) {
					gateway.close();
				}
			} finally {
				if (stateFile != null) {
					stateFile.close();
				}
			}
		}
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
		if (stateFile != null && stateFile.failed()) {
			throw new StorePersistenceException("A local commit failed; reopen the incident before continuing", null);
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
	 * Returns detached durable work in submission order.
	 *
	 * @return immutable pending operation list
	 */
	public synchronized List<PendingOperation> pendingOperations() {
		ensureOpen();
		return state == null ? List.of() : state.pending();
	}

	/**
	 * Reports whether edits made during an approval request remain locally blocked.
	 *
	 * @return true when unreviewed work remains in the queue
	 */
	public synchronized boolean hasUnreviewedPending() {
		ensureOpen();
		return state != null && state.pending().stream()
				.anyMatch(operation -> cutoff.blocked().contains(operation.operationId()));
	}

	/**
	 * Prevents edits to the submitted approval snapshot while a decision is pending.
	 */
	private void requireApprovalNotPending() {
		if (cutoff.reason() != null) {
			throw new StoreValidationException("Approval is required before changing queued operations; export "
					+ "the outbox for recovery");
		}
	}

	/**
	 * Prevents a rewrite from assigning unapproved identifiers to held work.
	 */
	private void requireSafeQueueRewrite() {
		requireApprovalNotPending();
		if (hasUnreviewedPending()) {
			throw new StoreValidationException("This queue contains held work and cannot be rewritten "
					+ "safely; export the outbox for manual recovery");
		}
	}

	/**
	 * Protects an unreviewed head while allowing reviewed conflicts to be resolved.
	 */
	private void requireSafeHeadRewrite() {
		requireApprovalNotPending();
		if (state != null && !state.pending().isEmpty()
				&& cutoff.blocked().contains(state.pending().get(0).operationId())) {
			throw new StoreValidationException("This work was not reviewed; export it for manual recovery");
		}
	}

	/**
	 * Submits pending work in order. This method performs blocking I/O and must not run on the Swing event thread.
	 * A conflict or rejection remains durable and blocks later operations. Ambiguous failures leave the exact batch
	 * and operation ID intact for the next replay.
	 */
	public synchronized void replayPending() {
		ensureOpen();
		if (state != null && !state.pending().isEmpty() && cutoff.reason() != null) {
			throw new RemoteAccessException(cutoff.reason(),
					"Credential requires administrator approval; pending work is retained for export", null);
		}
		RemoteIncidentGateway transport = requireGateway();
		while (state != null && !state.pending().isEmpty()) {
			PendingOperation operation = state.pending().get(0);
			if (cutoff.blocked().contains(operation.operationId())) {
				return;
			}
			if (operation.status() != PendingStatus.PENDING) {
				return;
			}
			boolean firstAttempt = operation.batch() == null;
			if (firstAttempt) {
				List<IncidentService.RecordSubmission> edits;
				IncidentService.RecordBatch exact = null;
				List<IncidentService.FieldConflict> conflicts = List.of();
				try {
					Base base = new Base(operation.before(), indexRecords(state.records()), state.cursor());
					RecordDiff diff = RecordDiff.between(operation.before(), operation.draft());
					edits = submissions(base, operation.draft(), diff);
					if (!edits.isEmpty()) {
						IncidentService.RecordBatch validated = batch(state.revision(), edits);
						exact = new IncidentService.RecordBatch(incident, operation.operationId(),
								validated.records());
						conflicts = dependentConflicts(operation, edits, state.records());
					}
				} catch (StoreValidationException failure) {
					replaceHead(new PendingOperation(operation.operationId(), operation.before(), operation.draft(),
							null, PendingStatus.REJECTED, List.of(), failure.getMessage(), operation.reviewBase()));
					return;
				}
				if (edits.isEmpty()) {
					removeAcknowledged(operation, state.records(), state.cursor(), null);
					continue;
				}
				operation = new PendingOperation(operation.operationId(), operation.before(), operation.draft(), exact,
						conflicts.isEmpty() ? PendingStatus.PENDING : PendingStatus.CONFLICT, conflicts, null,
						operation.reviewBase());
				replaceHead(operation);
				if (!conflicts.isEmpty()) {
					return;
				}
			}
			IncidentService.SubmissionResult result;
			try {
				result = transport.submit(operation.batch());
			} catch (RemoteAccessException failure) {
				recordCredentialCutoff(failure.reason());
				throw failure;
			} catch (StoreValidationException failure) {
				replaceHead(new PendingOperation(operation.operationId(), operation.before(), operation.draft(),
						operation.batch(), PendingStatus.REJECTED, List.of(), failure.getMessage(),
						operation.reviewBase(), firstAttempt));
				return;
			}
			if (!operation.operationId().equals(result.operationId())) {
				throw new StorePersistenceException("Host returned a receipt for a different operation", null);
			}
			if (!result.applied()) {
				replaceHead(new PendingOperation(operation.operationId(), operation.before(), operation.draft(),
						operation.batch(), PendingStatus.CONFLICT, result.conflicts(), null, operation.reviewBase()));
				return;
			}
			List<IncidentService.VersionedRecord> accepted;
			try {
				accepted = acceptedRecords(operation, result, transport);
			} catch (RemoteAccessException failure) {
				recordCredentialCutoff(failure.reason());
				throw failure;
			}
			if (result.receivedAt() == null) {
				throw new StorePersistenceException("Host receipt has no receipt time", null);
			}
			removeAcknowledged(operation, accepted, result.cursor(), result.receivedAt());
		}
	}

	/**
	 * Detects fields changed remotely since the operator's original dependent-edit base.
	 *
	 * @param operation edit with its original before and after documents
	 * @param edits candidate submissions against accepted predecessor records
	 * @param currentRecords records to compare with the operator's base
	 * @return explicit conflicts requiring review before submission
	 */
	private List<IncidentService.FieldConflict> dependentConflicts(PendingOperation operation,
			List<IncidentService.RecordSubmission> edits, List<IncidentService.VersionedRecord> currentRecords) {
		RecordIndex before = RecordIndex.of(operation.before());
		RecordIndex after = RecordIndex.of(operation.draft());
		RecordIndex observed = RecordIndex.of(operation.reviewBase());
		Map<RecordKey, IncidentService.VersionedRecord> accepted = indexRecords(currentRecords);
		List<IncidentService.FieldConflict> conflicts = new ArrayList<>();
		for (IncidentService.RecordSubmission edit : edits) {
			IncidentService.VersionedRecord current = accepted.get(edit.key());
			if (edit.operation() == IncidentService.Operation.CREATE) {
				if (current != null
						&& IncidentService.mergeIndependentCreates(edit.content(), current.content()) == null) {
					conflicts.add(new IncidentService.FieldConflict(edit.key(), "$record", null, edit.content(),
							current.content()));
				}
				continue;
			}
			if (edit.operation() == IncidentService.Operation.REMOVE) {
				JsonNode seen = observed.record(edit.key());
				if (current != null && seen != null && !RecordJson.same(seen, current.content())) {
					conflicts.add(new IncidentService.FieldConflict(edit.key(), "$record", seen, null,
							current.content()));
				}
				continue;
			}
			if (current == null) {
				throw new StoreValidationException("A dependent edit references a missing record");
			}
			JsonNode prior = RootSettings.isRoot(edit.key()) ? RootSettings.snapshot(operation.before())
					: before.record(edit.key());
			JsonNode mine = RootSettings.isRoot(edit.key()) ? RootSettings.snapshot(operation.draft())
					: after.record(edit.key());
			JsonNode operatorBase = RootSettings.isRoot(edit.key()) ? RootSettings.snapshot(operation.reviewBase())
					: observed.record(edit.key());
			JsonNode theirs = current.content();
			if (prior == null || mine == null || operatorBase == null) {
				throw new StoreValidationException("A dependent edit has no original record");
			}
			java.util.Set<String> names = new java.util.HashSet<>();
			prior.fieldNames().forEachRemaining(names::add);
			mine.fieldNames().forEachRemaining(names::add);
			for (String name : names) {
				JsonNode original = operatorBase.get(name);
				JsonNode local = mine.get(name);
				JsonNode remote = theirs.get(name);
				if (!RecordJson.same(prior.get(name), local) && !RecordJson.same(original, remote)
						&& !RecordJson.same(local, remote)) {
					conflicts.add(new IncidentService.FieldConflict(edit.key(), name, original, local, remote));
				}
			}
		}
		return conflicts;
	}

	/**
	 * Refreshes the head conflict's displayed host values without submitting. If no conflicts remain, replaces the
	 * head with a newly identified edit rebased against the current host state. The new operation requires explicit
	 * {@link #confirmPending(String)} before replay can submit it.
	 * This method performs blocking host I/O and must not run on the Swing event thread.
	 *
	 * @param operationId conflicting operation identifier
	 * @return updated conflict entry for explicit review
	 */
	public synchronized PendingOperation refreshConflict(String operationId) {
		ensureOpen();
		requireSafeHeadRewrite();
		if (state == null || state.pending().isEmpty()) {
			throw new StoreValidationException("No conflicting operation exists");
		}
		PendingOperation operation = state.pending().get(0);
		if (!operation.operationId().equals(operationId) || operation.status() != PendingStatus.CONFLICT
				|| operation.batch() == null) {
			throw new StoreValidationException("The selected operation is not a materialized conflict");
		}
		IncidentService.ReadResult read = requireGateway().read(incident);
		List<IncidentService.FieldConflict> conflicts = dependentConflicts(operation, operation.batch().records(),
				read.records());
		archivePending(operationId);
		if (conflicts.isEmpty()) {
			replaceHead(new PendingOperation(operation.operationId(), operation.before(), operation.draft(),
					operation.batch(), PendingStatus.CONFLICT, List.of(), null, operation.reviewBase()));
			resolvePending(operationId, Map.of(), true);
			return state.pending().get(0);
		}
		PendingOperation updated = new PendingOperation(operation.operationId(), operation.before(), operation.draft(),
				operation.batch(), PendingStatus.CONFLICT, conflicts, null, operation.reviewBase());
		replaceHead(updated);
		return updated;
	}

	/**
	 * Explicitly releases a rebased operation for replay after the operator reviews it.
	 *
	 * @param operationId operation awaiting confirmation
	 */
	public synchronized void confirmPending(String operationId) {
		ensureOpen();
		requireApprovalNotPending();
		if (state == null || state.pending().isEmpty()) {
			throw new StoreValidationException("No operation awaits confirmation");
		}
		PendingOperation operation = state.pending().get(0);
		if (!operation.operationId().equals(operationId) || operation.status() != PendingStatus.REVIEW_REQUIRED) {
			throw new StoreValidationException("The selected operation does not await confirmation");
		}
		replaceHead(new PendingOperation(operation.operationId(), operation.before(), operation.draft(),
				operation.batch(), PendingStatus.PENDING, List.of(), null, operation.reviewBase()));
	}

	/**
	 * Restores the exact accepted versions from the host's ordered history, including no-op receipts.
	 *
	 * @param operation acknowledged operation
	 * @param result host receipt
	 * @param transport host transport
	 * @return record bases for dependent edits
	 */
	private List<IncidentService.VersionedRecord> acceptedRecords(PendingOperation operation,
			IncidentService.SubmissionResult result, RemoteIncidentGateway transport) {
		Map<RecordKey, IncidentService.VersionedRecord> records = indexRecords(state.records());
		Map<RecordKey, IncidentService.RecordSubmission> submitted = new HashMap<>();
		operation.batch().records().forEach(item -> submitted.put(item.key(), item));
		if (result.versions().size() != submitted.size()) {
			throw new StorePersistenceException("Host receipt has incomplete record versions", null);
		}
		List<RecordVersionStore.RecordChange> changes = transport.changesSince(incident, 0).changes();
		for (IncidentService.VersionAssignment assignment : result.versions()) {
			IncidentService.RecordSubmission item = submitted.remove(assignment.key());
			if (item == null) {
				throw new StorePersistenceException("Host receipt contains unexpected record versions", null);
			}
			if (item.operation() == IncidentService.Operation.REMOVE) {
				records.remove(item.key());
				continue;
			}
			IncidentService.VersionedRecord existing = records.get(assignment.key());
			if (existing != null && existing.version() == assignment.version()) {
				continue;
			}
			RecordVersionStore.RecordChange change = changes.stream()
					.filter(value -> value.key().equals(assignment.key()) && value.version() == assignment.version()
							&& value.cursor() <= result.cursor())
					.findFirst().orElseThrow(() -> new StorePersistenceException(
							"Host did not retain the acknowledged record version", null));
			records.put(item.key(), new IncidentService.VersionedRecord(item.key(), assignment.version(),
					change.cursor(), change.receivedAt(), change.content(), item.parent(), item.parentField()));
		}
		return List.copyOf(records.values());
	}

	/**
	 * Archives and discards the sole pending edit after explicit user confirmation.
	 *
	 * @param operationId identifier of the edit to discard
	 */
	public synchronized void discardPending(String operationId) {
		ensureOpen();
		requireApprovalNotPending();
		if (state == null || state.pending().size() != 1
				|| !state.pending().get(0).operationId().equals(operationId)) {
			throw new StoreValidationException("Only a sole pending edit can be discarded safely");
		}
		PendingOperation operation = state.pending().get(0);
		if (operation.batch() != null && !(operation.status() == PendingStatus.REJECTED
				&& operation.definitivelyRejected())) {
			throw new StoreValidationException("An ambiguously submitted edit cannot be discarded");
		}
		archivePending(operationId);
		RevisionToken revision = new RevisionToken("local/" + UUID.randomUUID());
		commitState(new DisconnectedStateFile.State(state.base(), state.records(), state.cursor(), state.base(),
				revision, state.confirmedAt(), state.acceptedReceipts(), state.acceptedChanges(), List.of()));
	}

	/**
	 * Makes a rejected, not conflict-resolved operation eligible for an explicit retry after re-pairing.
	 *
	 * @param operationId operation to retry
	 */
	public synchronized void retryRejected(String operationId) {
		ensureOpen();
		requireApprovalNotPending();
		if (state == null || state.pending().isEmpty()) {
			throw new StoreValidationException("No rejected operation exists");
		}
		PendingOperation operation = state.pending().get(0);
		if (!operation.operationId().equals(operationId) || operation.status() != PendingStatus.REJECTED) {
			throw new StoreValidationException("The selected operation is not rejected");
		}
		replaceHead(new PendingOperation(operation.operationId(), operation.before(), operation.draft(),
				operation.batch(), PendingStatus.PENDING, List.of(), null, operation.reviewBase()));
	}

	/**
	 * Replaces a rejected queue with one new edit built from the current local draft, so that an operator who has
	 * corrected the cause in the editors can unblock synchronization. The rejected operations are first archived,
	 * exactly, in owner-only storage next to the journal, and remain available through {@link #exportOutbox(Path)}
	 * until then. A materialized head can be replaced only after a definitive first-attempt rejection; otherwise an
	 * earlier submission may have succeeded. At least one later edit (the correction) must exist. The new operation
	 * has a fresh identity and the head's original base.
	 *
	 * @param operationId identifier of the rejected head operation
	 * @return identifier of the replacement operation
	 * @throws StoreValidationException when the head is not rejected or no correcting edit has been saved
	 */
	public synchronized String replaceRejected(String operationId) {
		ensureOpen();
		requireSafeQueueRewrite();
		if (state == null || state.pending().isEmpty()) {
			throw new StoreValidationException("No rejected operation exists");
		}
		PendingOperation head = state.pending().get(0);
		if (!head.operationId().equals(operationId) || head.status() != PendingStatus.REJECTED) {
			throw new StoreValidationException("The selected operation is not rejected");
		}
		if (head.batch() != null && !head.definitivelyRejected()) {
			throw new StoreValidationException("An ambiguously submitted edit cannot be replaced");
		}
		if (state.pending().size() < 2) {
			throw new StoreValidationException("Correct the cause in the editors and save before replacing the "
					+ "rejected edit");
		}
		archivePending(operationId);
		String replacementId = "offline-" + UUID.randomUUID();
		PendingOperation replacement = new PendingOperation(replacementId, head.before(), state.draft(), null,
				PendingStatus.PENDING, List.of(), null, head.reviewBase());
		commitState(new DisconnectedStateFile.State(state.base(), state.records(), state.cursor(), state.draft(),
				state.revision(), state.confirmedAt(), state.acceptedReceipts(), state.acceptedChanges(),
				List.of(replacement)));
		return replacementId;
	}

	/**
	 * Splits a safely rejected head into independently bounded operations, preserving dependent edits.
	 * Records related through changed parents or explicit record IDs remain together. If the original edit cannot
	 * be projected exactly, or a dependency group exceeds a limit, the queue is left untouched for correction or
	 * export.
	 *
	 * @param operationId identifier of the rejected head
	 * @return identifiers of the replacement operations in replay order
	 * @throws StoreValidationException when the head is unsafe to split or cannot be split without loss
	 */
	public synchronized List<String> splitRejected(String operationId) {
		ensureOpen();
		requireSafeHeadRewrite();
		if (state == null || state.pending().isEmpty()) {
			throw new StoreValidationException("No rejected operation exists");
		}
		PendingOperation head = state.pending().get(0);
		if (!head.operationId().equals(operationId) || head.status() != PendingStatus.REJECTED
				|| (head.batch() != null && !head.definitivelyRejected())) {
			throw new StoreValidationException("Only a safely rejected edit can be split");
		}
		AppData before = head.before();
		AppData target = head.draft();
		RecordIndex oldIndex = RecordIndex.of(before);
		RecordIndex newIndex = RecordIndex.of(target);
		RecordDiff diff = RecordDiff.between(before, target);
		java.util.Set<RecordKey> changed = new java.util.TreeSet<>(diff.created());
		changed.addAll(diff.changed().keySet());
		changed.addAll(diff.removed());
		if (!RecordJson.same(RootSettings.snapshot(before), RootSettings.snapshot(target))) {
			changed.add(RootSettings.key());
		}
		Map<String, java.util.Set<RecordKey>> byId = new HashMap<>();
		for (RecordKey key : changed) {
			byId.computeIfAbsent(key.id(), ignored -> new java.util.HashSet<>()).add(key);
		}
		Map<RecordKey, java.util.Set<RecordKey>> references = new HashMap<>();
		for (RecordKey key : changed) {
			java.util.Set<RecordKey> linked = new java.util.HashSet<>();
			collectSplitReferences(RootSettings.isRoot(key) ? RootSettings.snapshot(before) : oldIndex.record(key),
					byId, linked);
			collectSplitReferences(RootSettings.isRoot(key) ? RootSettings.snapshot(target) : newIndex.record(key),
					byId, linked);
			linked.remove(key);
			references.put(key, linked);
		}
		Map<RecordKey, java.util.Set<RecordKey>> groups = new TreeMap<>();
		for (RecordKey key : changed) {
			RecordKey group = key;
			for (RecordIndex index : List.of(oldIndex, newIndex)) {
				RecordKey parent = index.parentOf(key);
				while (parent != null) {
					if (changed.contains(parent) && parent.compareTo(group) < 0) {
						group = parent;
					}
					parent = index.parentOf(parent);
				}
			}
			groups.computeIfAbsent(group, ignored -> new java.util.TreeSet<>()).add(key);
		}
		// Merge transitively related groups, including cases where lexical ordering differs from the tree.
		boolean merged;
		do {
			merged = false;
			List<java.util.Set<RecordKey>> values = new ArrayList<>(groups.values());
			outer: for (int i = 0; i < values.size(); i++) {
				for (int j = i + 1; j < values.size(); j++) {
					if (relatedGroups(values.get(i), values.get(j), oldIndex, newIndex, references)) {
						values.get(i).addAll(values.get(j));
						groups.values().remove(values.get(j));
						merged = true;
						break outer;
					}
				}
			}
		} while (merged);

		List<PendingOperation> replacements = new ArrayList<>();
		AppData chunkBefore = before;
		AppData projected = before;
		int groupIndex = 0;
		for (java.util.Set<RecordKey> group : groups.values()) {
			AppData candidate = projectSplit(projected, target, group, newIndex);
			boolean lastGroup = ++groupIndex == groups.size();
			boolean forceSplit = lastGroup && replacements.isEmpty()
					&& !changedKeys(chunkBefore, projected).isEmpty();
			if (forceSplit || !splitFits(chunkBefore, candidate)) {
				if (changedKeys(chunkBefore, projected).isEmpty()) {
					throw new StoreValidationException("A record or dependent group exceeds the batch limits");
				}
				replacements.add(splitOperation(chunkBefore, projected, head.reviewBase()));
				chunkBefore = projected;
				if (!splitFits(chunkBefore, candidate)) {
					throw new StoreValidationException("A record or dependent group exceeds the batch limits");
				}
			}
			projected = candidate;
		}
		if (!changedKeys(projected, target).isEmpty() || !sameParents(projected, target)) {
			throw new StoreValidationException("This edit cannot be split without changing its records");
		}
		if (!changedKeys(chunkBefore, projected).isEmpty()) {
			replacements.add(splitOperation(chunkBefore, target, head.reviewBase()));
		}
		if (replacements.size() < 2) {
			throw new StoreValidationException("This rejected edit cannot be split into smaller batches");
		}
		archivePending(operationId);
		List<PendingOperation> pending = new ArrayList<>(replacements);
		pending.addAll(state.pending().subList(1, state.pending().size()));
		commitState(new DisconnectedStateFile.State(state.base(), state.records(), state.cursor(), state.draft(),
				state.revision(), state.confirmedAt(), state.acceptedReceipts(), state.acceptedChanges(), pending));
		return replacements.stream().map(PendingOperation::operationId).toList();
	}

	/**
	 * Archives the complete current queue before superseding any work.
	 *
	 * @param operationId safe operation identifier used as archive label
	 */
	private void archivePending(String operationId) {
		try {
			stateFile.archive(operationId + "-" + UUID.randomUUID(), state.pending());
		} catch (IOException failure) {
			throw new StorePersistenceException("Cannot archive the pending operations", failure);
		}
	}

	/**
	 * Checks whether two sets contain related records in either document.
	 *
	 * @param first first group
	 * @param second second group
	 * @param oldIndex index before the edit
	 * @param newIndex index after the edit
	 * @param references explicit links between changed records
	 * @return true when the groups must remain together
	 */
	private static boolean relatedGroups(java.util.Set<RecordKey> first, java.util.Set<RecordKey> second,
			RecordIndex oldIndex, RecordIndex newIndex, Map<RecordKey, java.util.Set<RecordKey>> references) {
		for (RecordKey key : first) {
			if (references.get(key).stream().anyMatch(second::contains)) {
				return true;
			}
			for (RecordIndex index : List.of(oldIndex, newIndex)) {
				RecordKey parent = index.parentOf(key);
				while (parent != null) {
					if (second.contains(parent)) {
						return true;
					}
					parent = index.parentOf(parent);
				}
			}
		}
		for (RecordKey key : second) {
			if (references.get(key).stream().anyMatch(first::contains)) {
				return true;
			}
			for (RecordIndex index : List.of(oldIndex, newIndex)) {
				RecordKey parent = index.parentOf(key);
				while (parent != null) {
					if (first.contains(parent)) {
						return true;
					}
					parent = index.parentOf(parent);
				}
			}
		}
		return false;
	}

	/**
	 * Finds textual record IDs anywhere in a record snapshot, conservatively treating each as a dependency.
	 *
	 * @param value snapshot subtree
	 * @param byId changed keys indexed by ID
	 * @param result receives referenced keys
	 */
	private static void collectSplitReferences(JsonNode value, Map<String, java.util.Set<RecordKey>> byId,
			java.util.Set<RecordKey> result) {
		if (value == null) {
			return;
		}
		if (value.isTextual()) {
			result.addAll(byId.getOrDefault(value.textValue(), java.util.Set.of()));
		} else if (value.isContainerNode()) {
			value.elements().forEachRemaining(child -> collectSplitReferences(child, byId, result));
		}
	}

	/**
	 * Projects one dependent group into a detached intermediate document.
	 *
	 * @param source preceding intermediate document
	 * @param target original edited document
	 * @param group record keys to apply together
	 * @param targetIndex indexed target
	 * @return intermediate document
	 */
	private AppData projectSplit(AppData source, AppData target, java.util.Set<RecordKey> group,
			RecordIndex targetIndex) {
		AppData result = FullDocumentSnapshot.detach(source);
		java.util.Set<RecordKey> removed = new java.util.HashSet<>();
		List<RecordVersionStore.RecordHistory> histories = new ArrayList<>();
		for (RecordKey key : group) {
			if (RootSettings.isRoot(key)) {
				RootSettings.apply(result, RootSettings.snapshot(target));
			} else if (targetIndex.record(key) == null) {
				removed.add(key);
			} else {
				histories.add(new RecordVersionStore.RecordHistory(key, targetIndex.parentOf(key),
						targetIndex.parentFieldOf(key), List.of(new RecordVersionStore.RecordVersion(1, 0,
								Instant.EPOCH, targetIndex.record(key)))));
			}
		}
		bridge.remove(result, removed);
		bridge.apply(result, histories, true);
		java.util.Set<RecordKey> actual = changedKeys(source, result);
		if (!actual.equals(group)) {
			throw new StoreValidationException("This edit has dependencies that cannot be split safely");
		}
		return result;
	}

	/**
	 * Returns record and root-setting differences between two intermediate documents.
	 *
	 * @param before preceding document
	 * @param after subsequent document
	 * @return changed keys
	 */
	private static java.util.Set<RecordKey> changedKeys(AppData before, AppData after) {
		RecordDiff diff = RecordDiff.between(before, after);
		java.util.Set<RecordKey> keys = new java.util.TreeSet<>(diff.created());
		keys.addAll(diff.changed().keySet());
		keys.addAll(diff.removed());
		if (!RecordJson.same(RootSettings.snapshot(before), RootSettings.snapshot(after))) {
			keys.add(RootSettings.key());
		}
		return keys;
	}

	/**
	 * Checks that projection retained every record's containing relationship.
	 *
	 * @param first projected document
	 * @param second original draft
	 * @return true when all parent keys and fields match
	 */
	private static boolean sameParents(AppData first, AppData second) {
		RecordIndex left = RecordIndex.of(first);
		RecordIndex right = RecordIndex.of(second);
		if (!left.records().keySet().equals(right.records().keySet())) {
			return false;
		}
		for (RecordKey key : left.records().keySet()) {
			if (!Objects.equals(left.parentOf(key), right.parentOf(key))
					|| !Objects.equals(left.parentFieldOf(key), right.parentFieldOf(key))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Tests the same count and byte limits used by replay for a projected chunk.
	 *
	 * @param before chunk base
	 * @param after chunk draft
	 * @return whether the chunk fits
	 */
	private boolean splitFits(AppData before, AppData after) {
		try {
			List<IncidentService.RecordSubmission> edits = submissions(
					new Base(before, indexRecords(state.records()), state.cursor()), after,
					RecordDiff.between(before, after));
			batch(state.revision(), edits);
			ObjectNode request = WIRE_MAPPER.createObjectNode();
			request.put("operationId", "offline-" + "0".repeat(36));
			ArrayNode records = request.putArray("records");
			edits.forEach(edit -> records.add(WIRE_MAPPER.valueToTree(edit)));
			return request.toString().getBytes(StandardCharsets.UTF_8).length <= HostProtocol.MAX_REQUEST_BYTES;
		} catch (StoreValidationException failure) {
			return false;
		}
	}

	/**
	 * Creates one unmaterialized replacement while retaining the operator's original conflict base.
	 *
	 * @param before preceding document
	 * @param after projected document
	 * @param reviewBase original observed document
	 * @return new pending operation
	 */
	private static PendingOperation splitOperation(AppData before, AppData after, AppData reviewBase) {
		return new PendingOperation("offline-" + UUID.randomUUID(), before, after, null,
				PendingStatus.PENDING, List.of(), null, reviewBase);
	}

	/**
	 * Resolves the head conflict against a freshly read host state and preserves dependent local edits.
	 * The original queue is archived before any local values are superseded.
	 * Each recorded conflict must have an explicit choice. If a reviewed field has changed since the conflict,
	 * or another locally edited field now conflicts, resolution fails without changing the journal.
	 * This method performs blocking host I/O and must not run on the Swing event thread.
	 *
	 * @param operationId conflicting operation identifier
	 * @param choices explicit choices keyed by record and field
	 */
	public synchronized void resolvePending(String operationId, Map<ConflictField, ConflictChoice> choices) {
		resolvePending(operationId, choices, false);
	}

	/**
	 * Replaces the conflicting head, optionally requiring confirmation before replay.
	 *
	 * @param operationId conflicting operation identifier
	 * @param choices explicit conflict decisions
	 * @param requireConfirmation whether replay must wait for operator confirmation
	 */
	private void resolvePending(String operationId, Map<ConflictField, ConflictChoice> choices,
			boolean requireConfirmation) {
		ensureOpen();
		requireSafeHeadRewrite();
		Objects.requireNonNull(choices, "choices");
		if (state == null || state.pending().isEmpty()) {
			throw new StoreValidationException("No conflicting operation exists");
		}
		PendingOperation head = state.pending().get(0);
		if (!head.operationId().equals(operationId) || head.status() != PendingStatus.CONFLICT
				|| head.batch() == null) {
			throw new StoreValidationException("The selected operation is not a materialized conflict");
		}
		Map<ConflictField, IncidentService.FieldConflict> conflicts = new HashMap<>();
		for (IncidentService.FieldConflict conflict : head.conflicts()) {
			conflicts.put(new ConflictField(conflict.key(), conflict.field()), conflict);
		}
		if (!choices.keySet().equals(conflicts.keySet()) || choices.values().stream().anyMatch(Objects::isNull)) {
			throw new StoreValidationException("Every conflicting field requires an explicit choice");
		}
		IncidentService.ReadResult read = requireGateway().read(incident);
		Map<RecordKey, IncidentService.VersionedRecord> remote = recordMap(read);
		Map<RecordKey, IncidentService.VersionedRecord> original = indexRecords(state.records());
		List<IncidentService.RecordSubmission> resolved = new ArrayList<>();
		for (IncidentService.RecordSubmission item : head.batch().records()) {
			IncidentService.VersionedRecord current = remote.get(item.key());
			IncidentService.VersionedRecord old = original.get(item.key());
			ConflictField collision = new ConflictField(item.key(), "$record");
			if (item.operation() == IncidentService.Operation.CREATE) {
				if (current == null) {
					resolved.add(item);
				} else if (conflicts.containsKey(collision)) {
					verifyConflict(conflicts.get(collision), current.content());
					if (choices.get(collision) == ConflictChoice.MINE) {
						resolved.add(new IncidentService.RecordSubmission(item.key(), current.parent(),
								current.parentField(), current.version(), IncidentService.Operation.UPDATE,
								item.content()));
					}
				} else if (IncidentService.mergeIndependentCreates(item.content(), current.content()) != null) {
					resolved.add(item);
				} else {
					throw new StoreValidationException(HOST_CHANGED);
				}
				continue;
			}
			if (item.operation() == IncidentService.Operation.REMOVE) {
				if (current == null) {
					continue;
				}
				if (conflicts.containsKey(collision)) {
					verifyConflict(conflicts.get(collision), current.content());
					if (choices.get(collision) == ConflictChoice.THEIRS) {
						continue;
					}
				} else if (old == null || !RecordJson.same(old.content(), current.content())) {
					throw new StoreValidationException(HOST_CHANGED);
				}
				resolved.add(new IncidentService.RecordSubmission(item.key(), current.parent(),
						current.parentField(), current.version(), IncidentService.Operation.REMOVE,
						current.content()));
				continue;
			}
			if (old == null || current == null) {
				throw new StoreValidationException("A record needed for conflict review is missing");
			}
			ObjectNode merged = (ObjectNode) current.content();
			JsonNode prior = old.content();
			JsonNode mine = item.content();
			java.util.Set<String> names = new java.util.HashSet<>();
			prior.fieldNames().forEachRemaining(names::add);
			mine.fieldNames().forEachRemaining(names::add);
			for (String name : names) {
				JsonNode before = prior.get(name);
				JsonNode local = mine.get(name);
				JsonNode theirs = current.content().get(name);
				ConflictField field = new ConflictField(item.key(), name);
				if (conflicts.containsKey(field)) {
					verifyConflict(conflicts.get(field), theirs);
					if (choices.get(field) == ConflictChoice.THEIRS) {
						continue;
					}
				} else if (!RecordJson.same(before, local) && !RecordJson.same(before, theirs)
						&& !RecordJson.same(local, theirs)) {
					throw new StoreValidationException(HOST_CHANGED);
				}
				if (!RecordJson.same(before, local)) {
					if (local == null) {
						merged.remove(name);
					} else {
						merged.set(name, local.deepCopy());
					}
				}
			}
			resolved.add(new IncidentService.RecordSubmission(item.key(), current.parent(), current.parentField(),
					current.version(), item.operation(), merged));
		}
		AppData base = build(read);
		AppData draft = applyLocal(base, resolved);
		List<PendingOperation> pending = new ArrayList<>();
		pending.add(new PendingOperation("offline-" + UUID.randomUUID(), base, draft, null,
				requireConfirmation ? PendingStatus.REVIEW_REQUIRED : PendingStatus.PENDING, List.of(), null));
		Map<RecordKey, IncidentService.VersionedRecord> working = new HashMap<>(remote);
		updateWorkingRecords(working, resolved, read.cursor());
		for (int index = 1; index < state.pending().size(); index++) {
			PendingOperation later = state.pending().get(index);
			if (later.batch() != null || later.status() != PendingStatus.PENDING) {
				throw new StoreValidationException("Dependent operation has already been submitted");
			}
			RecordDiff diff = RecordDiff.between(later.before(), later.draft());
			List<IncidentService.RecordSubmission> edits = submissions(new Base(later.before(), working,
					read.cursor()), later.draft(), diff);
			AppData next = applyLocal(draft, edits);
			pending.add(new PendingOperation(later.operationId(), draft, next, null, PendingStatus.PENDING,
					List.of(), null, later.reviewBase()));
			updateWorkingRecords(working, edits, read.cursor());
			draft = next;
		}
		archivePending(operationId);
		commitState(new DisconnectedStateFile.State(base, read.records(), read.cursor(), draft,
				new RevisionToken("local/" + UUID.randomUUID()), Instant.now(), state.acceptedReceipts(),
				state.acceptedChanges(), pending));
	}

	/**
	 * Refuses a decision when the reviewed host value is no longer current.
	 *
	 * @param conflict previously returned host conflict
	 * @param current current host value
	 */
	private static void verifyConflict(IncidentService.FieldConflict conflict, JsonNode current) {
		if (!RecordJson.same(conflict.theirs(), current)) {
			throw new StoreValidationException(HOST_CHANGED);
		}
	}

	/**
	 * Applies record submissions to a detached document without contacting the host.
	 *
	 * @param source document before changes
	 * @param edits full record snapshots to apply
	 * @return detached updated document
	 */
	private AppData applyLocal(AppData source, List<IncidentService.RecordSubmission> edits) {
		AppData result = FullDocumentSnapshot.detach(source);
		List<RecordVersionStore.RecordHistory> histories = new ArrayList<>();
		java.util.Set<RecordKey> removed = new java.util.HashSet<>();
		for (IncidentService.RecordSubmission edit : edits) {
			if (edit.operation() == IncidentService.Operation.REMOVE) {
				removed.add(edit.key());
				continue;
			}
			histories.add(new RecordVersionStore.RecordHistory(edit.key(), edit.parent(), edit.parentField(),
					List.of(new RecordVersionStore.RecordVersion(1, 0, Instant.now(), edit.content()))));
		}
		bridge.apply(result, histories, true);
		bridge.remove(result, removed);
		return result;
	}

	/**
	 * Advances synthetic record content for rebasing unsent dependent edits.
	 *
	 * @param records working records
	 * @param edits applied record snapshots
	 * @param cursor current host cursor
	 */
	private static void updateWorkingRecords(Map<RecordKey, IncidentService.VersionedRecord> records,
			List<IncidentService.RecordSubmission> edits, long cursor) {
		for (IncidentService.RecordSubmission edit : edits) {
			if (edit.operation() == IncidentService.Operation.REMOVE) {
				records.remove(edit.key());
				continue;
			}
			IncidentService.VersionedRecord old = records.get(edit.key());
			records.put(edit.key(), new IncidentService.VersionedRecord(edit.key(), old == null ? 1 : old.version(),
					cursor, Instant.now(), edit.content(), edit.parent(), edit.parentField()));
		}
	}

	/**
	 * Saves a local edit and queue atomically before reporting success.
	 *
	 * @param data edited local document
	 * @param expected current local revision
	 * @return new local revision
	 */
	private RevisionToken saveDisconnected(AppData data, RevisionToken expected) {
		if (state == null) {
			throw new StoreValidationException("Load the incident before editing it");
		}
		AppData before = state.draft();
		if (!state.revision().equals(expected)) {
			Base held;
			synchronized (cacheLock) {
				held = bases.get(expected);
			}
			if (held == null || !state.pending().isEmpty()) {
				throw new StoreConflictException("The local incident has changed", expected, state.revision());
			}
			before = held.document();
		}
		RecordDiff diff = RecordDiff.between(before, data);
		if (!state.revision().equals(expected) && diff.created().isEmpty() && diff.changed().isEmpty()
				&& diff.removed().isEmpty() && RecordJson.same(RootSettings.snapshot(before), RootSettings.snapshot(data))) {
			synchronized (cacheLock) {
				issued.put(data, expected);
			}
			return expected;
		}
		List<PendingOperation> pending = new ArrayList<>(state.pending());
		RevisionToken revision = new RevisionToken("local/" + UUID.randomUUID());
		String operationId = "offline-" + UUID.randomUUID();
		if (hasUnreviewedPending()) {
			List<String> blocked = new ArrayList<>(cutoff.blocked());
			blocked.add(operationId);
			DisconnectedStateFile.Cutoff next = new DisconnectedStateFile.Cutoff(cutoff.reason(), blocked,
					cutoff.reviewed());
			stateFile.saveCutoff(next);
			cutoff = next;
		}
		pending.add(new PendingOperation(operationId, before, data, null,
				PendingStatus.PENDING, List.of(), null));
		commitState(new DisconnectedStateFile.State(state.base(), state.records(), state.cursor(),
				FullDocumentSnapshot.detach(data), revision, state.confirmedAt(), state.acceptedReceipts(),
				state.acceptedChanges(), pending));
		synchronized (cacheLock) {
			latestSavedRevision = revision;
			cacheBase(revision, new Base(FullDocumentSnapshot.detach(data), indexRecords(state.records()),
					state.cursor()));
			issued.put(data, revision);
		}
		return revision;
	}

	/**
	 * Returns the durable local draft detached from the journal.
	 *
	 * @return local snapshot and token
	 */
	private StoredIncident localSnapshot() {
		AppData data = FullDocumentSnapshot.detach(state.draft());
		synchronized (cacheLock) {
			loadedSnapshots.put(data, state.revision());
			cacheBase(state.revision(), new Base(FullDocumentSnapshot.detach(state.draft()),
					indexRecords(state.records()), state.cursor()));
			issued.put(data, state.revision());
		}
		return new StoredIncident(data, state.revision());
	}

	/**
	 * Indexes a persisted record collection.
	 *
	 * @param records record snapshots
	 * @return snapshots indexed by identity
	 */
	private static Map<RecordKey, IncidentService.VersionedRecord> indexRecords(
			List<IncidentService.VersionedRecord> records) {
		Map<RecordKey, IncidentService.VersionedRecord> indexed = new HashMap<>();
		records.forEach(record -> indexed.put(record.key(), record));
		return indexed;
	}

	/**
	 * Replaces the first pending operation in the journal.
	 *
	 * @param operation replacement operation
	 */
	private void replaceHead(PendingOperation operation) {
		List<PendingOperation> pending = new ArrayList<>(state.pending());
		pending.set(0, operation);
		commitState(new DisconnectedStateFile.State(state.base(), state.records(), state.cursor(), state.draft(),
				state.revision(), state.confirmedAt(), state.acceptedReceipts(), state.acceptedChanges(), pending));
	}

	/**
	 * Removes an acknowledged operation without replacing the visible local draft.
	 *
	 * @param operation acknowledged edit
	 * @param records accepted record versions
	 * @param cursor receipt cursor
	 * @param receivedAt host receipt time, or null when no host submission was necessary
	 */
	private void removeAcknowledged(PendingOperation operation, List<IncidentService.VersionedRecord> records,
			long cursor, Instant receivedAt) {
		List<PendingOperation> pending = new ArrayList<>(state.pending());
		pending.remove(0);
		Instant confirmedAt = receivedAt != null ? Instant.now() : state.confirmedAt();
		commitState(new DisconnectedStateFile.State(operation.draft(), records, cursor, state.draft(),
				state.revision(), confirmedAt, state.acceptedReceipts() + (receivedAt != null ? 1 : 0),
				receivedAt != null ? state.acceptedChanges().withReceipt(operation, receivedAt) : state.acceptedChanges(),
				pending));
	}

	/**
	 * Persists state before publishing it in memory.
	 *
	 * @param replacement complete state
	 */
	private void commitState(DisconnectedStateFile.State replacement) {
		stateFile.save(replacement);
		state = replacement;
	}

	/**
	 * Requires an attached host transport.
	 *
	 * @return attached gateway
	 */
	private RemoteIncidentGateway requireGateway() {
		if (gateway == null) {
			throw new StoreUnavailableException("No host is attached");
		}
		return gateway;
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
			cacheBase(token, new Base(built, records, cursor));
			issued.put(caller, token);
		}
		return new StoredIncident(caller, token);
	}

	/**
	 * Bounds cached bases while retaining active loaded documents and the latest accepted local-save base.
	 * The caller must hold {@code cacheLock}.
	 *
	 * @param token snapshot identity
	 * @param base snapshot and record bases
	 */
	private void cacheBase(RevisionToken token, Base base) {
		bases.put(token, base);
		java.util.Set<RevisionToken> pinned = new java.util.HashSet<>();
		if (stateFile != null) {
			pinned.addAll(loadedSnapshots.values());
		}
		if (latestSavedRevision != null) {
			pinned.add(latestSavedRevision);
		}
		if (stateFile != null && state != null) {
			pinned.add(state.revision());
		}
		while (bases.size() > MAX_CACHED_BASES) {
			RevisionToken evict = bases.keySet().stream().filter(key -> !pinned.contains(key)).findFirst()
					.orElse(null);
			if (evict == null) {
				break;
			}
			bases.remove(evict);
		}
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
			bridge.stabilizeDefaults(data, histories);
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
		for (RecordKey key : diff.removed()) {
			IncidentService.VersionedRecord held = base.records().get(key);
			if (held != null) {
				result.add(new IncidentService.RecordSubmission(key, held.parent(), held.parentField(),
						held.version(), IncidentService.Operation.REMOVE, held.content()));
			}
		}
		rootSubmission(base, edited).ifPresent(result::add);
		result.sort(Comparator.comparing(IncidentService.RecordSubmission::key));
		return result;
	}

	/**
	 * Derives the submission for incident-wide settings, such as the IAP phase, incident mode and time zone, which
	 * no identified record contains and which therefore travel as the virtual root-settings record.
	 *
	 * @param base snapshot the edit started from
	 * @param edited edited document
	 * @return the root-settings update, or empty when none of those values changed
	 * @throws StoreValidationException when the host does not hold the root-settings record
	 */
	private java.util.Optional<IncidentService.RecordSubmission> rootSubmission(Base base, AppData edited) {
		JsonNode before = RootSettings.snapshot(base.document());
		ObjectNode after = RootSettings.snapshot(edited);
		List<String> changed = new ArrayList<>();
		for (String name : RootSettings.propertyNames()) {
			if (!RecordJson.same(before.get(name), after.get(name))) {
				changed.add(name);
			}
		}
		if (changed.isEmpty()) {
			return java.util.Optional.empty();
		}
		IncidentService.VersionedRecord held = base.records().get(RootSettings.key());
		if (held == null) {
			throw new StoreValidationException("The host does not hold the incident-wide settings; reload the "
					+ "incident before saving");
		}
		ObjectNode content = (ObjectNode) held.content().deepCopy();
		for (String name : changed) {
			JsonNode value = after.get(name);
			if (value == null) {
				content.remove(name);
			} else {
				content.set(name, value.deepCopy());
			}
		}
		return java.util.Optional.of(new IncidentService.RecordSubmission(RootSettings.key(), null, null,
				held.version(), IncidentService.Operation.UPDATE, content));
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
		if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BATCH_BYTES) {
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
