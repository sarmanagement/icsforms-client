/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.sarmanagement.icsforms.host.NetworkInfo;

/**
 * A footer that tells the Communications Unit which networks this device is on, whether each is wireless or wired,
 * and whether the incident is being shared, with a button that opens the host window when this device can host.
 * The network list refreshes periodically because an interface can come and go while the application runs.
 */
final class NetworkStatusPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	/** Milliseconds between network refreshes. */
	private static final int REFRESH_MILLIS = 5000;

	private final transient SharingControls sharing;
	private final JTextArea networks = new JTextArea(2, 60);
	private final JTextArea sharingLine = new JTextArea(1, 40);
	private final JButton hostButton = new JButton("Host this incident...");
	private final Timer timer = new Timer(REFRESH_MILLIS, event -> refresh());
	private String shownNetworks = "";

	/**
	 * Creates the footer and starts refreshing it.
	 *
	 * @param sharing this window's sharing state and host action
	 */
	NetworkStatusPanel(SharingControls sharing) {
		super(new BorderLayout(8, 0));
		this.sharing = sharing;
		setBorder(BorderFactory.createTitledBorder("Network and sharing"));
		for (JTextArea area : List.of(networks, sharingLine)) {
			area.setEditable(false);
			area.setOpaque(false);
			area.setLineWrap(true);
			area.setWrapStyleWord(true);
			area.setFocusable(false);
		}
		JPanel text = new JPanel(new BorderLayout());
		text.add(networks, BorderLayout.CENTER);
		text.add(sharingLine, BorderLayout.SOUTH);
		add(text, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		hostButton.addActionListener(event -> sharing.showHostDialog());
		buttons.add(hostButton);
		add(buttons, BorderLayout.EAST);
		showSharing();
		refresh();
		timer.start();
	}

	/** Stops the periodic refresh; call when the owning window closes. */
	void dispose() {
		timer.stop();
	}

	/** Re-reads the sharing state and, off the event thread, the network list. */
	void refresh() {
		showSharing();
		new SwingWorker<String, Void>() {
			/** {@inheritDoc} */
			@Override
			protected String doInBackground() {
				return NetworkInfo.summary(NetworkInfo.distinct(NetworkInfo.local()));
			}

			/** {@inheritDoc} */
			@Override
			protected void done() {
				try {
					showNetworks(get());
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.util.concurrent.ExecutionException failure) {
					showNetworks("The networks this device is on could not be determined.");
				}
			}
		}.execute();
	}

	/**
	 * Shows the network list when it changed.
	 *
	 * @param summary one line per network
	 */
	private void showNetworks(String summary) {
		if (!summary.equals(shownNetworks)) {
			shownNetworks = summary;
			networks.setText("This device is on (interface: address on network):\n" + summary);
			revalidate();
		}
	}

	/** Shows the sharing state and the host button. */
	private void showSharing() {
		sharingLine.setText("Sharing: " + sharing.status());
		hostButton.setVisible(sharing.canHost());
		hostButton.setText(sharing.hosting() ? "Show host window..." : "Host this incident...");
	}

	/**
	 * Returns the displayed network text for tests.
	 *
	 * @return network text
	 */
	String networkText() {
		return networks.getText();
	}

	/**
	 * Returns the displayed sharing text for tests.
	 *
	 * @return sharing line
	 */
	String sharingText() {
		return sharingLine.getText();
	}

	/**
	 * Returns the host button for tests.
	 *
	 * @return the button
	 */
	JButton hostButton() {
		return hostButton;
	}
}
