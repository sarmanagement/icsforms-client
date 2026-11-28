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
		assertFalse(state.stopSharing());
		assertTrue(state.repair());
		assertTrue(state.outbox());
	}

	@Test
	void standaloneCanHostButNotStopOrRepair() {
		MainFrame.SharingMenuState state = MainFrame.sharingMenuState(MainFrame.SharingRole.STANDALONE);
		assertTrue(state.host());
		assertFalse(state.stopSharing());
		assertFalse(state.repair());
		assertFalse(state.outbox());
	}

	@Test
	void hostCanStopSharing() {
		assertTrue(MainFrame.sharingMenuState(MainFrame.SharingRole.HOST).stopSharing());
	}
}
