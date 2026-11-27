/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConnectDialogTest {
	@Test
	void identityCombinesDeviceAndUserNames() {
		assertEquals("laptop (ben)", ConnectDialog.identity("laptop", " ben "));
		assertEquals("laptop", ConnectDialog.identity("laptop", ""));
		assertEquals("device (ben)", ConnectDialog.identity(" ", "ben"));
	}
}
