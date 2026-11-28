/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.model;

import java.util.List;
import java.util.Locale;

/**
 * Detects radio channels that two people may have given the same zone and channel number, and suggests the
 * next free number in the same pattern.
 */
public final class RadioChannelNumbers {
	private RadioChannelNumbers() {
	}

	/**
	 * Finds another channel that already uses a channel number within the same zone or group.
	 *
	 * @param channels channels already in the plan
	 * @param self channel being edited, which never conflicts with itself
	 * @param zone zone or group of the candidate
	 * @param number candidate channel number
	 * @return the conflicting channel, or null when the number is blank or free
	 */
	public static RadioChannel findConflict(List<RadioChannel> channels, RadioChannel self, String zone,
			String number) {
		String key = normalize(number);
		if (key.isEmpty() || channels == null) {
			return null;
		}
		for (RadioChannel channel : channels) {
			boolean same = channel == self
					|| self != null && !channel.getChannelId().isBlank() && channel.getChannelId().equals(self.getChannelId());
			if (!same && key.equals(normalize(channel.getChannelNumber()))
					&& normalize(zone).equals(normalize(channel.getZoneGroup()))) {
				return channel;
			}
		}
		return null;
	}

	/**
	 * Suggests the next free number that keeps the entered pattern, so CH 3 becomes CH 4 and 7 becomes 8.
	 *
	 * @param channels channels already in the plan
	 * @param self channel being edited
	 * @param zone zone or group of the candidate
	 * @param number conflicting number
	 * @return a number no other channel in the zone uses
	 */
	public static String nextFree(List<RadioChannel> channels, RadioChannel self, String zone, String number) {
		String base = number == null ? "" : number.trim();
		int start = base.length();
		while (start > 0 && Character.isDigit(base.charAt(start - 1))) {
			start--;
		}
		boolean numeric = start < base.length();
		String prefix = numeric ? base.substring(0, start) : base + "-";
		int next = numeric ? Integer.parseInt(base.substring(start)) + 1 : 2;
		String candidate = prefix + next;
		while (findConflict(channels, self, zone, candidate) != null) {
			candidate = prefix + (++next);
		}
		return candidate;
	}

	/**
	 * Lists the channel numbers used by more than one channel in a zone.
	 *
	 * @param channels channels in the plan
	 * @return one entry per repeated zone and number, for example {@code "7"} or {@code "Zone A / 7"}
	 */
	public static List<String> repeated(List<RadioChannel> channels) {
		java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		java.util.Map<String, String> labels = new java.util.HashMap<>();
		for (RadioChannel channel : channels) {
			String number = channel.getChannelNumber() == null ? "" : channel.getChannelNumber().trim();
			if (number.isEmpty()) {
				continue;
			}
			String key = normalize(channel.getZoneGroup()) + "\u0000" + normalize(number);
			counts.merge(key, 1, Integer::sum);
			String zone = channel.getZoneGroup() == null ? "" : channel.getZoneGroup().trim();
			labels.putIfAbsent(key, zone.isEmpty() ? number : zone + " / " + number);
		}
		return counts.entrySet().stream().filter(entry -> entry.getValue() > 1).map(entry -> labels.get(entry.getKey()))
				.toList();
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
