/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the in-person pairing building blocks: numbers, QR payload, SAS suite and keyed channel. */
class PairingCoreTest {
	private static final byte[] HOST = "host-fingerprint".getBytes(StandardCharsets.UTF_8);
	private static final byte[] MITM = "mitm-fingerprint".getBytes(StandardCharsets.UTF_8);

	/**
	 * Runs a full exchange in which the device believes it is talking to the given host identity.
	 *
	 * @param deviceSeesHost identity the device observed
	 * @return the device outcome followed by the host outcome
	 * @throws GeneralSecurityException when the exchange fails
	 */
	private static SasSuite.Outcome[] exchange(byte[] deviceSeesHost) throws GeneralSecurityException {
		SasSuite suite = new EcdhSasSuite();
		SasSuite.Initiator device = suite.initiator();
		SasSuite.Responder host = suite.responder();
		byte[] share = host.receiveCommitment(device.commitment());
		byte[] opening = device.accept(share);
		SasSuite.Outcome hostOutcome = host.open(opening, HOST);
		return new SasSuite.Outcome[] {device.outcome(deviceSeesHost), hostOutcome};
	}

	@Test
	void numbersDisplayAndNormalizeAndTolerateTypingVariants() {
		String code = PairingCode.of(PairingCode.SPACE - 1);
		assertEquals(PairingCode.LENGTH, code.length());
		assertEquals("9999-ZZ", PairingCode.display(code));
		assertEquals(code, PairingCode.normalize("9999 zz"));
		assertEquals("0123AB", PairingCode.normalize("o123-ab"));
		assertEquals("1111AB", PairingCode.normalize("i111-ab"));
		assertThrows(IllegalArgumentException.class, () -> PairingCode.normalize("1234-IO"));
		assertThrows(IllegalArgumentException.class, () -> PairingCode.normalize("1234AB5"));
		assertThrows(IllegalArgumentException.class, () -> PairingCode.normalize("12AB34"));
	}

	@Test
	void payloadRoundTripsIncludingIpv6AndCarriesNoSecret() {
		PairingPayload payload = new PairingPayload("fe80::1", 8443, 8444, "ab".repeat(32));
		assertEquals(payload, PairingPayload.parse(payload.toText()));
		assertTrue(PairingPayload.isPayload(" icsforms-pair-3:x"));
		assertThrows(IllegalArgumentException.class, () -> PairingPayload.parse("ICSFORMS-PAIR-3:host:1:2:zz"));
	}

	@Test
	void bothSidesGetTheSameNumberAndKeyWhenTheyAgreeOnTheHost() throws GeneralSecurityException {
		SasSuite.Outcome[] outcomes = exchange(HOST);
		assertEquals(outcomes[0].code(), outcomes[1].code());
		assertArrayEquals(outcomes[0].secret(), outcomes[1].secret());
		assertEquals(PairingCode.LENGTH, outcomes[0].code().length());
		assertFalse(outcomes[0].toString().contains(outcomes[0].code()));
	}

	@Test
	void aDifferentObservedHostYieldsADifferentNumber() throws GeneralSecurityException {
		int differing = 0;
		for (int i = 0; i < 5; i++) {
			SasSuite.Outcome[] outcomes = exchange(MITM);
			if (!outcomes[0].code().equals(outcomes[1].code())) {
				differing++;
			}
		}
		assertTrue(differing >= 4);
	}

	@Test
	void tamperedOpeningOrCommitmentIsRejected() throws GeneralSecurityException {
		SasSuite suite = new EcdhSasSuite();
		SasSuite.Initiator device = suite.initiator();
		SasSuite.Responder host = suite.responder();
		byte[] share = host.receiveCommitment(device.commitment());
		byte[] opening = device.accept(share);
		opening[opening.length - 1] ^= 1;
		assertThrows(GeneralSecurityException.class, () -> host.open(opening, HOST));
		assertThrows(GeneralSecurityException.class, () -> suite.responder().receiveCommitment(new byte[] {1}));
	}

	@Test
	void confirmationBindsTheObservedHostIdentity() throws GeneralSecurityException {
		SasSuite.Outcome[] keys = exchange(HOST);
		ChannelSuite suite = new HkdfAesGcmChannelSuite();
		ChannelSuite.PairingChannel client = suite.open(keys[0].secret(), keys[0].transcript());
		ChannelSuite.PairingChannel server = suite.open(keys[1].secret(), keys[1].transcript());
		assertTrue(server.verifyConfirmation("client", HOST, client.confirmation("client", HOST)));
		assertFalse(server.verifyConfirmation("client", HOST, client.confirmation("client", MITM)));
		assertFalse(server.verifyConfirmation("server", HOST, client.confirmation("client", HOST)));
		assertFalse(server.verifyConfirmation("client", HOST, null));
	}

	@Test
	void sealedPayloadsAuthenticateKeyAndPurpose() throws GeneralSecurityException {
		SasSuite.Outcome[] keys = exchange(HOST);
		ChannelSuite suite = new HkdfAesGcmChannelSuite();
		ChannelSuite.PairingChannel client = suite.open(keys[0].secret(), keys[0].transcript());
		ChannelSuite.PairingChannel server = suite.open(keys[1].secret(), keys[1].transcript());
		byte[] sealed = client.seal("csr", "payload".getBytes(StandardCharsets.UTF_8));
		assertEquals("payload", new String(server.unseal("csr", sealed), StandardCharsets.UTF_8));
		assertThrows(GeneralSecurityException.class, () -> server.unseal("other", sealed));
		sealed[sealed.length - 1] ^= 1;
		assertThrows(GeneralSecurityException.class, () -> server.unseal("csr", sealed));
		assertNotEquals(0, sealed.length);
	}

	@Test
	void registryNegotiatesByOfferersPreferenceAndRejectsUnknownSuites() {
		PairingSuites suites = PairingSuites.defaults();
		assertEquals(List.of(EcdhSasSuite.ID), suites.sasIds());
		assertTrue(suites.sas(EcdhSasSuite.ID).isPresent());
		assertTrue(suites.channel("nope").isEmpty());
		assertEquals(EcdhSasSuite.ID,
				PairingSuites.choose(List.of("cpace-x", EcdhSasSuite.ID), suites.sasIds()).orElseThrow());
		assertTrue(PairingSuites.choose(List.of("cpace-x"), suites.sasIds()).isEmpty());
	}
}
