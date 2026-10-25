/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.comms;

import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builds transactional channel import previews without mutating either source. */
public class ChannelImportService {
	/** The explicit channel operation; names are never used as identities. */
	public enum Mode {
		APPEND, REPLACE, UPDATE_BY_ID
	}

	/**
	 * Applies a selection atomically to a detached plan; form metadata stays local.
	 * Appending assigns fresh IDs to colliding rows. Updating requires each selected
	 * ID to exist exactly once in the destination; unknown IDs reject the transaction.
	 * @param target destination plan.
	 * @param imported parsed source plan.
	 * @param selectedRows zero-based selected channel indexes, in desired order.
	 * @param mode operation to perform.
	 * @return detached resulting plan.
	 */
	public Ics205Form merge(Ics205Form target, Ics205Form imported, List<Integer> selectedRows, Mode mode) {
		if (target == null || imported == null || selectedRows == null || mode == null) {
			throw new IllegalArgumentException("Plans, selection, and import mode are required.");
		}
		Map<String, Integer> existing = index(target.getChannels());
		index(imported.getChannels());
		Ics205Form result = target.deepCopy();
		Ics205Form source = imported.deepCopy();
		Set<Integer> selection = new HashSet<>();
		List<RadioChannel> chosen = new ArrayList<>();
		for (Integer row : selectedRows) {
			if (row == null || row < 0 || row >= source.getChannels().size() || !selection.add(row)) {
				throw new IllegalArgumentException("Invalid or duplicate selected channel row: " + row);
			}
			chosen.add(source.getChannels().get(row));
		}
		if (mode == Mode.REPLACE) {
			result.setChannels(chosen);
			return result;
		}
		List<RadioChannel> channels = new ArrayList<>(result.getChannels());
		Set<String> used = new HashSet<>(existing.keySet());
		for (RadioChannel channel : chosen) {
			if (mode == Mode.UPDATE_BY_ID) {
				Integer position = existing.get(channel.getChannelId());
				if (position == null) {
					throw new IllegalArgumentException("Update requires an existing explicit channel ID: "
							+ channel.getChannelId());
				}
				channels.set(position, channel);
			} else {
				while (!used.add(channel.getChannelId())) {
					channel.setChannelId(UUID.randomUUID().toString());
				}
				channels.add(channel);
			}
		}
		result.setChannels(channels);
		return result;
	}

	/**
	 * Validates channel identity before any operation is applied.
	 * @param channels channel rows.
	 * @return ID to position mapping.
	 */
	private static Map<String, Integer> index(List<RadioChannel> channels) {
		Map<String, Integer> result = new HashMap<>();
		for (int i = 0; i < channels.size(); i++) {
			RadioChannel channel = channels.get(i);
			if (channel == null || channel.getChannelId() == null
					|| result.put(channel.getChannelId(), i) != null) {
				throw new IllegalArgumentException("Channels require unique explicit IDs.");
			}
			UUID.fromString(channel.getChannelId());
		}
		return result;
	}
}
