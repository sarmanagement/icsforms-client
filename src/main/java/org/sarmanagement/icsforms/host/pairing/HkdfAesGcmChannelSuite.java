/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

/**
 * HKDF-SHA-256 key schedule with HMAC-SHA-256 confirmation and AES-256-GCM payload protection. The key schedule is
 * Bouncy Castle's HKDF and the rest is the JDK.
 */
public final class HkdfAesGcmChannelSuite implements ChannelSuite {
	/** Wire identifier of this suite. */
	public static final String ID = "hkdf-sha256-aes256gcm";
	private static final int KEY_BYTES = 32;
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();

	/** {@inheritDoc} */
	@Override
	public String id() {
		return ID;
	}

	/** {@inheritDoc} */
	@Override
	public PairingChannel open(byte[] pakeKey, byte[] transcript) {
		byte[] salt = sha256(transcript);
		return new Channel(derive(pakeKey, salt, "icsforms pairing confirmation"),
				derive(pakeKey, salt, "icsforms pairing payload"));
	}

	/**
	 * Derives one key.
	 *
	 * @param secret input key material
	 * @param salt transcript hash
	 * @param info purpose label
	 * @return derived key
	 */
	private static byte[] derive(byte[] secret, byte[] salt, String info) {
		HKDFBytesGenerator generator = new HKDFBytesGenerator(new SHA256Digest());
		generator.init(new HKDFParameters(secret, salt, info.getBytes(StandardCharsets.UTF_8)));
		byte[] key = new byte[KEY_BYTES];
		generator.generateBytes(key, 0, key.length);
		return key;
	}

	/**
	 * Hashes bytes.
	 *
	 * @param data input
	 * @return SHA-256 digest
	 */
	private static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	/** Keys for one pairing exchange. */
	private static final class Channel implements PairingChannel {
		private final byte[] confirmationKey;
		private final byte[] payloadKey;

		/**
		 * Holds the derived keys.
		 *
		 * @param confirmationKey key for confirmation tags
		 * @param payloadKey key for payload protection
		 */
		Channel(byte[] confirmationKey, byte[] payloadKey) {
			this.confirmationKey = confirmationKey;
			this.payloadKey = payloadKey;
		}

		/** {@inheritDoc} */
		@Override
		public byte[] confirmation(String label, byte[] hostIdentity) {
			try {
				Mac mac = Mac.getInstance("HmacSHA256");
				mac.init(new SecretKeySpec(confirmationKey, "HmacSHA256"));
				mac.update(label.getBytes(StandardCharsets.UTF_8));
				mac.update((byte) 0);
				return mac.doFinal(hostIdentity);
			} catch (GeneralSecurityException exception) {
				throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
			}
		}

		/** {@inheritDoc} */
		@Override
		public boolean verifyConfirmation(String label, byte[] hostIdentity, byte[] tag) {
			return MessageDigest.isEqual(confirmation(label, hostIdentity), tag == null ? new byte[0] : tag);
		}

		/** {@inheritDoc} */
		@Override
		public byte[] seal(String purpose, byte[] plaintext) throws GeneralSecurityException {
			byte[] nonce = new byte[NONCE_BYTES];
			RANDOM.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(payloadKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
			byte[] body = cipher.doFinal(plaintext);
			byte[] result = Arrays.copyOf(nonce, NONCE_BYTES + body.length);
			System.arraycopy(body, 0, result, NONCE_BYTES, body.length);
			return result;
		}

		/** {@inheritDoc} */
		@Override
		public byte[] unseal(String purpose, byte[] sealed) throws GeneralSecurityException {
			if (sealed == null || sealed.length < NONCE_BYTES + TAG_BITS / 8) {
				throw new GeneralSecurityException("Protected payload is too short");
			}
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(payloadKey, "AES"),
					new GCMParameterSpec(TAG_BITS, Arrays.copyOf(sealed, NONCE_BYTES)));
			cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
			return cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES);
		}
	}
}
