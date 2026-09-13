package org.sarmanagement.icsforms.model;

/**
 * Canonical personnel-directory row shared by the T-card Directory View and ICS 205A export.
 *
 * @param card underlying personnel T-card
 * @param name person name
 * @param assignedPosition incident assigned position / role
 * @param state home state
 * @param unit home agency or unit
 * @param assignment current assignment label
 * @param status current status
 * @param contactMethods combined contact methods
 */
public record ResourceDirectoryEntry(TCard card,
                                     String name,
                                     String assignedPosition,
                                     String state,
                                     String unit,
                                     String assignment,
                                     String status,
                                     String contactMethods) {
}
