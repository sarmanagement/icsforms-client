/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.PlanReviewArea;

import java.io.IOException;
import java.util.Arrays;

/** Tracks detached display inputs without retaining mutable model references. */
final class UiRefreshState {
	private static final ObjectMapper COPIER = new ObjectMapper().registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
	private AppData incident;
	private byte[] content;

	/**
	 * Compares a role view's inputs, including shared context and content-bound review states.
	 *
	 * @param data active incident.
	 * @param inputs role-specific model facts and local display options.
	 * @return true when displayed information has changed.
	 */
	boolean roleChanged(AppData data, Object... inputs) {
		return changed(data, data.getIncidentContext(), data.getOrganizationalChart(), data.getTCards(),
				MainFrame.configuredZoneId(data).getId(), data.getPdfLayoutSettings(),
				data.getSafetyReview(), data.getHumanMedicalReview(), data.getCanineMedicalReview(),
				data.getCommunicationsReview(),
				Arrays.stream(PlanReviewArea.values()).map(data::getPlanReviewStatus).toList(), inputs);
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
}
