/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Short-code device pairing. A password-authenticated key exchange ({@link
 * org.sarmanagement.icsforms.host.pairing.PakeSuite}) turns a short, human-transferable code into a shared key,
 * and a keyed channel ({@link org.sarmanagement.icsforms.host.pairing.ChannelSuite}) derives confirmation and
 * payload keys from it, bound to the host identity each side observed. Both are identified on the wire by a suite
 * identifier and are chosen through {@link org.sarmanagement.icsforms.host.pairing.PairingSuites}, so either can be
 * replaced without touching the pairing protocol. No cryptographic primitive is implemented in this package; the
 * suites delegate to Bouncy Castle and the JDK.
 */
package org.sarmanagement.icsforms.host.pairing;
