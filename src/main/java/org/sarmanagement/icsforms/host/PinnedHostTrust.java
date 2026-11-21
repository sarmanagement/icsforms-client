/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trusts only a host whose certificate chain contains the pinned authority certificate. The pinned certificate
 * becomes the sole path-validation anchor, so normal expiry and signature checks still apply, and a changed host
 * identity is never trusted silently.
 */
final class PinnedHostTrust implements X509TrustManager {
	/**
	 * Raised when the presented chain does not contain the pinned certificate.
	 */
	static final class IdentityMismatchException extends CertificateException {
		private static final long serialVersionUID = 1L;

		/**
		 * Creates the exception.
		 */
		IdentityMismatchException() {
			super("The host identity does not match the pinned fingerprint");
		}
	}

	private final String pinnedFingerprint;

	/**
	 * Creates a trust manager.
	 *
	 * @param pinnedFingerprint SHA-256 fingerprint of the host authority certificate, hex
	 */
	PinnedHostTrust(String pinnedFingerprint) {
		this.pinnedFingerprint = pinnedFingerprint.toLowerCase(Locale.ROOT);
	}

	/** {@inheritDoc} */
	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new CertificateException("Client certificates are not accepted by a client trust manager");
	}

	/** {@inheritDoc} */
	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		X509Certificate anchor = null;
		for (X509Certificate certificate : chain) {
			if (pinnedFingerprint.equals(HostCrypto.fingerprint(certificate))) {
				anchor = certificate;
				break;
			}
		}
		if (anchor == null) {
			throw new IdentityMismatchException();
		}
		try {
			KeyStore anchors = KeyStore.getInstance("PKCS12");
			anchors.load(null, null);
			anchors.setCertificateEntry("pinned", anchor);
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
}
