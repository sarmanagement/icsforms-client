/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Stores custom activity event types as application-wide local preferences.
 */
public final class ActivityEventTypeCatalog {
	private static final String PREFERENCE_KEY = "custom-activity-event-types";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ActivityEventTypeCatalog.class);

	/** Prevents utility construction. */
	private ActivityEventTypeCatalog() {
	}

	/**
	 * Loads valid custom event types saved for this application.
	 *
	 * @return detached custom event types.
	 */
	public static List<ActivityEventType> loadCustomTypes() {
		try {
			List<ActivityEventType> saved = MAPPER.readValue(PREFERENCES.get(PREFERENCE_KEY, "[]"),
					new TypeReference<>() {
					});
			return saved.stream().filter(ActivityEventTypeCatalog::isValidCustomType)
					.map(ActivityEventTypeCatalog::copy).toList();
		} catch (IOException | RuntimeException exception) {
			return List.of();
		}
	}

	/**
	 * Persists custom types without changing any incident's independent event entries.
	 *
	 * @param eventTypes candidate application-wide types.
	 * @throws IllegalArgumentException if a custom ID, label, or uniqueness rule is invalid.
	 */
	public static void saveCustomTypes(List<ActivityEventType> eventTypes) {
		List<ActivityEventType> custom = eventTypes == null ? List.of()
				: eventTypes.stream().filter(type -> type != null && !type.isBuiltIn()).map(ActivityEventTypeCatalog::copy)
						.toList();
		Map<String, ActivityEventType> unique = new LinkedHashMap<>();
		for (ActivityEventType type : custom) {
			if (!isValidCustomType(type)) {
				throw new IllegalArgumentException("Custom event types require a unique [A-Z_]+ ID and a label of one to five words.");
			}
			if (unique.putIfAbsent(type.getId(), type) != null) {
				throw new IllegalArgumentException("Custom event type IDs must be unique.");
			}
		}
		try {
			PREFERENCES.put(PREFERENCE_KEY, MAPPER.writeValueAsString(new ArrayList<>(unique.values())));
			PREFERENCES.flush();
		} catch (IOException | java.util.prefs.BackingStoreException exception) {
			throw new IllegalStateException("Unable to save application-wide event types.", exception);
		}
	}

	/**
	 * Selects unique custom types supported by the current application catalog.
	 * Legacy incident-only identifiers remain in their incident without being
	 * promoted into application-wide preferences.
	 *
	 * @param eventTypes candidate incident types.
	 * @return detached valid custom types in their existing order.
	 */
	public static List<ActivityEventType> supportedCustomTypes(List<ActivityEventType> eventTypes) {
		Map<String, ActivityEventType> supported = new LinkedHashMap<>();
		if (eventTypes != null) {
			for (ActivityEventType type : eventTypes) {
				if (isValidCustomType(type)) {
					supported.putIfAbsent(type.getId(), copy(type));
				}
			}
		}
		return new ArrayList<>(supported.values());
	}

	/**
	 * Combines defaults, legacy incident types, and application-wide custom types.
	 *
	 * @param incidentTypes types already stored in the incident, possibly null.
	 * @return ordered detached event types for the incident.
	 */
	public static List<ActivityEventType> forIncident(List<ActivityEventType> incidentTypes) {
		Map<String, ActivityEventType> types = new LinkedHashMap<>();
		for (ActivityEventType builtIn : ActivityEventType.defaultTypes()) {
			types.put(builtIn.getId(), copy(builtIn));
		}
		if (incidentTypes != null) {
			for (ActivityEventType type : incidentTypes) {
				if (type != null && !type.isBuiltIn() && !type.getId().isBlank()) {
					types.putIfAbsent(type.getId(), copy(type));
				}
			}
		}
		for (ActivityEventType type : loadCustomTypes()) {
			types.put(type.getId(), type);
		}
		return new ArrayList<>(types.values());
	}

	/**
	 * Checks the supported identifier and label shape for a new custom type.
	 *
	 * @param type candidate type.
	 * @return true when it is a valid custom type.
	 */
	private static boolean isValidCustomType(ActivityEventType type) {
		if (type == null || type.isBuiltIn() || type.getId() == null || !type.getId().matches("[A-Z_]+")
				|| type.getLabel() == null || type.getLabel().isBlank()) {
			return false;
		}
		if (ActivityEventType.defaultTypes().stream().anyMatch(builtIn -> builtIn.getId().equals(type.getId()))) {
			return false;
		}
		return type.getLabel().trim().split("\\s+").length <= 5;
	}

	/**
	 * Makes a detached copy of one event type.
	 *
	 * @param type source type.
	 * @return detached type copy.
	 */
	private static ActivityEventType copy(ActivityEventType type) {
		return new ActivityEventType(type.getId(), type.getLabel(), type.isBuiltIn(), type.isShowInQuickMenu());
	}
}
