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

import com.fasterxml.jackson.databind.JsonNode;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IapPlanningStage;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.RootSettings;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
	void removalFromOneDeviceReachesTheHostAndASecondClient() {
		try (RemoteIncidentStore a = device("a"); RemoteIncidentStore b = device("b")) {
			StoredIncident first = a.loadSnapshot(id);
			org.sarmanagement.icsforms.model.OpenActionItem entry = new org.sarmanagement.icsforms.model.OpenActionItem();
			entry.setItem("Check access");
			first.data().getOpenActionItems().add(entry);
			a.save(id, first.data(), first.revision());

			StoredIncident withEntry = a.loadSnapshot(id);
			withEntry.data().getOpenActionItems().clear();
			a.save(id, withEntry.data(), withEntry.revision());

			assertTrue(b.loadSnapshot(id).data().getOpenActionItems().isEmpty());
			assertTrue(a.loadSnapshot(id).data().getOpenActionItems().isEmpty());
		}
	}

	@Test
	void agencyRepresentativeAddedOnOneDeviceReachesTheOther() {
		try (RemoteIncidentStore a = device("a"); RemoteIncidentStore b = device("b")) {
			StoredIncident first = a.loadSnapshot(id);
			org.sarmanagement.icsforms.model.OrgChartEntry entry = new org.sarmanagement.icsforms.model.OrgChartEntry();
			entry.setTitle("Agency Representative");
			entry.setName("Casey Lee");
			first.data().getOrganizationalChart().getAssignmentList().getAgencyRepresentatives().add(entry);
			a.save(id, first.data(), first.revision());

			assertEquals("Casey Lee", b.loadSnapshot(id).data().getOrganizationalChart().getAssignmentList()
					.getAgencyRepresentatives().get(0).getName());
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
	void everyRootFieldSavedByAClientReachesTheHostAndASecondClient() {
		Map<String, Consumer<AppData>> edits = new LinkedHashMap<>();
		edits.put("iapPhase", data -> data.setIapPhase(IapPhase.INITIAL_RESPONSE));
		edits.put("incidentMode", data -> data.setIncidentMode(IncidentMode.values()[IncidentMode.values().length - 1]));
		edits.put("useSystemTimeZone", data -> data.setUseSystemTimeZone(!data.isUseSystemTimeZone()));
		edits.put("configuredTimeZoneId", data -> data.setConfiguredTimeZoneId("America/Denver"));
		edits.put("iapPlanningStage", data -> data.setIapPlanningStage(IapPlanningStage.values()[1]));
		edits.put("ics215Enabled", data -> data.setIcs215Enabled(!data.isIcs215Enabled()));
		edits.put("ics205Enabled", data -> data.setIcs205Enabled(!data.isIcs205Enabled()));
		edits.put("form205aIapPage", data -> data.setForm205aIapPage("7"));
		edits.put("openActionTrackerEnabled", data -> data.setOpenActionTrackerEnabled(true));
		edits.put("ics233Portrait", data -> data.setIcs233Portrait(true));
		edits.put("clueLogIcs233Enabled", data -> data.setClueLogIcs233Enabled(true));
		edits.put("canineRoutingBaseLocationId", data -> data.setCanineRoutingBaseLocationId("base-1"));
		edits.put("safetyReview", data -> data.getSafetyReview().setReviewedBy("Safety Officer"));
		assertTrue(edits.keySet().containsAll(RootSettings.propertyNames().stream()
				.filter(name -> !name.equals("humanMedicalReview") && !name.equals("canineMedicalReview")
						&& !name.equals("communicationsReview") && !name.equals("medicalUnitLeaderAssignment"))
				.toList()));
		try (RemoteIncidentStore a = device("a"); RemoteIncidentStore b = device("b")) {
			for (Map.Entry<String, Consumer<AppData>> edit : edits.entrySet()) {
				StoredIncident snapshot = a.loadSnapshot(id);
				edit.getValue().accept(snapshot.data());
				JsonNode expected = RootSettings.snapshot(snapshot.data());
				assertNotEquals(expected, RootSettings.snapshot(a.loadSnapshot(id).data()), edit.getKey());

				RevisionToken token = a.save(id, snapshot.data(), snapshot.revision());

				assertNotEquals(snapshot.revision(), token, edit.getKey());
				assertEquals(token, a.loadSnapshot(id).revision(), edit.getKey());
				assertEquals(expected, RootSettings.snapshot(b.loadSnapshot(id).data()), edit.getKey());
				assertEquals(expected, RootSettings.snapshot(backing.loadSnapshot(id).data()), edit.getKey());
			}
		}
	}

	@Test
	void rootFieldConflictIsReportedAndDifferentRootFieldsMerge() {
		try (RemoteIncidentStore a = device("a"); RemoteIncidentStore b = device("b")) {
			StoredIncident first = a.loadSnapshot(id);
			StoredIncident second = b.loadSnapshot(id);
			first.data().setIapPhase(IapPhase.INITIAL_RESPONSE);
			second.data().setIapPhase(IapPhase.values()[IapPhase.values().length - 1]);
			RevisionToken afterA = a.save(id, first.data(), first.revision());

			StoreConflictException conflict = assertThrows(StoreConflictException.class,
					() -> b.save(id, second.data(), second.revision()));

			assertEquals(afterA, conflict.current());
			assertEquals(IapPhase.INITIAL_RESPONSE, b.loadSnapshot(id).data().getIapPhase());
			assertEquals(IapPhase.values()[IapPhase.values().length - 1], second.data().getIapPhase());

			StoredIncident third = b.loadSnapshot(id);
			StoredIncident fourth = a.loadSnapshot(id);
			third.data().setConfiguredTimeZoneId("America/Denver");
			fourth.data().setForm205aIapPage("9");
			b.save(id, third.data(), third.revision());
			a.save(id, fourth.data(), fourth.revision());
			AppData merged = backing.loadSnapshot(id).data();
			assertEquals("America/Denver", merged.getConfiguredTimeZoneId());
			assertEquals("9", merged.getForm205aIapPage());
		}
	}

	@Test
	void aSaveNeverReturnsATokenForARootEditThatWasNotSubmitted() {
		try (RemoteIncidentStore a = device("a")) {
			StoredIncident snapshot = a.loadSnapshot(id);
			snapshot.data().setIncidentMode(IncidentMode.values()[IncidentMode.values().length - 1]);
			AtomicReference<RevisionToken> returned = new AtomicReference<>();
			backing.failNext(ControllableIncidentStore.Operation.SAVE, new IllegalStateException("projection"));

			assertThrows(StoreCommittedException.class, () -> returned.set(a.save(id, snapshot.data(),
					snapshot.revision())));

			assertEquals(null, returned.get());
			RevisionToken retried = a.save(id, snapshot.data(), snapshot.revision());
			assertNotEquals(snapshot.revision(), retried);
			assertEquals(snapshot.data().getIncidentMode(), backing.loadSnapshot(id).data().getIncidentMode());
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

			StoreCommittedException committed = assertThrows(StoreCommittedException.class,
					() -> a.save(id, snapshot.data(), snapshot.revision()));
			assertTrue(committed.getMessage().contains("retrying the same save is safe"));
			RevisionToken retried = a.save(id, snapshot.data(), snapshot.revision());

			StoredIncident current = a.loadSnapshot(id);
			assertEquals("Committed once", current.data().getIncidentContext().getIncidentName());
			assertEquals(retried, current.revision());
		}
	}
}
