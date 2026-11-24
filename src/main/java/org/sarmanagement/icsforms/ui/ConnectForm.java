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

/**
 * Collects what a device needs to join a hosted incident: normally the host address (the device then shows a number for the host operator to type in), or,
 * as an advanced alternative, a pasted invitation or pairing payload.
 */
final class ConnectForm {
	/** Pairing text in a form accepted by {@link HostGateway#pair(String, String, org.sarmanagement.icsforms.host.PairingListener)}. */
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
		JTextArea advanced = new JTextArea(3, 40);
		advanced.setLineWrap(true);
		JTextField device = new JTextField(System.getProperty("user.name", "device"), 20);
		javax.swing.JButton find = new javax.swing.JButton("Find hosts on this network");
		find.addActionListener(event -> findHosts(parent, find, host));
		Object[] form = {"Host address (name or IP, optionally :port):", host, find, "Or paste an invitation or QR text (advanced):", new JScrollPane(advanced), "Device name:",
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
			if (host.getText().isBlank()) {
				JOptionPane.showMessageDialog(parent, "Enter the host address.", "Connect to a host",
						JOptionPane.WARNING_MESSAGE);
				continue;
			}
			return new ConnectForm(HostGateway.TYPED_PREFIX + host.getText().strip(), name);
		}
	}

	/**
	 * Browses for hosts with an open pairing window off the event thread and lets the operator pick one.
	 *
	 * @param parent component to center on
	 * @param button button to disable while browsing
	 * @param host field to fill with the chosen address
	 */
	private static void findHosts(Component parent, javax.swing.JButton button, JTextField host) {
		button.setEnabled(false);
		new javax.swing.SwingWorker<java.util.List<org.sarmanagement.icsforms.host.HostDiscovery.Found>, Void>() {
			/** {@inheritDoc} */
			@Override
			protected java.util.List<org.sarmanagement.icsforms.host.HostDiscovery.Found> doInBackground() {
				return org.sarmanagement.icsforms.host.HostDiscovery.create().browse(java.time.Duration.ofSeconds(3));
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				button.setEnabled(true);
				try {
					java.util.List<org.sarmanagement.icsforms.host.HostDiscovery.Found> found = get();
					if (found.isEmpty()) {
						JOptionPane.showMessageDialog(parent, "No host with an open pairing window was found. "
								+ "Type the address shown on the host instead.", "Connect to a host",
								JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					String[] labels = found.stream().map(f -> f.name() + " (" + f.address() + ")")
							.toArray(String[]::new);
					Object choice = JOptionPane.showInputDialog(parent, "Hosts found:", "Connect to a host",
							JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
					for (int i = 0; i < labels.length; i++) {
						if (labels[i].equals(choice)) {
							String address = found.get(i).address();
							host.setText((address.contains(":") ? "[" + address + "]" : address) + ":"
									+ found.get(i).enrollmentPort());
						}
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.util.concurrent.ExecutionException failure) {
					JOptionPane.showMessageDialog(parent, "Host discovery is not available.", "Connect to a host",
							JOptionPane.WARNING_MESSAGE);
				}
			}
		}.execute();
	}
}
