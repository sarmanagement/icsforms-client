/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;

/**
 * Keeps dialogs inside the usable screen area, so their buttons stay reachable at any display scale, and keeps
 * them resizable. Content that cannot fit is placed in a scroll pane.
 */
public final class ScreenFit {
	private static final String WRAPPED = "icsforms.screenFit.wrapped";
	private static final int MARGIN = 24;
	private static boolean installed;

	/** Prevents instantiation. */
	private ScreenFit() {
	}

	/**
	 * Installs a global listener that fits every dialog to the screen when it opens. Calling it again has no
	 * effect.
	 */
	public static synchronized void install() {
		if (installed || GraphicsEnvironment.isHeadless()) {
			return;
		}
		installed = true;
		Toolkit.getDefaultToolkit().addAWTEventListener((AWTEvent event) -> {
			if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof Dialog dialog) {
				fit(dialog);
			}
		}, AWTEvent.WINDOW_EVENT_MASK);
	}

	/**
	 * Returns the screen area available to windows, excluding task bars and docks.
	 *
	 * @param anchor component whose screen is used, or null for the default screen
	 * @return usable bounds in window coordinates
	 */
	public static Rectangle usableBounds(Component anchor) {
		if (GraphicsEnvironment.isHeadless()) {
			return new Rectangle(0, 0, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2);
		}
		GraphicsConfiguration configuration = anchor == null ? null : anchor.getGraphicsConfiguration();
		if (configuration == null) {
			configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
					.getDefaultConfiguration();
		}
		Rectangle bounds = configuration.getBounds();
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
		return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
				bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
	}

	/**
	 * Wraps content in a scroll pane when it is too large to leave room for a dialog's frame and button row.
	 *
	 * @param content dialog content
	 * @param anchor component whose screen is used
	 * @return the content itself when it fits, otherwise a bounded scroll pane around it
	 */
	public static JComponent bounded(JComponent content, Component anchor) {
		Rectangle usable = usableBounds(anchor);
		Dimension preferred = content.getPreferredSize();
		int maxWidth = Math.max(200, usable.width - 2 * MARGIN - 40);
		int maxHeight = Math.max(150, usable.height - 2 * MARGIN - 140);
		if (preferred.width <= maxWidth && preferred.height <= maxHeight) {
			return content;
		}
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setPreferredSize(new Dimension(Math.min(preferred.width + 20, maxWidth),
				Math.min(preferred.height + 20, maxHeight)));
		return scroll;
	}

	/**
	 * Makes a dialog resizable and no larger than the usable screen, moving it fully on screen. A dialog whose
	 * content still cannot fit gets a scrolling content pane.
	 *
	 * @param window window to fit
	 */
	public static void fit(Window window) {
		if (window instanceof Dialog dialog && !dialog.isUndecorated()) {
			dialog.setResizable(true);
		}
		Rectangle usable = usableBounds(window);
		int maxWidth = usable.width - MARGIN;
		int maxHeight = usable.height - MARGIN;
		if (window instanceof JDialog dialog) {
			wrapOversizedContent(dialog, maxWidth, maxHeight);
		}
		int width = Math.min(window.getWidth(), maxWidth);
		int height = Math.min(window.getHeight(), maxHeight);
		int x = Math.max(usable.x, Math.min(window.getX(), usable.x + usable.width - width));
		int y = Math.max(usable.y, Math.min(window.getY(), usable.y + usable.height - height));
		Rectangle target = new Rectangle(x, y, width, height);
		if (!target.equals(window.getBounds())) {
			window.setBounds(target);
			window.validate();
		}
	}

	/**
	 * Puts the content pane in a scroll pane when its preferred size exceeds the screen.
	 *
	 * @param dialog dialog to adjust
	 * @param maxWidth widest permitted window
	 * @param maxHeight tallest permitted window
	 */
	private static void wrapOversizedContent(JDialog dialog, int maxWidth, int maxHeight) {
		JRootPane root = dialog.getRootPane();
		Container content = dialog.getContentPane();
		if (content instanceof JComponent component && !Boolean.TRUE.equals(root.getClientProperty(WRAPPED))
				&& !(content instanceof JScrollPane)) {
			Dimension preferred = component.getPreferredSize();
			Insets frame = dialog.getInsets();
			if (preferred.width + frame.left + frame.right > maxWidth
					|| preferred.height + frame.top + frame.bottom > maxHeight) {
				root.putClientProperty(WRAPPED, Boolean.TRUE);
				JScrollPane scroll = new JScrollPane(component);
				scroll.setBorder(null);
				dialog.setContentPane(scroll);
				dialog.validate();
			}
		}
	}
}
