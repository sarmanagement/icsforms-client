/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
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

	@Test
	void eachRoleAndReferenceHasDistinctThemeWithoutChangingMainIcons() {
		BufferedImage main = (BufferedImage) ApplicationIcon.loadImages().get(0);
		int original = Arrays.hashCode(main.getRGB(0, 0, 16, 16, null, 0, 16));
		HashSet<Integer> signatures = new HashSet<>();
		signatures.add(original);
		for (ApplicationIcon.Variant variant : ApplicationIcon.Variant.values()) {
			List<Image> icons = ApplicationIcon.loadImages(variant);
			assertEquals(List.of(16, 32, 64, 128), icons.stream().map(icon -> icon.getWidth(null)).toList());
			BufferedImage icon = (BufferedImage) icons.get(0);
			signatures.add(Arrays.hashCode(icon.getRGB(0, 0, 16, 16, null, 0, 16)));
		}
		assertEquals(ApplicationIcon.Variant.values().length + 1, signatures.size());
		assertEquals(original, Arrays.hashCode(main.getRGB(0, 0, 16, 16, null, 0, 16)));
	}
}
