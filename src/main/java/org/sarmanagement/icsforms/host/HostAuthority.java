/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The host-run certificate authority. Its fingerprint is the host identity that invitations carry and devices
 * pin. The private key never leaves this object except through the {@link HostKeyStore} boundary.
 */
public final class HostAuthority {
	private static final Duration AUTHORITY_LIFETIME = Duration.ofDays(3650);
	private static final Duration SERVER_LIFETIME = Duration.ofDays(365);
	private final PrivateKey key;
	private final X509Certificate certificate;

	/**
	 * Wraps existing authority key material.
	 *
	 * @param key authority private key
	 * @param certificate self-signed authority certificate
	 */
	HostAuthority(PrivateKey key, X509Certificate certificate) {
		this.key = Objects.requireNonNull(key, "key");
		this.certificate = Objects.requireNonNull(certificate, "certificate");
	}

	/**
	 * Generates a new authority with a fresh key pair.
	 *
	 * @param now creation time
	 * @return new authority
	 */
	static HostAuthority generate(Instant now) {
		KeyPair keys = HostCrypto.generateKeyPair();
		X509Certificate certificate = HostCrypto.createAuthority(keys, "ICS Forms host authority", now,
				AUTHORITY_LIFETIME);
		return new HostAuthority(keys.getPrivate(), certificate);
	}

	/**
	 * Returns the authority private key for persistence by a key store.
	 *
	 * @return private key
	 */
	PrivateKey privateKey() {
		return key;
	}

	/**
	 * Returns the authority certificate.
	 *
	 * @return self-signed certificate
	 */
	X509Certificate certificate() {
		return certificate;
	}

	/**
	 * Returns the host identity fingerprint that devices pin.
	 *
	 * @return lowercase SHA-256 hexadecimal fingerprint of the authority certificate
	 */
	public String fingerprint() {
		return HostCrypto.fingerprint(certificate);
	}

	/**
	 * Returns the public authority certificate in PEM form.
	 *
	 * @return PEM certificate
	 */
	public String certificatePem() {
		return HostCrypto.pem(certificate);
	}

	/**
	 * Signs a device certificate for a public key whose proof of possession was already verified.
	 *
	 * @param deviceKey device public key
	 * @param deviceId device identifier
	 * @param now issue time
	 * @param notAfter credential expiry
	 * @return signed certificate
	 */
	X509Certificate issueDevice(PublicKey deviceKey, String deviceId, Instant now, Instant notAfter) {
		return HostCrypto.issueDevice(key, certificate, deviceKey, deviceId, now, notAfter);
	}

	/**
	 * Creates an in-memory TLS server identity signed by this authority. The key is regenerated on each start so
	 * the certificate always lists the addresses the host currently answers to.
	 *
	 * @param addresses DNS names and IP literals to list in the certificate
	 * @param now issue time
	 * @param password in-memory key store password
	 * @return key store holding the server key and chain
	 */
	KeyStore newServerKeyStore(List<String> addresses, Instant now, char[] password) {
		try {
			KeyPair serverKeys = HostCrypto.generateKeyPair();
			X509Certificate server = HostCrypto.issueServer(key, certificate, serverKeys.getPublic(), addresses, now,
					SERVER_LIFETIME);
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(null, null);
			store.setKeyEntry("server", serverKeys.getPrivate(), password, new X509Certificate[] {server, certificate});
			return store;
		} catch (GeneralSecurityException | java.io.IOException exception) {
			throw new IllegalStateException("Cannot create server identity", exception);
		}
	}

	/**
	 * Creates a key store that trusts only this authority, used to validate client certificates.
	 *
	 * @return trust store with a single anchor
	 */
	KeyStore newTrustStore() {
		try {
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(null, null);
			store.setCertificateEntry("host-ca", certificate);
			return store;
		} catch (GeneralSecurityException | java.io.IOException exception) {
			throw new IllegalStateException("Cannot create trust store", exception);
		}
	}
}
