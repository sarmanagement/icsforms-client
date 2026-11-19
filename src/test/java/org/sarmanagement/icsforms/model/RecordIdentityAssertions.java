/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Reflection-based identity assertions shared by model tests. */
public final class RecordIdentityAssertions {
	/** Prevents construction of this test helper. */
	private RecordIdentityAssertions() {
	}

	/**
	 * Collects every identified record and asserts identity uniqueness by concrete type.
	 *
	 * @param root model graph to inspect
	 * @return identities grouped by concrete type
	 * @throws IllegalAccessException when a model field cannot be inspected
	 */
	public static Map<Class<?>, Set<String>> collect(Object root) throws IllegalAccessException {
		Map<Class<?>, Set<String>> identities = new HashMap<>();
		visit(root, "$", identities, new IdentityHashMap<>());
		return identities;
	}

	/**
	 * Walks model values and collections to collect identified records.
	 *
	 * @param value value to visit
	 * @param path diagnostic path
	 * @param identities identities already collected
	 * @param visited object instances already traversed
	 * @throws IllegalAccessException when a model field cannot be inspected
	 */
	private static void visit(Object value, String path, Map<Class<?>, Set<String>> identities,
			IdentityHashMap<Object, Boolean> visited) throws IllegalAccessException {
		if (value == null || visited.put(value, Boolean.TRUE) != null) {
			return;
		}
		if (value instanceof Identified identified) {
			assertNotNull(identified.getId(), path);
			assertFalse(identified.getId().isBlank(), path);
			assertTrue(identities.computeIfAbsent(value.getClass(), ignored -> new HashSet<>())
					.add(identified.getId()), path + " duplicate ID " + identified.getId());
		}
		if (value instanceof Collection<?> collection) {
			int index = 0;
			for (Object item : collection) {
				assertRecordCollectionElement(item, path + "[" + index + "]");
				visit(item, path + "[" + index++ + "]", identities, visited);
			}
		} else if (value instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				assertRecordCollectionElement(entry.getValue(), path + "." + entry.getKey());
				visit(entry.getValue(), path + "." + entry.getKey(), identities, visited);
			}
		} else if (value.getClass().isArray()) {
			for (int index = 0; index < Array.getLength(value); index++) {
				visit(Array.get(value, index), path + "[" + index + "]", identities, visited);
			}
		} else if (value.getClass().getPackageName().equals(AppData.class.getPackageName())) {
			for (Class<?> type = value.getClass(); type != null
					&& type.getPackageName().equals(AppData.class.getPackageName()); type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
						field.setAccessible(true);
						visit(field.get(value), path + "." + field.getName(), identities, visited);
					}
				}
			}
		}
	}

	/**
	 * Ensures every persisted model collection object has its own identity.
	 *
	 * @param value collection member
	 * @param path diagnostic path
	 */
	private static void assertRecordCollectionElement(Object value, String path) {
		if (value != null && value.getClass().getPackageName().equals(AppData.class.getPackageName())
				&& !(value instanceof Identified)
				&& !Set.of(PlanReviewAssertion.class, Ics215Form.CountPair.class, Ics215Form.ResourceAllocation.class,
						Ics215Form.ResourceLink.class)
						.contains(value.getClass())) {
			throw new AssertionError(path + " is a persisted model collection value without an identity");
		}
	}
}
