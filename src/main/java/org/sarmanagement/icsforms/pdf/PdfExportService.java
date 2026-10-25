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
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates exporting selected or all supported ICS forms to PDF files.
 */
public class PdfExportService {
	private static final Logger LOGGER = Logger.getLogger(PdfExportService.class.getName());
	private final Map<String, PdfFormRenderer> renderers = new LinkedHashMap<>();
	private String lastBundleSummary = "";

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
		return exportSelected(formKey, data, outputDirectory, blankExportPages, false);
	}

	/**
	 * Stages a form with an export-only ICS 205 physical page orientation.
	 *
	 * @param formKey renderer identifier
	 * @param data incident document
	 * @param outputDirectory destination
	 * @param blankExportPages blank fallback numbering
	 * @param portrait205 whether to rotate ICS 205 body content onto portrait media
	 * @return published form file
	 * @throws IOException when rendering or publication fails
	 */
	private Path exportSelected(String formKey, AppData data, Path outputDirectory, BlankExportPages blankExportPages,
			boolean portrait205) throws IOException {
		PdfFormRenderer renderer = renderers.get(formKey);
		if (renderer == null) {
			throw new IllegalArgumentException("Unsupported form export: " + formKey);
		}
		Path output = outputDirectory.resolve(fileName(formKey));
		Path temporary = null;
		try {
			Files.createDirectories(outputDirectory);
			temporary = Files.createTempFile(outputDirectory, ".pdf-export-", ".pdf");
			AppData source = exportData(formKey, data, blankExportPages);
			if (portrait205 && renderer instanceof Ics205PdfRenderer radioRenderer) {
				radioRenderer.render(source, temporary, true);
			} else {
				renderer.render(source, temporary);
			}
			replaceFile(temporary, output);
			return output;
		} catch (IOException | RuntimeException exception) {
			LOGGER.log(Level.SEVERE, "PDF export failed for " + formKey, exception);
			throw new IOException("Failed to export " + formKey + ": " + exception.getMessage()
					+ "\nThe existing PDF was not changed.", exception);
		} finally {
			if (temporary != null) {
				deleteTemporary(temporary);
			}
		}
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
		return exportForms(defaultFormKeys(data), data, outputDirectory);
	}

	/**
	 * Renders a selection completely before atomically publishing individual PDFs.
	 *
	 * @param formKeys selected renderer identifiers.
	 * @param data incident document.
	 * @param outputDirectory destination directory.
	 * @return published files keyed by form.
	 * @throws IOException when rendering or publication fails.
	 */
	public Map<String, Path> exportForms(List<String> formKeys, AppData data, Path outputDirectory) throws IOException {
		Path staging = null;
		Map<String, Path> staged = new LinkedHashMap<>();
		Set<Path> retainedBackups = new LinkedHashSet<>();
		String current = "output setup";
		try {
			Files.createDirectories(outputDirectory);
			staging = Files.createTempDirectory(outputDirectory, ".pdf-all-");
			for (String formKey : formKeys) {
				current = formKey;
				staged.put(formKey, exportSelected(formKey, data, staging));
			}
			return publishAll(staged, outputDirectory, staging, retainedBackups);
		} catch (IOException | RuntimeException exception) {
			LOGGER.log(Level.SEVERE, "PDF selection export failed at " + current, exception);
			throw new IOException("PDF export failed at " + current + ". Rendered: "
					+ String.join(", ", staged.keySet()) + ".\n" + exception.getMessage(), exception);
		} finally {
			if (staging != null) {
				deleteStaging(staging, retainedBackups);
			}
		}
	}

	/**
	 * Returns the most recent bundle's inclusion or failure summary.
	 *
	 * @return human-readable summary for the export completion dialog.
	 */
	public String getLastBundleSummary() {
		return lastBundleSummary;
	}

	/**
	 * Publishes staged forms, restoring existing outputs if publication fails.
	 *
	 * @param staged complete temporary PDFs keyed by form.
	 * @param directory final output directory.
	 * @param staging private workspace for backups.
	 * @param retainedBackups recovery copies that must survive failed rollback.
	 * @return published paths keyed by form.
	 * @throws IOException when an atomic replacement fails.
	 */
	private Map<String, Path> publishAll(Map<String, Path> staged, Path directory, Path staging,
			Set<Path> retainedBackups) throws IOException {
		Map<Path, Path> backups = new LinkedHashMap<>();
		List<Path> published = new ArrayList<>();
		Map<String, Path> exported = new LinkedHashMap<>();
		try {
			for (Map.Entry<String, Path> entry : staged.entrySet()) {
				Path destination = directory.resolve(entry.getValue().getFileName());
				if (Files.exists(destination)) {
					Path backup = Files.createTempFile(staging, "backup-", ".pdf");
					Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
					backups.put(destination, backup);
				}
				replaceFile(entry.getValue(), destination);
				published.add(destination);
				exported.put(entry.getKey(), destination);
			}
			return exported;
		} catch (IOException | RuntimeException exception) {
			List<String> recovery = new ArrayList<>();
			for (Path destination : published) {
				Path backup = backups.get(destination);
				try {
					if (backup == null) {
						Files.deleteIfExists(destination);
					} else {
						replaceFile(backup, destination);
					}
				} catch (IOException | RuntimeException restoreFailure) {
					exception.addSuppressed(restoreFailure);
					if (backup != null) {
						retainedBackups.add(backup);
						recovery.add(backup.toAbsolutePath() + " -> " + destination.toAbsolutePath());
					} else {
						recovery.add("Could not remove newly published file: " + destination.toAbsolutePath());
					}
				}
			}
			if (!recovery.isEmpty()) {
				throw new IOException(exception.getMessage()
						+ "\nRollback was incomplete. Original recovery copies have been retained."
						+ "\nRecover these files after resolving the filesystem error:\n"
						+ String.join("\n", recovery), exception);
			}
			throw exception;
		}
	}

	/**
	 * Atomically replaces a destination with a complete rendered or recovery PDF.
	 *
	 * @param source complete source file.
	 * @param destination destination to replace.
	 * @throws IOException when the filesystem cannot complete an atomic replacement.
	 */
	protected void replaceFile(Path source, Path destination) throws IOException {
		Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Removes a temporary file, logging cleanup failures with their full cause.
	 *
	 * @param path private temporary path.
	 */
	private static void deleteTemporary(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			LOGGER.log(Level.WARNING, "Could not remove PDF export temporary file " + path, exception);
		}
	}

	/**
	 * Cleans a private export workspace, including files from a failed render.
	 *
	 * @param staging private export workspace.
	 */
	private static void deleteStaging(Path staging) {
		deleteStaging(staging, Set.of());
	}

	/**
	 * Cleans temporary components but never removes unrecovered original copies.
	 *
	 * @param staging private export workspace.
	 * @param retainedBackups original copies needed for manual recovery.
	 */
	private static void deleteStaging(Path staging, Set<Path> retainedBackups) {
		try (var files = Files.list(staging)) {
			files.filter(path -> !retainedBackups.contains(path)).forEach(PdfExportService::deleteTemporary);
		} catch (IOException exception) {
			LOGGER.log(Level.WARNING, "Could not list PDF export workspace " + staging, exception);
		}
		if (retainedBackups.isEmpty()) {
			deleteTemporary(staging);
		} else {
			LOGGER.warning("PDF recovery copies retained at: " + retainedBackups);
		}
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
		if (data == null || !isIapAttachmentSelected(data, "ICS 206")) {
			keys.remove("ICS 206");
		}
		if (data == null || !isIapAttachmentSelected(data, "ICS 206CANINE")) {
			keys.remove("ICS 206CANINE");
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
	 * Individual per-form PDFs are staged privately and then merged. The merged file
	 * is named using the incident name and operational period start, sanitised for
	 * filesystem safety. Temporary components are removed on success or failure;
	 * existing individual and bundle PDFs are never used as temporary files.
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
		return exportIapBundle(data, outputDirectory, selectedFormKeys, false);
	}

	/**
	 * Exports an IAP with a per-export native-landscape alternative for ICS 205.
	 * The default rotates its actual landscape content onto portrait media and crop
	 * boxes, leaving IAP page numbering upright. Other forms retain their own layouts.
	 *
	 * @param data incident document
	 * @param outputDirectory destination directory
	 * @param selectedFormKeys selected keys; null or empty uses default forms
	 * @param ics205NativeLandscape true to retain native landscape ICS 205 sheets
	 * @return merged bundle path
	 * @throws IOException when rendering, merging, or publication fails
	 */
	public Path exportIapBundle(AppData data, Path outputDirectory, List<String> selectedFormKeys,
			boolean ics205NativeLandscape) throws IOException {
		lastBundleSummary = "";
		Path staging = null;
		Map<String, Path> parts = new LinkedHashMap<>();
		String current = "output setup";
		try {
			Files.createDirectories(outputDirectory);
			staging = Files.createTempDirectory(outputDirectory, ".pdf-bundle-");
			// Filled-in initial response briefings remain included in later periods.
			boolean skipIcs201 = !ics201HasContent(data.getForm201());
			List<String> includedFormKeys = (selectedFormKeys == null || selectedFormKeys.isEmpty())
					? defaultFormKeys(data)
					: new ArrayList<>(selectedFormKeys);
			includedFormKeys = orderForIapBundle(includedFormKeys);
			if (!includeIcs203(data)) {
				includedFormKeys.remove("ICS 203");
			}
			current = "IAP cover";
			CoverPageRenderer coverPageRenderer = new CoverPageRenderer();
			Path coverPath = staging.resolve("cover.pdf");
			coverPageRenderer.render(data, coverPath);
			BlankExportPages blankExportPages = assignIapPageNumbers(data, includedFormKeys, skipIcs201);
			for (String formKey : includedFormKeys) {
				current = formKey;
				if (!renderers.containsKey(formKey)) {
					throw new IllegalArgumentException("Unsupported form export: " + formKey);
				}
				if (skipIcs201 && "ICS 201".equals(formKey)) {
					continue;
				}
				parts.put(formKey, exportSelected(formKey, data, staging, blankExportPages, !ics205NativeLandscape));
			}
			current = "bundle merge";
			String bundleName = iapBundleFileName(data.getIncidentContext());
			Path bundlePath = outputDirectory.resolve(bundleName);
			Path temporaryBundle = staging.resolve("merged.pdf");
			PDFMergerUtility merger = new PDFMergerUtility();
			merger.setDestinationFileName(temporaryBundle.toString());
			merger.addSource(coverPath.toFile());
			for (Path part : parts.values()) {
				merger.addSource(part.toFile());
			}
			merger.mergeDocuments(null); // null = in-memory; suitable for typical IAP sizes (< ~50 pages)
			replaceFile(temporaryBundle, bundlePath);
			lastBundleSummary = "IAP bundle exported successfully. Included forms: "
					+ (parts.isEmpty() ? "none (cover only)" : String.join(", ", parts.keySet())) + ".";
			LOGGER.info(lastBundleSummary + " Output: " + bundlePath);
			return bundlePath;
		} catch (IOException | RuntimeException exception) {
			String summary = "IAP bundle failed at " + current + ". Successfully rendered: "
					+ (parts.isEmpty() ? "none" : String.join(", ", parts.keySet()))
					+ ".\nNo bundle was published; existing PDFs were not changed.\n" + exception.getMessage();
			lastBundleSummary = summary;
			LOGGER.log(Level.SEVERE, summary, exception);
			throw new IOException(summary, exception);
		} finally {
			if (staging != null) {
				deleteStaging(staging);
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
		data.getMedicalPlan().setIapPage("");
		data.getCanineMedicalPlan().setIapPage("");
		data.setForm205aIapPage("");
		data.getForm205().setIapPage("");
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
				case "ICS 206" -> {
					if (included == null
							? isIapAttachmentSelected(data, formKey)
							: included.contains(formKey)) {
						data.getMedicalPlan().setIapPage(String.valueOf(page));
						page += Ics206PdfRenderer.pageCount(data);
					}
				}
				case "ICS 206CANINE" -> {
					if (included == null
							? isIapAttachmentSelected(data, formKey)
							: included.contains(formKey)) {
						data.getCanineMedicalPlan().setIapPage(String.valueOf(page));
						page += Ics206CaninePdfRenderer.pageCount(data);
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
				case "ICS 205" -> {
					if (includedOrAll(included, formKey)) {
						data.getForm205().setIapPage(String.valueOf(page));
						page += Ics205PdfRenderer.pageCount(data);
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
		copy.setForm205(data.getForm205());
		copy.setForm215a(data.getForm215a());
		copy.setForm208(data.getForm208());
		copy.setForm205aIapPage(data.getForm205aIapPage());
		copy.setForm207(data.getForm207());
		copy.setMedicalPlan(data.getMedicalPlan());
		copy.setCanineMedicalPlan(data.getCanineMedicalPlan());
		copy.setVetDirectory(data.getVetDirectory());
		copy.setStagingLocation(data.getStagingLocation());
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
