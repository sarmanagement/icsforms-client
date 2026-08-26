package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.IapPhase;

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
        assertEquals("2", data.getForm202().getIapPage(),
                "ICS 202 should follow immediately after ICS 201");
    }

    @Test
    void assignIapPageNumbers_initialResponsePhaseWithContent_includesIcs201() {
        AppData data = new AppData();
        data.setIapPhase(IapPhase.INITIAL_RESPONSE);
        data.getForm201().setPreparedByName("IC Name");
        PdfExportService.assignIapPageNumbers(data);
        assertEquals("1", data.getForm201().getIapPage());
    }
}
