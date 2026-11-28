/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.Closeable;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.sarmanagement.icsforms.host.PairingListener;

/**
 * A small non-modal window showing the number the host operator must type in to admit this device.
 */
final class PairingCodeWindow {
	private PairingCodeWindow() {
	}

	/**
	 * Creates a listener that shows the number in a window and closes it when pairing ends. It is safe to call
	 * from a background thread.
	 *
	 * @return a pairing listener that displays the number
	 */
	static PairingListener listener() {
		return code -> {
			if (GraphicsEnvironment.isHeadless()) {
				return () -> {
				};
			}
			JDialog[] holder = new JDialog[1];
			try {
				SwingUtilities.invokeAndWait(() -> holder[0] = show(code));
			} catch (java.lang.reflect.InvocationTargetException | InterruptedException failure) {
				if (failure instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				return () -> {
				};
			}
			Closeable handle = () -> SwingUtilities.invokeLater(() -> holder[0].dispose());
			return handle;
		};
	}

	/**
	 * Builds and shows the window.
	 *
	 * @param code number to display
	 * @return the dialog
	 */
	private static JDialog show(String code) {
		JDialog dialog = new JDialog((java.awt.Frame) null, "Pairing number", false);
		JLabel number = new JLabel(code, SwingConstants.CENTER);
		number.setFont(number.getFont().deriveFont(Font.BOLD, 40f));
		dialog.add(new JLabel("Tell the host operator this number, in person:", SwingConstants.CENTER),
				java.awt.BorderLayout.NORTH);
		dialog.add(number, java.awt.BorderLayout.CENTER);
		dialog.setAlwaysOnTop(true);
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
		return dialog;
	}
}
