/*
 * Licensed under the GNU General Public License, version 3 or later.
 * See LICENSE for details.
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.PocAssignment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF renderer for the SAR Clue Log.
 *
 * <p>
 * Each clue is rendered as a bordered block listing detecting task, date/time
 * collected, location, description, immediate action, follow-up, and duplicate
 * flag. Blocks flow across pages automatically.
 * </p>
 */
public class ClueLogPdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final String FORM_KEY = "Clue Log";

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return FORM_KEY;
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		PdfText.validateForm(data, getFormKey());
		Path parent = outputFile.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (PDDocument document = new PDDocument()) {
			AppData safe = data == null ? new AppData() : data;
			List<List<String>> blocks = paginateBlocks(safe, buildBlocks(safe));
			writeDocument(document, safe, "SAR", "CLUE LOG", blocks);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Splits oversized clue blocks before passing them to the shared block writer.
	 * Bounds match that writer's 50-point margin, 15-point leading, eight-point
	 * padding and 30-point first-page heading allowance.
	 *
	 * @param data paper-size preferences
	 * @param blocks original clue blocks
	 * @return printable blocks with numbered continuation markers
	 * @throws IOException if font metrics cannot be read
	 */
	private List<List<String>> paginateBlocks(AppData data, List<List<String>> blocks) throws IOException {
		float width = pageSize(data).getWidth() - 116f;
		int capacity = (int) ((pageSize(data).getHeight() - 146f) / 15f);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		List<List<String>> result = new ArrayList<>();
		for (List<String> block : blocks) {
			List<String> lines = new ArrayList<>();
			for (String raw : block) {
				for (String paragraph : raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
					for (String line : wrap(paragraph, 88)) {
						lines.addAll(wrapMeasured(font, line, width));
					}
				}
			}
			int offset = 0;
			while (offset < lines.size()) {
				List<String> part = new ArrayList<>();
				if (offset > 0) {
					part.add(block.get(0) + " (continued)");
				}
				int take = Math.min(capacity - part.size(), lines.size() - offset);
				part.addAll(lines.subList(offset, offset + take));
				result.add(part);
				offset += take;
			}
		}
		return result;
	}

	/**
	 * Splits a line only when it would exceed the shared writer's physical bounds.
	 * Long unbroken values are split without truncation.
	 *
	 * @param font shared writer's body font
	 * @param line previously character-wrapped text
	 * @param width maximum physical width in points
	 * @return fitting lines preserving all supplied characters
	 * @throws IOException if font metrics cannot be read
	 */
	private List<String> wrapMeasured(PDType1Font font, String line, float width) throws IOException {
		List<String> result = new ArrayList<>();
		String remainder = line;
		while (font.getStringWidth(remainder) / 1000f * 10f > width || remainder.length() > 88) {
			int end = 0;
			float used = 0f;
			while (end < remainder.length() && end < 88) {
				float advance = font.getStringWidth(remainder.substring(end, end + 1)) / 1000f * 10f;
				if (used + advance > width) {
					break;
				}
				used += advance;
				end++;
			}
			int space = remainder.lastIndexOf(' ', end - 1);
			if (space > 0) {
				end = space + 1;
			}
			result.add(remainder.substring(0, end));
			remainder = remainder.substring(end);
		}
		result.add(remainder);
		return result;
	}

	/**
	 * Builds the existing clue blocks, including optional follow-up POC history.
	 *
	 * @param data incident document.
	 * @return printable clue blocks.
	 */
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
			block.add("Date/Time:         " + formatPdfDateTime(data, e.getDateTimeCollected()));
			block.add("Location:          " + safe(e.getLocation()));
			block.add("Description:       " + safe(e.getDescription()));
			if (!safe(e.getImmediateAction()).isBlank()) {
				block.add("Immediate Action:  " + safe(e.getImmediateAction()));
			}
			if (!safe(e.getFollowUp()).isBlank()) {
				block.add("Follow-up:         " + safe(e.getFollowUp()));
			}
			for (PocAssignment assignment : e.getFollowUpAssignments()) {
				if (assignment == null) {
					continue;
				}
				block.add("Follow-up POC:     " + assignment.getAssignee()
						+ (assignment.getAssignedAt() == null ? ""
								: " (" + formatPdfDateTime(data, assignment.getAssignedAt()) + ")"));
			}
			blocks.add(block);
		}
		return blocks;
	}

	/**
	 * Normalizes optional text for the existing block layout.
	 *
	 * @param value optional text.
	 * @return non-null text.
	 */
	private String safe(String value) {
		return PdfText.normalize(value);
	}
}
