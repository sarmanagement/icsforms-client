package org.sarmanagement.icsforms.validation;

/**
 * Validation issue surfaced to the Swing editor and PDF export workflows.
 *
 * @param field
 *            field identifier associated with the validation issue.
 * @param message
 *            user-facing message describing the validation issue.
 */
public record ValidationMessage(String field, String message) {
}
