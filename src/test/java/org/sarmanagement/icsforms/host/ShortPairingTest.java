/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.pairing.PairingSuites;

/** End-to-end short-code pairing against a live loopback host. */
class ShortPairingTest {
	@TempDir
	Path directory;

	/** A correct code, with and without a pinned fingerprint, yields a working credential. */
	@Test
	void pairsWithTypedCodeAndWithPinnedFingerprint() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			HostClient.ShortPairingResult typed = HostClient.pairWithCode("127.0.0.1", offer.enrollmentPort(), null,
					offer.code(), "typed", PairingSuites.defaults());
			assertNotNull(typed.credential());
			assertEquals(fixture.incident.value(), typed.incidentId());
			HostClient client = HostClient.forDevice("127.0.0.1", typed.port(), offer.enrollmentPort(),
					typed.credential());
			assertNotNull(client.readIncident(fixture.incident.value()));
			// the window is single use
			assertThrows(HostClientException.class, () -> HostClient.pairWithCode("127.0.0.1", offer.enrollmentPort(),
					offer.fingerprint(), offer.code(), "again", PairingSuites.defaults()));
			HostClient.PairingOffer second = fixture.session.openPairing();
			HostClient.ShortPairingResult pinned = HostClient.pairWithCode("127.0.0.1", second.enrollmentPort(),
					second.fingerprint(), second.code(), "pinned", PairingSuites.defaults());
			assertNotNull(pinned.credential());
		}
	}

	/** A wrong code fails, and three failures burn the window even for the right code. */
	@Test
	void wrongCodesBurnTheWindow() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			for (int i = 0; i < 3; i++) {
				assertThrows(HostClientException.class, () -> HostClient.pairWithCode("127.0.0.1",
						offer.enrollmentPort(), null, "00000000", "bad", PairingSuites.defaults()));
			}
			assertThrows(HostClientException.class, () -> HostClient.pairWithCode("127.0.0.1", offer.enrollmentPort(),
					null, offer.code(), "late", PairingSuites.defaults()));
		}
	}

	/** An expired window and a closed window both refuse the right code. */
	@Test
	void expiredOrClosedWindowRefuses() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			fixture.clock.advance(Duration.ofDays(1));
			assertThrows(HostClientException.class, () -> HostClient.pairWithCode("127.0.0.1", offer.enrollmentPort(),
					null, offer.code(), "late", PairingSuites.defaults()));
			HostClient.PairingOffer reopened = fixture.session.openPairing();
			fixture.session.closePairing();
			assertThrows(HostClientException.class, () -> HostClient.pairWithCode("127.0.0.1",
					reopened.enrollmentPort(), null, reopened.code(), "closed", PairingSuites.defaults()));
		}
	}

	/** A pinned fingerprint that is not the host's fails before any code is sent. */
	@Test
	void wrongPinnedFingerprintFails() {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			HostClient.PairingOffer offer = fixture.session.openPairing();
			assertFalse(offer.toString().contains(offer.code()));
			assertThrows(HostClientException.class, () -> HostClient.pairWithCode("127.0.0.1", offer.enrollmentPort(),
					"ab".repeat(32), offer.code(), "mitm", PairingSuites.defaults()));
		}
	}
}
