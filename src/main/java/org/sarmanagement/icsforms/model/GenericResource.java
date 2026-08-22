package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Catch-all canonical resource — ICS 219-10 Generic (light purple T-card).
 *
 * <p>Used for resources that do not fit any of the standard ICS 219-1 through 219-8
 * categories.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenericResource extends IncidentResource {

    /**
     * Creates a new empty generic resource with a generated {@code resourceId}.
     */
    public GenericResource() {
        super();
    }

    /**
     * Convenience factory.
     *
     * @param identifier resource identifier or description.
     * @return new generic resource.
     */
    public static GenericResource of(String identifier) {
        GenericResource r = new GenericResource();
        r.setDisplayName(identifier == null ? "" : identifier);
        return r;
    }

    /** {@inheritDoc} */
    @Override
    public TCardType getTCardType() {
        return TCardType.GENERIC;
    }
}
