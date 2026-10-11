/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.pdf;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics204Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentContext;
import org.sarmanagement.icsforms.model.PdfLayoutSettings;
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
	 * @param renderers
	 *            supported renderers keyed by form identifier.
	 */
	public PdfExportService(PdfFormRenderer... renderers) {
		for (PdfFormRenderer renderer : renderers) {
			this.renderers.put(renderer.getFormKey(), renderer);
		}
	}

	/**
	 * Exports a single selected form.
	 *
	 * @param formKey
	 *            form identifier, such as {@code ICS 202}.
	 * @param data
	 *            incident document.
	 * @param outputDirectory
	 *            destination directory.
	 * @return created PDF file path.
	 * @throws IOException
	 *             when export fails.
	 */
	public Path exportSelected(String formKey, AppData data, Path outputDirectory) throws IOException {
		return exportSelected(formKey, data, outputDirectory, BlankExportPages.NONE);
	}

	/**
	 * Exports a form with any bundle-specific blank-page metadata.
	 *
	 * @param formKey form identifier.
	 * @param data incident document.
	 * @param outputDirectory destination directory.
	 * @param blankExportPages bundle page numbers for blank fallback forms.
	 * @return created file.
	 * @throws IOException when rendering fails.
	 */
	private Path exportSelected(String formKey, AppData data, Path outputDirectory, BlankExportPages blankExportPages)
			throws IOException {
		PdfFormRenderer renderer = renderers.get(formKey);
		if (renderer == null) {
			throw new IllegalArgumentException("Unsupported form export: " + formKey);
		}
		Path output = outputDirectory.resolve(fileName(formKey));
		renderer.render(exportData(formKey, data, blankExportPages), output);
		return output;
	}

	/**
	 * Exports all supported forms.
	 *
	 * @param data
	 *            incident document.
	 * @param outputDirectory
	 *            destination directory.
	 * @return map of form keys to created files.
	 * @throws IOException
	 *             when any export fails.
	 */
	public Map<String, Path> exportAll(AppData data, Path outputDirectory) throws IOException {
		Map<String, Path> exported = new LinkedHashMap<>();
		for (String formKey : defaultFormKeys(data)) {
			exported.put(formKey, exportSelected(formKey, data, outputDirectory));
		}
		return exported;
	}

	/**
	 * Returns whether a form key is supported.
	 *
	 * @param formKey
	 *            form identifier.
	 * @return {@code true} when supported.
	 */
	public boolean supports(String formKey) {
		return renderers.containsKey(formKey);
	}

	/**
	 * Lists all supported formats, including optional formats for explicit export.
	 *
	 * @return registered form identifiers.
	 */
	public List<String> formKeys() {
		return new ArrayList<>(renderers.keySet());
	}

	/**
	 * Selects default exports without duplicating the clue log or enabling a tracker.
	 *
	 * @param data incident document containing export preferences.
	 * @return selected registered form identifiers.
	 */
	private List<String> defaultFormKeys(AppData data) {
		List<String> keys = formKeys();
		if (data == null || !data.isOpenActionTrackerEnabled()) {
			keys.remove("ICS 233-CG");
		}
		if (data != null && data.isClueLogIcs233Enabled()
				&& renderers.containsKey("Clue Log (ICS 233-CG adapted)")) {
			keys.remove("Clue Log");
		} else {
			keys.remove("Clue Log (ICS 233-CG adapted)");
		}
		if (data == null || !isIapAttachmentSelected(data, "ICS 215A")) {
			keys.remove("ICS 215A");
		}
		if (data == null || !isIapAttachmentSelected(data, "ICS 208")) {
			keys.remove("ICS 208");
		}
		return FormOrder.sort(keys);
	}

	/**
	 * Checks whether ICS 202 lists a form as an IAP attachment.
	 *
	 * @param data incident document.
	 * @param formKey registered renderer key.
	 * @return true when included on ICS 202.
	 */
	static boolean isIapAttachmentSelected(AppData data, String formKey) {
		return data.getForm202().getIncidentActionPlanAttachments().stream()
				.anyMatch(item -> formKey.equalsIgnoreCase(item));
	}

	/**
	 * Exports all supported forms and merges them into a single IAP bundle PDF.
	 *
	 * <p>
	 * Individual per-form PDFs are written first and then merged. The merged file
	 * is named using the incident name and operational period start, sanitised for
	 * filesystem safety. The individual component files are removed after a
	 * successful merge.
	 * </p>
	 *
	 * @param data
	 *            incident document.
	 * @param outputDirectory
	 *            destination directory.
	 * @return path of the merged IAP bundle PDF.
	 * @throws IOException
	 *             when export or merge fails.
	 */
	public Path exportIapBundle(AppData data, Path outputDirectory) throws IOException {
		return exportIapBundle(data, outputDirectory, null);
	}

	/**
	 * Exports the selected supported forms and merges them into a single IAP bundle
	 * PDF.
	 *
	 * @param data
	 *            incident document.
	 * @param outputDirectory
	 *            destination directory.
	 * @param selectedFormKeys
	 *            selected form keys; when null/empty, all supported forms are used.
	 * @return path of the merged IAP bundle PDF.
	 * @throws IOException
	 *             when export or merge fails.
	 */
	public Path exportIapBundle(AppData data, Path outputDirectory, List<String> selectedFormKeys) throws IOException {
		Files.createDirectories(outputDirectory);
		// Skip ICS 201 only when it was never filled in (incident started as a full
		// operational period with no initial response briefing). When the form contains
		// content it is always included even if the incident has since moved to
		// DURING_OP.
		boolean skipIcs201 = !ics201HasContent(data.getForm201());
		List<String> includedFormKeys = (selectedFormKeys == null || selectedFormKeys.isEmpty())
				? defaultFormKeys(data)
				: new ArrayList<>(selectedFormKeys);
		includedFormKeys = orderForIapBundle(includedFormKeys);
		if (!includeIcs203(data)) {
			includedFormKeys.remove("ICS 203");
		}
		CoverPageRenderer coverPageRenderer = new CoverPageRenderer();
		Path coverPath = Files.createTempFile(outputDirectory, "cover-page-", ".pdf");
		List<Path> tempFiles = new ArrayList<>();
		try {
			coverPageRenderer.render(data, coverPath);
			tempFiles.add(coverPath);
			BlankExportPages blankExportPages = assignIapPageNumbers(data, includedFormKeys, skipIcs201);
			Map<String, Path> parts = new LinkedHashMap<>();
			for (String formKey : includedFormKeys) {
				if (!renderers.containsKey(formKey)) {
					continue;
				}
				if (skipIcs201 && "ICS 201".equals(formKey)) {
					continue;
				}
				parts.put(formKey, exportSelected(formKey, data, outputDirectory, blankExportPages));
			}
			tempFiles.addAll(parts.values());
			String bundleName = iapBundleFileName(data.getIncidentContext());
			Path bundlePath = outputDirectory.resolve(bundleName);
			PDFMergerUtility merger = new PDFMergerUtility();
			merger.setDestinationFileName(bundlePath.toString());
			merger.addSource(coverPath.toFile());
			for (Path part : parts.values()) {
				merger.addSource(part.toFile());
			}
			merger.mergeDocuments(null); // null = in-memory; suitable for typical IAP sizes (< ~50 pages)
			return bundlePath;
		} finally {
			for (Path part : tempFiles) {
				try {
					Files.deleteIfExists(part);
				} catch (IOException ignored) {
					// Best-effort cleanup; do not fail the export.
				}
			}
		}
	}

	/**
	 * Assigns sequential IAP page numbers to all forms in the document, ordered by
	 * bundle order, including all continuation pages.
	 *
	 * <p>
	 * This method mutates the forms in {@code data} in-place and is called just
	 * before the IAP bundle export so the page numbers printed on the PDFs are
	 * accurate.
	 * </p>
	 *
	 * @param data
	 *            incident document.
	 */
	static void assignIapPageNumbers(AppData data) {
		assignIapPageNumbers(data, null, !ics201HasContent(data.getForm201()));
	}

	/**
	 * Assigns page labels only to the forms that will actually be bundled.
	 *
	 * @param data incident document.
	 * @param includedFormKeys selected keys; null means all, empty means none.
	 * @param skipIcs201 whether the unused initial briefing is omitted.
	 * @return starting page labels for temporary blank task and activity forms.
	 */
	static BlankExportPages assignIapPageNumbers(AppData data, List<String> includedFormKeys, boolean skipIcs201) {
		int page = 1;
		String blankSarTaskPage = "";
		String blankIcs214Page = "";
		java.util.Set<String> included = includedFormKeys == null
				? null
				: new java.util.LinkedHashSet<>(includedFormKeys);
		data.getForm201().setIapPage("");
		data.getForm202().setIapPage("");
		data.setForm205aIapPage("");
		data.getForm215a().setIapPage("");
		data.getForm208().setIapPage("");
		data.getForm207().setIapPage("");
		data.getOrganizationalChart().getAssignmentList().setIapPage("");
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			task.setIapPage("");
		}
		for (Ics214Form log : data.getActivityLogs()) {
			log.setIapPage("");
		}
		for (String formKey : FormOrder.formKeys()) {
			switch (formKey) {
				case "ICS 201" -> {
					if (!skipIcs201 && includedOrAll(included, formKey)) {
						data.getForm201().setIapPage(String.valueOf(page));
						page += Ics201PdfRenderer.PAGE_COUNT;
					}
				}
				case "ICS 202" -> {
					if (includedOrAll(included, formKey)) {
						data.getForm202().setIapPage(String.valueOf(page++));
					}
				}
				case "ICS 203" -> {
					if (includedOrAll(included, formKey) && includeIcs203(data)) {
						data.getOrganizationalChart().getAssignmentList().setIapPage(String.valueOf(page));
						page += Ics203PdfRenderer.pageCount(data);
					}
				}
				case "ICS 204" -> {
					if (includedOrAll(included, formKey)) {
						data.getForm204().setIapPage(String.valueOf(page++));
						for (Ics204Form form : data.getAdditionalForms204()) {
							form.setIapPage(String.valueOf(page++));
						}
					}
				}
				case "SAR Task Assignment" -> {
					if (includedOrAll(included, formKey)) {
						List<SarTaskAssignment> tasks = data.getSarTaskAssignments();
						if (tasks.isEmpty()) {
							blankSarTaskPage = String.valueOf(page);
							page += 2;
						} else {
							for (SarTaskAssignment task : tasks) {
								task.setIapPage(String.valueOf(page));
								page += 2;
							}
						}
					}
				}
				case "ICS 205A" -> {
					if (includedOrAll(included, formKey)) {
						data.setForm205aIapPage(String.valueOf(page));
						page += Ics205aPdfRenderer.pageCount(data);
					}
				}
				case "ICS 207" -> {
					if (includedOrAll(included, formKey)) {
						data.getForm207().setIapPage(String.valueOf(page++));
					}
				}
				case "ICS 208" -> {
					if (includedOrDefaultAttachment(included, data, formKey)) {
						data.getForm208().setIapPage(String.valueOf(page));
						page += Ics208PdfRenderer.pageCount(data);
					}
				}
				case "ICS 214" -> {
					if (includedOrAll(included, formKey)) {
						List<Ics214Form> logs = data.getActivityLogs();
						if (logs.isEmpty()) {
							blankIcs214Page = String.valueOf(page);
							page++;
						} else {
							for (Ics214Form log : logs) {
								log.setIapPage(String.valueOf(page));
								page += Ics214PdfRenderer.pageCount(log);
							}
						}
					}
				}
				case "ICS 215A" -> {
					if (includedOrDefaultAttachment(included, data, formKey)) {
						data.getForm215a().setIapPage(String.valueOf(page));
						page += Ics215aPdfRenderer.pageCount(data);
					}
				}
				default -> {
				}
			}
		}
		return new BlankExportPages(blankSarTaskPage, blankIcs214Page);
	}

	/**
	 * Orders known IAP forms before remaining selected formats.
	 *
	 * @param formKeys selected identifiers.
	 * @return ordered identifiers.
	 */
	static List<String> orderForIapBundle(List<String> formKeys) {
		return new ArrayList<>(FormOrder.sort(formKeys));
	}

	/**
	 * Tests whether the optional organization assignment list belongs in the IAP.
	 *
	 * @param data incident document.
	 * @return true only when inclusion is enabled and the Org Chart has entries.
	 */
	static boolean includeIcs203(AppData data) {
		return data.getOrganizationalChart().getAssignmentList().isIncludeInIap()
				&& data.getOrganizationalChart().hasAssignments();
	}

	/**
	 * Supplies blank fallback forms without mutating the incident.
	 *
	 * @param formKey selected format.
	 * @param data incident document.
	 * @param blankExportPages starting pages for blank forms.
	 * @return original data or a copy with a numbered blank form.
	 */
	private AppData exportData(String formKey, AppData data, BlankExportPages blankExportPages) {
		if ("SAR Task Assignment".equals(formKey) && data.getSarTaskAssignments().isEmpty()
				&& !blankExportPages.sarTaskAssignmentStartPage().isBlank()) {
			AppData exportData = copyAppData(data);
			SarTaskAssignment blankTask = new SarTaskAssignment();
			blankTask.setIapPage(blankExportPages.sarTaskAssignmentStartPage());
			exportData.setSarTaskAssignments(List.of(blankTask));
			return exportData;
		}
		if ("ICS 214".equals(formKey) && data.getActivityLogs().isEmpty()
				&& !blankExportPages.ics214StartPage().isBlank()) {
			AppData exportData = copyAppData(data);
			Ics214Form blankLog = new Ics214Form();
			blankLog.setIapPage(blankExportPages.ics214StartPage());
			exportData.setActivityLogs(List.of(blankLog));
			return exportData;
		}
		return data;
	}

	/**
	 * Copies the root document for temporary export-only form substitutions.
	 *
	 * @param data source document.
	 * @return copy retaining document identity, revision, metadata, and preferences.
	 */
	private AppData copyAppData(AppData data) {
		AppData copy = new AppData();
		copy.setSchemaVersion(data.getSchemaVersion());
		copy.setIncidentId(data.getIncidentId());
		copy.setRevision(data.getRevision());
		copy.setCreatedAt(data.getCreatedAt());
		copy.setUpdatedAt(data.getUpdatedAt());
		copy.setOriginNodeId(data.getOriginNodeId());
		copy.setUseSystemTimeZone(data.isUseSystemTimeZone());
		copy.setConfiguredTimeZoneId(data.getConfiguredTimeZoneId());
		copy.setIncidentMode(data.getIncidentMode());
		copy.setIapPhase(data.getIapPhase());
		copy.setOperationalPeriodHistory(data.getOperationalPeriodHistory());
		copy.setIncidentContext(data.getIncidentContext());
		copy.setOrganizationalChart(data.getOrganizationalChart());
		copy.setForm201(data.getForm201());
		copy.setForm202(data.getForm202());
		copy.setForm215a(data.getForm215a());
		copy.setForm208(data.getForm208());
		copy.setForm205aIapPage(data.getForm205aIapPage());
		copy.setForm207(data.getForm207());
		copy.setForm204(data.getForm204());
		copy.setAdditionalForms204(data.getAdditionalForms204());
		copy.setActivityLogs(data.getActivityLogs());
		copy.setActivityEventTypes(data.getActivityEventTypes());
		copy.setSarTaskAssignments(data.getSarTaskAssignments());
		copy.setArchivedSafetyHazardReports(data.getArchivedSafetyHazardReports());
		copy.setCommonSafetyHazards(data.getCommonSafetyHazards());
		copy.setClueLogEntries(data.getClueLogEntries());
		copy.setOpenActionItems(data.getOpenActionItems());
		copy.setOpenActionTrackerEnabled(data.isOpenActionTrackerEnabled());
		copy.setIcs233Portrait(data.isIcs233Portrait());
		copy.setClueLogIcs233Enabled(data.isClueLogIcs233Enabled());
		copy.setTCards(data.getTCards());
		PdfLayoutSettings settings = new PdfLayoutSettings();
		settings.setPaperSize(data.getPdfLayoutSettings().getPaperSize());
		settings.setPageMarginPoints(data.getPdfLayoutSettings().getPageMarginPoints());
		copy.setPdfLayoutSettings(settings);
		return copy;
	}

	/**
	 * Checks membership in an optional form selection.
	 *
	 * @param included selection, or null for all.
	 * @param formKey candidate identifier.
	 * @return whether the candidate is included.
	 */
	private static boolean includedOrAll(java.util.Set<String> included, String formKey) {
		return included == null || included.contains(formKey);
	}

	/**
	 * Checks explicit bundle selection or the saved optional-form preference.
	 *
	 * @param included explicit bundle keys, or null for default selection.
	 * @param formKey safety form identifier.
	 * @param preference saved IAP preference.
	 * @return whether the safety form is bundled.
	 */
	private static boolean includedOrDefaultAttachment(java.util.Set<String> included, AppData data, String formKey) {
		return included == null ? isIapAttachmentSelected(data, formKey) : included.contains(formKey);
	}

	/**
	 * Bundle numbering for blank fallback forms.
	 *
	 * @param sarTaskAssignmentStartPage blank SAR task starting page.
	 * @param ics214StartPage blank activity log starting page.
	 */
	private record BlankExportPages(String sarTaskAssignmentStartPage, String ics214StartPage) {
		private static final BlankExportPages NONE = new BlankExportPages("", "");
	}

	/**
	 * Returns {@code true} when the ICS 201 form contains content indicating it was
	 * deliberately completed during an initial incident response. A form with both
	 * {@code situationSummary} and {@code preparedByName} blank is treated as never
	 * filled in and is excluded from the IAP bundle.
	 *
	 * @param form initial briefing, or null.
	 * @return whether the form was filled in.
	 */
	static boolean ics201HasContent(Ics201Form form) {
		if (form == null) {
			return false;
		}
		return !form.getSituationSummary().isBlank() || !form.getPreparedByName().isBlank();
	}

	/**
	 * Generates a stable export file name.
	 *
	 * @param formKey form identifier.
	 * @return PDF file name.
	 */
	private String fileName(String formKey) {
		return formKey.toLowerCase().replace(' ', '-') + ".pdf";
	}

	/**
	 * Generates the IAP bundle file name from incident context metadata.
	 *
	 * @param context
	 *            incident context.
	 * @return sanitised PDF file name.
	 */
	private static String iapBundleFileName(IncidentContext context) {
		String name = context == null || context.getIncidentName() == null ? "" : context.getIncidentName();
		String period = "";
		if (context != null && context.getOperationalPeriodStart() != null) {
			period = context.getOperationalPeriodStart().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
		}
		String base = (name.isBlank() ? "incident" : name) + (period.isBlank() ? "" : "_" + period) + "_IAP";
		// Replace characters that are unsafe on common filesystems.
		return base.replaceAll("[^A-Za-z0-9_\\-]", "_") + ".pdf";
	}
}
