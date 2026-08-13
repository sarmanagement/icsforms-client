package org.sarmanagement.icsforms.model;

/**
 * Indicates whether the IAP is being prepared before the operational period starts
 * or is being updated during an active operational period.
 *
 * <p>In {@link #PRE_OP} mode resources may be placed in an "Ordered" status on the T-card
 * rack; an "Ordered" rack column is shown when resources carry that status.  No automatic
 * check-in time is assigned when a resource card is created.
 * In {@link #DURING_OP} mode resources are checked in as they arrive; the "Ordered" rack
 * column is hidden when empty.</p>
 */
public enum IapPhase {
    /** IAP preparation phase — before the operational period begins. */
    PRE_OP,
    /** Active operational period — resources are checking in. */
    DURING_OP
}
