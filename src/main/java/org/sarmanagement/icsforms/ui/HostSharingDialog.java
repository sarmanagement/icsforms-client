/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.host.DeviceInfo;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.host.HostState;
import org.sarmanagement.icsforms.host.HostStatus;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Lets the host operator start and stop sharing the active incident, issue invitations and revoke devices.
 * Closing the dialog never stops sharing; only the Stop button or an explicit host shutdown does.
 */
public class HostSharingDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final int DEFAULT_PORT = 8443;
	private static final int DEFAULT_ENROLLMENT_PORT = 8444;
	private static final int REFRESH_MILLIS = 2000;

	private final transient IncidentStore store;
	private final transient IncidentId incident;
	private final transient Runnable flushEditors;
	private transient HostSession session;
	private final JLabel stateLabel = new JLabel("Not sharing");
	private final JTextField hostField = new JTextField(defaultHost(), 18);
	private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_PORT, 1, 65535, 1));
	private final JSpinner enrollmentSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_ENROLLMENT_PORT, 1, 65535, 1));
	private final JButton startButton = new JButton("Start sharing");
	private final JButton stopButton = new JButton("Stop sharing");
	private final JButton inviteButton = new JButton("Create invitation");
	private final JButton revokeButton = new JButton("Revoke selected device");
	private final JTextArea invitationArea = new JTextArea(4, 48);
	private final DefaultTableModel deviceModel = new DefaultTableModel(
			new Object[] {"Device", "State", "Expires", "Last contact", "Connected"}, 0) {
		private static final long serialVersionUID = 1L;

		/**
		 * Keeps the device list read-only.
		 *
		 * @param row row index
		 * @param column column index
		 * @return always false
		 */
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JTable deviceTable = new JTable(deviceModel);
	private final Timer refreshTimer = new Timer(REFRESH_MILLIS, event -> refresh());
	private transient List<DeviceInfo> shownDevices = List.of();

	/**
	 * Creates the dialog.
	 *
	 * @param owner owning frame
	 * @param store incident store
	 * @param incident incident to share
	 * @param flushEditors callback that saves pending editors and throws when they cannot be saved
	 */
	public HostSharingDialog(Frame owner, IncidentStore store, IncidentId incident, Runnable flushEditors) {
		super(owner, "Host this incident", false);
		this.store = store;
		this.incident = incident;
		this.flushEditors = flushEditors;
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		buildLayout();
		refresh();
		refreshTimer.start();
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Returns the best available advertised host name.
	 *
	 * @return local host name or loopback address
	 */
	private static String defaultHost() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException failure) {
			return "localhost";
		}
	}

	/**
	 * Builds the dialog contents.
	 */
	private void buildLayout() {
		JPanel top = new JPanel(new GridLayout(0, 1));
		JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT));
		settings.add(new JLabel("Address clients use:"));
		settings.add(hostField);
		settings.add(new JLabel("Port:"));
		settings.add(portSpinner);
		settings.add(new JLabel("Pairing port:"));
		settings.add(enrollmentSpinner);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttons.add(startButton);
		buttons.add(stopButton);
		buttons.add(stateLabel);
		top.add(settings);
		top.add(buttons);
		JTextArea note = new JTextArea("Both ports must be allowed through the host firewall. Invitations are "
				+ "single-use and short-lived; treat their text as a secret. Only this incident is shared.");
		note.setEditable(false);
		note.setLineWrap(true);
		note.setWrapStyleWord(true);
		note.setOpaque(false);
		top.add(note);

		invitationArea.setEditable(false);
		invitationArea.setLineWrap(true);
		JPanel invitePanel = new JPanel(new BorderLayout());
		invitePanel.setBorder(BorderFactory.createTitledBorder("Invitation (copy and send to the device operator)"));
		invitePanel.add(new JScrollPane(invitationArea), BorderLayout.CENTER);
		invitePanel.add(inviteButton, BorderLayout.SOUTH);

		deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JPanel devices = new JPanel(new BorderLayout());
		devices.setBorder(BorderFactory.createTitledBorder("Paired devices"));
		devices.add(new JScrollPane(deviceTable), BorderLayout.CENTER);
		devices.add(revokeButton, BorderLayout.SOUTH);

		JPanel center = new JPanel(new GridLayout(0, 1));
		center.add(invitePanel);
		center.add(devices);
		add(top, BorderLayout.NORTH);
		add(center, BorderLayout.CENTER);

		startButton.addActionListener(event -> startSharing());
		stopButton.addActionListener(event -> stopSharing());
		inviteButton.addActionListener(event -> issueInvitation());
		revokeButton.addActionListener(event -> revokeSelected());
	}

	/**
	 * Returns whether sharing is active or starting.
	 *
	 * @return true when a session is running or stopping
	 */
	public boolean isSharing() {
		HostSession current = session;
		if (current == null) {
			return false;
		}
		HostState state = current.state();
		return state == HostState.RUNNING || state == HostState.STARTING || state == HostState.STOPPING;
	}

	/**
	 * Returns how many paired devices have recently contacted the host.
	 *
	 * @return connected device count, or zero when not sharing
	 */
	public int connectedDeviceCount() {
		HostSession current = session;
		return current == null || !isSharing() ? 0 : current.connectedDeviceCount();
	}

	/**
	 * Starts sharing after flushing editors; a failure leaves the incident usable and is shown.
	 */
	private void startSharing() {
		if (isSharing()) {
			return;
		}
		try {
			HostConfig config = HostConfig.defaults(hostField.getText().strip(), (Integer) portSpinner.getValue(),
					(Integer) enrollmentSpinner.getValue());
			HostSession candidate = new HostSession(store, incident, config, flushEditors);
			candidate.start();
			session = candidate;
			invitationArea.setText("");
		} catch (RuntimeException failure) {
			session = null;
			JOptionPane.showMessageDialog(this, "Sharing could not be started; the incident is unchanged.\n"
					+ failure.getMessage(), "Host this incident", JOptionPane.ERROR_MESSAGE);
		}
		refresh();
	}

	/**
	 * Stops sharing after warning when devices are connected.
	 */
	private void stopSharing() {
		if (session == null) {
			return;
		}
		if (!confirmStop()) {
			return;
		}
		stopAsync(null);
	}

	/**
	 * Confirms stopping when clients are connected.
	 *
	 * @return true when sharing may stop
	 */
	public boolean confirmStop() {
		int connected = connectedDeviceCount();
		if (connected == 0) {
			return true;
		}
		return JOptionPane.showConfirmDialog(this, connected + " device(s) are connected. Stopping sharing tells "
				+ "them the host is stopping and ends their access. Stop sharing?", "Stop sharing",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	/**
	 * Stops the session on a worker thread so the window stays responsive during the grace period.
	 *
	 * @param afterStop callback run on the event thread once stopped, or null
	 */
	public void stopAsync(Runnable afterStop) {
		HostSession current = session;
		if (current == null) {
			if (afterStop != null) {
				afterStop.run();
			}
			return;
		}
		stopButton.setEnabled(false);
		new SwingWorker<Void, Void>() {
			/**
			 * Stops the session off the event thread.
			 *
			 * @return nothing
			 */
			@Override
			protected Void doInBackground() {
				current.stop();
				return null;
			}

			/**
			 * Refreshes the display and runs the follow-up action.
			 */
			@Override
			protected void done() {
				session = null;
				refresh();
				if (afterStop != null) {
					afterStop.run();
				}
			}
		}.execute();
	}

	/**
	 * Issues a new invitation and shows its text.
	 */
	private void issueInvitation() {
		HostSession current = session;
		if (current == null) {
			return;
		}
		try {
			invitationArea.setText(current.issueInvitation());
			invitationArea.selectAll();
			invitationArea.copy();
		} catch (RuntimeException failure) {
			JOptionPane.showMessageDialog(this, "Could not create an invitation: " + failure.getMessage(),
					"Invitation", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Revokes the selected device after confirmation.
	 */
	private void revokeSelected() {
		HostSession current = session;
		int row = deviceTable.getSelectedRow();
		if (current == null || row < 0 || row >= shownDevices.size()) {
			return;
		}
		DeviceInfo device = shownDevices.get(row);
		if (JOptionPane.showConfirmDialog(this, "Revoke access for \"" + device.name() + "\"?", "Revoke device",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}
		try {
			current.revoke(device.deviceId());
		} catch (RuntimeException failure) {
			JOptionPane.showMessageDialog(this, "Could not revoke the device: " + failure.getMessage(), "Revoke device",
					JOptionPane.ERROR_MESSAGE);
		}
		refresh();
	}

	/**
	 * Refreshes the status line, buttons and device list.
	 */
	private void refresh() {
		HostSession current = session;
		boolean running = current != null && current.state() == HostState.RUNNING;
		startButton.setEnabled(current == null);
		stopButton.setEnabled(current != null && current.state() != HostState.STOPPING);
		inviteButton.setEnabled(running);
		revokeButton.setEnabled(running);
		hostField.setEnabled(current == null);
		portSpinner.setEnabled(current == null);
		enrollmentSpinner.setEnabled(current == null);
		if (current == null) {
			stateLabel.setText("Not sharing");
			shownDevices = List.of();
			deviceModel.setRowCount(0);
			return;
		}
		HostStatus status = current.status();
		stateLabel.setText(status.state() == HostState.FAILED ? "FAILED: " + status.failure()
				: status.state() + " - " + status.connectedDevices() + " connected; fingerprint "
						+ status.hostFingerprint());
		if (running) {
			shownDevices = current.devices();
			deviceModel.setRowCount(0);
			for (DeviceInfo device : shownDevices) {
				deviceModel.addRow(new Object[] {device.name(), device.state(), device.expiresAt(),
						device.lastContact() == null ? "never" : device.lastContact(),
						device.connected() ? "yes" : "no"});
			}
		}
	}

	/**
	 * Stops refreshing and releases the window.
	 */
	@Override
	public void dispose() {
		refreshTimer.stop();
		super.dispose();
	}
}
