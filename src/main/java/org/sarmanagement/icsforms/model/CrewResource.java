package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Canonical crew or team resource — ICS 219-2 (green T-card).
 *
 * <p>A crew or team is a collective resource: multiple personnel deploying together
 * under a single unit designation.  The total headcount is tracked here; individual
 * team members may also have their own {@link PersonnelResource} records.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrewResource extends IncidentResource {

    /** Number of personnel in the crew or team. */
    private int numberOfPersons;

    /**
     * Creates a new empty crew resource with a generated {@code resourceId}.
     */
    public CrewResource() {
        super();
    }

    /**
     * Convenience factory.
     *
     * @param teamDesignator team identifier or name (e.g. "Team Alpha").
     * @param numberOfPersons number of personnel.
     * @return new crew resource.
     */
    public static CrewResource of(String teamDesignator, int numberOfPersons) {
        CrewResource r = new CrewResource();
        r.setDisplayName(teamDesignator == null ? "" : teamDesignator);
        r.setNumberOfPersons(numberOfPersons);
        return r;
    }

    /**
     * Returns the number of personnel in this crew or team.
     *
     * @return number of persons.
     */
    public int getNumberOfPersons() {
        return numberOfPersons;
    }

    /**
     * Sets the number of personnel in this crew or team.
     *
     * @param numberOfPersons number of persons (must be &ge; 0).
     */
    public void setNumberOfPersons(int numberOfPersons) {
        this.numberOfPersons = Math.max(0, numberOfPersons);
    }

    /** {@inheritDoc} */
    @Override
    public TCardType getTCardType() {
        return TCardType.CREW;
    }
}
