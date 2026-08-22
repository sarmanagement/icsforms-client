package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical incident resource entity with a stable identity.
 *
 * <p>Every real-world resource deployed to or requested for an incident is represented
 * by exactly one {@code IncidentResource} instance.  Forms and operational-period
 * assignments reference resources by their {@link #getResourceId() resourceId} rather
 * than by copying field values.  This ensures that edits to a resource propagate to
 * every live reference automatically, while finalized historical documents preserve
 * their own snapshots.</p>
 *
 * <p>The concrete subclasses correspond to the ICS 219 T-card categories:</p>
 * <ul>
 *   <li>{@link PersonnelResource} — 219-5 (white)</li>
 *   <li>{@link CanineResource}    — 219-7 (yellow), canine working asset</li>
 *   <li>{@link CrewResource}      — 219-2 (green), crew or team</li>
 *   <li>{@link EquipmentResource} — 219-3 engine / 219-7 equipment / 219-8 misc</li>
 *   <li>{@link AircraftResource}  — 219-4 helicopter / 219-6 fixed-wing or UAS</li>
 *   <li>{@link GenericResource}   — 219-10 generic</li>
 * </ul>
 *
 * <p>Stable fields (those that describe the resource itself, not its operational-period
 * role) are stored here.  Operational-period-specific values (radio channel, check-in
 * time, assigned status) live in {@link OperationalPeriodAssignment}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "resourceType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PersonnelResource.class,  name = "PERSONNEL"),
    @JsonSubTypes.Type(value = CanineResource.class,     name = "CANINE"),
    @JsonSubTypes.Type(value = CrewResource.class,       name = "CREW"),
    @JsonSubTypes.Type(value = EquipmentResource.class,  name = "EQUIPMENT"),
    @JsonSubTypes.Type(value = AircraftResource.class,   name = "AIRCRAFT"),
    @JsonSubTypes.Type(value = GenericResource.class,    name = "GENERIC"),
})
public abstract class IncidentResource {

    /** Stable UUID assigned once at creation and never changed. */
    private String resourceId = UUID.randomUUID().toString();

    /**
     * Human-readable display name.
     *
     * <p>For personnel this is the person's full name.  For canines this is the canine's
     * name/call-sign (not the handler's name).  For equipment/aircraft this is the unit
     * identifier or description.</p>
     */
    private String displayName = "";

    /** Home agency or organisation. */
    private String homeAgency = "";

    /** Home state two-letter abbreviation (e.g. {@code "CA"}). */
    private String homeState = "";

    /** Contact phone number. Stable across operational periods. */
    private String phoneNumber = "";

    /**
     * Resource or unit identifier (e.g. crew designator, equipment ID, call sign).
     * Stable across operational periods.
     */
    private String resourceIdentifier = "";

    /** Optional free-text notes about the resource itself (not assignment-specific). */
    private String notes = "";

    /** Instant at which this resource record was first created. */
    private Instant createdAt = Instant.now();

    /** Instant of the most recent edit to this resource record. */
    private Instant updatedAt = Instant.now();

    /** Monotonically increasing edit counter used for optimistic concurrency. */
    private long revision = 1;

    /**
     * Creates a new resource entity with a freshly generated {@link #getResourceId() resourceId}.
     */
    protected IncidentResource() {
    }

    // -----------------------------------------------------------------------
    // Identity
    // -----------------------------------------------------------------------

    /**
     * Returns the stable resource identifier (UUID).
     *
     * @return resource UUID, never {@code null}.
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Sets the stable resource identifier.  Should only be called during deserialization
     * or migration; do not reassign once the resource is referenced from other objects.
     *
     * @param resourceId stable UUID string.
     */
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId == null || resourceId.isBlank()
                ? UUID.randomUUID().toString() : resourceId;
    }

    // -----------------------------------------------------------------------
    // Stable display / identity fields
    // -----------------------------------------------------------------------

    /**
     * Returns the human-readable display name of this resource.
     *
     * @return display name, never {@code null}.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the human-readable display name.
     *
     * @param displayName display name; {@code null} treated as blank.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
    }

    /**
     * Returns the home agency or organisation.
     *
     * @return home agency.
     */
    public String getHomeAgency() {
        return homeAgency;
    }

    /**
     * Sets the home agency.
     *
     * @param homeAgency home agency; {@code null} treated as blank.
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
     * Sets the home state abbreviation; value is upper-cased and trimmed.
     *
     * @param homeState home state abbreviation.
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
     * @param phoneNumber phone number; {@code null} treated as blank.
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber;
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
     * @param resourceIdentifier resource identifier; {@code null} treated as blank.
     */
    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier == null ? "" : resourceIdentifier;
    }

    /**
     * Returns free-text notes about this resource.
     *
     * @return notes.
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Sets free-text notes.
     *
     * @param notes notes; {@code null} treated as blank.
     */
    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    // -----------------------------------------------------------------------
    // Audit / versioning
    // -----------------------------------------------------------------------

    /**
     * Returns the instant at which this resource record was created.
     *
     * @return creation instant.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation instant (used during deserialization).
     *
     * @param createdAt creation instant.
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the instant of the most recent update to this resource.
     *
     * @return last-updated instant.
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last-updated instant (used during deserialization or explicit updates).
     *
     * @param updatedAt last-updated instant.
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the monotonically increasing edit counter.
     *
     * @return revision number.
     */
    public long getRevision() {
        return revision;
    }

    /**
     * Sets the revision counter.
     *
     * @param revision revision counter value.
     */
    public void setRevision(long revision) {
        this.revision = revision;
    }

    /**
     * Increments the {@link #getRevision() revision} counter and updates
     * {@link #getUpdatedAt() updatedAt} to the current instant.
     */
    public void bumpRevision() {
        this.revision++;
        this.updatedAt = Instant.now();
    }

    // -----------------------------------------------------------------------
    // Abstract helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the {@link TCardType} that corresponds to this resource subtype,
     * for use when bridging to legacy T-card rendering code.
     *
     * @return matching {@link TCardType}.
     */
    public abstract TCardType getTCardType();
}
