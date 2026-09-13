package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ics205aPdfRendererTest {

    @Test
    void renderProducesCommunicationsListSortedByLastName() throws Exception {
        AppData data = new AppData();
        IncidentContext context = data.getIncidentContext();
        context.setIncidentName("River Search");
        context.setOperationalPeriodStart(LocalDateTime.of(2026, 9, 13, 8, 0));
        context.setOperationalPeriodEnd(LocalDateTime.of(2026, 9, 13, 20, 0));
        context.setCurrentUser("Pat Planner");
        context.setCurrentUserPositionTitle("Communications Unit Leader");
        data.setForm205aIapPage("7");

        SarTaskAssignment task = new SarTaskAssignment();
        task.setAssignmentId("A1");
        task.setAssignmentTeamNumber("A-1");
        task.setResourceIdentifier("Ground-1");
        task.setLeader("Zoey Zulu");
        task.setLeaderRole("Task Leader");
        task.setAssignment("Search shoreline");

        TCard zulu = new TCard();
        zulu.setCardType(TCardType.PERSONNEL);
        zulu.setPersonName("Zoey Zulu");
        zulu.setRadioChannel("Tac 4");
        zulu.setPhoneNumber("555-4000");
        zulu.setSourceRef("sar:A1:leader");

        TCard alpha = new TCard();
        alpha.setCardType(TCardType.PERSONNEL);
        alpha.setPersonName("Amy Alpha");
        alpha.setPhoneNumber("555-1000");
        alpha.setSourceRef("org:safetyOfficer");

        SarTaskResource leader = new SarTaskResource();
        leader.setName("Zoey Zulu");
        leader.setResourceId(zulu.getResourceId());
        task.setResourcesAssigned(List.of(leader));

        data.setSarTaskAssignments(List.of(task));
        data.setTCards(List.of(zulu, alpha));

        Path output = Files.createTempFile("ics-205a-", ".pdf");
        new Ics205aPdfRenderer().render(data, output);

        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            String normalizedText = text.replaceAll("\\s+", " ");
            assertTrue(text.contains("ICS 205A COMMUNICATIONS LIST"));
            assertTrue(text.contains("1. Incident Name"));
            assertTrue(text.contains("2. Operational Period"));
            assertTrue(text.contains("3. Basic Local Communications Information"));
            assertTrue(text.contains("4. Prepared By"));
            assertTrue(text.contains("River Search"));
            assertTrue(text.contains("Pat Planner"));
            assertTrue(text.contains("Communications Unit Leader"));
            assertTrue(text.contains("Signature"));
            assertTrue(text.contains("ICS 205A"));
            assertFalse(normalizedText.contains("ICS 205A, Page 1 of 1"));
            assertTrue(normalizedText.matches(".*IAP Page[: ]+7.*"));
            assertTrue(text.contains("Amy Alpha"));
            assertTrue(text.contains("Zoey Zulu"));
            assertTrue(text.indexOf("Amy Alpha") < text.indexOf("Zoey Zulu"));
            assertTrue(text.contains("Tac 4"));
            assertTrue(text.contains("555-4000"));
            assertEquals(1, countRectangles(pdf.getPage(0), 36f, 36f, 67.5f, 24f));
            assertEquals(1, countRectangles(pdf.getPage(0), 103.5f, 36f, 67.5f, 24f));
        }
    }

    @Test
    void fitTextConstrainsPreparedByValuesToAvailableColumnWidth() throws Exception {
        Ics205aPdfRenderer renderer = new Ics205aPdfRenderer();
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        String original = "Communications Unit Leader Assigned To Unified Command Operations";
        String fitted = (String) invoke(renderer, "fitText",
                new Class<?>[]{PDType1Font.class, String.class, float.class},
                regular, original, 110f);

        assertTrue(fitted.length() < original.length());
        assertTrue(regular.getStringWidth(fitted) / 1000f * 10f <= 110f);
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
                if (!(tokens.get(i - 4) instanceof COSNumber rectX)
                        || !(tokens.get(i - 3) instanceof COSNumber rectY)
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

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
