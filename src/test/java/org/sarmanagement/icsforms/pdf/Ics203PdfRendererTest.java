/*
 * Copyright (C) 2026 SAR Management
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics203Form;
import org.sarmanagement.icsforms.model.Ics203Form.Branch;
import org.sarmanagement.icsforms.model.Ics203Form.Position;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.OrgChartEntry.Section;
import org.sarmanagement.icsforms.model.PdfLayoutSettings.PaperSize;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the independent ICS 203 assignment-list renderer. */
class Ics203PdfRendererTest {
	@Test
	void blankAndNullDocumentsHaveAllNumberedBlocksAndEmptyLabeledRows() throws Exception {
		assertEquals(1, Ics203PdfRenderer.pageCount(null));
		try (PDDocument pdf = render(null)) {
			assertEquals(1, pdf.getNumberOfPages());
			String text = text(pdf);
			for (String label : List.of("ICS 203", "ORGANIZATION ASSIGNMENT LIST", "1. Incident Name",
					"2. Operational Period", "3. Incident Commander(s) and Command Staff",
					"4. Agency/Organization Representatives", "5. Planning Section", "6. Logistics Section",
					"7. Operations Section", "8. Finance/Administration Section", "9. Prepared By",
					"Incident Commander", "Deputy", "Safety Officer", "Public Information Officer", "Liaison Officer",
					"Agency/Organization", "Resources Unit Leader", "Situation Unit Leader", "Documentation Unit Leader",
					"Demobilization Unit Leader", "Technical Specialists", "Support Branch", "Supply Unit Leader",
					"Facilities Unit Leader", "Ground Support Unit Leader", "Service Branch", "Communications Unit Leader",
					"Medical Unit Leader", "Food Unit Leader", "Staging Area Manager", "Division/Group",
					"Air Operations Branch", "Time Unit Leader", "Procurement Unit Leader",
					"Compensation/Claims Unit Leader", "Cost Unit Leader", "Signature:", "IAP Page", "Page 1 of 1")) {
				assertTrue(text.contains(label), label);
			}
			assertEquals(9, occurrences(text, "Division/Group"));
		}
	}

	@Test
	void mapsAllSharedAndSupplementalRolesAndPreservesTypedNames() throws Exception {
		AppData data = new AppData();
		List<String> names = new ArrayList<>();
		String[] fields = {"SafetyOfficerName", "PublicInformationOfficerName", "LiaisonOfficerName",
				"OperationsSectionChiefName", "PlanningSectionChiefName", "LogisticsSectionChiefName",
				"FinanceAdminSectionChiefName", "DocumentationUnitLeaderName", "ResourcesUnitLeaderName",
				"SituationUnitLeaderName", "DemobilizationUnitLeaderName", "StagingAreaManagerName",
				"CommunicationsUnitLeaderName", "CommunicationsTechnicianName", "SupplyUnitLeaderName",
				"FacilitiesUnitLeaderName", "GroundSupportUnitLeaderName", "FoodUnitLeaderName", "TimeUnitLeaderName",
				"ProcurementUnitLeaderName", "CompClaimsUnitLeaderName", "CostUnitLeaderName"};
		for (int i = 0; i < fields.length; i++) {
			String name = "Person" + i + " / Trainee (T)";
			Method setter = data.getOrganizationalChart().getClass().getMethod("set" + fields[i], String.class);
			setter.invoke(data.getOrganizationalChart(), name);
			names.add(name);
		}
		Ics203Form form = data.getOrganizationalChart().getAssignmentList();
		for (Position position : Position.values()) {
			String name = "Supplement" + position.ordinal();
			form.setPosition(position, name);
			names.add(name);
		}
		form.setPreparedByName("Elizabeth Montgomery");
		form.setPreparedByPositionTitle("Planning Section Chief");
		form.setPreparedBySignature("Signed Elizabeth (T)");
		form.setIapPage("12");
		form.getAgencyRepresentatives().add(entry("County Rescue", "Agency Representative"));
		Branch branch = new Branch();
		branch.setName("Ground Branch");
		branch.setDirector("Branch Director");
		branch.setDeputy("Branch Deputy");
		branch.getDivisions().add(entry("North Group", "Division Supervisor"));
		form.getBranches().add(branch);
		data.getOrganizationalChart().setIncidentCommanders(List.of("Legacy Commander"));
		data.getOrganizationalChart().setIncidentCommanderEntries(
				List.of(entry("", "Modern / (T)"), entry("", "Partner")));
		try (PDDocument pdf = render(data)) {
			String text = text(pdf);
			for (String name : names) {
				assertTrue(text.contains(name), name);
			}
			for (String value : List.of("Unified Command", "Modern / (T)", "Partner",
					"Elizabeth Montgomery", "Planning Section Chief", "Signed Elizabeth (T)", "IAP Page 12",
					"County Rescue", "Agency Representative", "Ground Branch", "Branch Director", "Branch Deputy",
					"North Group", "Division Supervisor")) {
				assertTrue(text.contains(value), value);
			}
			assertFalse(text.contains("Legacy Commander"));
		}
		data.getOrganizationalChart().setIncidentCommanderEntries(List.of(entry("", "")));
		try (PDDocument pdf = render(data)) {
			assertTrue(text(pdf).contains("Legacy Commander"));
		}
	}

