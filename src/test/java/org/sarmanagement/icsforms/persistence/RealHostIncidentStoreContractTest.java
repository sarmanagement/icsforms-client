/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs the snapshot token contract against the production {@link RemoteIncidentStore} talking to a real host
 * service over HTTPS with mutual TLS. Creating and deleting incidents are host-administrative actions performed
 * on the backing file store, because a remote device cannot do either; everything else goes through the
 * production remote store and the real protocol.
 */
class RealHostIncidentStoreContractTest extends AbstractSnapshotIncidentStoreContractTest {
	@TempDir
	Path directory;
	private ControllableIncidentStore backing;
	private final Map<IncidentId, HostSession> sessions = new HashMap<>();
	private final Set<IncidentId> retired = new HashSet<>();
	private final List<Handle> handles = new ArrayList<>();

	/**
	 * Stops every host session and the backing store.
	 */
	@AfterEach
	void tearDown() {
		for (Handle handle : handles) {
			handle.close();
		}
		sessions.values().forEach(HostSession::stop);
		if (backing != null) {
			backing.close();
		}
	}

	/** {@inheritDoc} */
	@Override
	protected IncidentStore openStore() {
		if (backing == null) {
			backing = new ControllableIncidentStore(directory);
		}
		Handle handle = new Handle();
		handles.add(handle);
		return handle;
	}

	/** {@inheritDoc} */
	@Override
	protected void failNext(IncidentStore store, ControllableIncidentStore.Operation operation) {
		((Handle) store).armed = operation;
	}

	/**
	 * Gateway that fails a submission before it reaches the host, so a failed save provably leaves the host
	 * unchanged. Failures after the host committed are covered by a dedicated test.
	 */
	private static final class SubmitFailingGateway implements RemoteIncidentGateway {
		private final RemoteIncidentGateway delegate;
		private final Handle owner;

		/**
		 * Creates the gateway.
		 *
		 * @param delegate real HTTPS gateway
		 * @param owner handle that arms the failure
		 */
		SubmitFailingGateway(RemoteIncidentGateway delegate, Handle owner) {
			this.delegate = delegate;
			this.owner = owner;
		}

		/** {@inheritDoc} */
		@Override
		public String location() {
			return delegate.location();
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ReadResult read(IncidentId incident) {
			return delegate.read(incident);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ChangeResult changesSince(IncidentId incident, long after) {
			return delegate.changesSince(incident, after);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
			if (owner.fails(ControllableIncidentStore.Operation.SAVE)) {
				throw new StorePersistenceException("Scheduled operation failure", null);
			}
			return delegate.submit(batch);
		}

		/** {@inheritDoc} */
		@Override
		public HostProbe probe() {
			return delegate.probe();
		}

		/** {@inheritDoc} */
		@Override
		public void close() {
			delegate.close();
		}
	}

	/**
	 * One client handle on the shared host scope.
	 */
	private final class Handle implements IncidentStore {
		private final Map<IncidentId, RemoteIncidentStore> remotes = new HashMap<>();
		private ControllableIncidentStore.Operation armed;
		private boolean closed;

		/**
		 * Rejects use after close.
		 */
		private void open() {
			if (closed) {
				throw new StoreUnavailableException("Closed");
			}
		}

		/**
		 * Consumes an armed failure for an operation.
		 *
		 * @param operation operation about to run
		 * @return whether it must fail
		 */
		boolean fails(ControllableIncidentStore.Operation operation) {
			boolean fail = armed == operation;
			if (fail) {
				armed = null;
			}
			return fail;
		}

		/**
		 * Returns this handle's remote store for an incident, pairing a device on first use.
		 *
		 * @param id incident identifier
		 * @return remote store
		 */
		private synchronized RemoteIncidentStore remote(IncidentId id) {
			if (retired.contains(id)) {
				throw new IncidentNotFoundException("Deleted");
			}
			HostSession session = sessions.get(id);
			if (session == null) {
				throw new IncidentNotFoundException("Unknown incident");
			}
			return remotes.computeIfAbsent(id, key -> {
				RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "contract-" + remotes.size());
				return new RemoteIncidentStore(new SubmitFailingGateway(paired.gateway(), this), key);
			});
		}

		/** {@inheritDoc} */
		@Override
		public IncidentId create(AppData data) {
			open();
			if (data.getIncidentId() != null && retired.contains(new IncidentId(data.getIncidentId()))) {
				throw new DuplicateIncidentException("Retired");
			}
			if (fails(ControllableIncidentStore.Operation.CREATE)) {
				throw new StorePersistenceException("Scheduled operation failure", null);
			}
			IncidentId id = backing.create(data);
			HostSession session = new HostSession(backing, id, HostConfig.defaults("127.0.0.1", 0, 0)
					.withBindAddress("127.0.0.1").withStopTiming(Duration.ZERO, Duration.ofSeconds(2)), null);
			session.start();
			sessions.put(id, session);
			RemoteIncidentStore remote = remote(id);
			remote.associate(data, remote.loadSnapshot(id).revision());
			return id;
		}

		/** {@inheritDoc} */
		@Override
		public AppData load(IncidentId id) {
			return loadSnapshot(id).data();
		}

		/** {@inheritDoc} */
		@Override
		public StoredIncident loadSnapshot(IncidentId id) {
			open();
			return remote(id).loadSnapshot(id);
		}

		/** {@inheritDoc} */
		@Override
		public void save(IncidentId id, AppData data) {
			throw new UnsupportedOperationException("Tokenless saves are not supported");
		}

		/** {@inheritDoc} */
		@Override
		public RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
			open();
			RemoteIncidentStore remote = remote(id);
			return remote.save(id, data, expected);
		}

