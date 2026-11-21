/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.EditLockSupport;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the mutual-TLS host, pairing, admission, revocation and lifecycle over loopback. */
class HostSessionTest {
	@TempDir
	Path directory;

	@Test
	void standaloneCreatesNoListener() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			assertEquals(HostState.STOPPED, fixture.session.state());
			HostStatus status = fixture.session.status();
			assertEquals(0, status.port());
			assertEquals(0, status.enrollmentPort());
			assertNull(status.hostFingerprint());
			fixture.session.stop();
			assertEquals(HostState.STOPPED, fixture.session.state());
		}
	}

	@Test
	void pairedDeviceReadsAndWrites() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet 1");
			HostClient client = fixture.client(device);
			JsonNode read = client.readIncident(fixture.incident.value());
			assertEquals(fixture.incident.value(), read.path("incidentId").asText());
			assertTrue(read.path("records").size() > 0);
			JsonNode result = client.submit(fixture.incident.value(), fixture.renameBatch(client, "op-1", "Renamed"));
			assertTrue(result.path("applied").asBoolean(), result.toString());
			JsonNode changes = client.changesSince(fixture.incident.value(), 0);
			assertTrue(changes.path("changes").size() > 0);
			assertEquals(HostConnectionState.CONNECTED, client.diagnose(device));
			assertEquals(1, fixture.session.connectedDeviceCount());
		}
	}

	@Test
	void credentialLastsSeventyTwoHoursAndEndsAtPeriodClose() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential standard = fixture.pair("Standard");
			assertEquals(fixture.clock.instant().plus(Duration.ofHours(72)), standard.expiresAt());
			fixture.session.setPeriodCloseCutoff(fixture.clock.instant().plus(Duration.ofHours(5)));
			DeviceCredential capped = fixture.pair("Capped");
			assertEquals(fixture.clock.instant().plus(Duration.ofHours(5)), capped.expiresAt());
			fixture.session.setPeriodCloseCutoff(fixture.clock.instant().minusSeconds(1));
			HostClientException refused = assertThrows(HostClientException.class,
					() -> fixture.session.issueInvitation());
			assertEquals("period_closed", refused.code());
		}
	}

	@Test
	void periodCloseCapsIssuedCredentialsAndNoCutoffMeansSeventyTwoHours() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			Instant close = fixture.clock.instant().plus(Duration.ofHours(10));
			fixture.session.setPeriodCloseCutoff(close);
			DeviceCredential underPeriod = fixture.pair("Under period");
			assertFalse(underPeriod.expiresAt().isAfter(close));
			fixture.session.setPeriodCloseCutoff(null);
			DeviceCredential noCutoff = fixture.pair("No cutoff");
			assertEquals(fixture.clock.instant().plus(Duration.ofHours(72)), noCutoff.expiresAt());
		}
	}

	@Test
	void shorteningThePeriodEndsExistingCredentialsAtTheNewClose() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			HostClient client = fixture.client(device);
			assertEquals(HostConnectionState.CONNECTED, client.diagnose(device));
			fixture.session.setPeriodCloseCutoff(fixture.clock.instant().plus(Duration.ofHours(1)));
			assertEquals(HostConnectionState.CONNECTED, client.diagnose(device));
			fixture.clock.advance(Duration.ofHours(2));
			HostClientException expired = assertThrows(HostClientException.class,
					() -> client.readIncident(fixture.incident.value()));
			assertEquals(HostClientException.Kind.CREDENTIAL_EXPIRED, expired.kind());
			assertEquals(HostConnectionState.CREDENTIAL_EXPIRED, client.diagnose(device));
			assertEquals(CredentialState.EXPIRED, fixture.session.devices().get(0).state());
			fixture.session.setPeriodCloseCutoff(null);
			assertEquals(HostConnectionState.CONNECTED, client.diagnose(device));
		}
	}

	@Test
	void expiredCredentialIsDistinguishedFromUnreachable() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			fixture.clock.advance(Duration.ofHours(73));
			HostClient client = fixture.client(device);
			HostClientException expired = assertThrows(HostClientException.class,
					() -> client.readIncident(fixture.incident.value()));
			assertEquals(HostClientException.Kind.CREDENTIAL_EXPIRED, expired.kind());
			assertEquals(HostConnectionState.CREDENTIAL_EXPIRED, client.diagnose(device));
		}
	}

	@Test
	void revokedDeviceIsRejectedAndDistinguished() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			HostClient client = fixture.client(device);
			String deviceId = fixture.session.devices().get(0).deviceId();
			fixture.session.revoke(deviceId);
			HostClientException revoked = assertThrows(HostClientException.class,
					() -> client.readIncident(fixture.incident.value()));
			assertEquals(HostClientException.Kind.CREDENTIAL_REVOKED, revoked.kind());
			assertFalse(revoked.getMessage().contains(fixture.incident.value()));
			assertEquals(HostConnectionState.CREDENTIAL_REVOKED, client.diagnose(device));
		}
	}

	@Test
	void unreachableHostIsDistinguished() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			HostClient client = fixture.client(device);
			fixture.session.stop();
			assertEquals(HostConnectionState.HOST_UNREACHABLE, client.diagnose(device));
			HostClientException failure = assertThrows(HostClientException.class,
					() -> client.readIncident(fixture.incident.value()));
			assertEquals(HostClientException.Kind.UNREACHABLE, failure.kind());
		}
	}

	@Test
	void wrongHostIdentityIsRejectedNotTrusted(@TempDir Path other) {
		try (HostFixture first = new HostFixture(directory); HostFixture second = new HostFixture(other)) {
			first.start();
			second.start();
			DeviceCredential device = first.pair("Tablet");
			HostStatus impostor = second.session.status();
			HostClient client = HostClient.forDevice("127.0.0.1", impostor.port(), impostor.enrollmentPort(), device);
			HostClientException failure = assertThrows(HostClientException.class,
					() -> client.readIncident(first.incident.value()));
			assertEquals(HostClientException.Kind.IDENTITY_CHANGED, failure.kind());
			assertEquals(HostConnectionState.HOST_IDENTITY_CHANGED, client.diagnose(device));
		}
	}

	@Test
	void invitationWithWrongFingerprintFailsPairing() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostInvitation real = HostInvitation.parse(fixture.session.issueInvitation());
			HostInvitation forged = new HostInvitation(real.host(), real.port(), real.enrollmentPort(),
					"0".repeat(64), real.incidentId(), real.secret(), real.expiresAt());
			HostClientException failure = assertThrows(HostClientException.class,
					() -> HostClient.pair(forged, "Tablet"));
			assertEquals(HostClientException.Kind.IDENTITY_CHANGED, failure.kind());
			assertNull(failure.code());
			assertTrue(fixture.session.devices().isEmpty());
		}
	}

	@Test
	void deviceFromAnotherAuthorityIsNotAdmitted(@TempDir Path other) {
		try (HostFixture first = new HostFixture(directory); HostFixture second = new HostFixture(other)) {
			first.start();
			second.start();
			DeviceCredential foreign = second.pair("Foreign");
			HostStatus status = first.session.status();
			DeviceCredential pinnedToFirst = foreignPinnedTo(foreign, first);
			HostClient client = HostClient.forDevice("127.0.0.1", status.port(), status.enrollmentPort(),
					pinnedToFirst);
			HostClientException failure = assertThrows(HostClientException.class,
					() -> client.readIncident(first.incident.value()));
			assertEquals(HostClientException.Kind.UNREACHABLE, failure.kind());
		}
	}

	@Test
	void invitationRejections() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostInvitation used = HostInvitation.parse(fixture.session.issueInvitation());
			HostClient.pair(used, "First");
			HostClientException reused = assertThrows(HostClientException.class, () -> HostClient.pair(used, "Again"));
			assertEquals(HostClientException.Kind.INVITATION_REJECTED, reused.kind());

			HostInvitation wrong = HostInvitation.parse(fixture.session.issueInvitation());
			HostInvitation otherIncident = new HostInvitation(wrong.host(), wrong.port(), wrong.enrollmentPort(),
					wrong.hostFingerprint(), IncidentId.newId().value(), wrong.secret(), wrong.expiresAt());
			HostClientException scope = assertThrows(HostClientException.class,
					() -> HostClient.pair(otherIncident, "Wrong"));
			assertEquals(HostClientException.Kind.INVITATION_REJECTED, scope.kind());

			HostInvitation old = HostInvitation.parse(fixture.session.issueInvitation());
			fixture.clock.advance(Duration.ofMinutes(16));
			HostClientException expired = assertThrows(HostClientException.class, () -> HostClient.pair(old, "Late"));
			assertEquals(HostClientException.Kind.INVITATION_REJECTED, expired.kind());
			assertEquals(expired.getMessage(), reused.getMessage());

			HostInvitation unknown = new HostInvitation(old.host(), old.port(), old.enrollmentPort(),
					old.hostFingerprint(), old.incidentId(), "not-a-real-secret", old.expiresAt());
			HostClientException guess = assertThrows(HostClientException.class,
					() -> HostClient.pair(unknown, "Guess"));
			assertEquals(expired.getMessage(), guess.getMessage());
			assertEquals(1, fixture.session.devices().size());
		}
	}

	@Test
	void concurrentRedemptionSucceedsOnlyOnce() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			HostConfig config = fixture.config().withPairingLimits(100, 1000);
			fixture.session = new HostSession(fixture.store, fixture.incident, config, null);
			fixture.start();
			HostInvitation invitation = HostInvitation.parse(fixture.session.issueInvitation());
			int attempts = 8;
			ExecutorService pool = Executors.newFixedThreadPool(attempts);
			CountDownLatch go = new CountDownLatch(1);
			AtomicInteger successes = new AtomicInteger();
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				String name = "Device " + i;
				Callable<Boolean> task = () -> {
					go.await();
					try {
						HostClient.pair(invitation, name);
						successes.incrementAndGet();
						return true;
					} catch (HostClientException failure) {
						return false;
					}
				};
				results.add(pool.submit(task));
			}
			go.countDown();
			for (Future<Boolean> result : results) {
				result.get(30, TimeUnit.SECONDS);
			}
			pool.shutdownNow();
			assertEquals(1, successes.get());
			assertEquals(1, fixture.session.devices().size());
		}
	}

	@Test
	void pairingIsRateLimited() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.session = new HostSession(fixture.store, fixture.incident,
					fixture.config().withPairingLimits(3, 100), null);
			fixture.start();
			HostInvitation real = HostInvitation.parse(fixture.session.issueInvitation());
			HostInvitation guess = new HostInvitation(real.host(), real.port(), real.enrollmentPort(),
					real.hostFingerprint(), real.incidentId(), "guess", real.expiresAt());
			for (int i = 0; i < 3; i++) {
				HostClientException failure = assertThrows(HostClientException.class,
						() -> HostClient.pair(guess, "Guess"));
				assertEquals(HostClientException.Kind.INVITATION_REJECTED, failure.kind());
			}
			HostClientException limited = assertThrows(HostClientException.class,
					() -> HostClient.pair(real, "Legit"));
			assertEquals(HostClientException.Kind.RATE_LIMITED, limited.kind());
			assertTrue(fixture.session.devices().isEmpty());
		}
	}

	@Test
	void preAuthStatusRevealsNoIncidentContents() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostInvitation invitation = HostInvitation.parse(fixture.session.issueInvitation());
			JsonNode status = HostClient.anonymous(invitation).status();
			assertEquals("running", status.path("hostState").asText());
			assertEquals(invitation.hostFingerprint(), status.path("hostFingerprint").asText());
			assertFalse(status.toString().contains(fixture.incident.value()));
		}
	}

	@Test
	void versionNegotiationAndRequestLimits() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			HostStatus status = fixture.session.status();
			HttpClient http = HttpClient.newBuilder().sslContext(device.sslContext()).build();
			URI uri = URI.create("https://127.0.0.1:" + status.port() + "/v1/incidents/" + fixture.incident.value());
			HttpResponse<String> old = http.send(HttpRequest.newBuilder(uri).header(
					HostProtocol.API_VERSION_HEADER, "99").header(HostProtocol.CLIENT_VERSION_HEADER, "1.0.0").build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(426, old.statusCode());
			assertTrue(old.body().contains("incompatible_client"));
			HttpResponse<String> noHeaders = http.send(HttpRequest.newBuilder(uri).build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(426, noHeaders.statusCode());

			HostClient client = fixture.client(device);
			String huge = "{\"operationId\":\"x\",\"pad\":\"" + "a".repeat(HostProtocol.MAX_REQUEST_BYTES + 10) + "\"}";
			HostClientException tooLarge = assertThrows(HostClientException.class,
					() -> client.submit(fixture.incident.value(), huge));
			assertEquals("payload_too_large", tooLarge.code());
			assertNotNull(client.changesSince(fixture.incident.value(), 0));

			HttpClient anonymous = HttpClient.newBuilder().sslContext(device.sslContext()).build();
			URI pairing = URI.create("https://127.0.0.1:" + status.enrollmentPort() + "/v1/pairing/redeem");
			HttpResponse<String> oversize = anonymous.send(HttpRequest.newBuilder(pairing)
					.POST(HttpRequest.BodyPublishers.ofString("x".repeat(HostProtocol.MAX_PAIRING_BYTES + 100))).build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(413, oversize.statusCode());
			assertTrue(oversize.body().contains("payload_too_large"));
		}
	}

	@Test
	void unknownIncidentAndAdminAreNotAvailableToDevices() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			HostClient client = fixture.client(device);
			HostClientException missing = assertThrows(HostClientException.class,
					() -> client.readIncident(IncidentId.newId().value()));
			assertEquals(404, missing.status());
			HostClientException admin = assertThrows(HostClientException.class, client::listDevices);
			assertEquals("forbidden", admin.code());
			HostClientException issue = assertThrows(HostClientException.class, client::issueInvitation);
			assertEquals("forbidden", issue.code());
		}
	}

	@Test
	void revocationSurvivesRestart() {
		DeviceCredential kept;
		DeviceCredential revoked;
		IncidentId incident;
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			incident = fixture.incident;
			kept = fixture.pair("Kept");
			revoked = fixture.pair("Revoked");
			String revokedId = fixture.session.devices().stream().filter(d -> d.name().equals("Revoked")).findFirst()
					.orElseThrow().deviceId();
			fixture.session.revoke(revokedId);
		}
		try (HostFixture restarted = new HostFixture(directory, incident)) {
			restarted.start();
			HostClient keptClient = restarted.client(kept);
			assertEquals(HostConnectionState.CONNECTED, keptClient.diagnose(kept));
			assertEquals(HostConnectionState.CREDENTIAL_REVOKED, restarted.client(revoked).diagnose(revoked));
			assertTrue(keptClient.readIncident(incident.value()).has("records"));
		}
	}

	@Test
	void startStopRestartAndStoppingStatus() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			assertEquals(HostState.RUNNING, fixture.session.state());
			DeviceCredential device = fixture.pair("Tablet");
			fixture.session.stop();
			assertEquals(HostState.STOPPED, fixture.session.state());
			fixture.start();
			assertEquals(HostConnectionState.CONNECTED, fixture.client(device).diagnose(device));

			fixture.session = new HostSession(fixture.store, fixture.incident,
					fixture.config().withStopTiming(Duration.ofSeconds(3), Duration.ofSeconds(2)), null);
			fixture.session.stop();
		}
		try (HostFixture fixture = new HostFixture(directory.resolve("slow"))) {
			fixture.session = new HostSession(fixture.store, fixture.incident,
					fixture.config().withStopTiming(Duration.ofSeconds(3), Duration.ofSeconds(2)), null);
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			HostClient client = fixture.client(device);
			Thread stopper = new Thread(fixture.session::stop);
			stopper.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (fixture.session.state() != HostState.STOPPING && System.nanoTime() < deadline) {
				Thread.sleep(10);
			}
			assertEquals("stopping", client.status().path("hostState").asText());
			HostClientException unavailable = assertThrows(HostClientException.class,
					() -> client.readIncident(fixture.incident.value()));
			assertEquals(HostClientException.Kind.HOST_UNAVAILABLE, unavailable.kind());
			stopper.join(10000);
			assertEquals(HostState.STOPPED, fixture.session.state());
		}
	}

	@Test
	void portInUseLeavesIncidentUsable() throws Exception {
		try (HostFixture fixture = new HostFixture(directory); ServerSocket busy = new ServerSocket(0, 1,
				InetAddress.getLoopbackAddress())) {
			fixture.session = new HostSession(fixture.store, fixture.incident,
					HostConfig.defaults("127.0.0.1", busy.getLocalPort(), 0).withBindAddress("127.0.0.1")
							.withClock(fixture.clock),
					null);
			HostStartException failure = assertThrows(HostStartException.class, () -> fixture.session.start());
			assertEquals(HostStartException.Reason.PORT_IN_USE, failure.reason());
			assertEquals(HostState.STOPPED, fixture.session.state());
			assertEquals(0, fixture.session.status().port());
			try (FileIncidentStore other = new FileIncidentStore(directory)) {
				assertTrue(other.lock(fixture.incident));
			}
			fixture.store.load(fixture.incident);
		}
	}

	@Test
	void enrollmentPortInUseReleasesEverything() throws Exception {
		try (HostFixture fixture = new HostFixture(directory); ServerSocket busy = new ServerSocket(0, 1,
				InetAddress.getLoopbackAddress())) {
			fixture.session = new HostSession(fixture.store, fixture.incident,
					HostConfig.defaults("127.0.0.1", 0, busy.getLocalPort()).withBindAddress("127.0.0.1")
							.withClock(fixture.clock),
					null);
			HostStartException failure = assertThrows(HostStartException.class, () -> fixture.session.start());
			assertEquals(HostStartException.Reason.PORT_IN_USE, failure.reason());
			try (FileIncidentStore other = new FileIncidentStore(directory)) {
				assertTrue(other.lock(fixture.incident));
			}
		}
	}

	@Test
	void storageFailureLeavesIncidentUsable() {
		try (HostFixture fixture = new HostFixture(directory)) {
			HostKeyStore failing = new HostKeyStore() {
				/** {@inheritDoc} */
				@Override
				public HostAuthority loadOrCreate() {
					throw new HostStorageException("disk full");
				}

				/** {@inheritDoc} */
				@Override
				public java.util.Map<String, byte[]> exportForBackup() {
					throw new HostStorageException("disk full");
				}

				/** {@inheritDoc} */
				@Override
				public void importFromBackup(java.util.Map<String, byte[]> entries) {
					throw new HostStorageException("disk full");
				}
			};
			HostStorage storage = new HostStorage(directory, failing, fixture.clock);
			HostSession session = new HostSession(fixture.store, fixture.incident, fixture.config(), null, storage);
			HostStartException failure = assertThrows(HostStartException.class, session::start);
			assertEquals(HostStartException.Reason.STORAGE_FAILURE, failure.reason());
			assertEquals(HostState.STOPPED, session.state());
			try (FileIncidentStore other = new FileIncidentStore(directory)) {
				assertTrue(other.lock(fixture.incident));
			}
		}
	}

	@Test
	void editorFlushFailureStopsStartup() {
		try (HostFixture fixture = new HostFixture(directory)) {
			HostSession session = new HostSession(fixture.store, fixture.incident, fixture.config(), () -> {
				throw new IllegalStateException("cannot save");
			});
			HostStartException failure = assertThrows(HostStartException.class, session::start);
			assertEquals(HostStartException.Reason.EDITOR_FLUSH_FAILED, failure.reason());
			assertEquals(0, session.status().port());
		}
	}

	@Test
	void secondWriterCannotUseSharedIncidentWhileHosting() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			try (FileIncidentStore other = new FileIncidentStore(directory)) {
				EditLockSupport locks = other.editLocks().orElseThrow();
				assertTrue(locks.isLockedElsewhere(fixture.incident));
				assertFalse(locks.lock(fixture.incident));
				HostSession second = new HostSession(other, fixture.incident, fixture.config(), null);
				HostStartException failure = assertThrows(HostStartException.class, second::start);
				assertEquals(HostStartException.Reason.NOT_EXCLUSIVE, failure.reason());
				AppData data = other.load(fixture.incident);
				assertThrows(RuntimeException.class, () -> other.save(fixture.incident, data));
			}
			fixture.session.stop();
			try (FileIncidentStore other = new FileIncidentStore(directory)) {
				assertTrue(other.lock(fixture.incident));
			}
		}
	}

	@Test
	void separateProcessCannotWriteSharedFilesWhileHostingButCanAfterStop() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			List<String> whileHosting = runForeignWriter(fixture);
			assertTrue(whileHosting.contains("LOCK=false"), whileHosting.toString());
			assertTrue(whileHosting.contains("SAVE=REFUSED"), whileHosting.toString());
			assertFalse(fixture.store.load(fixture.incident).getIncidentContext().getIncidentName()
					.equals("written by another process"));
			fixture.session.stop();
			List<String> afterStop = runForeignWriter(fixture);
			assertTrue(afterStop.contains("LOCK=true"), afterStop.toString());
			assertTrue(afterStop.contains("SAVE=OK"), afterStop.toString());
		}
	}

	/**
	 * Runs {@link HostForeignWriter} in a new JVM using this test's class path.
	 *
	 * @param fixture fixture whose store and incident to use
	 * @return output lines of the child process
	 * @throws Exception when the process cannot run or times out
	 */
	private static List<String> runForeignWriter(HostFixture fixture) throws Exception {
		String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		Process process = new ProcessBuilder(javaBinary, "-Djava.awt.headless=true", "-cp",
				System.getProperty("java.class.path"), HostForeignWriter.class.getName(),
				fixture.directory.toString(), fixture.incident.value()).redirectErrorStream(true).start();
		List<String> lines = new ArrayList<>();
		try (java.io.BufferedReader reader = new java.io.BufferedReader(
				new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}
		assertTrue(process.waitFor(60, TimeUnit.SECONDS), "child JVM timed out");
		return lines;
	}

	@Test
	void hostOwnedFilesAreOwnerOnly() throws IOException {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			fixture.pair("Tablet");
			if (!OwnerOnlyFiles.posix()) {
				return;
			}
			Path host = directory.resolve("host");
			Set<PosixFilePermission> expected = Set.of(PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE);
			for (String name : List.of("ca.p12", "ca.pass", "registry.json")) {
				assertEquals(expected, Files.getPosixFilePermissions(host.resolve(name)), name);
			}
			String registry = Files.readString(host.resolve("registry.json"));
			assertFalse(registry.contains("secret\""));
		}
	}

	@Test
	void devicesListShowsStateAndContact() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			DeviceCredential device = fixture.pair("Tablet");
			assertEquals(1, fixture.session.devices().size());
			assertNull(fixture.session.devices().get(0).lastContact());
			fixture.client(device).readIncident(fixture.incident.value());
			DeviceInfo info = fixture.session.devices().get(0);
			assertEquals(CredentialState.ACTIVE, info.state());
			assertTrue(info.connected());
			assertTrue(info.lastContact().isBefore(Instant.now().plusSeconds(1)));
			fixture.clock.advance(Duration.ofMinutes(5));
			assertFalse(fixture.session.devices().get(0).connected());
		}
	}

	/**
	 * Re-pins a credential issued by another authority to a host's fingerprint, simulating a device that presents
	 * a certificate the host did not sign.
	 *
	 * @param foreign credential from another authority
	 * @param host host to pin
	 * @return credential with the same key material but the host's pin
	 */
	private static DeviceCredential foreignPinnedTo(DeviceCredential foreign, HostFixture host) {
		return new DeviceCredential(foreign.keyStore(), foreign.password(),
				host.session.status().hostFingerprint(), foreign.deviceId(), foreign.expiresAt());
	}
}
