package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for ICS 214 PDF rendering.
 */
class Ics214PdfRendererTest {

	@Test
	void blankFormRendersToNonEmptyPdf() throws Exception {
		Path outputDir = Path.of("target", "test-output", "ics214");
		Files.createDirectories(outputDir);
		Path outputFile = outputDir.resolve("blank-ics-214-" + System.nanoTime() + ".pdf");

		new Ics214PdfRenderer().render(new AppData(), outputFile);

		assertTrue(Files.exists(outputFile));
		assertTrue(Files.size(outputFile) > 0L);
	}

	@Test
	void formWithCustomEventTypeRendersToNonEmptyPdf() throws Exception {
		Path outputDir = Path.of("target", "test-output", "ics214");
		Files.createDirectories(outputDir);
		Path outputFile = outputDir.resolve("custom-event-ics-214-" + System.nanoTime() + ".pdf");

		AppData data = new AppData();
		List<ActivityEventType> types = new ArrayList<>(ActivityEventType.defaultTypes());
		ActivityEventType custom = new ActivityEventType("AERIAL_SEARCH", "Aerial Search Completed", false);
		types.add(custom);
		data.setActivityEventTypes(types);

		Ics214Form form = new Ics214Form();
		ActivityLogEntry entry1 = new ActivityLogEntry();
		entry1.setTimestamp(LocalDateTime.now());
		entry1.setEventTypeId("AERIAL_SEARCH");
		entry1.setNotableActivity("Grid 42 completed with no find.");
		form.getActivityLog().add(entry1);

		ActivityLogEntry entry2 = new ActivityLogEntry();
		entry2.setTimestamp(LocalDateTime.now().minusHours(1));
		entry2.setEventTypeId(ActivityEventType.ID_CLUE_DETECTED);
		entry2.setNotableActivity("Boot print near creek.");
		form.getActivityLog().add(entry2);

		data.getActivityLogs().add(form);

		new Ics214PdfRenderer().render(data, outputFile);

		assertTrue(Files.exists(outputFile));
		assertTrue(Files.size(outputFile) > 0L);
		try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(outputFile.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains("ICS 214"));
			assertFalse(text.contains("ICS 214, Page 1 of 1"));
		}
	}

	@Test
	void multipageFormOffsetsIapPagePerRenderedPage() throws Exception {
		Path outputDir = Path.of("target", "test-output", "ics214");
		Files.createDirectories(outputDir);
		Path outputFile = outputDir.resolve("multipage-ics-214-" + System.nanoTime() + ".pdf");

		AppData data = new AppData();
		Ics214Form form = new Ics214Form();
		form.setPreparedByName("Planner");
		form.setPreparedByPositionTitle("Planning");
		form.setPreparedBySignature("Planner");
		form.setPreparedDateTime(LocalDateTime.parse("2026-01-01T08:00:00"));
		form.setIapPage("7");
		for (int i = 0; i < 20; i++) {
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setTimestamp(LocalDateTime.parse("2026-01-01T08:00:00").plusMinutes(i));
			entry.setNotableActivity("Activity " + i);
			form.getActivityLog().add(entry);
		}
		data.getActivityLogs().add(form);

		new Ics214PdfRenderer().render(data, outputFile);

		try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(outputFile.toFile())) {
			assertEquals(2, pdf.getNumberOfPages());
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			stripper.setEndPage(1);
			String firstPageText = stripper.getText(pdf).replaceAll("\\s+", " ");
			stripper.setStartPage(2);
			stripper.setEndPage(2);
			String secondPageText = stripper.getText(pdf).replaceAll("\\s+", " ");
			assertTrue(firstPageText.contains("ICS 214, Page 1 of 2"));
			assertTrue(firstPageText.contains("IAP Page 7") || firstPageText.contains("IAP Page: 7"));
			assertTrue(secondPageText.contains("ICS 214, Page 2 of 2"));
			assertTrue(secondPageText.contains("IAP Page 8") || secondPageText.contains("IAP Page: 8"));
		}
	}

	@Test
	void taskLinkedEntriesRenderCanonicalResourceLabel() throws Exception {
		Path outputDir = Path.of("target", "test-output", "ics214");
		Files.createDirectories(outputDir);
		Path outputFile = outputDir.resolve("task-resource-ics-214-" + System.nanoTime() + ".pdf");
		AppData data = sampleTaskLinkedData(false);

		new Ics214PdfRenderer().render(data, outputFile);

		try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(outputFile.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains("Resource: 1: Team Frodo"));
		}
	}

	@Test
	void struckOutEntriesAddStrikeThroughLinesToPdf() throws Exception {
		Path outputDir = Path.of("target", "test-output", "ics214");
		Files.createDirectories(outputDir);
		Path plainOutput = outputDir.resolve("plain-ics-214-" + System.nanoTime() + ".pdf");
		Path struckOutput = outputDir.resolve("struck-ics-214-" + System.nanoTime() + ".pdf");

		new Ics214PdfRenderer().render(sampleTaskLinkedData(false), plainOutput);
		new Ics214PdfRenderer().render(sampleTaskLinkedData(true), struckOutput);

		try (org.apache.pdfbox.pdmodel.PDDocument plainPdf = Loader.loadPDF(plainOutput.toFile());
				org.apache.pdfbox.pdmodel.PDDocument struckPdf = Loader.loadPDF(struckOutput.toFile())) {
			assertTrue(countLineToOperators(struckPdf.getPage(0)) > countLineToOperators(plainPdf.getPage(0)));
		}
	}

	private static AppData sampleTaskLinkedData(boolean struckOut) {
		AppData data = new AppData();
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assign-1");
		task.setAssignmentTeamNumber("1");
		task.setResourceIdentifier("Team Frodo");
		data.setSarTaskAssignments(List.of(task));

		Ics214Form form = new Ics214Form();
		form.setLinkedSarTaskAssignmentId("assign-1");
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setTimestamp(LocalDateTime.parse("2026-01-01T08:30:00"));
		entry.setEventTypeId(ActivityEventType.ID_RESOURCE_ON_TASK);
		entry.setResourceIdentifier("Team Frodo");
		entry.setNotableActivity("On task");
		entry.setStruckOut(struckOut);
		form.getActivityLog().add(entry);
		data.getActivityLogs().add(form);
		return data;
	}

	private static int countLineToOperators(PDPage page) throws Exception {
		int count = 0;
		try (InputStream inputStream = page.getContents()) {
			PDFStreamParser parser = new PDFStreamParser(inputStream.readAllBytes());
			for (Object token : parser.parse()) {
				if (token instanceof Operator operator && "l".equals(operator.getName())) {
					count++;
				}
			}
		}
		return count;
	}
}
