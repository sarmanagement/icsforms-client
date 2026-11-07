/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.EnumMap;

/** Lockless in-memory server double with opaque tokens and no file identities. */
public final class RemoteIncidentStore implements IncidentStore {
	private static final ObjectMapper COPIER = new ObjectMapper().registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
	private final Map<IncidentId, StoredIncident> documents;
	private final Set<IncidentId> retired;
	private final Map<AppData, RevisionToken> callerTokens = new IdentityHashMap<>();
	private final Map<ControllableIncidentStore.Operation, RuntimeException> failures =
			new EnumMap<>(ControllableIncidentStore.Operation.class);
	private boolean closed;

	/** Opens an isolated pathless server fixture. */
	public RemoteIncidentStore() {
		this(new HashMap<>(), new HashSet<>());
	}

	/**
	 * Opens a handle over shared server state.
	 *
	 * @param documents persistent server documents
	 * @param retired tombstoned identities
	 */
	RemoteIncidentStore(Map<IncidentId, StoredIncident> documents, Set<IncidentId> retired) {
		this.documents = documents;
		this.retired = retired;
	}

	/**
	 * Fails one operation before commit.
	 *
	 * @param operation operation to fail
	 */
	public void failNext(ControllableIncidentStore.Operation operation) {
		failNext(operation, new StorePersistenceException("Scheduled server failure", null));
	}

	/**
	 * Fails the next operation of a type with a caller-selected exception, before mutation.
	 *
	 * @param operation operation to fail once
	 * @param failure failure to surface
	 */
	public void failNext(ControllableIncidentStore.Operation operation, RuntimeException failure) {
		synchronized (documents) {
			failures.put(operation, failure);
		}
	}

	/** {@inheritDoc} */
	@Override
	public IncidentId create(AppData data) {
		synchronized (documents) {
			before(ControllableIncidentStore.Operation.CREATE);
			AppData candidate = copy(data);
			IncidentId id = candidate.getIncidentId() == null ? IncidentId.newId()
					: IncidentId.of(candidate.getIncidentId());
			if (documents.containsKey(id) || retired.contains(id)) {
				throw new DuplicateIncidentException("Identity already assigned: " + id);
			}
			candidate.setIncidentId(id.value());
			candidate.setCreatedAt(Instant.now());
			candidate.setUpdatedAt(candidate.getCreatedAt());
			RevisionToken revision = token();
			documents.put(id, new StoredIncident(candidate, revision));
			callerTokens.put(data, revision);
			data.setIncidentId(id.value());
			data.setCreatedAt(candidate.getCreatedAt());
			data.setUpdatedAt(candidate.getUpdatedAt());
			return id;
		}
	}

	/** {@inheritDoc} */
	@Override
	public StoredIncident loadSnapshot(IncidentId id) {
		synchronized (documents) {
			before(ControllableIncidentStore.Operation.LOAD);
			StoredIncident snapshot = require(id);
			AppData detached = copy(snapshot.data());
			callerTokens.put(detached, snapshot.revision());
			return new StoredIncident(detached, snapshot.revision());
		}
	}

	/** {@inheritDoc} */
	@Override
	public RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
		synchronized (documents) {
			before(ControllableIncidentStore.Operation.SAVE);
			if (data == null || !id.value().equals(data.getIncidentId()) || expected == null) {
				throw new StoreValidationException("Identity or expected revision is invalid");
			}
			check(require(id), expected);
			AppData candidate = copy(data);
			candidate.setUpdatedAt(Instant.now());
			RevisionToken revision = token();
			documents.put(id, new StoredIncident(candidate, revision));
			data.setUpdatedAt(candidate.getUpdatedAt());
			callerTokens.put(data, revision);
			return revision;
		}
	}

	/** {@inheritDoc} */
	@Override
	public List<IncidentSummary> list() {
		synchronized (documents) {
			before(ControllableIncidentStore.Operation.LIST);
			return documents.entrySet().stream().map(entry -> {
				AppData data = entry.getValue().data();
				return new IncidentSummary("server:incident-collection", data.getIncidentContext().getIncidentName(),
						data.getIapPhase(), data.getUpdatedAt(), entry.getKey(),
						data.getForm201().getIncidentNumber(), entry.getValue().revision());
			}).sorted(Comparator.comparing(IncidentSummary::lastModified).reversed()).toList();
		}
	}

	/** {@inheritDoc} */
	@Override
	public void delete(IncidentId id, RevisionToken expected) {
		synchronized (documents) {
			before(ControllableIncidentStore.Operation.DELETE);
			if (expected == null) {
				throw new StoreValidationException("Expected revision is required");
			}
			check(require(id), expected);
			documents.remove(id);
			retired.add(id);
		}
	}

	/** {@inheritDoc} */
	@Override
	public Optional<IncidentId> importFile(Path jsonFile) {
		throw new UnsupportedOperationException("Local transfers are not available on this server.");
	}

	/** {@inheritDoc} */
	@Override
	public void exportFile(IncidentId id, Path target) {
		throw new UnsupportedOperationException("Local transfers are not available on this server.");
	}

	/** {@inheritDoc} */
	@Override
	public StoreDescriptor describe() {
		return new StoreDescriptor("Server", "server:incident-collection");
	}

	/**
	 * Returns the server token associated with this exact caller snapshot, never AppData's long.
	 *
	 * @param data caller snapshot previously created, loaded or successfully saved
	 * @return its last issued token
	 */
	@Override
	public RevisionToken revisionToken(AppData data) {
		synchronized (documents) {
			RevisionToken revision = callerTokens.get(data);
			if (revision == null) {
				throw new StoreValidationException("Caller snapshot has no server revision");
			}
			return revision;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		closed = true;
	}

	/**
	 * Validates lifecycle and applies a scheduled failure.
	 *
	 * @param operation operation about to execute
	 */
	private void before(ControllableIncidentStore.Operation operation) {
		if (closed) {
			throw new StoreUnavailableException("Server handle is closed");
		}
		RuntimeException failure = failures.remove(operation);
		if (failure != null) {
			throw failure;
		}
	}

	/**
	 * Finds live server state.
	 *
	 * @param id incident identity
	 * @return live snapshot
	 */
	private StoredIncident require(IncidentId id) {
		StoredIncident snapshot = documents.get(id);
		if (snapshot == null) {
			throw new IncidentNotFoundException("Missing or deleted incident: " + id);
		}
		return snapshot;
	}

	/**
	 * Compares tokens without interpreting their contents.
	 *
	 * @param current current snapshot
	 * @param expected caller's token
	 */
	private void check(StoredIncident current, RevisionToken expected) {
		if (!current.revision().equals(expected)) {
			throw new StoreConflictException("Stale server snapshot", expected, current.revision());
		}
	}

	/**
	 * Issues a nonnumeric server-generated token.
	 *
	 * @return opaque revision
	 */
	private RevisionToken token() {
		return new RevisionToken("etag/" + UUID.randomUUID());
	}

	/**
	 * Detaches fields without calling caller getters.
	 *
	 * @param data caller's document
	 * @return detached document
	 */
	private AppData copy(AppData data) {
		if (data == null || data.getSchemaVersion() > AppData.CURRENT_SCHEMA_VERSION) {
			throw new StoreValidationException("Missing document or unsupported schema");
		}
		return COPIER.convertValue(data, AppData.class);
	}
}
