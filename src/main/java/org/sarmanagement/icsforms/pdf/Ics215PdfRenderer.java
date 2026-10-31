/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics215Form;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the ICS 215 Operational Planning Worksheet with explicit continuation pages.
 *
 * <p>The renderer retains no per-request state; paper size, period, and content
 * are resolved from the supplied incident on each call.</p>
 */
public class Ics215PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final float FONT_SIZE = 7f;
	private static final float BOLD_SIZE = 7f;
	private static final float HEADER_SIZE = 14f;
	private static final float LINE_HEIGHT = 9f;
	private static final float CELL_PADDING = 3f;
	private static final float METADATA_HEIGHT = 42f;
	private static final float TABLE_HEADER_HEIGHT = 52f;
	private static final float FOOTER_HEIGHT = 86f;
	private static final float MIN_ASSIGNMENT_HEIGHT = 48f;
	private static final int RESOURCE_COLUMNS_PER_PAGE = 3;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 215";
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		AppData safeData = data == null ? new AppData() : data;
		Ics215Form form = formForExport(safeData);
		validate(form, safeData);
		Files.createDirectories(outputFile.getParent());
		try (PDDocument document = new PDDocument()) {
			renderPages(document, safeData, form);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Counts the exact pages rendered for the selected worksheet and paper size.
	 *
	 * @param data incident containing active and planned worksheet snapshots.
	 * @return physical page count, at least one.
	 */
	public static int pageCount(AppData data) {
		AppData safeData = data == null ? new AppData() : data;
		Ics215PdfRenderer renderer = new Ics215PdfRenderer();
		Ics215Form form = formForExport(safeData);
		PDPage sample = renderer.newIcs215Page(safeData, form);
		FormLayout layout = renderer.formLayout(sample, safeData);
		int columnPages = renderer.columnPageCount(form);
		int rowPages = renderer.planRows(form, layout).size();
		return Math.max(1, columnPages * rowPages);
	}

	/**
	 * Returns the worksheet explicitly selected for this PDF export.
	 *
	 * @param data incident data.
	 * @return active- or next-period worksheet selected by the export caller.
	 */
	static Ics215Form formForExport(AppData data) {
		return data.isIcs215ExportNextPeriod() ? data.getNextPeriodForm215() : data.getForm215();
	}

	/**
	 * Renders every resource-column group and its vertically paginated assignment rows.
	 *
	 * @param document destination PDF.
	 * @param data incident data.
	 * @param form selected worksheet.
	 * @throws IOException when drawing fails.
	 */
	private void renderPages(PDDocument document, AppData data, Ics215Form form) throws IOException {
		PDPage sample = newIcs215Page(data, form);
		FormLayout layout = formLayout(sample, data);
		List<RowLayout> rows = rowLayouts(form, data, layout);
		List<PagePlan> rowPages = planRows(rows, layout);
		int headingCount = form.getResourceHeadings().size();
		int totalPages = columnPageCount(form) * rowPages.size();
		int physicalPage = 0;
		for (int columnStart = 0; columnStart < Math.max(1, headingCount);
				columnStart += RESOURCE_COLUMNS_PER_PAGE) {
			int columnEnd = Math.min(headingCount, columnStart + RESOURCE_COLUMNS_PER_PAGE);
			for (int rowPage = 0; rowPage < rowPages.size(); rowPage++) {
				physicalPage++;
				PagePlan plan = rowPages.get(rowPage);
				PDPage page = newIcs215Page(data, form);
				document.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
					PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
					PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
					FormLayout pageLayout = formLayout(page, data);
					String continuation = continuationLabel(columnStart, columnEnd, headingCount,
							rowPage, rowPages.size());
					drawFormHeader(stream, bold, pageLayout, "ICS 215",
							"OPERATIONAL PLANNING WORKSHEET" + continuation);
					drawFormFrame(stream, pageLayout);
					drawMetadata(stream, regular, bold, pageLayout, data, form);
					float[] widths = columnWidths(pageLayout);
					float tableTop = pageLayout.top() - METADATA_HEIGHT;
					drawTableHeader(stream, regular, bold, pageLayout, widths, tableTop, form,
							columnStart, columnEnd, headingCount);
					float rowTop = tableTop - TABLE_HEADER_HEIGHT;
					float rowBottom = pageLayout.y() + FOOTER_HEIGHT;
					for (RowSegment segment : plan.segments()) {
						float height = segmentHeight(segment, rows);
						drawAssignment(stream, regular, pageLayout, widths, rowTop, height, segment, rows,
								form, columnStart, columnEnd);
						rowTop -= height;
					}
					if (plan.segments().isEmpty()) {
						drawBlankAssignment(stream, regular, pageLayout, widths, rowTop, form,
								columnStart, columnEnd);
					}
					stream.moveTo(pageLayout.x(), rowBottom);
					stream.lineTo(pageLayout.x() + pageLayout.width(), rowBottom);
					stream.stroke();
					drawTotalsAndPreparer(stream, regular, bold, pageLayout, data, form, columnStart, columnEnd,
							headingCount, rowPage == rowPages.size() - 1, physicalPage, totalPages);
				}
			}
		}
	}

	/**
	 * Creates a landscape page using the ICS-215-specific paper selection.
	 *
	 * @param data incident PDF margin settings.
	 * @param form selected worksheet.
	 * @return landscape page.
	 */
	private PDPage newIcs215Page(AppData data, Ics215Form form) {
		PDRectangle portrait = switch (form.getPaperSize()) {
			case A4 -> PDRectangle.A4;
			case LEGAL -> new PDRectangle(612f, 1008f);
			case TABLOID -> new PDRectangle(792f, 1224f);
			default -> PDRectangle.LETTER;
		};
		return new PDPage(new PDRectangle(portrait.getHeight(), portrait.getWidth()));
	}

	/**
	 * Returns the number of horizontal resource-heading groups.
	 *
	 * @param form selected worksheet.
	 * @return at least one group, including blank worksheets.
	 */
	private int columnPageCount(Ics215Form form) {
		return Math.max(1, (form.getResourceHeadings().size() + RESOURCE_COLUMNS_PER_PAGE - 1)
				/ RESOURCE_COLUMNS_PER_PAGE);
	}

	/**
	 * Builds text layouts for each ordered work assignment at the current page width.
	 *
	 * @param form selected worksheet.
	 * @param layout page layout.
	 * @return row content and wrapped fixed-field text.
	 */
	private List<RowLayout> rowLayouts(Ics215Form form, AppData data, FormLayout layout) {
		float[] widths = columnWidths(layout);
		List<RowLayout> result = new ArrayList<>();
		for (Ics215Form.WorkRow row : form.getWorkRows()) {
			if (row == null) {
				continue;
			}
			List<List<String>> cells = new ArrayList<>();
			cells.add(wrapCell(safe(row.branch).isBlank() ? form.getBranch() : row.branch, widths[0]));
			cells.add(wrapCell(managementValue(row), widths[1]));
			cells.add(wrapCell(join(row.assignment, row.specialInstructions), widths[2]));
			cells.add(List.of(""));
			cells.add(wrapCell(row.overheadPositions, widths[4]));
			cells.add(wrapCell(join(row.specialEquipment, row.supplies), widths[5]));
			cells.add(wrapCell(row.reportingLocation, widths[6]));
			cells.add(wrapCell(formatPdfDateTime(data, row.requestedArrival), widths[7]));
			int maxLines = cells.stream().mapToInt(List::size).max().orElse(1);
			result.add(new RowLayout(row, cells, Math.max(1, maxLines)));
		}
		return result;
	}

	/**
	 * Splits oversized rows into continuation segments and packs them into page bodies.
	 *
	 * @param form selected worksheet.
	 * @param layout page layout.
	 * @return exact vertical page plan.
	 */
	private List<PagePlan> planRows(Ics215Form form, FormLayout layout) {
		return planRows(rowLayouts(form, null, layout), layout);
	}

	/**
	 * Splits oversized rows into continuation segments and packs them into page bodies.
	 *
	 * @param rows wrapped row content.
	 * @param layout page layout.
	 * @return exact vertical page plan.
	 */
	private List<PagePlan> planRows(List<RowLayout> rows, FormLayout layout) {
		float tableTop = layout.top() - METADATA_HEIGHT - TABLE_HEADER_HEIGHT;
		float bodyHeight = tableTop - (layout.y() + FOOTER_HEIGHT);
		int lineCapacity = Math.max(1, (int) ((bodyHeight - CELL_PADDING * 2f) / LINE_HEIGHT));
		List<RowSegment> segments = new ArrayList<>();
		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			RowLayout row = rows.get(rowIndex);
			int offset = 0;
			while (offset < row.lineCount()) {
				int count = Math.min(lineCapacity, row.lineCount() - offset);
				segments.add(new RowSegment(rowIndex, offset, count, offset == 0));
				offset += count;
			}
		}
		List<PagePlan> pages = new ArrayList<>();
		List<RowSegment> current = new ArrayList<>();
		float used = 0f;
		for (RowSegment segment : segments) {
			float height = segmentHeight(segment, rows);
			if (!current.isEmpty() && used + height > bodyHeight) {
				pages.add(new PagePlan(List.copyOf(current)));
				current.clear();
				used = 0f;
			}
			current.add(segment);
			used += height;
		}
		if (!current.isEmpty() || pages.isEmpty()) {
			pages.add(new PagePlan(List.copyOf(current)));
		}
		return pages;
	}

	/**
	 * Computes the physical height of one assignment segment.
	 *
	 * @param segment row segment.
	 * @param rows wrapped row content.
	 * @return segment height in points.
	 */
	private float segmentHeight(RowSegment segment, List<RowLayout> rows) {
		float textHeight = segment.lineCount() * LINE_HEIGHT + CELL_PADDING * 2f;
		return segment.firstSegment() ? Math.max(MIN_ASSIGNMENT_HEIGHT, textHeight) : textHeight;
	}

	/**
	 * Draws the identifying incident and worksheet target-period blocks.
	 *
	 * @param stream page stream.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param layout page layout.
	 * @param data incident data.
	 * @param form worksheet selected for export.
	 * @throws IOException when drawing fails.
	 */
	private void drawMetadata(PDPageContentStream stream, PDType1Font regular, PDType1Font bold,
			FormLayout layout, AppData data, Ics215Form form) throws IOException {
		float x = layout.x();
		float top = layout.top();
		float split = x + layout.width() * .46f;
		float bottom = top - METADATA_HEIGHT;
		stream.moveTo(x, bottom);
		stream.lineTo(x + layout.width(), bottom);
		stream.moveTo(split, top);
		stream.lineTo(split, bottom);
		stream.stroke();
		drawText(stream, bold, 8f, x + CELL_PADDING, top - 12f,
				"1. Incident Name: " + data.getIncidentContext().getIncidentName());
		drawText(stream, bold, 8f, split + CELL_PADDING, top - 12f, "2. Operational Period");
		drawText(stream, regular, 8f, split + CELL_PADDING, top - 25f,
				"Date From: " + formatPdfDate(data, form.getTargetPeriodStart())
						+ "    Date To: " + formatPdfDate(data, form.getTargetPeriodEnd()));
		drawText(stream, regular, 8f, split + CELL_PADDING, top - 37f,
				"Time From: " + formatPdfTime(data, form.getTargetPeriodStart())
						+ "    Time To: " + formatPdfTime(data, form.getTargetPeriodEnd()));
	}

	/**
	 * Draws numbered block headings and the current resource-column group.
	 *
	 * @param stream page stream.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param layout page layout.
	 * @param widths column widths.
	 * @param top header top.
	 * @param form selected worksheet.
	 * @param columnStart first heading index in this page group.
	 * @param columnEnd exclusive last heading index in this page group.
	 * @param headingCount total headings across all horizontal continuations.
	 * @throws IOException when drawing fails.
	 */
	private void drawTableHeader(PDPageContentStream stream, PDType1Font regular, PDType1Font bold,
			FormLayout layout, float[] widths, float top, Ics215Form form, int columnStart,
			int columnEnd, int headingCount) throws IOException {
		float bottom = top - TABLE_HEADER_HEIGHT;
		drawTableGrid(stream, layout, widths, top, TABLE_HEADER_HEIGHT);
		int resourceSlots = RESOURCE_COLUMNS_PER_PAGE;
		String[] labels = {"3. Branch", "4. Division, Group, Other", "5. Work Assignment / Instructions",
				"6. Resource\nReq/Have/Need", "7. Overhead", "8. Special Equipment / Supplies",
				"9. Reporting Location", "10. Requested Arrival"};
		int[] fixedIndices = {0, 1, 2, 3, 4 + resourceSlots, 5 + resourceSlots, 6 + resourceSlots,
				7 + resourceSlots};
		for (int index = 0; index < labels.length; index++) {
			float x = columnX(layout, widths, fixedIndices[index]);
			drawWrapped(stream, bold, BOLD_SIZE, x + CELL_PADDING, top - CELL_PADDING - BOLD_SIZE,
					widths[fixedIndices[index]] - CELL_PADDING * 2f, labels[index]);
		}
		for (int slot = 0; slot < resourceSlots; slot++) {
			int headingIndex = columnStart + slot;
			if (headingIndex >= columnEnd) {
				continue;
			}
			Ics215Form.ResourceHeading heading = form.getResourceHeadings().get(headingIndex);
			String label = heading.category + "\n" + heading.kind + "\n" + heading.type
					+ "\nUnit: " + heading.countingUnit;
			float x = columnX(layout, widths, 4 + slot);
			drawWrapped(stream, bold, BOLD_SIZE, x + CELL_PADDING, top - CELL_PADDING - BOLD_SIZE,
					widths[4 + slot] - CELL_PADDING * 2f, label);
		}
		drawText(stream, regular, FONT_SIZE, layout.x() + CELL_PADDING, bottom + 2f,
				"Resource columns " + (headingCount == 0 ? "none" : (columnStart + 1) + "–" + columnEnd)
						+ " of " + headingCount);
	}

	/**
	 * Draws one row segment, with three separate resource quantity subrows.
	 *
	 * @param stream page stream.
	 * @param regular regular font.
	 * @param layout page layout.
	 * @param widths column widths.
	 * @param top row top.
	 * @param height row height.
	 * @param segment row segment.
	 * @param rows wrapped row content.
	 * @param form selected worksheet.
	 * @param columnStart first heading index.
	 * @param columnEnd exclusive last heading index.
	 * @throws IOException when drawing fails.
	 */
	private void drawAssignment(PDPageContentStream stream, PDType1Font regular, FormLayout layout,
			float[] widths, float top, float height, RowSegment segment, List<RowLayout> rows,
			Ics215Form form, int columnStart, int columnEnd) throws IOException {
		RowLayout rowLayout = rows.get(segment.rowIndex());
		Ics215Form.WorkRow row = rowLayout.row();
		float bottom = top - height;
		drawHorizontal(stream, layout.x(), layout.x() + layout.width(), bottom);
		for (int column = 1; column < widths.length; column++) {
			float x = columnX(layout, widths, column);
			stream.moveTo(x, top);
			stream.lineTo(x, bottom);
		}
		stream.stroke();
		for (int index = 0; index < rowLayout.cells().size(); index++) {
			if (index == 3) {
				continue;
			}
			int widthIndex = fixedCellWidthIndex(index);
			float x = columnX(layout, widths, widthIndex) + CELL_PADDING;
			drawCellLines(stream, regular, rowLayout.cells().get(index), segment,
					x, top - CELL_PADDING - FONT_SIZE);
		}
		int resourceLabelIndex = 3;
		float resourceTop = top;
		if (segment.firstSegment()) {
			float subrow = height / 3f;
			for (int metric = 0; metric < 3; metric++) {
				float metricTop = top - metric * subrow;
				if (metric > 0) {
					drawHorizontal(stream, columnX(layout, widths, resourceLabelIndex), columnX(layout, widths,
							4 + RESOURCE_COLUMNS_PER_PAGE), metricTop);
				}
				drawText(stream, regular, FONT_SIZE, columnX(layout, widths, resourceLabelIndex) + CELL_PADDING,
						metricTop - Math.min(subrow - 2f, 12f), new String[] {"Req.", "Have", "Need"}[metric]);
				for (int headingIndex = columnStart; headingIndex < columnEnd; headingIndex++) {
					int slot = headingIndex - columnStart;
					Ics215Form.ResourceAllocation allocation = row.resourceAllocations == null
							? null : row.resourceAllocations.get(form.getResourceHeadings().get(headingIndex).id);
					Ics215Form.CountPair pair = switch (metric) {
						case 0 -> allocation == null ? null : allocation.required;
						case 1 -> allocation == null ? null : allocation.acceptedHave;
						default -> allocation == null ? null : form.need(row.id,
								form.getResourceHeadings().get(headingIndex).id);
					};
					drawText(stream, regular, FONT_SIZE,
							columnX(layout, widths, 4 + slot) + CELL_PADDING,
							metricTop - Math.min(subrow - 2f, 12f), pairText(pair));
				}
			}
		} else {
			drawText(stream, regular, FONT_SIZE, columnX(layout, widths, resourceLabelIndex) + CELL_PADDING,
					top - CELL_PADDING - FONT_SIZE, "Details continued");
			drawText(stream, regular, FONT_SIZE, columnX(layout, widths, 4) + CELL_PADDING,
					top - CELL_PADDING - FONT_SIZE, "Counts on first segment");
		}
	}

	/**
	 * Draws the blank Required/Have/Need grid for an empty worksheet.
	 *
	 * @param stream page stream.
	 * @param regular regular font.
	 * @param layout page layout.
	 * @param widths column widths.
	 * @param top table-body top.
	 * @param form selected worksheet.
	 * @param columnStart first heading index.
	 * @param columnEnd exclusive last heading index.
	 * @throws IOException when drawing fails.
	 */
	private void drawBlankAssignment(PDPageContentStream stream, PDType1Font regular, FormLayout layout,
			float[] widths, float top, Ics215Form form, int columnStart, int columnEnd) throws IOException {
		float height = MIN_ASSIGNMENT_HEIGHT;
		drawHorizontal(stream, layout.x(), layout.x() + layout.width(), top - height);
		for (int column = 1; column < widths.length; column++) {
			float x = columnX(layout, widths, column);
			stream.moveTo(x, top);
			stream.lineTo(x, top - height);
		}
		stream.stroke();
		float subrow = height / 3f;
		for (int metric = 0; metric < 3; metric++) {
			float y = top - metric * subrow;
			if (metric > 0) {
				drawHorizontal(stream, columnX(layout, widths, 3), columnX(layout, widths, 4 + RESOURCE_COLUMNS_PER_PAGE),
						y);
			}
			drawText(stream, regular, FONT_SIZE, columnX(layout, widths, 3) + CELL_PADDING,
					y - 12f, new String[] {"Req.", "Have", "Need"}[metric]);
		}
	}

	/**
	 * Draws blocks 11–14 and clearly labels repeated continuation totals.
	 *
	 * @param stream page stream.
	 * @param regular regular font.
	 * @param bold bold font.
	 * @param layout page layout.
	 * @param form selected worksheet.
	 * @param columnStart first resource heading.
	 * @param columnEnd exclusive last resource heading.
	 * @param headingCount total headings.
	 * @param finalVerticalPage true on the last row page in this column group.
	 * @param pageNumber physical worksheet page number.
	 * @param totalPages total physical pages.
	 * @throws IOException when drawing fails.
	 */
	private void drawTotalsAndPreparer(PDPageContentStream stream, PDType1Font regular, PDType1Font bold,
			FormLayout layout, AppData data, Ics215Form form, int columnStart, int columnEnd, int headingCount,
			boolean finalVerticalPage, int pageNumber, int totalPages) throws IOException {
		float x = layout.x();
		float top = layout.y() + FOOTER_HEIGHT;
		float leftWidth = layout.width() * .66f;
		float rightX = x + leftWidth;
		stream.moveTo(x, top);
		stream.lineTo(x + layout.width(), top);
		stream.moveTo(rightX, layout.y());
		stream.lineTo(rightX, top);
		stream.stroke();
		drawText(stream, bold, BOLD_SIZE, x + CELL_PADDING, top - 11f, "ICS 215 — TOTALS");
		if (finalVerticalPage) {
			String[] labels = {"11. Total Resources Required", "12. Total Resources Have on Hand",
					"13. Total Resources Need To Order"};
			float rowHeight = 16f;
			for (int metric = 0; metric < labels.length; metric++) {
				float y = top - 25f - metric * rowHeight;
				drawText(stream, bold, BOLD_SIZE, x + CELL_PADDING, y, labels[metric]);
				if (columnEnd > columnStart) {
					float valuesStart = x + leftWidth * .50f;
					float valueWidth = (leftWidth * .50f) / (columnEnd - columnStart);
					for (int index = columnStart; index < columnEnd; index++) {
						Ics215Form.ResourceHeading heading = form.getResourceHeadings().get(index);
						Ics215Form.CountPair totals = switch (metric) {
							case 0 -> form.totalRequired(heading.id);
							case 1 -> form.totalOnHand(heading.id);
							default -> form.totalNeedToOrder(heading.id);
						};
						drawText(stream, regular, FONT_SIZE, valuesStart + (index - columnStart) * valueWidth,
								y, pairText(totals));
					}
				}
				if (metric < labels.length - 1) {
					drawHorizontal(stream, x, rightX, top - 30f - metric * rowHeight);
				}
			}
			drawText(stream, regular, FONT_SIZE, x + CELL_PADDING, layout.y() + 8f,
					"Resource columns " + (headingCount == 0 ? "none" : (columnStart + 1) + "–" + columnEnd)
							+ " of " + headingCount + "; unique on-hand pool is not row allocations.");
		} else {
			drawText(stream, bold, BOLD_SIZE, x + CELL_PADDING, top - 39f,
					"Continuation: totals appear on the final assignment page for this resource-column set.");
			drawText(stream, regular, FONT_SIZE, x + CELL_PADDING, layout.y() + 8f,
					"Resource column continuation " + (columnStart + 1) + "–" + columnEnd
							+ " of " + headingCount + "; totals are not additional resources.");
		}
		drawText(stream, bold, BOLD_SIZE, rightX + CELL_PADDING, top - 11f, "14. Prepared by:");
		drawText(stream, regular, FONT_SIZE, rightX + CELL_PADDING, top - 25f,
				"Name: " + form.getPreparerName());
		drawText(stream, regular, FONT_SIZE, rightX + CELL_PADDING, top - 38f,
				"Position/Title: " + form.getPreparerPositionTitle());
		drawText(stream, regular, FONT_SIZE, rightX + CELL_PADDING, top - 51f,
				"Signature: " + form.getPreparerSignature());
		drawText(stream, regular, FONT_SIZE, rightX + CELL_PADDING, top - 64f,
				"Date/Time: " + formatPdfDateTime(data, form.getPreparedDateTime()));
		drawText(stream, regular, FONT_SIZE, rightX + CELL_PADDING, layout.y() + 8f,
				"ICS 215, Page " + pageNumber + " of " + totalPages
						+ (form.getIapPage().isBlank() ? "" : " — IAP Page " + form.getIapPage()));
	}

	/**
	 * Draws outer and internal matrix grid lines for a heading row.
	 *
	 * @param stream page stream.
	 * @param layout page layout.
	 * @param widths all fixed and resource widths.
	 * @param top row top.
	 * @param height row height.
	 * @throws IOException when drawing fails.
	 */
	private void drawTableGrid(PDPageContentStream stream, FormLayout layout, float[] widths,
			float top, float height) throws IOException {
		float bottom = top - height;
		drawHorizontal(stream, layout.x(), layout.x() + layout.width(), bottom);
		for (int column = 1; column < widths.length; column++) {
			float x = columnX(layout, widths, column);
			stream.moveTo(x, top);
			stream.lineTo(x, bottom);
		}
		stream.stroke();
	}

	/**
	 * Calculates fixed block widths and resource column widths for a landscape page.
	 *
	 * @param layout page layout.
	 * @return ordered widths: branch, context, assignment, metric label, three resources, overhead, equipment,
	 *         reporting, arrival.
	 */
	private float[] columnWidths(FormLayout layout) {
		float width = layout.width();
		float[] fixed = {width * .055f, width * .08f, width * .155f, width * .045f,
				width * .075f, width * .08f, width * .07f, width * .07f};
		float used = 0f;
		for (float value : fixed) {
			used += value;
		}
		float resource = (width - used) / RESOURCE_COLUMNS_PER_PAGE;
		float[] columns = new float[fixed.length + RESOURCE_COLUMNS_PER_PAGE];
		System.arraycopy(fixed, 0, columns, 0, 4);
		for (int index = 0; index < RESOURCE_COLUMNS_PER_PAGE; index++) {
			columns[4 + index] = resource;
		}
		System.arraycopy(fixed, 4, columns, 7, 4);
		return columns;
	}

	/**
	 * Returns the left edge for one matrix column.
	 *
	 * @param layout page layout.
	 * @param widths ordered column widths.
	 * @param column target index.
	 * @return absolute left coordinate.
	 */
	private float columnX(FormLayout layout, float[] widths, int column) {
		float x = layout.x();
		for (int index = 0; index < column && index < widths.length; index++) {
			x += widths[index];
		}
		return x;
	}

	/**
	 * Maps a fixed cell to its width index when resource columns are inserted.
	 *
	 * @param cell fixed content-cell number.
	 * @return matrix column index.
	 */
	private int fixedCellWidthIndex(int cell) {
		return cell < 4 ? cell : cell + RESOURCE_COLUMNS_PER_PAGE;
	}

	/**
	 * Wraps one fixed cell's content into the available point width.
	 *
	 * @param value source text.
	 * @param width column width in points.
	 * @return wrapped lines, including blank lines for explicit paragraphs.
	 */
	private List<String> wrapCell(String value, float width) {
		int characters = Math.max(1, (int) ((width - CELL_PADDING * 2f) / (FONT_SIZE * .53f)));
		List<String> result = new ArrayList<>();
		for (String paragraph : safe(value).split("\n", -1)) {
			for (String line : wrap(paragraph, characters)) {
				if (line.length() <= characters) {
					result.add(line);
				} else {
					for (int index = 0; index < line.length(); index += characters) {
						result.add(line.substring(index, Math.min(line.length(), index + characters)));
					}
				}
			}
		}
		return result.isEmpty() ? List.of("") : result;
	}

	/**
	 * Draws only the line slice belonging to one vertical row segment.
	 *
	 * @param stream page stream.
	 * @param font regular font.
	 * @param lines wrapped cell text.
	 * @param segment current row segment.
	 * @param x left coordinate.
	 * @param baseline first baseline.
	 * @throws IOException when drawing fails.
	 */
	private void drawCellLines(PDPageContentStream stream, PDType1Font font, List<String> lines,
			RowSegment segment, float x, float baseline) throws IOException {
		int end = Math.min(lines.size(), segment.lineOffset() + segment.lineCount());
		for (int index = segment.lineOffset(); index < end; index++) {
			drawText(stream, font, FONT_SIZE, x, baseline - (index - segment.lineOffset()) * LINE_HEIGHT,
					lines.get(index));
		}
	}

	/**
	 * Draws wrapped header text within one matrix cell.
	 *
	 * @param stream page stream.
	 * @param font drawing font.
	 * @param size font size.
	 * @param x left coordinate.
	 * @param baseline first baseline.
	 * @param width available width.
	 * @param value source text.
	 * @throws IOException when drawing fails.
	 */
	private void drawWrapped(PDPageContentStream stream, PDType1Font font, float size, float x,
			float baseline, float width, String value) throws IOException {
		List<String> lines = wrapCell(value, width);
		for (int index = 0; index < lines.size(); index++) {
			drawText(stream, font, size, x, baseline - index * LINE_HEIGHT, lines.get(index));
		}
	}

	/**
	 * Draws one single-line string using a balanced PDF text block.
	 *
	 * @param stream page stream.
	 * @param font selected font.
	 * @param size font size.
	 * @param x baseline x coordinate.
	 * @param y baseline y coordinate.
	 * @param value source text.
	 * @throws IOException when drawing fails.
	 */
	private void drawText(PDPageContentStream stream, PDType1Font font, float size, float x,
			float y, String value) throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, size);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(safe(value)));
		}
	}

	/**
	 * Draws a horizontal grid line.
	 *
	 * @param stream page stream.
	 * @param startX line start.
	 * @param endX line end.
	 * @param y vertical coordinate.
	 * @throws IOException when drawing fails.
	 */
	private void drawHorizontal(PDPageContentStream stream, float startX, float endX, float y) throws IOException {
		stream.moveTo(startX, y);
		stream.lineTo(endX, y);
		stream.stroke();
	}

	/**
	 * Builds a continuation label that distinguishes resource and assignment pages.
	 *
	 * @param columnStart first heading index.
	 * @param columnEnd exclusive last heading index.
	 * @param headingCount total headings.
	 * @param rowPage vertical page index.
	 * @param rowPageCount vertical page count.
	 * @return empty text for the first page, otherwise a visible continuation.
	 */
	private String continuationLabel(int columnStart, int columnEnd, int headingCount,
			int rowPage, int rowPageCount) {
		if (columnStart == 0 && rowPage == 0) {
			return "";
		}
		StringBuilder label = new StringBuilder(" (CONTINUED");
		if (headingCount > RESOURCE_COLUMNS_PER_PAGE) {
			label.append(" — resource columns ").append(columnStart + 1).append("–").append(columnEnd)
					.append(" of ").append(headingCount);
		}
		if (rowPageCount > 1) {
			label.append(" — assignment page ").append(rowPage + 1).append(" of ").append(rowPageCount);
		}
		return label.append(')').toString();
	}

	/**
	 * Returns the explicit ICS management value for one row.
	 *
	 * @param row assignment row.
	 * @return context label and value.
	 */
	private String managementValue(Ics215Form.WorkRow row) {
		String key = row.managementContext == null ? "" : row.managementContext;
		return switch (key) {
			case org.sarmanagement.icsforms.model.Ics204Form.MANAGEMENT_BRANCH -> "Branch: " + safe(row.branch);
			case org.sarmanagement.icsforms.model.Ics204Form.MANAGEMENT_DIVISION -> "Division: " + safe(row.division);
			case org.sarmanagement.icsforms.model.Ics204Form.MANAGEMENT_GROUP -> "Group: " + safe(row.group);
			default -> row.other == null || row.other.isBlank()
					? "Staging: " + safe(row.stagingArea) : "Other: " + safe(row.other);
		};
	}

	/**
	 * Formats an ICS quantity pair using slash notation and preserving unknown values.
	 *
	 * @param pair resource quantity pair.
	 * @return single-resource/team count pair.
	 */
	private static String pairText(Ics215Form.CountPair pair) {
		if (pair == null) {
			return "?/?";
		}
		return (pair.singleResources == null ? "?" : pair.singleResources.toString())
				+ "/" + (pair.teams == null ? "?" : pair.teams.toString());
	}

	/**
	 * Formats detail text by combining both supported source fields.
	 *
	 * @param first primary text.
	 * @param second supplemental text.
	 * @return newline-separated text.
	 */
	private static String join(String first, String second) {
		if (first == null || first.isBlank()) {
			return safe(second);
		}
		return second == null || second.isBlank() ? first : first + "\n" + second;
	}

	/**
	 * Validates every printable worksheet value before generating output.
	 *
	 * @param form selected worksheet.
	 * @param data incident data.
	 */
	private void validate(Ics215Form form, AppData data) {
		form.validateOrThrow();
		PdfText.validate(data.getIncidentContext().getIncidentName(), getFormKey(), "incidentName");
		PdfText.validate(form.getBranch(), getFormKey(), "branch");
		PdfText.validate(form.getSpecialInstructions(), getFormKey(), "specialInstructions");
		PdfText.validate(form.getPreparerName(), getFormKey(), "preparerName");
		PdfText.validate(form.getPreparerPositionTitle(), getFormKey(), "preparerPositionTitle");
		PdfText.validate(form.getPreparerSignature(), getFormKey(), "preparerSignature");
		for (int index = 0; index < form.getResourceHeadings().size(); index++) {
			Ics215Form.ResourceHeading heading = form.getResourceHeadings().get(index);
			PdfText.validate(heading.category, getFormKey(), "resourceHeadings[" + index + "].category");
			PdfText.validate(heading.kind, getFormKey(), "resourceHeadings[" + index + "].kind");
			PdfText.validate(heading.type, getFormKey(), "resourceHeadings[" + index + "].type");
			PdfText.validate(heading.countingUnit, getFormKey(), "resourceHeadings[" + index + "].countingUnit");
		}
		for (int index = 0; index < form.getWorkRows().size(); index++) {
			Ics215Form.WorkRow row = form.getWorkRows().get(index);
			if (row == null) {
				continue;
			}
			String path = "workRows[" + index + "].";
			PdfText.validate(row.branch, getFormKey(), path + "branch");
			PdfText.validate(row.division, getFormKey(), path + "division");
			PdfText.validate(row.group, getFormKey(), path + "group");
			PdfText.validate(row.other, getFormKey(), path + "other");
			PdfText.validate(row.stagingArea, getFormKey(), path + "stagingArea");
			PdfText.validate(row.assignment, getFormKey(), path + "assignment");
			PdfText.validate(row.specialInstructions, getFormKey(), path + "specialInstructions");
			PdfText.validate(row.overheadPositions, getFormKey(), path + "overheadPositions");
			PdfText.validate(row.specialEquipment, getFormKey(), path + "specialEquipment");
			PdfText.validate(row.supplies, getFormKey(), path + "supplies");
			PdfText.validate(row.reportingLocation, getFormKey(), path + "reportingLocation");
		}
	}

	/**
	 * Returns a nonnull value for printable content.
	 *
	 * @param value optional source text.
	 * @return nonnull text.
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * A row and its wrapped content, planned independently of PDF page state.
	 *
	 * @param row source assignment.
	 * @param cells wrapped fixed-column values.
	 * @param lineCount maximum fixed-cell line count.
	 */
	private record RowLayout(Ics215Form.WorkRow row, List<List<String>> cells, int lineCount) {
	}

	/**
	 * One assignment row segment, with resource values only on its first segment.
	 *
	 * @param rowIndex index in the detached row layout list.
	 * @param lineOffset first text line.
	 * @param lineCount number of wrapped lines.
	 * @param firstSegment true for the segment with resource values.
	 */
	private record RowSegment(int rowIndex, int lineOffset, int lineCount, boolean firstSegment) {
	}

	/**
	 * Vertically paginated assignment segments for one resource-column group.
	 *
	 * @param segments assignment segments on this page.
	 */
	private record PagePlan(List<RowSegment> segments) {
	}
}
