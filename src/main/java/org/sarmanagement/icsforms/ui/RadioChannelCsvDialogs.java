/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.comms.ChannelImportService;
import org.sarmanagement.icsforms.comms.ChirpCsvService;
import org.sarmanagement.icsforms.comms.RadioChannelCsvService;
import org.sarmanagement.icsforms.model.Ics205Form;
import org.sarmanagement.icsforms.model.RadioChannel;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Transactional native/CHIRP interchange with injectable dialogs for headless tests.
 */
final class RadioChannelCsvDialogs {
	private final Ics205Panel parent;
	private final AppController controller;
	private final Dialogs dialogs;

	/**
	 * Creates parent-scoped Swing file interchange.
	 *
	 * @param parent communications editor.
	 * @param controller active incident controller.
	 */
	RadioChannelCsvDialogs(Ics205Panel parent, AppController controller) {
		this(parent, controller, new SwingDialogs());
	}

	/**
	 * Creates interchange with controlled user decisions.
	 *
	 * @param parent communications editor.
	 * @param controller active incident controller.
	 * @param dialogs file and confirmation interactions.
	 */
	RadioChannelCsvDialogs(Ics205Panel parent, AppController controller, Dialogs dialogs) {
		this.parent = parent;
		this.controller = controller;
		this.dialogs = dialogs;
	}

	/** Parses and previews the entire input before replacing any live incident data. */
	void importCsv() {
		importCsv(false);
	}

	/**
	 * Opens import with an explicit initial format and previews before changing live data.
	 *
	 * @param chirp whether to initially select CHIRP generic CSV.
	 */
	void importCsv(boolean chirp) {
		try {
			ImportSource source = dialogs.chooseImport(parent, chirp);
			if (source == null) {
				return;
			}
			String csv = Files.readString(source.path(), StandardCharsets.UTF_8);
			Ics205Form imported = source.chirp() ? new ChirpCsvService().importCsv(csv)
					: new RadioChannelCsvService().importCsv(csv);
			ImportSelection selection = dialogs.preview(parent, imported);
			if (selection == null) {
				return;
			}
			Ics205Form result = new ChannelImportService().merge(controller.getData().getForm205(), imported,
					selection.rows(), selection.mode());
			if (selection.importMetadata()) {
				result.setSpecialInstructions(imported.getSpecialInstructions());
				result.setPreparedByName(imported.getPreparedByName());
				result.setPreparedBySignature(imported.getPreparedBySignature());
				result.setPreparedDateTime(imported.getPreparedDateTime());
				result.setDatePrepared(imported.getDatePrepared());
				result.setIapPage(imported.getIapPage());
				if (selection.mode() == ChannelImportService.Mode.REPLACE) {
					result.setFormId(imported.getFormId());
				}
			}
			controller.getData().setForm205(result);
			controller.markDirty();
			parent.refreshFromModel();
		} catch (IOException | RuntimeException exception) {
			dialogs.error(parent, "Import cancelled; communications plan unchanged. " + exception.getMessage());
		}
	}

	/**
	 * Exports all native data or an explicitly accepted safe CHIRP projection.
	 *
	 * @param chirp whether to create CHIRP generic CSV instead of lossless native CSV.
	 */
	void exportCsv(boolean chirp) {
		try {
			parent.pushToModel();
			String csv;
			if (chirp) {
				ChirpCsvService.ExportReport report = new ChirpCsvService().exportCsv(controller.getData().getForm205());
				if (!dialogs.confirmProjection(parent, report)) {
					return;
				}
				csv = report.csv();
			} else {
				csv = new RadioChannelCsvService().exportCsv(controller.getData().getForm205());
			}
			Path path = dialogs.chooseExport(parent, chirp);
			if (path == null || Files.exists(path) && !dialogs.confirmOverwrite(parent, path)) {
				return;
			}
			RadioChannelCsvService.writeCsv(path, csv);
		} catch (IOException | RuntimeException exception) {
			dialogs.error(parent, "CSV export failed. " + exception.getMessage());
		}
	}

	/**
	 * File and format explicitly selected for import.
	 *
	 * @param path source file.
	 * @param chirp true for CHIRP generic CSV.
	 */
	record ImportSource(Path path, boolean chirp) {
	}

	/**
	 * Confirmed import transaction.
	 *
	 * @param rows selected zero-based channel indexes.
	 * @param mode explicit append, replace or identity update.
	 * @param importMetadata whether to replace local instructions and preparation details.
	 */
	record ImportSelection(List<Integer> rows, ChannelImportService.Mode mode, boolean importMetadata) {
	}

