/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics215Form;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.PlanReviewArea;
import org.sarmanagement.icsforms.model.PlanReviewStatus;
import org.sarmanagement.icsforms.model.PlanReuseService;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RecordIdentityAssertions;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies controller plan reuse without opening or mutating the source incident. */
class AppControllerPlanReuseTest {
	@TempDir
	Path directory;

	@Test
	void everyReuseInteractionFlushesBeforeCopyAndRefreshesAfterward() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData target = new AppData();
			var id = store.create(target);
			AppController controller = new AppController(target, store, id,
					new PdfExportService(), new IncidentValidator());
			List<String> events = new ArrayList<>();
			controller.setPlanReuseEditorCallbacks(() -> {
				events.add("flush");
				target.getMedicalPlan().setEmergencyProcedures("Pending current editor");
			}, () -> {
				events.add("refresh");
				assertEquals("Imported plan", target.getMedicalPlan().getEmergencyProcedures());
			});
			AppData source = new AppData();
			source.getMedicalPlan().setEmergencyProcedures("Imported plan");
			controller.withPlanReuseEditors(() -> {
				events.add("import");
				assertEquals("Pending current editor", target.getMedicalPlan().getEmergencyProcedures());
				controller.bringForwardPlans(
						org.sarmanagement.icsforms.model.PlanReuseService.snapshot(source),
						Set.of(PlanReviewArea.HUMAN_MEDICAL), true);
			});
			assertEquals(List.of("flush", "import", "refresh"), events);
			controller.save();
			events.clear();
			controller.setPlanReuseEditorCallbacks(() -> events.add("flush"), () -> events.add("refresh"));
			assertThrows(IllegalStateException.class, () -> controller.withPlanReuseEditors(() -> {
				throw new IllegalStateException("Cancelled interaction");
			}));
			assertEquals(List.of("flush", "refresh"), events);
			events.clear();
			controller.setPlanReuseEditorCallbacks(() -> {
				events.add("rejected table edit");
				throw new IllegalStateException("Finish editing the table value.");
			}, () -> events.add("refresh"));
			assertThrows(IllegalStateException.class,
					() -> controller.withPlanReuseEditors(() -> events.add("must not open")));
			assertEquals(List.of("rejected table edit"), events);
		}
	}

	@Test
	void previousSourcesRequirePlansAndRemainIndependent() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			var id = store.create(data);
			AppController controller = new AppController(data, store, id, new PdfExportService(), new IncidentValidator());
			assertFalse(controller.hasPreviousPlans());
			assertFalse(controller.canReusePlans());
			controller.newOperationalPeriod();
			assertFalse(controller.hasPreviousPlans());
			data.getForm208().setFreeText("Previous safety plan");
			controller.save();
			controller.newOperationalPeriod();
			assertTrue(controller.hasPreviousPlans());
			assertTrue(controller.canReusePlans());
			assertEquals(1, controller.getPreviousPlanSources().size());
			var source = controller.getPreviousPlanSources().get(0);
			source.getForm208().setFreeText("Detached snapshot");
			assertEquals("Previous safety plan", data.getOperationalPeriodHistory().get(1).getForm208().getFreeText());
			controller.bringForwardPlans(source, Set.of(PlanReviewArea.SAFETY), false);
			assertEquals("Detached snapshot", data.getForm208().getFreeText());
			controller.save();
			assertEquals("Detached snapshot", store.load(id).getForm208().getFreeText());
		}
	}

	@Test
	void importsCurrentAndOlderPlansWithoutSwitchingOrSavingSourceIncident() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData source = new AppData();
			source.getIncidentContext().setIncidentName("Source");
			source.getForm208().setFreeText("Older safety");
			source.advanceToNewOperationalPeriod();
			source.getMedicalPlan().setEmergencyProcedures("Current medical");
			source.recordPlanReview(PlanReviewArea.HUMAN_MEDICAL, "Source reviewer", LocalDateTime.now());
			var sourceId = store.create(source);
			long sourceRevision = source.getRevision();
			AppData target = new AppData();
			target.getIncidentContext().setIncidentName("Target");
			LocalDateTime currentStart = LocalDateTime.of(2026, 10, 25, 10, 0);
			target.getIncidentContext().setOperationalPeriodStart(currentStart);
			var targetId = store.create(target);
			AppController controller = new AppController(target, store, targetId,
					new PdfExportService(), new IncidentValidator());
			var binding = controller.captureIncidentBinding();
			assertFalse(controller.hasPreviousPlans());
			assertTrue(controller.canReusePlans());
			var sources = controller.getImportPlanSources(sourceId);
			assertEquals(2, sources.size());
			controller.bringForwardPlans(sources.get(0), Set.of(PlanReviewArea.HUMAN_MEDICAL), true);
			controller.bringForwardPlans(sources.get(1), Set.of(PlanReviewArea.SAFETY), true);
			assertTrue(controller.isCurrent(binding));
			assertSame(target, controller.getData());
			assertEquals(targetId, controller.getActiveIncidentId());
			assertEquals("Target", target.getIncidentContext().getIncidentName());
			assertEquals(currentStart, target.getIncidentContext().getOperationalPeriodStart());
			assertEquals("Current medical", target.getMedicalPlan().getEmergencyProcedures());
			assertEquals("Older safety", target.getForm208().getFreeText());
			assertEquals(PlanReviewStatus.NOT_YET_REVIEWED, target.getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
			controller.save();
			AppData saved = store.load(targetId);
			assertEquals("Current medical", saved.getMedicalPlan().getEmergencyProcedures());
			assertEquals("Older safety", saved.getForm208().getFreeText());
			assertEquals(currentStart, saved.getIncidentContext().getOperationalPeriodStart());
			assertEquals(sourceRevision, store.load(sourceId).getRevision());
			assertEquals(PlanReviewStatus.REVIEWED_APPROVED,
					store.load(sourceId).getPlanReviewStatus(PlanReviewArea.HUMAN_MEDICAL));
			assertThrows(IllegalArgumentException.class, () -> controller.getImportPlanSources(targetId));
		}
	}

	@Test
	void reusesEveryPlanAreaWithFreshIdentitiesAndConsistentWorksheetReferences() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData source = reusablePlans();
			Map<Class<?>, Set<String>> originalSourceIds = RecordIdentityAssertions.collect(source);
			AppData.OperationalPeriodRecord snapshot = PlanReuseService.snapshot(source);
			Map<Class<?>, Set<String>> sourceIds = RecordIdentityAssertions.collect(snapshot);
			AppData target = new AppData();
			var targetId = store.create(target);
			AppController controller = new AppController(target, store, targetId,
					new PdfExportService(), new IncidentValidator());
			controller.bringForwardPlans(snapshot, Set.of(PlanReviewArea.values()), true, true, false);

			Map<Class<?>, Set<String>> targetIds = RecordIdentityAssertions.collect(target);
			for (Map.Entry<Class<?>, Set<String>> entry : sourceIds.entrySet()) {
				Set<String> targetTypeIds = targetIds.getOrDefault(entry.getKey(), Set.of());
				assertTrue(java.util.Collections.disjoint(entry.getValue(), targetTypeIds),
						entry.getKey().getSimpleName() + " retained source identities");
			}
			Ics215Form.ResourceHeading sourceHeading = snapshot.getNextPeriodForm215().getResourceHeadings().get(0);
			Ics215Form.ResourceHeading copiedHeading = target.getNextPeriodForm215().getResourceHeadings().get(0);
			Ics215Form.WorkRow copiedRow = target.getNextPeriodForm215().getWorkRows().get(0);
			assertNotEquals(sourceHeading.id, copiedHeading.id);
			assertNotEquals(snapshot.getNextPeriodForm215().getWorkRows().get(0).id, copiedRow.id);
			assertTrue(copiedRow.resourceAllocations.containsKey(copiedHeading.id));
			assertFalse(copiedRow.resourceAllocations.containsKey(sourceHeading.id));
			assertEquals("", copiedRow.ics204DraftFormId);
			assertEquals(source.getForm205().getChannels().get(0).getId(),
					snapshot.getForm205().getChannels().get(0).getId());
			RecordIdentityAssertions.collect(target);
			assertEquals(originalSourceIds, RecordIdentityAssertions.collect(source));
			assertEquals(sourceIds, RecordIdentityAssertions.collect(snapshot));
		}
	}

	@Test
	void previousPeriodReuseKeepsArchiveIdsAndRemintsTheLiveCopy() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = reusablePlans();
			data.advanceToNewOperationalPeriod(true);
			AppData.OperationalPeriodRecord archived = data.getOperationalPeriodHistory().get(0);
			Map<Class<?>, Set<String>> archivedIds = RecordIdentityAssertions.collect(archived);
			var id = store.create(data);
			AppController controller = new AppController(data, store, id,
					new PdfExportService(), new IncidentValidator());

			controller.bringForwardPlans(archived, Set.of(PlanReviewArea.values()), false, true, false);

			Map<Class<?>, Set<String>> allIds = RecordIdentityAssertions.collect(data);
			for (Map.Entry<Class<?>, Set<String>> entry : archivedIds.entrySet()) {
				Set<String> liveTypeIds = new java.util.HashSet<>(allIds.getOrDefault(entry.getKey(), Set.of()));
				liveTypeIds.removeAll(entry.getValue());
				assertTrue(java.util.Collections.disjoint(entry.getValue(), liveTypeIds));
			}
			RecordIdentityAssertions.collect(data);
			assertEquals(archivedIds, RecordIdentityAssertions.collect(archived));
		}
	}

	/**
	 * Creates representative content for all reusable plan areas and ICS 215 links.
	 *
	 * @return incident containing each reusable plan type
	 */
	private static AppData reusablePlans() {
		AppData source = new AppData();
		source.getForm205().setSpecialInstructions("Shared channel plan");
		source.getForm205().getChannels().add(new RadioChannel());
		org.sarmanagement.icsforms.model.SafetyAnalysisEntry analysis =
				new org.sarmanagement.icsforms.model.SafetyAnalysisEntry();
		analysis.setHazardsRisks("Uneven terrain");
		source.getForm215a().getEntries().add(analysis);
		SafetyMessageBlock safetyBlock = new SafetyMessageBlock();
		safetyBlock.setHazard("Cold weather");
		source.getForm208().getHazardBlocks().add(safetyBlock);
		MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
		hospital.setName("Regional hospital");
		source.getMedicalPlan().getHospitals().add(hospital);
		source.getCanineMedicalPlan().getSelectedFacilities().add(new VetFacility());
		source.getCanineMedicalPlan().getSelectedFacilities().get(0).setName("Snapshot clinic");
		Ics215Form planned = source.getNextPeriodForm215();
		Ics215Form.ResourceHeading heading = new Ics215Form.ResourceHeading();
		heading.category = "Medical";
		planned.getResourceHeadings().add(heading);
		Ics215Form.WorkRow row = new Ics215Form.WorkRow();
		row.resourceAllocations.put(heading.id, new Ics215Form.ResourceAllocation());
		row.ics204DraftFormId = "source-form";
		planned.getWorkRows().add(row);
		return source;
	}
}
