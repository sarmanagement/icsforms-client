package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Canonical aircraft resource — ICS 219-4 Helicopter (blue) or 219-6 Fixed-Wing / UAS
 * (orange).
 *
 * <p>The {@link #getAircraftCategory()} discriminates between the two card colours.
 * The designated pilot or operator is tracked via {@link #getPilotResourceId()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AircraftResource extends IncidentResource {

    /**
     * Aircraft category, used to pick the correct 219 T-card colour.
     */
    public enum AircraftCategory {
        /** ICS 219-4 — Helicopter (blue). */
        HELICOPTER,
        /**
         * ICS 219-6 — Fixed-wing aircraft and unmanned aerial systems / drones (orange).
         */
        FIXED_WING
    }

    private AircraftCategory aircraftCategory = AircraftCategory.FIXED_WING;

    /**
     * {@code resourceId} of the {@link PersonnelResource} who is the primary pilot or
     * operator of this aircraft.  Blank when no pilot has been linked.
     */
    private String pilotResourceId = "";

    /**
     * Cached display name of the pilot, for rendering convenience.
     * Canonical value lives in the pilot's {@link PersonnelResource}.
     */
    private String pilotDisplayName = "";

    /**
     * Creates a new empty aircraft resource with a generated {@code resourceId}.
     */
    public AircraftResource() {
        super();
    }

    /**
     * Convenience factory.
     *
     * @param identifier      tail number, call sign, or UAS identifier.
     * @param aircraftCategory helicopter or fixed-wing/UAS.
     * @return new aircraft resource.
     */
    public static AircraftResource of(String identifier, AircraftCategory aircraftCategory) {
        AircraftResource r = new AircraftResource();
        r.setDisplayName(identifier == null ? "" : identifier);
        r.setAircraftCategory(aircraftCategory == null ? AircraftCategory.FIXED_WING : aircraftCategory);
        return r;
    }

    /**
     * Returns the aircraft category.
     *
     * @return aircraft category.
     */
    public AircraftCategory getAircraftCategory() {
        return aircraftCategory;
    }

    /**
     * Sets the aircraft category.
     *
     * @param aircraftCategory aircraft category; {@code null} treated as
     *                         {@link AircraftCategory#FIXED_WING}.
     */
    public void setAircraftCategory(AircraftCategory aircraftCategory) {
        this.aircraftCategory = aircraftCategory == null ? AircraftCategory.FIXED_WING : aircraftCategory;
    }

    /**
     * Returns the {@code resourceId} of the pilot {@link PersonnelResource}.
     *
     * @return pilot resource ID.
     */
    public String getPilotResourceId() {
        return pilotResourceId;
    }

    /**
     * Sets the {@code resourceId} of the pilot {@link PersonnelResource}.
     *
     * @param pilotResourceId pilot resource ID; {@code null} treated as blank.
     */
    public void setPilotResourceId(String pilotResourceId) {
        this.pilotResourceId = pilotResourceId == null ? "" : pilotResourceId;
    }

    /**
     * Returns the cached pilot display name for rendering convenience.
     *
     * @return pilot display name.
     */
    public String getPilotDisplayName() {
        return pilotDisplayName;
    }

    /**
     * Sets the cached pilot display name.
     *
     * @param pilotDisplayName pilot display name; {@code null} treated as blank.
     */
    public void setPilotDisplayName(String pilotDisplayName) {
        this.pilotDisplayName = pilotDisplayName == null ? "" : pilotDisplayName;
    }

    /** {@inheritDoc} */
    @Override
    public TCardType getTCardType() {
        if (aircraftCategory == AircraftCategory.HELICOPTER) {
            return TCardType.HELICOPTER;
        }
        return TCardType.FIXED_WING;
    }
}
