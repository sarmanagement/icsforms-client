package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Exports all supported forms and merges them into a single IAP bundle PDF.
     *
     * <p>Individual per-form PDFs are written first and then merged.  The merged file is
     * named using the incident name and operational period start, sanitised for filesystem
     * safety.  The individual component files are removed after a successful merge.</p>
     *
     * @param data incident document.
     * @param outputDirectory destination directory.
     * @return path of the merged IAP bundle PDF.
     * @throws IOException when export or merge fails.
     */
    public Path exportIapBundle(AppData data, Path outputDirectory) throws IOException {
        assignIapPageNumbers(data);
        Map<String, Path> parts = exportAll(data, outputDirectory);
        String bundleName = iapBundleFileName(data.getIncidentContext());
        Path bundlePath = outputDirectory.resolve(bundleName);
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(bundlePath.toString());
        List<Path> tempFiles = new ArrayList<>(parts.values());
        for (Path part : tempFiles) {
            merger.addSource(part.toFile());
        }
        merger.mergeDocuments(null); // null = in-memory; suitable for typical IAP sizes (< ~50 pages)
        // Remove individual component files now that the bundle is written.
        for (Path part : tempFiles) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // Best-effort cleanup; do not fail the export.
            }
        }
        return bundlePath;
    }

    /**
     * Generates a stable file name from a form identifier.
     *
     * @param formKey form identifier.
     * @return PDF file name.
     */
    /**
     * Assigns sequential IAP page numbers to all forms in the document, ordered by ICS form
     * number: ICS 202 (page 1), ICS 204 forms (primary then additional), SAR Task Assignment
     * forms, ICS 214 activity logs.
     *
     * <p>This method mutates the forms in {@code data} in-place and is called just before the
     * IAP bundle export so the page numbers printed on the PDFs are accurate.</p>
     *
     * @param data incident document.
     */
    static void assignIapPageNumbers(AppData data) {
        int page = 1;
        // ICS 202
        data.getForm202().setIapPage(String.valueOf(page++));
        // ICS 204 — primary form
        data.getForm204().setIapPage(String.valueOf(page++));
        // ICS 204 — additional forms
        for (Ics204Form form : data.getAdditionalForms204()) {
            form.setIapPage(String.valueOf(page++));
        }
        // SAR Task Assignment forms (TAFs)
        for (SarTaskAssignment task : data.getSarTaskAssignments()) {
            task.setIapPage(String.valueOf(page++));
        }
        // ICS 214 activity logs
        for (org.sarmanagement.icsforms.model.Ics214Form log : data.getActivityLogs()) {
            log.setIapPage(String.valueOf(page++));
        }
    }

    private String fileName(String formKey) {
        return formKey.toLowerCase().replace(' ', '-') + ".pdf";
    }

    /**
     * Generates the IAP bundle file name from incident context metadata.
     *
     * @param context incident context.
     * @return sanitised PDF file name.
     */
    private static String iapBundleFileName(IncidentContext context) {
        String name = context == null || context.getIncidentName() == null ? "" : context.getIncidentName();
        String period = "";
        if (context != null && context.getOperationalPeriodStart() != null) {
            period = context.getOperationalPeriodStart().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        }
        String base = (name.isBlank() ? "incident" : name)
                + (period.isBlank() ? "" : "_" + period)
                + "_IAP";
        // Replace characters that are unsafe on common filesystems.
        return base.replaceAll("[^A-Za-z0-9_\\-]", "_") + ".pdf";
    }
}
