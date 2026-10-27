/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.PodFactorRating;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SarTaskResource;
import org.sarmanagement.icsforms.model.SarTaskSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Printable two-page SAR task assignment and debriefing PDF renderer.
 */
public class SarTaskAssignmentPdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final float BODY_FONT_SIZE = 9f;
	private static final float HEADING_FONT_SIZE = 10f;
	private static final float CELL_PADDING = 4f;
	private static final int RESOURCE_SLOT_COUNT = 18;

	/**
	 * Identifies the SAR form in export selections.
	 * @return canonical form key.
	 */
	@Override
	public String getFormKey() {
		return "SAR Task Assignment";
	}

	/**
	 * Exports exactly one assignment page and one debrief page for each task.
	 * @param data source incident and page settings.
	 * @param outputFile destination PDF.
	 * @throws IOException when PDF creation fails.
	 */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		PdfText.validateForm(data, getFormKey());
		Files.createDirectories(outputFile.getParent());
		AppData safeData = data == null ? new AppData() : data;
		try (PDDocument document = new PDDocument()) {
			List<SarTaskAssignment> tasks = safeData.getSarTaskAssignments().isEmpty()
					? List.of(new SarTaskAssignment())
					: safeData.getSarTaskAssignments();
			for (SarTaskAssignment task : tasks) {
				renderAssignmentPage(document, safeData, safeData.getIncidentContext(), task);
				renderDebriefPage(document, safeData, task, safeData.getClueLogEntries());
			}
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Centers the SAR title within the configured horizontal margins.
	 * @param stream page stream.
	 * @param bold title font.
	 * @param layout printable page frame.
	 * @param title complete page title.
	 * @throws IOException when drawing fails.
	 */
	private void drawSarHeader(PDPageContentStream stream, PDType1Font bold, FormLayout layout, String title)
			throws IOException {
		float measured = bold.getStringWidth(PdfText.singleLine(title)) / 1000f;
		float size = Math.min(14f, layout.width() / Math.max(1f, measured));
		try (PdfText.TextBlock block = PdfText.begin(stream)) {
			stream.setFont(bold, size);
			stream.newLineAtOffset((layout.pageWidth() - measured * size) / 2f, layout.headerBaseline());
			stream.showText(PdfText.singleLine(title));
		}
	}

	/**
	 * Draws the assignment's planned instructions and resources.
	 * @param document destination document.
	 * @param data incident and layout settings.
	 * @param context shared operational period and incident.
	 * @param task source assignment.
	 * @throws IOException when drawing fails.
	 */
	private void renderAssignmentPage(PDDocument document, AppData data, IncidentContext context,
			SarTaskAssignment task) throws IOException {
		PDPage page = newPage(data);
		document.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			FormLayout layout = formLayout(page, data);
			float pageWidth = layout.width();
			float formHeight = layout.height();

			drawSarHeader(stream, bold, layout, "SAR TASK ASSIGNMENT FORM");
			drawFormFrame(stream, layout);
			float y = layout.top();

			float[] rows = fittedRows(formHeight, 3, 58f, 58f, 176f, 104f, 42f, 104f, 74f, 44f);
			float row1 = rows[0];
			float row2 = rows[1];
			float row3 = rows[2];
			float row4 = rows[3];
			float row5 = rows[4];
			float row6 = rows[5];
			float row7 = rows[6];
			float row8 = rows[7];

			float leftWidth = pageWidth * 0.30f;
			float middleWidth = pageWidth * 0.41f; // slightly narrower to give section 3 more room
			float rightWidth = pageWidth - leftWidth - middleWidth;

			drawCell(stream, layout.x(), y - row1, leftWidth, row1);
			drawCell(stream, layout.x() + leftWidth, y - row1, middleWidth, row1);
			drawCell(stream, layout.x() + leftWidth + middleWidth, y - row1, rightWidth, row1);
			drawSection(stream, bold, regular, layout.x(), y - row1, leftWidth, row1, "1. Incident Name",
					taskOrContextIncident(task, context));
			drawOperationalPeriodSection(stream, bold, regular, layout.x() + leftWidth, y - row1, middleWidth, row1,
					context, data);
			drawAssignmentNumberSection(stream, bold, regular, layout.x() + leftWidth + middleWidth, y - row1,
					rightWidth, row1, task.getAssignmentTeamNumber());
			y -= row1;

			float contextWidth = pageWidth * 0.22f;
			float operationsWidth = pageWidth - contextWidth;
			drawCell(stream, layout.x(), y - row2, operationsWidth, row2);
			drawCell(stream, layout.x() + operationsWidth, y - row2, contextWidth, row2);
			drawOperationsSection(stream, bold, regular, layout.x(), y - row2, operationsWidth, row2, task);
			drawContextSection(stream, bold, regular, layout.x() + operationsWidth, y - row2, contextWidth, row2, task);
			y -= row2;

			drawCell(stream, layout.x(), y - row3, pageWidth, row3);
			drawResourcesSection(stream, bold, regular, layout.x(), y - row3, pageWidth, row3, task, data);
			y -= row3;

			drawCell(stream, layout.x(), y - row4, pageWidth, row4);
			drawSection(stream, bold, regular, layout.x(), y - row4, pageWidth, row4, "6. Work Assignment",
					task.getAssignment());
			y -= row4;

			drawCell(stream, layout.x(), y - row5, pageWidth, row5);
			drawSection(stream, bold, regular, layout.x(), y - row5, pageWidth, row5, "7. Transportation Instructions",
					task.getTransportationInstructions());
			y -= row5;

			float mapWidth = pageWidth * 0.68f;
			drawCell(stream, layout.x(), y - row6, mapWidth, row6);
			drawCell(stream, layout.x() + mapWidth, y - row6, pageWidth - mapWidth, row6);
			drawSection(stream, bold, regular, layout.x(), y - row6, mapWidth, row6, "8. Task Map",
					task.getTaskMap());
			drawSection(stream, bold, regular, layout.x() + mapWidth, y - row6, pageWidth - mapWidth, row6,
					"9. Special Equipment", task.getSpecialEquipment());
			y -= row6;

			drawCell(stream, layout.x(), y - row7, pageWidth, row7);
			drawCommunicationsSection(stream, bold, regular, layout.x(), y - row7, pageWidth, row7,
					task.getCommunications());
			y -= row7;

			drawPreparedBySection(stream, bold, regular, layout.x(), y - row8, pageWidth, row8, "11. Prepared by",
					task.getPreparedByName(), task.getPreparedByPositionTitle(), task.getPreparedDateTime(),
					"SAR Task Assignment Form - Page 1 of 2"
							+ (task.getIapPage().isBlank() ? "" : " | IAP Page " + task.getIapPage()),
					data);
		}
	}

	/**
	 * Draws completed task findings, leaving nonreturned tasks' debrief fields blank.
	 * @param document destination document.
	 * @param data incident and layout settings.
	 * @param task source assignment, never modified.
	 * @param clueLogEntries incident clues linked to returned tasks.
	 * @throws IOException when drawing fails.
	 */
	private void renderDebriefPage(PDDocument document, AppData data, SarTaskAssignment task,
			List<ClueLogEntry> clueLogEntries) throws IOException {
		PDPage page = newPage(data);
		document.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			FormLayout layout = formLayout(page, data);
			float pageWidth = layout.width();
			float formHeight = layout.height();
			boolean returned = "returned".equals(task.getTaskLifecycleStatus());

			drawSarHeader(stream, bold, layout, "SAR TASK ASSIGNMENT FORM DEBRIEFING");
			drawFormFrame(stream, layout);
			float y = layout.top();

			float[] rows = fittedRows(formHeight, 1, 68f, 220f, 140f, 90f, 48f);
			float row1 = rows[0];
			float row2 = rows[1];
			float row3 = rows[2];
			float row4 = rows[3];
			float row5 = rows[4];

			float leftWidth = pageWidth * 0.34f;
			float middleWidth = pageWidth * 0.37f;
			float rightWidth = pageWidth - leftWidth - middleWidth;

			drawCell(stream, layout.x(), y - row1, leftWidth, row1);
			drawCell(stream, layout.x() + leftWidth, y - row1, middleWidth, row1);
			drawCell(stream, layout.x() + leftWidth + middleWidth, y - row1, rightWidth, row1);
			drawSection(stream, bold, regular, layout.x(), y - row1, leftWidth, row1, "12. Debriefing Supervisor",
					returned ? task.getDebriefingSupervisor() : "");
			drawTimeOnAssignmentSection(stream, bold, regular, layout.x() + leftWidth, y - row1, middleWidth, row1,
					task, data);
			drawDebriefHeaderRight(stream, bold, regular, layout.x() + leftWidth + middleWidth, y - row1, rightWidth,
					row1, task);
			y -= row1;

			drawCell(stream, layout.x(), y - row2, pageWidth, row2);
			drawDebriefingSection(stream, bold, regular, layout.x(), y - row2, pageWidth, row2, task,
					cluesForTask(task, clueLogEntries), data);
			y -= row2;

			drawCell(stream, layout.x(), y - row3, pageWidth, row3);
			drawSection(stream, bold, regular, layout.x(), y - row3, pageWidth, row3, "16. Areas Not Covered",
					returned ? task.getAreasNotCovered() : "");
			y -= row3;

			drawCell(stream, layout.x(), y - row4, pageWidth, row4);
			drawSection(stream, bold, regular, layout.x(), y - row4, pageWidth, row4, "17. Hazards Observed",
					returned ? task.getHazardsObserved() : "");
			y -= row4;

			drawPreparedBySection(stream, bold, regular, layout.x(), y - row5, pageWidth, row5, "18. Prepared by",
					returned ? task.getDebriefPreparedByName() : "",
					returned ? task.getDebriefPreparedByPositionTitle() : "",
					returned ? task.getDebriefPreparedDateTime() : null,
					"SAR Task Assignment Form - Page 2 of 2", data);
		}
	}

	/**
	 * Draws the paired resource roster with complete fitted cell values.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param task assigned resources.
	 * @param data incident document.
	 * @throws IOException when drawing fails.
	 */
	private void drawResourcesSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, SarTaskAssignment task, AppData data) throws IOException {
		float scale = Math.min(1f, height / 176f);
		drawHeading(stream, bold, x, y + height, width, scale, "5. Resources Assigned");
		drawRightAlignedHeadingValue(stream, regular, x, y + height, width, scale, task.getResourceIdentifier());
		float detailLineY = y + height - (CELL_PADDING + HEADING_FONT_SIZE + 16f) * scale;
		drawInlinePair(stream, regular, x + CELL_PADDING, detailLineY, width * 0.42f - CELL_PADDING, scale, "Resource Type",
				safe(task.getResourceType()));
		drawInlinePair(stream, regular, x + (width * 0.42f), detailLineY, width * 0.58f - CELL_PADDING, scale, "Task Geometry",
				safe(task.getTaskType()));
		float tableTop = detailLineY - (BODY_FONT_SIZE + 4f) * scale;
		float headerHeight = 20f * scale;
		float headerBottom = tableTop - headerHeight;
		float rowHeight = (headerBottom - y) / 9f;
		float[] widths = {0.16f, 0.34f, 0.16f, 0.34f};

		drawHorizontalLine(stream, x, x + width, tableTop);
		drawHorizontalLine(stream, x, x + width, headerBottom);
		float currentX = x;
		for (int i = 0; i < widths.length - 1; i++) {
			currentX += width * widths[i];
			drawVerticalLine(stream, currentX, y, tableTop);
		}
		String[] headings = {"Function", "Name", "Function", "Name"};
		float[] starts = columnStarts(x, width, widths);
		for (int i = 0; i < headings.length; i++) {
			writeWrappedCellText(stream, bold, starts[i], headerBottom, width * widths[i], headerHeight,
					List.of(headings[i]));
		}

		List<SarTaskResource> resources = printableResources(task);
		while (resources.size() < RESOURCE_SLOT_COUNT) {
			resources.add(new SarTaskResource());
		}
		for (int rowIndex = 0; rowIndex < 9; rowIndex++) {
			float rowTop = headerBottom - (rowIndex * rowHeight);
			float rowBottom = Math.max(y, rowTop - rowHeight);
			drawHorizontalLine(stream, x, x + width, rowBottom);
			SarTaskResource left = resources.get(rowIndex * 2);
			SarTaskResource right = resources.get((rowIndex * 2) + 1);
			writeWrappedCellText(stream, regular, starts[0], rowBottom, width * widths[0], rowTop - rowBottom,
					List.of(safe(left.getFunction())));
			writeWrappedCellText(stream, regular, starts[1], rowBottom, width * widths[1], rowTop - rowBottom,
					List.of(safe(left.getName())));
			writeWrappedCellText(stream, regular, starts[2], rowBottom, width * widths[2], rowTop - rowBottom,
					List.of(safe(right.getFunction())));
			writeWrappedCellText(stream, regular, starts[3], rowBottom, width * widths[3], rowTop - rowBottom,
					List.of(safe(right.getName())));
		}
	}

	/**
	 * Fits all communications entries into the three-column contact table.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param communications complete contact entries.
	 * @throws IOException when drawing fails.
	 */
	private void drawCommunicationsSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, List<CommunicationEntry> communications) throws IOException {
		float scale = Math.min(1f, height / 74f);
		drawHeading(stream, bold, x, y + height, width, scale, "10. Communications");
		float tableTop = y + height - 14f * scale;
		float headerHeight = 20f * scale;
		float headerBottom = tableTop - headerHeight;
		float[] widths = {0.30f, 0.30f, 0.40f};
		float[] starts = columnStarts(x, width, widths);
		drawHorizontalLine(stream, x, x + width, tableTop);
		drawHorizontalLine(stream, x, x + width, headerBottom);
		float currentX = x;
		for (int i = 0; i < widths.length - 1; i++) {
			currentX += width * widths[i];
			drawVerticalLine(stream, currentX, y, tableTop);
		}
		String[] headings = {"Name", "Function", "Primary Contact"};
		for (int i = 0; i < headings.length; i++) {
			writeWrappedCellText(stream, bold, starts[i], headerBottom, width * widths[i], headerHeight,
					List.of(headings[i]));
		}
		int rows = Math.max(3, communications.size());
		float rowHeight = (headerBottom - y) / rows;
		for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
			float rowTop = headerBottom - (rowIndex * rowHeight);
			float rowBottom = Math.max(y, rowTop - rowHeight);
			drawHorizontalLine(stream, x, x + width, rowBottom);
			if (rowIndex >= communications.size()) {
				continue;
			}
			CommunicationEntry entry = communications.get(rowIndex);
			writeWrappedCellText(stream, regular, starts[0], rowBottom, width * widths[0], rowTop - rowBottom,
					List.of(safe(entry.getName())));
			writeWrappedCellText(stream, regular, starts[1], rowBottom, width * widths[1], rowTop - rowBottom,
					List.of(safe(entry.getFunction())));
			writeWrappedCellText(stream, regular, starts[2], rowBottom, width * widths[2], rowTop - rowBottom,
					List.of(safe(entry.getPrimaryContact())));
		}
	}

	/**
	 * Draws bounded management names and contact details.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param task management fields.
	 * @throws IOException when drawing fails.
	 */
	private void drawOperationsSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, SarTaskAssignment task) throws IOException {
		float scale = Math.min(1f, height / 58f);
		drawHeading(stream, bold, x, y + height, width, scale, "4. Operations Personnel");
		float lineY = y + height - (CELL_PADDING + HEADING_FONT_SIZE + 14f) * scale;
		drawInlinePair(stream, regular, x + CELL_PADDING, lineY, width * 0.58f - CELL_PADDING, scale, "Name",
				task.getOperationsSectionChiefName());
		drawInlinePair(stream, regular, x + (width * 0.58f), lineY, width * 0.42f - CELL_PADDING, scale, "Contact Number(s)",
				task.getOperationsSectionChiefContact());
		if (!task.getSecondaryManagementRoleLabel().isBlank()) {
			drawInlinePair(stream, regular, x + CELL_PADDING, lineY - 16f * scale, width * 0.58f - CELL_PADDING, scale,
					task.getSecondaryManagementRoleLabel(),
					task.getSecondaryManagementName());
			drawInlinePair(stream, regular, x + (width * 0.58f), lineY - 16f * scale, width * 0.42f - CELL_PADDING, scale, "Contact",
					task.getSecondaryManagementContact());
		}
	}

	/**
	 * Draws the highest-priority populated assignment context.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param task context fields.
	 * @throws IOException when drawing fails.
	 */
	private void drawContextSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			float width, float height, SarTaskAssignment task) throws IOException {
		LabeledValue relevantContext = relevantContext(task);
		drawSection(stream, bold, regular, x, y, width, height, "3. " + relevantContext.label(),
				relevantContext.value());
	}

	/**
	 * Draws planned dates and times, independently of task lifecycle.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param context shared planned operational period.
	 * @param data export timezone settings.
	 * @throws IOException when drawing fails.
	 */
	private void drawOperationalPeriodSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float x, float y, float width, float height, IncidentContext context, AppData data) throws IOException {
		float scale = Math.min(1f, height / 58f);
		drawHeading(stream, bold, x, y + height, width, scale, "2. Operational Period");
		float labelY = y + height - (CELL_PADDING + HEADING_FONT_SIZE + 14f) * scale;
		drawInlinePair(stream, regular, x + CELL_PADDING, labelY, width / 2f - CELL_PADDING, scale, "Date From",
				formatDate(data, context == null ? null : context.getOperationalPeriodStart()));
		drawInlinePair(stream, regular, x + (width / 2f), labelY, width / 2f - CELL_PADDING, scale, "Date To",
				formatDate(data, context == null ? null : context.getOperationalPeriodEnd()));
		drawInlinePair(stream, regular, x + CELL_PADDING, labelY - 16f * scale, width / 2f - CELL_PADDING, scale, "Time From",
				formatTime(data, context == null ? null : context.getOperationalPeriodStart()));
		drawInlinePair(stream, regular, x + (width / 2f), labelY - 16f * scale, width / 2f - CELL_PADDING, scale, "Time To",
				formatTime(data, context == null ? null : context.getOperationalPeriodEnd()));
	}

	/**
	 * Draws the assignment identifier in a balanced text block.
	 * @param stream page stream.
	 * @param bold identifier font.
	 * @param regular body font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param height cell height.
	 * @param assignmentTeamNumber source identifier.
	 * @throws IOException when drawing fails.
	 */
	private void drawAssignmentNumberSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, String assignmentTeamNumber) throws IOException {
		drawSection(stream, bold, bold, x, y, width, height, "3. Assignment/Team Number", assignmentTeamNumber);
	}

	/**
	 * Draws actual assignment dates and times only after the task has returned.
	 * Planned and active tasks retain blank printed fields without changing stored notes.
	 *
	 * @param stream page stream.
	 * @param bold label font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param height cell height.
	 * @param task source assignment and lifecycle.
	 * @param data export timezone settings.
	 * @throws IOException when drawing fails.
	 */
	private void drawTimeOnAssignmentSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, SarTaskAssignment task, AppData data) throws IOException {
		float scale = Math.min(1f, height / 68f);
		drawHeading(stream, bold, x, y + height, width, scale, "13. Time On Assignment");
		float labelY = y + height - (CELL_PADDING + HEADING_FONT_SIZE + 14f) * scale;
		boolean returned = "returned".equals(task.getTaskLifecycleStatus());
		drawInlinePair(stream, regular, x + CELL_PADDING, labelY, width / 2f - CELL_PADDING, scale, "Date From",
				returned ? formatDate(data, task.getAssignmentStart()) : "");
		drawInlinePair(stream, regular, x + (width / 2f), labelY, width / 2f - CELL_PADDING, scale, "Date To",
				returned ? formatDate(data, task.getAssignmentEnd()) : "");
		drawInlinePair(stream, regular, x + CELL_PADDING, labelY - 16f * scale, width / 2f - CELL_PADDING, scale, "Time From",
				returned ? formatTime(data, task.getAssignmentStart()) : "");
		drawInlinePair(stream, regular, x + (width / 2f), labelY - 16f * scale, width / 2f - CELL_PADDING, scale, "Time To",
				returned ? formatTime(data, task.getAssignmentEnd()) : "");
	}

	/**
	 * Draws the debrief assignment identifier and vehicle miles.
	 * @param stream page stream.
	 * @param bold identifier font.
	 * @param regular body font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param height cell height.
	 * @param task source assignment.
	 * @throws IOException when drawing fails.
	 */
	private void drawDebriefHeaderRight(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, SarTaskAssignment task) throws IOException {
		float scale = Math.min(1f, height / 68f);
		drawHeading(stream, bold, x, y + height, width, scale, "14. Assignment/Team Number");
		writeWrappedCellText(stream, bold, x, y + 28f * scale, width, height - 42f * scale,
				List.of(safe(task.getAssignmentTeamNumber())));
		drawInlinePair(stream, regular, x + CELL_PADDING, y + 16f * scale, width - 2f * CELL_PADDING, scale, "Vehicle Miles",
				"returned".equals(task.getTaskLifecycleStatus()) ? safe(task.getVehicleMiles()) : "");
	}

	/**
	 * Draws preparer metadata and the aligned form footer.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular body font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param heading section heading.
	 * @param name preparer name.
	 * @param title preparer position.
	 * @param dateTime preparation time.
	 * @param footerLabel form and page labels.
	 * @param data incident timezone settings.
	 * @throws IOException when drawing fails.
	 */
	private void drawPreparedBySection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, String heading, String name, String title, LocalDateTime dateTime,
			String footerLabel, AppData data) throws IOException {
		drawCell(stream, x, y, width, height);
		float scale = Math.min(1f, height / 44f);
		drawHeading(stream, bold, x, y + height, width, scale, heading);
		float footerBandHeight = 14f * scale;
		float footerCellWidth = width / 3f;
		drawCell(stream, x, y, footerCellWidth, footerBandHeight);
		float contentHeight = height - footerBandHeight - 14f * scale;
		writeWrappedCellText(stream, regular, x, y + footerBandHeight, width * 0.42f, contentHeight,
				List.of("Name: " + safe(name)));
		writeWrappedCellText(stream, regular, x + width * 0.42f, y + footerBandHeight, width * 0.32f,
				contentHeight, List.of("Position/Title: " + safe(title)));
		writeWrappedCellText(stream, regular, x + width * 0.74f, y + footerBandHeight, width * 0.26f,
				contentHeight, List.of("Date/Time: " + formatDateTime(data, dateTime)));
		int sepIdx = footerLabel.indexOf(" | ");
		String leftLabel = sepIdx >= 0 ? footerLabel.substring(0, sepIdx) : footerLabel;
		String rightLabel = sepIdx >= 0 ? footerLabel.substring(sepIdx + 3) : "";
		writeWrappedCellText(stream, regular, x, y, rightLabel.isBlank() ? width : width * 0.75f,
				footerBandHeight, List.of(leftLabel));
		if (!rightLabel.isBlank()) {
			writeWrappedCellText(stream, regular, x + width * 0.75f, y, width * 0.25f,
					footerBandHeight, List.of(rightLabel));
		}
	}

	/**
	 * Draws returned-task findings, keeping short POD values beside the heading and
	 * flowing longer POD narratives into the fitted body. Only bare numeric POD
	 * values receive a percentage suffix. Nonreturned tasks print only the label.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular body font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param task source assignment.
	 * @param clues associated clue entries.
	 * @param data incident document.
	 * @throws IOException when drawing fails.
	 */
	private void drawDebriefingSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x,
			float y, float width, float height, SarTaskAssignment task, List<ClueLogEntry> clues, AppData data)
			throws IOException {
		float scale = Math.min(1f, height / 220f);
		drawHeading(stream, bold, x, y + height, width, scale, "15. Debriefing");
		if (!"returned".equals(task.getTaskLifecycleStatus())) {
			return;
		}
		List<String> entries = new ArrayList<>(debriefSectionLines(task, clues, data));
		if (!safe(task.getReportedPod()).isBlank()) {
			String pod = safe(task.getReportedPod()).strip();
			String podLabel = "REPORTED POD: " + pod + (pod.matches("\\d+(?:\\.\\d+)?") ? "%" : "");
			float podWidth = bold.getStringWidth(PdfText.singleLine(podLabel)) / 1000f * BODY_FONT_SIZE;
			if (!pod.contains("\n") && podWidth <= width / 2f - 2f * CELL_PADDING) {
				writeWrappedCellText(stream, bold, x + width / 2f, y + height - 16f * scale, width / 2f,
						16f * scale, List.of(podLabel));
			} else {
				entries.add(0, podLabel);
			}
		}
		writeWrappedCellText(stream, regular, x, y, width, height - 20f * scale, entries);
	}

	/**
	 * Draws a heading above a complete, measured and fitted logical text block.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width section width.
	 * @param height section height.
	 * @param heading numbered field label.
	 * @param value unwrapped content.
	 * @throws IOException when drawing fails.
	 */
	private void drawSection(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			float width, float height, String heading, String value) throws IOException {
		float scale = Math.min(1f, height / 42f);
		drawHeading(stream, bold, x, y + height, width, scale, heading);
		writeWrappedCellText(stream, regular, x, y, width, height - 12f * scale,
				List.of(safe(value)));
	}

	/**
	 * Draws a section heading in a balanced text block.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param x left edge.
	 * @param topY top edge.
	 * @param width heading cell width.
	 * @param scale vertical spacing scale for compressed rows.
	 * @param heading section heading.
	 * @throws IOException when drawing fails.
	 */
	private void drawHeading(PDPageContentStream stream, PDType1Font bold, float x, float topY, float width,
			float scale, String heading)
			throws IOException {
		float measured = bold.getStringWidth(PdfText.singleLine(heading)) / 1000f;
		float size = Math.min(HEADING_FONT_SIZE * scale, (width - 2f * CELL_PADDING) / Math.max(1f, measured));
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, size);
			stream.newLineAtOffset(x + CELL_PADDING, topY - (CELL_PADDING + HEADING_FONT_SIZE) * scale);
			stream.showText(PdfText.singleLine(heading));
		}
	}

	/**
	 * Measures and draws normalized text aligned to the section's right edge.
	 * @param stream page stream.
	 * @param font text font.
	 * @param x left edge.
	 * @param topY top edge.
	 * @param width section width.
	 * @param scale vertical spacing scale for compressed rows.
	 * @param value source text.
	 * @throws IOException when measurement or drawing fails.
	 */
	private void drawRightAlignedHeadingValue(PDPageContentStream stream, PDType1Font font, float x, float topY,
			float width, float scale, String value) throws IOException {
		String safeValue = safe(value);
		if (safeValue.isBlank()) {
			return;
		}
		float measured = font.getStringWidth(PdfText.singleLine(safeValue)) / 1000f;
		float size = Math.min(BODY_FONT_SIZE * scale, (width * 0.5f - CELL_PADDING) / Math.max(1f, measured));
		float textWidth = measured * size;
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, size);
			stream.newLineAtOffset(x + width - CELL_PADDING - textWidth,
					topY - (CELL_PADDING + HEADING_FONT_SIZE) * scale);
			stream.showText(PdfText.singleLine(safeValue));
		}
	}

	/**
	 * Fits a label and complete value within the available inline field.
	 * @param stream page stream.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y baseline.
	 * @param width field width.
	 * @param scale vertical spacing scale for compressed rows.
	 * @param label field label.
	 * @param value source value.
	 * @throws IOException when drawing fails.
	 */
	private void drawInlinePair(PDPageContentStream stream, PDType1Font regular, float x, float y, float width, float scale,
			String label, String value) throws IOException {
		writeWrappedCellText(stream, regular, x, y - 3f * scale, width, 15f * scale, List.of(label + ": " + safe(value)));
	}

	/**
	 * Fits complete logical entries to a ruled cell using measured font advances.
	 * Rewraps at each candidate size rather than discarding overflowing lines.
	 * @param stream page stream.
	 * @param font body font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param height cell height.
	 * @param lines complete logical entries.
	 * @throws IOException when drawing fails.
	 * @throws IllegalArgumentException when the configured margins leave no cell space.
	 */
	private void writeWrappedCellText(PDPageContentStream stream, PDType1Font font, float x, float y, float width,
			float height, List<String> lines) throws IOException {
		if (width <= 0f || height <= 0f) {
			throw new IllegalArgumentException("SAR PDF margins leave no space for a printable field");
		}
		List<String> entries = lines == null || lines.isEmpty() ? List.of("") : lines;
		float padding = Math.min(CELL_PADDING, height * 0.15f);
		float size = BODY_FONT_SIZE;
		List<String> wrapped;
		while (true) {
			wrapped = new ArrayList<>();
			for (String entry : entries) {
				wrapped.addAll(wrapText(safe(entry), font, size, width - 2f * padding));
			}
			float textHeight = size + Math.max(0, wrapped.size() - 1) * size * 1.2f;
			if (textHeight <= height - 2f * padding) {
				break;
			}
			size *= 0.9f;
		}
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, size);
			stream.newLineAtOffset(x + padding, y + height - padding - size);
			for (String line : wrapped) {
				stream.showText(PdfText.singleLine(line));
				stream.newLineAtOffset(0, -size * 1.2f);
			}
		}
	}

	/**
	 * Expands the main narrative row, or scales all rows to respect larger margins.
	 * @param height available form height.
	 * @param expandable index of the narrative row.
	 * @param preferred preferred row heights.
	 * @return row heights contained within the form frame.
	 */
	private float[] fittedRows(float height, int expandable, float... preferred) {
		float[] rows = expandRowToFill(height, expandable, preferred);
		float total = 0f;
		for (float row : rows) {
			total += row;
		}
		if (total > height) {
			for (int index = 0; index < rows.length; index++) {
				rows[index] *= height / total;
			}
		}
		return rows;
	}

	/**
	 * Resolves relative table widths into absolute left edges.
	 * @param x table left edge.
	 * @param width table width.
	 * @param widths relative column widths.
	 * @return left edges for each column.
	 */
	private float[] columnStarts(float x, float width, float[] widths) {
		float[] starts = new float[widths.length];
		float current = x;
		for (int i = 0; i < widths.length; i++) {
			starts[i] = current;
			current += width * widths[i];
		}
		return starts;
	}

	/**
	 * Draws a horizontal table rule.
	 * @param stream page stream.
	 * @param x1 left edge.
	 * @param x2 right edge.
	 * @param y rule position.
	 * @throws IOException when drawing fails.
	 */
	private void drawHorizontalLine(PDPageContentStream stream, float x1, float x2, float y) throws IOException {
		stream.moveTo(x1, y);
		stream.lineTo(x2, y);
		stream.stroke();
	}

	/**
	 * Draws a vertical table rule.
	 * @param stream page stream.
	 * @param x rule position.
	 * @param y1 bottom edge.
	 * @param y2 top edge.
	 * @throws IOException when drawing fails.
	 */
	private void drawVerticalLine(PDPageContentStream stream, float x, float y1, float y2) throws IOException {
		stream.moveTo(x, y1);
		stream.lineTo(x, y2);
		stream.stroke();
	}

	/**
	 * Outlines a logical form cell.
	 * @param stream page stream.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width cell width.
	 * @param height cell height.
	 * @throws IOException when drawing fails.
	 */
	private void drawCell(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
		stream.addRect(x, y, width, height);
		stream.stroke();
	}

	/**
	 * Resolves the task incident name with shared-context fallback.
	 * @param task source assignment.
	 * @param context optional incident context.
	 * @return printed incident name.
	 */
	private String taskOrContextIncident(SarTaskAssignment task, IncidentContext context) {
		return safe(task.getIncidentName()).isBlank() && context != null
				? context.getIncidentName()
				: task.getIncidentName();
	}

	/**
	 * Copies roster entries, omitting null rows and the redundant resource header.
	 * @param task source assignment.
	 * @return independent printable roster list.
	 */
	private List<SarTaskResource> printableResources(SarTaskAssignment task) {
		List<SarTaskResource> resources = new ArrayList<>();
		String taskResourceIdentifier = safe(task.getResourceIdentifier()).trim();
		for (SarTaskResource resource : task.getResourcesAssigned()) {
			// Ignore sparse/null entries so partially edited rows still render as blank
			// slots
			// instead of failing the entire PDF export.
			if (resource == null) {
				continue;
			}
			String resourceName = safe(resource.getName()).trim();
			String resourceFunction = safe(resource.getFunction()).trim();
			if (!taskResourceIdentifier.isBlank() && resourceName.equalsIgnoreCase(taskResourceIdentifier)
					&& (resourceFunction.isBlank() || "Resource".equalsIgnoreCase(resourceFunction))) {
				continue;
			}
			resources.add(resource);
		}
		return resources;
	}

	/**
	 * Selects clues by stable assignment ID, falling back to legacy team identity.
	 * @param task source assignment.
	 * @param clueLogEntries incident clues.
	 * @return matching clue entries in their original order.
	 */
	private List<ClueLogEntry> cluesForTask(SarTaskAssignment task, List<ClueLogEntry> clueLogEntries) {
		List<ClueLogEntry> matches = new ArrayList<>();
		for (ClueLogEntry clue : clueLogEntries) {
			if (clue == null) {
				continue;
			}
			if (!safe(task.getAssignmentId()).isBlank()
					&& safe(task.getAssignmentId()).equals(clue.getAssignmentId())) {
				matches.add(clue);
				continue;
			}
			if (safe(task.getAssignmentId()).isBlank() && !safe(task.getAssignmentTeamNumber()).isBlank()
					&& safe(task.getAssignmentTeamNumber()).equals(clue.getDetectingTask())) {
				matches.add(clue);
			}
		}
		return matches;
	}

	/**
	 * Collects complete logical debrief entries without character-count wrapping.
	 * @param task returned assignment.
	 * @param clues associated clues.
	 * @param data export timezone settings.
	 * @return narrative, clues and applicable POD and canine conditions.
	 */
	private List<String> debriefSectionLines(SarTaskAssignment task, List<ClueLogEntry> clues, AppData data) {
		List<String> lines = new ArrayList<>();
		addBlock(lines, safe(task.getDebriefNotes()));
		if (!clues.isEmpty()) {
			lines.add("");
			lines.add("Clues Detected:");
			for (ClueLogEntry clue : clues) {
				String dupFlag = clue.isPossibleDuplicate() ? " [POSSIBLE DUPLICATE]" : "";
				String clueSummary = joinNonBlank(formatDateTime(data, clue.getDateTimeCollected()), safe(clue.getLocation()),
						safe(clue.getDescription()));
				addBlock(lines, "- " + clueSummary + dupFlag);
				if (!safe(clue.getImmediateAction()).isBlank()) {
					addBlock(lines, "Action taken: " + clue.getImmediateAction());
				}
				if (!safe(clue.getFollowUp()).isBlank()) {
					addBlock(lines, "Follow-up: " + clue.getFollowUp());
				}
			}
		}
		List<PodFactorRating> factors = SarTaskSupport.factorRatings(task.getResourceType(),
				task.getQualitativePodFactors());
		boolean hasFactorContent = factors.stream()
				.anyMatch(factor -> factor.getScore() != null || !safe(factor.getDescription()).isBlank());
		if (hasFactorContent) {
			lines.add("");
			lines.add("Qualitative POD Factors (" + SarTaskSupport.podProfileLabel(task.getResourceType()) + "):");
			for (PodFactorRating factor : factors) {
				if (factor.getScore() == null && safe(factor.getDescription()).isBlank()) {
					continue;
				}
				String factorSummary = factor.getName() + " "
						+ (factor.getScore() == null ? "" : factor.getScore() + "/" + factor.getMaxScore());
				if (!safe(factor.getDescription()).isBlank()) {
					factorSummary += ": " + factor.getDescription();
				}
				addBlock(lines, "- " + factorSummary.trim());
			}
		}
		if (SarTaskSupport.usesCanineFactors(task.getResourceType())) {
			List<String> canineLines = new ArrayList<>();
			addIfPresent(canineLines, "Canine Type", task.getCanineSearchType());
			addIfPresent(canineLines, "Imprint", task.getCanineImprint());
			addIfPresent(canineLines, "Sun Angle", task.getCanineSunAngle());
			addIfPresent(canineLines, "Day/Night", task.getCanineDayNight());
			addIfPresent(canineLines, "Cloud Cover", task.getCanineCloudCover());
			addIfPresent(canineLines, "Wind Speed", task.getCanineWindSpeed());
			if (!canineLines.isEmpty()) {
				lines.add("");
				lines.add("Canine Assignment Conditions:");
				for (String canineLine : canineLines) {
					addBlock(lines, "- " + canineLine);
				}
			}
		}
		return lines.isEmpty() ? List.of("") : lines;
	}

	/**
	 * Appends a populated logical block for subsequent measured wrapping.
	 * @param lines destination entries.
	 * @param value complete block text.
	 */
	private void addBlock(List<String> lines, String value) {
		if (!safe(value).isBlank()) {
			lines.add(value);
		}
	}

	/**
	 * Appends a labeled condition only when its value is populated.
	 * @param lines destination entries.
	 * @param label condition label.
	 * @param value condition value.
	 */
	private void addIfPresent(List<String> lines, String label, String value) {
		if (!safe(value).isBlank()) {
			lines.add(label + ": " + value);
		}
	}

	/**
	 * Selects the first populated ICS 204 management context.
	 * @param task source context fields.
	 * @return selected label and value, or a blank context.
	 */
	private LabeledValue relevantContext(SarTaskAssignment task) {
		// The printed form only has room for one management-context cell, so prefer the
		// first populated ICS 204 context field in its Branch -> Division -> Group ->
		// Staging Area order so the PDF matches the linked assignment context source.
		if (!safe(task.getBranch()).isBlank()) {
			return new LabeledValue("Branch", task.getBranch());
		}
		if (!safe(task.getDivision()).isBlank()) {
			return new LabeledValue("Division", task.getDivision());
		}
		if (!safe(task.getGroup()).isBlank()) {
			return new LabeledValue("Group", task.getGroup());
		}
		if (!safe(task.getStagingArea()).isBlank()) {
			return new LabeledValue("Staging Area", task.getStagingArea());
		}
		return new LabeledValue("Context", "");
	}

	/**
	 * Formats a date in the export timezone.
	 * @param data timezone settings.
	 * @param value optional stored UTC time.
	 * @return formatted date or blank.
	 */
	private String formatDate(AppData data, LocalDateTime value) {
		return formatPdfDate(data, value);
	}

	/**
	 * Formats a time in the export timezone.
	 * @param data timezone settings.
	 * @param value optional stored UTC time.
	 * @return formatted time or blank.
	 */
	private String formatTime(AppData data, LocalDateTime value) {
		return formatPdfTime(data, value);
	}

	/**
	 * Formats a combined date and time in the export timezone.
	 * @param data timezone settings.
	 * @param value optional stored UTC time.
	 * @return formatted date and time or blank.
	 */
	private String formatDateTime(AppData data, LocalDateTime value) {
		return formatPdfDateTime(data, value);
	}

	/**
	 * Joins populated clue summary components without modifying their contents.
	 * @param values summary components.
	 * @return pipe-delimited summary.
	 */
	private String joinNonBlank(String... values) {
		List<String> parts = new ArrayList<>();
		for (String value : values) {
			if (!safe(value).isBlank()) {
				parts.add(value.trim());
			}
		}
		return String.join(" | ", parts);
	}

	/**
	 * Normalizes layout whitespace without altering stored content.
	 * @param value optional text.
	 * @return normalized text retaining logical line breaks.
	 */
	private String safe(String value) {
		return PdfText.normalize(value);
	}

	/**
	 * Selected management context for the single printed context field.
	 * @param label context heading.
	 * @param value context content.
	 */
	private record LabeledValue(String label, String value) {
	}
}
