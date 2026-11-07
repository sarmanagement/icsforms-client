/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

/** Indicates that another writer has changed an incident since it was loaded. */
public class StoreConflictException extends IllegalStateException {
	private final RevisionToken expected;
	private final RevisionToken current;
	/**
	 * Creates a revision conflict.
	 *
	 * @param message
	 *            explanation of the conflict.
	 */
	@Deprecated
	public StoreConflictException(String message) {
		this(message, null, null);
	}

	/**
	 * Creates a conflict retaining the compared revisions.
	 *
	 * @param message explanation
	 * @param expected caller's expected revision
	 * @param current current stored revision
	 */
	public StoreConflictException(String message, RevisionToken expected, RevisionToken current) {
		super(message);
		this.expected = expected;
		this.current = current;
	}

	/** @return caller's expected token, or null for a legacy/lock conflict */
	public RevisionToken expected() {
		return expected;
	}

	/** @return current stored token, or null for a legacy/lock conflict */
	public RevisionToken current() {
		return current;
	}

	/** @return caller's expected token */
	public RevisionToken getExpected() {
		return expected;
	}

	/** @return current stored token */
	public RevisionToken getCurrent() {
		return current;
	}
}
