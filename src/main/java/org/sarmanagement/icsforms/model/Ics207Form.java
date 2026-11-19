package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Form data for the Incident Organization Chart (ICS 207).
 *
 * <p>
 * The organizational structure itself is stored in the shared
 * {@link OrganizationalChart} on {@link AppData}. This form holds only the
 * metadata that appears in the ICS 207 header and footer: preparer block and
 * the auto-assigned IAP page number.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ics207Form implements Identified {
	@com.fasterxml.jackson.annotation.JsonProperty("id")
	private String id = UUID.randomUUID().toString();
	private String preparedByName = "";
	private String preparedByPositionTitle = "";
	private LocalDateTime preparedDateTime = null;
	private String iapPage = "";

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

	public String getPreparedByName() {
		return preparedByName;
	}
	public void setPreparedByName(String v) {
		preparedByName = v == null ? "" : v;
	}

	public String getPreparedByPositionTitle() {
		return preparedByPositionTitle;
	}
	public void setPreparedByPositionTitle(String v) {
		preparedByPositionTitle = v == null ? "" : v;
	}

	public LocalDateTime getPreparedDateTime() {
		return preparedDateTime;
	}
	public void setPreparedDateTime(LocalDateTime v) {
		preparedDateTime = v;
	}

	public String getIapPage() {
		return iapPage;
	}
	public void setIapPage(String v) {
		iapPage = v == null ? "" : v;
	}
}
