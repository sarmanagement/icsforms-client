package org.sarmanagement.icsforms.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SarTaskSupportTest {

    @Test
    void canineSweepWidthFactorRetainsLegacySavedValues() {
        PodFactorRating legacy = new PodFactorRating();
        legacy.setName("Established Sweep Width Pattern");
        legacy.setMaxScore(10);
        legacy.setScore(7);
        legacy.setDescription("Legacy value");

        PodFactorRating migrated = SarTaskSupport.factorRatings(SarTaskSupport.RESOURCE_TYPE_CANINE, List.of(legacy)).stream()
                .filter(rating -> "Sweep Width Pattern".equals(rating.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(migrated);
        assertEquals(7, migrated.getScore());
        assertEquals("Legacy value", migrated.getDescription());
    }

    @Test
    void taskLifecycleNormalizesLegacyStatesToNewAssignedVocabulary() {
        SarTaskAssignment task = new SarTaskAssignment();
        task.setTaskLifecycleStatus("On Task");
        assertEquals("assigned - on task", task.getTaskLifecycleStatus());
        task.setTaskLifecycleStatus("Planning");
        assertEquals("planned", task.getTaskLifecycleStatus());
        task.setTaskLifecycleStatus("Returned");
        assertEquals("returned", task.getTaskLifecycleStatus());
    }
}
