package org.sarmanagement.icsforms.validation;

/**
 * Validation issue surfaced to the Swing editor and PDF export workflows.
 */
public record ValidationMessage(String field, String message) {
}
