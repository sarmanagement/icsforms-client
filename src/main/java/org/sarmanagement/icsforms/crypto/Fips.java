/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.crypto;

import java.security.Provider;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;

/**
 * Supplies the Bouncy Castle FIPS provider that host-mode cryptography uses. The provider is passed explicitly to
 * each JCA call rather than registered globally, so the rest of the application keeps the JDK providers. The module
 * runs in its default (general) mode; it is not switched to approved-only mode.
 */
public final class Fips {
	private static final Provider PROVIDER = new BouncyCastleFipsProvider();

	private Fips() {
	}

	/**
	 * Returns the shared provider instance.
	 *
	 * @return the Bouncy Castle FIPS provider
	 */
	public static Provider provider() {
		return PROVIDER;
	}
}
