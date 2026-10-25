/*
 * Copyright (C) 2026 SAR Management contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.PocAssignment;

/**
 * Adapts every clue to ICS 233-CG, retaining clue numbering and POC history.
 */
public final class ClueLogIcs233PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "Clue Log (ICS 233-CG adapted)";
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		render(data, outputFile, data != null && data.isIcs233Portrait()
				? PdfOrientation.PORTRAIT : PdfOrientation.LANDSCAPE);
	}

	/**
	 * Renders the adapted clue log with an export-only orientation.
	 *
	 * @param data source incident
	 * @param outputFile destination PDF
	 * @param orientation media and content orientation
	 * @throws IOException when output fails
	 */
	public void render(AppData data, Path outputFile, PdfOrientation orientation) throws IOException {
		PdfText.validateForm(data, getFormKey());
		AppData source = data == null ? new AppData() : data;
		List<NumberedClue> clues = new ArrayList<>();
		if (source.getClueLogEntries() != null) {
			for (int index = 0; index < source.getClueLogEntries().size(); index++) {
				ClueLogEntry clue = source.getClueLogEntries().get(index);
				if (clue != null) {
					clues.add(new NumberedClue(index + 1, clue));
				}
			}
		}
		clues.sort(Comparator.comparingInt(value -> stage(value.clue())));
		List<List<String>> rows = new ArrayList<>();
		for (NumberedClue numbered : clues) {
			ClueLogEntry clue = numbered.clue();
			String item = "Description: " + Ics233PdfLayout.safe(clue.getDescription())
					+ "\nLocation: " + Ics233PdfLayout.safe(clue.getLocation())
					+ "\nFound by: " + Ics233PdfLayout.safe(clue.getDetectedBy())
					+ " (Task: " + Ics233PdfLayout.safe(clue.getDetectingTask()) + ")";
			String status = "Immediate action: " + Ics233PdfLayout.safe(clue.getImmediateAction())
					+ "\nFollow-up: " + Ics233PdfLayout.safe(clue.getFollowUp())
					+ (clue.isPossibleDuplicate() ? "\nPossible duplicate" : "")
					+ "\nStatus: " + Ics233PdfLayout.safe(clue.getFollowUpStatus());
			rows.add(List.of(Integer.toString(numbered.number()), item, pocHistory(source, clue),
					clue.isFollowUpBriefed() ? "X" : "", formatPdfDateTime(source, clue.getDateTimeCollected()),
					status, formatPdfDateTime(source, clue.getFollowUpTargetDate()),
					formatPdfDateTime(source, clue.getFollowUpCompletedDate())));
		}
		new Ics233PdfLayout().render(source, outputFile,
				"ICS 233-CG INCIDENT OPEN ACTION TRACKER Adapted as a Clue Log", rows, true, orientation);
	}

	/**
	 * Classifies clues for stable open, assigned, then followed-up ordering.
	 *
	 * @param clue source clue
	 * @return zero for open, one for assigned, two for followed up
	 */
	private int stage(ClueLogEntry clue) {
		String status = Ics233PdfLayout.safe(clue.getFollowUpStatus()).trim().toLowerCase(Locale.ROOT);
		if (clue.getFollowUpCompletedDate() != null || "completed".equals(status)
				|| "followed up".equals(status) || "followed up on".equals(status)) {
			return 2;
		}
		if (!Ics233PdfLayout.safe(clue.getFollowUpAssignedTo()).isBlank()) {
			return 1;
		}
		return 0;
	}

	/**
	 * Formats the complete stored assignment history, falling back to current POC.
	 *
	 * @param data export timezone preferences
	 * @param clue source clue
	 * @return chronological stored POC entries with assignment timestamps
	 */
	private String pocHistory(AppData data, ClueLogEntry clue) {
		List<String> entries = new ArrayList<>();
		if (clue.getFollowUpAssignments() != null) {
			for (PocAssignment assignment : clue.getFollowUpAssignments()) {
				if (assignment != null) {
					entries.add(Ics233PdfLayout.safe(assignment.getAssignee()) + " "
							+ formatPdfDateTime(data, assignment.getAssignedAt()));
				}
			}
		}
		return entries.isEmpty() ? Ics233PdfLayout.safe(clue.getFollowUpAssignedTo()) : String.join("\n", entries);
	}

	/**
	 * Keeps a clue's original number independent of export ordering.
	 *
	 * @param number original one-based clue number
	 * @param clue source record
	 */
	private record NumberedClue(int number, ClueLogEntry clue) {
	}
}
