package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * ICS 219 Resource Status (T-Card) record.
 *
 * <p>The minimal implementation focuses on the 219-5 Personnel card.  All other card
 * types are scaffolded with a common set of fields and a {@link TCardType} discriminator.
 * Special mappings: canine+handler resources are stored as {@link TCardType#MISC_EQUIPMENT};
 * drone resources are stored as {@link TCardType#AIRCRAFT}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TCard {
    private TCardType cardType = TCardType.PERSONNEL;

    // 219-5 Personnel fields (used when cardType == PERSONNEL)
    /** Person's full name. */
    private String personName = "";
    /** Home agency or organization. */
    private String homeAgency = "";
    /** Home state two-letter abbreviation (e.g. "CA"). */
    private String homeState = "";
    /** Contact phone number. */
    private String phoneNumber = "";
    /** Assigned radio channel or talkgroup for the operational period. */
    private String radioChannel = "";
    /** Date and time of check-in at the incident. */
    private LocalDateTime checkInDateTime;

    // Common fields used across card types
    /** Resource or unit identifier. */
    private String resourceIdentifier = "";
    /** Current location (e.g. "ICP"). */
    private String location = "";
    /** Current status (Enroute, At Staging, Assigned, Out of Service). */
    private String status = "";
    /** Optional notes or description. */
    private String notes = "";

    /**
     * Opaque reference identifying the source object that generated this card.
     *
     * <p>Format conventions:
     * <ul>
     *   <li>{@code "org:<fieldKey>"} — org chart staff position, e.g. {@code "org:safetyOfficer"}</li>
     *   <li>{@code "sar:<assignmentId>:<resourceIndex>"} — SAR task resource,
     *       index 0 being the task leader</li>
     * </ul>
     * Blank when the card was created manually or imported from CSV.</p>
     */
    private String sourceRef = "";

    /**
     * Creates an empty T-card defaulting to a Personnel card.
     */
    public TCard() {
    }

    /**
     * Returns the card type.
     *
     * @return card type.
     */
    public TCardType getCardType() {
        return cardType;
    }

    /**
     * Sets the card type.
     *
     * @param cardType card type.
     */
    public void setCardType(TCardType cardType) {
        this.cardType = cardType == null ? TCardType.PERSONNEL : cardType;
    }

    /**
     * Returns the person's name (219-5).
     *
     * @return person name.
     */
    public String getPersonName() {
        return personName;
    }

    /**
     * Sets the person's name (219-5).
     *
     * @param personName person name.
     */
    public void setPersonName(String personName) {
        this.personName = personName == null ? "" : personName;
    }

    /**
     * Returns the home agency.
     *
     * @return home agency.
     */
    public String getHomeAgency() {
        return homeAgency;
    }

    /**
     * Sets the home agency.
     *
     * @param homeAgency home agency.
     */
    public void setHomeAgency(String homeAgency) {
        this.homeAgency = homeAgency == null ? "" : homeAgency;
    }

    /**
     * Returns the home state two-letter abbreviation.
     *
     * @return home state.
     */
    public String getHomeState() {
        return homeState;
    }

    /**
     * Sets the home state two-letter abbreviation.
     *
     * @param homeState home state (two-letter abbreviation).
     */
    public void setHomeState(String homeState) {
        this.homeState = homeState == null ? "" : homeState.toUpperCase().trim();
    }

    /**
     * Returns the contact phone number.
     *
     * @return phone number.
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the contact phone number.
     *
     * @param phoneNumber phone number.
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber;
    }

    /**
     * Returns the assigned radio channel or talkgroup for the operational period.
     *
     * @return radio channel.
     */
    public String getRadioChannel() {
        return radioChannel == null ? "" : radioChannel;
    }

    /**
     * Sets the assigned radio channel or talkgroup for the operational period.
     *
     * @param radioChannel radio channel or talkgroup.
     */
    public void setRadioChannel(String radioChannel) {
        this.radioChannel = radioChannel == null ? "" : radioChannel;
    }

    /**
     * Returns the check-in date and time at the incident.
     *
     * @return check-in date/time.
     */
    public LocalDateTime getCheckInDateTime() {
        return checkInDateTime;
    }

    /**
     * Sets the check-in date and time at the incident.
     *
     * @param checkInDateTime check-in date/time.
     */
    public void setCheckInDateTime(LocalDateTime checkInDateTime) {
        this.checkInDateTime = checkInDateTime;
    }

    /**
     * Returns the resource or unit identifier.
     *
     * @return resource identifier.
     */
    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    /**
     * Sets the resource or unit identifier.
     *
     * @param resourceIdentifier resource identifier.
     */
    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier == null ? "" : resourceIdentifier;
    }

    /**
     * Returns the current location (e.g. "ICP").
     *
     * @return location.
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the current location.
     *
     * @param location location.
     */
    public void setLocation(String location) {
        this.location = location == null ? "" : location;
    }

    /**
     * Returns the current status (Enroute, At Staging, Assigned, Out of Service).
     *
     * @return status.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the current status.
     *
     * @param status status.
     */
    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    /**
     * Returns optional notes.
     *
     * @return notes.
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Sets optional notes.
     *
     * @param notes notes.
     */
    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    /**
     * Returns the source reference identifying the incident object (org chart position or
     * SAR task resource) that generated this T-card, or blank for manually created cards.
     *
     * @return source reference string, never {@code null}.
     */
    public String getSourceRef() {
        return sourceRef == null ? "" : sourceRef;
    }

    /**
     * Sets the source reference.
     *
     * @param sourceRef source reference; {@code null} is treated as blank.
     */
    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef == null ? "" : sourceRef;
    }

    /**
     * Returns a display label for this card.
     *
     * @return display label.
     */
    public String getDisplayLabel() {
        String name = personName.isBlank() ? resourceIdentifier : personName;
        return name.isBlank() ? cardType.getLabel() : name;
    }
}
