/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host.pairing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.Locale;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant;
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroups;
import org.bouncycastle.crypto.agreement.jpake.JPAKERound1Payload;
import org.bouncycastle.crypto.agreement.jpake.JPAKERound2Payload;
import org.bouncycastle.util.BigIntegers;

/**
 * Finite-field J-PAKE (RFC 8236) over the NIST 3072-bit prime-order group, provided by Bouncy Castle. The
 * elliptic-curve variant is not usable here because its zero-knowledge proof type cannot be rebuilt from received
 * bytes through the library's public API.
 */
public final class JpakeSuite implements PakeSuite {
	/** Wire identifier of this suite. */
	public static final String ID = "jpake-ff-3072";
	private static final int MAX_FIELD = 1024;
	private static final int MAX_PROOF = 4;

	/** {@inheritDoc} */
	@Override
	public String id() {
		return ID;
	}

	/** {@inheritDoc} */
	@Override
	public PakeExchange start(Role role, char[] password) {
		String self = role.name().toLowerCase(Locale.ROOT);
		String peer = (role == Role.CLIENT ? Role.SERVER : Role.CLIENT).name().toLowerCase(Locale.ROOT);
		return new Exchange(new JPAKEParticipant(self, password.clone(), JPAKEPrimeOrderGroups.NIST_3072), peer);
	}

	/** One party's J-PAKE state. */
	private static final class Exchange implements PakeExchange {
		private final JPAKEParticipant participant;
		private final String peerId;

		/**
		 * Wraps a participant.
		 *
		 * @param participant library participant
		 * @param peerId identifier the peer must use
		 */
		Exchange(JPAKEParticipant participant, String peerId) {
			this.participant = participant;
			this.peerId = peerId;
		}

		/** {@inheritDoc} */
		@Override
		public byte[] round1() {
			JPAKERound1Payload payload = participant.createRound1PayloadToSend();
			return write(payload.getGx1(), payload.getGx2(), payload.getKnowledgeProofForX1(),
					payload.getKnowledgeProofForX2());
		}

		/** {@inheritDoc} */
		@Override
		public byte[] round2(byte[] peerRound1) throws GeneralSecurityException {
			try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(peerRound1))) {
				BigInteger gx1 = readInteger(in);
				BigInteger gx2 = readInteger(in);
				BigInteger[] proof1 = readProof(in);
				BigInteger[] proof2 = readProof(in);
				participant.validateRound1PayloadReceived(new JPAKERound1Payload(peerId, gx1, gx2, proof1, proof2));
			} catch (IOException | CryptoException | RuntimeException failure) {
				throw new GeneralSecurityException("The pairing exchange failed validation", failure);
			}
			JPAKERound2Payload payload = participant.createRound2PayloadToSend();
			return write(payload.getA(), null, payload.getKnowledgeProofForX2s(), null);
		}

		/** {@inheritDoc} */
		@Override
		public byte[] complete(byte[] peerRound2) throws GeneralSecurityException {
			try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(peerRound2))) {
				BigInteger a = readInteger(in);
				BigInteger[] proof = readProof(in);
				participant.validateRound2PayloadReceived(new JPAKERound2Payload(peerId, a, proof));
			} catch (IOException | CryptoException | RuntimeException failure) {
				throw new GeneralSecurityException("The pairing exchange failed validation", failure);
			}
			return BigIntegers.asUnsignedByteArray(participant.calculateKeyingMaterial());
		}

		/**
		 * Encodes up to two values and up to two proofs as length-prefixed fields.
		 *
		 * @param first first value
		 * @param second second value, or null
		 * @param firstProof first proof
		 * @param secondProof second proof, or null
		 * @return encoded message
		 */
		private static byte[] write(BigInteger first, BigInteger second, BigInteger[] firstProof,
				BigInteger[] secondProof) {
			try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					DataOutputStream out = new DataOutputStream(bytes)) {
				writeInteger(out, first);
				if (second != null) {
					writeInteger(out, second);
				}
				writeProof(out, firstProof);
				if (secondProof != null) {
					writeProof(out, secondProof);
				}
				out.flush();
				return bytes.toByteArray();
			} catch (IOException impossible) {
				throw new IllegalStateException(impossible);
			}
		}

		/**
		 * Writes one integer.
		 *
		 * @param out destination
		 * @param value integer
		 * @throws IOException when writing fails
		 */
		private static void writeInteger(DataOutputStream out, BigInteger value) throws IOException {
			byte[] bytes = BigIntegers.asUnsignedByteArray(value);
			out.writeShort(bytes.length);
			out.write(bytes);
		}

		/**
		 * Writes one proof.
		 *
		 * @param out destination
		 * @param proof proof integers
		 * @throws IOException when writing fails
		 */
		private static void writeProof(DataOutputStream out, BigInteger[] proof) throws IOException {
			out.writeByte(proof.length);
			for (BigInteger value : proof) {
				writeInteger(out, value);
			}
		}

		/**
		 * Reads one integer, bounding its size.
		 *
		 * @param in source
		 * @return integer
		 * @throws IOException when the field is truncated or oversized
		 */
		private static BigInteger readInteger(DataInputStream in) throws IOException {
			int length = in.readUnsignedShort();
			if (length == 0 || length > MAX_FIELD) {
				throw new IOException("Field length out of range");
			}
			byte[] bytes = new byte[length];
			in.readFully(bytes);
			return new BigInteger(1, bytes);
		}

		/**
		 * Reads one proof, bounding its length.
		 *
		 * @param in source
		 * @return proof integers
		 * @throws IOException when the proof is truncated or oversized
		 */
		private static BigInteger[] readProof(DataInputStream in) throws IOException {
			int count = in.readUnsignedByte();
			if (count == 0 || count > MAX_PROOF) {
				throw new IOException("Proof length out of range");
			}
			BigInteger[] proof = new BigInteger[count];
			for (int index = 0; index < count; index++) {
				proof[index] = readInteger(in);
			}
			return proof;
		}
	}
}
