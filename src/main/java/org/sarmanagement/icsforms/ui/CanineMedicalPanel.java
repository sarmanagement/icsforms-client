/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.medical.AddressGeocoder;
import org.sarmanagement.icsforms.medical.DrivingRouteProvider;
import org.sarmanagement.icsforms.medical.GeoPoint;
import org.sarmanagement.icsforms.medical.OpenRouteServiceDrivingRouter;
import org.sarmanagement.icsforms.medical.OpenRouteServiceGeocoder;
import org.sarmanagement.icsforms.medical.VetDirectoryFileService;
import org.sarmanagement.icsforms.medical.VetRoutingService;
import org.sarmanagement.icsforms.medical.VetShortlistSelector;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * Canine-specific medical workspace with local regional records and road routing.
 */
public class CanineMedicalPanel extends JPanel {
	private static final String UNKNOWN = "Unknown";
	private final AppController controller;
	private final Path defaultDirectory;
	private final AddressGeocoder geocoder;
	private final DrivingRouteProvider routeProvider;
	private final VetDirectoryFileService fileService = new VetDirectoryFileService();
	private final VetShortlistSelector shortlistSelector = new VetShortlistSelector();
	private final JCheckBox active = new JCheckBox("Include active canine medical plan in the IAP");
	private final JTextField stagingLabel = new JTextField();
	private final JTextField stagingAddress = new JTextField();
	private final JTextField stagingLatitude = new JTextField();
	private final JTextField stagingLongitude = new JTextField();
	private final JTextField shortlistLimit = new JTextField("8", 4);
	private final JCheckBox includeUnknown = new JCheckBox("Also route unknown 24-hour status (not confirmed)");
	private final DefaultTableModel directoryModel = new DefaultTableModel(new String[] {
			"ID", "Name", "Town", "Address", "Contacts (; separated)", "24 Hours", "Availability notes",
			"Capabilities", "Verified at", "Latitude", "Longitude", "Source", "Record"
	}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return column > 0 && column < 12;
		}
	};
	private final DefaultTableModel selectionModel = new DefaultTableModel(new String[] {
			"Name", "Town", "Address", "Contact numbers", "24 Hours", "Distance m", "Duration s", "Method",
			"Capabilities / notes", "ID", "Record"
	}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return column <= 4 || column == 8;
		}
	};
	private final JTable directoryTable = new JTable(directoryModel);
	private final JTable selectionTable = new JTable(selectionModel);
	private final JTextField poisonContact = new JTextField();
	private final JTextField onsiteVet = new JTextField();
	private final JTextField onsiteDvm = new JTextField();
	private final JTextArea capabilityNotes = new JTextArea(3, 50);
	private final JTextArea procedures = new JTextArea(4, 50);
	private final JCheckBox aviationUsed = new JCheckBox("Aviation assets are used; coordinate with Air Operations");
	private final JTextField preparedBy = new JTextField();
	private final JTextField preparerSignature = new JTextField();
	private final JTextField preparedAt = new JTextField();
	private final JTextField safetyOfficer = new JTextField();
	private final JTextField safetySignature = new JTextField();
	private final JComboBox<String> reviewStatus = new JComboBox<>(
			new String[] {"Not reviewed", "Reviewed", "Approved"});
	private final JLabel status = new JLabel("Vet addresses are sent to the configured geocoding/routing provider.");
	private final JProgressBar progress = new JProgressBar();
	private Consumer<String> exportRequest = ignored -> { };
	private boolean loading;
	private boolean flushing;
	private SwingWorker<?, ?> activeWorker;

	/**
	 * Creates the canine medical workspace using OpenRouteService as the road provider.
	 *
	 * @param controller application controller.
	 * @param defaultDirectory default local file chooser location.
	 */
	public CanineMedicalPanel(AppController controller, Path defaultDirectory) {
		this(controller, defaultDirectory, new OpenRouteServiceGeocoder(), new OpenRouteServiceDrivingRouter());
	}

	/**
	 * Creates the workspace with injectable geocoding and routing providers.
	 *
	 * @param controller application controller.
	 * @param defaultDirectory default local file chooser location.
	 * @param geocoder address geocoder.
	 * @param routeProvider ground routing provider.
	 */
	public CanineMedicalPanel(AppController controller, Path defaultDirectory, AddressGeocoder geocoder,
			DrivingRouteProvider routeProvider) {
		super(new BorderLayout(6, 6));
		this.controller = controller;
		this.defaultDirectory = defaultDirectory;
		this.geocoder = geocoder;
		this.routeProvider = routeProvider;
		add(buildTop(), BorderLayout.NORTH);
		add(buildDirectoryArea(), BorderLayout.CENTER);
		progress.setIndeterminate(true);
		progress.setVisible(false);
		JPanel bottom = new JPanel(new BorderLayout(6, 4));
		bottom.add(buildPlanFields(), BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout(6, 2));
		footer.add(progress, BorderLayout.WEST);
		footer.add(status, BorderLayout.CENTER);
		bottom.add(footer, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);
		configureListeners();
		refreshFromModel();
	}

	/**
	 * Sets the shared ICS 206CANINE export action.
	 *
	 * @param exportRequest export callback; null disables the shortcut.
	 */
	public void setExportRequest(Consumer<String> exportRequest) {
		this.exportRequest = exportRequest == null ? ignored -> { } : exportRequest;
	}

	/**
	 * Refreshes the editor while retaining the model's directory and accepted snapshots.
	 */
	public void refreshFromModel() {
		loading = true;
		try {
			AppData data = controller.getData();
			StagingLocation staging = data.getStagingLocation();
			CanineMedicalPlan plan = data.getCanineMedicalPlan();
			active.setSelected(plan.isActive());
			stagingLabel.setText(staging.getLabel());
			stagingAddress.setText(staging.getAddress());
			stagingLatitude.setText(staging.getLatitude() == null ? "" : staging.getLatitude().toString());
			stagingLongitude.setText(staging.getLongitude() == null ? "" : staging.getLongitude().toString());
			poisonContact.setText(plan.getPoisonControlContact());
			onsiteVet.setText(plan.getOnsiteVeterinarian());
			onsiteDvm.setText(plan.getVeterinarianDvm());
			capabilityNotes.setText(plan.getVeterinaryCapabilityNotes());
			procedures.setText(plan.getEmergencyProcedures());
			aviationUsed.setSelected(plan.isAviationAssetsUsed());
			preparedBy.setText(plan.getPreparedByMedicalUnitLeader());
			preparerSignature.setText(plan.getPreparerSignature());
			preparedAt.setText(plan.getPreparedAt() == null ? "" : plan.getPreparedAt().toString());
			safetyOfficer.setText(plan.getReviewedBySafetyOfficer());
			safetySignature.setText(plan.getSafetyOfficerSignature());
			reviewStatus.setSelectedItem(plan.getReviewStatus());
			loadDirectory(data.getVetDirectory());
			loadSelection(plan.getSelectedFacilities());
		} finally {
			loading = false;
		}
	}

	/**
	 * Flushes editor state and any active table edits to the incident model.
	 */
	public void pushToModel() {
		if (flushing) {
			return;
		}
		flushing = true;
		try {
			stopEditing(directoryTable);
			stopEditing(selectionTable);
			AppData data = controller.getData();
			StagingLocation staging = data.getStagingLocation();
			staging.setLabel(stagingLabel.getText());
			staging.setAddress(stagingAddress.getText());
			Double latitude = coordinate(stagingLatitude.getText(), -90, 90);
			Double longitude = coordinate(stagingLongitude.getText(), -180, 180);
			if (latitude != null && longitude != null
					&& (!latitude.equals(staging.getLatitude()) || !longitude.equals(staging.getLongitude()))) {
				staging.setResolution(stagingAddress.getText(), latitude, longitude, "manual",
						"Operator-entered coordinates", Instant.now());
			}
			data.getCanineMedicalPlan().setActive(active.isSelected());
			data.getCanineMedicalPlan().setPoisonControlContact(poisonContact.getText());
			data.getCanineMedicalPlan().setOnsiteVeterinarian(onsiteVet.getText());
			data.getCanineMedicalPlan().setVeterinarianDvm(onsiteDvm.getText());
			data.getCanineMedicalPlan().setVeterinaryCapabilityNotes(capabilityNotes.getText());
			data.getCanineMedicalPlan().setEmergencyProcedures(procedures.getText());
			data.getCanineMedicalPlan().setAviationAssetsUsed(aviationUsed.isSelected());
			data.getCanineMedicalPlan().setPreparedByMedicalUnitLeader(preparedBy.getText());
			data.getCanineMedicalPlan().setPreparerSignature(preparerSignature.getText());
			setPreparedAt(data.getCanineMedicalPlan());
			data.getCanineMedicalPlan().setReviewedBySafetyOfficer(safetyOfficer.getText());
			data.getCanineMedicalPlan().setSafetyOfficerSignature(safetySignature.getText());
			data.getCanineMedicalPlan().setReviewStatus(String.valueOf(reviewStatus.getSelectedItem()));
			saveDirectory(data.getVetDirectory());
			saveSelection(data.getCanineMedicalPlan());
		} finally {
			flushing = false;
		}
	}

	/**
	 * Builds the incident/base controls and local directory workflow.
	 *
	 * @return top control panel.
	 */
	private JPanel buildTop() {
		JPanel root = new JPanel(new BorderLayout(4, 4));
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bar.add(active);
		JButton export = new JButton("Export ICS 206CANINE PDF");
		export.addActionListener(event -> {
			pushToModel();
			exportRequest.accept("ICS 206CANINE");
		});
		bar.add(export);
		JButton load = new JButton("Load regional JSON/CSV…");
		load.addActionListener(event -> importFiles());
		JButton save = new JButton("Save directory JSON/CSV…");
		save.addActionListener(event -> exportDirectory());
		bar.add(load);
		bar.add(save);
		root.add(bar, BorderLayout.NORTH);

		JPanel base = new JPanel(new GridLayout(0, 6, 4, 4));
		base.setBorder(BorderFactory.createTitledBorder("Staging / base location"));
		base.add(new JLabel("Label"));
		base.add(stagingLabel);
		base.add(new JLabel("Full address"));
		base.add(stagingAddress);
		JButton geocodeBase = new JButton("Look up address…");
		geocodeBase.addActionListener(event -> geocodeBase());
		base.add(geocodeBase);
		JButton manualCoordinates = new JButton("Set coordinates");
		manualCoordinates.addActionListener(event -> setManualBaseCoordinates());
		base.add(manualCoordinates);
		base.add(new JLabel("Latitude"));
		base.add(stagingLatitude);
		base.add(new JLabel("Longitude"));
		base.add(stagingLongitude);
		base.add(new JLabel("Closest N"));
		base.add(shortlistLimit);
		JButton calculate = new JButton("Calculate all eligible driving routes");
		calculate.addActionListener(event -> calculateRoutes());
		base.add(calculate);
		JButton propose = new JButton("Preview closest 24-hour vets…");
		propose.addActionListener(event -> previewSelection());
		base.add(propose);
		root.add(base, BorderLayout.CENTER);
		JPanel note = new JPanel(new FlowLayout(FlowLayout.LEFT));
		note.add(includeUnknown);
		note.add(new JLabel("Only confirmed Yes is selected by default. Routing never verifies availability."));
		root.add(note, BorderLayout.SOUTH);
		return root;
	}

	/**
	 * Builds the directory and accepted-plan tables without truncating source data.
	 *
	 * @return table area.
	 */
	private JPanel buildDirectoryArea() {
		configureTables();
		JPanel directory = new JPanel(new BorderLayout(4, 4));
		directory.setBorder(BorderFactory.createTitledBorder("All regional records (retained in incident)"));
		directory.add(new JScrollPane(directoryTable), BorderLayout.CENTER);
		JPanel directoryControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton add = new JButton("Add facility");
		add.addActionListener(event -> addFacility());
		JButton remove = new JButton("Remove selected");
		remove.addActionListener(event -> removeDirectoryRows());
		JButton geocodeVet = new JButton("Look up selected address…");
		geocodeVet.addActionListener(event -> geocodeSelectedVet());
		directoryControls.add(add);
		directoryControls.add(remove);
		directoryControls.add(geocodeVet);
		directory.add(directoryControls, BorderLayout.SOUTH);

		JPanel selected = new JPanel(new BorderLayout(4, 4));
		selected.setBorder(BorderFactory.createTitledBorder(
				"Accepted plan snapshots (manual include/exclude/edit/reorder; directory stays intact)"));
		selected.add(new JScrollPane(selectionTable), BorderLayout.CENTER);
		JPanel selectionControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton includeSelected = new JButton("Include selected directory rows");
		includeSelected.addActionListener(event -> addSelectedFacilities());
		JButton excludeSelected = new JButton("Exclude selected plan rows");
		excludeSelected.addActionListener(event -> removeSelectionRows());
		JButton up = new JButton("Move up");
		up.addActionListener(event -> moveSelection(-1));
		JButton down = new JButton("Move down");
		down.addActionListener(event -> moveSelection(1));
		selectionControls.add(includeSelected);
		selectionControls.add(excludeSelected);
		selectionControls.add(up);
		selectionControls.add(down);
		selected.add(selectionControls, BorderLayout.SOUTH);
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, directory, selected);
		split.setResizeWeight(0.55);
		JPanel area = new JPanel(new BorderLayout());
		area.add(split, BorderLayout.CENTER);
		return area;
	}

	/**
	 * Builds editable canine procedures and acknowledgment fields.
	 *
	 * @return canine plan details.
	 */
	private JPanel buildPlanFields() {
		JPanel root = new JPanel(new GridLayout(1, 2, 6, 4));
		JPanel clinical = new JPanel(new GridLayout(0, 1, 2, 2));
		clinical.setBorder(BorderFactory.createTitledBorder("Adapted canine medical plan"));
		clinical.add(new JLabel("Poison-control contact text (editable)"));
		clinical.add(poisonContact);
		clinical.add(new JLabel("Onsite veterinarian"));
		clinical.add(onsiteVet);
		clinical.add(new JLabel("DVM"));
		clinical.add(onsiteDvm);
		clinical.add(new JLabel("Veterinary capability notes / preamble"));
		clinical.add(new JScrollPane(capabilityNotes));
		clinical.add(new JLabel("Special emergency procedures"));
		clinical.add(new JScrollPane(procedures));
		clinical.add(aviationUsed);
		JPanel signatures = new JPanel(new GridLayout(0, 2, 4, 4));
		signatures.setBorder(BorderFactory.createTitledBorder("Preparation / Safety review"));
		signatures.add(new JLabel("Medical Unit Leader"));
		signatures.add(preparedBy);
		signatures.add(new JLabel("Preparer signature"));
		signatures.add(preparerSignature);
		signatures.add(new JLabel("Prepared at (yyyy-MM-ddTHH:mm)"));
		signatures.add(preparedAt);
		signatures.add(new JLabel("Safety Officer"));
		signatures.add(safetyOfficer);
		signatures.add(new JLabel("Safety Officer signature"));
		signatures.add(safetySignature);
		signatures.add(new JLabel("Review status"));
		signatures.add(reviewStatus);
		root.add(clinical);
		root.add(signatures);
		return root;
	}

	/**
	 * Configures table columns and hides only internal object references.
	 */
	private void configureTables() {
		directoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		selectionTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		directoryTable.getColumnModel().getColumn(12).setMinWidth(0);
		directoryTable.getColumnModel().getColumn(12).setMaxWidth(0);
		directoryTable.getColumnModel().getColumn(12).setWidth(0);
		selectionTable.getColumnModel().getColumn(9).setMinWidth(0);
		selectionTable.getColumnModel().getColumn(9).setMaxWidth(0);
		selectionTable.getColumnModel().getColumn(9).setWidth(0);
		selectionTable.getColumnModel().getColumn(10).setMinWidth(0);
		selectionTable.getColumnModel().getColumn(10).setMaxWidth(0);
		selectionTable.getColumnModel().getColumn(10).setWidth(0);
		directoryTable.getColumnModel().getColumn(5).setCellEditor(
				new DefaultCellEditor(new JComboBox<>(new String[] {"Yes", "No", UNKNOWN})));
		selectionTable.getColumnModel().getColumn(4).setCellEditor(
				new DefaultCellEditor(new JComboBox<>(new String[] {"Yes", "No", UNKNOWN})));
	}

	/**
	 * Attaches change listeners and cancels stale network work when data changes.
	 */
	private void configureListeners() {
		active.addActionListener(event -> inputChanged(false));
		aviationUsed.addActionListener(event -> inputChanged(false));
		reviewStatus.addActionListener(event -> inputChanged(false));
		for (JTextField field : List.of(stagingLabel, stagingAddress, stagingLatitude, stagingLongitude,
				shortlistLimit, poisonContact, onsiteVet, onsiteDvm, preparedBy, preparerSignature, preparedAt,
				safetyOfficer, safetySignature)) {
			watch(field, field == stagingAddress || field == stagingLatitude || field == stagingLongitude);
		}
		watch(capabilityNotes, false);
		watch(procedures, false);
		directoryModel.addTableModelListener(event -> inputChanged(true));
		selectionModel.addTableModelListener(event -> inputChanged(false));
	}

	/**
	 * Registers a document listener for a text field.
	 *
	 * @param field editable field.
	 * @param invalidatesRouting whether an edit invalidates route requests.
	 */
	private void watch(JTextField field, boolean invalidatesRouting) {
		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				inputChanged(invalidatesRouting);
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				inputChanged(invalidatesRouting);
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				inputChanged(invalidatesRouting);
			}
		});
	}

	/**
	 * Registers a document listener for a multiline field.
	 *
	 * @param area editable field.
	 * @param invalidatesRouting whether an edit invalidates route requests.
	 */
	private void watch(JTextArea area, boolean invalidatesRouting) {
		area.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				inputChanged(invalidatesRouting);
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				inputChanged(invalidatesRouting);
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				inputChanged(invalidatesRouting);
			}
		});
	}

	/**
	 * Flushes edits and schedules ordinary incident autosave.
	 *
	 * @param invalidateRoutes whether the edit changes input locations or directory data.
	 */
	private void inputChanged(boolean invalidateRoutes) {
		if (!loading && !flushing) {
			if (invalidateRoutes) {
				cancelWorker();
			}
			pushToModel();
			controller.markDirty();
		}
	}

	/**
	 * Loads all reusable directory entries into the regional table.
	 *
	 * @param directory incident-owned directory.
	 */
	private void loadDirectory(VetDirectory directory) {
		directoryModel.setRowCount(0);
		for (VetFacility facility : directory.getFacilities()) {
			directoryModel.addRow(directoryRow(facility));
		}
	}

	/**
	 * Loads accepted snapshots independently from the regional directory.
	 *
	 * @param facilities plan snapshots.
	 */
	private void loadSelection(List<VetFacility> facilities) {
		selectionModel.setRowCount(0);
		for (VetFacility facility : facilities) {
			selectionModel.addRow(selectionRow(facility));
		}
	}

	/**
	 * Saves every directory table row without replacing the accepted shortlist.
	 *
	 * @param directory destination directory.
	 */
	private void saveDirectory(VetDirectory directory) {
		List<VetFacility> facilities = new ArrayList<>();
		for (int row = 0; row < directoryModel.getRowCount(); row++) {
			VetFacility facility = record(directoryModel.getValueAt(row, 12));
			String oldAddress = facility.getAddress();
			facility.setName(cell(directoryModel, row, 1));
			facility.setTown(cell(directoryModel, row, 2));
			facility.setAddress(cell(directoryModel, row, 3));
			facility.setContactNumbers(contacts(cell(directoryModel, row, 4)));
			facility.setAvailable24Hours(parseBoolean(cell(directoryModel, row, 5)));
			facility.setAvailabilityNotes(cell(directoryModel, row, 6));
			facility.setCapabilities(cell(directoryModel, row, 7));
			Double latitude = coordinate(cell(directoryModel, row, 9), -90, 90);
			Double longitude = coordinate(cell(directoryModel, row, 10), -180, 180);
			if (!oldAddress.equals(facility.getAddress())) {
				directoryModel.setValueAt("", row, 9);
				directoryModel.setValueAt("", row, 10);
			} else if (latitude != null && longitude != null
					&& (!latitude.equals(facility.getLatitude()) || !longitude.equals(facility.getLongitude()))) {
				facility.setCoordinates(latitude, longitude, "manual", "Operator-entered coordinates");
			}
			facility.setSourceName(cell(directoryModel, row, 11));
			directoryModel.setValueAt(facility.copy(), row, 12);
			facilities.add(facility);
		}
		directory.setFacilities(facilities);
	}

	/**
	 * Saves accepted editable rows as independent snapshots.
	 *
	 * @param plan canine plan.
	 */
	private void saveSelection(CanineMedicalPlan plan) {
		List<VetFacility> facilities = new ArrayList<>();
		for (int row = 0; row < selectionModel.getRowCount(); row++) {
			VetFacility facility = record(selectionModel.getValueAt(row, 10));
			facility.setName(cell(selectionModel, row, 0));
			facility.setTown(cell(selectionModel, row, 1));
			String previousAddress = facility.getAddress();
			facility.setAddress(cell(selectionModel, row, 2));
			facility.setContactNumbers(contacts(cell(selectionModel, row, 3)));
			facility.setAvailable24Hours(parseBoolean(cell(selectionModel, row, 4)));
			if (!previousAddress.equals(facility.getAddress())) {
				facility.clearDistance();
			}
			facility.setCapabilities(cell(selectionModel, row, 8));
			selectionModel.setValueAt(facility.copy(), row, 10);
			facilities.add(facility);
		}
		plan.setSelectedFacilities(facilities);
	}

	/**
	 * Builds one editable regional table row.
	 *
	 * @param facility source facility.
	 * @return all display cells plus the source record.
	 */
	private Object[] directoryRow(VetFacility facility) {
		return new Object[] {facility.getId(), facility.getName(), facility.getTown(), facility.getAddress(),
				String.join(";", facility.getContactNumbers()), availability(facility.getAvailable24Hours()),
				facility.getAvailabilityNotes(), facility.getCapabilities(),
				facility.getVerifiedAt() == null ? "" : facility.getVerifiedAt().toString(),
				value(facility.getLatitude()), value(facility.getLongitude()), facility.getSourceName(), facility.copy()};
	}

	/**
	 * Builds one editable accepted-plan table row.
	 *
	 * @param facility source snapshot.
	 * @return display cells plus stable ID and record.
	 */
	private Object[] selectionRow(VetFacility facility) {
		return new Object[] {facility.getName(), facility.getTown(), facility.getAddress(),
				String.join("; ", facility.getContactNumbers()), availability(facility.getAvailable24Hours()),
				distance(facility), duration(facility), facility.getDistanceMethod(), facility.getCapabilities(),
				facility.getId(), facility.copy()};
	}

	/**
	 * Adds a local directory record.
	 */
	private void addFacility() {
		VetFacility facility = new VetFacility();
		loading = true;
		directoryModel.addRow(directoryRow(facility));
		loading = false;
		inputChanged(true);
	}

	/**
	 * Removes selected regional records after confirmation.
	 */
	private void removeDirectoryRows() {
		int[] selected = directoryTable.getSelectedRows();
		if (selected.length == 0) {
			return;
		}
		if (JOptionPane.showConfirmDialog(this,
				"Remove " + selected.length + " record(s) from this incident directory? The source files are unchanged.",
				"Remove regional records", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		for (int index = selected.length - 1; index >= 0; index--) {
			directoryModel.removeRow(directoryTable.convertRowIndexToModel(selected[index]));
		}
	}

	/**
	 * Manually adds selected facilities as plan snapshots, including explicit exceptions.
	 */
	private void addSelectedFacilities() {
		int[] selected = directoryTable.getSelectedRows();
		if (selected.length == 0) {
			return;
		}
		int exceptions = 0;
		for (int viewRow : selected) {
			int row = directoryTable.convertRowIndexToModel(viewRow);
			VetFacility facility = record(directoryModel.getValueAt(row, 12));
			if (!Boolean.TRUE.equals(facility.getAvailable24Hours())) {
				exceptions++;
			}
		}
		if (exceptions > 0 && JOptionPane.showConfirmDialog(this,
				exceptions + " selected record(s) are not confirmed 24-hour facilities. Include with status shown?",
				"Confirm availability exception", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		for (int viewRow : selected) {
			int row = directoryTable.convertRowIndexToModel(viewRow);
			addSnapshot(record(directoryModel.getValueAt(row, 12)));
		}
	}

	/**
	 * Adds one independent plan snapshot unless already present by stable ID.
	 *
	 * @param source source facility.
	 */
	private void addSnapshot(VetFacility source) {
		for (int row = 0; row < selectionModel.getRowCount(); row++) {
			if (source.getId().equals(cell(selectionModel, row, 9))) {
				return;
			}
		}
		selectionModel.addRow(selectionRow(source.copy()));
	}

	/**
	 * Removes selected plan snapshots without touching regional records.
	 */
	private void removeSelectionRows() {
		int[] selected = selectionTable.getSelectedRows();
		for (int index = selected.length - 1; index >= 0; index--) {
			selectionModel.removeRow(selectionTable.convertRowIndexToModel(selected[index]));
		}
	}

	/**
	 * Moves one accepted facility within the shortlist.
	 *
	 * @param direction -1 to move up or 1 to move down.
	 */
	private void moveSelection(int direction) {
		int viewRow = selectionTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int row = selectionTable.convertRowIndexToModel(viewRow);
		int target = row + direction;
		if (target < 0 || target >= selectionModel.getRowCount()) {
			return;
		}
		Object[] values = rowValues(selectionModel, row);
		selectionModel.removeRow(row);
		selectionModel.insertRow(target, values);
		selectionTable.setRowSelectionInterval(target, target);
	}

	/**
	 * Loads local regional files and presents the merge report before changing incident state.
	 */
	private void importFiles() {
		JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileFilter(new FileNameExtensionFilter("Regional veterinarian files (*.json, *.csv)", "json", "csv"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		pushToModel();
		List<Path> files = java.util.Arrays.stream(chooser.getSelectedFiles()).map(java.io.File::toPath).toList();
		VetDirectoryFileService.ImportResult result = fileService.importFiles(files, controller.getData().getVetDirectory());
		String preview = "Imported records: " + result.directory().getFacilities().size()
				+ "\nDuplicates skipped: " + result.duplicateCount()
				+ "\nConflicting IDs retained with new IDs: " + result.conflicts().size()
				+ "\nParse errors: " + result.errors().size();
		if (!result.conflicts().isEmpty()) {
			preview += "\n\nConflicts:\n" + String.join("\n", result.conflicts());
		}
		if (!result.errors().isEmpty()) {
			preview += "\n\nErrors:\n" + String.join("\n", result.errors());
		}
		if (JOptionPane.showConfirmDialog(this, preview, "Preview regional directory merge",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		controller.getData().setVetDirectory(result.directory());
		refreshFromModel();
		controller.markDirty();
	}

	/**
	 * Explicitly exports the current incident directory after overwrite confirmation.
	 */
	private void exportDirectory() {
		pushToModel();
		JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
		chooser.setFileFilter(new FileNameExtensionFilter("JSON regional directory (*.json)", "json"));
		chooser.addChoosableFileFilter(new FileNameExtensionFilter("CSV regional directory (*.csv)", "csv"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		Path target = chooser.getSelectedFile().toPath();
		boolean overwrite = false;
		if (java.nio.file.Files.exists(target)) {
			if (JOptionPane.showConfirmDialog(this, "Replace " + target.getFileName() + "?",
					"Confirm overwrite", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
				return;
			}
			overwrite = true;
		}
		try {
			fileService.save(target, controller.getData().getVetDirectory(), overwrite);
			status.setText("Regional directory explicitly saved to " + target.getFileName());
		} catch (IOException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Directory export failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Geocodes the staging/base address off the EDT and asks the operator to confirm a candidate.
	 */
	private void geocodeBase() {
		pushToModel();
		AppData incident = controller.getData();
		String address = incident.getStagingLocation().getAddress();
		long baseGeneration = incident.getStagingLocation().getGeneration();
		startWorker(new SwingWorker<List<AddressGeocoder.Candidate>, Void>() {
			@Override
			protected List<AddressGeocoder.Candidate> doInBackground() throws Exception {
				return geocoder.geocode(address);
			}

			@Override
			protected void done() {
				try {
					List<AddressGeocoder.Candidate> candidates = get();
					if (!isCurrent(incident, baseGeneration) || !isActive(this)) {
						return;
					}
					AddressGeocoder.Candidate candidate = confirmCandidate(candidates);
					if (candidate != null) {
						incident.getStagingLocation().setResolution(candidate.label(), candidate.point().latitude(),
								candidate.point().longitude(), "openrouteservice-geocoder",
								"Operator-confirmed candidate; confidence " + candidate.confidence(), Instant.now());
						refreshFromModel();
						controller.markDirty();
						status.setText("Confirmed base coordinates from ORS geocoding.");
					}
				} catch (CancellationException exception) {
					status.setText("Geocoding request cancelled.");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException exception) {
					showProviderError(exception.getCause());
				} finally {
					finishWorker(this);
				}
			}
		});
	}

	/**
	 * Geocodes a selected facility's address off the EDT.
	 */
	private void geocodeSelectedVet() {
		int viewRow = directoryTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		pushToModel();
		AppData incident = controller.getData();
		int row = directoryTable.convertRowIndexToModel(viewRow);
		VetFacility facility = record(directoryModel.getValueAt(row, 12));
		String id = facility.getId();
		String address = facility.getAddress();
		long baseGeneration = incident.getStagingLocation().getGeneration();
		startWorker(new SwingWorker<List<AddressGeocoder.Candidate>, Void>() {
			@Override
			protected List<AddressGeocoder.Candidate> doInBackground() throws Exception {
				return geocoder.geocode(address);
			}

			@Override
			protected void done() {
				try {
					List<AddressGeocoder.Candidate> candidates = get();
					if (!isCurrent(incident, baseGeneration) || !isActive(this)) {
						return;
					}
					AddressGeocoder.Candidate candidate = confirmCandidate(candidates);
					if (candidate != null) {
						VetFacility current = findFacility(incident, id);
						if (current != null && current.getAddress().equals(address)) {
							current.setCoordinates(candidate.point().latitude(), candidate.point().longitude(),
									"openrouteservice-geocoder", "Operator-confirmed: " + candidate.label());
							refreshFromModel();
							controller.markDirty();
						}
					}
				} catch (CancellationException exception) {
					status.setText("Geocoding request cancelled.");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException exception) {
					showProviderError(exception.getCause());
				} finally {
					finishWorker(this);
				}
			}
		});
	}

	/**
	 * Requests all eligible road routes without straight-line prefiltering.
	 */
	private void calculateRoutes() {
		pushToModel();
		AppData incident = controller.getData();
		StagingLocation base = copyStaging(incident.getStagingLocation());
		long baseGeneration = base.getGeneration();
		VetDirectory snapshot = copyDirectory(incident.getVetDirectory());
		boolean routeUnknown = includeUnknown.isSelected();
		int limit = integer(shortlistLimit.getText(), VetShortlistSelector.DEFAULT_LIMIT);
		startWorker(new SwingWorker<RouteWorkResult, Void>() {
			@Override
			protected RouteWorkResult doInBackground() throws Exception {
				VetRoutingService routing = new VetRoutingService(routeProvider);
				VetRoutingService.CalculationReport report = routing.calculate(base, snapshot, routeUnknown, false);
				return new RouteWorkResult(snapshot, report, limit);
			}

			@Override
			protected void done() {
				try {
					RouteWorkResult result = get();
					if (!isCurrent(incident, baseGeneration) || !isActive(this)) {
						status.setText("Discarded stale route results; incident/base/directory changed.");
						return;
					}
					mergeRouteResults(incident, result.directory(), baseGeneration);
					refreshFromModel();
					controller.markDirty();
					status.setText("Road routes calculated: " + result.report().calculated() + "; cached: "
							+ result.report().cached() + "; skipped by availability policy: "
							+ result.report().skipped() + ". " + String.join(" ", result.report().warnings()));
				} catch (CancellationException exception) {
					status.setText("Route calculation cancelled.");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException exception) {
					showProviderError(exception.getCause());
				} finally {
					finishWorker(this);
				}
			}
		});
	}

	/**
	 * Shows a proposal and replaces the existing accepted snapshots only after confirmation.
	 */
	private void previewSelection() {
		pushToModel();
		AppData data = controller.getData();
		List<VetFacility> proposal = shortlistSelector.propose(data.getVetDirectory().getFacilities(),
				integer(shortlistLimit.getText(), VetShortlistSelector.DEFAULT_LIMIT),
				data.getStagingLocation().getGeneration());
		if (proposal.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"No current ORS driving results for confirmed 24-hour facilities. Resolve locations and calculate routes first.",
					"No route proposal", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		DefaultTableModel preview = new DefaultTableModel(
				new String[] {"Name", "Town", "Address", "24 Hours", "Road distance (m)", "Drive duration (s)"}, 0);
		for (VetFacility facility : proposal) {
			preview.addRow(new Object[] {facility.getName(), facility.getTown(), facility.getAddress(),
					availability(facility.getAvailable24Hours()), distance(facility), duration(facility)});
		}
		if (JOptionPane.showConfirmDialog(this, new JScrollPane(new JTable(preview)),
				"Review nearest confirmed 24-hour road routes before replacing the plan",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		selectionModel.setRowCount(0);
		for (VetFacility facility : proposal) {
			selectionModel.addRow(selectionRow(facility));
		}
		pushToModel();
		controller.markDirty();
	}

	/**
	 * Saves validated manually entered base coordinates.
	 */
	private void setManualBaseCoordinates() {
		pushToModel();
		Double latitude = coordinate(stagingLatitude.getText(), -90, 90);
		Double longitude = coordinate(stagingLongitude.getText(), -180, 180);
		if (latitude == null || longitude == null) {
			status.setText("Enter valid finite latitude and longitude values.");
			return;
		}
		controller.getData().getStagingLocation().setResolution(stagingAddress.getText(), latitude, longitude,
				"manual", "Operator-entered coordinates", Instant.now());
		refreshFromModel();
		controller.markDirty();
	}

	/**
	 * Requests the operator to select a geocoding candidate.
	 *
	 * @param candidates ordered geocoding candidates.
	 * @return selected candidate, or null when no match/cancel.
	 */
	private AddressGeocoder.Candidate confirmCandidate(List<AddressGeocoder.Candidate> candidates) {
		if (candidates.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No address candidates were returned.", "Geocoding",
					JOptionPane.INFORMATION_MESSAGE);
			return null;
		}
		Object selected = JOptionPane.showInputDialog(this, "Confirm the resolved location:",
				"Choose geocoding result", JOptionPane.QUESTION_MESSAGE, null,
				candidates.toArray(), candidates.get(0));
		return selected instanceof AddressGeocoder.Candidate candidate ? candidate : null;
	}

	/**
	 * Starts one background worker and shows cancellable progress.
	 *
	 * @param worker background task.
	 */
	private void startWorker(SwingWorker<?, ?> worker) {
		cancelWorker();
		activeWorker = worker;
		progress.setVisible(true);
		status.setText("Working with the selected address/route provider. Cancel with Escape by changing incident.");
		worker.execute();
	}

	/**
	 * Cancels any running address or route request.
	 */
	private void cancelWorker() {
		if (activeWorker != null && !activeWorker.isDone()) {
			activeWorker.cancel(true);
		}
		activeWorker = null;
		progress.setVisible(false);
	}

	/**
	 * Hides the progress indicator after a worker completes.
	 */
	private void finishWorker(SwingWorker<?, ?> worker) {
		if (activeWorker == worker) {
			activeWorker = null;
			progress.setVisible(false);
		}
	}

	/**
	 * Checks that an asynchronous worker remains the active request.
	 *
	 * @param worker request to check.
	 * @return true when the worker has not been superseded or cancelled.
	 */
	private boolean isActive(SwingWorker<?, ?> worker) {
		return activeWorker == worker && !worker.isCancelled();
	}

	/**
	 * Checks that asynchronous results still belong to the same incident and base.
	 *
	 * @param incident captured incident object.
	 * @param baseGeneration captured base generation.
	 * @return true when the response may be applied.
	 */
	private boolean isCurrent(AppData incident, long baseGeneration) {
		return controller.getData() == incident
				&& incident.getStagingLocation().getGeneration() == baseGeneration;
	}

	/**
	 * Copies a directory for background route calculation.
	 *
	 * @param source current incident directory.
	 * @return independent route snapshot.
	 */
	private VetDirectory copyDirectory(VetDirectory source) {
		VetDirectory copy = new VetDirectory();
		for (VetFacility facility : source.getFacilities()) {
			copy.getFacilities().add(facility.copy());
		}
		for (VetDirectory.Source metadata : source.getSources()) {
			VetDirectory.Source item = new VetDirectory.Source();
			item.setName(metadata.getName());
			item.setFormat(metadata.getFormat());
			item.setImportedAt(metadata.getImportedAt());
			copy.getSources().add(item);
		}
		return copy;
	}

	/**
	 * Copies the base fields and current generation into a request snapshot.
	 *
	 * @param source current base.
	 * @return immutable-in-practice location snapshot.
	 */
	private StagingLocation copyStaging(StagingLocation source) {
		return source.copy();
	}

	/**
	 * Copies route results to matching unchanged directory entries only.
	 *
	 * @param incident captured incident.
	 * @param routedDirectory worker result.
	 * @param baseGeneration staging generation.
	 */
	private void mergeRouteResults(AppData incident, VetDirectory routedDirectory, long baseGeneration) {
		for (VetFacility routed : routedDirectory.getFacilities()) {
			VetFacility current = findFacility(incident, routed.getId());
			if (current != null && current.getAddress().equals(routed.getAddress())
					&& routed.hasCurrentDistance(baseGeneration)) {
				current.setDistance(routed.getDistanceMeters(), routed.getDurationSeconds(),
						routed.getDistanceMethod(), routed.getDistanceProvenance(), routed.getCalculatedAt(),
						baseGeneration);
			}
		}
	}

	/**
	 * Finds one regional facility by stable ID.
	 *
	 * @param incident incident document.
	 * @param id stable facility ID.
	 * @return source record or null.
	 */
	private VetFacility findFacility(AppData incident, String id) {
		return incident.getVetDirectory().getFacilities().stream().filter(item -> id.equals(item.getId())).findFirst()
				.orElse(null);
	}

	/**
	 * Displays only sanitized provider errors.
	 *
	 * @param failure worker error.
	 */
	private void showProviderError(Throwable failure) {
		String message = failure instanceof IOException ? failure.getMessage() : "Provider request failed";
		status.setText(message);
		JOptionPane.showMessageDialog(this, message, "Location service", JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Parses a preparation time without clearing the previous model value on invalid input.
	 *
	 * @param plan canine plan.
	 */
	private void setPreparedAt(CanineMedicalPlan plan) {
		if (preparedAt.getText().isBlank()) {
			plan.setPreparedAt(null);
			return;
		}
		try {
			plan.setPreparedAt(LocalDateTime.parse(preparedAt.getText().trim()));
		} catch (RuntimeException exception) {
			status.setText("Preparation date/time must use yyyy-MM-ddTHH:mm.");
		}
	}

	/**
	 * Stops an active cell editor before model replacement.
	 *
	 * @param table table to flush.
	 */
	private void stopEditing(JTable table) {
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}
	}

	/**
	 * Returns one table value as text.
	 *
	 * @param model table model.
	 * @param row row index.
	 * @param column column index.
	 * @return non-null display value.
	 */
	private static String cell(DefaultTableModel model, int row, int column) {
		Object value = model.getValueAt(row, column);
		return value == null ? "" : value.toString();
	}

	/**
	 * Returns a snapshot object from a hidden table column.
	 *
	 * @param value stored object.
	 * @return independent facility.
	 */
	private static VetFacility record(Object value) {
		return value instanceof VetFacility facility ? facility.copy() : new VetFacility();
	}

	/**
	 * Serializes one table row for manual reordering.
	 *
	 * @param model source table model.
	 * @param row row index.
	 * @return row cell values.
	 */
	private static Object[] rowValues(DefaultTableModel model, int row) {
		Object[] values = new Object[model.getColumnCount()];
		for (int column = 0; column < values.length; column++) {
			values[column] = model.getValueAt(row, column);
		}
		return values;
	}

	/**
	 * Parses semicolon-separated contacts.
	 *
	 * @param value contact cell.
	 * @return individual contact values.
	 */
	private static List<String> contacts(String value) {
		return java.util.Arrays.stream(value.split(";")).map(String::trim).filter(item -> !item.isBlank()).toList();
	}

	/**
	 * Parses one optional geographic coordinate.
	 *
	 * @param value source text.
	 * @param minimum geographic minimum.
	 * @param maximum geographic maximum.
	 * @return valid coordinate or null.
	 */
	private static Double coordinate(String value, double minimum, double maximum) {
		try {
			double coordinate = Double.parseDouble(value.trim());
			return Double.isFinite(coordinate) && coordinate >= minimum && coordinate <= maximum ? coordinate : null;
		} catch (RuntimeException exception) {
			return null;
		}
	}

	/**
	 * Parses the explicit three-state availability value.
	 *
	 * @param value table text.
	 * @return nullable availability.
	 */
	private static Boolean parseBoolean(String value) {
		if ("yes".equalsIgnoreCase(value)) {
			return true;
		}
		if ("no".equalsIgnoreCase(value)) {
			return false;
		}
		return null;
	}

	/**
	 * Formats nullable availability without implying unknown is verified.
	 *
	 * @param value availability.
	 * @return Yes, No, or Unknown.
	 */
	private static String availability(Boolean value) {
		return value == null ? UNKNOWN : value ? "Yes" : "No";
	}

	/**
	 * Formats a missing numeric value as blank.
	 *
	 * @param value number.
	 * @return number text.
	 */
	private static String value(Double value) {
		return value == null ? "" : value.toString();
	}

	/**
	 * Returns a road distance or the explicit offline-estimate label.
	 *
	 * @param facility route record.
	 * @return distance text.
	 */
	private static String distance(VetFacility facility) {
		return Double.isFinite(facility.getDistanceMeters())
				? String.format(java.util.Locale.ROOT, "%.0f", facility.getDistanceMeters())
				: "";
	}

	/**
	 * Returns a route duration only when the provider supplied one.
	 *
	 * @param facility route record.
	 * @return duration text.
	 */
	private static String duration(VetFacility facility) {
		return Double.isFinite(facility.getDurationSeconds())
				? String.format(java.util.Locale.ROOT, "%.0f", facility.getDurationSeconds())
				: "";
	}

	/**
	 * Parses a positive closest-N count with the documented default.
	 *
	 * @param value user text.
	 * @param defaultValue default count.
	 * @return positive limit.
	 */
	private static int integer(String value, int defaultValue) {
		try {
			int number = Integer.parseInt(value.trim());
			return number > 0 ? number : defaultValue;
		} catch (RuntimeException exception) {
			return defaultValue;
		}
	}

	/**
	 * One worker result retaining the full copied directory.
	 *
	 * @param directory directory containing calculated routes.
	 * @param report partial route status.
	 * @param limit requested shortlist limit.
	 */
	private record RouteWorkResult(VetDirectory directory, VetRoutingService.CalculationReport report, int limit) {
	}
}
