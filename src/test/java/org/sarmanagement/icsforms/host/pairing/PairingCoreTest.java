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

/** Verifies the short-code pairing building blocks: codes, QR payload, PAKE suite and keyed channel. */
class PairingCoreTest {
	private static byte[][] exchange(String clientCode, String serverCode) throws GeneralSecurityException {
		PakeSuite suite = new JpakeSuite();
		PakeExchange client = suite.start(PakeSuite.Role.CLIENT, clientCode.toCharArray());
		PakeExchange server = suite.start(PakeSuite.Role.SERVER, serverCode.toCharArray());
		byte[] c1 = client.round1();
		byte[] s1 = server.round1();
		byte[] s2 = server.round2(c1);
		byte[] c2 = client.round2(s1);
		return new byte[][] {client.complete(s2), server.complete(c2)};
	}

	@Test
	void generatedCodesNormalizeAndTolerateTypingVariants() {
		String code = PairingCode.generate();
		assertEquals(PairingCode.LENGTH, code.length());
		assertEquals(code, PairingCode.normalize(PairingCode.display(code).toLowerCase()));
		assertEquals("01234567", PairingCode.normalize("o123-4567"));
		assertEquals("11111111", PairingCode.normalize("iL1i l1Il"));
		assertThrows(IllegalArgumentException.class, () -> PairingCode.normalize("ABCDEFG"));
		assertThrows(IllegalArgumentException.class, () -> PairingCode.normalize("ABCDEFGU"));
	}

	@Test
	void payloadRoundTripsIncludingIpv6AndFitsAHighCorrectionQrCode() {
		String fingerprint = "ab".repeat(32);
		PairingPayload payload = new PairingPayload("fe80::1", 8443, 8444, fingerprint, "k7qm-4xd9");
		PairingPayload parsed = PairingPayload.parse(payload.toText());
		assertEquals(payload, parsed);
		assertEquals("K7QM4XD9", parsed.code());
		assertFalse(parsed.toString().contains("K7QM"));
		assertTrue(PairingPayload.isPayload(" icsforms-pair-2:x"));
		assertThrows(IllegalArgumentException.class, () -> PairingPayload.parse("ICSFORMS-PAIR-2:host:1:2:zz:K7QM4XD9"));
	}

	@Test
	void sameCodeYieldsTheSameKeyAndADifferentCodeDoesNot() throws GeneralSecurityException {
		byte[][] keys = exchange("K7QM4XD9", "K7QM4XD9");
		assertArrayEquals(keys[0], keys[1]);
		byte[][] wrong = exchange("K7QM4XD9", "K7QM4XD8");
		assertFalse(java.util.Arrays.equals(wrong[0], wrong[1]));
	}

	@Test
	void corruptedPeerMessagesAreRejected() {
		PakeSuite suite = new JpakeSuite();
		PakeExchange server = suite.start(PakeSuite.Role.SERVER, "K7QM4XD9".toCharArray());
		assertThrows(GeneralSecurityException.class, () -> server.round2(new byte[] {1, 2, 3}));
		assertThrows(GeneralSecurityException.class, () -> server.round2(new byte[0]));
	}

	@Test
	void confirmationBindsTheObservedHostIdentity() throws GeneralSecurityException {
		byte[][] keys = exchange("K7QM4XD9", "K7QM4XD9");
		byte[] transcript = "transcript".getBytes(StandardCharsets.UTF_8);
		ChannelSuite suite = new HkdfAesGcmChannelSuite();
		ChannelSuite.PairingChannel client = suite.open(keys[0], transcript);
		ChannelSuite.PairingChannel server = suite.open(keys[1], transcript);
		byte[] real = "real-fingerprint".getBytes(StandardCharsets.UTF_8);
		byte[] mitm = "mitm-fingerprint".getBytes(StandardCharsets.UTF_8);
		assertTrue(server.verifyConfirmation("client", real, client.confirmation("client", real)));
		assertFalse(server.verifyConfirmation("client", real, client.confirmation("client", mitm)));
		assertFalse(server.verifyConfirmation("server", real, client.confirmation("client", real)));
		assertFalse(server.verifyConfirmation("client", real, null));
		ChannelSuite.PairingChannel other = suite.open(keys[1], "other".getBytes(StandardCharsets.UTF_8));
		assertFalse(other.verifyConfirmation("client", real, client.confirmation("client", real)));
	}

	@Test
	void sealedPayloadsAuthenticateKeyAndPurpose() throws GeneralSecurityException {
		byte[][] keys = exchange("K7QM4XD9", "K7QM4XD9");
		ChannelSuite suite = new HkdfAesGcmChannelSuite();
		byte[] transcript = new byte[] {9};
		ChannelSuite.PairingChannel client = suite.open(keys[0], transcript);
		ChannelSuite.PairingChannel server = suite.open(keys[1], transcript);
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
		assertEquals(List.of(JpakeSuite.ID), suites.pakeIds());
		assertTrue(suites.pake(JpakeSuite.ID).isPresent());
		assertTrue(suites.channel("nope").isEmpty());
		assertEquals(JpakeSuite.ID,
				PairingSuites.choose(List.of("cpace-x", JpakeSuite.ID), suites.pakeIds()).orElseThrow());
		assertTrue(PairingSuites.choose(List.of("cpace-x"), suites.pakeIds()).isEmpty());
	}
}
