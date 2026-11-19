package org.sarmanagement.icsforms.model;

import java.util.UUID;

/**
 * Persisted PDF page layout preferences for exports.
 */
public class PdfLayoutSettings implements Identified {
	public enum PaperSize {
		LETTER, A4
	}

	@com.fasterxml.jackson.annotation.JsonProperty("id")
	private String id = UUID.randomUUID().toString();
	private PaperSize paperSize = PaperSize.LETTER;
	private float pageMarginPoints = 18f;

	/** {@inheritDoc} */
	@Override
	public String getId() {
		return id;
	}

	/** {@inheritDoc} */
	@Override
	@com.fasterxml.jackson.annotation.JsonIgnore
	public void setId(String id) {
		IdentityGuard.check(this.id, id);
		this.id = id;
	}

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
