/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores the authority key in an owner-only Java PKCS#12 key store inside host-owned storage. The key store
 * password lives in a sibling owner-only file; operating-system file permissions are the real protection in the
 * pilot, and OS-protected key storage is a later stage.
 */
public final class FileHostKeyStore implements HostKeyStore {
	private static final String KEYSTORE_FILE = "ca.p12";
	private static final String PASSWORD_FILE = "ca.pass";
	private static final String ALIAS = "ca";
	private final Path directory;
	private final Clock clock;

	/**
	 * Creates a file-backed key store.
	 *
	 * @param directory host-owned key directory
	 * @param clock clock used for certificate validity
	 */
	public FileHostKeyStore(Path directory, Clock clock) {
		this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath();
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** {@inheritDoc} */
	@Override
	public synchronized HostAuthority loadOrCreate() {
		Path keystore = directory.resolve(KEYSTORE_FILE);
		Path password = directory.resolve(PASSWORD_FILE);
		try {
			if (Files.exists(keystore)) {
				if (!Files.exists(password)) {
					throw new HostStorageException("Host authority key store exists without its password file");
				}
				return load(Files.readAllBytes(keystore), Files.readString(password, StandardCharsets.UTF_8).trim());
			}
			OwnerOnlyFiles.createDirectory(directory);
			HostAuthority authority = HostAuthority.generate(clock.instant());
			char[] secret = Base64.getUrlEncoder().withoutPadding().encodeToString(HostCrypto.randomBytes(32))
					.toCharArray();
			OwnerOnlyFiles.write(password, new String(secret).getBytes(StandardCharsets.UTF_8));
			OwnerOnlyFiles.write(keystore, serialize(authority, secret));
			return authority;
		} catch (IOException | GeneralSecurityException exception) {
			throw new HostStorageException("Cannot load or create the host authority key store", exception);
		}
	}

	/** {@inheritDoc} */
	@Override
	public synchronized Map<String, byte[]> exportForBackup() {
		try {
			Map<String, byte[]> entries = new LinkedHashMap<>();
			entries.put(KEYSTORE_FILE, Files.readAllBytes(directory.resolve(KEYSTORE_FILE)));
			entries.put(PASSWORD_FILE, Files.readAllBytes(directory.resolve(PASSWORD_FILE)));
			return entries;
		} catch (IOException exception) {
			throw new HostStorageException("Cannot read the host authority key store for backup", exception);
		}
	}

	/** {@inheritDoc} */
	@Override
	public synchronized void importFromBackup(Map<String, byte[]> entries) {
		byte[] keystore = entries.get(KEYSTORE_FILE);
		byte[] password = entries.get(PASSWORD_FILE);
		if (keystore == null || password == null) {
			throw new HostStorageException("Backup does not contain the host authority key store");
		}
		try {
			load(keystore, new String(password, StandardCharsets.UTF_8).trim());
			OwnerOnlyFiles.createDirectory(directory);
			OwnerOnlyFiles.write(directory.resolve(PASSWORD_FILE), password);
			OwnerOnlyFiles.write(directory.resolve(KEYSTORE_FILE), keystore);
		} catch (IOException | GeneralSecurityException exception) {
			throw new HostStorageException("Cannot restore the host authority key store", exception);
		}
	}

	/**
	 * Parses key store bytes into an authority.
	 *
	 * @param bytes PKCS#12 content
	 * @param password key store password
	 * @return authority
	 * @throws IOException when the content cannot be read
	 * @throws GeneralSecurityException when the key store is invalid
	 */
	private HostAuthority load(byte[] bytes, String password) throws IOException, GeneralSecurityException {
		char[] secret = password.toCharArray();
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(new ByteArrayInputStream(bytes), secret);
		Key key = store.getKey(ALIAS, secret);
		Certificate certificate = store.getCertificate(ALIAS);
		if (!(key instanceof PrivateKey privateKey) || !(certificate instanceof X509Certificate x509)) {
			throw new GeneralSecurityException("Key store does not hold an authority key");
		}
		return new HostAuthority(privateKey, x509);
	}

	/**
	 * Serializes an authority to PKCS#12 bytes.
	 *
	 * @param authority authority to store
	 * @param secret key store password
	 * @return PKCS#12 content
	 * @throws IOException when the content cannot be written
	 * @throws GeneralSecurityException when the key store cannot be built
	 */
	private byte[] serialize(HostAuthority authority, char[] secret) throws IOException, GeneralSecurityException {
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(null, null);
		store.setKeyEntry(ALIAS, authority.privateKey(), secret, new Certificate[] {authority.certificate()});
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		store.store(output, secret);
		return output.toByteArray();
	}
}
