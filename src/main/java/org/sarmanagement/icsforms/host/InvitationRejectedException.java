/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * An invitation was refused. The reason is for host-side diagnostics only; the remote caller always receives one
 * uniform response so rejected invitations cannot be told apart.
 */
public class InvitationRejectedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates the exception.
	 *
	 * @param reason internal diagnostic reason that never contains the invitation secret
	 */
	public InvitationRejectedException(String reason) {
		super(reason);
	}
}
