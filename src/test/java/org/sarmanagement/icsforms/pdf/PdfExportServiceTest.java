/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PdfExportService} helper logic — specifically the ICS 201
 * inclusion rules and IAP page numbering.
 */
class PdfExportServiceTest {
	@TempDir
	Path outputDirectory;

	@Test
	void organizationAssignmentListBundleRequiresEntriesAndEnabledSetting() throws Exception {
		AppData data = new AppData();
		PdfExportService service = new PdfExportService(new Ics203PdfRenderer());
		data.getOrganizationalChart().getAssignmentList().setPreparedByName("Preparer only");
		assertFalse(PdfExportService.includeIcs203(data));
		assertEquals(1, bundlePages(service, data));

		data.getOrganizationalChart().setSafetyOfficerName("A. Safety");
		assertTrue(PdfExportService.includeIcs203(data));
		assertEquals(2, bundlePages(service, data));

		data.getOrganizationalChart().getAssignmentList().setIncludeInIap(false);
		assertFalse(PdfExportService.includeIcs203(data));
		assertEquals(1, bundlePages(service, data));
		try (PDDocument pdf = Loader.loadPDF(service.exportSelected("ICS 203", data, outputDirectory).toFile())) {
			assertEquals(1, pdf.getNumberOfPages());
			assertTrue(new PDFTextStripper().getText(pdf).contains("A. Safety"));
		}
	}

