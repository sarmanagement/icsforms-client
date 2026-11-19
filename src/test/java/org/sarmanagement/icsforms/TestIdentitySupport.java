/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import org.sarmanagement.icsforms.model.Identified;

/** Provides explicit identities for test fixtures without weakening production setters. */
public final class TestIdentitySupport {
	private TestIdentitySupport() {
	}

	/**
	 * Assigns a fixture identity directly to its canonical identity field.
	 *
	 * @param record fixture record
	 * @param id identity used by the fixture
	 */
	public static void assign(Identified record, String id) {
		for (Class<?> type = record.getClass(); type != null; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (field.isAnnotationPresent(JsonProperty.class)
						&& "id".equals(field.getAnnotation(JsonProperty.class).value())) {
					try {
						field.setAccessible(true);
						field.set(record, id);
						return;
					} catch (IllegalAccessException exception) {
						throw new IllegalStateException("Could not assign fixture identity.", exception);
					}
				}
			}
		}
		throw new IllegalArgumentException("Fixture record has no canonical identity field.");
	}
}
