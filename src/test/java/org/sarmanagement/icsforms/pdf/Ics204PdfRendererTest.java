package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.ResourceAssignment;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics204PdfRendererTest {

	@Test
	void preparedBySectionKeepsTypicalFullNamesVisible() throws Exception {
		AppData data = new AppData();
		IncidentContext context = data.getIncidentContext();
		context.setIncidentName("Test Incident");
		context.setOperationalPeriodStart(LocalDateTime.of(2026, 1, 1, 8, 0));
		context.setOperationalPeriodEnd(LocalDateTime.of(2026, 1, 1, 20, 0));

		Ics204Form form = data.getForm204();
		form.setPreparedByName("Elizabeth Montgomery");
		form.setPreparedByPositionTitle("Planning Section Chief");
		form.setPreparedBySignature("Elizabeth Montgomery");
		form.setPreparedDateTime(LocalDateTime.of(2026, 1, 1, 12, 30));
		form.setIapPage("3");
		form.setOperationsSectionChiefName("Ops Chief");
		form.setOperationsSectionChiefContact("555-0100");
		form.setDivision("Division A");

		Path outputDir = Path.of("target", "test-output", "ics204");
		Files.createDirectories(outputDir);
		Path outputFile = outputDir.resolve("prepared-by-ics-204-" + System.nanoTime() + ".pdf");

		new Ics204PdfRenderer().render(data, outputFile);

		try (PDDocument pdf = Loader.loadPDF(outputFile.toFile())) {
			String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
			assertTrue(text.contains("Elizabeth Montgomery"));
			assertTrue(text.contains("Planning Section Chief"));
			assertTrue(text.contains("Date/Time"));
			assertTrue(text.contains("IAP Page 3") || text.contains("IAP Page: 3"));
			assertEquals(1, countRectangles(pdf.getPage(0), 36f, 36f, 540f, 72f));
		}
	}

	@Test
	void workAssignmentSectionUsesFullIdentifiersAndCombinesMatchingAssignments() throws Exception {
		Ics204Form form = new Ics204Form();
		ResourceAssignment first = new ResourceAssignment();
		first.setAssignmentTeamNumber("2");
		first.setResourceIdentifier("Team Violet");
		first.setAssignment("Sweep the creek drainage");
		ResourceAssignment second = new ResourceAssignment();
		second.setAssignmentTeamNumber("1");
		second.setResourceIdentifier("Team Frodo");
		second.setAssignment("Sweep the creek drainage");
		ResourceAssignment third = new ResourceAssignment();
		third.setAssignmentTeamNumber("3");
		third.setResourceIdentifier("Team Sam");
		third.setAssignment("Check the ridge spur");
		form.setResourcesAssigned(List.of(first, second, third));

		Method method = Ics204PdfRenderer.class.getDeclaredMethod("workAssignmentLines", Ics204Form.class);
		method.setAccessible(true);

		@SuppressWarnings("unchecked")
		List<String> lines = (List<String>) method.invoke(new Ics204PdfRenderer(), form);

		assertEquals("1: Team Frodo; 2: Team Violet: Sweep the creek drainage", lines.get(0));
		assertEquals("3: Team Sam: Check the ridge spur", lines.get(1));
	}

	@Test
	void sectionHeightRebalancingReclaimsBlankResourceRowsWhenWorkWouldOverflow() throws Exception {
		Ics204Form form = new Ics204Form();
		ResourceAssignment resource = new ResourceAssignment();
		resource.setAssignmentTeamNumber("1");
		resource.setResourceIdentifier("Team Frodo");
		resource.setAssignment("Alpha Bravo Charlie Delta Echo Foxtrot Golf Hotel India Juliet Kilo Lima Mike November Oscar Papa Quebec Romeo Sierra Tango Uniform Victor Whiskey X-ray Yankee Zulu "
				.repeat(12));
		form.setResourcesAssigned(List.of(resource));
		form.setSpecialInstructions("Short special instructions");

		Method method = Ics204PdfRenderer.class.getDeclaredMethod("adjustedSectionHeights", Ics204Form.class, float.class,
				float.class, float.class);
		method.setAccessible(true);

		Object heights = method.invoke(new Ics204PdfRenderer(), form, 282f, 84f, 60f);
		Class<?> heightsClass = heights.getClass();
		Method resourcesHeight = heightsClass.getDeclaredMethod("resourcesHeight");
		Method workHeight = heightsClass.getDeclaredMethod("workAssignmentHeight");
		Method resourceRowCount = heightsClass.getDeclaredMethod("resourceRowCount");

		assertTrue(((Float) resourcesHeight.invoke(heights)) < 282f);
		assertTrue(((Float) workHeight.invoke(heights)) > 84f);
		assertEquals(2, resourceRowCount.invoke(heights));
	}

	private int countRectangles(PDPage page, float x, float y, float width, float height) throws Exception {
		int count = 0;
		try (InputStream inputStream = page.getContents()) {
			PDFStreamParser parser = new PDFStreamParser(inputStream.readAllBytes());
			java.util.List<Object> tokens = parser.parse();
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
				if (closeTo(rectX.floatValue(), x) && closeTo(rectY.floatValue(), y)
						&& closeTo(rectWidth.floatValue(), width) && closeTo(rectHeight.floatValue(), height)) {
					count++;
				}
			}
		}
		return count;
	}

	private boolean closeTo(float actual, float expected) {
		return Math.abs(actual - expected) < 0.1f;
	}
}
