package org.sarmanagement.icsforms.model;

/**
 * Persisted PDF page layout preferences for exports.
 */
public class PdfLayoutSettings {
	public enum PaperSize {
		LETTER, A4
	}

	private PaperSize paperSize = PaperSize.LETTER;
	private float pageMarginPoints = 18f;

	public PaperSize getPaperSize() {
		return paperSize;
	}

	public void setPaperSize(PaperSize paperSize) {
		this.paperSize = paperSize == null ? PaperSize.LETTER : paperSize;
	}

	public float getPageMarginPoints() {
		return pageMarginPoints;
	}

	public void setPageMarginPoints(float pageMarginPoints) {
		this.pageMarginPoints = Math.max(0f, pageMarginPoints);
	}
}
