package org.sarmanagement.icsforms.model;

/**
 * Identifies the current incident phase and drives which forms are active vs. read-only.
 *
 * <ul>
 *   <li>{@link #PRE_OP} – pre-operational planning: the team is preparing for a potential or
 *       anticipated incident.  No ICS 201 has been generated yet; resources may be placed in an
 *       "Ordered" status on the T-card rack.</li>
 *   <li>{@link #INITIAL_RESPONSE} – initial incident response: an ICS 201 Incident Briefing is
 *       the primary capture tool.  The 201 is fully editable; ICS 202 and ICS 204 are also
 *       available for concurrent planning.  Resources may still be in an "Ordered" status.</li>
 *   <li>{@link #DURING_OP} – subsequent full operational period: the ICS 201 from the initial
 *       response is locked (read-only) and attached to the IAP as a historical record.  ICS 202
 *       and ICS 204 are the primary operational forms.  The "Ordered" rack column is hidden
 *       when empty.</li>
 * </ul>
 */
public enum IapPhase {
    /** Pre-operational planning phase — before the incident response begins. */
    PRE_OP,
    /**
     * Initial incident response phase — ICS 201 is the primary capture tool and is fully
     * editable.
     */
    INITIAL_RESPONSE,
    /**
     * Subsequent full operational period — ICS 201 is locked as a historical record; ICS 202
     * and ICS 204 are the primary forms.
     */
    DURING_OP
}
