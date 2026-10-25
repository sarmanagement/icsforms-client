/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads packaged application icon derivatives for desktop windows and taskbars.
 */
public final class ApplicationIcon {
	private static final List<String> ICON_PATHS = List.of("/images/icsforms-client-16.png",
			"/images/icsforms-client-32.png", "/images/icsforms-client-64.png", "/images/icsforms-client-128.png");

	/** Distinct, labeled theme badges for responsibility and reference windows. */
	public enum Variant {
		SAFETY("S", new Color(174, 92, 0)),
		MEDICAL("M", new Color(163, 24, 40)),
		OPERATIONS("O", new Color(0, 98, 74)),
		INCIDENT_COMMAND("I", new Color(93, 46, 133)),
		COMMUNICATIONS("C", new Color(0, 85, 153)),
		ACTIVITY_LOG("L", new Color(30, 89, 101)),
		HUMAN_REFERENCE("H", new Color(145, 30, 84)),
		CANINE_REFERENCE("K", new Color(86, 67, 27));

		private final String label;
		private final Color color;

		/**
		 * Creates a role-specific theme badge.
		 *
		 * @param label short role label.
		 * @param color theme color.
		 */
		Variant(String label, Color color) {
			this.label = label;
			this.color = color;
		}
	}

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
	 * Installs role-specific icons without replacing the main application taskbar icon.
	 *
	 * @param window role or reference window.
	 * @param variant responsibility-specific theme.
	 */
	public static void install(Window window, Variant variant) {
		window.setIconImages(loadImages(variant));
	}

	/**
	 * Adds a high-contrast role badge to each packaged application icon size.
	 *
	 * @param variant responsibility-specific theme.
	 * @return independent role icons from smallest to largest.
	 */
	static List<Image> loadImages(Variant variant) {
		List<Image> icons = new ArrayList<>();
		for (Image image : loadImages()) {
			int size = image.getWidth(null);
			BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = icon.createGraphics();
			try {
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				graphics.drawImage(image, 0, 0, null);
				int badge = Math.max(10, size * 5 / 8);
				int origin = size - badge;
				graphics.setColor(variant.color);
				graphics.fillRoundRect(origin, origin, badge, badge, size / 8, size / 8);
				graphics.setColor(Color.WHITE);
				graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, badge * 4 / 5)));
				var metrics = graphics.getFontMetrics();
				graphics.drawString(variant.label, origin + (badge - metrics.stringWidth(variant.label)) / 2,
						origin + (badge - metrics.getHeight()) / 2 + metrics.getAscent());
			} finally {
				graphics.dispose();
			}
			icons.add(icon);
		}
		return icons;
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
