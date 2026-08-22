package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ICS 219 Resource Status (T-Card) record.
 *
 * <p>The minimal implementation focuses on the 219-5 Personnel card.  All other card
 * types are scaffolded with a common set of fields and a {@link TCardType} discriminator.
 * Special mappings: canine resources are stored as {@link TCardType#EQUIPMENT} (219-7,
 * yellow — canines are treated as working assets/equipment in ICS);
 * drone/fixed-wing resources are stored as {@link TCardType#FIXED_WING} (219-6).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TCard {

    /**
     * Stable UUID assigned once at creation and never changed.
     * Used by {@link SarTaskResource#getResourceId()} to reference this card without
     * relying on mutable display-name strings.
     */
    private String resourceId = UUID.randomUUID().toString();

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
     * For canine (and other resource) T-cards: the name of the handler or operator
     * who manages this resource.  When set, the rack view groups this card below the
     * handler's personnel card.  A handler may be linked to more than one canine card.
     * Blank when not applicable.
     */
    private String handlerName = "";

    /**
     * Number of persons represented by this T-card.  Meaningful for CREW cards
     * (a crew of N people) and PERSONNEL cards (always 1).  Zero means unspecified.
     */
    private int numberOfPersons = 0;

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
     * Returns the stable resource identifier (UUID) for this card.
     * Used by {@link SarTaskResource} to reference this card without relying on names.
     *
     * @return resource UUID, never {@code null}.
     */
    public String getResourceId() {
        if (resourceId == null || resourceId.isBlank()) {
            resourceId = UUID.randomUUID().toString();
        }
        return resourceId;
    }

    /**
     * Sets the stable resource identifier.  Should only be called during deserialization.
     *
     * @param resourceId resource UUID string; blank/null generates a new UUID.
     */
    public void setResourceId(String resourceId) {
        this.resourceId = (resourceId == null || resourceId.isBlank())
                ? UUID.randomUUID().toString() : resourceId;
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
     * Returns the name of the handler or operator who manages this resource.
     *
     * <p>Used to group canine (and other resource) cards below their handler's
     * personnel card in the rack view.  Blank when not applicable.</p>
     *
     * @return handler name, never {@code null}.
     */
    public String getHandlerName() {
        return handlerName == null ? "" : handlerName;
    }

    /**
     * Sets the name of the handler or operator who manages this resource.
     *
     * @param handlerName handler name; {@code null} is treated as blank.
     */
    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName == null ? "" : handlerName;
    }

    /**
     * Returns the stored number of persons for this card.  Zero means unspecified.
     * For PERSONNEL cards the effective count is always 1; use {@link #personCount()}
     * when computing headcounts.
     *
     * @return stored number of persons (&ge; 0).
     */
    public int getNumberOfPersons() {
        return numberOfPersons;
    }

    /**
     * Sets the number of persons for this card.  Values below zero are clamped to zero.
     *
     * @param numberOfPersons number of persons (&ge; 0).
     */
    public void setNumberOfPersons(int numberOfPersons) {
        this.numberOfPersons = Math.max(0, numberOfPersons);
    }

    /**
     * Returns the number of people contributed by this card to an assignment headcount.
     *
     * <ul>
     *   <li>PERSONNEL — always 1.</li>
     *   <li>CREW — {@code numberOfPersons} when set (&gt; 0), otherwise 1.</li>
     *   <li>All other types (equipment, canine, aircraft, …) — 0.</li>
     * </ul>
     *
     * @return person count contribution.
     */
    public int personCount() {
        if (cardType == TCardType.PERSONNEL) {
            return 1;
        }
        if (cardType == TCardType.CREW) {
            return numberOfPersons > 0 ? numberOfPersons : 1;
        }
        return 0;
    }

    /**
     * Returns a display label for this card.
     *
     * <p>For PERSONNEL cards, the person's name ({@code personName}) is used.
     * For all other card types (equipment, canines, aircraft, etc.),
     * {@code resourceIdentifier} is the primary display name — that is the
     * generalised identifier: dog call sign, apparatus name, tail number, etc.</p>
     *
     * @return display label.
     */
    public String getDisplayLabel() {
        if (cardType == TCardType.PERSONNEL) {
            String name = personName.isBlank() ? resourceIdentifier : personName;
            return name.isBlank() ? cardType.getLabel() : name;
        } else {
            String name = resourceIdentifier.isBlank() ? personName : resourceIdentifier;
            return name.isBlank() ? cardType.getLabel() : name;
        }
    }
}
