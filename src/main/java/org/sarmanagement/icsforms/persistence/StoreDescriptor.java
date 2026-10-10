package org.sarmanagement.icsforms.persistence;

/**
 * Human-readable store identity.
 *
 * @param type
 *            store type.
 * @param location
 *            store location.
 */
public record StoreDescriptor(String type, String location) {
}
