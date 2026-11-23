/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trust manager for typed-code pairing, where no fingerprint is known in advance. It validates the presented chain
 * against its own last certificate, so a peer must prove possession of a key certified by that authority, and it
 * records that authority's fingerprint without trusting it. Trust is established only if the pairing key
 * confirmation, which binds the recorded fingerprint, succeeds. Every later handshake must present the same
 * authority.
 */
final class CapturingHostTrust implements X509TrustManager {
	private final AtomicReference<String> observed = new AtomicReference<>();

	/** {@inheritDoc} */
	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new CertificateException("Client certificates are not accepted by a client trust manager");
	}

	/** {@inheritDoc} */
	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (chain == null || chain.length == 0) {
			throw new CertificateException("No certificate chain");
		}
		X509Certificate anchor = chain[chain.length - 1];
		String fingerprint = HostCrypto.fingerprint(anchor);
		if (!observed.compareAndSet(null, fingerprint) && !fingerprint.equals(observed.get())) {
			throw new PinnedHostTrust.IdentityMismatchException();
		}
		try {
			KeyStore anchors = KeyStore.getInstance("PKCS12");
			anchors.load(null, null);
			anchors.setCertificateEntry("observed", anchor);
			TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
			factory.init(anchors);
			for (TrustManager manager : factory.getTrustManagers()) {
				if (manager instanceof X509TrustManager x509) {
					x509.checkServerTrusted(chain, authType);
					return;
				}
			}
			throw new CertificateException("No trust manager available");
		} catch (GeneralSecurityException | java.io.IOException exception) {
			if (exception instanceof CertificateException certificateException) {
				throw certificateException;
			}
			throw new CertificateException("Cannot validate the host certificate", exception);
		}
	}

	/** {@inheritDoc} */
	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}

	/**
	 * Returns the authority fingerprint seen on the first handshake.
	 *
	 * @return lower-case hex SHA-256 fingerprint, or null before any handshake
	 */
	String observedFingerprint() {
		return observed.get();
	}
}
