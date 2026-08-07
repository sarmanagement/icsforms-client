package org.sarmanagement.icsforms.pdf;

import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coordinates exporting selected or all supported ICS forms to PDF files.
 */
public class PdfExportService {
    private final Map<String, PdfFormRenderer> renderers = new LinkedHashMap<>();

    /**
     * Creates an export service with the supplied form renderers.
     *
     * @param renderers supported renderers keyed by form identifier.
     */
    public PdfExportService(PdfFormRenderer... renderers) {
        for (PdfFormRenderer renderer : renderers) {
            this.renderers.put(renderer.getFormKey(), renderer);
        }
    }

    /**
     * Exports a single selected form.
     *
     * @param formKey form identifier, such as {@code ICS 202}.
     * @param data incident document.
     * @param outputDirectory destination directory.
     * @return created PDF file path.
     * @throws IOException when export fails.
     */
    public Path exportSelected(String formKey, AppData data, Path outputDirectory) throws IOException {
        PdfFormRenderer renderer = renderers.get(formKey);
        if (renderer == null) {
            throw new IllegalArgumentException("Unsupported form export: " + formKey);
        }
        Path output = outputDirectory.resolve(fileName(formKey));
        renderer.render(data, output);
        return output;
    }

    /**
     * Exports all supported forms.
     *
     * @param data incident document.
     * @param outputDirectory destination directory.
     * @return map of form keys to created files.
     * @throws IOException when any export fails.
     */
    public Map<String, Path> exportAll(AppData data, Path outputDirectory) throws IOException {
        Map<String, Path> exported = new LinkedHashMap<>();
        for (String formKey : renderers.keySet()) {
            exported.put(formKey, exportSelected(formKey, data, outputDirectory));
        }
        return exported;
    }

    /**
     * Returns whether a form key is supported.
     *
     * @param formKey form identifier.
     * @return {@code true} when supported.
     */
    public boolean supports(String formKey) {
        return renderers.containsKey(formKey);
    }

    /**
     * Generates a stable file name from a form identifier.
     *
     * @param formKey form identifier.
     * @return PDF file name.
     */
    private String fileName(String formKey) {
        return formKey.toLowerCase().replace(' ', '-') + ".pdf";
    }
}
