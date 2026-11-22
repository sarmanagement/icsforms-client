/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers durable replay validation and recovery of rejected operations. */
class RemoteIncidentStoreRejectionTest {
	@TempDir
	Path directory;
	private ControllableIncidentStore backing;
	private HostSession session;
	private IncidentId incident;

	@BeforeEach
	void start() {
		backing = new ControllableIncidentStore(directory.resolve("host"));
		incident = backing.create(new AppData());
		session = new HostSession(backing, incident,
				HostConfig.defaults("127.0.0.1", 0, 0).withBindAddress("127.0.0.1")
						.withStopTiming(Duration.ZERO, Duration.ofSeconds(2)), null);
		session.start();
	}

	@AfterEach
	void stop() {
		session.stop();
		backing.close();
	}

	/**
	 * Pairs and detaches a transport for the shared host.
	 *
	 * @return authenticated transport
	 */
	private RemoteIncidentGateway pair() {
		RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "rejection-test");
		RemoteIncidentGateway gateway = paired.detachGateway();
		paired.close();
		return gateway;
	}

	@Test
	void oversizedCountIsDurablyRejectedWithAllDependentEditsExportable() throws Exception {
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		Path cache = directory.resolve("cache");
		String id;
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			for (int index = 0; index < 101; index++) {
				ClueLogEntry entry = new ClueLogEntry();
				entry.setDescription("clue " + index);
				snapshot.data().getClueLogEntries().add(entry);
			}
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			snapshot.data().getIncidentContext().setIncidentName("later edit");
			local.save(incident, snapshot.data(), revision);
			local.replayPending();
			var head = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, head.status());
			assertNull(head.batch());
			assertTrue(head.rejection().contains("100"));
			assertEquals(2, local.pendingOperations().size());
			assertThrows(StoreValidationException.class, () -> local.discardPending(head.operationId()));
			local.replayPending();
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, local.pendingOperations().get(0).status());
			local.retryRejected(head.operationId());
			local.replayPending();
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, local.pendingOperations().get(0).status());
			Path exported = directory.resolve("outbox.json");
			local.exportOutbox(exported);
			String text = Files.readString(exported);
			assertTrue(text.contains("clue 100"));
			assertTrue(text.contains("later edit"));
			assertTrue(backing.loadSnapshot(incident).data().getClueLogEntries().isEmpty());
			id = head.operationId();
		}
		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(id, restored.pendingOperations().get(0).operationId());
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, restored.pendingOperations().get(0).status());
			assertEquals(101, restored.loadSnapshot(incident).data().getClueLogEntries().size());
			assertEquals("later edit", restored.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void byteLimitCountsUtf8InsteadOfCharacters() {
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			String original = snapshot.data().getIncidentContext().getIncidentName();
			snapshot.data().getIncidentContext().setIncidentName("é".repeat(460_000));
			local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertNull(rejected.batch());
			assertTrue(rejected.rejection().contains("larger"));
			assertEquals(original, backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void rejectionAfterAmbiguousAcceptanceCannotBeDiscardedAndRetriesExactBatch() {
		AmbiguousGateway gateway = new AmbiguousGateway(pair());
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, directory.resolve("cache"))) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("accepted once");
			local.save(incident, snapshot.data(), snapshot.revision());
			assertThrows(StoreUnavailableException.class, local::replayPending);
			var exact = local.pendingOperations().get(0).batch();
			assertNotNull(exact);
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertEquals(exact, rejected.batch());
			assertThrows(StoreValidationException.class, () -> local.discardPending(rejected.operationId()));
			local.retryRejected(rejected.operationId());
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals(1, local.acceptedReceiptCount());
			assertEquals("accepted once", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void rejectedHeadIsReplacedByCorrectedDraftAfterArchivingTheOriginal() throws Exception {
		AmbiguousGateway gateway = new AmbiguousGateway(pair(), true);
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("wrong name");
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertThrows(StoreValidationException.class, () -> local.replaceRejected(rejected.operationId()));
			snapshot.data().getIncidentContext().setIncidentName("corrected name");
			local.save(incident, snapshot.data(), revision);
			assertEquals(2, local.pendingOperations().size());
			assertThrows(StoreValidationException.class, () -> local.replaceRejected("unknown"));
			String replacement = local.replaceRejected(rejected.operationId());
			assertEquals(1, local.pendingOperations().size());
			assertEquals(replacement, local.pendingOperations().get(0).operationId());
			assertEquals(RemoteIncidentStore.PendingStatus.PENDING, local.pendingOperations().get(0).status());
			Path archive;
			try (var files = Files.walk(cache)) {
				archive = files.filter(path -> path.getFileName().toString().contains(".rejected-")).findFirst()
						.orElseThrow();
			}
			String archived = Files.readString(archive);
			assertTrue(archived.contains(rejected.operationId()));
			assertTrue(archived.contains("wrong name"));
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals("corrected name",
					backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	/** Simulates a lost acceptance, followed by a validation error, then normal receipt replay. */
	private static final class AmbiguousGateway implements RemoteIncidentGateway {
		private final RemoteIncidentGateway delegate;
		private final boolean rejectFirst;
		private int submissions;

		/**
		 * Wraps an authenticated transport.
		 *
		 * @param delegate actual host transport
		 */
		AmbiguousGateway(RemoteIncidentGateway delegate) {
			this(delegate, false);
		}

		/**
		 * Wraps an authenticated transport.
		 *
		 * @param delegate actual host transport
		 * @param rejectFirst when true, the first submission is rejected without reaching the host
		 */
		AmbiguousGateway(RemoteIncidentGateway delegate, boolean rejectFirst) {
			this.delegate = delegate;
			this.rejectFirst = rejectFirst;
		}

		/** {@inheritDoc} */
		@Override
		public Optional<String> authorityFingerprint() {
			return delegate.authorityFingerprint();
		}

		/** {@inheritDoc} */
		@Override
		public String location() {
			return delegate.location();
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ReadResult read(IncidentId id) {
			return delegate.read(id);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.ChangeResult changesSince(IncidentId id, long cursor) {
			return delegate.changesSince(id, cursor);
		}

		/** {@inheritDoc} */
		@Override
		public IncidentService.SubmissionResult submit(IncidentService.RecordBatch batch) {
			submissions++;
			if (rejectFirst) {
				if (submissions == 1) {
					throw new StoreValidationException("Host policy rejected the edit");
				}
				return delegate.submit(batch);
			}
			if (submissions == 1) {
				delegate.submit(batch);
				throw new StoreUnavailableException("Lost response");
			}
			if (submissions == 2) {
				throw new StoreValidationException("Rejected retry");
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
}
