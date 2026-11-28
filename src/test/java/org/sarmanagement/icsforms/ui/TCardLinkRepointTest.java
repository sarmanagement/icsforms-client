/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;

/** Covers active references changed when duplicate T-cards are merged. */
@ExtendWith(SwingTestLifecycleExtension.class)
class TCardLinkRepointTest {
	@Test
	void repointsActiveReferencesAndRetainsOtherLinksAfterSaving() {
		AppData data = new AppData();
		SarTaskResource resource = new SarTaskResource();
		resource.setResourceId("removed");
		SarTaskAssignment task = new SarTaskAssignment();
		ResourceAssignment assignment = new ResourceAssignment();
		data.getForm204().getResourcesAssigned().add(assignment);
		task.setAssignmentId(assignment.getAssignmentId());
		task.setDebriefingSupervisorResourceId("removed");
		task.getResourcesAssigned().add(resource);
		data.getSarTaskAssignments().add(task);
		Ics214Form changed = new Ics214Form();
		changed.setLinkedResourceId("removed");
		Ics214Form unchanged = new Ics214Form();
		unchanged.setLinkedResourceId("other");
		data.getActivityLogs().add(changed);
		data.getActivityLogs().add(unchanged);
		data.getCanineMedicalPlan().setVeterinaryPersonnelResourceIds(List.of("removed", "other", "kept"));
		var fixture = IncidentStoreTestFixtures.create(data);
		AppController controller = fixture.controller();

		controller.repointTCardLinks("removed", "kept");
		controller.repointTCardLinks("removed", "kept");
		controller.markDirty();
		controller.save();

		AppData restored = fixture.store().load(fixture.incidentId());
		assertEquals("kept", restored.getSarTaskAssignments().get(0).getDebriefingSupervisorResourceId());
		assertEquals("kept", restored.getSarTaskAssignments().get(0).getResourcesAssigned().get(0).getResourceId());
		assertEquals(List.of("kept", "other"), restored.getActivityLogs().stream()
				.map(Ics214Form::getLinkedResourceId).toList());
		assertEquals(List.of("kept", "other"), restored.getCanineMedicalPlan().getVeterinaryPersonnelResourceIds());
	}

	@Test
	void blankReplacementDoesNotEraseReferences() {
		AppData data = new AppData();
		data.getCanineMedicalPlan().setVeterinaryPersonnelResourceIds(List.of("removed"));
		AppController controller = IncidentStoreTestFixtures.controller(data);

		controller.repointTCardLinks("removed", "");
		controller.repointTCardLinks("removed", null);

		assertEquals(List.of("removed"), data.getCanineMedicalPlan().getVeterinaryPersonnelResourceIds());
	}
}
