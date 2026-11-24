/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.host.DeviceInfo;
import org.sarmanagement.icsforms.host.HostBackup;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.host.HostState;
import org.sarmanagement.icsforms.host.HostStatus;
import org.sarmanagement.icsforms.host.HostStorage;
import com.fasterxml.jackson.databind.JsonNode;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
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
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lets the host operator start and stop sharing the active incident, issue invitations and revoke devices.
 * Closing the dialog never stops sharing; only the Stop button or an explicit host shutdown does.
 */
public class HostSharingDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final int DEFAULT_PORT = 8443;
	private static final int DEFAULT_ENROLLMENT_PORT = 8444;
	private static final int REFRESH_MILLIS = 2000;
	/** Sensitivity warning shown before a backup or restore is chosen. */
	static final String SENSITIVITY_WARNING = "The host backup contains the host's certificate authority key, the "
			+ "device registry and the incident data. Anyone holding it can mint device credentials. Keep it "
			+ "owner-only on trusted storage and never share it.";

	/**
	 * Presents confirmations, messages and file choices so the dialog's guard logic can be exercised without
	 * modal windows.
	 */
	public interface Prompts {
		/**
		 * Asks a yes/no question.
		 *
		 * @param parent parent component
		 * @param title title
		 * @param message question text
		 * @return true when the operator agrees
		 */
		boolean confirm(Component parent, String title, String message);

		/**
		 * Shows a message.
		 *
		 * @param parent parent component
		 * @param title title
		 * @param message message text
		 * @param error true for a failure message
		 */
		void show(Component parent, String title, String message, boolean error);

		/**
		 * Asks for a file location.
		 *
		 * @param parent parent component
		 * @param title title
		 * @param save true to choose a new file to write, false to choose an existing file
		 * @return chosen path, or null when cancelled
		 */
		Path chooseFile(Component parent, String title, boolean save);
	}

	/** Swing implementation of {@link Prompts}. */
	private static final class SwingPrompts implements Prompts {
		/** {@inheritDoc} */
		@Override
		public boolean confirm(Component parent, String title, String message) {
			return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
		}

		/** {@inheritDoc} */
		@Override
		public void show(Component parent, String title, String message, boolean error) {
			JOptionPane.showMessageDialog(parent, message, title,
					error ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseFile(Component parent, String title, boolean save) {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle(title);
			int result = save ? chooser.showSaveDialog(parent) : chooser.showOpenDialog(parent);
			return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
		}
	}

	private final transient IncidentStore store;
	private final transient IncidentId incident;
	private final transient Runnable flushEditors;
	private final transient Supplier<Instant> periodCloseSource;
	private final transient Runnable onRestored;
	private transient Prompts prompts = new SwingPrompts();
	private transient HostSession session;
	private transient RemoteIncidentStore loopbackStore;
	private volatile boolean starting;
	private boolean stopping;
	private boolean refreshingDevices;
	private boolean reviewingRequests;
	private transient java.util.List<Path> cacheRoots =
			java.util.List.of(Path.of(System.getProperty("user.home"), ".icsforms", "remote-cache"));
	private transient java.util.function.Consumer<IncidentStore> onLoopback = loopback -> { };
	private transient Runnable onLocal = () -> { };
	private transient java.util.function.Consumer<Boolean> transitionGuard = active -> { };
	private final JLabel stateLabel = new JLabel("Not sharing");
	private final JTextField hostField = new JTextField(defaultHost(), 18);
	private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_PORT, 0, 65535, 1));
	private final JSpinner enrollmentSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_ENROLLMENT_PORT, 0, 65535, 1));
	private final JButton startButton = new JButton("Start sharing");
	private final JButton stopButton = new JButton("Stop sharing");
	private final JButton inviteButton = new JButton("Create long invitation");
	private final JButton pairButton = new JButton("Start adding a device");
	private final JLabel pairingCodeLabel = new JLabel(" ");
	private final JTextField entryField = new JTextField(8);
	private final JButton entryButton = new JButton("Confirm number");
	private final Timer pairingTimer = new Timer(1000, event -> pollPairing());
	private boolean polling;
	private final JLabel pairingDetailLabel = new JLabel(" ");
	private final JLabel qrLabel = new JLabel();
	private String pairingPayloadText = "";
	private final JButton revokeButton = new JButton("Revoke selected device");
	private final JButton reviewRequestsButton = new JButton("Review re-authorization requests");
	private final JButton backupButton = new JButton("Back up host data...");
	private final JButton restoreButton = new JButton("Restore host data...");
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
	 * @param periodCloseSource supplies the current operational period close, or null when none is defined
	 * @param onRestored callback run after a successful restore so the editor can reload the incident, or null
	 */
	public HostSharingDialog(Frame owner, IncidentStore store, IncidentId incident, Runnable flushEditors,
			Supplier<Instant> periodCloseSource, Runnable onRestored) {
		super(owner, "Host this incident", false);
		this.store = store;
		this.incident = incident;
		this.flushEditors = flushEditors;
		this.periodCloseSource = periodCloseSource == null ? () -> null : periodCloseSource;
		this.onRestored = onRestored == null ? () -> { } : onRestored;
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		buildLayout();
		refresh();
		refreshTimer.start();
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Installs the callbacks that move the host window between the local file store and the loopback remote
	 * store. The host window has no direct-write bypass while sharing: it edits through the same service as
	 * connected devices.
	 *
	 * @param loopbackStarted receives the loopback store once sharing runs; a failure stops sharing again
	 * @param localRestored invoked on the event thread after sharing stopped, to return to the local store
	 */
	public void setStoreSwitch(java.util.function.Consumer<IncidentStore> loopbackStarted, Runnable localRestored) {
		this.onLoopback = loopbackStarted == null ? loopback -> { } : loopbackStarted;
		this.onLocal = localRestored == null ? () -> { } : localRestored;
	}

	/**
	 * Sets the private durable cache root before starting a host.
	 *
	 * @param root disconnected cache root
	 */
	public void setCacheRoot(Path root) {
		if (starting || session != null) {
			throw new IllegalStateException("Cannot change the cache root while sharing");
		}
		setCacheRoots(java.util.List.of(java.util.Objects.requireNonNull(root, "root")));
	}

	/**
	 * Sets the candidate private cache roots, in order of preference, before starting a host.
	 *
	 * @param roots disconnected cache roots
	 */
	public void setCacheRoots(java.util.List<Path> roots) {
		if (starting || session != null) {
			throw new IllegalStateException("Cannot change the cache root while sharing");
		}
		if (roots.isEmpty()) {
			throw new IllegalArgumentException("At least one cache root is required");
		}
		cacheRoots = java.util.List.copyOf(roots);
	}

	/**
	 * Installs a callback that prevents editing and other store transitions while a host transition is in progress.
	 * The callback runs on the event thread.
	 *
	 * @param guard receives true when a transition begins and false when it finishes
	 */
	public void setTransitionGuard(java.util.function.Consumer<Boolean> guard) {
		transitionGuard = guard == null ? active -> { } : guard;
	}

	/**
	 * Derives the credential cutoff from an incident's current operational period end. The stored value is a
	 * UTC date-time. No demobilisation indicator exists in the model yet, so only the period end is used.
	 *
	 * @param data incident data
	 * @return period close instant, or null when no period end is defined
	 */
	public static Instant periodCloseOf(AppData data) {
		if (data == null || data.getIncidentContext() == null) {
			return null;
		}
		LocalDateTime end = data.getIncidentContext().getOperationalPeriodEnd();
		return end == null ? null : end.toInstant(ZoneOffset.UTC);
	}

	/**
	 * Explains why an incident cannot be hosted because its operational period has already closed. Hosts refuse to
	 * issue credentials after the close, so sharing would start and then fail at the first invitation.
	 *
	 * @param data incident data
	 * @param now current time
	 * @return the message to show the operator, or null when the period is open or has no defined end
	 */
	public static String periodClosedMessage(AppData data, Instant now) {
		Instant close = periodCloseOf(data);
		if (close == null || now.isBefore(close)) {
			return null;
		}
		return "The operational period ended "
				+ java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").format(close.atOffset(ZoneOffset.UTC))
				+ ", so devices cannot be admitted and the incident cannot be hosted.\n"
				+ "Set a later operational period end in the incident details, then try again.";
	}

	/**
	 * Sets the address and ports used on the next start, for tests and scripted setup.
	 *
	 * @param host address clients use
	 * @param port mutual-TLS port
	 * @param enrollmentPort pairing and status port
	 */
	void configure(String host, int port, int enrollmentPort) {
		hostField.setText(host);
		portSpinner.setValue(port);
		enrollmentSpinner.setValue(enrollmentPort);
	}

	/**
	 * Replaces the prompt implementation, for tests.
	 *
	 * @param replacement prompt implementation
	 */
	void setPrompts(Prompts replacement) {
		this.prompts = replacement;
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
		settings.add(new JLabel("Port (0 = any free port):"));
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
		invitePanel.setBorder(BorderFactory.createTitledBorder("Long invitation (advanced; a secret)"));
		invitePanel.add(new JScrollPane(invitationArea), BorderLayout.CENTER);
		invitePanel.add(inviteButton, BorderLayout.SOUTH);

		JPanel entryRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		entryRow.add(entryField);
		entryRow.add(entryButton);
		entryField.setEnabled(false);
		entryButton.setEnabled(false);
		JPanel codeText = new JPanel(new GridLayout(0, 1));
		codeText.add(pairButton);
		codeText.add(pairingDetailLabel);
		codeText.add(pairingCodeLabel);
		codeText.add(entryRow);
		JPanel pairPanel = new JPanel(new BorderLayout(8, 0));
		pairPanel.setBorder(BorderFactory.createTitledBorder(
				"Add a device in person (the device shows a number; type it here)"));
		pairPanel.add(codeText, BorderLayout.CENTER);
		pairPanel.add(qrLabel, BorderLayout.EAST);

		deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JPanel devices = new JPanel(new BorderLayout());
		devices.setBorder(BorderFactory.createTitledBorder("Paired devices"));
		devices.add(new JScrollPane(deviceTable), BorderLayout.CENTER);
		JPanel deviceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		deviceButtons.add(revokeButton);
		deviceButtons.add(reviewRequestsButton);
		deviceButtons.add(backupButton);
		deviceButtons.add(restoreButton);
		devices.add(deviceButtons, BorderLayout.SOUTH);

		JPanel center = new JPanel(new GridLayout(0, 1));
		center.add(pairPanel);
		center.add(invitePanel);
		center.add(devices);
		add(top, BorderLayout.NORTH);
		add(center, BorderLayout.CENTER);

		startButton.addActionListener(event -> startSharing());
		stopButton.addActionListener(event -> stopSharing());
		inviteButton.addActionListener(event -> issueInvitation());
		pairButton.addActionListener(event -> openPairing());
		entryButton.addActionListener(event -> enterPairingCode());
		revokeButton.addActionListener(event -> revokeSelected());
		reviewRequestsButton.addActionListener(event -> reviewReauthorizationRequests());
		backupButton.addActionListener(event -> backupAction());
		restoreButton.addActionListener(event -> restoreAction());
	}

	/**
	 * Loads pending re-authorization requests off the event thread.
	 */
	private void reviewReauthorizationRequests() {
		HostSession current = session;
		if (current == null || reviewingRequests) {
			return;
		}
		reviewingRequests = true;
		reviewRequestsButton.setEnabled(false);
		new SwingWorker<List<JsonNode>, Void>() {
			/** {@inheritDoc} */
			@Override
			protected List<JsonNode> doInBackground() {
				return current.reauthorizationRequests();
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				reviewingRequests = false;
				reviewRequestsButton.setEnabled(isSharing());
				try {
					List<JsonNode> requests = get();
					if (requests.isEmpty()) {
						prompts.show(HostSharingDialog.this, "Re-authorization",
								"There are no pending requests.", false);
					} else {
						presentReauthorizationRequest(current, requests);
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.util.concurrent.ExecutionException failure) {
					prompts.show(HostSharingDialog.this, "Re-authorization",
							"Could not load requests: " + failure.getCause().getMessage(), true);
				}
			}
		}.execute();
	}

	/**
	 * Shows one request's queued work before allowing an administrator decision.
	 *
	 * @param current active host session
	 * @param requests pending requests
	 */
	private void presentReauthorizationRequest(HostSession current, List<JsonNode> requests) {
		String[] labels = requests.stream().map(request -> request.path("kind").asText() + " · "
				+ request.path("deviceName").asText() + " · original device "
				+ request.path("originalDeviceId").asText()).toArray(String[]::new);
		int selected = JOptionPane.showOptionDialog(this, "Select a request to review:", "Re-authorization requests",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
		if (selected < 0 || selected >= requests.size()) {
			return;
		}
		JsonNode request = requests.get(selected);
		JTextArea outbox = new JTextArea(request.path("outbox").toPrettyString(), 20, 72);
		outbox.setEditable(false);
		outbox.setCaretPosition(0);
		JPanel review = new JPanel(new BorderLayout(6, 6));
		review.add(new JLabel("Request " + request.path("requestId").asText() + " · " + labels[selected]),
				BorderLayout.NORTH);
		review.add(new JScrollPane(outbox), BorderLayout.CENTER);
		String[] decisions = {"Approve", "Deny", "Leave pending"};
		int decision = JOptionPane.showOptionDialog(this, review, "Review pending outbox",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, decisions, decisions[2]);
		if (decision == 0 || decision == 1) {
			resolveReauthorization(current, request.path("requestId").asText(), decision == 0);
		}
	}

	/**
	 * Records the administrator's decision off the event thread.
	 *
	 * @param current active host session
	 * @param requestId request to resolve
	 * @param approve true to approve, false to deny
	 */
	private void resolveReauthorization(HostSession current, String requestId, boolean approve) {
		reviewRequestsButton.setEnabled(false);
		new SwingWorker<Void, Void>() {
			/** {@inheritDoc} */
			@Override
			protected Void doInBackground() {
				if (approve) {
					current.approveReauthorization(requestId);
				} else {
					current.denyReauthorization(requestId);
				}
				return null;
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				reviewRequestsButton.setEnabled(isSharing());
				try {
					get();
					prompts.show(HostSharingDialog.this, "Re-authorization",
							approve ? "Request approved." : "Request denied. The device's local outbox is unchanged.",
							false);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.util.concurrent.ExecutionException failure) {
					prompts.show(HostSharingDialog.this, "Re-authorization",
							"Could not resolve request: " + failure.getCause().getMessage(), true);
				}
			}
		}.execute();
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
	 * Starts sharing after flushing editors; a failure leaves the incident usable and is shown. Binding the
	 * listeners and the first loopback read run on a background worker with a visible "Starting" state, so the
	 * event thread is never blocked; the switch to the loopback store happens back on the event thread once the
	 * host is running.
	 */
	void startSharing() {
		if (isSharing() || starting) {
			return;
		}
		HostConfig config;
		HostSession candidate;
		try {
			config = HostConfig.defaults(hostField.getText().strip(), (Integer) portSpinner.getValue(),
					(Integer) enrollmentSpinner.getValue());
			transitionGuard.accept(true);
			flushEditors.run();
			candidate = new HostSession(store, incident, config, null);
		} catch (RuntimeException failure) {
			transitionGuard.accept(false);
			showStartFailure(failure);
			return;
		}
		starting = true;
		startButton.setEnabled(false);
		stateLabel.setText("Starting - binding ports and reading the incident…");
		new SwingWorker<RemoteIncidentStore, Void>() {
			/**
			 * Starts the host session off the event thread.
			 *
			 * @return nothing
			 */
			@Override
			protected RemoteIncidentStore doInBackground() {
				candidate.start();
				try {
					return candidate.loopbackStore(cacheRoots);
				} catch (RuntimeException failure) {
					candidate.stop();
					throw failure;
				}
			}

			/**
			 * Completes the start on the event thread or reports the failure.
			 */
			@Override
			protected void done() {
				try {
					RemoteIncidentStore opened = get();
					candidate.setPeriodCloseCutoff(periodCloseSource.get());
					try {
						onLoopback.accept(opened);
					} catch (RuntimeException switchFailure) {
						new SwingWorker<Void, Void>() {
							/** {@inheritDoc} */
							@Override
							protected Void doInBackground() {
								try {
									opened.close();
								} finally {
									candidate.stop();
								}
								return null;
							}

							/** {@inheritDoc} */
							@Override
							protected void done() {
								starting = false;
								transitionGuard.accept(false);
								showStartFailure(switchFailure);
							}
						}.execute();
						return;
					}
					loopbackStore = opened;
					session = candidate;
					invitationArea.setText("");
					clearPairing();
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					session = null;
				} catch (java.util.concurrent.ExecutionException failure) {
					session = null;
					showStartFailure(failure.getCause() instanceof RuntimeException runtime ? runtime
							: new IllegalStateException(failure.getCause()));
				} catch (RuntimeException failure) {
					session = null;
					showStartFailure(failure);
				}
				starting = false;
				transitionGuard.accept(false);
				refresh();
			}
		}.execute();
	}

	/**
	 * Describes whether a device is connected; an active device that was once in contact but no longer is
	 * is marked as having lost contact.
	 *
	 * @param device device row
	 * @return "yes", "no - lost contact" or "no"
	 */
	static String connectionText(DeviceInfo device) {
		if (device.connected()) {
			return "yes";
		}
		return device.lastContact() != null && device.state() == org.sarmanagement.icsforms.host.CredentialState.ACTIVE
				? "no - lost contact" : "no";
	}

	/**
	 * Returns whether a start is still in progress.
	 *
	 * @return true while the host session is being started
	 */
	boolean isStarting() {
		return starting;
	}

	/**
	 * Tells the operator the start failed and that the incident is unchanged.
	 *
	 * @param failure why the start failed
	 */
	private void showStartFailure(RuntimeException failure) {
		session = null;
		prompts.show(this, "Host this incident", "Sharing could not be started; the incident is unchanged.\n"
				+ failure.getMessage(), true);
		refresh();
	}

	/**
	 * Stops sharing after warning when devices are connected.
	 */
	void stopSharing() {
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
		return prompts.confirm(this, "Stop sharing", connected + " device(s) are connected. Stopping sharing tells "
				+ "them the host is stopping and ends their access. Stop sharing?");
	}

	/**
	 * Stops the session on a worker thread so the window stays responsive during the grace period.
	 *
	 * @param afterStop callback run on the event thread once stopped, or null
	 */
	public void stopAsync(Runnable afterStop) {
		HostSession current = session;
		if (starting || stopping) {
			return;
		}
		if (current == null) {
			if (afterStop != null) {
				afterStop.run();
			}
			return;
		}
		stopping = true;
		transitionGuard.accept(true);
		try {
			flushEditors.run();
		} catch (RuntimeException failure) {
			stopping = false;
			transitionGuard.accept(false);
			prompts.show(this, "Stop sharing", "Pending edits could not be saved: " + failure.getMessage(), true);
			refresh();
			return;
		}
		stopButton.setEnabled(false);
		RemoteIncidentStore remote = loopbackStore;
		new SwingWorker<Boolean, Void>() {
			/**
			 * Stops the session off the event thread.
			 *
			 * @return nothing
			 */
			@Override
			protected Boolean doInBackground() {
				if (remote != null && !remote.pendingOperations().isEmpty()) {
					try {
						remote.replayPending();
					} catch (RuntimeException failure) {
						// The journal retains failed or ambiguous work for review.
					}
					if (!remote.pendingOperations().isEmpty()) {
						return false;
					}
				}
				current.prepareStop();
				return true;
			}

			/**
			 * Refreshes the display and runs the follow-up action.
			 */
			@Override
			protected void done() {
				try {
					if (!get()) {
						prompts.show(HostSharingDialog.this, "Stop sharing",
								"Sharing cannot stop while the host outbox contains unsynchronized work. "
										+ "Review and resolve the outbox first.", true);
						finishStopTransition();
						return;
					}
					onLocal.run();
				} catch (Exception failure) {
					current.cancelPreparedStop();
					if (failure instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					prompts.show(HostSharingDialog.this, "Stop sharing",
							"Sharing was not stopped; the outbox could not be checked or the local file could not "
									+ "be opened: " + failure.getMessage(), true);
					finishStopTransition();
					return;
				}
				new SwingWorker<Void, Void>() {
					/** {@inheritDoc} */
					@Override
					protected Void doInBackground() {
						try {
							current.stop();
						} finally {
							if (remote != null) {
								remote.close();
							}
						}
						return null;
					}

					/** {@inheritDoc} */
					@Override
					protected void done() {
						try {
							get();
							session = null;
							loopbackStore = null;
							finishStopTransition();
							if (afterStop != null) {
								afterStop.run();
							}
						} catch (Exception failure) {
							if (failure instanceof InterruptedException) {
								Thread.currentThread().interrupt();
							}
							prompts.show(HostSharingDialog.this, "Stop sharing",
									"Could not finish stopping sharing: " + failure.getMessage(), true);
							finishStopTransition();
						}
					}
				}.execute();
			}
		}.execute();
	}

	/** Ends a stop attempt and restores the controls on the event thread. */
	private void finishStopTransition() {
		stopping = false;
		transitionGuard.accept(false);
		refresh();
	}

	/**
	 * Issues a new invitation and shows its text.
	 */
	void issueInvitation() {
		HostSession current = session;
		if (current == null) {
			return;
		}
		inviteButton.setEnabled(false);
		new SwingWorker<String, Void>() {
			/** {@inheritDoc} */
			@Override
			protected String doInBackground() {
				return current.issueInvitation();
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				try {
					String invitation = get();
					if (session == current && !stopping) {
						invitationArea.setText(invitation);
						copyInvitation();
					}
				} catch (Exception failure) {
					if (failure instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					prompts.show(HostSharingDialog.this, "Invitation",
							"Could not create an invitation: " + failure.getMessage(), true);
				}
				refresh();
			}
		}.execute();
	}

	/**
	 * Opens the in-person pairing window and shows the address, expiry and a fingerprint QR code.
	 */
	void openPairing() {
		HostSession current = session;
		if (current == null) {
			return;
		}
		pairButton.setEnabled(false);
		new SwingWorker<org.sarmanagement.icsforms.host.HostClient.PairingOffer, Void>() {
			/** {@inheritDoc} */
			@Override
			protected org.sarmanagement.icsforms.host.HostClient.PairingOffer doInBackground() {
				return current.openPairing();
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				try {
					org.sarmanagement.icsforms.host.HostClient.PairingOffer offer = get();
					if (session == current && !stopping) {
						showPairing(offer);
					}
				} catch (Exception failure) {
					if (failure instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					prompts.show(HostSharingDialog.this, "Add a device",
							"Could not open pairing: " + failure.getMessage(), true);
				}
				refresh();
			}
		}.execute();
	}

	/**
	 * Displays a pairing offer: the address and a QR code carrying only the host fingerprint, then starts
	 * watching for a device that wants to join.
	 *
	 * @param offer the open pairing window
	 */
	private void showPairing(org.sarmanagement.icsforms.host.HostClient.PairingOffer offer) {
		pairingCodeLabel.setText("Waiting for a device. On it choose Connect and enter this host.");
		pairingDetailLabel.setText("Host " + offer.host() + ":" + offer.enrollmentPort() + " until "
				+ java.time.LocalTime.ofInstant(offer.expiresAt(), java.time.ZoneId.systemDefault())
						.truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
		pairingPayloadText = offer.payload().toText();
		qrLabel.setIcon(new javax.swing.ImageIcon(QrImages.render(pairingPayloadText, 200)));
		pairingTimer.start();
	}

	/** Polls the host for a device waiting for its number to be entered. */
	private void pollPairing() {
		HostSession current = session;
		if (current == null || polling) {
			return;
		}
		polling = true;
		new SwingWorker<org.sarmanagement.icsforms.host.HostClient.PairingState, Void>() {
			/** {@inheritDoc} */
			@Override
			protected org.sarmanagement.icsforms.host.HostClient.PairingState doInBackground() {
				return current.pairingState();
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				polling = false;
				try {
					org.sarmanagement.icsforms.host.HostClient.PairingState state = get();
					if (session != current) {
						return;
					}
					if (!state.open()) {
						clearPairing();
						return;
					}
					boolean waiting = state.awaitingDevice() != null;
					entryField.setEnabled(waiting);
					entryButton.setEnabled(waiting);
					if (waiting) {
						pairingCodeLabel.setText("'" + state.awaitingDevice()
								+ "' wants to join. Type the number shown on that device:");
					}
				} catch (Exception failure) {
					if (failure instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					clearPairing();
				}
			}
		}.execute();
	}

	/** Sends the number the operator typed and reports the result. */
	void enterPairingCode() {
		HostSession current = session;
		String typed = entryField.getText();
		if (current == null || typed.isBlank()) {
			return;
		}
		entryButton.setEnabled(false);
		new SwingWorker<String, Void>() {
			/** {@inheritDoc} */
			@Override
			protected String doInBackground() {
				return current.enterPairingCode(typed);
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				String message;
				try {
					String result = get();
					message = switch (result) {
						case "confirmed" -> "The number matches. The device is being added.";
						case "burned" -> "Wrong number. Too many attempts; the pairing window is closed.";
						case "mismatch" -> "That number does not match. Check the device and try again.";
						default -> "No device is waiting.";
					};
					entryField.setText("");
				} catch (Exception failure) {
					if (failure instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					message = "Could not check the number: " + failure.getMessage();
				}
				pairingDetailLabel.setText(message);
				refresh();
			}
		}.execute();
	}

	/** Forgets any displayed pairing state and QR image. */
	private void clearPairing() {
		pairingTimer.stop();
		pairingCodeLabel.setText(" ");
		pairingDetailLabel.setText(" ");
		entryField.setText("");
		entryField.setEnabled(false);
		entryButton.setEnabled(false);
		qrLabel.setIcon(null);
		pairingPayloadText = "";
	}

	/**
	 * Returns the QR and clipboard text of the displayed pairing offer.
	 *
	 * @return payload text, empty when none is shown
	 */
	String pairingPayload() {
		return pairingPayloadText;
	}

	/**
	 * Copies the displayed invitation text to the clipboard.
	 */
	void copyInvitation() {
		invitationArea.selectAll();
		invitationArea.copy();
	}

	/**
	 * Returns the invitation text currently shown.
	 *
	 * @return invitation text, empty when none
	 */
	String invitationText() {
		return invitationArea.getText();
	}

	/**
	 * Returns the status line text.
	 *
	 * @return status text
	 */
	String statusText() {
		return stateLabel.getText();
	}

	/**
	 * Returns the device table.
	 *
	 * @return table of paired devices
	 */
	JTable deviceTable() {
		return deviceTable;
	}

	/**
	 * Returns the sharing session while one exists.
	 *
	 * @return session, or null
	 */
	HostSession session() {
		return session;
	}

	/**
	 * Resolves the host-owned storage for backup and restore, preferring the live session's instance.
	 *
	 * @return storage
	 */
	private HostStorage storage() {
		HostSession current = session;
		if (current != null && current.storage() != null) {
			return current.storage();
		}
		return new HostStorage(Path.of(store.describe().location()), Clock.systemUTC());
	}

	/**
	 * Warns about sensitivity, asks for a destination and writes the host backup.
	 */
	private void backupAction() {
		if (!prompts.confirm(this, "Back up host data", SENSITIVITY_WARNING + "\n\nContinue?")) {
			return;
		}
		Path target = prompts.chooseFile(this, "Save host backup", true);
		if (target == null) {
			return;
		}
		String problem = backupTo(target);
		prompts.show(this, "Back up host data", problem == null ? "Backup written (owner-only) to " + target
				: "Backup failed: " + problem, problem != null);
	}

	/**
	 * Writes the combined incident, registry and key backup; the file is created owner-only.
	 *
	 * @param target new backup file
	 * @return null on success, otherwise a description of the failure
	 */
	String backupTo(Path target) {
		try {
			flushEditors.run();
			HostBackup.create(store, incident, storage(), target);
			return null;
		} catch (RuntimeException failure) {
			return String.valueOf(failure.getMessage());
		}
	}

	/**
	 * Warns, asks for a backup and restores it unless sharing is active.
	 */
	private void restoreAction() {
		if (isSharing()) {
			prompts.show(this, "Restore host data", RESTORE_WHILE_SHARING, true);
			return;
		}
		if (!prompts.confirm(this, "Restore host data", SENSITIVITY_WARNING
				+ "\n\nRestoring replaces this incident, its history, the device registry and the host keys. "
				+ "Continue?")) {
			return;
		}
		Path source = prompts.chooseFile(this, "Open host backup", false);
		if (source == null) {
			return;
		}
		String problem = restoreFrom(source);
		prompts.show(this, "Restore host data", problem == null ? "Backup restored. Devices and revocations in the "
				+ "backup are in force." : "Restore failed: " + problem, problem != null);
	}

	/** Message shown when a restore is attempted while sharing. */
	static final String RESTORE_WHILE_SHARING = "Stop sharing before restoring a host backup.";

	/**
	 * Restores a host backup; refused while sharing. On success the editor reloads the restored incident.
	 *
	 * @param source backup file
	 * @return null on success, otherwise a description of the failure
	 */
	String restoreFrom(Path source) {
		if (isSharing()) {
			return RESTORE_WHILE_SHARING;
		}
		try {
			HostBackup.restore(store, incident, storage(), source, true);
		} catch (RuntimeException failure) {
			return String.valueOf(failure.getMessage());
		}
		try {
			onRestored.run();
		} catch (RuntimeException failure) {
			return "The backup was restored but the editor could not reload it: " + failure.getMessage();
		}
		return null;
	}

	/**
	 * Revokes the selected device after confirmation.
	 */
	void revokeSelected() {
		HostSession current = session;
		int row = deviceTable.getSelectedRow();
		if (current == null || row < 0 || row >= shownDevices.size()) {
			return;
		}
		DeviceInfo device = shownDevices.get(row);
		if (!prompts.confirm(this, "Revoke device", "Revoke access for \"" + device.name() + "\"?")) {
			return;
		}
		revokeButton.setEnabled(false);
		new SwingWorker<Void, Void>() {
			/** {@inheritDoc} */
			@Override
			protected Void doInBackground() {
				current.revoke(device.deviceId());
				return null;
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				try {
					get();
				} catch (Exception failure) {
					if (failure instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					prompts.show(HostSharingDialog.this, "Revoke device",
							"Could not revoke the device: " + failure.getMessage(), true);
				}
				refresh();
			}
		}.execute();
	}

	/**
	 * Refreshes the status line, buttons and device list.
	 */
	void refresh() {
		HostSession current = session;
		boolean running = current != null && current.state() == HostState.RUNNING;
		if (running) {
			current.setPeriodCloseCutoff(periodCloseSource.get());
		}
		startButton.setEnabled(current == null && !starting);
		stopButton.setEnabled(current != null && !stopping && current.state() != HostState.STOPPING);
		inviteButton.setEnabled(running && !stopping);
		pairButton.setEnabled(running && !stopping);
		revokeButton.setEnabled(running && !stopping);
		reviewRequestsButton.setEnabled(running && !stopping && !reviewingRequests);
		hostField.setEnabled(current == null);
		portSpinner.setEnabled(current == null);
		enrollmentSpinner.setEnabled(current == null);
		if (current == null && starting) {
			return;
		}
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
		if (running && !stopping && !refreshingDevices) {
			refreshingDevices = true;
			new SwingWorker<List<DeviceInfo>, Void>() {
				/** {@inheritDoc} */
				@Override
				protected List<DeviceInfo> doInBackground() {
					return current.devices();
				}

				/** {@inheritDoc} */
				@Override
				protected void done() {
					refreshingDevices = false;
					try {
						List<DeviceInfo> devices = get();
						if (session != current || stopping) {
							return;
						}
						shownDevices = devices;
						deviceModel.setRowCount(0);
						for (DeviceInfo device : devices) {
							deviceModel.addRow(new Object[] {device.name(), device.state(), device.expiresAt(),
									device.lastContact() == null ? "never" : device.lastContact(),
									connectionText(device)});
						}
					} catch (InterruptedException failure) {
						Thread.currentThread().interrupt();
					} catch (java.util.concurrent.ExecutionException failure) {
						// A later refresh will retry after transient connection failures.
					}
				}
			}.execute();
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
