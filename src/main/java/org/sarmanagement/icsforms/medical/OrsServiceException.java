/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import java.io.IOException;

/**
 * Sanitized ORS error that never includes request headers or response bodies.
 */
public class OrsServiceException extends IOException {
	private final int statusCode;

	/**
	 * Creates a redacted provider failure.
	 *
	 * @param statusCode HTTP status, or zero for transport failures.
	 */
	public OrsServiceException(int statusCode) {
		super(messageFor(statusCode));
		this.statusCode = statusCode;
	}

	/**
	 * Returns the HTTP status or zero for an unavailable request.
	 *
	 * @return status code.
	 */
	public int getStatusCode() {
		return statusCode;
	}

	/**
	 * Creates a non-sensitive error description.
	 *
	 * @param statusCode HTTP status.
	 * @return safe message.
	 */
	private static String messageFor(int statusCode) {
		return switch (statusCode) {
			case 0 -> "OpenRouteService request failed or timed out";
			case 401, 403 -> "OpenRouteService authorization failed; check local API-key configuration";
			case 429 -> "OpenRouteService quota or rate limit reached";
			case 408 -> "OpenRouteService request timed out";
			case 413 -> "OpenRouteService request exceeds the service limit";
			default -> "OpenRouteService request failed (HTTP " + statusCode + ")";
		};
	}
}
