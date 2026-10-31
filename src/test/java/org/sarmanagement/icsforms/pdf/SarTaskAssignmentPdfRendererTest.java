/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.PdfLayoutSettings.PaperSize;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers lossless measured SAR layouts and lifecycle-sensitive printed debrief fields. */
class SarTaskAssignmentPdfRendererTest {
	@Test
	void nonReturnedTasksLeaveAllDebriefContentBlankWithoutChangingEditableData() throws Exception {
		for (String lifecycle : List.of("planned", "assigned - enroute to assignment", "assigned - on task",
				"assigned - returning from assignment")) {
			AppData data = data(lifecycle);
			data.getSarTaskAssignments().get(0).setDebriefingCompleted(true);
			try (PDDocument pdf = render(data)) {
				String front = page(pdf, 1);
				assertTrue(front.contains("2026-02-02"));
				assertTrue(front.contains("07:15"));
				assertTrue(front.contains("08:45"));
				assertTrue(front.contains("Planned assignment content"));
				assertTrue(front.contains("Front contact"));
				String debrief = page(pdf, 2);
				assertTrue(debrief.contains("13. Time On Assignment"));
				for (String label : List.of("Date From", "Date To", "Time From", "Time To")) {
					assertTrue(debrief.contains(label));
				}
				for (String value : List.of("2026-02-02", "07:15", "08:45")) {
					assertFalse(debrief.contains(value), lifecycle + ": " + value);
				}
				for (String value : List.of("Editable", "2026-02-02", "07:15", "08:45", "543.21",
						"REPORTED POD", "81%", "Clues Detected", "POSSIBLE DUPLICATE",
						"Qualitative POD", "Canine Assignment Conditions")) {
					assertFalse(debrief.contains(value), lifecycle + ": " + value);
				}
				assertTrue(debrief.contains("Identity-17"));
			}
			SarTaskAssignment task = data.getSarTaskAssignments().get(0);
			assertEquals("Editable Supervisor", task.getDebriefingSupervisor());
			assertEquals("Editable debrief notes", task.getDebriefNotes());
			assertEquals("Editable preparer", task.getDebriefPreparedByName());
			assertEquals("543.21", task.getVehicleMiles());
			assertTrue(task.isDebriefingCompleted());
			assertEquals("Editable canine type", task.getCanineSearchType());
			assertEquals("Editable day", task.getCanineDayNight());
			assertEquals("Editable clouds", task.getCanineCloudCover());
			assertEquals("Editable wind", task.getCanineWindSpeed());
			assertEquals("Editable clue description", data.getClueLogEntries().get(0).getDescription());
		}
	}

	@Test
	void activeCanineConditionsDoNotPopulateAnOtherwiseBlankDebrief() throws Exception {
		AppData data = data("assigned - on task");
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		task.setDebriefNotes("");
		try (PDDocument pdf = render(data)) {
			String debrief = page(pdf, 2);
			for (String value : List.of("Canine Assignment Conditions", "Canine Type", "Imprint", "Sun Angle",
					"Day/Night", "Cloud Cover", "Wind Speed", "Editable canine type", "Editable wind")) {
				assertFalse(debrief.contains(value), value);
			}
		}
		assertEquals("Editable canine type", task.getCanineSearchType());
		assertEquals("Editable wind", task.getCanineWindSpeed());
	}

	@Test
	void returnedTasksShowActualTimesInTheConfiguredTimezoneEvenBeforeDebriefCompletion() throws Exception {
		AppData data = data("returned");
		try (PDDocument pdf = render(data)) {
			String debrief = page(pdf, 2);
			assertTrue(debrief.contains("2026-02-02"));
			assertTrue(debrief.contains("07:15"));
			assertTrue(debrief.contains("08:45"));
			assertTrue(debrief.contains("Editable debrief notes"));
			for (String value : List.of("Editable Supervisor", "Editable uncovered area", "Editable observed hazard",
					"Editable preparer", "Editable position", "543.21", "81%", "Editable clue description",
					"Editable clue action", "Editable clue follow-up", "Editable imprint", "Qualitative POD")) {
				assertTrue(debrief.contains(value), value);
			}
		}
		assertFalse(data.getSarTaskAssignments().get(0).isDebriefingCompleted());
	}

