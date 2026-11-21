/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies which root properties travel as the virtual root-settings record. */
class RootSettingsTest {
	@Test
	void everyRootValueThatNoRecordContainsIsCarriedAndRecordsAndMetadataAreNot() {
		JsonNode snapshot = RootSettings.snapshot(new AppData());

		assertEquals(RootSettings.ID, snapshot.get("id").asText());
		for (String name : new String[] {"iapPhase", "incidentMode", "useSystemTimeZone", "configuredTimeZoneId",
				"iapPlanningStage", "ics215Enabled", "ics205Enabled", "form205aIapPage", "openActionTrackerEnabled",
				"ics233Portrait", "clueLogIcs233Enabled", "canineRoutingBaseLocationId", "safetyReview",
				"humanMedicalReview", "canineMedicalReview", "communicationsReview",
				"medicalUnitLeaderAssignment"}) {
			assertTrue(RootSettings.propertyNames().contains(name), name);
			assertTrue(snapshot.has(name), name);
		}
		for (String name : new String[] {"incidentId", "revision", "createdAt", "updatedAt", "schemaVersion",
				"form201", "incidentContext", "activityLogs", "operationalPeriodHistory", "tCards"}) {
			assertFalse(RootSettings.propertyNames().contains(name), name);
		}
	}

	@Test
	void applyUpdatesOnlyRootValuesAndRoundTrips() {
		AppData source = new AppData();
		source.setIapPhase(IapPhase.INITIAL_RESPONSE);
		source.setIncidentMode(IncidentMode.values()[IncidentMode.values().length - 1]);
		source.setUseSystemTimeZone(false);
		source.setConfiguredTimeZoneId("America/Denver");
		AppData target = new AppData();
		target.setIncidentId("keep-me");
		target.getIncidentContext().setIncidentName("Keep");

		RootSettings.apply(target, RootSettings.snapshot(source));

		assertEquals(source.getIapPhase(), target.getIapPhase());
		assertEquals(source.getIncidentMode(), target.getIncidentMode());
		assertEquals("America/Denver", target.getConfiguredTimeZoneId());
		assertFalse(target.isUseSystemTimeZone());
		assertEquals("keep-me", target.getIncidentId());
		assertEquals("Keep", target.getIncidentContext().getIncidentName());
		assertEquals(RootSettings.snapshot(source), RootSettings.snapshot(target));
	}

	@Test
	void identityIsFixedAndImmutable() {
		RootSettings settings = new RootSettings();
		assertEquals(RootSettings.ID, settings.getId());
		assertTrue(RootSettings.isRoot(RootSettings.key()));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> settings.setId("other"));
	}
}
