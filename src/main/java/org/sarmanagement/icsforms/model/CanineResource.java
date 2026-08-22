package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Canonical canine working-asset resource — ICS 219-7 (yellow T-card).
 *
 * <p>In ICS, search canines are treated as working assets/equipment, not as personnel.
 * This class carries the canine's own identity fields plus a link to the
 * {@link PersonnelResource} who is the handler or operator.  The handler's name is
 * stored here for display convenience; the authoritative handler record is the
 * {@link PersonnelResource} identified by {@link #getHandlerResourceId()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanineResource extends IncidentResource {

    /**
     * {@code resourceId} of the {@link PersonnelResource} that is the primary handler
     * or operator of this canine.  Blank when no handler has been linked yet.
     */
    private String handlerResourceId = "";

    /**
     * Cached display name of the handler, kept in sync with the handler's
     * {@link PersonnelResource#getDisplayName()} for convenience in rendering.
     * This is display-only; the canonical value is in the handler's PersonnelResource.
     */
    private String handlerDisplayName = "";

    /** Breed or type of canine (e.g. "German Shepherd", "Labrador Retriever"). */
    private String breed = "";

    /**
     * Imprint type for scent work (e.g. "Human Remains", "Live Human", "Trailing").
     * Corresponds to the {@code canineImprint} field on SAR task assignments.
     */
    private String imprint = "";

    /**
     * Creates a new empty canine resource with a generated {@code resourceId}.
     */
    public CanineResource() {
        super();
    }

    /**
     * Convenience factory — creates a canine resource with the given call-sign / name.
     *
     * @param canineName the canine's name or call-sign (e.g. "K9-Ranger").
     * @return new canine resource.
     */
    public static CanineResource of(String canineName) {
        CanineResource r = new CanineResource();
        r.setDisplayName(canineName == null ? "" : canineName);
        return r;
    }

    /**
     * Returns the {@code resourceId} of the handler {@link PersonnelResource}, or blank
     * if none has been linked.
     *
     * @return handler resource ID.
     */
    public String getHandlerResourceId() {
        return handlerResourceId;
    }

    /**
     * Sets the {@code resourceId} of the handler {@link PersonnelResource}.
     *
     * @param handlerResourceId handler resource ID; {@code null} treated as blank.
     */
    public void setHandlerResourceId(String handlerResourceId) {
        this.handlerResourceId = handlerResourceId == null ? "" : handlerResourceId;
    }

    /**
     * Returns the cached display name of the handler for rendering convenience.
     *
     * @return handler display name.
     */
    public String getHandlerDisplayName() {
        return handlerDisplayName;
    }

    /**
     * Sets the cached handler display name.  This should be updated whenever the
     * handler's {@link PersonnelResource#getDisplayName()} changes.
     *
     * @param handlerDisplayName handler display name; {@code null} treated as blank.
     */
    public void setHandlerDisplayName(String handlerDisplayName) {
        this.handlerDisplayName = handlerDisplayName == null ? "" : handlerDisplayName;
    }

    /**
     * Returns the breed or type of canine.
     *
     * @return breed.
     */
    public String getBreed() {
        return breed;
    }

    /**
     * Sets the breed or type of canine.
     *
     * @param breed breed; {@code null} treated as blank.
     */
    public void setBreed(String breed) {
        this.breed = breed == null ? "" : breed;
    }

    /**
     * Returns the imprint type for scent work.
     *
     * @return imprint type.
     */
    public String getImprint() {
        return imprint;
    }

    /**
     * Sets the imprint type for scent work.
     *
     * @param imprint imprint type; {@code null} treated as blank.
     */
    public void setImprint(String imprint) {
        this.imprint = imprint == null ? "" : imprint;
    }

    /** {@inheritDoc} */
    @Override
    public TCardType getTCardType() {
        return TCardType.EQUIPMENT;
    }
}