		/** {@inheritDoc} */
		@Override
		public List<IncidentSummary> list() {
			open();
			List<IncidentSummary> summaries = new ArrayList<>();
			for (IncidentId id : sessions.keySet()) {
				if (!retired.contains(id)) {
					summaries.addAll(remote(id).list());
				}
			}
			return summaries;
		}

		/** {@inheritDoc} */
		@Override
		public void delete(IncidentId id) {
			throw new UnsupportedOperationException("Tokenless deletes are not supported");
		}

		/** {@inheritDoc} */
		@Override
		public void delete(IncidentId id, RevisionToken expected) {
			open();
			RemoteIncidentStore remote = remote(id);
			RevisionToken current = remote.loadSnapshot(id).revision();
			if (!current.equals(expected)) {
				throw new StoreConflictException("Changed", expected, current);
			}
			if (fails(ControllableIncidentStore.Operation.DELETE)) {
				throw new StorePersistenceException("Scheduled operation failure", null);
			}
			sessions.remove(id).stop();
			backing.delete(id, backing.loadSnapshot(id).revision());
			retired.add(id);
		}

		/** {@inheritDoc} */
		@Override
		public Optional<Path> localFile(IncidentId id) {
			return Optional.empty();
		}

		/** {@inheritDoc} */
		@Override
		public RevisionToken revisionToken(AppData data) {
			open();
			return remote(new IncidentId(data.getIncidentId())).revisionToken(data);
		}

		/** {@inheritDoc} */
		@Override
		public Optional<IncidentId> importFile(Path jsonFile) {
			throw new UnsupportedOperationException("Imports are host-administrative");
		}

		/** {@inheritDoc} */
		@Override
		public List<org.sarmanagement.icsforms.persistence.ImportValidationException.Problem> getLastImportProblems() {
			return List.of();
		}

		/** {@inheritDoc} */
		@Override
		public void exportFile(IncidentId id, Path target) {
			remote(id).exportFile(id, target);
		}

		/** {@inheritDoc} */
		@Override
		public StoreDescriptor describe() {
			return new StoreDescriptor("Remote", "contract host");
		}

		/** {@inheritDoc} */
		@Override
		public synchronized void close() {
			closed = true;
			remotes.values().forEach(RemoteIncidentStore::close);
		}
	}
}
