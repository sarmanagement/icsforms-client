/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers lifecycle-sensitive actual time fields without suppressing editable debrief content. */
class SarTaskAssignmentPdfRendererTest {
	@Test
	void nonReturnedTasksLeaveActualTimesBlankAndRetainEditableDebriefContent() throws Exception {
		for (String lifecycle : List.of("planned", "assigned - enroute to assignment", "assigned - on task",
				"assigned - returning from assignment")) {
			AppData data = data(lifecycle);
			try (PDDocument pdf = render(data)) {
				String front = page(pdf, 1);
				assertTrue(front.contains("2026-02-02"));
				assertTrue(front.contains("07:15"));
				assertTrue(front.contains("08:45"));
				String debrief = page(pdf, 2);
				assertTrue(debrief.contains("13. Time On Assignment"));
				for (String label : List.of("Date From", "Date To", "Time From", "Time To")) {
					assertTrue(debrief.contains(label));
				}
				for (String value : List.of("2026-02-02", "07:15", "08:45")) {
					assertFalse(debrief.contains(value), lifecycle + ": " + value);
				}
				for (String value : List.of("Editable Supervisor", "Editable debrief notes", "Editable uncovered area",
						"Editable observed hazard")) {
					assertTrue(debrief.contains(value), value);
				}
			}
		}
	}

	@Test
	void returnedTasksShowActualTimesInTheConfiguredTimezoneEvenBeforeDebriefCompletion() throws Exception {
		AppData data = data("returned");
		try (PDDocument pdf = render(data)) {
			String debrief = page(pdf, 2);
			assertTrue(debrief.contains("2026-02-02"));
			assertTrue(debrief.contains("07:15"));
			assertTrue(debrief.contains("08:45"));
			assertTrue(debrief.contains("Editable debrief notes"));
		}
		assertFalse(data.getSarTaskAssignments().get(0).isDebriefingCompleted());
	}

	/**
	 * Builds an assignment whose front-page period equals its prospective actual times.
	 *
	 * @param lifecycle requested task state.
	 * @return source document with editable debrief content and a fixed export timezone.
	 */
	private AppData data(String lifecycle) {
		AppData data = new AppData();
		data.setUseSystemTimeZone(false);
		data.setConfiguredTimeZoneId("America/New_York");
		LocalDateTime start = LocalDateTime.of(2026, 2, 2, 12, 15);
		LocalDateTime end = LocalDateTime.of(2026, 2, 2, 13, 45);
		data.getIncidentContext().setOperationalPeriodStart(start);
		data.getIncidentContext().setOperationalPeriodEnd(end);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setTaskLifecycleStatus(lifecycle);
		task.setAssignmentStart(start);
		task.setAssignmentEnd(end);
		task.setDebriefingSupervisor("Editable Supervisor");
		task.setDebriefNotes("Editable debrief notes");
		task.setAreasNotCovered("Editable uncovered area");
		task.setHazardsObserved("Editable observed hazard");
		data.getSarTaskAssignments().add(task);
		return data;
	}

	/**
	 * Renders and loads an assignment without retaining its intermediate PDF.
	 *
	 * @param data export document.
	 * @return loaded PDF owned by the caller.
	 * @throws IOException when rendering or loading fails.
	 */
	private PDDocument render(AppData data) throws IOException {
		Path output = Path.of("target", "test-output", "sar-lifecycle", "form-" + System.nanoTime() + ".pdf");
		try {
			new SarTaskAssignmentPdfRenderer().render(data, output);
			return Loader.loadPDF(Files.readAllBytes(output));
		} finally {
			Files.deleteIfExists(output);
		}
	}

	/**
	 * Extracts one page with normalized whitespace.
	 *
	 * @param document source PDF.
	 * @param page one-based page number.
	 * @return extracted page text.
	 * @throws IOException when extraction fails.
	 */
	private String page(PDDocument document, int page) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		stripper.setStartPage(page);
		stripper.setEndPage(page);
		return stripper.getText(document).replaceAll("\\s+", " ");
	}
}
