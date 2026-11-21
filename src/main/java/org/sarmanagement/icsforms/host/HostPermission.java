/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

/**
 * Pilot permissions, granted at whole-incident granularity. There are no roles or operator claims yet; the device
 * record carries a role ceiling so they can be added without changing the schema.
 */
public enum HostPermission {
	/** Connect to the host. */
	CONNECT,
	/** Read records and changes. */
	READ,
	/** Create records. */
	WRITE,
	/** Update existing records. */
	UPDATE,
	/** Strike out records (the application's delete, always recorded in history). */
	DELETE
}
