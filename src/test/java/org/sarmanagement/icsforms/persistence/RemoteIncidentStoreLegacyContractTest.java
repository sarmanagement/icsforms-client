/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the capability-split legacy contract against the production {@link RemoteIncidentStore} talking to a real
 * host over HTTPS with mutual TLS.
 *
 * <p>
 * The legacy tests that are skipped here, and why: {@code multipleIncidentsAreIndependent} and
 * {@code listSortsMostRecentlyUpdatedFirst} (a host scope shares exactly one incident),
 * {@code deletedIncidentsStayDeleted} (devices cannot delete), {@code deletionConflictsWithAnEditLock} (no edit
 * locks, no delete) and {@code exportAndImportCreateIndependentCopies} (devices cannot import). Numeric
 * {@code AppData.getRevision()} assertions are skipped inside the remaining tests because remote tokens are
 * opaque. The incident is seeded in the host's backing store because creation is host-administrative; every
 * operation under test goes through the remote store and the real protocol. Export, listing, restart and
 * concurrency are exercised directly by the tests in this class.
 */
class RemoteIncidentStoreLegacyContractTest extends AbstractIncidentStoreContractTest {
	private final Map<IncidentStore, HostBacked> hosts = new IdentityHashMap<>();
	private final List<HostBacked> opened = new ArrayList<>();

	/**
	 * A real host session over a backing file store plus the remote store paired to it.
	 */
	private final class HostBacked implements IncidentStore {
		private final FileIncidentStore backing;
		private HostSession session;
		private RemoteIncidentStore remote;
		private boolean failNextSubmit;

		/**
		 * Opens the backing store and, when it already holds the shared incident, a host session for it.
		 *
		 * @param directory backing directory
		 */
		HostBacked(Path directory) {
			backing = new FileIncidentStore(directory);
			List<IncidentSummary> existing = backing.list();
			if (!existing.isEmpty()) {
				share(existing.get(0).incidentId());
			}
		}

		/**
		 * Starts a host session for an incident and pairs a remote store to it.
		 *
		 * @param id incident to share
		 */
		private void share(IncidentId id) {
			session = new HostSession(backing, id, HostConfig.defaults("127.0.0.1", 0, 0)
					.withBindAddress("127.0.0.1").withStopTiming(Duration.ZERO, Duration.ofSeconds(2)), null);
			session.start();
			RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "legacy-contract");
			remote = new RemoteIncidentStore(new FailingGateway(paired.gateway(), this), id);
		}

		/**
		 * Seeds the shared incident in the backing store and starts the host.
		 *
		 * @param data initial document
		 * @return incident identity
		 */
		IncidentId seedIncident(AppData data) {
			IncidentId id = backing.create(data);
			share(id);
			return id;
		}

		/**
		 * Returns the remote store.
		 *
		 * @return remote store
		 */
		private RemoteIncidentStore remote() {
			if (remote == null) {
				throw new IncidentNotFoundException("No shared incident");
			}
			return remote;
		}

		/** {@inheritDoc} */
		@Override
		public IncidentId create(AppData data) {
			return remote().create(data);
		}

		/** {@inheritDoc} */
		@Override
		public StoredIncident loadSnapshot(IncidentId id) {
			return remote().loadSnapshot(id);
		}

		/** {@inheritDoc} */
		@Override
		public RevisionToken save(IncidentId id, AppData data, RevisionToken expected) {
			return remote().save(id, data, expected);
		}

		/** {@inheritDoc} */
		@Override
		public RevisionToken revisionToken(AppData data) {
			return remote().revisionToken(data);
		}

		/** {@inheritDoc} */
		@Override
		public List<IncidentSummary> list() {
			return remote().list();
		}

		/** {@inheritDoc} */
		@Override
		public Optional<IncidentId> importFile(Path jsonFile) {
			return remote().importFile(jsonFile);
		}

		/** {@inheritDoc} */
		@Override
		public void exportFile(IncidentId id, Path target) {
			remote().exportFile(id, target);
		}

		/** {@inheritDoc} */
		@Override
		public StoreDescriptor describe() {
			return remote().describe();
		}

