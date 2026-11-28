/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.host.pairing.PairingPayload;

/** Verifies that the rendered pairing QR code decodes to the payload. */
class QrImagesTest {
	/**
	 * A worst-case sized payload survives rendering and decoding.
	 *
	 * @throws Exception when decoding fails
	 */
	@Test
	void payloadRoundTripsThroughQr() throws Exception {
		String text = new PairingPayload("host-" + "a".repeat(40) + ".example.org", 8443, 8444, "ab".repeat(32)).toText();
		var image = QrImages.render(text, 400);
		try {
			String decoded = new QRCodeReader()
					.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
			assertEquals(text, decoded);
		} catch (NotFoundException failure) {
			throw new AssertionError("QR code not decodable", failure);
		}
	}
}
