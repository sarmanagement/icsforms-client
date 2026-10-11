/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that all packaged application icon sizes are available.
 */
class ApplicationIconTest {
	@Test
	void loadsPngDerivativesForCommonDesktopIconSizes() {
		List<Image> icons = ApplicationIcon.loadImages();

		assertEquals(List.of(16, 32, 64, 128),
				icons.stream().map(icon -> icon.getWidth(null)).toList());
	}
}