		/** {@inheritDoc} */
		@Override
		public void close() {
			if (remote != null) {
				remote.close();
			}
			if (session != null) {
				session.stop();
			}
			backing.close();
		}
	}

	/**
	 * Gateway that fails one submission before it reaches the host.
	 */
	private static final class FailingGateway implements RemoteIncidentGateway {
		private final RemoteIncidentGateway delegate;
		private final HostBacked owner;

		/**
		 * Creates the gateway.
		 *
		 * @param delegate real HTTPS gateway
		 * @param owner holder of the failure flag
		 */
		FailingGateway(RemoteIncidentGateway delegate, HostBacked owner) {
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
			if (owner.failNextSubmit) {
				owner.failNextSubmit = false;
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

	/** {@inheritDoc} */
	@Override
	protected IncidentStore createStore(Path directory) {
		HostBacked host = new HostBacked(directory);
		hosts.put(host, host);
		opened.add(host);
		return host;
	}

	/** {@inheritDoc} */
	@Override
	protected IncidentId seed(IncidentStore store, AppData data) {
		return ((HostBacked) store).seedIncident(data);
	}

	/** {@inheritDoc} */
	@Override
	protected void persist(IncidentStore store, IncidentId id, AppData data) {
		store.save(id, data, store.revisionToken(data));
	}

	/** {@inheritDoc} */
	@Override
	protected boolean supportsCreate() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected boolean supportsMultipleIncidents() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected boolean supportsDelete() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected boolean supportsEditLocks() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected boolean supportsNumericRevisions() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected boolean supportsImport() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected boolean lock(IncidentStore store, IncidentId id) {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void unlock(IncidentStore store, IncidentId id) {
		// Remote stores have no edit locks.
	}

	/** {@inheritDoc} */
	@Override
	protected void failNextSave(IncidentStore store) {
		((HostBacked) store).failNextSubmit = true;
	}

	/** {@inheritDoc} */
	@Override
	protected void failNextDelete(IncidentStore store) {
		throw new UnsupportedOperationException("Remote stores cannot delete");
	}

	/** Lists exactly the one shared incident and hides file paths. */
	@Test
	void listShowsTheOneSharedIncident() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = seed(store, namedDocument("Shared"));
			List<IncidentSummary> summaries = store.list();
			assertEquals(1, summaries.size());
			assertEquals(id, summaries.get(0).incidentId());
			assertTrue(store.localFile(id).isEmpty());
		}
	}

	/** A restarted host serves the same data and a fresh remote store gets a usable token. */
	@Test
	void persistedStateSurvivesAHostRestartWithAUsableToken() {
		IncidentId id;
		try (IncidentStore store = createStore(tempDir)) {
			id = seed(store, namedDocument("Before"));
			AppData data = store.load(id);
			data.getIncidentContext().setIncidentName("Saved");
			persist(store, id, data);
		}
		try (IncidentStore restarted = createStore(tempDir)) {
			StoredIncident snapshot = restarted.loadSnapshot(id);
			assertEquals("Saved", snapshot.data().getIncidentContext().getIncidentName());
			snapshot.data().getIncidentContext().setIncidentName("After restart");
			RevisionToken next = restarted.save(id, snapshot.data(), snapshot.revision());
			assertNotEquals(snapshot.revision(), next);
			assertEquals("After restart", restarted.load(id).getIncidentContext().getIncidentName());
		}
	}

	/** Exactly one of two writers from the same snapshot wins and the other sees a conflict. */
	@Test
	void exactlyOneWriterFromTheSameSnapshotWins() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = seed(store, namedDocument("Race"));
			StoredIncident first = store.loadSnapshot(id);
			StoredIncident second = store.loadSnapshot(id);
			first.data().getIncidentContext().setIncidentName("One");
			second.data().getIncidentContext().setIncidentName("Two");
			store.save(id, first.data(), first.revision());
			assertThrows(StoreConflictException.class, () -> store.save(id, second.data(), second.revision()));
			assertEquals("One", store.load(id).getIncidentContext().getIncidentName());
		}
	}

	/** Exported JSON imports into a standalone store as an independent incident. */
	@Test
	void exportedFileImportsIntoAStandaloneStoreAsAnIndependentIncident() throws Exception {
		try (IncidentStore store = createStore(tempDir.resolve("host"))) {
			IncidentId id = seed(store, namedDocument("Exported"));
			Path json = tempDir.resolve("export.json");
			store.exportFile(id, json);
			assertTrue(Files.size(json) > 0);
			assertEquals(1, store.loadSnapshot(id).data().getRevision());
			assertEquals(1, new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.toFile())
					.path("revision").asLong(-1));
			try (FileIncidentStore standalone = new FileIncidentStore(tempDir.resolve("standalone"))) {
				IncidentId copy = standalone.importFile(json).orElseThrow();
				assertNotEquals(id, copy);
				assertEquals("Exported", standalone.load(copy).getIncidentContext().getIncidentName());
				assertEquals(0, standalone.load(copy).getRevision());
			}
		}
	}

	/** Creation, deletion and import fail explicitly on the remote store. */
	@Test
	void administrativeOperationsFailExplicitly() {
		try (IncidentStore store = createStore(tempDir)) {
			IncidentId id = seed(store, namedDocument("Admin"));
			assertThrows(UnsupportedOperationException.class, () -> store.create(namedDocument("Other")));
			assertThrows(UnsupportedOperationException.class, () -> store.delete(id, store.loadSnapshot(id).revision()));
			assertThrows(UnsupportedOperationException.class, () -> store.importFile(tempDir.resolve("x.json")));
		}
	}

	/**
	 * Creates a named document.
	 *
	 * @param name incident name
	 * @return document
	 */
	private static AppData namedDocument(String name) {
		AppData data = new AppData();
		data.getIncidentContext().setIncidentName(name);
		return data;
	}
}
