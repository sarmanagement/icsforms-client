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

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production remote store through a real host: merges, conflicts and explicit limits. */
class RemoteIncidentStoreHostTest {
	@TempDir
	Path directory;
	private ControllableIncidentStore backing;
	private HostSession session;
	private IncidentId id;

	/**
	 * Starts a host over a file-backed incident.
	 */
	@BeforeEach
	void start() {
		backing = new ControllableIncidentStore(directory);
		id = backing.create(new AppData());
		session = new HostSession(backing, id, HostConfig.defaults("127.0.0.1", 0, 0).withBindAddress("127.0.0.1")
				.withStopTiming(Duration.ZERO, Duration.ofSeconds(2)), null);
		session.start();
	}

	/**
	 * Stops the host.
	 */
	@AfterEach
	void stop() {
		session.stop();
		backing.close();
	}

	/**
	 * Pairs a new device.
	 *
	 * @param name device name
	 * @return its remote store
	 */
	private RemoteIncidentStore device(String name) {
		return HostGateway.pair(session.issueInvitation(), name);
	}

	@Test
	void editsToDifferentFieldsFromTwoDevicesMerge() {
		try (RemoteIncidentStore a = device("a"); RemoteIncidentStore b = device("b")) {
			StoredIncident first = a.loadSnapshot(id);
			StoredIncident second = b.loadSnapshot(id);
			first.data().getIncidentContext().setIncidentName("Name from A");
			second.data().getIncidentContext().setTaskMap("Number from B");
			RevisionToken afterA = a.save(id, first.data(), first.revision());
			RevisionToken afterB = b.save(id, second.data(), second.revision());

			assertNotEquals(afterA, afterB);
			AppData merged = a.loadSnapshot(id).data();
			assertEquals("Name from A", merged.getIncidentContext().getIncidentName());
			assertEquals("Number from B", merged.getIncidentContext().getTaskMap());
		}
	}

	@Test
	void sameFieldEditsConflictAndExposeTheCurrentToken() {
		try (RemoteIncidentStore a = device("a"); RemoteIncidentStore b = device("b")) {
			StoredIncident first = a.loadSnapshot(id);
			StoredIncident second = b.loadSnapshot(id);
			first.data().getIncidentContext().setIncidentName("A");
			second.data().getIncidentContext().setIncidentName("B");
			RevisionToken afterA = a.save(id, first.data(), first.revision());

			StoreConflictException conflict = assertThrows(StoreConflictException.class,
					() -> b.save(id, second.data(), second.revision()));

			assertEquals(second.revision(), conflict.expected());
			assertEquals(afterA, conflict.current());
			assertEquals("A", b.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			assertEquals("B", second.data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void snapshotsAreDetachedAndTokensAreOpaqueAndRepeatable() {
		try (RemoteIncidentStore a = device("a")) {
			StoredIncident one = a.loadSnapshot(id);
			StoredIncident two = a.loadSnapshot(id);
			one.data().getIncidentContext().setIncidentName("Local only");

			assertEquals(one.revision(), two.revision());
			assertNotEquals("Local only", two.data().getIncidentContext().getIncidentName());
			assertNotEquals("Local only", a.loadSnapshot(id).data().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void unsupportedOperationsFailExplicitlyAndNothingLocalIsExposed() {
		try (RemoteIncidentStore a = device("a")) {
			assertThrows(UnsupportedOperationException.class, () -> a.create(new AppData()));
			assertThrows(UnsupportedOperationException.class, () -> a.delete(id));
			assertThrows(UnsupportedOperationException.class, () -> a.importFile(directory.resolve("x.json")));
			assertTrue(a.localFile(id).isEmpty());
			assertTrue(a.editLocks().isEmpty());
			assertEquals(1, a.list().size());
			assertFalse(a.describe().toString().contains(directory.toString()));
		}
	}

	@Test
	void failureAfterTheHostCommittedIsReportedAndTheSameSaveRetriesIdempotently() {
		try (RemoteIncidentStore a = device("a")) {
			StoredIncident snapshot = a.loadSnapshot(id);
			snapshot.data().getIncidentContext().setIncidentName("Committed once");
			backing.failNext(ControllableIncidentStore.Operation.SAVE, new IllegalStateException("projection"));

			assertThrows(StorePersistenceException.class, () -> a.save(id, snapshot.data(), snapshot.revision()));
			RevisionToken retried = a.save(id, snapshot.data(), snapshot.revision());

			StoredIncident current = a.loadSnapshot(id);
			assertEquals("Committed once", current.data().getIncidentContext().getIncidentName());
			assertEquals(retried, current.revision());
		}
	}
}
