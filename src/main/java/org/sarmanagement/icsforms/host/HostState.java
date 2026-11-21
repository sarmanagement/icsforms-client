/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * Lifecycle state of a host session.
 */
public enum HostState {
	/** Startup is in progress and no client is admitted yet. */
	STARTING,
	/** The incident is shared and clients are served. */
	RUNNING,
	/** Sharing is ending; outstanding requests are draining and clients are being notified. */
	STOPPING,
	/** Sharing has stopped and no listener is open. */
	STOPPED,
	/** The service failed while running; requests are refused until the operator stops and restarts hosting. */
	FAILED
}
