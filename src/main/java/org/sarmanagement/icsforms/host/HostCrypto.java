/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Thin wrapper over the JDK and Bouncy Castle for the few X.509 and PKCS#10 operations host mode needs. It
 * contains no cryptographic algorithms of its own; every primitive is delegated to an established provider.
 */
final class HostCrypto {
	/** Signature algorithm used for every host-issued certificate and request. */
	static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
	private static final Duration BACKDATE = Duration.ofMinutes(5);
	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * Prevents construction of this utility class.
	 */
	private HostCrypto() {
	}

	/**
	 * Generates a NIST P-256 key pair.
	 *
	 * @return new key pair
	 */
	static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
			return generator.generateKeyPair();
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Elliptic curve keys are unavailable", exception);
		}
	}

	/**
	 * Creates a self-signed certificate authority certificate.
	 *
	 * @param keys authority key pair
	 * @param commonName subject common name
	 * @param now issue time
	 * @param lifetime validity period
	 * @return signed certificate
	 */
	static X509Certificate createAuthority(KeyPair keys, String commonName, Instant now, Duration lifetime) {
		try {
			X500Name name = new X500Name("CN=" + escape(commonName));
			X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, serial(), date(now.minus(BACKDATE)),
					date(now.plus(lifetime)), name, keys.getPublic());
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
			builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
			return sign(builder, keys.getPrivate());
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot build authority certificate", exception);
		}
	}

	/**
	 * Issues the host's TLS server certificate.
	 *
	 * @param authorityKey authority private key
	 * @param authority authority certificate
	 * @param serverKey server public key
	 * @param addresses DNS names and IP addresses the host answers to
	 * @param now issue time
	 * @param lifetime validity period
	 * @return signed server certificate
	 */
	static X509Certificate issueServer(PrivateKey authorityKey, X509Certificate authority, PublicKey serverKey,
			List<String> addresses, Instant now, Duration lifetime) {
		try {
			X509v3CertificateBuilder builder = leaf(authority, serverKey, "icsforms-host", now,
					now.plus(lifetime));
			builder.addExtension(Extension.keyUsage, true,
					new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyAgreement));
			builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
			List<GeneralName> names = new ArrayList<>();
			for (String address : addresses) {
				names.add(generalName(address));
			}
			builder.addExtension(Extension.subjectAlternativeName, false,
					new GeneralNames(names.toArray(new GeneralName[0])));
			return sign(builder, authorityKey);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot build server certificate", exception);
		}
	}

	/**
	 * Issues a device client-authentication certificate.
	 *
	 * @param authorityKey authority private key
	 * @param authority authority certificate
	 * @param deviceKey device public key from a verified request
	 * @param deviceId stable device identifier used as the subject common name
	 * @param now issue time
	 * @param notAfter credential expiry
	 * @return signed device certificate
	 */
	static X509Certificate issueDevice(PrivateKey authorityKey, X509Certificate authority, PublicKey deviceKey,
			String deviceId, Instant now, Instant notAfter) {
		try {
			X509v3CertificateBuilder builder = leaf(authority, deviceKey, "device-" + deviceId, now, notAfter);
			builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
			builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
			return sign(builder, authorityKey);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot build device certificate", exception);
		}
	}

	/**
	 * Creates the common builder for end-entity certificates.
	 *
	 * @param authority issuer certificate
	 * @param key subject public key
	 * @param commonName subject common name
	 * @param now issue time
	 * @param notAfter expiry
	 * @return builder with basic constraints applied
	 * @throws IOException when an extension cannot be encoded
	 */
	private static X509v3CertificateBuilder leaf(X509Certificate authority, PublicKey key, String commonName,
			Instant now, Instant notAfter) throws IOException {
		X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
				new X500Name(authority.getSubjectX500Principal().getName()), serial(), date(now.minus(BACKDATE)),
				date(notAfter), new X500Name("CN=" + escape(commonName)), key);
		builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
		return builder;
	}

	/**
	 * Signs a certificate builder with the given key.
	 *
	 * @param builder populated certificate builder
	 * @param signingKey issuer private key
	 * @return signed certificate
	 */
	private static X509Certificate sign(X509v3CertificateBuilder builder, PrivateKey signingKey) {
		try {
			ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(signingKey);
			return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
		} catch (OperatorCreationException | GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot sign certificate", exception);
		}
	}

	/**
	 * Builds a subject alternative name for a DNS name or literal IP address.
	 *
	 * @param address host name or IP literal
	 * @return general name entry
	 */
	private static GeneralName generalName(String address) {
		boolean literal = address.matches("[0-9.]+") || address.contains(":");
		if (literal) {
			return new GeneralName(GeneralName.iPAddress, address);
		}
		return new GeneralName(GeneralName.dNSName, address);
	}

	/**
	 * Creates a PKCS#10 certification request proving possession of a device private key.
	 *
	 * @param keys device key pair
	 * @param commonName requested common name, ignored by the host
	 * @return PEM-encoded request
	 */
	static String createRequest(KeyPair keys, String commonName) {
		try {
			PKCS10CertificationRequest request = new JcaPKCS10CertificationRequestBuilder(
					new X500Name("CN=" + escape(commonName)), keys.getPublic())
					.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate()));
			return pem("CERTIFICATE REQUEST", request.getEncoded());
		} catch (OperatorCreationException | IOException exception) {
			throw new IllegalStateException("Cannot build certification request", exception);
		}
	}

	/**
	 * Parses a PKCS#10 request, verifies its proof of possession and returns its public key. Only elliptic curve keys
	 * of at least 256 bits are accepted.
	 *
	 * @param pem PEM-encoded request
	 * @return verified device public key
	 * @throws GeneralSecurityException when the request is malformed, unsigned or uses an unacceptable key
	 */
	static PublicKey verifyRequest(String pem) throws GeneralSecurityException {
		try (PEMParser parser = new PEMParser(new StringReader(pem))) {
			Object parsed = parser.readObject();
			if (!(parsed instanceof PKCS10CertificationRequest request)) {
				throw new GeneralSecurityException("Not a certification request");
			}
			if (!request.isSignatureValid(new JcaContentVerifierProviderBuilder().setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
					.build(request.getSubjectPublicKeyInfo()))) {
				throw new GeneralSecurityException("Request signature is invalid");
			}
			PublicKey key = java.security.KeyFactory.getInstance("EC").generatePublic(
					new java.security.spec.X509EncodedKeySpec(request.getSubjectPublicKeyInfo().getEncoded()));
			if (!(key instanceof ECPublicKey ecKey) || ecKey.getParams().getOrder().bitLength() < 256) {
				throw new GeneralSecurityException("Unacceptable device key");
			}
			return key;
		} catch (IOException | PKCSException | OperatorCreationException | RuntimeException exception) {
			throw new GeneralSecurityException("Malformed certification request", exception);
		}
	}

	/**
	 * Encodes a certificate as PEM.
	 *
	 * @param certificate certificate to encode
	 * @return PEM text
	 */
	static String pem(X509Certificate certificate) {
		try {
			return pem("CERTIFICATE", certificate.getEncoded());
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot encode certificate", exception);
		}
	}

	/**
	 * Decodes a PEM certificate.
	 *
	 * @param pem PEM text
	 * @return parsed certificate
	 * @throws GeneralSecurityException when the text is not a certificate
	 */
	static X509Certificate parseCertificate(String pem) throws GeneralSecurityException {
		try (org.bouncycastle.util.io.pem.PemReader reader = new org.bouncycastle.util.io.pem.PemReader(
				new StringReader(pem))) {
			org.bouncycastle.util.io.pem.PemObject object = reader.readPemObject();
			if (object == null || !"CERTIFICATE".equals(object.getType())) {
				throw new GeneralSecurityException("Not a certificate");
			}
			return (X509Certificate) CertificateFactory.getInstance("X.509")
					.generateCertificate(new ByteArrayInputStream(object.getContent()));
		} catch (IOException exception) {
			throw new GeneralSecurityException("Malformed certificate", exception);
		}
	}

	/**
	 * Computes the lowercase hexadecimal SHA-256 fingerprint of a certificate.
	 *
	 * @param certificate certificate to fingerprint
	 * @return 64-character fingerprint
	 */
	static String fingerprint(X509Certificate certificate) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot fingerprint certificate", exception);
		}
	}

	/**
	 * Computes the lowercase hexadecimal SHA-256 digest of arbitrary text, used to store invitation secrets only as
	 * hashes.
	 *
	 * @param text value to hash
	 * @return 64-character digest
	 */
	static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	/**
	 * Returns random bytes from the platform's secure random source.
	 *
	 * @param length number of bytes
	 * @return random bytes
	 */
	static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	/**
	 * Checks whether a string is a literal IP address that can be bound or advertised.
	 *
	 * @param address candidate text
	 * @return true when the text parses as an IP literal
	 */
	static boolean isIpLiteral(String address) {
		if (!(address.matches("[0-9.]+") || address.contains(":"))) {
			return false;
		}
		try {
			InetAddress.getByName(address);
			return true;
		} catch (java.net.UnknownHostException exception) {
			return false;
		}
	}

	/**
	 * Generates a positive random certificate serial number.
	 *
	 * @return serial number
	 */
	private static BigInteger serial() {
		return new BigInteger(127, RANDOM).add(BigInteger.ONE);
	}

	/**
	 * Converts an instant to a legacy date.
	 *
	 * @param instant instant to convert
	 * @return date
	 */
	private static Date date(Instant instant) {
		return Date.from(instant);
	}

	/**
	 * Escapes a value for use inside a distinguished name.
	 *
	 * @param value raw value
	 * @return value with RFC 4514 special characters removed
	 */
	private static String escape(String value) {
		return value.replaceAll("[^A-Za-z0-9 ._-]", "_");
	}

	/**
	 * Encodes DER bytes as a PEM block.
	 *
	 * @param type PEM block type
	 * @param der DER content
	 * @return PEM text
	 */
	private static String pem(String type, byte[] der) {
		try (StringWriter text = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(text)) {
			writer.writeObject(new org.bouncycastle.util.io.pem.PemObject(type, der));
			writer.flush();
			return text.toString();
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot encode PEM", exception);
		}
	}
}
