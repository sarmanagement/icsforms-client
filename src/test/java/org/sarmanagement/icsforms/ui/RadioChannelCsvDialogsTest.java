/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.comms.ChannelImportService;
import org.sarmanagement.icsforms.comms.ChirpCsvService;
import org.sarmanagement.icsforms.comms.RadioChannelCsvService;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics205PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Headless user-decision tests for transactional preview/import and safe CHIRP exports. */
class RadioChannelCsvDialogsTest {
	private Path directory;
	private AppData data;
	private AppController controller;
	private Ics205Panel panel;
	private FakeDialogs dialogs;

	@BeforeEach
	void setUp() throws Exception {
		directory = Files.createDirectories(Path.of("target", "radio-csv-ui-" + UUID.randomUUID()));
		data = new AppData();
		data.setIcs205Enabled(false);
		data.getForm205().setSpecialInstructions("Local instructions");
		data.getForm205().getChannels().add(Ics205PanelTest.channel("Existing"));
		controller = new AppController(data, new LocalRepository(directory.resolve("incident.json")),
				new PdfExportService(new Ics205PdfRenderer()), new IncidentValidator());
		SwingUtilities.invokeAndWait(() -> panel = new Ics205Panel(controller));
		dialogs = new FakeDialogs();
	}

	@AfterEach
	void tearDown() throws Exception {
		SwingUtilities.invokeAndWait(() -> ((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop());
		try (var files = Files.walk(directory)) {
			for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	@Test
	void cancellingFileOrPreviewLeavesExactLivePlanUntouched() throws Exception {
		Ics205Form before = data.getForm205();
		runImport();
		assertSame(before, data.getForm205());
		writeImport(source("Incoming"));
		dialogs.selection = null;
		runImport();
		assertSame(before, data.getForm205());
		assertEquals("Existing", before.getChannels().get(0).getChannelName());
		assertFalse(controller.isDirty());
		assertNull(dialogs.error);
	}

	@Test
	void malformedInputNeverReachesPreviewOrChangesPlan() throws Exception {
		Path input = directory.resolve("invalid.csv");
		Files.writeString(input, "not,a,valid,native,header\n");
		dialogs.source = new RadioChannelCsvDialogs.ImportSource(input, false);
		Ics205Form before = data.getForm205();
		runImport();
		assertSame(before, data.getForm205());
		assertFalse(dialogs.previewed);
		assertNotNull(dialogs.error);
		assertFalse(controller.isDirty());
	}

	@Test
	void appendPreviewDefaultsSelectAllAndKeepLocalMetadata() throws Exception {
		Ics205Form imported = source("Incoming", "Not selected");
		writeImport(imported);
		SwingUtilities.invokeAndWait(() -> {
			RadioChannelCsvDialogs.ImportPreviewPanel preview = new RadioChannelCsvDialogs.ImportPreviewPanel(imported);
			assertEquals(ChannelImportService.Mode.APPEND, preview.selection().mode());
			assertEquals(List.of(0, 1), preview.selection().rows());
			assertFalse(preview.selection().importMetadata());
			JTable table = find(preview, JTable.class);
			table.setValueAt(false, 1, 0);
			dialogs.selection = preview.selection();
		});
		runImport();
		assertEquals(List.of("Existing", "Incoming"), names());
		assertEquals("Local instructions", data.getForm205().getSpecialInstructions());
		assertTrue(controller.isDirty());
		assertFalse(data.isIcs205Enabled());
	}

	@Test
	void explicitReplaceAndMetadataChoiceApplyOnlyAfterConfirmation() throws Exception {
		Ics205Form imported = source("Replacement");
		imported.setPreparedByName("Imported preparer");
		imported.setPreparedBySignature("Imported signature");
		imported.setPreparedDateTime(java.time.LocalDateTime.of(2026, 10, 24, 19, 15));
		imported.setDatePrepared(java.time.LocalDateTime.of(2026, 10, 24, 17, 15));
		imported.setIapPage("9");
		writeImport(imported);
		dialogs.selection = new RadioChannelCsvDialogs.ImportSelection(List.of(0), ChannelImportService.Mode.REPLACE, true);
		runImport();
		assertEquals(List.of("Replacement"), names());
		assertEquals("Imported instructions", data.getForm205().getSpecialInstructions());
		assertEquals("Imported preparer", data.getForm205().getPreparedByName());
		assertEquals("Imported signature", data.getForm205().getPreparedBySignature());
		assertEquals(imported.getPreparedDateTime(), data.getForm205().getPreparedDateTime());
		assertEquals(imported.getDatePrepared(), data.getForm205().getDatePrepared());
		assertEquals("9", data.getForm205().getIapPage());
	}

	@Test
	void unknownUpdateIdFailsAtomicallyAndMatchingIdUpdatesOnlySelectedRow() throws Exception {
		Ics205Form imported = source("Updated");
		writeImport(imported);
		dialogs.selection = new RadioChannelCsvDialogs.ImportSelection(List.of(0), ChannelImportService.Mode.UPDATE_BY_ID, false);
		Ics205Form before = data.getForm205();
		runImport();
		assertSame(before, data.getForm205());
		assertNotNull(dialogs.error);
		assertFalse(controller.isDirty());
		imported.getChannels().get(0).setChannelId(before.getChannels().get(0).getChannelId());
		writeImport(imported);
		dialogs.error = null;
		runImport();
		assertEquals(List.of("Updated"), names());
		assertNull(dialogs.error);
		assertEquals("Local instructions", data.getForm205().getSpecialInstructions());
	}

	@Test
	void chirpImportRequiresExplicitFormatAndPreviewsBeforeApplying() throws Exception {
		Path input = directory.resolve("chirp.csv");
		Files.writeString(input, "Location,Name,Frequency,Duplex,Offset,Mode\n1,Simplex,155.500,,0,FM\n");
		dialogs.source = new RadioChannelCsvDialogs.ImportSource(input, true);
		dialogs.selection = new RadioChannelCsvDialogs.ImportSelection(List.of(0), ChannelImportService.Mode.APPEND, false);
		runImport();
		assertTrue(dialogs.previewed);
		assertEquals(List.of("Existing", "Simplex"), names());
		assertEquals("155.500", data.getForm205().getChannels().get(1).getTxFrequency());
		assertEquals(RadioChannel.TX_POLICY_TRANSMIT, data.getForm205().getChannels().get(1).getTxPolicy());
	}

	@Test
	void nativeExportIncludesHiddenRowsAndDoesNotChangePrintSelection() throws Exception {
		RadioChannel channel = data.getForm205().getChannels().get(0);
		channel.setIncludeOnForm(false);
		dialogs.destination = directory.resolve("native.csv");
		runExport(false);
		Ics205Form reloaded = new RadioChannelCsvService().read(dialogs.destination);
		assertEquals(List.of("Existing"), reloaded.getChannels().stream().map(RadioChannel::getChannelName).toList());
		assertFalse(reloaded.getChannels().get(0).isIncludeOnForm());
		assertFalse(data.isIcs205Enabled());
		assertFalse(controller.isDirty());
	}

	@Test
	void chirpProjectionCancellationDoesNotWriteAndExplicitConsentExcludesUnsupportedRows() throws Exception {
		dialogs.destination = directory.resolve("chirp-export.csv");
		dialogs.acceptProjection = false;
		runExport(true);
		assertNotNull(dialogs.report);
		assertEquals(1, dialogs.report.excludedChannels().size());
		assertFalse(Files.exists(dialogs.destination));
		RadioChannel valid = Ics205PanelTest.channel("Analog");
		valid.setChannelNumber("2");
		valid.setRxFrequency("155.500");
		valid.setTxFrequency("155.500");
		valid.setTxPolicy(RadioChannel.TX_POLICY_TRANSMIT);
		valid.setMode("FM");
		valid.setRxBandwidth("25");
		valid.setTxBandwidth("25");
		data.getForm205().getChannels().add(valid);
		dialogs.acceptProjection = true;
		runExport(true);
		assertTrue(Files.exists(dialogs.destination));
		assertEquals(List.of("Analog"), new ChirpCsvService().importCsv(Files.readString(dialogs.destination))
				.getChannels().stream().map(RadioChannel::getChannelName).toList());
		assertEquals(List.of("Existing", "Analog"), names());
	}

	@Test
	void cancellingOverwritePreservesExistingFile() throws Exception {
		dialogs.destination = directory.resolve("existing.csv");
		Files.writeString(dialogs.destination, "Existing bytes");
		dialogs.acceptOverwrite = false;
		runExport(false);
		assertEquals("Existing bytes", Files.readString(dialogs.destination));
	}

	/**
	 * Writes an input fixture inside the build directory.
	 *
	 * @param imported parsed native fixture.
	 * @throws java.io.IOException when writing fails.
	 */
	private void writeImport(Ics205Form imported) throws java.io.IOException {
		Path path = directory.resolve("import.csv");
		new RadioChannelCsvService().write(path, imported);
		dialogs.source = new RadioChannelCsvDialogs.ImportSource(path, false);
	}

	/**
	 * Creates an imported plan with distinct names.
	 *
	 * @param names channel names.
	 * @return detached import plan.
	 */
	private static Ics205Form source(String... names) {
		Ics205Form form = new Ics205Form();
		form.setSpecialInstructions("Imported instructions");
		for (String name : names) {
			form.getChannels().add(Ics205PanelTest.channel(name));
		}
		return form;
	}

	/**
	 * Returns current channel names in display order.
	 *
	 * @return channel names.
	 */
	private List<String> names() {
		return data.getForm205().getChannels().stream().map(RadioChannel::getChannelName).toList();
	}

	/**
	 * Executes import on Swing's event thread.
	 *
	 * @throws Exception when the event fails.
	 */
	private void runImport() throws Exception {
		SwingUtilities.invokeAndWait(() -> new RadioChannelCsvDialogs(panel, controller, dialogs).importCsv());
	}

	/**
	 * Executes export on Swing's event thread.
	 *
	 * @param chirp whether to use CHIRP projection.
	 * @throws Exception when the event fails.
	 */
	private void runExport(boolean chirp) throws Exception {
		SwingUtilities.invokeAndWait(() -> new RadioChannelCsvDialogs(panel, controller, dialogs).exportCsv(chirp));
	}

	/**
	 * Finds a component by type in a headless panel hierarchy.
	 *
	 * @param root component tree.
	 * @param type requested type.
	 * @param <T> component type.
	 * @return matching component or null.
	 */
	private static <T extends Component> T find(Component root, Class<T> type) {
		if (type.isInstance(root)) {
			return type.cast(root);
		}
		if (root instanceof Container container) {
			for (Component component : container.getComponents()) {
				T found = find(component, type);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** Explicit test decisions; never opens real dialogs or choosers. */
	private static final class FakeDialogs implements RadioChannelCsvDialogs.Dialogs {
		private RadioChannelCsvDialogs.ImportSource source;
		private RadioChannelCsvDialogs.ImportSelection selection;
		private ChirpCsvService.ExportReport report;
		private Path destination;
		private boolean previewed;
		private boolean acceptProjection;
		private boolean acceptOverwrite;
		private String error;

		/** {@inheritDoc} */
		@Override
		public RadioChannelCsvDialogs.ImportSource chooseImport(Component parent) {
			return source;
		}

		/** {@inheritDoc} */
		@Override
		public RadioChannelCsvDialogs.ImportSelection preview(Component parent, Ics205Form imported) {
			previewed = true;
			return selection;
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmProjection(Component parent, ChirpCsvService.ExportReport report) {
			this.report = report;
			return acceptProjection;
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseExport(Component parent, boolean chirp) {
			return destination;
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmOverwrite(Component parent, Path path) {
			return acceptOverwrite;
		}

		/** {@inheritDoc} */
		@Override
		public void error(Component parent, String message) {
			error = message;
		}
	}
}
