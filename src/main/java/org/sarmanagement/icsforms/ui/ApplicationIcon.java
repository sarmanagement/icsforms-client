/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads packaged application icon derivatives for desktop windows and taskbars.
 */
public final class ApplicationIcon {
	private static final List<String> ICON_PATHS = List.of("/images/icsforms-client-16.png",
			"/images/icsforms-client-32.png", "/images/icsforms-client-64.png", "/images/icsforms-client-128.png");

	/** Prevents instantiation. */
	private ApplicationIcon() {
	}

	/**
	 * Installs all available icon sizes on a window and the platform taskbar.
	 *
	 * @param window application window.
	 */
	public static void install(Window window) {
		List<Image> icons = loadImages();
		if (icons.isEmpty()) {
			return;
		}
		window.setIconImages(icons);
		try {
			Taskbar taskbar = Taskbar.getTaskbar();
			if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
				taskbar.setIconImage(icons.get(icons.size() - 1));
			}
		} catch (UnsupportedOperationException | SecurityException exception) {
			// Window icons still apply when the platform does not expose a taskbar icon.
		}
	}

	/**
	 * Reads all valid PNG icon sizes from the application resources.
	 *
	 * @return available icon images from smallest to largest.
	 */
	static List<Image> loadImages() {
		List<Image> icons = new ArrayList<>();
		for (String path : ICON_PATHS) {
			try (var resource = ApplicationIcon.class.getResourceAsStream(path)) {
				if (resource != null) {
					Image image = ImageIO.read(resource);
					if (image != null) {
						icons.add(image);
					}
				}
			} catch (IOException exception) {
				throw new IllegalStateException("Could not load application icon: " + path, exception);
			}
		}
		return icons;
	}
}
