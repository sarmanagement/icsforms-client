package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF renderer for the SAR Clue Log.
 *
 * <p>Each clue is rendered as a bordered block listing detecting task, date/time collected,
 * location, description, immediate action, follow-up, and duplicate flag.  Blocks flow across
 * pages automatically.</p>
 */
public class ClueLogPdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FORM_KEY = "Clue Log";

    /** {@inheritDoc} */
    @Override
    public String getFormKey() {
        return FORM_KEY;
    }

    /** {@inheritDoc} */
    @Override
    public void render(AppData data, Path outputFile) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (PDDocument document = new PDDocument()) {
            AppData safe = data == null ? new AppData() : data;
            List<List<String>> blocks = buildBlocks(safe);
            writeDocument(document, "SAR", "CLUE LOG", blocks);
            document.save(outputFile.toFile());
        }
    }

    private List<List<String>> buildBlocks(AppData data) {
        List<ClueLogEntry> entries = data.getClueLogEntries();
        if (entries == null || entries.isEmpty()) {
            List<String> empty = new ArrayList<>();
            empty.add("No clues recorded.");
            List<List<String>> result = new ArrayList<>();
            result.add(empty);
            return result;
        }

        List<List<String>> blocks = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ClueLogEntry e = entries.get(i);
            List<String> block = new ArrayList<>();
            block.add("Clue #" + (i + 1) + (e.isPossibleDuplicate() ? "  [POSSIBLE DUPLICATE]" : ""));
            block.add("Detecting Task:    " + safe(e.getDetectingTask()));
            block.add("Date/Time:         " + (e.getDateTimeCollected() == null ? "" : DATE_TIME_FORMATTER.format(e.getDateTimeCollected())));
            block.add("Location:          " + safe(e.getLocation()));
            block.add("Description:       " + safe(e.getDescription()));
            if (!safe(e.getImmediateAction()).isBlank()) {
                block.add("Immediate Action:  " + safe(e.getImmediateAction()));
            }
            if (!safe(e.getFollowUp()).isBlank()) {
                block.add("Follow-up:         " + safe(e.getFollowUp()));
            }
            blocks.add(block);
        }
        return blocks;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
