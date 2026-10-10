/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.safety;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.Ics215aForm;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests deterministic message drafts and preservation of officer edits.
 */
class SafetyMessageComposerTest {
	@Test
	void createsOneLinkedBlockPerHazardAndCollectsExplicitEquipmentOnly() {
		Ics215aForm analysis = new Ics215aForm();
		SafetyAnalysisEntry entry = entry("Terrain, Dense Brush, Trip Hazards", "Slow down.", "Compass\nWater");
		analysis.setEntries(List.of(entry));
		Ics208Form message = new Ics208Form();

		new SafetyMessageComposer().updateDraft(analysis, message);

		assertEquals(1, message.getHazardBlocks().size());
		assertEquals(entry.getHazardsRisks(), message.getHazardBlocks().get(0).getHazard());
		assertEquals(entry.getMitigations(), message.getHazardBlocks().get(0).getPrecautions());
		assertEquals(List.of(entry.getEntryId()), message.getHazardBlocks().get(0).getSourceEntryIds());
		assertEquals("Compass\nWater", message.getGeneralRequirements());
	}

	@Test
	void preservesEditedBlockAndFlagsItWhenSourceChanges() {
		Ics215aForm analysis = new Ics215aForm();
		SafetyAnalysisEntry entry = entry("Terrain", "Slow down.", "");
		analysis.setEntries(List.of(entry));
		Ics208Form message = new Ics208Form();
		SafetyMessageComposer composer = new SafetyMessageComposer();
		composer.updateDraft(analysis, message);
		SafetyMessageBlock block = message.getHazardBlocks().get(0);
		block.setPrecautions("Officer-edited instruction.");
		entry.setMitigations("Use a buddy system.");

		composer.updateDraft(analysis, message);

		assertEquals(1, message.getHazardBlocks().size());
		assertEquals("Officer-edited instruction.", block.getPrecautions());
		assertTrue(block.isNeedsReview());
	}

	@Test
	void doesNotExtractEquipmentFromUnclassifiedMitigationProse() {
		Ics215aForm analysis = new Ics215aForm();
		analysis.setEntries(List.of(entry("Terrain", "Wear boots and carry water.", "")));
		Ics208Form message = new Ics208Form();

		new SafetyMessageComposer().updateDraft(analysis, message);

		assertTrue(message.getGeneralRequirements().isEmpty());
		assertFalse(message.getHazardBlocks().get(0).getPrecautions().isEmpty());
	}

	private static SafetyAnalysisEntry entry(String hazard, String mitigation, String equipment) {
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setHazardsRisks(hazard);
		entry.setMitigations(mitigation);
		entry.setEquipmentPpe(equipment);
		return entry;
	}
}
