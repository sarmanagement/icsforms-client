/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.util.Arrays;

/** Tracks detached display inputs without retaining mutable model references. */
final class UiRefreshState {
	private static final ObjectMapper COPIER = new ObjectMapper().registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
	private AppData incident;
	private byte[] content;
	private AppData pendingIncident;
	private Object[] pendingInputs;

	/**
	 * Checks inputs without accepting a baseline before the view has updated successfully.
	 *
	 * @param data active incident.
	 * @param inputs view-specific model and local display inputs.
	 * @return true when the view needs an update.
	 * @throws IllegalStateException when the display inputs cannot be serialized.
	 */
	boolean needsRefresh(AppData data, Object... inputs) {
		try {
			pendingIncident = data;
			pendingInputs = inputs;
			return incident != data || !Arrays.equals(content, serialize(inputs));
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to compare display inputs", exception);
		}
	}

	/**
	 * Accepts the pending inputs only after the synchronous presentation update succeeds.
	 *
	 * @throws IllegalStateException when the displayed inputs cannot be serialized.
	 */
	void complete() {
		try {
			byte[] displayed = serialize(pendingInputs);
			incident = pendingIncident;
			content = displayed;
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to compare display inputs", exception);
		}
	}

	/**
	 * Refreshes changed display inputs and records a baseline only after successful presentation.
	 *
	 * @param data active incident.
	 * @param refresh synchronous presentation update.
	 * @param inputs detached projection inputs.
	 * @return true when the presentation was updated.
	 * @throws IllegalStateException when display inputs cannot be serialized.
	 */
	boolean refresh(AppData data, Runnable refresh, Object... inputs) {
		try {
			byte[] next = serialize(inputs);
			if (incident == data && Arrays.equals(content, next)) {
				return false;
			}
			refresh.run();
			incident = data;
			content = serialize(inputs);
			return true;
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to compare display inputs", exception);
		}
	}

	/**
	 * Checks a role view's inputs with its shared incident banner and display timezone.
	 *
	 * @param data active incident.
	 * @param inputs role-specific model facts and local display options.
	 * @return true when displayed information has changed.
	 */
	boolean roleChanged(AppData data, Object... inputs) {
		return needsRefresh(data, data.getIncidentContext(), MainFrame.configuredZoneId(data).getId(), inputs);
	}

	/**
	 * Records display inputs and reports whether they differ from the previous refresh.
	 *
	 * @param data incident identity, which invalidates even identical replacement content.
	 * @param inputs model facts and local display options used by the view.
	 * @return true when the view needs refreshing.
	 * @throws IllegalStateException when display inputs cannot be serialized.
	 */
	boolean changed(AppData data, Object... inputs) {
		try {
			byte[] next = serialize(inputs);
			boolean changed = incident != data || !Arrays.equals(content, next);
			incident = data;
			content = next;
			return changed;
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to compare display inputs", exception);
		}
	}

	/**
	 * Serializes stored fields without invoking model getters that may normalize values.
	 *
	 * @param value model state to compare.
	 * @return detached serialized fields.
	 * @throws IOException when the state cannot be serialized.
	 */
	static byte[] serialize(Object value) throws IOException {
		return COPIER.writeValueAsBytes(value);
	}

	/**
	 * Restores a detached incident using the same field names as the snapshot writer.
	 *
	 * @param content serialized incident fields.
	 * @return detached incident retaining all snapshot fields.
	 * @throws IOException when the snapshot cannot be read.
	 */
	static AppData readIncident(byte[] content) throws IOException {
		return COPIER.readValue(content, AppData.class);
	}
}