	/** Injectable user interactions; cancellation is represented by null or false. */
	interface Dialogs {
		/**
		 * Chooses the import file and format.
		 *
		 * @param parent owning editor.
		 * @return source or null.
		 */
		ImportSource chooseImport(Component parent);

		/**
		 * Chooses an import source with a preferred initial format.
		 *
		 * @param parent owning editor.
		 * @param chirp whether to initially select CHIRP generic CSV.
		 * @return source or null.
		 */
		default ImportSource chooseImport(Component parent, boolean chirp) {
			return chooseImport(parent);
		}

		/**
		 * Previews parsed channels without modifying the live plan.
		 *
		 * @param parent owning editor.
		 * @param imported detached input.
		 * @return confirmed selection or null.
		 */
		ImportSelection preview(Component parent, Ics205Form imported);

		/**
		 * Confirms the CHIRP diagnostics and explicit exclusions.
		 *
		 * @param parent owning editor.
		 * @param report conversion diagnostics.
		 * @return true only when explicitly accepted.
		 */
		boolean confirmProjection(Component parent, ChirpCsvService.ExportReport report);

		/**
		 * Chooses an export destination.
		 *
		 * @param parent owning editor.
		 * @param chirp whether exporting CHIRP.
		 * @return path or null.
		 */
		Path chooseExport(Component parent, boolean chirp);

		/**
		 * Confirms overwriting an existing file.
		 *
		 * @param parent owning editor.
		 * @param path existing file.
		 * @return whether replacement was accepted.
		 */
		boolean confirmOverwrite(Component parent, Path path);

		/**
		 * Shows an operation error.
		 *
		 * @param parent owning editor.
		 * @param message error details.
		 */
		void error(Component parent, String message);
	}

	/** Document-modal implementations of file and preview interactions. */
	private static final class SwingDialogs implements Dialogs {
		/** {@inheritDoc} */
		@Override
		public ImportSource chooseImport(Component parent) {
			return chooseImport(parent, false);
		}

		/** {@inheritDoc} */
		@Override
		public ImportSource chooseImport(Component parent, boolean chirp) {
			JFileChooser chooser = fileChooser("Import communications plan CSV");
			JComboBox<String> format = new JComboBox<>(new String[]{"Native ICS 205 CSV (lossless)", "CHIRP generic CSV"});
			format.setSelectedIndex(chirp ? 1 : 0);
			JPanel accessory = new JPanel(new BorderLayout(4, 4));
			accessory.add(new JLabel("Input format (explicit):"), BorderLayout.NORTH);
			accessory.add(format, BorderLayout.CENTER);
			chooser.setAccessory(accessory);
			return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
					? new ImportSource(chooser.getSelectedFile().toPath(), format.getSelectedIndex() == 1) : null;
		}

		/** {@inheritDoc} */
		@Override
		public ImportSelection preview(Component parent, Ics205Form imported) {
			ImportPreviewPanel panel = new ImportPreviewPanel(imported);
			return UiSupport.showResizableConfirmDialog(parent, "Preview channel import", panel, new Dimension(980, 560))
					? panel.selection() : null;
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmProjection(Component parent, ChirpCsvService.ExportReport report) {
			JTextArea text = new JTextArea(String.join("\n", report.diagnostics()), 12, 75);
			text.setEditable(false);
			text.setLineWrap(true);
			text.setWrapStyleWord(true);
			JPanel panel = new JPanel(new BorderLayout());
			panel.add(new JScrollPane(text), BorderLayout.CENTER);
			JCheckBox exclude = new JCheckBox("Explicitly exclude the " + report.excludedChannels().size()
					+ " unsupported channel(s) listed above");
			if (!report.excludedChannels().isEmpty()) {
				panel.add(exclude, BorderLayout.SOUTH);
			}
			return UiSupport.showResizableConfirmDialog(parent, "Review CHIRP programming projection",
					panel, new Dimension(820, 420)) && (report.excludedChannels().isEmpty() || exclude.isSelected());
		}

		/** {@inheritDoc} */
		@Override
		public Path chooseExport(Component parent, boolean chirp) {
			JFileChooser chooser = fileChooser(chirp ? "Export CHIRP generic CSV" : "Export native ICS 205 CSV");
			chooser.setSelectedFile(new java.io.File(chirp ? "communications-chirp.csv" : "ics205.csv"));
			return chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION
					? chooser.getSelectedFile().toPath() : null;
		}

		/** {@inheritDoc} */
		@Override
		public boolean confirmOverwrite(Component parent, Path path) {
			return UiSupport.showResizableOptionDialog(parent, "Replace CSV file?",
					new JLabel("Replace " + path + "?"), new Dimension(700, 140),
					new String[]{"Replace", "Cancel"}, "Cancel") == 0;
		}

		/** {@inheritDoc} */
		@Override
		public void error(Component parent, String message) {
			RadioChannelPicker.showError(parent, message);
		}

		/**
		 * Creates an owner-scoped file chooser rather than application-modal dialogs.
		 *
		 * @param title dialog title.
		 * @return CSV file chooser.
		 */
		private static JFileChooser fileChooser(String title) {
			JFileChooser chooser = new JFileChooser() {
				/** {@inheritDoc} */
				@Override
				protected JDialog createDialog(Component parent) {
					JDialog dialog = super.createDialog(parent);
					dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
					return dialog;
				}
			};
			chooser.setDialogTitle(title);
			chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
			return chooser;
		}
	}

