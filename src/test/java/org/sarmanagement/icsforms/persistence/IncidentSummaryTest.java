/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.IapPhase;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that deprecated path conveniences never manufacture remote filesystem identities. */
class IncidentSummaryTest {
	@Test
	void remoteRelativeAndMalformedLocationsHaveNoLocalPath() {
		for (String location : new String[]{"server:incident-collection", "https://example.invalid/incidents",
				"relative.json", "file:relative", "\u0000", null}) {
			IncidentSummary summary = new IncidentSummary(location, "Remote", IapPhase.PRE_OP,
					Instant.EPOCH, IncidentId.newId(), "", new RevisionToken("etag/value"));
			assertNull(summary.path());
		}
	}

	@Test
	void absolutePathAndFileUriLocationsRemainCompatible() {
		Path local = Path.of("incident.json").toAbsolutePath();
		for (String location : new String[]{local.toString(), local.toUri().toString()}) {
			IncidentSummary summary = new IncidentSummary(location, "Local", IapPhase.PRE_OP,
					Instant.EPOCH, IncidentId.newId(), "", new RevisionToken("file/value"));
			assertEquals(local, summary.path());
		}
	}

	@Test
	void legacyConstructorsRetainExactPathsAndIndependentDisplayLabels() {
		for (Path local : new Path[]{Path.of("incident.json"), Path.of("incident.json").toAbsolutePath()}) {
			IncidentSummary oldFour = new IncidentSummary(local, "Local", IapPhase.PRE_OP, Instant.EPOCH);
			IncidentSummary oldEight = new IncidentSummary(local, "Local", IapPhase.PRE_OP, Instant.EPOCH,
					IncidentId.newId(), "Number", 7, "Unrelated label");
			assertEquals(local, oldFour.path());
			assertEquals(local, oldEight.path());
			assertEquals("Unrelated label", oldEight.storeLocation());
			assertEquals(new RevisionToken("7"), oldEight.revision());
		}
	}

	@Test
	void neutralSummariesRetainValueEqualityWithoutRequiringPaths() {
		IncidentId id = IncidentId.newId();
		IncidentSummary first = new IncidentSummary("server:collection", "Remote", IapPhase.PRE_OP,
				Instant.EPOCH, id, "Number", new RevisionToken("etag/value"));
		IncidentSummary equal = new IncidentSummary("server:collection", "Remote", IapPhase.PRE_OP,
				Instant.EPOCH, id, "Number", new RevisionToken("etag/value"));
		IncidentSummary changed = new IncidentSummary("server:collection", "Remote", IapPhase.PRE_OP,
				Instant.EPOCH, id, "Number", new RevisionToken("etag/changed"));
		assertEquals(first, equal);
		assertEquals(first.hashCode(), equal.hashCode());
		assertNotEquals(first, changed);
		assertNotEquals(first, null);
		assertNotEquals(first, "Remote");
		assertTrue(first.toString().contains("storeLocation=server:collection"));
		assertNull(first.path());
	}
}
