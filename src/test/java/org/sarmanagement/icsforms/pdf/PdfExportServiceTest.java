package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PdfExportService} helper logic — specifically the ICS 201 inclusion rules
 * and IAP page numbering.
 */
class PdfExportServiceTest {

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
        assertEquals("5", data.getForm202().getIapPage(),
                "ICS 202 should follow immediately after ICS 201");
        assertEquals("6", data.getForm205aIapPage(),
                "ICS 205A should follow immediately after ICS 202");
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

        assertEquals("6", data.getForm205aIapPage());
        assertEquals("8", data.getForm207().getIapPage());
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
    void orderForIapBundlePlacesSarTaskAssignmentBeforeIcs214() {
        List<String> ordered = PdfExportService.orderForIapBundle(
                List.of("ICS 201", "ICS 202", "ICS 205A", "ICS 207", "ICS 204", "ICS 214", "SAR Task Assignment"));

        assertEquals(List.of("ICS 201", "ICS 202", "ICS 205A", "ICS 207", "ICS 204", "SAR Task Assignment", "ICS 214"),
                ordered);
    }
}