	@Test
	void organizationAssignmentListFollows202AndOffsetsLaterFormsForContinuationPages() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().setSafetyOfficerName("A. Safety");
		for (int i = 0; i < 60; i++) {
			OrgChartEntry representative = new OrgChartEntry();
			representative.setTitle("Agency " + i);
			representative.setName("Representative " + i);
			data.getOrganizationalChart().getAssignmentList().getAgencyRepresentatives().add(representative);
		}
		int pages203 = Ics203PdfRenderer.pageCount(data);
		assertTrue(pages203 > 1);
		PdfExportService.assignIapPageNumbers(data, List.of("ICS 207", "ICS 203", "ICS 202"), true);
		assertEquals("1", data.getForm202().getIapPage());
		assertEquals("2", data.getOrganizationalChart().getAssignmentList().getIapPage());
		assertEquals(String.valueOf(2 + pages203), data.getForm207().getIapPage());
		PdfExportService service = new PdfExportService(new Ics207PdfRenderer(), new Ics203PdfRenderer(),
				new Ics202PdfRenderer());
		Path bundle = service.exportIapBundle(data, outputDirectory, List.of("ICS 207", "ICS 203", "ICS 202"));
		try (PDDocument pdf = Loader.loadPDF(bundle.toFile())) {
			assertEquals(3 + pages203, pdf.getNumberOfPages());
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(2);
			stripper.setEndPage(2);
			assertTrue(stripper.getText(pdf).contains("INCIDENT OBJECTIVES"));
			stripper.setStartPage(3);
			stripper.setEndPage(3);
			assertTrue(stripper.getText(pdf).contains("ORGANIZATION ASSIGNMENT LIST"));
			stripper.setStartPage(3 + pages203);
			stripper.setEndPage(3 + pages203);
			assertTrue(stripper.getText(pdf).contains("INCIDENT ORGANIZATION CHART"));
		}
		data.getOrganizationalChart().getAssignmentList().setIncludeInIap(false);
		PdfExportService.assignIapPageNumbers(data, List.of("ICS 202", "ICS 203", "ICS 207"), true);
		assertEquals("", data.getOrganizationalChart().getAssignmentList().getIapPage());
		assertEquals("2", data.getForm207().getIapPage());
	}

	@Test
	void emptyPageNumberSelectionIncludesNoForms() {
		AppData data = new AppData();
		data.getOrganizationalChart().setSafetyOfficerName("A. Safety");
		data.getOrganizationalChart().getAssignmentList().setIapPage("99");
		data.setForm205aIapPage("88");
		PdfExportService.assignIapPageNumbers(data, List.of(), true);
		assertEquals("", data.getOrganizationalChart().getAssignmentList().getIapPage());
		assertEquals("", data.getForm205aIapPage());
		assertEquals("", data.getForm202().getIapPage());
		assertEquals("", data.getForm207().getIapPage());
	}

	@Test
	void deselectedOrganizationAssignmentListDoesNotConsumeIapPages() {
		AppData data = new AppData();
		data.getOrganizationalChart().setSafetyOfficerName("A. Safety");
		PdfExportService.assignIapPageNumbers(data, List.of("ICS 202", "ICS 207"), true);
		assertEquals("", data.getOrganizationalChart().getAssignmentList().getIapPage());
		assertEquals("2", data.getForm207().getIapPage());
		assertEquals(List.of("ICS 202", "ICS 203", "ICS 205A", "ICS 207"),
				PdfExportService.orderForIapBundle(List.of("ICS 207", "ICS 203", "ICS 205A", "ICS 202")));
	}

	@Test
	void defaultSafetyPageNumbersFollowIcs202Attachments() {
		AppData data = new AppData();
		data.getForm202().setIncidentActionPlanAttachments(List.of("ICS 208"));

		PdfExportService.assignIapPageNumbers(data, null, true);

		assertFalse(data.getForm208().getIapPage().isBlank());
		assertTrue(data.getForm215a().getIapPage().isBlank());
		assertTrue(PdfExportService.isIapAttachmentSelected(data, "ICS 208"));
		assertFalse(PdfExportService.isIapAttachmentSelected(data, "ICS 215A"));
	}

	@Test
	void medicalPlanInclusionIsControlledByIcs202AttachmentSelection() throws Exception {
		PdfExportService service = new PdfExportService(new Ics206PdfRenderer());
		AppData data = new AppData();

		assertTrue(service.exportAll(data, outputDirectory).isEmpty());

		data.getForm202().setIncidentActionPlanAttachments(List.of("ICS 206"));
		assertTrue(service.exportAll(data, outputDirectory).containsKey("ICS 206"));
	}

	@Test
	void caninePlanInclusionIsControlledByIcs202AttachmentSelection() throws Exception {
		PdfExportService service = new PdfExportService(new Ics206CaninePdfRenderer());
		AppData data = new AppData();

		assertTrue(service.exportAll(data, outputDirectory).isEmpty());

		data.getForm202().setIncidentActionPlanAttachments(List.of("ICS 206CANINE"));
		assertTrue(service.exportAll(data, outputDirectory).containsKey("ICS 206CANINE"));
		PdfExportService.assignIapPageNumbers(data, List.of("ICS 206CANINE"), true);
		assertEquals("1", data.getCanineMedicalPlan().getIapPage());
	}

	/**
	 * Exports and counts an IAP containing only the registered organization list.
	 *
	 * @param service export service.
	 * @param data incident document.
	 * @return rendered page count including the cover.
	 * @throws Exception when PDF export or loading fails.
	 */
	private int bundlePages(PdfExportService service, AppData data) throws Exception {
		try (PDDocument pdf = Loader.loadPDF(service.exportIapBundle(data, outputDirectory).toFile())) {
			return pdf.getNumberOfPages();
		}
	}

	@Test
	void exportCopyRetainsStoreMetadataAndTrackerPreferences() throws Exception {
		AppData data = new AppData();
		data.normalizeIdentity();
		data.setRevision(12);
		data.setOriginNodeId("origin");
		data.setOpenActionTrackerEnabled(true);
		data.setIcs233Portrait(true);
		data.setClueLogIcs233Enabled(true);
		PdfExportService service = new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
				new SarTaskAssignmentPdfRenderer());
		var method = PdfExportService.class.getDeclaredMethod("copyAppData", AppData.class);
		method.setAccessible(true);
		AppData copy = (AppData) method.invoke(service, data);
		assertEquals(data.getIncidentId(), copy.getIncidentId());
		assertEquals(data.getRevision(), copy.getRevision());
		assertEquals(data.getCreatedAt(), copy.getCreatedAt());
		assertEquals(data.getUpdatedAt(), copy.getUpdatedAt());
		assertEquals(data.getOriginNodeId(), copy.getOriginNodeId());
		assertTrue(copy.isOpenActionTrackerEnabled());
		assertTrue(copy.isIcs233Portrait());
		assertTrue(copy.isClueLogIcs233Enabled());
	}

	// -------------------------------------------------------------------------
	// ics201HasContent
	// -------------------------------------------------------------------------

	@Test
	void ics201HasContent_nullForm_returnsFalse() {
		assertFalse(PdfExportService.ics201HasContent(null));
	}

	@Test
	void ics201HasContent_blankForm_returnsFalse() {
		Ics201Form form = new Ics201Form();
		// Both situationSummary and preparedByName are empty by default.
		assertFalse(PdfExportService.ics201HasContent(form));
	}

	@Test
	void ics201HasContent_withSituationSummary_returnsTrue() {
		Ics201Form form = new Ics201Form();
		form.setSituationSummary("Three lost hikers, day 2 of search.");
		assertTrue(PdfExportService.ics201HasContent(form));
	}

	@Test
	void ics201HasContent_withPreparedByName_returnsTrue() {
		Ics201Form form = new Ics201Form();
		form.setPreparedByName("J. Smith");
		assertTrue(PdfExportService.ics201HasContent(form));
	}

	@Test
	void ics201HasContent_withBothFields_returnsTrue() {
		Ics201Form form = new Ics201Form();
		form.setSituationSummary("Day 1 briefing.");
		form.setPreparedByName("Operations Chief");
		assertTrue(PdfExportService.ics201HasContent(form));
	}

	// -------------------------------------------------------------------------
	// assignIapPageNumbers — ICS 201 inclusion rules
	// -------------------------------------------------------------------------

	@Test
	void assignIapPageNumbers_emptyForm201_skipsIcs201Page() {
		AppData data = new AppData();
		data.setIapPhase(IapPhase.DURING_OP);
		// Form 201 is empty — no content.
		PdfExportService.assignIapPageNumbers(data);
		// Page should not have been assigned to form 201.
		assertTrue(data.getForm201().getIapPage() == null || data.getForm201().getIapPage().isBlank(),
				"Blank ICS 201 should not receive an IAP page number");
		// Form 202 should start at page 1.
		assertEquals("1", data.getForm202().getIapPage());
	}

	@Test
	void assignIapPageNumbers_completedForm201_includesIcs201PageRegardlessOfPhase() {
		AppData data = new AppData();
		data.setIapPhase(IapPhase.DURING_OP);
		data.getForm201().setSituationSummary("Initial response briefing.");
		PdfExportService.assignIapPageNumbers(data);
		assertEquals("1", data.getForm201().getIapPage(),
				"Completed ICS 201 must be assigned page 1 even in DURING_OP phase");
		assertEquals("5", data.getForm202().getIapPage(), "ICS 202 should follow immediately after ICS 201");
		assertEquals("9", data.getForm205().getIapPage());
		assertEquals("10", data.getForm205aIapPage(),
				"ICS 205A follows ICS 205, ICS 204 and its selected SAR task assignment pages");
	}

	@Test
	void assignIapPageNumbers_initialResponsePhaseWithContent_includesIcs201() {
		AppData data = new AppData();
		data.setIapPhase(IapPhase.INITIAL_RESPONSE);
		data.getForm201().setPreparedByName("IC Name");
		PdfExportService.assignIapPageNumbers(data);
		assertEquals("1", data.getForm201().getIapPage());
		assertEquals("5", data.getForm202().getIapPage());
	}

	@Test
	void assignIapPageNumbers_offsetsLaterFormsWhen205aSpansMultiplePages() {
		AppData data = new AppData();
		data.getForm201().setPreparedByName("IC Name");
		List<TCard> cards = new ArrayList<>();
		for (int i = 0; i < 22; i++) {
			TCard card = new TCard();
			card.setCardType(TCardType.PERSONNEL);
			card.setPersonName("Person " + i);
			cards.add(card);
		}
		data.setTCards(cards);

		PdfExportService.assignIapPageNumbers(data);

		assertEquals("10", data.getForm205aIapPage());
		assertEquals(String.valueOf(10 + Ics205aPdfRenderer.pageCount(data)), data.getForm207().getIapPage());
	}

	@Test
	void assignIapPageNumbers_skips205aWhenNotIncludedInSelectedBundle() {
		AppData data = new AppData();

		PdfExportService.assignIapPageNumbers(data, List.of("ICS 202", "ICS 207"), true);

		assertEquals("", data.getForm205aIapPage());
		assertEquals("1", data.getForm202().getIapPage());
		assertEquals("2", data.getForm207().getIapPage());
	}

	@Test
	void assignIapPageNumbers_offsetsForMultipageTaskAndActivityForms() {
		AppData data = new AppData();
		data.getForm201().setPreparedByName("IC Name");

		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("TASK-1");
		data.getSarTaskAssignments().add(task);

		Ics214Form firstLog = new Ics214Form();
		for (int i = 0; i < 20; i++) {
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setNotableActivity("Entry " + i);
			firstLog.getActivityLog().add(entry);
		}
		Ics214Form secondLog = new Ics214Form();
		data.getActivityLogs().add(firstLog);
		data.getActivityLogs().add(secondLog);

		PdfExportService.assignIapPageNumbers(data, List.of("ICS 201", "SAR Task Assignment", "ICS 214"), false);

		assertEquals("1", data.getForm201().getIapPage());
		assertEquals("5", task.getIapPage());
		assertEquals("7", firstLog.getIapPage());
		assertEquals("9", secondLog.getIapPage());
	}

	@Test
	void assignIapPageNumbersCountsBlankTaskAndActivityFormsWithoutMutatingData() {
		AppData data = new AppData();

		PdfExportService.assignIapPageNumbers(data, List.of("SAR Task Assignment", "ICS 214"), true);

		assertTrue(data.getSarTaskAssignments().isEmpty());
		assertTrue(data.getActivityLogs().isEmpty());
	}

	@Test
	void orderForIapBundleUsesNumericalOrderAndPlacesSarTasksAfterIcs204() {
		List<String> ordered = PdfExportService.orderForIapBundle(
				List.of("ICS 215A", "Clue Log", "ICS 214", "ICS 208", "ICS 205A", "ICS 204", "SAR Task Assignment",
						"ICS 202"));

		assertEquals(List.of("ICS 202", "ICS 204", "SAR Task Assignment", "ICS 205A", "ICS 208", "ICS 214",
				"ICS 215A", "Clue Log"), ordered);
	}

	@Test
	void iapPageNumbersUseTheSameOrderAsTheCombinedFormSequence() {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		data.setSarTaskAssignments(List.of(task));
		PdfExportService.assignIapPageNumbers(data,
				List.of("ICS 215A", "ICS 208", "SAR Task Assignment", "ICS 204", "ICS 202"), true);

		assertEquals("1", data.getForm202().getIapPage());
		assertEquals("2", data.getForm204().getIapPage());
		assertEquals("3", task.getIapPage());
		assertEquals("5", data.getForm208().getIapPage());
		assertEquals("6", data.getForm215a().getIapPage());
	}
}
