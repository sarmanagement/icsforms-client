/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

/**
 * What a role panel needs to know about, and do with, this window's sharing state, so a unit that depends on the
 * network can show it and offer to start hosting without depending on the main window.
 */
interface SharingControls {
	/** Controls for a window that cannot host, such as a test or a standalone panel. */
	SharingControls NONE = new SharingControls() {
		/** {@inheritDoc} */
		@Override
		public String status() {
			return "";
		}

		/** {@inheritDoc} */
		@Override
		public boolean canHost() {
			return false;
		}

		/** {@inheritDoc} */
		@Override
		public boolean hosting() {
			return false;
		}

		/** {@inheritDoc} */
		@Override
		public void showHostDialog() {
			// Nothing to show.
		}
	};

	/**
	 * Describes the sharing state in one line.
	 *
	 * @return text such as "Not sharing" or "Hosting this incident"
	 */
	String status();

	/**
	 * Tells whether this window can host its incident, or already does.
	 *
	 * @return false for a connected client, or when the incident is not in the local file store
	 */
	boolean canHost();

	/**
	 * Tells whether this window is hosting its incident now.
	 *
	 * @return true while sharing
	 */
	boolean hosting();

	/** Opens the "Host this incident" window. */
	void showHostDialog();
}
