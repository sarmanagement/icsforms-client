/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Renders text as a QR code image using high error correction. */
final class QrImages {
	private QrImages() {
	}

	/**
	 * Renders the text as a black-on-white QR code with a quiet zone.
	 *
	 * @param text text to encode
	 * @param pixels target edge length in pixels, at least the symbol size
	 * @return the image
	 * @throws IllegalArgumentException when the text cannot be encoded
	 */
	static BufferedImage render(String text, int pixels) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, pixels, pixels,
					Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H, EncodeHintType.MARGIN, 4));
			BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
					BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < matrix.getHeight(); y++) {
				for (int x = 0; x < matrix.getWidth(); x++) {
					image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
				}
			}
			return image;
		} catch (WriterException failure) {
			throw new IllegalArgumentException("Cannot encode the QR code", failure);
		}
	}
}
