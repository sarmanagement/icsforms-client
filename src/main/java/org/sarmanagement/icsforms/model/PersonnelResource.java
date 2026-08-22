package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Canonical personnel resource — ICS 219-5 (white T-card).
 *
 * <p>Stable identity fields (name, agency, state, phone) belong to this entity.
 * Operational-period-specific values such as radio channel and assigned status
 * are stored in {@link OperationalPeriodAssignment}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonnelResource extends IncidentResource {

    /**
     * Creates a new empty personnel resource with a generated {@code resourceId}.
     */
    public PersonnelResource() {
        super();
    }

    /**
     * Convenience factory — creates a personnel resource with the given display name.
     *
     * @param name the person's full name.
     * @return new personnel resource.
     */
    public static PersonnelResource of(String name) {
        PersonnelResource r = new PersonnelResource();
        r.setDisplayName(name == null ? "" : name);
        return r;
    }

    /** {@inheritDoc} */
    @Override
    public TCardType getTCardType() {
        return TCardType.PERSONNEL;
    }
}
