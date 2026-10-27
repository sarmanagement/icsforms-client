/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.PdfLayoutSettings.PaperSize;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Covers IAP page advancement past measured multipage objectives, assignments and charts. */
class Ics207IapPaginationTest {
	@Test
	void objectivesAndAssignmentsAdvanceLaterFormsByTheirActualRenderedPages() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			AppData data = longAssignmentIncident(paper);
			Path directory = Path.of("target", "test-output", "measured-iap", paper.name());
			Files.createDirectories(directory);
			int objectivePages = renderedPages(new Ics202PdfRenderer(), data, directory.resolve("202.pdf"));
			int assignmentPages = renderedPages(new Ics204PdfRenderer(), data, directory.resolve("204.pdf"));
			assertTrue(objectivePages > 1);
			assertTrue(assignmentPages > 1);
			assertEquals(objectivePages, Ics202PdfRenderer.pageCount(data));
			assertEquals(assignmentPages, Ics204PdfRenderer.pageCount(data));
			List<String> selected = List.of("ICS 202", "ICS 204", "ICS 206");
			PdfExportService.assignIapPageNumbers(data, selected, true);
			assertEquals("1", data.getForm202().getIapPage());
			assertEquals(String.valueOf(1 + objectivePages), data.getForm204().getIapPage());
			assertEquals(String.valueOf(1 + objectivePages + assignmentPages), data.getMedicalPlan().getIapPage());
			PdfExportService service = new PdfExportService(new Ics202PdfRenderer(), new Ics204PdfRenderer(),
					new Ics206PdfRenderer());
			Path bundle = service.exportIapBundle(data, directory, selected);
			try (PDDocument pdf = Loader.loadPDF(bundle.toFile())) {
				assertEquals(1 + objectivePages + assignmentPages + Ics206PdfRenderer.pageCount(data),
						pdf.getNumberOfPages());
				PDFTextStripper stripper = new PDFTextStripper();
				int medicalPhysicalPage = 2 + objectivePages + assignmentPages;
				stripper.setStartPage(medicalPhysicalPage);
				stripper.setEndPage(medicalPhysicalPage);
				String text = stripper.getText(pdf).replaceAll("\\s+", " ");
				assertTrue(text.contains("MEDICAL PLAN"));
				assertTrue(text.contains("IAP Page " + (medicalPhysicalPage - 1)), text);
			}
		}
	}

	@Test
	void additionalAssignmentCountsUseTheirOwnContentWithoutReplacingThePrimaryForm() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			AppData data = longAssignmentIncident(paper);
			Ics204Form primary = data.getForm204();
			primary.setIapPage("Original primary label");
			Ics204Form additional = new Ics204Form();
			additional.setSpecialInstructions("Additional assignment instructions. ".repeat(600));
			Ics204Form blank = new Ics204Form();
			data.getAdditionalForms204().addAll(List.of(additional, blank));
			int primaryPages = Ics204PdfRenderer.pageCount(data);
			int additionalPages = Ics204PdfRenderer.pageCount(data, additional);
			int blankPages = Ics204PdfRenderer.pageCount(data, blank);
			assertSame(primary, data.getForm204());
			assertEquals("Original primary label", primary.getIapPage());
			assertTrue(additionalPages > blankPages);
			AppData independent = new AppData();
			independent.getPdfLayoutSettings().setPaperSize(paper);
			independent.setForm204(additional);
			Path directory = Path.of("target", "test-output", "measured-iap", paper.name());
			Files.createDirectories(directory);
			assertEquals(additionalPages,
					renderedPages(new Ics204PdfRenderer(), independent, directory.resolve("additional204.pdf")));
			PdfExportService.assignIapPageNumbers(data, List.of("ICS 204", "ICS 206"), true);
			assertSame(primary, data.getForm204());
			assertEquals("1", primary.getIapPage());
			assertEquals(String.valueOf(1 + primaryPages), additional.getIapPage());
			assertEquals(String.valueOf(1 + primaryPages + additionalPages), blank.getIapPage());
			assertEquals(String.valueOf(1 + primaryPages + additionalPages + blankPages),
					data.getMedicalPlan().getIapPage());
		}
	}

	/**
	 * Builds long objective and assignment content for either supported paper size.
	 * @param paper requested paper size.
	 * @return incident requiring objective and resource-table continuations.
	 */
	private AppData longAssignmentIncident(PaperSize paper) {
		AppData data = new AppData();
		data.getPdfLayoutSettings().setPaperSize(paper);
		data.getForm202().setObjectives(List.of("Search every assigned drainage safely. ".repeat(700)));
		for (int index = 0; index < 18; index++) {
			ResourceAssignment resource = new ResourceAssignment();
			resource.setResourceIdentifier("Resource " + index);
			resource.setLeader("Assignment Leader " + index);
			resource.setReportingLocation("Fictional staging area, check in before deployment. ".repeat(3));
			data.getForm204().getResourcesAssigned().add(resource);
		}
		return data;
	}

	/**
	 * Renders a form and reads its physical page count independently of its counter.
	 * @param renderer renderer under test.
	 * @param data incident to render.
	 * @param file destination inside the build directory.
	 * @return physical number of exported pages.
	 * @throws Exception when rendering or reading fails.
	 */
	private int renderedPages(PdfFormRenderer renderer, AppData data, Path file) throws Exception {
		renderer.render(data, file);
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			return pdf.getNumberOfPages();
		}
	}

	@Test
	void fortyTaskOrganizationChartAdvancesLaterFormPagesByItsCompleteMeasuredCount() throws Exception {
		for (PaperSize paper : PaperSize.values()) {
			AppData data = new AppData();
			data.getPdfLayoutSettings().setPaperSize(paper);
			for (int index = 0; index < 40; index++) {
				SarTaskAssignment task = new SarTaskAssignment();
				task.setResourceIdentifier(String.format("Assignment %02d", index));
				task.setLeader(String.format("Task Leader %02d", index));
				data.getSarTaskAssignments().add(task);
			}
			Ics214Form activity = new Ics214Form();
			data.getActivityLogs().add(activity);
			int chartPages = Ics207PdfRenderer.pageCount(data);
			int safetyPages = Ics208PdfRenderer.pageCount(data);
			assertTrue(chartPages > 1);
			List<String> selected = List.of("ICS 214", "ICS 207", "ICS 208");
			PdfExportService.assignIapPageNumbers(data, selected, true);
			assertEquals("1", data.getForm207().getIapPage());
			assertEquals(String.valueOf(1 + chartPages), data.getForm208().getIapPage());
			assertEquals(String.valueOf(1 + chartPages + safetyPages), activity.getIapPage());
			PdfExportService service = new PdfExportService(new Ics207PdfRenderer(), new Ics208PdfRenderer(),
					new Ics214PdfRenderer());
			Path directory = Path.of("target", "test-output", "ics207-iap", paper.name());
			Path bundle = service.exportIapBundle(data, directory, selected);
			try (PDDocument pdf = Loader.loadPDF(bundle.toFile())) {
				assertEquals(1 + chartPages + safetyPages + Ics214PdfRenderer.pageCount(activity),
						pdf.getNumberOfPages());
				PDFTextStripper stripper = new PDFTextStripper();
				stripper.setStartPage(1 + chartPages);
				stripper.setEndPage(1 + chartPages);
				String finalChart = stripper.getText(pdf).replaceAll("\\s+", " ");
				assertTrue(finalChart.contains("INCIDENT ORGANIZATION CHART (ICS 207) Page " + chartPages));
				assertTrue(finalChart.contains("IAP Page " + chartPages));
				stripper.setStartPage(2 + chartPages + safetyPages);
				stripper.setEndPage(2 + chartPages + safetyPages);
				String activityText = stripper.getText(pdf).replaceAll("\\s+", " ");
				assertTrue(activityText.contains("IAP Page " + activity.getIapPage()));
			} finally {
				Files.deleteIfExists(bundle);
			}
		}
	}
}
