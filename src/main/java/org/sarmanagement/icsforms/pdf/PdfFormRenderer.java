package org.sarmanagement.icsforms.pdf;

import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Renders a single ICS form from the shared incident document to a PDF file.
 */
public interface PdfFormRenderer {
    /**
     * Returns the form identifier handled by this renderer.
     *
     * @return form identifier.
     */
    String getFormKey();

    /**
     * Renders the form content to the supplied PDF path.
     *
     * @param data shared incident document.
     * @param outputFile destination PDF path.
     * @throws IOException when the PDF cannot be written.
     */
    void render(AppData data, Path outputFile) throws IOException;
}
