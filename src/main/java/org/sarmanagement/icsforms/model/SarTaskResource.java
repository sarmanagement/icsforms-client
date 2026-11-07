/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.util.UUID;

/**
 * Resource entry printed on the SAR task assignment form.
 */
public class SarTaskResource {
	private String id = UUID.randomUUID().toString();
	private String function = "";
	private String icsPosition = "";
	private String homeAgency = "";
	private String name = "";
	private TCardType cardType = TCardType.PERSONNEL;

	/**
	 * Stable UUID referencing the {@link TCard#getResourceId()} of the linked
	 * T-card. When set, lookups in {@code syncTCards} use this ID rather than the
	 * display name, preventing type-corruption when a resource name coincidentally
	 * matches another card. Blank for legacy entries created before UUID linking
	 * was introduced.
	 */
	private String resourceId = "";

	/**
	 * Returns this assignment resource row's identity, distinct from its optional
	 * link to a T-card.
	 *
	 * @return row UUID, never null or blank
	 */
	public String getId() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
		return id;
	}

	/**
	 * Sets this assignment resource row's identity.
	 *
	 * @param id row UUID; null or blank generates a UUID
	 */
	public void setId(String id) {
		this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
	}

	/** @return resource function or role. */
	public String getFunction() {
		return function;
	}

	/**
	 * @param function
	 *            resource function or role.
	 */
	public void setFunction(String function) {
		this.function = function == null ? "" : function;
	}

	/** @return ICS position or role. */
	public String getIcsPosition() {
		return icsPosition.isBlank() ? function : icsPosition;
	}

	/**
	 * @param icsPosition
	 *            ICS position or role.
	 */
	public void setIcsPosition(String icsPosition) {
		this.icsPosition = icsPosition == null ? "" : icsPosition;
	}

	/** @return home agency. */
	public String getHomeAgency() {
		return homeAgency;
	}

	/**
	 * @param homeAgency
	 *            home agency.
	 */
	public void setHomeAgency(String homeAgency) {
		this.homeAgency = homeAgency == null ? "" : homeAgency;
	}

	/** @return resource name. */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            resource name.
	 */
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	/**
	 * @return T-card type for this resource ({@code null} treated as PERSONNEL).
	 */
	public TCardType getCardType() {
		return cardType;
	}

	/**
	 * @param cardType
	 *            T-card type for this resource.
	 */
	public void setCardType(TCardType cardType) {
		this.cardType = cardType;
	}

	/**
	 * Returns the stable UUID of the linked {@link TCard}, or blank for legacy
	 * entries.
	 *
	 * @return resource UUID, never {@code null}.
	 */
	public String getResourceId() {
		return resourceId == null ? "" : resourceId;
	}

	/**
	 * Sets the stable UUID referencing the linked {@link TCard}.
	 *
	 * @param resourceId
	 *            resource UUID; {@code null} treated as blank.
	 */
	public void setResourceId(String resourceId) {
		this.resourceId = resourceId == null ? "" : resourceId;
	}
}
