package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics207Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskSupport;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfFormLayoutRegressionTest {
	private static final float DEFAULT_PAGE_MARGIN = 18f;

	@Test
	void generatedFormsKeepBottomSectionAlignedAndInsideMargins() throws Exception {
		AppData data = sampleData();
		Path tempDir = Files.createTempDirectory("pdf-layout-regression");

		assertFormLayout(new Ics201PdfRenderer(), data, tempDir.resolve("ics201.pdf"), DEFAULT_PAGE_MARGIN);
		assertFormLayout(new Ics202PdfRenderer(), data, tempDir.resolve("ics202.pdf"), DEFAULT_PAGE_MARGIN);
		assertFormLayout(new Ics204PdfRenderer(), data, tempDir.resolve("ics204.pdf"), DEFAULT_PAGE_MARGIN);
		assertFormLayout(new Ics205aPdfRenderer(), data, tempDir.resolve("ics205a.pdf"), DEFAULT_PAGE_MARGIN);
		assertFormLayout(new Ics207PdfRenderer(), data, tempDir.resolve("ics207.pdf"), DEFAULT_PAGE_MARGIN);
		assertFormLayout(new Ics214PdfRenderer(), data, tempDir.resolve("ics214.pdf"), DEFAULT_PAGE_MARGIN);
		assertFormLayout(new SarTaskAssignmentPdfRenderer(), data, tempDir.resolve("sar-task.pdf"),
				DEFAULT_PAGE_MARGIN);
	}

	@Test
	void renderersSupportConfiguredA4Margins() throws Exception {
		AppData data = sampleData();
		PdfLayoutSettings settings = data.getPdfLayoutSettings();
		settings.setPaperSize(PdfLayoutSettings.PaperSize.A4);
		settings.setPageMarginPoints(54f);

		Path file = Files.createTempFile("ics204-a4", ".pdf");
		new Ics204PdfRenderer().render(data, file);

		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			PDPage page = pdf.getPage(0);
			assertTrue(closeTo(page.getMediaBox().getWidth(), PDRectangle.A4.getWidth()));
			assertTrue(closeTo(page.getMediaBox().getHeight(), PDRectangle.A4.getHeight()));

			Rect outer = outerFormRectangle(page);
			assertEquals(54f, outer.x(), 0.2f);
			assertEquals(54f, outer.y(), 0.2f);
			assertTrue(outer.x() + outer.width() <= page.getMediaBox().getWidth() - 54f + 0.2f);
			assertTrue(outer.y() + outer.height() <= page.getMediaBox().getHeight() - 54f + 0.2f);
			assertTrue(hasBottomAlignedSection(page, outer));
		}
	}

	private void assertFormLayout(PdfFormRenderer renderer, AppData data, Path file, float margin) throws Exception {
		renderer.render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			assertTrue(pdf.getNumberOfPages() >= 1);
			for (int i = 0; i < pdf.getNumberOfPages(); i++) {
				PDPage page = pdf.getPage(i);
				Rect outer = outerFormRectangle(page);
				assertWithinMargins(page.getMediaBox(), outer, margin);
				assertTrue(hasBottomAlignedSection(page, outer),
						renderer.getFormKey() + " page " + (i + 1) + " should have a bottom-aligned metadata section");
			}
		}
	}

	private Rect outerFormRectangle(PDPage page) throws Exception {
		List<Rect> rectangles = rectangles(page);
		PDRectangle mediaBox = page.getMediaBox();
		Rect outer = rectangles.stream().filter(rect -> rect.width() > mediaBox.getWidth() * 0.75f)
				.filter(rect -> rect.height() > mediaBox.getHeight() * 0.75f)
				.max(java.util.Comparator.comparingDouble(Rect::area)).orElse(null);
		assertNotNull(outer, "expected an enclosing form rectangle");
		return outer;
	}

	private boolean hasBottomAlignedSection(PDPage page, Rect outer) throws Exception {
		for (Rect rect : rectangles(page)) {
			if (rect.area() >= outer.area()) {
				continue;
			}
			if (closeTo(rect.x(), outer.x()) && closeTo(rect.y(), outer.y()) && closeTo(rect.width(), outer.width())
					&& rect.height() < outer.height()) {
				return true;
			}
		}
		return false;
	}

	private void assertWithinMargins(PDRectangle mediaBox, Rect outer, float margin) {
		assertTrue(outer.x() >= margin - 0.2f);
		assertTrue(outer.y() >= margin - 0.2f);
		assertTrue(outer.x() + outer.width() <= mediaBox.getWidth() - margin + 0.2f);
		assertTrue(outer.y() + outer.height() <= mediaBox.getHeight() - margin + 0.2f);
	}

	private List<Rect> rectangles(PDPage page) throws Exception {
		List<Rect> rectangles = new ArrayList<>();
		try (InputStream inputStream = page.getContents()) {
			PDFStreamParser parser = new PDFStreamParser(inputStream.readAllBytes());
			List<Object> tokens = parser.parse();
			for (int i = 4; i < tokens.size(); i++) {
				Object token = tokens.get(i);
				if (!(token instanceof Operator operator) || !"re".equals(operator.getName())) {
					continue;
				}
				if (!(tokens.get(i - 4) instanceof COSNumber rectX) || !(tokens.get(i - 3) instanceof COSNumber rectY)
						|| !(tokens.get(i - 2) instanceof COSNumber rectWidth)
						|| !(tokens.get(i - 1) instanceof COSNumber rectHeight)) {
					continue;
				}
				rectangles.add(new Rect(rectX.floatValue(), rectY.floatValue(), rectWidth.floatValue(),
						rectHeight.floatValue()));
			}
		}
		assertFalse(rectangles.isEmpty(), "expected rectangle drawing commands");
		return rectangles;
	}

	private boolean closeTo(float actual, float expected) {
		return Math.abs(actual - expected) < 0.2f;
	}

	private AppData sampleData() {
		IncidentContext context = new IncidentContext("Test Incident", LocalDateTime.parse("2026-01-01T00:00:00"),
				LocalDateTime.parse("2026-01-01T12:00:00"), "Planner", "Planning Section Chief");
		context.setTaskMap("Map-42");

		Ics201Form form201 = new Ics201Form();
		form201.setIncidentName("Test Incident");
		form201.setIncidentNumber("INC-201");
		form201.setDateInitiated(LocalDate.parse("2026-01-01"));
		form201.setTimeInitiated(LocalTime.parse("06:30"));
		form201.setMapSketch("Grid map attached");
		form201.setSituationSummary("Initial briefing summary");
		form201.setCurrentObjectives(List.of("Protect life", "Stabilize scene"));
		Ics201Form.ActionEntry actionEntry = new Ics201Form.ActionEntry();
		actionEntry.setTime("07:00");
		actionEntry.setActions("Dispatch initial resources");
		form201.setCurrentActions(List.of(actionEntry));
		Ics201Form.ResourceSummaryEntry resourceSummary = new Ics201Form.ResourceSummaryEntry();
		resourceSummary.setResource("Ground Team");
		resourceSummary.setResourceIdentifier("GT-1");
		resourceSummary.setDateTimeOrdered(LocalDateTime.parse("2026-01-01T07:05:00"));
		resourceSummary.setEta(LocalDateTime.parse("2026-01-01T07:35:00"));
		resourceSummary.setArrived(true);
		resourceSummary.setNotes("Ready at ICP");
		form201.setResources(List.of(resourceSummary));
		form201.setPreparedByName("Planner");
		form201.setPreparedByPositionTitle("Planning Section Chief");
		form201.setPreparedBySignature("Planner");
		form201.setPreparedDateTime(LocalDateTime.parse("2026-01-01T00:30:00"));
		form201.setIapPage("1");

		Ics202Form form202 = new Ics202Form();
		form202.setObjectives(List.of("Protect life", "Stabilize scene"));
		form202.setCommandEmphasis("Responder accountability");
		form202.setSituationalAwareness("Wind shift expected.");
		form202.setSiteSafetyPlanRequired(true);
		form202.setIncidentActionPlanAttachments(List.of("ICS 203", "Map packet"));
		form202.setApprovedByIncidentCommanderName("IC Name");
		form202.setApprovedDateTime(LocalDateTime.parse("2026-01-01T01:00:00"));
		form202.setIapPage("2");

		ResourceAssignment resource = new ResourceAssignment();
		resource.setAssignmentId("assign-1");
		resource.setAssignmentTeamNumber("A-1");
		resource.setResourceType(SarTaskSupport.RESOURCE_TYPE_CANINE);
		resource.setTaskType(SarTaskSupport.TASK_TYPE_AREA);
		resource.setResourceIdentifier("Team 1");
		resource.setLeaderRole("Leader/Handler");
		resource.setLeader("Leader A");
		resource.setNumberOfPersons(4);
		resource.setContact("555-0101");
		resource.setReportingLocation("ICP");
		resource.setSpecialEquipment("ATV");
		resource.setSupplies("Medical kit");
		resource.setRemarks("Check in hourly");
		resource.setNotes("Bring maps");
		resource.setAssignment("Search trail segment");

		CommunicationEntry communicationEntry = new CommunicationEntry();
		communicationEntry.setName("Team 1 Lead");
		communicationEntry.setFunction("Medical");
		communicationEntry.setPrimaryContact("Tac 1");

		Ics204Form form204 = new Ics204Form();
		form204.setManagementContext(Ics204Form.MANAGEMENT_DIVISION);
		form204.setDivision("Division A");
		form204.setOperationsSectionChiefName("Ops Chief");
		form204.setOperationsSectionChiefContact("555-0199");
		form204.setDivisionGroupSupervisorName("Supervisor");
		form204.setDivisionGroupSupervisorContact("Tac 2");
		form204.setResourcesAssigned(List.of(resource));
		form204.setCommunications(List.of(communicationEntry));
		form204.setSharedWorkAssignment("Shared assignment");
		form204.setSpecialInstructions("Maintain radio discipline");
		form204.setPreparedByName("Planner");
		form204.setPreparedByPositionTitle("Planning Section Chief");
		form204.setPreparedDateTime(LocalDateTime.parse("2026-01-01T02:00:00"));
		form204.setIapPage("3");

		SarTaskAssignment task = SarTaskAssignment.fromResourceAssignment(resource, context, form204);
		task.setPreparedDateTime(form204.getPreparedDateTime());
		task.setDebriefNotes("Initial debrief notes");
		task.setReportedPod("65");
		task.setCanineSearchType("Wilderness air scent");
		task.setCanineImprint("Live find");
		task.setCanineSunAngle("Low sun");
		task.setCanineDayNight("Day");
		task.setCanineCloudCover("Broken clouds");
		task.setCanineWindSpeed("8 mph");
		task.setQualitativePodFactors(samplePodFactors());

		Ics207Form form207 = new Ics207Form();
		form207.setPreparedByName("Planner");
		form207.setPreparedByPositionTitle("Planning Section Chief");
		form207.setPreparedDateTime(LocalDateTime.parse("2026-01-01T02:30:00"));
		form207.setIapPage("4");

		Ics214Form form214 = new Ics214Form();
		form214.setName("Planning");
		form214.setIcsPosition("Situation Unit");
		form214.setHomeAgency("SAR");
		ActivityLogEntry logEntry = new ActivityLogEntry();
		logEntry.setTimestamp(LocalDateTime.parse("2026-01-01T09:00:00"));
		logEntry.setNotableActivity("Briefed incoming resources");
		form214.setActivityLog(List.of(logEntry));
		form214.setPreparedByName("Planner");
		form214.setPreparedByPositionTitle("Planning Section Chief");
		form214.setPreparedBySignature("Planner");
		form214.setPreparedDateTime(LocalDateTime.parse("2026-01-01T03:00:00"));
		form214.setIapPage("5");

		TCard preparer = new TCard();
		preparer.setCardType(TCardType.PERSONNEL);
		preparer.setPersonName("Planner, Pat");
		preparer.setHomeAgency("County SAR");
		preparer.setHomeState("WA");
		preparer.setStatus("Assigned");
		preparer.setPhoneNumber("555-0102");
		preparer.setRadioChannel("Tac 2");
		preparer.setSourceRef("context:preparer");

		AppData data = new AppData(context, form202, form204, List.of(task));
		data.setForm201(form201);
		data.setForm207(form207);
		data.setActivityLogs(List.of(form214));
		data.setTCards(List.of(preparer));
		OrganizationalChart organizationalChart = new OrganizationalChart();
		organizationalChart.setIncidentCommanders(List.of("IC One", "IC Two"));
		organizationalChart.setOperationsSectionChiefName("Ops Chief");
		data.setOrganizationalChart(organizationalChart);
		data.setSchemaVersion(AppData.CURRENT_SCHEMA_VERSION);
		return data;
	}

	private List<PodFactorRating> samplePodFactors() {
		PodFactorRating hazards = new PodFactorRating();
		hazards.setName("Hazards Observed");
		hazards.setMaxScore(5);
		hazards.setScore(4);

		PodFactorRating wind = new PodFactorRating();
		wind.setName("Wind");
		wind.setMaxScore(10);
		wind.setScore(7);
		wind.setDescription("Steady crosswind");
		return List.of(hazards, wind);
	}

	private record Rect(float x, float y, float width, float height) {
		private float area() {
			return width * height;
		}
	}
}