	@Test
	void fallbackUsesActualDivisionsGroupsAndIcs204SupervisorsBeforeTaskLeaders() throws Exception {
		AppData data = new AppData();
		data.getForm204().setDivision("Division North");
		data.getForm204().setDivisionGroupSupervisorName("ICS204 Supervisor");
		Ics204Form additional = new Ics204Form();
		additional.setGroup("Water");
		additional.setDivisionGroupSupervisorName("Water Supervisor");
		data.getAdditionalForms204().add(additional);
		SarTaskAssignment duplicate = new SarTaskAssignment();
		duplicate.setDivision("north");
		duplicate.setLeader("Wrong Task Leader");
		SarTaskAssignment task = new SarTaskAssignment();
		task.setGroup("Ridge");
		task.setLeader("Task Leader");
		task.setResourceIdentifier("Not a Division");
		SarTaskAssignment unlabeled = new SarTaskAssignment();
		unlabeled.setResourceIdentifier("Identifier Only");
		unlabeled.setLeader("Unlabeled Leader");
		data.getSarTaskAssignments().addAll(List.of(duplicate, task, unlabeled));
		data.getOrganizationalChart().getAssignmentList().getBranches().add(new Branch());
		try (PDDocument pdf = render(data)) {
			assertEquals(1, pdf.getNumberOfPages());
			String text = text(pdf);
			for (String expected : List.of("Division North", "ICS204 Supervisor", "Group Water",
					"Water Supervisor", "Group Ridge", "Task Leader")) {
				assertTrue(text.contains(expected), expected);
			}
			for (String absent : List.of("Wrong Task Leader", "Not a Division", "Identifier Only", "Unlabeled Leader")) {
				assertFalse(text.contains(absent), absent);
			}
			assertEquals(1, occurrences(text, "Division North"));
		}
		Branch explicit = new Branch();
		explicit.setDeputy("Explicit Deputy");
		data.getOrganizationalChart().getAssignmentList().getBranches().add(explicit);
		try (PDDocument pdf = render(data)) {
			String text = text(pdf);
			assertTrue(text.contains("Explicit Deputy"));
			assertFalse(text.contains("ICS204 Supervisor"));
			assertFalse(text.contains("Task Leader"));
		}
	}

	@Test
	void customRolesOccupyTheirProperTwoColumnBlocks() throws Exception {
		AppData data = new AppData();
		OrgChartEntry specialist = entry("Technical Specialists", "Specialist Person");
		specialist.setSection(Section.OPERATIONS);
		OrgChartEntry technician = entry("Communications Technician", "Radio Person");
		technician.setSection(Section.PLANNING);
		data.getOrganizationalChart().getAdditionalPositions().addAll(List.of(specialist, technician));
		for (Section section : Section.values()) {
			OrgChartEntry custom = entry("Custom " + section.ordinal(), "CustomPerson" + section.ordinal());
			custom.setSection(section);
			data.getOrganizationalChart().getAdditionalPositions().add(custom);
		}
		try (PDDocument pdf = render(data)) {
			Positions positions = new Positions();
			positions.getText(pdf);
			float planning = positions.y("5. Planning Section");
			float logistics = positions.y("6. Logistics Section");
			assertTrue(positions.y("Specialist Person") > planning);
			assertTrue(positions.y("Specialist Person") < logistics);
			assertTrue(positions.y("Radio Person") > logistics);
			assertTrue(positions.x("5. Planning Section") < 306f);
			assertTrue(positions.x("7. Operations Section") > 306f);
			assertTrue(positions.x("8. Finance/Administration Section") > 306f);
			assertTrue(positions.x("Specialist Person") < 306f);
			assertTrue(positions.x("CustomPerson1") > 306f);
			String text = text(pdf);
			for (Section section : Section.values()) {
				assertTrue(text.contains("CustomPerson" + section.ordinal()));
			}
		}
	}