	/** Selectable read-only channel preview with append as the safe default. */
	static final class ImportPreviewPanel extends JPanel {
		private final DefaultTableModel model;
		private final JComboBox<ChannelImportService.Mode> mode = new JComboBox<>(ChannelImportService.Mode.values());
		private final JCheckBox metadata = new JCheckBox("Replace local instructions and preparation metadata", false);

		/**
		 * Displays every imported channel field, including values not printed on ICS 205.
		 *
		 * @param imported detached parsed plan.
		 */
		ImportPreviewPanel(Ics205Form imported) {
			super(new BorderLayout(6, 6));
			List<String> columns = new ArrayList<>(List.of("Import"));
			for (RadioChannelEditorPanel.Field field : RadioChannelEditorPanel.Field.values()) {
				columns.add(field.label);
			}
			columns.addAll(List.of("Transmit policy", "Include on form", "Programming metadata"));
			model = new DefaultTableModel(columns.toArray(), 0) {
				/** {@inheritDoc} */
				@Override
				public Class<?> getColumnClass(int column) {
					return column == 0 ? Boolean.class : Object.class;
				}

				/** {@inheritDoc} */
				@Override
				public boolean isCellEditable(int row, int column) {
					return column == 0;
				}
			};
			for (RadioChannel channel : imported.getChannels()) {
				List<Object> row = new ArrayList<>(List.of(true));
				for (RadioChannelEditorPanel.Field field : RadioChannelEditorPanel.Field.values()) {
					row.add(field.getter.apply(channel));
				}
				row.addAll(List.of(channel.getTxPolicy(), channel.isIncludeOnForm(), channel.getProgrammingMetadata()));
				model.addRow(row.toArray());
			}
			JTable table = new JTable(model);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			table.getAccessibleContext().setAccessibleName("Select channels to import");
			for (int column = 1; column < table.getColumnCount(); column++) {
				table.getColumnModel().getColumn(column).setPreferredWidth(160);
			}
			JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
			mode.setSelectedItem(ChannelImportService.Mode.APPEND);
			options.add(new JLabel("Channel operation:"));
			options.add(mode);
			options.add(metadata);
			add(options, BorderLayout.NORTH);
			add(new JScrollPane(table), BorderLayout.CENTER);
			JTextArea instructions = new JTextArea("Imported special instructions:\n" + imported.getSpecialInstructions()
					+ "\nPrepared by: " + imported.getPreparedByName() + "\nSignature: " + imported.getPreparedBySignature()
					+ "\nForm date prepared (UTC): " + imported.getDatePrepared()
					+ "\nPreparation date/time (UTC): " + imported.getPreparedDateTime() + "\nIAP page: " + imported.getIapPage(),
					6, 70);
			instructions.setEditable(false);
			add(new JScrollPane(instructions), BorderLayout.SOUTH);
		}

		/**
		 * Collects only selected rows and explicit operation choices.
		 *
		 * @return confirmed import options.
		 */
		ImportSelection selection() {
			List<Integer> rows = new ArrayList<>();
			for (int row = 0; row < model.getRowCount(); row++) {
				if (Boolean.TRUE.equals(model.getValueAt(row, 0))) {
					rows.add(row);
				}
			}
			return new ImportSelection(rows, (ChannelImportService.Mode) mode.getSelectedItem(), metadata.isSelected());
		}
	}
}
