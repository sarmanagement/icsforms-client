/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.awt.Component;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.pairing.PairingCode;

/**
 * Collects what a device needs to join a hosted incident: normally the host address and the short pairing code, or,
 * as an advanced alternative, a pasted invitation or pairing payload.
 */
final class ConnectForm {
	/** Pairing text in a form accepted by {@link HostGateway#pair(String, String)}. */
	private final String pairingText;
	/** Device name entered by the operator. */
	private final String deviceName;

	/**
	 * Creates a result.
	 *
	 * @param pairingText text to hand to the pairing gateway
	 * @param deviceName device name shown to the host operator
	 */
	private ConnectForm(String pairingText, String deviceName) {
		this.pairingText = pairingText;
		this.deviceName = deviceName;
	}

	/**
	 * Returns the pairing text.
	 *
	 * @return text to hand to the pairing gateway
	 */
	String pairingText() {
		return pairingText;
	}

	/**
	 * Returns the device name.
	 *
	 * @return device name
	 */
	String deviceName() {
		return deviceName;
	}

	/**
	 * Shows the form and returns the entry, or null when cancelled or empty.
	 *
	 * @param parent component to center on
	 * @return the entry, or null
	 */
	static ConnectForm ask(Component parent) {
		JTextField host = new JTextField(24);
		JTextField code = new JTextField(12);
		JTextArea advanced = new JTextArea(3, 40);
		advanced.setLineWrap(true);
		JTextField device = new JTextField(System.getProperty("user.name", "device"), 20);
		Object[] form = {"Host address (name or IP, optionally :port):", host, "Pairing code shown on the host:",
				code, "Or paste an invitation or QR text (advanced):", new JScrollPane(advanced), "Device name:",
				device};
		while (true) {
			if (JOptionPane.showConfirmDialog(parent, form, "Connect to a host", JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
				return null;
			}
			String pasted = advanced.getText().strip();
			String name = device.getText().strip();
			if (!pasted.isEmpty()) {
				return new ConnectForm(pasted, name);
			}
			String problem = host.getText().isBlank() ? "Enter the host address." : null;
			try {
				PairingCode.normalize(code.getText());
			} catch (IllegalArgumentException failure) {
				problem = failure.getMessage();
			}
			if (problem != null) {
				JOptionPane.showMessageDialog(parent, problem, "Connect to a host", JOptionPane.WARNING_MESSAGE);
				continue;
			}
			return new ConnectForm(HostGateway.TYPED_PREFIX + host.getText().strip() + ":" + code.getText().strip(),
					name);
		}
	}
}
