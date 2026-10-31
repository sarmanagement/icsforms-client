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
import org.sarmanagement.icsforms.model.Ics207Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured PDF renderer for the Incident Organization Chart (ICS 207) form.
 *
 * <p>
 * Produces organization-chart pages using the configured paper and margins:
 * <ol>
 * <li>Section 1 — Incident Name</li>
 * <li>Section 2 — Operational Period</li>
 * <li>Section 3 — Graphical organization chart</li>
 * <li>Section 4 — Prepared by footer</li>
 * </ol>
 */
public class Ics207PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private static final float BODY_FONT_SIZE = 9f;
	private static final float HEADING_FONT_SIZE = 9f;
	private static final float CELL_PADDING = 4f;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 207";
	}

	/**
	 * Counts the complete chart pages using the same node measurements as rendering.
	 *
	 * @param data source incident, or null for a blank chart.
	 * @return number of organization-chart pages.
	 */
	public static int pageCount(AppData data) {
		try {
			return new Ics207PdfRenderer().countPages(data == null ? new AppData() : data);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/**
	 * Measures pagination without creating PDF content streams or rendering the form.
	 *
	 * @param data incident organization and layout settings.
	 * @return largest page count among the four section columns.
	 * @throws IOException when font measurement fails.
	 */
	private int countPages(AppData data) throws IOException {
		FormLayout layout = formLayout(newPage(data), data);
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		float chartTop = layout.top() - 50f - CELL_PADDING - HEADING_FONT_SIZE - 8f;
		float nodeTop = sectionBarY(chartTop, buildCmdStaff(data.getOrganizationalChart()).size()) - 50f;
		float bottom = layout.y() + 50f + 4f;
		float width = (layout.width() - 2f * CELL_PADDING) / 4f - 8f;
		int result = 1;
		for (List<String[]> section : sectionNodes(data)) {
			int pages = 1;
			float top = nodeTop;
			for (String[] unit : section) {
				Node node = measureNode(unit, bold, regular, width, 18f);
				if (node.height() > nodeTop - bottom) {
					throw new IllegalArgumentException("PDF margins or organization content leave insufficient space "
							+ "for an ICS 207 assignment node");
				}
				if (top - node.height() < bottom) {
					pages++;
					top = nodeTop;
				}
				top -= node.height() + 2f;
			}
			result = Math.max(result, pages);
		}
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		PdfText.validateForm(data, getFormKey());
		Files.createDirectories(outputFile.getParent());
		AppData safeData = data == null ? new AppData() : data;
		try (PDDocument document = new PDDocument()) {
			renderPages(document, safeData);
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Renders every section node without retaining pagination state on the renderer.
	 *
	 * @param document destination PDF.
	 * @param data incident organization and assignments.
	 * @throws IOException when drawing fails.
	 */
	private void renderPages(PDDocument document, AppData data) throws IOException {
		List<List<String[]>> remaining = sectionNodes(data);
		int pageNumber = 1;
		do {
			int before = remaining.stream().mapToInt(List::size).sum();
			remaining = renderPage(document, data, remaining, pageNumber++);
			int after = remaining.stream().mapToInt(List::size).sum();
			if (after > 0 && after == before) {
				throw new IllegalArgumentException("PDF margins or organization content leave insufficient space "
						+ "for an ICS 207 assignment node");
			}
		} while (remaining.stream().anyMatch(section -> !section.isEmpty()));
	}

	/**
	 * Builds the four ordered section columns independently of PDF output.
	 *
	 * @param data incident organization and assignments.
	 * @return operations, planning, logistics and finance node lists.
	 */
	private List<List<String[]>> sectionNodes(AppData data) {
		OrganizationalChart chart = data.getOrganizationalChart();
		return List.of(buildOpsSubUnits(chart, data.getSarTaskAssignments()), buildPlanningSubUnits(chart),
				buildLogisticsSubUnits(chart, data.getMedicalUnitLeaderAssignment().getName()),
				buildFinanceSubUnits(chart));
	}

	/**
	 * Draws a complete numbered chart form and the section nodes fitting this page.
	 *
	 * @param document destination PDF.
	 * @param data incident organization and layout settings.
	 * @param subUnits remaining nodes in operations, planning, logistics and finance order.
	 * @param pageNumber one-based form page.
	 * @return section nodes that require another chart page.
	 * @throws IOException when drawing fails.
	 */
	private List<List<String[]>> renderPage(PDDocument document, AppData data, List<List<String[]>> subUnits,
			int pageNumber) throws IOException {
		IncidentContext context = data.getIncidentContext();
		OrganizationalChart chart = data.getOrganizationalChart();
		Ics207Form form = data.getForm207();

		PDPage page = newPage(data);
		document.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			FormLayout layout = formLayout(page, data);

			drawFormHeader(stream, bold, layout, "ICS 207", "INCIDENT ORGANIZATION CHART", pageNumber);
			drawFormFrame(stream, layout);

			float y = layout.top();
			float fullW = layout.width();
			float halfW = fullW / 2f;
			float x = layout.x();
			float formBot = layout.y();

			// Row 1: Incident Name | Operational Period
			float row1H = 50f;
			drawCell(stream, x, y - row1H, halfW, row1H);
			drawCell(stream, x + halfW, y - row1H, halfW, row1H);
			drawHeading(stream, bold, x, y, "1. Incident Name");
			writeCellText(stream, regular, x + CELL_PADDING, y - CELL_PADDING - HEADING_FONT_SIZE - 16f,
					safe(context == null ? "" : context.getIncidentName()));
			drawOpPeriod(stream, bold, regular, x + halfW, y - row1H, halfW, row1H, context, data);
			y -= row1H;

			// Footer row: Prepared By
			float footerH = 50f;
			float footerY = formBot;

			// Row 2 (middle): org chart
			float chartH = y - footerY - footerH;
			List<List<String[]>> remaining = drawOrgChart(stream, bold, regular, x, footerY + footerH, fullW,
					chartH, chart, subUnits);

			// Footer: Prepared by
			drawFooter(stream, bold, regular, x, footerY, fullW, footerH, form, data, pageNumber);
			return remaining;
		}
	}

	// -----------------------------------------------------------------------
	// Section sub-renderers
	// -----------------------------------------------------------------------

	private void drawOpPeriod(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			float width, float height, IncidentContext ctx, AppData data) throws IOException {
		drawHeading(stream, bold, x, y + height, "2. Operational Period");
		float labelY = y + height - CELL_PADDING - HEADING_FONT_SIZE - 14f;
		float halfW = width / 2f;
		drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY, "Date From",
				formatDate(data, ctx == null ? null : ctx.getOperationalPeriodStart()));
		drawInlinePair(stream, bold, regular, x + CELL_PADDING + halfW, labelY, "Date To",
				formatDate(data, ctx == null ? null : ctx.getOperationalPeriodEnd()));
		drawInlinePair(stream, bold, regular, x + CELL_PADDING, labelY - 16f, "Time From",
				formatTime(data, ctx == null ? null : ctx.getOperationalPeriodStart()));
		drawInlinePair(stream, bold, regular, x + CELL_PADDING + halfW, labelY - 16f, "Time To",
				formatTime(data, ctx == null ? null : ctx.getOperationalPeriodEnd()));
	}

	/**
	 * Draws preparation metadata and the continuation-adjusted IAP page.
	 *
	 * @param stream page stream.
	 * @param bold label font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y bottom edge.
	 * @param width footer width.
	 * @param height footer height.
	 * @param form preparation metadata.
	 * @param data timezone settings.
	 * @param pageNumber one-based form page.
	 * @throws IOException when drawing fails.
	 */
	private void drawFooter(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			float width, float height, Ics207Form form, AppData data, int pageNumber) throws IOException {
		drawPreparedByMetadataSection(stream, bold, regular, BODY_FONT_SIZE, HEADING_FONT_SIZE, CELL_PADDING, x, y,
				width, height, 18f, 8f, "4. Prepared By", safe(form.getPreparedByName()),
				safe(form.getPreparedByPositionTitle()), "____________________", "ICS 207",
				addPageOffset(form.getIapPage(), pageNumber - 1),
				formatDateTime(data, form.getPreparedDateTime()));
	}

	// -----------------------------------------------------------------------
	// Graphical org chart (section 3)
	// -----------------------------------------------------------------------

	/**
	 * Draws the organization tree, with inline command-staff assignments beside its stem.
	 *
	 * @param stream page content stream.
	 * @param bold title font.
	 * @param regular name font.
	 * @param sX chart left edge.
	 * @param sY chart bottom edge.
	 * @param width chart width.
	 * @param height chart height.
	 * @param chart organization assignments.
	 * @param subUnits remaining section nodes in column order.
	 * @return nodes that did not fit below their section chief on this page.
	 * @throws IOException when PDF drawing fails.
	 */
	private List<List<String[]>> drawOrgChart(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			float sX, float sY, float width, float height, OrganizationalChart chart, List<List<String[]>> subUnits)
			throws IOException {
		drawCell(stream, sX, sY, width, height);
		drawHeading(stream, bold, sX, sY + height, "3. Organization Chart");

		final float LEFT = sX + CELL_PADDING;
		final float CWIDTH = width - 2f * CELL_PADDING;
		final float TOP = sY + height - CELL_PADDING - HEADING_FONT_SIZE - 8f;

		// --- IC box ---
		List<String> icNames = chart.getIncidentCommanders();
		List<String> nonBlankIc = new ArrayList<>();
		if (icNames != null) {
			for (String n : icNames) {
				if (!safe(n).isBlank())
					nonBlankIc.add(n);
			}
		}
		boolean isUC = nonBlankIc.size() > 1;
		String icLabel = isUC ? "Unified Command" : "Incident Commander";
		String icNameLine = String.join(", ", nonBlankIc);

		float icBoxW = Math.min(180f, CWIDTH * 0.36f);
		float icBoxH = 32f;
		// Center the IC box; command staff branch off the vertical stem to the left
		float icCX = LEFT + CWIDTH * 0.5f;
		float icLeft = icCX - icBoxW / 2f;
		float icTop = TOP - 4f;
		float icBot = icTop - icBoxH;

		// --- Command staff (always shown), right of vertical stem ---
		List<String[]> cmdStaff = buildCmdStaff(chart);

		float sBoxH = 18f, sGap = 3f;
		// Staff boxes leave space beside the stem and at the chart's right edge.
		float sLeft = icCX + 20f;
		float sBoxW = ((LEFT + CWIDTH) - icCX - 12f) * 2f / 3f;

		for (int i = 0; i < cmdStaff.size(); i++) {
			float sTop = icBot - 4f - i * (sBoxH + sGap);
			float sBotY = sTop - sBoxH;
			float midY = sTop - sBoxH / 2f;
			// Horizontal connector from vertical stem (icCX) rightward to staff box left
			// edge.
			stream.moveTo(icCX, midY);
			stream.lineTo(sLeft, midY);
			stream.stroke();
			// Staff box
			stream.addRect(sLeft, sBotY, sBoxW, sBoxH);
			stream.stroke();
			drawCommandStaffText(stream, bold, regular, cmdStaff.get(i)[0], cmdStaff.get(i)[1],
					sLeft + 2f, midY - 2.5f, sBoxW - 4f);
		}

		// IC box on top of connectors
		stream.addRect(icLeft, icBot, icBoxW, icBoxH);
		stream.stroke();
		drawCentered(stream, bold, 7f, icLabel, icLeft, icTop - 9f, icBoxW);
		drawCentered(stream, regular, 7f, truncate(icNameLine, (int) (icBoxW / 4.5f)), icLeft, icTop - 18f, icBoxW);

		// --- Vertical stem from IC box bottom to section bar ---
		float sBarY = sectionBarY(TOP, cmdStaff.size());
		stream.moveTo(icCX, icBot);
		stream.lineTo(icCX, sBarY);
		stream.stroke();

		// --- Four section boxes ---
		float secW = CWIDTH / 4f;
		float secBoxH = 32f;
		stream.moveTo(LEFT + secW / 2f, sBarY);
		stream.lineTo(LEFT + CWIDTH - secW / 2f, sBarY);
		stream.stroke();

		float secBoxTop = sBarY - 12f;
		float secBoxBot = secBoxTop - secBoxH;

		String[] secTitles = {"Operations", "Planning", "Logistics", "Finance/Admin"};
		String[] secChiefs = {safe(chart.getOperationsSectionChiefName()), safe(chart.getPlanningSectionChiefName()),
				safe(chart.getLogisticsSectionChiefName()), safe(chart.getFinanceAdminSectionChiefName())};

		float[] secCX = new float[4];
		for (int i = 0; i < 4; i++) {
			float secLeft = LEFT + i * secW;
			secCX[i] = secLeft + secW / 2f;
			stream.moveTo(secCX[i], sBarY);
			stream.lineTo(secCX[i], secBoxTop);
			stream.stroke();
			float bL = secLeft + 4f, bW = secW - 8f;
			stream.addRect(bL, secBoxBot, bW, secBoxH);
			stream.stroke();
			drawCentered(stream, bold, 7f, secTitles[i], bL, secBoxTop - 9f, bW);
			drawCentered(stream, bold, 7f, "Section Chief", bL, secBoxTop - 17f, bW);
			if (!secChiefs[i].isBlank()) {
				drawCentered(stream, regular, 7f, truncate(secChiefs[i], (int) (bW / 4.3f)), bL, secBoxTop - 26f, bW);
			}
		}

		// --- Sub-unit boxes below each section ---
		float subH = 18f;
		float subGap = 2f;
		float subTopStart = secBoxBot - 6f;

		List<List<String[]>> remaining = new ArrayList<>();
		for (int section = 0; section < subUnits.size(); section++) {
			remaining.add(drawSubUnits(stream, bold, regular, LEFT + section * secW, secCX[section],
					secBoxBot, secW, subTopStart, subH, subGap, subUnits.get(section), sY));
		}
		return remaining;
	}

	/**
	 * Locates the section connector bar below the incident commander and command staff.
	 *
	 * @param chartTop chart content top.
	 * @param staffCount number of command-staff boxes.
	 * @return section bar vertical coordinate.
	 */
	private float sectionBarY(float chartTop, int staffCount) {
		float commanderBottom = chartTop - 4f - 32f;
		float staffBottom = staffCount == 0 ? commanderBottom
				: commanderBottom - 4f - (staffCount - 1) * 21f - 18f;
		return Math.min(commanderBottom, staffBottom) - 14f;
	}

	/**
	 * Wraps every node field and measures its complete height without truncation.
	 *
	 * @param unit node title followed by its value fields.
	 * @param bold title font.
	 * @param regular value font.
	 * @param width node width.
	 * @param minimumHeight minimum box height.
	 * @return wrapped fields and required box height.
	 * @throws IOException when font measurement fails.
	 */
	private Node measureNode(String[] unit, PDType1Font bold, PDType1Font regular, float width,
			float minimumHeight) throws IOException {
		List<String> labels = wrapText(unit[0], bold, 6.5f, width - 4f);
		List<String> values = new ArrayList<>();
		for (int field = 1; field < unit.length; field++) {
			values.addAll(wrapText(unit[field], regular, 6.5f, width - 4f));
		}
		return new Node(labels, values, Math.max(minimumHeight, 4f + 8f * (labels.size() + values.size())));
	}

	/**
	 * Measured content of one assignment or section-role box.
	 *
	 * @param labels wrapped bold title lines.
	 * @param values wrapped regular value lines.
	 * @param height complete required box height.
	 */
	private record Node(List<String> labels, List<String> values, float height) {
	}

	/**
	 * Draws measured, wrapped nodes connected to their section chief. Assignment
	 * leaders and personnel counts remain inside the assignment's own node.
	 *
	 * @param stream page stream.
	 * @param bold node title font.
	 * @param regular node value font.
	 * @param sectionLeft section column's left edge.
	 * @param sectionCX connector center.
	 * @param sectionBoxBot section chief box bottom.
	 * @param secW section column width.
	 * @param subTopStart first node top.
	 * @param subH minimum node height.
	 * @param subGap gap between nodes.
	 * @param subUnits remaining nodes with title followed by value fields.
	 * @param minY section bottom.
	 * @return all nodes not fitting this page, in their original order.
	 * @throws IOException when measuring or drawing fails.
	 */
	private List<String[]> drawSubUnits(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float sectionLeft,
			float sectionCX, float sectionBoxBot, float secW, float subTopStart, float subH, float subGap,
			List<String[]> subUnits, float minY) throws IOException {
		if (subUnits.isEmpty())
			return List.of();
		float bL = sectionLeft + 4f, bW = secW - 8f;
		float connectorTop = subTopStart;
		stream.moveTo(sectionCX, sectionBoxBot);
		stream.lineTo(sectionCX, connectorTop);
		stream.stroke();

		float curTop = connectorTop;
		for (int i = 0; i < subUnits.size(); i++) {
			Node node = measureNode(subUnits.get(i), bold, regular, bW, subH);
			float botY = curTop - node.height();
			if (botY < minY + 4f) {
				return subUnits.subList(i, subUnits.size());
			}
			stream.addRect(bL, botY, bW, node.height());
			stream.stroke();
			float baseline = curTop - 8f;
			for (String line : node.labels()) {
				drawCentered(stream, bold, 6.5f, line, bL, baseline, bW);
				baseline -= 8f;
			}
			for (String line : node.values()) {
				drawCentered(stream, regular, 6.5f, line, bL, baseline, bW);
				baseline -= 8f;
			}
			if (i < subUnits.size() - 1) {
				stream.moveTo(sectionCX, botY);
				stream.lineTo(sectionCX, botY - subGap);
				stream.stroke();
			}
			curTop = botY - subGap;
		}
		return List.of();
	}

	// -----------------------------------------------------------------------
	// Sub-unit list builders
	// -----------------------------------------------------------------------

	private List<String[]> buildCmdStaff(OrganizationalChart chart) {
		List<String[]> list = new ArrayList<>();
		list.add(new String[]{"Safety Officer", safe(chart.getSafetyOfficerName())});
		list.add(new String[]{"Public Info. Officer", safe(chart.getPublicInformationOfficerName())});
		list.add(new String[]{"Liaison Officer", safe(chart.getLiaisonOfficerName())});
		for (OrgChartEntry e : chart.getAdditionalPositions()) {
			if (e.getSection() == OrgChartEntry.Section.COMMAND_STAFF) {
				list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
			}
		}
		return list;
	}

	/**
	 * Builds operations nodes with each task leader inside the associated assignment.
	 *
	 * @param chart shared operations positions.
	 * @param tasks task assignments, or null for none.
	 * @return staging, assignment and additional operations nodes in source order.
	 */
	private List<String[]> buildOpsSubUnits(OrganizationalChart chart, List<SarTaskAssignment> tasks) {
		List<String[]> list = new ArrayList<>();
		addIfNamed(list, "Staging Area Mgr", chart.getStagingAreaManagerName());
		for (SarTaskAssignment t : tasks == null ? List.<SarTaskAssignment>of() : tasks) {
			if (t == null) {
				continue;
			}
			String label = safe(t.getResourceIdentifier()).isBlank()
					? (safe(t.getAssignmentTeamNumber()).isBlank() ? "Task" : safe(t.getAssignmentTeamNumber()))
					: safe(t.getResourceIdentifier());
			int people = countPeople(t);
			String count = people == 1 ? "1 person" : people + " people";
			list.add(safe(t.getLeader()).isBlank() ? new String[]{label, count}
					: new String[]{label, "Leader: " + safe(t.getLeader()), count});
		}
		for (OrgChartEntry e : chart.getAdditionalPositions()) {
			if (e.getSection() == OrgChartEntry.Section.OPERATIONS) {
				list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
			}
		}
		return list;
	}

	private List<String[]> buildPlanningSubUnits(OrganizationalChart chart) {
		List<String[]> list = new ArrayList<>();
		addIfNamed(list, "Doc Unit Leader", chart.getDocumentationUnitLeaderName());
		addIfNamed(list, "Resources Unit Ldr", chart.getResourcesUnitLeaderName());
		addIfNamed(list, "Situation Unit Ldr", chart.getSituationUnitLeaderName());
		addIfNamed(list, "Demob Unit Ldr", chart.getDemobilizationUnitLeaderName());
		for (OrgChartEntry e : chart.getAdditionalPositions()) {
			if (e.getSection() == OrgChartEntry.Section.PLANNING) {
				list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
			}
		}
		return list;
	}

	private List<String[]> buildLogisticsSubUnits(OrganizationalChart chart, String medicalUnitLeaderName) {
		List<String[]> list = new ArrayList<>();
		addIfNamed(list, "Comms Unit Leader", chart.getCommunicationsUnitLeaderName());
		addIfNamed(list, "Comms Technician", chart.getCommunicationsTechnicianName());
		addIfNamed(list, "Supply Unit Ldr", chart.getSupplyUnitLeaderName());
		addIfNamed(list, "Facilities Unit Ldr", chart.getFacilitiesUnitLeaderName());
		addIfNamed(list, "Gnd Support Unit Ldr", chart.getGroundSupportUnitLeaderName());
		addIfNamed(list, "Food Unit Ldr", chart.getFoodUnitLeaderName());
		addIfNamed(list, "Medical Unit Ldr", medicalUnitLeaderName);
		for (OrgChartEntry e : chart.getAdditionalPositions()) {
			if (e.getSection() == OrgChartEntry.Section.LOGISTICS) {
				list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
			}
		}
		return list;
	}

	private List<String[]> buildFinanceSubUnits(OrganizationalChart chart) {
		List<String[]> list = new ArrayList<>();
		addIfNamed(list, "Time Unit Ldr", chart.getTimeUnitLeaderName());
		addIfNamed(list, "Procurement Unit Ldr", chart.getProcurementUnitLeaderName());
		addIfNamed(list, "Comp/Claims Unit Ldr", chart.getCompClaimsUnitLeaderName());
		addIfNamed(list, "Cost Unit Ldr", chart.getCostUnitLeaderName());
		for (OrgChartEntry e : chart.getAdditionalPositions()) {
			if (e.getSection() == OrgChartEntry.Section.FINANCE_ADMIN) {
				list.add(new String[]{safe(e.getTitle()), safe(e.getName())});
			}
		}
		return list;
	}

	/**
	 * Adds a two-element array {title, name} only when {@code name} is non-blank.
	 */
	private static void addIfNamed(List<String[]> list, String title, String name) {
		if (name != null && !name.isBlank()) {
			list.add(new String[]{title, name});
		}
	}

	// -----------------------------------------------------------------------
	// Drawing helpers
	// -----------------------------------------------------------------------

	/**
	 * Places a staff title and its assigned name on one baseline, fitting both to the box.
	 *
	 * @param stream page content stream.
	 * @param bold title font.
	 * @param regular name font.
	 * @param title position title.
	 * @param name assigned officer.
	 * @param x text left edge.
	 * @param y shared baseline.
	 * @param width available text width.
	 * @throws IOException when font measurement or PDF drawing fails.
	 */
	private void drawCommandStaffText(PDPageContentStream stream, PDType1Font bold, PDType1Font regular,
			String title, String name, float x, float y, float width) throws IOException {
		String label = safe(title);
		String value = safe(name);
		float naturalWidth = (bold.getStringWidth(PdfText.singleLine(label))
				+ regular.getStringWidth(PdfText.singleLine(value))) / 1000f * 7f + 6f;
		float size = Math.min(7f, 7f * width / naturalWidth);
		drawOrgText(stream, bold, size, label, x, y);
		float nameX = x + bold.getStringWidth(PdfText.singleLine(label)) / 1000f * size + 6f * size / 7f;
		drawOrgText(stream, regular, size, value, nameX, y);
	}

	private void drawCell(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
		stream.addRect(x, y, width, height);
		stream.stroke();
	}

	/**
	 * Draws a section heading in a balanced text block.
	 * @param stream page stream.
	 * @param bold heading font.
	 * @param x left edge.
	 * @param topY top edge.
	 * @param heading section heading.
	 * @throws IOException when drawing fails.
	 */
	private void drawHeading(PDPageContentStream stream, PDType1Font bold, float x, float topY, String heading)
			throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, HEADING_FONT_SIZE);
			stream.newLineAtOffset(x + CELL_PADDING, topY - CELL_PADDING - HEADING_FONT_SIZE);
			stream.showText(PdfText.singleLine(heading));
		}
	}

	/**
	 * Writes normalized inline cell text.
	 * @param stream page stream.
	 * @param font body font.
	 * @param x left edge.
	 * @param y baseline.
	 * @param value source value.
	 * @throws IOException when drawing fails.
	 */
	private void writeCellText(PDPageContentStream stream, PDType1Font font, float x, float y, String value)
			throws IOException {
		String s = safe(value);
		if (s.isBlank())
			return;
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, BODY_FONT_SIZE);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(s));
		}
	}

	/**
	 * Draws an inline label and normalized value.
	 * @param stream page stream.
	 * @param bold label font.
	 * @param regular value font.
	 * @param x left edge.
	 * @param y baseline.
	 * @param label field label.
	 * @param value source value.
	 * @throws IOException when drawing fails.
	 */
	private void drawInlinePair(PDPageContentStream stream, PDType1Font bold, PDType1Font regular, float x, float y,
			String label, String value) throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(bold, BODY_FONT_SIZE);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(label + ": "));
		}
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(regular, BODY_FONT_SIZE);
			stream.newLineAtOffset(x + bold.getStringWidth(PdfText.singleLine(label + ": ")) / 1000f * BODY_FONT_SIZE, y);
			stream.showText(PdfText.singleLine(safe(value)));
		}
	}

	/**
	 * Measures and draws normalized text centered in a chart box.
	 * @param stream page stream.
	 * @param font text font.
	 * @param size font size.
	 * @param text source text.
	 * @param boxLeft box left edge.
	 * @param baselineY baseline.
	 * @param boxWidth box width.
	 * @throws IOException when measurement or drawing fails.
	 */
	private void drawCentered(PDPageContentStream stream, PDType1Font font, float size, String text, float boxLeft,
			float baselineY, float boxWidth) throws IOException {
		String s = safe(text);
		if (s.isBlank())
			return;
		float tw = font.getStringWidth(PdfText.singleLine(s)) / 1000f * size;
		float off = Math.max(0f, (boxWidth - tw) / 2f);
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, size);
			stream.newLineAtOffset(boxLeft + off, baselineY);
			stream.showText(PdfText.singleLine(s));
		}
	}

	/**
	 * Draws normalized chart text at its calculated position.
	 * @param stream page stream.
	 * @param font text font.
	 * @param size font size.
	 * @param text source text.
	 * @param x left edge.
	 * @param y baseline.
	 * @throws IOException when drawing fails.
	 */
	private void drawOrgText(PDPageContentStream stream, PDType1Font font, float size, String text, float x, float y)
			throws IOException {
		String s = safe(text);
		if (s.isBlank())
			return;
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, size);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(s));
		}
	}

	// -----------------------------------------------------------------------
	// Utility
	// -----------------------------------------------------------------------

	/**
	 * Normalizes whitespace without stripping unwanted controls.
	 * @param v optional text.
	 * @return normalized text for validation and layout.
	 */
	private static String safe(String v) {
		return PdfText.normalize(v);
	}

	private static String truncate(String s, int max) {
		if (s == null)
			return "";
		return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
	}

	private String formatDate(AppData data, LocalDateTime dt) {
		return formatPdfDate(data, dt);
	}

	private String formatTime(AppData data, LocalDateTime dt) {
		return formatPdfTime(data, dt);
	}

	private String formatDateTime(AppData data, java.time.LocalDateTime dt) {
		return formatPdfDateTime(data, dt);
	}
}
