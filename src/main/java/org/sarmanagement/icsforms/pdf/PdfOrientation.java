/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

/**
 * Selects physical PDF media and the orientation of its printed content per export.
 */
public enum PdfOrientation {
	/** Upright content laid out to fit portrait media. */
	PORTRAIT,
	/** Upright content laid out to fit landscape media. */
	LANDSCAPE,
	/** Landscape content rotated counterclockwise onto portrait media. */
	PORTRAIT_FIT
}
