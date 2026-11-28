/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SharingMenuStateTest {
	@Test
	void clientCannotHostButCanRepair() {
		MainFrame.SharingMenuState state = MainFrame.sharingMenuState(MainFrame.SharingRole.CLIENT);
		assertFalse(state.host());
		assertFalse(state.connect());
		assertTrue(state.disconnect());
		assertFalse(state.stopSharing());
		assertTrue(state.repair());
		assertTrue(state.outbox());
	}

	@Test
	void standaloneCanHostButNotStopOrRepair() {
		MainFrame.SharingMenuState state = MainFrame.sharingMenuState(MainFrame.SharingRole.STANDALONE);
		assertTrue(state.host());
		assertTrue(state.connect());
		assertFalse(state.disconnect());
		assertFalse(state.stopSharing());
		assertFalse(state.repair());
		assertFalse(state.outbox());
	}

	@Test
	void hostCanStopSharing() {
		assertTrue(MainFrame.sharingMenuState(MainFrame.SharingRole.HOST).stopSharing());
	}

	@Test
	void hostingPinsTheSystemZoneOnceSoClientsShareIt() {
		org.sarmanagement.icsforms.model.AppData data = new org.sarmanagement.icsforms.model.AppData();
		assertTrue(MainFrame.pinIncidentTimeZone(data));
		assertFalse(data.isUseSystemTimeZone());
		assertTrue(data.getConfiguredTimeZoneId().equals(java.time.ZoneId.systemDefault().getId()));
		data.setConfiguredTimeZoneId("America/Denver");
		assertFalse(MainFrame.pinIncidentTimeZone(data));
		assertTrue(data.getConfiguredTimeZoneId().equals("America/Denver"));
	}
}
