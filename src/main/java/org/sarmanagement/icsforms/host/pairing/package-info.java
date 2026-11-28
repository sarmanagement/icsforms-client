/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-person device pairing. A key agreement with a short confirmation number ({@link
 * org.sarmanagement.icsforms.host.pairing.SasSuite}) lets the host operator confirm, by typing the number the
 * device displays, that both ends saw the same exchange. A keyed channel ({@link
 * org.sarmanagement.icsforms.host.pairing.ChannelSuite}) derives confirmation and payload keys from the agreed
 * secret, bound to the host identity each side observed. Both are identified on the wire by a suite identifier and
 * chosen through {@link org.sarmanagement.icsforms.host.pairing.PairingSuites}, so either can be replaced without
 * touching the pairing protocol. No cryptographic primitive is implemented in this package; the suites use the
 * Bouncy Castle FIPS provider.
 */
package org.sarmanagement.icsforms.host.pairing;
