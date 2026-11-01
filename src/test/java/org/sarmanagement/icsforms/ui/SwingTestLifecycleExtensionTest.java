/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Verifies that asynchronous Swing exceptions are captured for test teardown. */
class SwingTestLifecycleExtensionTest {
	@Test
	void capturesUncaughtEventDispatchExceptions() throws Exception {
		SwingUtilities.invokeLater(() -> {
			throw new IllegalStateException("deliberate EDT failure probe");
		});
		SwingUtilities.invokeAndWait(() -> {
		});

		var failures = SwingTestLifecycleExtension.drainCapturedFailures();

		assertEquals(1, failures.size());
		assertInstanceOf(IllegalStateException.class, failures.get(0).getCause());
	}
}
