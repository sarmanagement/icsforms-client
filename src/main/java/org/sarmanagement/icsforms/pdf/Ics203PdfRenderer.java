/*
 * Copyright (C) 2026 SAR Management
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package org.sarmanagement.icsforms.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics203Form;
import org.sarmanagement.icsforms.model.Ics203Form.Position;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.OrgChartEntry.Section;
import org.sarmanagement.icsforms.model.OrganizationalChart;
import org.sarmanagement.icsforms.model.SarTaskAssignment;

/**
 * Stateless, two-column Organization Assignment List (ICS 203) renderer.
 * Continuations retain all labeled form blocks and paginate text by measured
 * font width rather than truncating names or assignments.
 */
public class Ics203PdfRenderer extends AbstractPdfRenderer implements PdfFormRenderer {
	private static final float FONT_SIZE = 7f;
	private static final float PADDING = 3f;
	private static final float TOP_HEIGHT = 48f;
	private static final float FOOTER_HEIGHT = 64f;

	/** {@inheritDoc} */
	@Override
	public String getFormKey() {
		return "ICS 203";
	}

	/**
	 * Counts pages using precisely the same layout and wrapping as rendering.
	 *
	 * @param data incident document, or null for a blank form.
	 * @return number of form pages.
	 */
	public static int pageCount(AppData data) {
		try {
			return new Ics203PdfRenderer().plan(data == null ? new AppData() : data).pages();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void render(AppData data, Path outputFile) throws IOException {
		PdfText.validateForm(data, getFormKey());
		AppData source = data == null ? new AppData() : data;
		Plan plan = plan(source);
		if (outputFile.getParent() != null) {
			Files.createDirectories(outputFile.getParent());
		}
		try (PDDocument document = new PDDocument()) {
			for (int index = 0; index < plan.pages(); index++) {
				PDPage page = newPage(source);
				document.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
					drawPage(stream, source, plan, index);
				}
			}
			document.save(outputFile.toFile());
		}
	}

	/**
	 * Builds a render-local page plan; no document or pagination state is retained.
	 *
	 * @param data source document.
	 * @return wrapped form contents and page count.
	 * @throws IOException when font metrics cannot be read.
	 */
	private Plan plan(AppData data) throws IOException {
		FormLayout layout = formLayout(newPage(data), data);
		float width = layout.width() / 2f;
		float height = layout.height() - TOP_HEIGHT - FOOTER_HEIGHT;
		float rowHeight = height / 38f;
		float size = Math.min(FONT_SIZE, Math.min((rowHeight - 3f) / 1.2f, width / 38f));
		if (size < 3f || width < 120f) {
			throw new IllegalArgumentException("PDF margins leave insufficient space for ICS 203");
		}
		OrganizationalChart chart = data.getOrganizationalChart();
		Ics203Form form = chart.getAssignmentList();
		List<Block> blocks = blocks(data, chart, form);
		int pages = 1;
		for (Block block : blocks) {
			for (Row row : block.rows().subList(0, block.fixed())) {
				pages = Math.max(pages, row.prepare(width, size));
			}
			List<Row> extras = new ArrayList<>();
			List<Row> sourceExtras = block.rows().subList(block.fixed(), block.rows().size());
			for (int source = 0; source < sourceExtras.size(); source++) {
				Row row = sourceExtras.get(source);
				if (row.shade() && "Branch".equals(row.label) && source + 5 < sourceExtras.size()) {
					appendWrappedBranch(extras, sourceExtras.subList(source, source + 6), width, size);
					source += 5;
					continue;
				}
				int count = row.prepare(width, size);
				for (int i = 0; i < count; i++) {
					Row fragment = new Row(row.label(i), row.value(i), row.shade());
					fragment.prepare(width, size);
					extras.add(fragment);
				}
			}
			block.rows().subList(block.fixed(), block.rows().size()).clear();
			block.rows().addAll(extras);
			int capacity = block.slots() - 1 - block.fixed();
			pages = Math.max(pages, (extras.size() + capacity - 1) / capacity);
		}
		boolean defaultPreparer = clean(form.getPreparedByName()).isBlank();
		List<Row> metadata = List.of(new Row("Name:", defaultPreparer
				? chart.getResourcesUnitLeaderName() : form.getPreparedByName()),
				new Row("Position/Title:", defaultPreparer && !clean(chart.getResourcesUnitLeaderName()).isBlank()
						&& clean(form.getPreparedByPositionTitle()).isBlank()
								? "Resources Unit Leader" : form.getPreparedByPositionTitle()),
				new Row("Signature:", form.getPreparedBySignature()), new Row("IAP Page", form.getIapPage()),
				new Row("1. Incident Name", data.getIncidentContext().getIncidentName()));
		float[] widths = {layout.width() * .30f, layout.width() * .34f, layout.width() * .36f,
				layout.width() * .20f, width};
		for (int i = 0; i < metadata.size(); i++) {
			pages = Math.max(pages, metadata.get(i).prepareMetadata(widths[i], size, i != 4));
		}
		return new Plan(layout, blocks, metadata, rowHeight, size, pages);
	}

	/**
	 * Wraps an overflow branch as complete six-row continuation groups so even
	 * long director or deputy values remain beneath their branch heading.
	 *
	 * @param output flattened continuation rows.
	 * @param group branch heading, director, deputy and three division rows.
	 * @param width column width.
	 * @param size font size.
	 * @throws IOException when font metrics fail.
	 */
	private void appendWrappedBranch(List<Row> output, List<Row> group, float width, float size) throws IOException {
		while (output.size() % 6 != 0) {
			Row blank = new Row("", "");
			blank.prepare(width, size);
			output.add(blank);
		}
		int contextPages = 1;
		for (int i = 0; i < 3; i++) {
			contextPages = Math.max(contextPages, group.get(i).prepare(width, size));
		}
		List<Row> divisions = new ArrayList<>();
		for (int i = 3; i < 6; i++) {
			Row row = group.get(i);
			int count = row.prepare(width, size);
			for (int fragment = 0; fragment < count; fragment++) {
				Row part = new Row(row.label(fragment), row.value(fragment));
				part.prepare(width, size);
				divisions.add(part);
			}
		}
		int pages = Math.max(contextPages, (divisions.size() + 2) / 3);
		for (int page = 0; page < pages; page++) {
			for (int i = 0; i < 3; i++) {
				Row row = group.get(i);
				Row part = new Row(row.label(page), row.value(page), row.shade());
				part.prepare(width, size);
				output.add(part);
			}
			for (int i = 0; i < 3; i++) {
				int division = page * 3 + i;
				Row row = division < divisions.size() ? divisions.get(division) : new Row("Division/Group", "");
				row.prepare(width, size);
				output.add(row);
			}
		}
	}

	/**
	 * Maps shared organization fields into the numbered ICS 203 blocks.
	 *
	 * @param data source document.
	 * @param chart shared organization.
	 * @param form assignment-list-specific fields.
	 * @return ordered left then right form blocks.
	 */
	private List<Block> blocks(AppData data, OrganizationalChart chart, Ics203Form form) {
		List<String> names = chart.getIncidentCommanderEntries().stream().filter(e -> e != null)
				.map(OrgChartEntry::getName).filter(n -> n != null && !n.isBlank()).toList();
		if (names.isEmpty()) {
			names = chart.getIncidentCommanders().stream().filter(n -> n != null && !n.isBlank()).toList();
		}
		Block command = new Block("3. Incident Commander(s) and Command Staff", false, 7);
		command.add(names.size() > 1 ? "Unified Command" : "Incident Commander", String.join(" / ", names));
		command.add("Deputy", form.getPosition(Position.DEPUTY_INCIDENT_COMMANDER));
		command.add("Safety Officer", chart.getSafetyOfficerName());
		command.add("Public Information Officer", chart.getPublicInformationOfficerName());
		command.add("Liaison Officer", chart.getLiaisonOfficerName());
		command.fix();
		Block agencies = new Block("4. Agency/Organization Representatives", false, 5);
		agencies.rows().add(new Row("Agency/Organization", "Name", true));
		agencies.fix();
		for (OrgChartEntry entry : form.getAgencyRepresentatives()) {
			if (entry != null) {
				agencies.add(entry.getTitle(), entry.getName());
			}
		}
		Block planning = new Block("5. Planning Section", false, 10);
		planning.add("Chief", chart.getPlanningSectionChiefName());
		planning.add("Deputy", form.getPosition(Position.DEPUTY_PLANNING_CHIEF));
		planning.add("Resources Unit Leader", chart.getResourcesUnitLeaderName());
		planning.add("Situation Unit Leader", chart.getSituationUnitLeaderName());
		planning.add("Documentation Unit Leader", chart.getDocumentationUnitLeaderName());
		planning.add("Demobilization Unit Leader", chart.getDemobilizationUnitLeaderName());
		planning.add("Technical Specialists", "");
		planning.fix();
		Block logistics = new Block("6. Logistics Section", false, 16);
		logistics.add("Chief", chart.getLogisticsSectionChiefName());
		logistics.add("Deputy", form.getPosition(Position.DEPUTY_LOGISTICS_CHIEF));
		logistics.header("Support Branch");
		logistics.add("Director", form.getPosition(Position.SUPPORT_BRANCH_DIRECTOR));
		logistics.add("Supply Unit Leader", chart.getSupplyUnitLeaderName());
		logistics.add("Facilities Unit Leader", chart.getFacilitiesUnitLeaderName());
		logistics.add("Ground Support Unit Leader", chart.getGroundSupportUnitLeaderName());
		logistics.header("Service Branch");
		logistics.add("Director", form.getPosition(Position.SERVICE_BRANCH_DIRECTOR));
		logistics.add("Communications Unit Leader", chart.getCommunicationsUnitLeaderName());
		String medicalUnitLeader = chart.getMedicalUnitLeaderName().isBlank()
				? form.getPosition(Position.MEDICAL_UNIT_LEADER) : chart.getMedicalUnitLeaderName();
		logistics.add("Medical Unit Leader", medicalUnitLeader);
		logistics.add("Food Unit Leader", chart.getFoodUnitLeaderName());
		logistics.add("Communications Technician", chart.getCommunicationsTechnicianName());
		logistics.fix();
		Block operations = new Block("7. Operations Section", true, 30);
		operations.add("Chief", chart.getOperationsSectionChiefName());
		operations.add("Deputy", form.getPosition(Position.DEPUTY_OPERATIONS_CHIEF));
		operations.add("Staging Area Manager", chart.getStagingAreaManagerName());
		List<Ics203Form.Branch> branches = form.getBranches().stream().filter(b -> b != null && substantive(b))
				.toList();
		for (int i = 0; i < 3; i++) {
			operations.header("Branch");
			operations.add("Director", "");
			operations.add("Deputy", "");
			for (int j = 0; j < 3; j++) {
				operations.add("Division/Group", "");
			}
		}
		operations.header("Air Operations Branch");
		operations.add("Director", form.getPosition(Position.AIR_OPERATIONS_BRANCH_DIRECTOR));
		operations.fix();
		if (branches.isEmpty()) {
			List<OrgChartEntry> divisions = fallback(data);
			for (int i = 0; i < divisions.size(); i++) {
				OrgChartEntry entry = divisions.get(i);
				if (i < 9) {
					operations.rows().set(6 + (i / 3) * 6 + i % 3,
							new Row(entry.getTitle(), entry.getName()));
				} else {
					operations.add(entry.getTitle(), entry.getName());
				}
			}
		} else {
			// Named branches are grouped into the repeated three-branch template.
			for (int i = 0; i < Math.min(3, branches.size()); i++) {
				Ics203Form.Branch branch = branches.get(i);
				int offset = 3 + i * 6;
				operations.rows().set(offset, new Row("Branch", branch.getName(), true));
				operations.rows().set(offset + 1, new Row("Director", branch.getDirector()));
				operations.rows().set(offset + 2, new Row("Deputy", branch.getDeputy()));
				for (int j = 0; j < Math.min(3, branch.getDivisions().size()); j++) {
					OrgChartEntry entry = branch.getDivisions().get(j);
					if (entry != null) {
						operations.rows().set(offset + 3 + j,
								new Row("Division/Group: " + entry.getTitle(), entry.getName()));
					}
				}
			}
			for (int i = 0; i < branches.size(); i++) {
				Ics203Form.Branch branch = branches.get(i);
				addBranchContinuations(operations, branch, i < 3 ? 3 : 0, i >= 3, i + 1);
			}
		}
		Block finance = new Block("8. Finance/Administration Section", true, 8);
		finance.add("Chief", chart.getFinanceAdminSectionChiefName());
		finance.add("Deputy", form.getPosition(Position.DEPUTY_FINANCE_CHIEF));
		finance.add("Time Unit Leader", chart.getTimeUnitLeaderName());
		finance.add("Procurement Unit Leader", chart.getProcurementUnitLeaderName());
		finance.add("Compensation/Claims Unit Leader", chart.getCompClaimsUnitLeaderName());
		finance.add("Cost Unit Leader", chart.getCostUnitLeaderName());
		finance.fix();
		for (OrgChartEntry entry : chart.getAdditionalPositions()) {
			if (entry == null) {
				continue;
			}
			String title = clean(entry.getTitle()).toLowerCase(Locale.ROOT);
			Block destination;
			if (title.contains("technical specialist")) {
				destination = planning;
			} else if (title.contains("communications technician")) {
				destination = logistics;
			} else {
				Section section = entry.getSection();
				destination = section == Section.COMMAND_STAFF ? command
						: section == Section.PLANNING ? planning : section == Section.LOGISTICS ? logistics
								: section == Section.FINANCE_ADMIN ? finance : operations;
			}
			destination.add(entry.getTitle(), entry.getName());
		}
		return List.of(command, agencies, planning, logistics, operations, finance);
	}

	/**
	 * Detects explicit branch content, ignoring completely empty placeholders.
	 *
	 * @param branch candidate branch.
	 * @return whether the branch suppresses automatic division/group population.
	 */
	private boolean substantive(Ics203Form.Branch branch) {
		return !clean(branch.getName()).isBlank() || !clean(branch.getDirector()).isBlank()
				|| !clean(branch.getDeputy()).isBlank() || branch.getDivisions().stream().anyMatch(e -> e != null
						&& (!clean(e.getTitle()).isBlank() || !clean(e.getName()).isBlank()));
	}

	/**
	 * Keeps overflow branch identity, director and deputy together with up to
	 * three division rows in each continuation's six available operations slots.
	 *
	 * @param operations operations block.
	 * @param branch explicit branch.
	 * @param start first division not already in the standard branch template.
	 * @param includeEmpty whether a branch without divisions still needs a group.
	 * @param branchNumber one-based fallback identity for unnamed branches.
	 */
	private void addBranchContinuations(Block operations, Ics203Form.Branch branch, int start,
			boolean includeEmpty, int branchNumber) {
		int count = branch.getDivisions().size();
		String branchName = clean(branch.getName()).isBlank() ? String.valueOf(branchNumber) : branch.getName();
		for (int first = start; first < count || includeEmpty; first += 3) {
			includeEmpty = false;
			while ((operations.rows().size() - operations.fixed()) % 6 != 0) {
				operations.add("", "");
			}
			operations.rows().add(new Row("Branch", branchName, true));
			operations.add("Director", branch.getDirector());
			operations.add("Deputy", branch.getDeputy());
			for (int offset = 0; offset < 3; offset++) {
				OrgChartEntry entry = first + offset < count ? branch.getDivisions().get(first + offset) : null;
				operations.add(entry == null ? "Division/Group" : "Division/Group: " + entry.getTitle(),
						entry == null ? "" : entry.getName());
			}
		}
	}

	/**
	 * Collects distinct actual division/group labels, giving ICS 204 supervisors
	 * precedence over SAR task leaders. Task identifiers are never used as labels.
	 *
	 * @param data source incident.
	 * @return deduplicated division/group assignments.
	 */
	private List<OrgChartEntry> fallback(AppData data) {
		Map<String, OrgChartEntry> rows = new LinkedHashMap<>();
		List<Ics204Form> forms = new ArrayList<>();
		forms.add(data.getForm204());
		forms.addAll(data.getAdditionalForms204());
		for (int index = 0; index < forms.size(); index++) {
			Ics204Form form = forms.get(index);
			if (form != null) {
				String path = index == 0 ? "form204" : "additionalForms204[" + index + "]";
				addDivision(rows, form.getBranch(), "Division", form.getDivision(), form.getDivisionGroupSupervisorName(),
						path, "divisionGroupSupervisorName");
				addDivision(rows, form.getBranch(), "Group", form.getGroup(), form.getDivisionGroupSupervisorName(),
						path, "divisionGroupSupervisorName");
			}
		}
		for (int index = 0; index < data.getSarTaskAssignments().size(); index++) {
			SarTaskAssignment task = data.getSarTaskAssignments().get(index);
			if (task != null) {
				String path = "sarTaskAssignments[" + (index + 1) + "]";
				addDivision(rows, task.getBranch(), "Division", task.getDivision(), task.getLeader(), path, "leader");
				addDivision(rows, task.getBranch(), "Group", task.getGroup(), task.getLeader(), path, "leader");
			}
		}
		return new ArrayList<>(rows.values());
	}

	/**
	 * Adds a labeled fallback row without replacing an already mapped supervisor.
	 *
	 * @param rows ordered distinct assignments.
	 * @param branch source assignment's branch name.
	 * @param kind division or group.
	 * @param name actual division/group name.
	 * @param supervisor supervisor or fallback task leader.
	 * @param sourcePath source model path for actionable validation.
	 * @param supervisorField source property supplying the supervisor name.
	 */
	private void addDivision(Map<String, OrgChartEntry> rows, String branch, String kind, String name, String supervisor,
			String sourcePath, String supervisorField) {
		String label = clean(name).trim();
		if (label.isBlank()) {
			return;
		}
		String prefix = kind.toLowerCase(Locale.ROOT) + " ";
		if (label.toLowerCase(Locale.ROOT).startsWith(prefix)) {
			label = label.substring(prefix.length()).trim();
		}
		String branchName = clean(branch).trim();
		String key = branchName.toLowerCase(Locale.ROOT) + ":" + kind + ":" + label.toLowerCase(Locale.ROOT);
		OrgChartEntry existing = rows.get(key);
		if (existing == null) {
			PdfText.validate(branch, getFormKey(), sourcePath + ".branch");
			PdfText.validate(name, getFormKey(), sourcePath + "." + kind.toLowerCase(Locale.ROOT));
			PdfText.validate(supervisor, getFormKey(), sourcePath + "." + supervisorField);
			OrgChartEntry entry = new OrgChartEntry();
			entry.setTitle((branchName.isBlank() ? "" : "Branch " + branchName + " / ") + kind + " " + label);
			entry.setName(clean(supervisor));
			rows.put(key, entry);
		} else if (clean(existing.getName()).isBlank()) {
			PdfText.validate(supervisor, getFormKey(), sourcePath + "." + supervisorField);
			existing.setName(clean(supervisor));
		}
	}

	/**
	 * Draws the repeated full form and the selected continuation slices.
	 *
	 * @param stream target page stream.
	 * @param data source incident.
	 * @param plan render-local page plan.
	 * @param index zero-based continuation index.
	 * @throws IOException when PDF output fails.
	 */
	private void drawPage(PDPageContentStream stream, AppData data, Plan plan, int index) throws IOException {
		PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		FormLayout layout = plan.layout();
		float width = layout.width() / 2f;
		drawFormHeader(stream, bold, layout, "ICS 203", "ORGANIZATION ASSIGNMENT LIST");
		drawFormFrame(stream, layout);
		stream.addRect(layout.x(), layout.top() - TOP_HEIGHT, width, TOP_HEIGHT);
		stream.addRect(layout.x() + width, layout.top() - TOP_HEIGHT, width, TOP_HEIGHT);
		stream.stroke();
		text(stream, bold, plan.size(), layout.x() + PADDING, layout.top() - 11f, "1. Incident Name");
		text(stream, regular, plan.size(), layout.x() + PADDING, layout.top() - 25f,
				plan.metadata().get(4).value(index));
		text(stream, bold, plan.size(), layout.x() + width + PADDING, layout.top() - 11f, "2. Operational Period");
		text(stream, regular, plan.size(), layout.x() + width + PADDING, layout.top() - 25f,
				"Date From: " + periodValue(data, data.getIncidentContext().getOperationalPeriodStart(), "MM/dd/yyyy"));
		text(stream, regular, plan.size(), layout.x() + width * 1.5f + PADDING, layout.top() - 25f,
				"Date To: " + periodValue(data, data.getIncidentContext().getOperationalPeriodEnd(), "MM/dd/yyyy"));
		text(stream, regular, plan.size(), layout.x() + width + PADDING, layout.top() - 39f,
				"Time From: " + periodValue(data, data.getIncidentContext().getOperationalPeriodStart(), "HH:mm"));
		text(stream, regular, plan.size(), layout.x() + width * 1.5f + PADDING, layout.top() - 39f,
				"Time To: " + periodValue(data, data.getIncidentContext().getOperationalPeriodEnd(), "HH:mm"));
		float left = layout.top() - TOP_HEIGHT;
		float right = left;
		for (Block block : plan.blocks()) {
			float top = block.right() ? right : left;
			float x = layout.x() + (block.right() ? width : 0f);
			float height = block.slots() * plan.rowHeight();
			stream.setNonStrokingColor(.85f);
			stream.addRect(x, top - plan.rowHeight(), width, plan.rowHeight());
			stream.fill();
			stream.setNonStrokingColor(0f);
			text(stream, bold, plan.size(), x + PADDING, top - plan.size() - PADDING, block.title());
			stream.addRect(x, top - height, width, height);
			stream.stroke();
			int extraSlots = block.slots() - 1 - block.fixed();
			for (int slot = 0; slot < block.slots() - 1; slot++) {
				int rowIndex = slot < block.fixed() ? slot : block.fixed() + index * extraSlots
						+ slot - block.fixed();
				Row row = rowIndex < block.rows().size() ? block.rows().get(rowIndex) : new Row("", "");
				if (rowIndex >= block.rows().size()) {
					row.prepare(width, plan.size());
				}
				float rowTop = top - (slot + 1) * plan.rowHeight();
				if (row.shade()) {
					stream.setNonStrokingColor(.92f);
					stream.addRect(x, rowTop - plan.rowHeight(), width, plan.rowHeight());
					stream.fill();
					stream.setNonStrokingColor(0f);
				}
				stream.moveTo(x, rowTop - plan.rowHeight());
				stream.lineTo(x + width, rowTop - plan.rowHeight());
				if (!row.shade() || block == plan.blocks().get(1)) {
					stream.stroke();
					stream.setLineWidth(.4f);
					stream.moveTo(x + width * .53f, rowTop);
					stream.lineTo(x + width * .53f, rowTop - plan.rowHeight());
					stream.stroke();
					stream.setLineWidth(1f);
				}
				stream.stroke();
				int fragmentIndex = slot < block.fixed() ? index : 0;
				text(stream, bold, plan.size(), x + PADDING, rowTop - plan.size() - PADDING,
						row.label(fragmentIndex));
				text(stream, regular, plan.size(), x + width * .53f + PADDING,
						rowTop - plan.size() - PADDING, row.value(fragmentIndex));
			}
			if (block.right()) {
				right -= height;
			} else {
				left -= height;
			}
		}
		drawPreparedByMetadataSection(stream, bold, regular, plan.size(), plan.size(), PADDING, layout.x(),
				layout.y(), layout.width(), FOOTER_HEIGHT, 18f, 14f, .30f, .64f, .64f, "9. Prepared By",
				plan.metadata().get(0).value(index), plan.metadata().get(1).value(index),
				plan.metadata().get(2).value(index), "ICS 203",
				addPageOffset(plan.metadata().get(3).value(index), index),
				formatPdfDateTime(data, data.getOrganizationalChart().getAssignmentList().getPreparedDateTime()));
		text(stream, regular, plan.size(), layout.x() + PADDING, layout.y() + 44f,
				"Page " + (index + 1) + " of " + plan.pages());
	}

	/**
	 * Formats one operational-period component in the configured display timezone.
	 *
	 * @param data incident document.
	 * @param value stored UTC timestamp, possibly null.
	 * @param pattern date or time format.
	 * @return formatted component, or blank when no timestamp is set.
	 */
	private String periodValue(AppData data, LocalDateTime value, String pattern) {
		return value == null ? "" : zonedPdfDateTime(data, value).format(DateTimeFormatter.ofPattern(pattern));
	}

	/**
	 * Writes an already width-checked text fragment.
	 *
	 * @param stream output stream.
	 * @param font text font.
	 * @param size font size.
	 * @param x left position.
	 * @param y baseline.
	 * @param value printable text.
	 * @throws IOException when PDF output fails.
	 */
	private void text(PDPageContentStream stream, PDType1Font font, float size, float x, float y, String value)
			throws IOException {
		try (PdfText.TextBlock textBlock = PdfText.begin(stream)) {
			stream.setFont(font, size);
			stream.newLineAtOffset(x, y);
			stream.showText(PdfText.singleLine(clean(value)));
		}
	}

	/**
	 * Normalizes inline whitespace and rejects unsupported Standard 14 glyphs,
	 * preserving typed slashes, parentheses, and trainee designations.
	 *
	 * @param value input string.
	 * @return printable PDF text.
	 */
	private static String clean(String value) {
		return PdfText.singleLine(value);
	}

	/**
	 * Splits text at measured width, including words longer than a cell.
	 *
	 * @param value text to split.
	 * @param font measuring font.
	 * @param size font size.
	 * @param width available width.
	 * @return all text fragments without discarded characters.
	 * @throws IOException when font metrics fail.
	 */
	private static List<String> fragments(String value, PDType1Font font, float size, float width) throws IOException {
		PdfText.validate(value, "ICS 203", "organization field");
		List<String> result = new ArrayList<>();
		for (String text : PdfText.normalize(value).split("\n", -1)) {
			int start = 0;
			if (text.isEmpty()) {
				result.add("");
			}
			while (start < text.length()) {
				int end = start;
				while (end < text.length()
						&& font.getStringWidth(text.substring(start, end + 1)) * size / 1000f <= width) {
					end++;
				}
				end = Math.max(start + 1, end);
				if (end < text.length()) {
					int space = text.lastIndexOf(' ', end - 1);
					if (space > start) {
						end = space + 1;
					}
				}
				result.add(text.substring(start, end));
				start = end;
			}
		}
		return result.isEmpty() ? List.of("") : result;
	}

	/**
	 * Immutable layout and render-local mutable wrapped rows.
	 *
	 * @param layout printable form bounds.
	 * @param blocks numbered organization sections.
	 * @param metadata wrapped preparer and incident fields.
	 * @param rowHeight height of one ruled row.
	 * @param size body font size.
	 * @param pages total continuation pages.
	 */
	private record Plan(FormLayout layout, List<Block> blocks, List<Row> metadata, float rowHeight, float size,
			int pages) {
	}

	/** One fixed-size form block with repeated standard rows and paginated extras. */
	private static final class Block {
		private final String title;
		private final boolean right;
		private final int slots;
		private final List<Row> rows = new ArrayList<>();
		private int fixed;

		/**
		 * Creates a block.
		 *
		 * @param title numbered section title.
		 * @param right whether this occupies the right column.
		 * @param slots total height in row units, including its heading.
		 */
		private Block(String title, boolean right, int slots) {
			this.title = title;
			this.right = right;
			this.slots = slots;
		}

		/** @return numbered section title. */
		private String title() {
			return title;
		}

		/** @return whether this block belongs in the right column. */
		private boolean right() {
			return right;
		}

		/** @return height in row units. */
		private int slots() {
			return slots;
		}

		/** @return repeated standard row count. */
		private int fixed() {
			return fixed;
		}

		/** @return render-local rows. */
		private List<Row> rows() {
			return rows;
		}

		/**
		 * Adds a labeled assignment.
		 *
		 * @param label position label.
		 * @param value assigned name.
		 */
		private void add(String label, String value) {
			rows.add(new Row(label, value));
		}

		/**
		 * Adds a gray branch heading.
		 *
		 * @param label branch label.
		 */
		private void header(String label) {
			rows.add(new Row(label, "", true));
		}

		/** Marks the existing rows as the repeated standard form skeleton. */
		private void fix() {
			fixed = rows.size();
		}
	}

	/** Labeled assignment with independent lossless text continuation fragments. */
	private static final class Row {
		private final String label;
		private final String value;
		private final boolean shade;
		private List<String> labels = List.of("");
		private List<String> values = List.of("");

		/**
		 * Creates an ordinary assignment.
		 *
		 * @param label position label.
		 * @param value assigned name.
		 */
		private Row(String label, String value) {
			this(label, value, false);
		}

		/**
		 * Creates a row.
		 *
		 * @param label position label.
		 * @param value assigned name.
		 * @param shade whether this is a gray branch heading.
		 */
		private Row(String label, String value, boolean shade) {
			this.label = label;
			this.value = value;
			this.shade = shade;
		}

		/**
		 * Measures both cell contents.
		 *
		 * @param width whole row width.
		 * @param size font size.
		 * @return required continuation count.
		 * @throws IOException when font metrics fail.
		 */
		private int prepare(float width, float size) throws IOException {
			labels = fragments(label, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), size,
					width * .53f - 2f * PADDING);
			values = fragments(value, new PDType1Font(Standard14Fonts.FontName.HELVETICA), size,
					width * .47f - 2f * PADDING);
			return Math.max(labels.size(), values.size());
		}

		/**
		 * Measures a full-width metadata value, reserving only its inline label.
		 *
		 * @param width complete metadata cell width.
		 * @param size font size.
		 * @param inline whether the label shares the value's baseline.
		 * @return required value continuation count.
		 * @throws IOException when font metrics fail.
		 */
		private int prepareMetadata(float width, float size, boolean inline) throws IOException {
			PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			float labelWidth = inline ? bold.getStringWidth(PdfText.singleLine(label + " ")) * size / 1000f : 0f;
			labels = List.of(clean(label));
			values = fragments(value, new PDType1Font(Standard14Fonts.FontName.HELVETICA), size,
					width - labelWidth - 2f * PADDING);
			return values.size();
		}

		/** @return whether this is a shaded branch heading. */
		private boolean shade() {
			return shade;
		}

		/**
		 * Selects the repeated label or its continuation.
		 *
		 * @param index continuation index.
		 * @return visible label.
		 */
		private String label(int index) {
			return labels.size() == 1 ? labels.get(0) : index < labels.size() ? labels.get(index) : labels.get(0);
		}

		/**
		 * Selects the repeated value or its continuation.
		 *
		 * @param index continuation index.
		 * @return visible value.
		 */
		private String value(int index) {
			return values.size() == 1 ? values.get(0) : index < values.size() ? values.get(index) : "";
		}
	}
}