	@Test
	void overflowRetainsBranchesRepresentativesAndLongUnbrokenTextOnRepeatedForms() throws Exception {
		AppData data = new AppData();
		Ics203Form form = data.getOrganizationalChart().getAssignmentList();
		for (int i = 0; i < 20; i++) {
			form.getAgencyRepresentatives().add(entry("Agency" + i, "Representative" + i));
			Branch branch = new Branch();
			branch.setName("Branch" + i);
			branch.setDirector("Director" + i);
			branch.setDeputy("Deputy" + i);
			for (int j = 0; j < 5; j++) {
				branch.getDivisions().add(entry("Division" + i + "-" + j, "Supervisor" + i + "-" + j));
			}
			form.getBranches().add(branch);
		}
		String longName = "$".repeat(900) + " ENDMARKER";
		data.getOrganizationalChart().setSafetyOfficerName(longName);
		OrgChartEntry custom = entry("Long Custom", "Q".repeat(700) + " CUSTOMEND");
		custom.setSection(Section.PLANNING);
		data.getOrganizationalChart().getAdditionalPositions().add(custom);
		form.setPreparedByName("%".repeat(800) + " PREPAREDEND");
		try (PDDocument pdf = render(data)) {
			assertEquals(Ics203PdfRenderer.pageCount(data), pdf.getNumberOfPages());
			assertTrue(pdf.getNumberOfPages() > 1);
			String text = text(pdf);
			assertTrue(text.contains("ENDMARKER"));
			assertTrue(text.contains("CUSTOMEND"));
			assertTrue(text.contains("PREPAREDEND"));
			assertEquals(900, occurrences(text, "$"));
			assertEquals(700, occurrences(text, "Q"));
			assertEquals(800, occurrences(text, "%"));
			for (int i = 0; i < 20; i++) {
				assertTrue(text.contains("Agency" + i));
				assertTrue(text.contains("Representative" + i));
				assertTrue(text.contains("Director" + i));
				assertTrue(text.contains("Deputy" + i));
				for (int j = 0; j < 5; j++) {
					assertTrue(text.contains("Division" + i + "-" + j));
					assertTrue(text.contains("Supervisor" + i + "-" + j));
				}
			}
			for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
				PDFTextStripper stripper = new PDFTextStripper();
				stripper.setStartPage(i);
				stripper.setEndPage(i);
				String page = stripper.getText(pdf);
				assertTrue(page.contains("Page " + i + " of " + pdf.getNumberOfPages()));
				for (int block = 3; block <= 9; block++) {
					assertTrue(page.contains(block + ". "), "block " + block + " on page " + i);
				}
			}
		}
	}

	@Test
	void a4LargeMarginsAndConfiguredTimezoneKeepAllContentInsideForm() throws Exception {
		AppData data = new AppData();
		data.getPdfLayoutSettings().setPaperSize(PaperSize.A4);
		data.getPdfLayoutSettings().setPageMarginPoints(108f);
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("America/New_York");
		data.getIncidentContext().setIncidentName("A4 Incident");
		data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.of(2026, 1, 1, 1, 30));
		data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.of(2026, 1, 1, 8, 45));
		data.getOrganizationalChart().getAssignmentList().setPreparedDateTime(LocalDateTime.of(2026, 1, 1, 2, 30));
		try (PDDocument pdf = render(data)) {
			assertEquals(PDRectangle.A4.getWidth(), pdf.getPage(0).getMediaBox().getWidth());
			assertEquals(1, pdf.getNumberOfPages());
			assertEquals(Ics203PdfRenderer.pageCount(data), pdf.getNumberOfPages());
			String text = text(pdf);
			assertTrue(text.contains("2025-12-31 20:30"));
			assertTrue(text.contains("2026-01-01 03:45"));
			assertTrue(text.contains("2025-12-31 21:30"));
			Positions positions = new Positions();
			positions.getText(pdf);
			for (TextPosition position : positions.positions) {
				assertTrue(position.getXDirAdj() >= 108f - 1f, position.getUnicode());
				assertTrue(position.getXDirAdj() + position.getWidthDirAdj() <= PDRectangle.A4.getWidth() - 107f,
						position.getUnicode() + " at " + position.getXDirAdj());
				assertTrue(position.getYDirAdj() < PDRectangle.A4.getHeight() - 108f,
						position.getUnicode() + " at " + position.getYDirAdj());
			}
		}
	}

	@Test
	void repeatedRenderingDoesNotRetainStateOrModifyIcs207() throws Exception {
		Ics203PdfRenderer renderer = new Ics203PdfRenderer();
		AppData data = new AppData();
		data.getOrganizationalChart().setIncidentCommanders(List.of("Legacy Commander"));
		data.getOrganizationalChart().getAssignmentList().setPreparedByName("Prepared Person");
		String before;
		try (PDDocument pdf = renderWith(new Ics207PdfRenderer(), data)) {
			before = text(pdf);
		}
		String first;
		try (PDDocument pdf = renderWith(renderer, data)) {
			first = text(pdf);
		}
		try (PDDocument pdf = renderWith(renderer, null)) {
			assertFalse(text(pdf).contains("Prepared Person"));
			assertEquals(1, pdf.getNumberOfPages());
		}
		try (PDDocument pdf = renderWith(renderer, data)) {
			assertEquals(first, text(pdf));
		}
		try (PDDocument pdf = renderWith(new Ics207PdfRenderer(), data)) {
			assertEquals(before, text(pdf));
		}
		assertEquals(List.of("Legacy Commander"), data.getOrganizationalChart().getIncidentCommanders());
		assertTrue(data.getOrganizationalChart().getAssignmentList().getBranches().isEmpty());
	}

	/**
	 * Builds an agency, custom position or division/group entry.
	 *
	 * @param title row label.
	 * @param name assigned person.
	 * @return populated entry.
	 */
	private OrgChartEntry entry(String title, String name) {
		OrgChartEntry result = new OrgChartEntry();
		result.setTitle(title);
		result.setName(name);
		return result;
	}

	/**
	 * Renders an ICS 203 and loads it in memory, removing the test artifact.
	 *
	 * @param data incident document.
	 * @return loaded PDF, owned by the caller.
	 * @throws IOException when rendering or loading fails.
	 */
	private PDDocument render(AppData data) throws IOException {
		return renderWith(new Ics203PdfRenderer(), data);
	}

	/**
	 * Renders any structured form for comparison.
	 *
	 * @param renderer form renderer.
	 * @param data incident document.
	 * @return loaded PDF, owned by the caller.
	 * @throws IOException when rendering or loading fails.
	 */
	private PDDocument renderWith(PdfFormRenderer renderer, AppData data) throws IOException {
		Path path = Path.of("target", "test-output", "ics203", "form-" + System.nanoTime() + ".pdf");
		try {
			renderer.render(data, path);
			return Loader.loadPDF(Files.readAllBytes(path));
		} finally {
			Files.deleteIfExists(path);
		}
	}

	/**
	 * Extracts normalized text for assertions.
	 *
	 * @param document rendered PDF.
	 * @return text with collapsed whitespace.
	 * @throws IOException when PDF text extraction fails.
	 */
	private String text(PDDocument document) throws IOException {
		return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
	}

	/**
	 * Counts non-overlapping substrings.
	 *
	 * @param text complete text.
	 * @param needle substring to count.
	 * @return occurrence count.
	 */
	private int occurrences(String text, String needle) {
		int count = 0;
		for (int offset = text.indexOf(needle); offset >= 0; offset = text.indexOf(needle, offset + needle.length())) {
			count++;
		}
		return count;
	}

	/** Extracts actual text coordinates to verify column placement and bounds. */
	private static class Positions extends PDFTextStripper {
		private final List<TextPosition> positions = new ArrayList<>();
		private final List<String> strings = new ArrayList<>();
		private final List<TextPosition> starts = new ArrayList<>();

		/**
		 * Creates a coordinate extractor.
		 *
		 * @throws IOException when initialization fails.
		 */
		private Positions() throws IOException {
			super();
		}

		/** {@inheritDoc} */
		@Override
		protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
			positions.addAll(textPositions);
			strings.add(text);
			starts.add(textPositions.get(0));
			super.writeString(text, textPositions);
		}

		/**
		 * Locates a text chunk's left coordinate.
		 *
		 * @param value text prefix.
		 * @return left coordinate.
		 */
		private float x(String value) {
			return starts.get(find(value)).getXDirAdj();
		}

		/**
		 * Locates a text chunk's vertical coordinate from the top.
		 *
		 * @param value text prefix.
		 * @return top-relative coordinate.
		 */
		private float y(String value) {
			return starts.get(find(value)).getYDirAdj();
		}

		/**
		 * Locates the first chunk containing text.
		 *
		 * @param value text to find.
		 * @return chunk index.
		 */
		private int find(String value) {
			for (int i = 0; i < strings.size(); i++) {
				if (strings.get(i).contains(value)) {
					return i;
				}
			}
			throw new AssertionError("Missing text coordinate: " + value);
		}
	}
}
