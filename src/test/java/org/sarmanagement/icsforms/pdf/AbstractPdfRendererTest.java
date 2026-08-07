package org.sarmanagement.icsforms.pdf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AbstractPdfRendererTest {

    @Test
    void expandRowToFillAddsRemainingHeightToTargetRow() {
        float[] rows = new TestRenderer().expandRowToFill(100f, 1, 20f, 30f, 10f);
        assertArrayEquals(new float[]{20f, 70f, 10f}, rows);
    }

    private static final class TestRenderer extends AbstractPdfRenderer {
    }
}
