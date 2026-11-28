/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.pairing.PairingSuites;

/** End-to-end in-person pairing against a live loopback host. */
class ShortPairingTest {
	@TempDir
	Path directory;

	/**
	 * One in-flight device.
	 *
	 * @param result pairing outcome
	 * @param shown the number the device displays
	 */
	private record Device(CompletableFuture<HostClient.ShortPairingResult> result, CompletableFuture<String> shown) {
	}

	/**
	 * Starts a device on a background thread.
	 *
	 * @param offer the host's open window
	 * @param pinned fingerprint read from a QR code, or null for typed entry
	 * @param name device name
	 * @return the running device
	 */
	private static Device startDevice(HostClient.PairingOffer offer, String pinned, String name) {
		CompletableFuture<String> shown = new CompletableFuture<>();
		CompletableFuture<HostClient.ShortPairingResult> result = CompletableFuture.supplyAsync(
				() -> HostClient.pairInPerson("127.0.0.1", offer.enrollmentPort(), pinned, name,
						PairingSuites.defaults(), code -> {
							shown.complete(code);
							return () -> {
							};
						}));
		result.whenComplete((r, failure) -> {
			if (failure != null) {
				shown.completeExceptionally(failure);
			}
		});
		return new Device(result, shown);
	}

	/**
	 * Waits for a device to display its number.
	 *
	 * @param device the device
	 * @return the displayed number
	 * @throws Exception when it is not displayed in time
	 */
	private static String number(Device device) throws Exception {
		return device.shown().get(20, TimeUnit.SECONDS);
	}

	/**
	 * Returns a well-formed number that differs from the given one.
	 *
	 * @param right the correct number
	 * @return a wrong number
	 */
	private static String wrong(String right) {
		return "0000AA".equals(right) ? "0001AA" : "0000AA";
	}

	@Test
	void pairsWithTypedHostAndWithPinnedFingerprint() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			Device typed = startDevice(offer, null, "typed");
			assertEquals("confirmed", fixture.session.enterPairingCode(number(typed)));
			HostClient.ShortPairingResult result = typed.result().get(20, TimeUnit.SECONDS);
			assertEquals(fixture.incident.value(), result.incidentId());
			HostClient client = HostClient.forDevice("127.0.0.1", result.port(), offer.enrollmentPort(),
					result.credential());
			assertNotNull(client.readIncident(fixture.incident.value()));
			assertFalse(fixture.session.pairingState().open());
			HostClient.PairingOffer second = fixture.session.openPairing();
			Device pinned = startDevice(second, second.fingerprint(), "pinned");
			assertEquals("confirmed", fixture.session.enterPairingCode(number(pinned).toLowerCase()));
			assertNotNull(pinned.result().get(20, TimeUnit.SECONDS).credential());
		}
	}

	@Test
	void wrongNumbersBurnTheWindow() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			for (int i = 0; i < 2; i++) {
				Device device = startDevice(offer, null, "bad" + i);
				assertEquals("mismatch", fixture.session.enterPairingCode(wrong(number(device))));
				assertThrows(ExecutionException.class, () -> device.result().get(20, TimeUnit.SECONDS));
			}
			Device last = startDevice(offer, null, "last");
			String right = number(last);
			assertEquals("burned", fixture.session.enterPairingCode(wrong(right)));
			assertEquals("none", fixture.session.enterPairingCode(right));
			assertFalse(fixture.session.pairingState().open());
		}
	}

	@Test
	void secondDeviceIsToldBusy() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			Device first = startDevice(offer, null, "first");
			number(first);
			Device second = startDevice(offer, null, "second");
			ExecutionException failure = assertThrows(ExecutionException.class,
					() -> second.result().get(20, TimeUnit.SECONDS));
			assertTrue(failure.getCause() instanceof HostClientException);
			assertEquals("confirmed", fixture.session.enterPairingCode(number(first)));
			assertNotNull(first.result().get(20, TimeUnit.SECONDS).credential());
		}
	}

	@Test
	void expiredOrClosedWindowRefuses() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			fixture.clock.advance(Duration.ofDays(1));
			assertThrows(HostClientException.class, () -> HostClient.pairInPerson("127.0.0.1",
					offer.enrollmentPort(), null, "late", PairingSuites.defaults(), PairingListener.NONE));
			HostClient.PairingOffer reopened = fixture.session.openPairing();
			fixture.session.closePairing();
			assertThrows(HostClientException.class, () -> HostClient.pairInPerson("127.0.0.1",
					reopened.enrollmentPort(), null, "closed", PairingSuites.defaults(), PairingListener.NONE));
		}
	}

	@Test
	void wrongPinnedFingerprintFails() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			assertThrows(HostClientException.class, () -> HostClient.pairInPerson("127.0.0.1",
					offer.enrollmentPort(), "ab".repeat(32), "mitm", PairingSuites.defaults(),
					PairingListener.NONE));
		}
	}

	@Test
	void gatewayAcceptsTypedEntryAndQrPayload() throws Exception {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			CompletableFuture<String> shown = new CompletableFuture<>();
			CompletableFuture<?> typed = CompletableFuture.supplyAsync(() -> HostGateway
					.pair(HostGateway.TYPED_PREFIX + "127.0.0.1:" + offer.enrollmentPort(), "typed", code -> {
						shown.complete(code);
						return () -> {
						};
					}));
			assertEquals("confirmed", fixture.session.enterPairingCode(shown.get(20, TimeUnit.SECONDS)));
			assertNotNull(typed.get(20, TimeUnit.SECONDS));
			HostClient.PairingOffer second = fixture.session.openPairing();
			CompletableFuture<String> shown2 = new CompletableFuture<>();
			CompletableFuture<?> qr = CompletableFuture.supplyAsync(() -> HostGateway
					.pair(second.payload().toText(), "qr", code -> {
						shown2.complete(code);
						return () -> {
						};
					}));
			assertEquals("confirmed", fixture.session.enterPairingCode(shown2.get(20, TimeUnit.SECONDS)));
			assertNotNull(qr.get(20, TimeUnit.SECONDS));
		}
	}
}
