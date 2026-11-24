/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.sarmanagement.icsforms.crypto.Fips;

/**
 * ECDH on P-256 with a hash commitment from the device, and a short number derived with HMAC-SHA-256. All
 * primitives come from the Bouncy Castle FIPS provider; only the message flow is defined here.
 */
public final class EcdhSasSuite implements SasSuite {
	/** Wire identifier. */
	public static final String ID = "ecdh-p256-sha256-sas";
	private static final int NONCE_BYTES = 16;
	private static final SecureRandom RANDOM = new SecureRandom();

	/** {@inheritDoc} */
	@Override
	public String id() {
		return ID;
	}

	/** {@inheritDoc} */
	@Override
	public Initiator initiator() throws GeneralSecurityException {
		return new Device();
	}

	/** {@inheritDoc} */
	@Override
	public Responder responder() {
		return new Host();
	}

	/**
	 * Generates an ephemeral P-256 key pair.
	 *
	 * @return the key pair
	 * @throws GeneralSecurityException when generation fails
	 */
	private static KeyPair newKeys() throws GeneralSecurityException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", Fips.provider());
		generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
		return generator.generateKeyPair();
	}

	/**
	 * Computes the ECDH shared secret.
	 *
	 * @param own own key pair
	 * @param peerShare peer's encoded public key
	 * @return shared secret
	 * @throws GeneralSecurityException when the share is not a valid P-256 public key
	 */
	private static byte[] agree(KeyPair own, byte[] peerShare) throws GeneralSecurityException {
		PublicKey peer = KeyFactory.getInstance("EC", Fips.provider())
				.generatePublic(new X509EncodedKeySpec(peerShare));
		KeyAgreement agreement = KeyAgreement.getInstance("ECDH", Fips.provider());
		agreement.init(own.getPrivate());
		agreement.doPhase(peer, true);
		return agreement.generateSecret();
	}

	/**
	 * Hashes an opening to its commitment.
	 *
	 * @param opening nonce followed by the share
	 * @return commitment
	 * @throws GeneralSecurityException when SHA-256 is unavailable
	 */
	private static byte[] commit(byte[] opening) throws GeneralSecurityException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256", Fips.provider());
		digest.update("icsforms-sas-commitment".getBytes(StandardCharsets.UTF_8));
		return digest.digest(opening);
	}

	/**
	 * Derives the outcome both ends compute.
	 *
	 * @param secret ECDH secret
	 * @param commitment device commitment
	 * @param hostShare host key share
	 * @param opening device opening
	 * @param identity host fingerprint bytes
	 * @return the outcome
	 */
	private static Outcome derive(byte[] secret, byte[] commitment, byte[] hostShare, byte[] opening,
			byte[] identity) {
		try {
			byte[] transcript = PairingTranscript.of(ID.getBytes(StandardCharsets.UTF_8), commitment, hostShare,
					opening);
			Mac mac = Mac.getInstance("HmacSHA256", Fips.provider());
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			mac.update("icsforms-sas".getBytes(StandardCharsets.UTF_8));
			mac.update(PairingTranscript.of(transcript, identity));
			byte[] tag = mac.doFinal();
			long value = 0;
			for (int index = 0; index < 8; index++) {
				value = (value << 8) | (tag[index] & 0xFF);
			}
			String code = PairingCode.of(Long.remainderUnsigned(value, PairingCode.SPACE));
			byte[] bound = PairingTranscript.of(transcript, identity);
			return new Outcome(code, secret, bound);
		} catch (GeneralSecurityException failure) {
			throw new IllegalStateException("HMAC-SHA-256 is unavailable", failure);
		}
	}

	/** The device's half. */
	private static final class Device implements Initiator {
		private final KeyPair keys;
		private final byte[] opening;
		private final byte[] commitment;
		private byte[] hostShare;
		private byte[] secret;

		/**
		 * Creates the initiator with a fresh share, nonce and commitment.
		 *
		 * @throws GeneralSecurityException when key generation fails
		 */
		Device() throws GeneralSecurityException {
			keys = newKeys();
			byte[] share = keys.getPublic().getEncoded();
			byte[] nonce = new byte[NONCE_BYTES];
			RANDOM.nextBytes(nonce);
			opening = new byte[NONCE_BYTES + share.length];
			System.arraycopy(nonce, 0, opening, 0, NONCE_BYTES);
			System.arraycopy(share, 0, opening, NONCE_BYTES, share.length);
			commitment = commit(opening);
		}

		/** {@inheritDoc} */
		@Override
		public byte[] commitment() {
			return commitment.clone();
		}

		/** {@inheritDoc} */
		@Override
		public byte[] accept(byte[] responderShare) throws GeneralSecurityException {
			secret = agree(keys, responderShare);
			hostShare = responderShare.clone();
			return opening.clone();
		}

		/** {@inheritDoc} */
		@Override
		public Outcome outcome(byte[] hostIdentity) {
			if (secret == null) {
				throw new IllegalStateException("The host's share has not been accepted");
			}
			return derive(secret, commitment, hostShare, opening, hostIdentity);
		}
	}

	/** The host's half. */
	private static final class Host implements Responder {
		private KeyPair keys;
		private byte[] commitment;
		private byte[] hostShare;

		/** {@inheritDoc} */
		@Override
		public byte[] receiveCommitment(byte[] deviceCommitment) throws GeneralSecurityException {
			if (keys != null || deviceCommitment == null || deviceCommitment.length != 32) {
				throw new GeneralSecurityException("Invalid commitment");
			}
			commitment = deviceCommitment.clone();
			keys = newKeys();
			hostShare = keys.getPublic().getEncoded();
			return hostShare.clone();
		}

		/** {@inheritDoc} */
		@Override
		public Outcome open(byte[] opening, byte[] hostIdentity) throws GeneralSecurityException {
			if (keys == null || opening == null || opening.length <= NONCE_BYTES) {
				throw new GeneralSecurityException("Invalid opening");
			}
			if (!MessageDigest.isEqual(commit(opening), commitment)) {
				throw new GeneralSecurityException("The opening does not match the commitment");
			}
			byte[] share = Arrays.copyOfRange(opening, NONCE_BYTES, opening.length);
			return derive(agree(keys, share), commitment, hostShare, opening, hostIdentity);
		}
	}
}
