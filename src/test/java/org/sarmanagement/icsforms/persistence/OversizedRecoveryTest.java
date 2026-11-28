/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;

/** Covers archived recovery of oversized and discarded local edits. */
class OversizedRecoveryTest {
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
	 * Creates an authenticated transport for the test incident.
	 *
	 * @return detached host transport
	 */
	private RemoteIncidentGateway pair() {
		RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "oversized-recovery");
		RemoteIncidentGateway gateway = paired.detachGateway();
		paired.close();
		return gateway;
	}

	@Test
	void selectedRecordsAreResubmittedAndOriginalQueueIsArchivedExactlyAcrossRestart() throws Exception {
		Path cache = directory.resolve("cache");
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			for (int index = 0; index < 101; index++) {
				ClueLogEntry entry = new ClueLogEntry();
				entry.setDescription("clue " + index);
				snapshot.data().getClueLogEntries().add(entry);
			}
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			assertNull(rejected.batch());
			snapshot.data().getClueLogEntries().remove(100);
			snapshot.data().getClueLogEntries().remove(99);
			snapshot.data().getIncidentContext().setIncidentName("later correction");
			local.save(incident, snapshot.data(), revision);
			Path original = directory.resolve("original.json");
			local.exportOutbox(original);
			local.replaceRejected(rejected.operationId());
			assertArchiveEquals(cache, original);
			assertEquals(99, local.loadSnapshot(incident).data().getClueLogEntries().size());
		}
		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertEquals(99, restored.loadSnapshot(incident).data().getClueLogEntries().size());
			assertEquals("later correction", restored.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
		try (RemoteIncidentStore restored = new RemoteIncidentStore(pair(), incident, cache)) {
			restored.replayPending();
			assertTrue(restored.pendingOperations().isEmpty());
			assertEquals(99, backing.loadSnapshot(incident).data().getClueLogEntries().size());
			assertEquals("later correction", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void oversizedSingleRecordCanBeCorrectedAndOriginalRetained() throws Exception {
		Path cache = directory.resolve("cache");
		try (RemoteIncidentStore local = new RemoteIncidentStore(pair(), incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("é".repeat(460_000));
			RevisionToken revision = local.save(incident, snapshot.data(), snapshot.revision());
			local.replayPending();
			var rejected = local.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.REJECTED, rejected.status());
			snapshot.data().getIncidentContext().setIncidentName("corrected");
			local.save(incident, snapshot.data(), revision);
			Path original = directory.resolve("original.json");
			local.exportOutbox(original);
			local.replaceRejected(rejected.operationId());
			assertArchiveEquals(cache, original);
			local.replayPending();
			assertTrue(local.pendingOperations().isEmpty());
			assertEquals("corrected", backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void discardArchivesTheExactSoleEditBeforeResetAndSurvivesRestart() throws Exception {
		Path cache = directory.resolve("cache");
		RemoteIncidentGateway gateway = pair();
		String fingerprint = gateway.authorityFingerprint().orElseThrow();
		try (RemoteIncidentStore local = new RemoteIncidentStore(gateway, incident, cache)) {
			StoredIncident snapshot = local.loadSnapshot(incident);
			snapshot.data().getIncidentContext().setIncidentName("work retained in archive");
			local.save(incident, snapshot.data(), snapshot.revision());
			Path original = directory.resolve("original.json");
			local.exportOutbox(original);
			local.discardPending(local.pendingOperations().get(0).operationId());
			assertArchiveEquals(cache, original);
			assertTrue(local.pendingOperations().isEmpty());
			assertFalse(local.loadSnapshot(incident).data().getIncidentContext().getIncidentName()
					.equals("work retained in archive"));
		}
		try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, incident)) {
			assertTrue(restored.pendingOperations().isEmpty());
		}
	}

	/**
	 * Compares the archived queue to a complete outbox export.
	 *
	 * @param cache private cache root
	 * @param original exported queue
	 * @throws Exception when reading either copy fails
	 */
	private static void assertArchiveEquals(Path cache, Path original) throws Exception {
		List<Path> archives;
		try (var files = Files.walk(cache)) {
			archives = files.filter(path -> path.getFileName().toString().contains(".rejected-")).toList();
		}
		assertEquals(1, archives.size());
		ObjectMapper mapper = new ObjectMapper();
		assertEquals(mapper.readTree(original.toFile()), mapper.readTree(archives.get(0).toFile()));
	}
}
