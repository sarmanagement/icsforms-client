/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.sarmanagement.icsforms.host.HostDiscovery;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostReachability;
import org.sarmanagement.icsforms.host.NetworkInfo;
import org.sarmanagement.icsforms.host.PairingListener;

/**
 * The dialog a device uses to join a hosted incident. The basic view finds hosts on the local network and asks only
 * for a device name; the advanced view adds a typed host address and a pasted invitation. Once the operator chooses
 * Connect, the same dialog shows progress and the large pairing number to read out to the host operator, so no
 * other window opens or closes during pairing.
 */
final class ConnectDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	/** Name of the card holding the entry form. */
	private static final String FORM = "form";
	/** Name of the card holding connection progress. */
	private static final String PROGRESS = "progress";

	/**
	 * Performs the pairing and first load for the dialog. It runs on a background thread, and must call the
	 * supplied listener when the pairing number is known.
	 */
	@FunctionalInterface
	interface Connector {
		/**
		 * Pairs with the host and loads the first snapshot.
		 *
		 * @param pairingText text accepted by {@link HostGateway#pair(String, String, PairingListener)}
		 * @param deviceName device name shown to the host operator
		 * @param listener receives the number the host operator must type
		 * @return the connection, handed back from {@link ConnectDialog#show}; if it implements
		 *         {@link AutoCloseable} in element zero it is closed when the operator abandoned the dialog
		 */
		Object[] connect(String pairingText, String deviceName, PairingListener listener);
	}

	/** Performs the connection. */
	private final Connector connector;
	/** Switches between the form and the progress view. */
	private final CardLayout cards = new CardLayout();
	/** Holds both views. */
	private final JPanel content = new JPanel(cards);
	/** Hosts found by discovery. */
	private final DefaultListModel<HostDiscovery.Found> foundModel = new DefaultListModel<>();
	/** List of discovered hosts. */
	private final JList<HostDiscovery.Found> foundList = new JList<>(foundModel);
	/** Discovery state text. */
	private final JLabel searchStatus = new JLabel(" ");
	/** Device name entry. */
	private final JTextField deviceField = new JTextField(localHostName(), 16);
	/** User name entry. */
	private final JTextField userField = new JTextField(System.getProperty("user.name", ""), 14);
	/** Typed host address. */
	private final JTextField addressField = new JTextField(24);
	/** Pasted invitation. */
	private final JTextArea invitationArea = new JTextArea(3, 40);
	/** Tells whether the typed address is on a local network. */
	private final JLabel addressHint = new JLabel(" ");
	/** Advanced options. */
	private final JPanel advancedPanel = new JPanel();
	/** Toggles the advanced options. */
	private final JButton advancedButton = new JButton("Advanced options...");
	/** Form-level message, such as a failed attempt. */
	private final JLabel formMessage = new JLabel(" ");
	/** Progress headline. */
	private final JLabel progressStatus = new JLabel(" ", SwingConstants.CENTER);
	/** The pairing number in large print. */
	private final JLabel codeLabel = new JLabel(" ", SwingConstants.CENTER);
	/** Instructions below the pairing number. */
	private final JTextArea instructions = new JTextArea();
	/** Networks this device is on. */
	private final List<NetworkInfo.Network> networks = NetworkInfo.distinct(NetworkInfo.local());
	/** Connection produced by a successful attempt. */
	private Object[] result;
	/** Set when the operator cancelled, so a late success is released rather than used. */
	private volatile boolean abandoned;

	/**
	 * Creates the dialog.
	 *
	 * @param owner window to center on, or null
	 * @param connector performs the connection
	 */
	private ConnectDialog(Window owner, Connector connector) {
		super(owner, "Connect to a host", ModalityType.APPLICATION_MODAL);
		this.connector = connector;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			/** {@inheritDoc} */
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				cancel();
			}
		});
		content.add(buildForm(), FORM);
		content.add(buildProgress(), PROGRESS);
		add(content);
		pack();
		setMinimumSize(new java.awt.Dimension(560, 420));
		setLocationRelativeTo(owner);
	}

	/**
	 * Shows the dialog and blocks until the device is connected or the operator cancels.
	 *
	 * @param parent component to center on
	 * @param connector performs the pairing and first load
	 * @return the connection returned by the connector, or null when cancelled
	 */
	static Object[] show(Component parent, Connector connector) {
		Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
		if (owner == null && parent instanceof Window window) {
			owner = window;
		}
		ConnectDialog dialog = new ConnectDialog(owner, connector);
		dialog.search();
		dialog.setVisible(true);
		return dialog.result;
	}

	/**
	 * Builds the entry form.
	 *
	 * @return form panel
	 */
	private JPanel buildForm() {
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

		JTextArea network = readOnly("This device is on:\n" + NetworkInfo.summary(networks));
		panel.add(network, BorderLayout.NORTH);

		JPanel centre = new JPanel(new BorderLayout(6, 6));
		JPanel hosts = new JPanel(new BorderLayout(4, 4));
		hosts.setBorder(BorderFactory.createTitledBorder("Hosts on this network that are ready to add a device"));
		foundList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		foundList.setVisibleRowCount(4);
		foundList.addListSelectionListener(event -> {
			HostDiscovery.Found chosen = foundList.getSelectedValue();
			if (!event.getValueIsAdjusting() && chosen != null) {
				addressField.setText(addressOf(chosen));
			}
		});
		hosts.add(new JScrollPane(foundList), BorderLayout.CENTER);
		JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton refresh = new JButton("Find hosts on this network");
		refresh.addActionListener(event -> search());
		searchRow.add(refresh);
		searchRow.add(searchStatus);
		hosts.add(searchRow, BorderLayout.SOUTH);
		centre.add(hosts, BorderLayout.CENTER);

		JPanel lower = new JPanel();
		lower.setLayout(new BoxLayout(lower, BoxLayout.Y_AXIS));
		JPanel deviceRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		deviceRow.add(new JLabel("This device:"));
		deviceRow.add(deviceField);
		deviceRow.add(new JLabel("Your name:"));
		deviceRow.add(userField);
		lower.add(deviceRow);
		advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));
		advancedPanel.setBorder(BorderFactory.createTitledBorder("Advanced"));
		JPanel addressRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		addressRow.add(new JLabel("Host address (name or IP, optionally :port; default port 8444):"));
		addressRow.add(addressField);
		advancedPanel.add(addressRow);
		addressHint.setAlignmentX(Component.LEFT_ALIGNMENT);
		advancedPanel.add(addressHint);
		JPanel pasteRow = new JPanel(new BorderLayout());
		pasteRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		pasteRow.add(new JLabel("Or paste an invitation or QR text:"), BorderLayout.NORTH);
		invitationArea.setLineWrap(true);
		pasteRow.add(new JScrollPane(invitationArea), BorderLayout.CENTER);
		advancedPanel.add(pasteRow);
		advancedPanel.setVisible(false);
		lower.add(advancedPanel);
		addressField.getDocument().addDocumentListener(new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				updateAddressHint();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				updateAddressHint();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				updateAddressHint();
			}
		});
		centre.add(lower, BorderLayout.SOUTH);
		panel.add(centre, BorderLayout.CENTER);

		JPanel south = new JPanel(new BorderLayout());
		formMessage.setForeground(new Color(0xB00020));
		south.add(formMessage, BorderLayout.NORTH);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		advancedButton.addActionListener(event -> toggleAdvanced());
		JButton connect = new JButton("Connect");
		connect.addActionListener(event -> connect());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(event -> cancel());
		JButton troubleshoot = new JButton("Troubleshoot...");
		troubleshoot.setToolTipText("Checks whether the host can be pinged and its pairing and data ports reached");
		troubleshoot.addActionListener(event -> troubleshoot());
		buttons.add(troubleshoot);
		buttons.add(advancedButton);
		buttons.add(connect);
		buttons.add(cancel);
		south.add(buttons, BorderLayout.SOUTH);
		panel.add(south, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(connect);
		return panel;
	}

	/**
	 * Builds the progress view with the large pairing number.
	 *
	 * @return progress panel
	 */
	private JPanel buildProgress() {
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		progressStatus.setFont(progressStatus.getFont().deriveFont(Font.BOLD, 16f));
		panel.add(progressStatus, BorderLayout.NORTH);
		codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 72f));
		panel.add(codeLabel, BorderLayout.CENTER);
		instructions.setEditable(false);
		instructions.setLineWrap(true);
		instructions.setWrapStyleWord(true);
		instructions.setOpaque(false);
		instructions.setFont(instructions.getFont().deriveFont(14f));
		JPanel south = new JPanel(new BorderLayout());
		south.add(instructions, BorderLayout.CENTER);
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(event -> cancel());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(cancel);
		south.add(buttons, BorderLayout.SOUTH);
		panel.add(south, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Creates a non-editable wrapped text area that looks like a label.
	 *
	 * @param text text to show
	 * @return text area
	 */
	private static JTextArea readOnly(String text) {
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		return area;
	}

	/**
	 * Formats a discovered host as a typed address.
	 *
	 * @param found discovered host
	 * @return address with port, bracketed for IPv6
	 */
	static String addressOf(HostDiscovery.Found found) {
		String address = found.address();
		return (address.contains(":") ? "[" + address + "]" : address) + ":" + found.enrollmentPort();
	}

	/**
	 * Returns this computer's host name, used to identify the device to the host operator.
	 *
	 * @return host name, or {@code device} when it cannot be determined
	 */
	private static String localHostName() {
		try {
			return java.net.InetAddress.getLocalHost().getHostName();
		} catch (java.net.UnknownHostException failure) {
			return "device";
		}
	}

	/**
	 * Combines the device and user names into the name the host operator sees.
	 *
	 * @param device device (host) name
	 * @param user user name, possibly blank
	 * @return {@code device (user)}, or just the device when no user is given
	 */
	static String identity(String device, String user) {
		String host = device.strip().isEmpty() ? "device" : device.strip();
		return user.strip().isEmpty() ? host : host + " (" + user.strip() + ")";
	}

	/** Shows or hides the advanced options. */
	private void toggleAdvanced() {
		boolean show = !advancedPanel.isVisible();
		advancedPanel.setVisible(show);
		advancedButton.setText(show ? "Basic options" : "Advanced options...");
		pack();
	}

	/** Explains whether the typed address is on a network this device is on. */
	private void updateAddressHint() {
		String typed = addressField.getText().strip();
		if (typed.isEmpty()) {
			addressHint.setText(" ");
			return;
		}
		Optional<NetworkInfo.Network> match = NetworkInfo.networkContaining(typed, networks);
		if (match.isPresent()) {
			addressHint.setText("On this device's network " + match.get().networkText() + " (" + match.get().interfaceName() + ").");
		} else if (typed.matches("\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?")) {
			addressHint.setText("Not on any network this device is on; check that both devices use the same network.");
		} else {
			addressHint.setText(" ");
		}
	}

	/** Browses for hosts with an open pairing window and fills the list. */
	private void search() {
		searchStatus.setText("Searching...");
		new SwingWorker<List<HostDiscovery.Found>, Void>() {
			/** {@inheritDoc} */
			@Override
			protected List<HostDiscovery.Found> doInBackground() {
				return HostDiscovery.create().browse(Duration.ofSeconds(3));
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				try {
					List<HostDiscovery.Found> found = get();
					foundModel.clear();
					found.forEach(foundModel::addElement);
					if (found.isEmpty()) {
						searchStatus.setText("No host is ready. On the host choose Start adding a device, or use Advanced options to type its address.");
					} else {
						searchStatus.setText(found.size() + " found.");
						foundList.setSelectedIndex(0);
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.util.concurrent.ExecutionException failure) {
					searchStatus.setText("Host discovery is not available; use Advanced options to type the host address.");
				}
			}
		}.execute();
	}

	/**
	 * Checks whether the chosen host can be reached and shows the result: whether it can be pinged and whether its
	 * pairing and data ports accept connections. The host is taken from a pasted invitation or QR text, then a
	 * typed address, then the selected discovered host.
	 */
	private void troubleshoot() {
		String entered = invitationArea.getText().strip();
		if (entered.isEmpty()) {
			entered = addressField.getText().strip();
		}
		if (entered.isEmpty() && foundList.getSelectedValue() != null) {
			entered = addressOf(foundList.getSelectedValue());
		}
		HostReachability.Target target;
		try {
			target = HostReachability.parse(entered);
		} catch (IllegalArgumentException failure) {
			formMessage.setText(entered.isEmpty()
					? "Select a host in the list, or open Advanced options and enter its address, to troubleshoot."
					: failure.getMessage());
			return;
		}
		formMessage.setText(" ");
		showTroubleshooting(target);
	}

	/**
	 * Runs the reachability checks in the background and shows them in a dialog that opens at once and fills in
	 * when they finish.
	 *
	 * @param target host to check
	 */
	private void showTroubleshooting(HostReachability.Target target) {
		JTextArea report = readOnly("Checking " + target.host() + "...");
		report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, report.getFont().getSize()));
		JDialog dialog = new JDialog(this, "Troubleshoot the connection", ModalityType.DOCUMENT_MODAL);
		dialog.setLayout(new BorderLayout(8, 8));
		JScrollPane scroll = new JScrollPane(report);
		scroll.setPreferredSize(new java.awt.Dimension(640, 220));
		dialog.add(scroll, BorderLayout.CENTER);
		JButton close = new JButton("Close");
		close.addActionListener(event -> dialog.dispose());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(close);
		dialog.add(buttons, BorderLayout.SOUTH);
		dialog.pack();
		dialog.setLocationRelativeTo(this);
		new SwingWorker<List<HostReachability.Step>, Void>() {
			/** {@inheritDoc} */
			@Override
			protected List<HostReachability.Step> doInBackground() {
				return HostReachability.check(target.host(), target.pairingPort(), target.dataPort(),
						HostReachability.DEFAULT_TIMEOUT_MILLIS);
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				try {
					List<HostReachability.Step> steps = get();
					String sameNetwork = NetworkInfo.networkContaining(target.host(), networks)
							.map(network -> "This device and the host are both on " + network.networkText() + " ("
									+ network.interfaceName() + ").\n")
							.orElse("");
					report.setText(sameNetwork + HostReachability.describe(steps) + (HostReachability.allPassed(steps)
							? "\nThe host can be reached. If connecting still fails, make sure the host is sharing "
									+ "and devices can join, then try again."
							: "\nFix the failed step first; the host cannot be paired with until it passes."));
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.util.concurrent.ExecutionException failure) {
					report.setText("The checks could not run: " + failure.getCause());
				}
			}
		}.execute();
		dialog.setVisible(true);
	}

	/** Validates the form and starts pairing. */
	private void connect() {
		String pasted = invitationArea.getText().strip();
		String typed = addressField.getText().strip();
		String text;
		if (!pasted.isEmpty()) {
			text = pasted;
		} else if (!typed.isEmpty()) {
			text = HostGateway.TYPED_PREFIX + typed;
		} else {
			formMessage.setText("Select a host in the list, or open Advanced options and enter its address.");
			return;
		}
		String name = identity(deviceField.getText(), userField.getText());
		formMessage.setText(" ");
		codeLabel.setText(" ");
		instructions.setText("");
		progressStatus.setText("Contacting the host...");
		cards.show(content, PROGRESS);
		runConnection(text, name);
	}

	/**
	 * Runs the connection on a background worker and reports each stage in this dialog.
	 *
	 * @param text pairing text
	 * @param name device name
	 */
	private void runConnection(String text, String name) {
		PairingListener listener = code -> {
			SwingUtilities.invokeLater(() -> showCode(code));
			return (Closeable) () -> SwingUtilities.invokeLater(this::codeFinished);
		};
		new SwingWorker<Object[], Void>() {
			/** {@inheritDoc} */
			@Override
			protected Object[] doInBackground() {
				return connector.connect(text, name, listener);
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				try {
					Object[] connection = get();
					if (abandoned) {
						release(connection);
						return;
					}
					result = connection;
					dispose();
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.util.concurrent.ExecutionException failure) {
					if (!abandoned) {
						failed(failure.getCause());
					}
				}
			}
		}.execute();
	}

	/**
	 * Shows the pairing number in large print with instructions.
	 *
	 * @param code pairing number
	 */
	private void showCode(String code) {
		progressStatus.setText("Waiting for the host operator to enter this number");
		codeLabel.setText(code);
		instructions.setText("Tell the host operator this number, in person.\n\nOn the host: open the sharing dialog, "
				+ "choose Start adding a device, type this number in the number field and press Confirm number. "
				+ "This window continues by itself once the host confirms.");
		pack();
	}

	/** Reports that the host has finished with the number, and the first load is running. */
	private void codeFinished() {
		if (result == null && !abandoned) {
			progressStatus.setText("The host confirmed this device. Loading the incident...");
			codeLabel.setText(" ");
			instructions.setText("");
		}
	}

	/**
	 * Returns to the form with the reason a connection attempt failed.
	 *
	 * @param cause failure from the connector
	 */
	private void failed(Throwable cause) {
		String message = cause == null || cause.getMessage() == null ? String.valueOf(cause) : cause.getMessage();
		formMessage.setText("Could not connect: " + message);
		cards.show(content, FORM);
	}

	/** Abandons the dialog; a connection that completes later is released. */
	private void cancel() {
		abandoned = true;
		result = null;
		dispose();
	}

	/**
	 * Closes a connection nobody will use.
	 *
	 * @param connection the connector's result
	 */
	private static void release(Object[] connection) {
		if (connection != null && connection.length > 0 && connection[0] instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception ignored) {
				// Nothing further can be done for a connection that was already abandoned.
			}
		}
	}
}
