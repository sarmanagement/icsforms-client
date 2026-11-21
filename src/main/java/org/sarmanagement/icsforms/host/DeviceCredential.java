/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * A device's identity for one host: its private key and certificate in an in-memory PKCS12 key store, plus the
 * pinned host fingerprint. Persisted to an owner-only Java key store file in the pilot.
 */
public final class DeviceCredential {
	static final String KEY_ALIAS = "device";
	static final String CA_ALIAS = "host-ca";
	private final KeyStore keyStore;
	private final char[] password;
	private final String hostFingerprint;
	private final String deviceId;
	private final Instant expiresAt;

	/**
	 * Creates a credential.
	 *
	 * @param keyStore key store holding the device key and chain
	 * @param password key store password
	 * @param hostFingerprint pinned host authority fingerprint
	 * @param deviceId device identifier assigned by the host
	 * @param expiresAt credential expiry
	 */
	DeviceCredential(KeyStore keyStore, char[] password, String hostFingerprint, String deviceId, Instant expiresAt) {
		this.keyStore = keyStore;
		this.password = password.clone();
		this.hostFingerprint = hostFingerprint;
		this.deviceId = deviceId;
		this.expiresAt = expiresAt;
	}

	/**
	 * Returns the pinned host authority fingerprint.
	 *
	 * @return hex fingerprint
	 */
	public String hostFingerprint() {
		return hostFingerprint;
	}

	/**
	 * Returns the host-assigned device identifier.
	 *
	 * @return device id
	 */
	public String deviceId() {
		return deviceId;
	}

	/**
	 * Returns the credential expiry.
	 *
	 * @return expiry time
	 */
	public Instant expiresAt() {
		return expiresAt;
	}

	/**
	 * Returns the fingerprint of the device certificate.
	 *
	 * @return hex fingerprint
	 */
	public String certificateFingerprint() {
		try {
			return HostCrypto.fingerprint((X509Certificate) keyStore.getCertificate(KEY_ALIAS));
		} catch (java.security.KeyStoreException exception) {
			throw new IllegalStateException("Credential key store is not readable", exception);
		}
	}

	/**
	 * Returns the key store holding the device key and chain.
	 *
	 * @return key store
	 */
	KeyStore keyStore() {
		return keyStore;
	}

	/**
	 * Returns a copy of the key store password.
	 *
	 * @return password
	 */
	char[] password() {
		return password.clone();
	}

	/**
	 * Builds a TLS context that presents this device certificate and trusts only the pinned host.
	 *
	 * @return TLS context
	 */
	SSLContext sslContext() {
		try {
			KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			factory.init(keyStore, password);
			SSLContext context = SSLContext.getInstance("TLSv1.3");
			context.init(factory.getKeyManagers(), new javax.net.ssl.TrustManager[] {new PinnedHostTrust(hostFingerprint)},
					null);
			return context;
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot create TLS context", exception);
		}
	}

	/**
	 * Saves the credential to an owner-only key store file. The host authority certificate is stored with it so the
	 * pin can be re-derived on load.
	 *
	 * @param file target file
	 * @throws IOException when the file cannot be written
	 */
	public void save(Path file) throws IOException {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			keyStore.store(bytes, password);
			OwnerOnlyFiles.write(file, bytes.toByteArray());
		} catch (GeneralSecurityException exception) {
			throw new IOException("Cannot store the device credential", exception);
		}
	}

	/**
	 * Loads a credential saved by {@link #save(Path)}.
	 *
	 * @param file key store file
	 * @param password key store password
	 * @return the credential
	 * @throws IOException when the file cannot be read or is not a device credential
	 */
	public static DeviceCredential load(Path file, char[] password) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(in, password);
			if (!store.isKeyEntry(KEY_ALIAS) || store.getCertificate(CA_ALIAS) == null) {
				throw new IOException("Credential file is not a device credential");
			}
			X509Certificate device = (X509Certificate) store.getCertificate(KEY_ALIAS);
			String id = device.getSubjectX500Principal().getName().replaceFirst("^CN=", "").split(",")[0];
			return new DeviceCredential(store, password, HostCrypto.fingerprint((X509Certificate) store.getCertificate(CA_ALIAS)),
					id, device.getNotAfter().toInstant());
		} catch (GeneralSecurityException exception) {
			throw new IOException("Cannot read the device credential", exception);
		}
	}
}
