/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.safety;

import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.Ics215aForm;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates editable ICS 208 drafts from ICS 215A rows without inventing safety guidance.
 */
public class SafetyMessageComposer {
	/**
	 * Updates a message draft, adding new analysis rows and flagging stale blocks.
	 * Existing officer-edited content is never overwritten.
	 *
	 * @param analysis source ICS 215A.
	 * @param message destination ICS 208.
	 */
	public void updateDraft(Ics215aForm analysis, Ics208Form message) {
		Map<String, SafetyAnalysisEntry> sources = analysis.getEntries().stream().filter(entry -> entry != null)
				.collect(Collectors.toMap(SafetyAnalysisEntry::getEntryId, Function.identity(), (first, second) -> first));
		LinkedHashSet<String> requiredEquipment = explicitEquipment(analysis.getEntries());
		message.setGeneralRequirements(mergeRequirements(message.getGeneralRequirements(), requiredEquipment));

		List<String> coveredIds = new ArrayList<>();
		for (SafetyMessageBlock block : message.getHazardBlocks()) {
			boolean current = false;
			for (String sourceId : block.getSourceEntryIds()) {
				SafetyAnalysisEntry source = sources.get(sourceId);
				if (source == null) {
					block.setNeedsReview(true);
					continue;
				}
				coveredIds.add(sourceId);
				current = true;
				if (!source.getHazardsRisks().equals(block.getHazard())
						|| !source.getMitigations().equals(block.getPrecautions())
						|| !source.getIncidentArea().equals(block.getIncidentArea())) {
					block.setNeedsReview(true);
				}
			}
			if (!current) {
				block.setNeedsReview(true);
			}
		}
		for (SafetyAnalysisEntry entry : analysis.getEntries()) {
			if (entry != null && !coveredIds.contains(entry.getEntryId())) {
				message.getHazardBlocks().add(blockFor(entry));
			}
		}
	}

	/**
	 * Creates one editable message block per analysis row.
	 *
	 * @param entry source analysis row.
	 * @return new block linked to the row.
	 */
	private static SafetyMessageBlock blockFor(SafetyAnalysisEntry entry) {
		SafetyMessageBlock block = new SafetyMessageBlock();
		block.setHazard(entry.getHazardsRisks());
		block.setIncidentArea(entry.getIncidentArea());
		block.setPrecautions(entry.getMitigations());
		block.setSourceEntryIds(List.of(entry.getEntryId()));
		return block;
	}

	/**
	 * Collects only equipment explicitly classified in equipment/PPE fields.
	 *
	 * @param entries analysis rows.
	 * @return insertion-ordered exact equipment labels.
	 */
	private static LinkedHashSet<String> explicitEquipment(List<SafetyAnalysisEntry> entries) {
		LinkedHashSet<String> equipment = new LinkedHashSet<>();
		for (SafetyAnalysisEntry entry : entries) {
			if (entry != null) {
				for (String item : entry.getEquipmentPpe().split("\\R")) {
					if (!item.isBlank()) {
						equipment.add(item.trim());
					}
				}
			}
		}
		return equipment;
	}

	/**
	 * Adds newly identified equipment to general requirements without duplicates.
	 *
	 * @param existing current general requirements.
	 * @param equipment explicit equipment items.
	 * @return combined text.
	 */
	private static String mergeRequirements(String existing, LinkedHashSet<String> equipment) {
		LinkedHashSet<String> lines = new LinkedHashSet<>();
		if (existing != null) {
			for (String line : existing.split("\\R")) {
				if (!line.isBlank()) {
					lines.add(line.trim());
				}
			}
		}
		lines.addAll(equipment);
		return String.join("\n", lines);
	}
}
