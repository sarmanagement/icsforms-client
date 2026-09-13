package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractPdfRendererTest {

    @Test
    void expandRowToFillAddsRemainingHeightToTargetRow() {
        float[] rows = new TestRenderer().expandRowToFill(100f, 1, 20f, 30f, 10f);
        assertArrayEquals(new float[]{20f, 70f, 10f}, rows);
    }

    @Test
    void addPageOffsetAdvancesNumericPageLabels() {
        assertEquals("9", new TestRenderer().addPageOffset("7", 2));
    }

    @Test
    void addPageOffsetLeavesNonNumericLabelsUntouched() {
        assertEquals("Appendix A", new TestRenderer().addPageOffset("Appendix A", 3));
    }

    @Test
    void formPageLabelOmitsPageCountForSinglePageForms() {
        assertEquals("ICS 205A", new TestRenderer().formPageLabel("ICS 205A", 1, 1));
    }

    @Test
    void formPageLabelIncludesPageCountForMultipageForms() {
        assertEquals("ICS 205A, Page 2 of 3", new TestRenderer().formPageLabel("ICS 205A", 2, 3));
    }

    private static final class TestRenderer extends AbstractPdfRenderer {
    }
}