	@Test
	void unprintedDebriefGlyphsDoNotPreventActiveAssignmentExport() throws Exception {
		AppData data = data("assigned - on task");
		SarTaskAssignment task = data.getSarTaskAssignments().get(0);
		task.setDebriefNotes("Unprinted \uD83D\uDD0E");
		task.setDebriefingSupervisor("Unprinted \uD83D\uDD0E");
		data.getClueLogEntries().get(0).setDescription("Unprinted \uD83D\uDD0E");
		try (PDDocument pdf = render(data)) {
			assertFalse(page(pdf, 2).contains("Unprinted"));
			assertTrue(page(pdf, 1).contains("Planned assignment content"));
		}
		assertEquals("Unprinted \uD83D\uDD0E", task.getDebriefNotes());
	}

	@Test
	void returnedTaskCluesStillReceivePrintedFieldValidation() {
		AppData data = data("returned");
		data.getClueLogEntries().get(0).setDescription("Printed clue \u96EA");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> render(data));
		assertTrue(exception.getMessage().contains("clueLogEntries[1].description"));
		assertTrue(exception.getMessage().contains("U+96EA"));
	}

	@Test
	void narrativePodFlowsBelowTheHeadingWithoutAppendingAPercentageOrLosingText() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			AppData data = new AppData();
			data.getPdfLayoutSettings().setPaperSize(paper);
			data.getPdfLayoutSettings().setPageMarginPoints(54f);
			SarTaskAssignment task = new SarTaskAssignment();
			task.setTaskLifecycleStatus("returned");
			String pod = "Not estimated; directional sign examination only, not a systematic area search. "
					+ "Follow confirmed tracks to the last sign and preserve the entire evidence timeline.";
			task.setReportedPod(pod);
			data.getSarTaskAssignments().add(task);
			try (PDDocument pdf = render(data)) {
				String debrief = page(pdf, 2);
				assertTrue(debrief.contains("REPORTED POD: " + pod));
				assertFalse(debrief.contains("timeline.%"));
				float width = pdf.getPage(1).getMediaBox().getWidth() - 108f;
				float height = pdf.getPage(1).getMediaBox().getHeight() - 108f - 14f;
				assertPrintedInside(glyphs(pdf, 2), "REPORTED POD: " + pod, 54f, 136f + 20f,
						width, 220f + height - 566f - 20f);
				assertEquals(2, pdf.getNumberOfPages());
			}
		}
	}

	@Test
	void numericPodRetainsOnePercentageSuffix() throws Exception {
		for (String pod : List.of("81", "81%", "81.5")) {
			AppData data = data("returned");
			data.getSarTaskAssignments().get(0).setReportedPod(pod);
			try (PDDocument pdf = render(data)) {
				String debrief = page(pdf, 2);
				assertTrue(debrief.contains("REPORTED POD: " + pod.replace("%", "") + "%"));
				assertFalse(debrief.contains("%%"));
			}
		}
	}

	@Test
	void measuredWrappingUsesTheFullWorkAssignmentWidthOnLetterAndA4() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			for (float margin : List.of(36f, 60f)) {
				AppData data = data("returned");
				data.getPdfLayoutSettings().setPaperSize(paper);
				data.getPdfLayoutSettings().setPageMarginPoints(margin);
				String assignment = "ill trail iii litter ".repeat(70).strip();
				data.getSarTaskAssignments().get(0).setAssignment(assignment);
				try (PDDocument pdf = render(data)) {
					assertEquals(2, pdf.getNumberOfPages());
					List<Glyph> glyphs = glyphs(pdf, 1);
					float width = pdf.getPage(0).getMediaBox().getWidth() - 2f * margin;
					float height = pdf.getPage(0).getMediaBox().getHeight() - 2f * margin - 14f;
					float scale = Math.min(1f, height / 660f);
					float top = margin + 14f + (58f + 58f + 176f) * scale;
					float rowHeight = 104f * scale + Math.max(0f, height - 660f);
					assertPrintedInside(glyphs, assignment, margin, top, width, rowHeight);
					List<Glyph> body = textGlyphs(glyphs, assignment);
					List<Float> baselines = body.stream().map(Glyph::y).distinct().toList();
					assertTrue(baselines.size() >= 3);
					for (int index = 0; index < baselines.size() - 1; index++) {
						float baseline = baselines.get(index);
						List<Glyph> line = body.stream().filter(glyph -> glyph.y() == baseline).toList();
						float used = line.get(line.size() - 1).x() + line.get(line.size() - 1).width()
								- line.get(0).x();
						assertTrue(used > (width - 8f) * 0.85f, "Premature wrapping: " + used + "/" + width);
					}
				}
			}
		}
	}

	@Test
	void twoAndAHalfInchMarginsFitEverySectionIncludingBothPreparers() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			AppData data = data("returned");
			data.getPdfLayoutSettings().setPaperSize(paper);
			data.getPdfLayoutSettings().setPageMarginPoints(180f);
			SarTaskAssignment task = data.getSarTaskAssignments().get(0);
			task.setPreparedByName("Front preparer retained");
			task.setPreparedByPositionTitle("Front position retained");
			task.setPreparedDateTime(task.getAssignmentStart());
			task.setOperationsSectionChiefName("Operations name retained");
			task.setOperationsSectionChiefContact("Operations contact retained");
			task.setSecondaryManagementRoleLabel("Deputy");
			task.setSecondaryManagementName("Deputy name retained");
			task.setSecondaryManagementContact("Deputy contact retained");
			task.setTransportationInstructions("Transportation retained");
			task.setTaskMap("Map retained");
			task.setSpecialEquipment("Equipment retained");
			try (PDDocument pdf = render(data)) {
				assertEquals(2, pdf.getNumberOfPages());
				float width = pdf.getPage(0).getMediaBox().getWidth() - 360f;
				float height = pdf.getPage(0).getMediaBox().getHeight() - 360f - 14f;
				List<Glyph> front = glyphs(pdf, 1);
				List<Glyph> debrief = glyphs(pdf, 2);
				for (List<Glyph> page : List.of(front, debrief)) {
					for (Glyph glyph : page) {
						assertTrue(glyph.x() >= 180f - 0.1f
								&& glyph.x() + glyph.width() <= 180f + width + 0.1f,
								"Margin overflow: " + glyph);
						assertTrue(glyph.y() - glyph.height() >= 166f - 0.1f
								&& glyph.y() <= 194f + height + 0.1f,
								"Margin overflow: " + glyph);
					}
				}
				float frontPreparerHeight = 44f * height / 660f;
				float frontPreparerTop = 194f + height - frontPreparerHeight;
				assertPrintedInside(front, task.getPreparedByName(), 180f, frontPreparerTop,
						width * 0.42f, frontPreparerHeight);
				assertPrintedInside(front, task.getPreparedByPositionTitle(), 180f + width * 0.42f,
						frontPreparerTop, width * 0.32f, frontPreparerHeight);
				float debriefPreparerHeight = 48f * height / 566f;
				float debriefPreparerTop = 194f + height - debriefPreparerHeight;
				assertPrintedInside(debrief, task.getDebriefPreparedByName(), 180f, debriefPreparerTop,
						width * 0.42f, debriefPreparerHeight);
				assertPrintedInside(debrief, task.getDebriefPreparedByPositionTitle(), 180f + width * 0.42f,
						debriefPreparerTop, width * 0.32f, debriefPreparerHeight);
				float operationsTop = 194f + 58f * height / 660f;
				float operationsHeight = 58f * height / 660f;
				for (String value : List.of(task.getOperationsSectionChiefName(), task.getSecondaryManagementName())) {
					assertPrintedInside(front, value, 180f, operationsTop, width * 0.78f * 0.58f, operationsHeight);
				}
				for (String value : List.of(task.getAssignment(), task.getTransportationInstructions(), task.getTaskMap(),
						task.getSpecialEquipment(), "Front contact")) {
					assertFalse(textGlyphs(front, value).isEmpty());
				}
				for (String value : List.of(task.getDebriefNotes(), task.getAreasNotCovered(), task.getHazardsObserved(),
						"Editable clue description", "Editable imprint")) {
					assertFalse(textGlyphs(debrief, value).isEmpty());
				}
			}
		}
	}

	@Test
	void longNarrativesTablesAndUnbrokenTokensRemainInsideTheirCellsWithRendererReuse() throws Exception {
		SarTaskAssignmentPdfRenderer renderer = new SarTaskAssignmentPdfRenderer();
		for (PaperSize paper : PaperSize.values()) {
			AppData data = data("returned");
			data.getPdfLayoutSettings().setPaperSize(paper);
			data.getPdfLayoutSettings().setPageMarginPoints(54f);
			SarTaskAssignment task = data.getSarTaskAssignments().get(0);
			task.setAssignment("Canine search assignment " + "WWWWW broad slopes narrow trails ".repeat(65) + " ASSIGNEND");
			task.setTransportationInstructions("Transport " + "W".repeat(220) + " TRANSPORTEND");
			task.setTaskMap("Map reference " + "M".repeat(240) + " MAPEND");
			task.setSpecialEquipment("Equipment " + "W".repeat(180) + " EQUIPMENTEND");
			task.setAreasNotCovered("Uncovered " + "Dense vegetation ".repeat(110) + " AREAEND");
			task.setHazardsObserved("Hazards " + "Wide cliffs ".repeat(95) + " HAZARDEND");
			String timeline = "07:00 departed; 07:05 on task; 07:50 complete and returning; 08:00 returned; "
					+ "08:10 debriefed. " + "Tracker timeline and evidence ".repeat(180) + " TIMELINEEND";
			task.setDebriefNotes(timeline);
			SarTaskResource resource = new SarTaskResource();
			resource.setFunction("ResourceFunction" + "W".repeat(75));
			resource.setName("ResourceName" + "M".repeat(90));
			task.setResourcesAssigned(List.of(resource));
			CommunicationEntry contact = new CommunicationEntry();
			contact.setName("ContactName" + "W".repeat(65));
			contact.setFunction("ContactFunction" + "M".repeat(60));
			contact.setPrimaryContact("ContactNumber" + "9".repeat(160));
			task.setCommunications(List.of(contact));
			try (PDDocument pdf = render(renderer, data)) {
				assertEquals(2, pdf.getNumberOfPages());
				float width = pdf.getPage(0).getMediaBox().getWidth() - 108f;
				float pageHeight = pdf.getPage(0).getMediaBox().getHeight();
				float workHeight = 104f + pageHeight - 108f - 14f - 660f;
				float top = 68f + 58f + 58f + 176f;
				List<Glyph> front = glyphs(pdf, 1);
				assertPrintedInside(front, task.getAssignment(), 54f, top, width, workHeight);
				top += workHeight;
				assertPrintedInside(front, task.getTransportationInstructions(), 54f, top, width, 42f);
				top += 42f;
				assertPrintedInside(front, task.getTaskMap(), 54f, top, width * 0.68f, 104f);
				assertPrintedInside(front, task.getSpecialEquipment(), 54f + width * 0.68f, top, width * 0.32f, 104f);
				top += 104f;
				float contactTop = top + 34f;
				float contactHeight = (74f - 34f) / 3f;
				assertPrintedInside(front, contact.getName(), 54f, contactTop, width * 0.30f, contactHeight);
				assertPrintedInside(front, contact.getFunction(), 54f + width * 0.30f, contactTop,
						width * 0.30f, contactHeight);
				assertPrintedInside(front, contact.getPrimaryContact(), 54f + width * 0.60f, contactTop,
						width * 0.40f, contactHeight);
				float resourceTop = 68f + 58f + 58f + 63f;
				float resourceHeight = (176f - 63f) / 9f;
				assertPrintedInside(front, resource.getFunction(), 54f, resourceTop, width * 0.16f, resourceHeight);
				assertPrintedInside(front, resource.getName(), 54f + width * 0.16f, resourceTop,
						width * 0.34f, resourceHeight);
				List<Glyph> debrief = glyphs(pdf, 2);
				float debriefHeight = 220f + pageHeight - 108f - 14f - 566f;
				assertPrintedInside(debrief, timeline, 54f, 68f + 68f, width, debriefHeight);
				assertPrintedInside(debrief, "Editable clue description", 54f, 68f + 68f, width, debriefHeight);
				assertPrintedInside(debrief, task.getAreasNotCovered(), 54f, 136f + debriefHeight, width, 140f);
				assertPrintedInside(debrief, task.getHazardsObserved(), 54f, 276f + debriefHeight, width, 90f);
				for (Glyph glyph : front) {
					assertTrue(glyph.x() >= 54f - 0.1f && glyph.x() + glyph.width() <= 54f + width + 0.1f);
					assertTrue(glyph.y() <= pageHeight - 54f + 0.1f);
				}
			}
		}
	}

	/**
	 * Builds an assignment whose front-page period equals its prospective actual times.
	 *
	 * @param lifecycle requested task state.
	 * @return source document with editable debrief content and a fixed export timezone.
	 */
	private AppData data(String lifecycle) {
		AppData data = new AppData();
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("America/New_York");
		LocalDateTime start = LocalDateTime.of(2026, 2, 2, 12, 15);
		LocalDateTime end = LocalDateTime.of(2026, 2, 2, 13, 45);
		data.getIncidentContext().setOperationalPeriodStart(start);
		data.getIncidentContext().setOperationalPeriodEnd(end);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setTaskLifecycleStatus(lifecycle);
		task.setAssignmentStart(start);
		task.setAssignmentEnd(end);
		task.setAssignmentTeamNumber("Identity-17");
		task.setAssignmentId("assignment-17");
		task.setAssignment("Planned assignment content");
		CommunicationEntry contact = new CommunicationEntry();
		contact.setName("Front contact");
		task.setCommunications(List.of(contact));
		task.setDebriefingSupervisor("Editable Supervisor");
		task.setDebriefNotes("Editable debrief notes");
		task.setAreasNotCovered("Editable uncovered area");
		task.setHazardsObserved("Editable observed hazard");
		task.setVehicleMiles("543.21");
		task.setReportedPod("81");
		task.setDebriefPreparedByName("Editable preparer");
		task.setDebriefPreparedByPositionTitle("Editable position");
		task.setDebriefPreparedDateTime(end);
		task.setResourceType(SarTaskSupport.RESOURCE_TYPE_CANINE);
		task.setCanineSearchType("Editable canine type");
		task.setCanineImprint("Editable imprint");
		task.setCanineSunAngle("Editable sun");
		task.setCanineDayNight("Editable day");
		task.setCanineCloudCover("Editable clouds");
		task.setCanineWindSpeed("Editable wind");
		List<PodFactorRating> factors = SarTaskSupport.factorRatings(task.getResourceType(), List.of());
		factors.get(0).setScore(7);
		factors.get(0).setDescription("Editable factor description");
		task.setQualitativePodFactors(factors);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setAssignmentId("assignment-17");
		clue.setDescription("Editable clue description");
		clue.setLocation("Editable clue location");
		clue.setImmediateAction("Editable clue action");
		clue.setFollowUp("Editable clue follow-up");
		clue.setPossibleDuplicate(true);
		clue.setDateTimeCollected(end);
		data.getClueLogEntries().add(clue);
		data.getSarTaskAssignments().add(task);
		return data;
	}

	/**
	 * Renders and loads an assignment without retaining its intermediate PDF.
	 *
	 * @param data export document.
	 * @return loaded PDF owned by the caller.
	 * @throws IOException when rendering or loading fails.
	 */
	private PDDocument render(AppData data) throws IOException {
		return render(new SarTaskAssignmentPdfRenderer(), data);
	}

	/**
	 * Exports with a reusable renderer and removes the intermediate PDF.
	 * @param renderer renderer instance under test.
	 * @param data export document.
	 * @return loaded PDF owned by the caller.
	 * @throws IOException when rendering or loading fails.
	 */
	private PDDocument render(SarTaskAssignmentPdfRenderer renderer, AppData data) throws IOException {
		Path output = Path.of("target", "test-output", "sar-lifecycle", "form-" + System.nanoTime() + ".pdf");
		try {
			renderer.render(data, output);
			return Loader.loadPDF(Files.readAllBytes(output));
		} finally {
			Files.deleteIfExists(output);
		}
	}

	/**
	 * Extracts one page with normalized whitespace.
	 *
	 * @param document source PDF.
	 * @param page one-based page number.
	 * @return extracted page text.
	 * @throws IOException when extraction fails.
	 */
	private String page(PDDocument document, int page) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		stripper.setStartPage(page);
		stripper.setEndPage(page);
		return stripper.getText(document).replaceAll("\\s+", " ");
	}

	/**
	 * Captures glyph bounds in PDF drawing order for one page.
	 * @param document source PDF.
	 * @param page one-based page number.
	 * @return nonwhitespace glyphs and their page coordinates.
	 * @throws IOException when extracting fails.
	 */
	private List<Glyph> glyphs(PDDocument document, int page) throws IOException {
		GlyphStripper stripper = new GlyphStripper();
		stripper.setStartPage(page);
		stripper.setEndPage(page);
		stripper.getText(document);
		return stripper.glyphs;
	}

	/**
	 * Locates complete expected content independently of inserted wrap whitespace.
	 * @param glyphs page glyphs in drawing order.
	 * @param text complete expected content.
	 * @return glyphs composing the expected content.
	 */
	private List<Glyph> textGlyphs(List<Glyph> glyphs, String text) {
		String expected = text.replaceAll("\\s+", "");
		String actual = glyphs.stream().map(Glyph::text).collect(java.util.stream.Collectors.joining());
		int start = actual.indexOf(expected);
		assertTrue(start >= 0, "Missing content: " + text.substring(0, Math.min(80, text.length())));
		return glyphs.subList(start, start + expected.length());
	}

	/**
	 * Checks full content preservation and every printed glyph against its logical cell.
	 * @param glyphs page glyphs.
	 * @param text expected content.
	 * @param x cell left edge.
	 * @param top cell top in top-down PDF text coordinates.
	 * @param width cell width.
	 * @param height cell height.
	 */
	private void assertPrintedInside(List<Glyph> glyphs, String text, float x, float top, float width, float height) {
		for (Glyph glyph : textGlyphs(glyphs, text)) {
			assertTrue(glyph.x() >= x - 0.1f && glyph.x() + glyph.width() <= x + width + 0.1f,
					"Horizontal cell overflow: " + glyph);
			assertTrue(glyph.y() - glyph.height() >= top - 0.1f && glyph.y() <= top + height + 0.1f,
					"Vertical cell overflow: " + glyph);
		}
	}

	/** Collects glyph geometry before PDFTextStripper groups or sorts text. */
	private static class GlyphStripper extends PDFTextStripper {
		private final List<Glyph> glyphs = new ArrayList<>();

		/**
		 * Creates a geometry collector.
		 * @throws IOException when the PDF text engine cannot initialize.
		 */
		private GlyphStripper() throws IOException {
			super();
		}

		/**
		 * Records each visible glyph in source drawing order.
		 * @param position decoded glyph and its geometry.
		 */
		@Override
		protected void processTextPosition(TextPosition position) {
			String text = position.getUnicode();
			if (!text.isBlank()) {
				glyphs.add(new Glyph(text, position.getXDirAdj(), position.getYDirAdj(),
						position.getWidthDirAdj(), position.getHeightDir()));
			}
			super.processTextPosition(position);
		}
	}

	/**
	 * A visible PDF glyph and its top-down page bounds.
	 * @param text glyph text.
	 * @param x left edge.
	 * @param y baseline.
	 * @param width advance width.
	 * @param height glyph height.
	 */
	private record Glyph(String text, float x, float y, float width, float height) {
	}
}
