/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

/** Unit tests for invitations, version comparison and rate limiting. */
class HostProtocolTest {
	@Test
	void invitationRoundTripsAndRedactsSecret() {
		HostInvitation invitation = new HostInvitation("host.example", 8443, 8444, "ab".repeat(32), "incident-1",
				"top-secret", Instant.parse("2030-01-01T00:00:00Z"));
		HostInvitation parsed = HostInvitation.parse("  " + invitation.toText() + "\n");
		assertEquals(invitation, parsed);
		assertFalse(invitation.toString().contains("top-secret"));
		assertThrows(IllegalArgumentException.class, () -> HostInvitation.parse("garbage"));
		assertThrows(IllegalArgumentException.class, () -> HostInvitation.parse("ICSFORMS-INVITE-1:@@@"));
	}

	@Test
	void versionComparisonIgnoresQualifiers() {
		assertTrue(HostProtocol.atLeast("0.1.0", "0.1.0"));
		assertTrue(HostProtocol.atLeast("1.0.0-SNAPSHOT", "0.9.9"));
		assertFalse(HostProtocol.atLeast("0.0.9", "0.1.0"));
		assertFalse(HostProtocol.atLeast(null, "0.1.0"));
	}

	@Test
	void rateLimiterSlidesWindowPerKey() {
		MutableClock clock = new MutableClock();
		RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);
		assertTrue(limiter.tryAcquire("a"));
		assertTrue(limiter.tryAcquire("a"));
		assertFalse(limiter.tryAcquire("a"));
		assertTrue(limiter.tryAcquire("b"));
		clock.advance(Duration.ofSeconds(61));
		assertTrue(limiter.tryAcquire("a"));
	}
}
