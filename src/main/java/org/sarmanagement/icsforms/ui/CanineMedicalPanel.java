/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.medical.AddressGeocoder;
import org.sarmanagement.icsforms.medical.DrivingRouteProvider;
import org.sarmanagement.icsforms.medical.OpenRouteServiceDrivingRouter;
import org.sarmanagement.icsforms.medical.OpenRouteServiceGeocoder;
import org.sarmanagement.icsforms.medical.VetDirectoryFileService;
import org.sarmanagement.icsforms.medical.VetRoutingService;
import org.sarmanagement.icsforms.medical.VetShortlistSelector;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CanineMedicalPlan;
import org.sarmanagement.icsforms.model.IncidentLocation;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.StagingLocation;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.model.UtcTimestamp;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
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
	private final JTextField stagingLabel = new JTextField();
	private final JTextField stagingAddress = new JTextField();
	private final JTextField stagingLatitude = new JTextField();
	private final JTextField stagingLongitude = new JTextField();
	private final JComboBox<RoutingBaseChoice> routingBaseLocations = new JComboBox<>();
	private final JTextField shortlistLimit = new JTextField("8", 4);
	private final JButton geocodeBaseButton = new JButton("Look up address…");
	private final JButton setBaseCoordinatesButton = new JButton("Set coordinates");
	private final JButton calculateRoutesButton = new JButton("3. Calculate routes");
	private final JButton previewSelectionButton = new JButton("4. Review closest 24-hour vets…");
	private final JButton geocodeVetButton = new JButton("2. Look up selected address(es)…");
	private final JButton includeSelectedButton = new JButton("Include selected directory rows");
	private final DefaultTableModel directoryModel = new DefaultTableModel(new String[] {
			"ID", "Name", "Town", "Address", "Contacts (; separated)", "24 Hours", "Availability notes",
			"Capabilities / notes", "Verified at", "Latitude", "Longitude", "Source", "State", "ZIP", "Fax",
			"Record", "Road distance (mi)", "Drive time (min)"
	}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return column > 0 && column < 15;
		}
	};
	private final DefaultTableModel selectionModel = new DefaultTableModel(new String[] {
			"Name", "Town", "State", "ZIP", "Address", "Contact numbers", "Fax", "24 Hours", "Road distance (mi)",
			"Drive time (min)", "Method", "Capabilities / notes", "ID", "Record"
	}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return column <= 7 || column == 11;
		}
	};
	private final DefaultTableModel transportModel = new DefaultTableModel(new String[] {
			"Service", "Location", "Contact numbers", "Frequency", "Air or ground", "Level of service"
	}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return true;
		}
	};
	private final JTable directoryTable = new JTable(directoryModel);
	private final JTable selectionTable = new JTable(selectionModel);
	private final JTable transportTable = new JTable(transportModel);
	private final JPopupMenu directoryPopupMenu = new JPopupMenu();
	private final JTextField poisonContact = new JTextField();
	private final JTextArea aidStations = new JTextArea(2, 70);
	private final JTextArea transportServices = new JTextArea(2, 40);
	private final JTextField onsiteVet = new JTextField();
	private final JList<PersonChoice> veterinaryPersonnel = new JList<>();
	private final JTextArea capabilityNotes = new JTextArea(3, 50);
	private final JTextArea procedures = new JTextArea(2, 70);
	private final JCheckBox aviationUsed = new JCheckBox("Aviation assets are used; coordinate with Air Operations");
	private final JTextField preparedBy = new JTextField();
	private final JTextField preparedAt = new JTextField();
	private final JTextField safetyOfficer = new JTextField();
	private final JComboBox<String> reviewStatus = new JComboBox<>(
			new String[] {"Not reviewed", "Reviewed", "Approved"});
	private final JLabel status = new JLabel(
			"OpenRouteService is the default provider. Configure its API key in Configuration > Medical Routing.");
	private final JProgressBar progress = new JProgressBar();
	private final JButton cancelRequest = new JButton("Cancel request");
	private final JTabbedPane workspace = new JTabbedPane();
	private final JTabbedPane planSections = new JTabbedPane();
	private Consumer<String> exportRequest = ignored -> { };
	private boolean loading;
	private boolean flushing;
	private final JButton bringForward = new JButton("Bring Forward…");
	private AppData lastLoadedIncident;
	private SwingWorker<?, ?> activeWorker;

	/**
	 * Selectable view of an incident-owned address for canine routing.
	 */
	private static final class RoutingBaseChoice {
		private final String locationId;
		private final String label;
		private final String locationName;
		private final String address;

		/**
		 * Creates one selectable shared incident location.
		 *
		 * @param locationId location identifier, blank for the manual base.
		 * @param label visible choice label.
		 * @param locationName name copied into the routing origin.
		 * @param address location address.
		 */
		private RoutingBaseChoice(String locationId, String label, String locationName, String address) {
			this.locationId = locationId;
			this.label = label;
			this.locationName = locationName;
			this.address = address;
		}

		/**
		 * Returns the stable incident-location identifier.
		 *
		 * @return location identifier or blank for manual entry.
		 */
		private String locationId() {
			return locationId;
		}

		/**
		 * Returns the shared location label.
		 *
		 * @return visible location name.
		 */
		private String label() {
			return label;
		}

		/**
		 * Returns the incident location name used on the routing origin.
		 *
		 * @return location name.
		 */
		private String locationName() {
			return locationName;
		}

		/**
		 * Returns the shared location address.
		 *
		 * @return location address.
		 */
		private String address() {
			return address;
		}

		/** {@inheritDoc} */
		@Override
		public String toString() {
			return label;
		}
	}

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
		RadioChannelPicker.install(transportTable, 3, controller);
		this.defaultDirectory = defaultDirectory;
		this.geocoder = geocoder;
		this.routeProvider = routeProvider;
		JPanel heading = new JPanel(new BorderLayout());
		heading.add(buildTop(), BorderLayout.NORTH);
		bringForward.addActionListener(event -> BringForwardDialog.showFromEditor(this, controller,
				this::pushToModel, this::refreshFromModel));
		heading.add(bringForward, BorderLayout.CENTER);
		heading.add(new PlanReviewStrip(controller,
				org.sarmanagement.icsforms.model.PlanReviewArea.CANINE_MEDICAL, this::pushToModel),
				BorderLayout.SOUTH);
		add(heading, BorderLayout.NORTH);
		workspace.addTab("Canine ICS 206CANINE plan", buildPlanFields());
		workspace.addTab("Veterinary facilities and routes", buildDirectoryArea());
		add(workspace, BorderLayout.CENTER);
		progress.setIndeterminate(true);
		progress.setVisible(false);
		JPanel footer = new JPanel(new BorderLayout(6, 2));
		footer.add(progress, BorderLayout.WEST);
		footer.add(status, BorderLayout.CENTER);
		cancelRequest.setVisible(false);
		cancelRequest.addActionListener(event -> cancelWorker());
		footer.add(cancelRequest, BorderLayout.EAST);
		add(footer, BorderLayout.SOUTH);
		configureListeners();
		refreshFromModel();
	}

	/**
	 * Returns the plan and veterinary subtabs for focused UI tests.
	 *
	 * @return canine workspace tabs.
	 */
	JTabbedPane workspaceTabs() {
		return workspace;
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
		bringForward.setEnabled(controller.canReusePlans());
		loading = true;
		try {
			AppData data = controller.getData();
			if (lastLoadedIncident != null && lastLoadedIncident != data) {
				cancelWorker();
			}
			lastLoadedIncident = data;
			StagingLocation staging = data.getStagingLocation();
			CanineMedicalPlan plan = data.getCanineMedicalPlan();
			loadRoutingBaseChoices(data);
			RoutingBaseChoice selectedBase = (RoutingBaseChoice) routingBaseLocations.getSelectedItem();
			if (selectedBase != null && !selectedBase.locationId().isBlank()) {
				staging.setLabel(selectedBase.locationName());
				staging.setAddress(selectedBase.address());
			}
			stagingLabel.setText(staging.getLabel());
			stagingAddress.setText(staging.getAddress());
			stagingLatitude.setText(staging.getLatitude() == null ? "" : staging.getLatitude().toString());
			stagingLongitude.setText(staging.getLongitude() == null ? "" : staging.getLongitude().toString());
			aidStations.setText(plan.getAidStationInformation());
			transportServices.setText(plan.getTransportServices());
			loadTransport(plan.getTransportServiceEntries());
			poisonContact.setText(plan.getPoisonControlContact());
			onsiteVet.setText(plan.getOnsiteVeterinarian());
			loadVeterinaryPersonnel(data, plan);
			capabilityNotes.setText(plan.getVeterinaryCapabilityNotes());
			procedures.setText(plan.getEmergencyProcedures());
			aviationUsed.setSelected(plan.isAviationAssetsUsed());
			preparedBy.setText(MedicalRoleSupport.medicalUnitLeaderName(data));
			preparedAt.setText(UiSupport.formatIsoDateTime(plan.getPreparedAt()));
			safetyOfficer.setText(MedicalRoleSupport.safetyOfficerName(data));
			reviewStatus.setSelectedItem(plan.getReviewStatus());
			loadDirectory(data.getVetDirectory());
			loadSelection(plan.getSelectedFacilities());
		} finally {
			loading = false;
		}
		refreshWorkflowControls();
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
			stopEditing(transportTable);
			AppData data = controller.getData();
			StagingLocation staging = data.getStagingLocation();
			String previousBaseAddress = staging.getAddress();
			staging.setLabel(stagingLabel.getText());
			staging.setAddress(stagingAddress.getText());
			String selectedLocationId = data.getCanineRoutingBaseLocationId();
			if (!selectedLocationId.isBlank() && data.getIncidentLocations().stream().noneMatch(location ->
					location != null && selectedLocationId.equals(location.getId())
							&& location.getAddress().equals(staging.getAddress()))) {
				data.setCanineRoutingBaseLocationId("");
			}
			Double latitude = coordinate(stagingLatitude.getText(), -90, 90);
			Double longitude = coordinate(stagingLongitude.getText(), -180, 180);
			if (!previousBaseAddress.equals(staging.getAddress())) {
				stagingLatitude.setText("");
				stagingLongitude.setText("");
			} else if (latitude != null && longitude != null
					&& (!latitude.equals(staging.getLatitude()) || !longitude.equals(staging.getLongitude()))) {
				staging.setResolution(stagingAddress.getText(), latitude, longitude, "manual",
						"Operator-entered coordinates", UtcTimestamp.nowInstant());
			}
			data.getCanineMedicalPlan().setAidStationInformation(aidStations.getText());
			data.getCanineMedicalPlan().setTransportServices(transportServices.getText());
			saveTransport(data.getCanineMedicalPlan());
			data.getCanineMedicalPlan().setPoisonControlContact(poisonContact.getText());
			data.getCanineMedicalPlan().setOnsiteVeterinarian(onsiteVet.getText());
			data.getCanineMedicalPlan().setVeterinaryPersonnelResourceIds(
					veterinaryPersonnel.getSelectedValuesList().stream().map(PersonChoice::resourceId).toList());
			data.getCanineMedicalPlan().setVeterinaryCapabilityNotes(capabilityNotes.getText());
			data.getCanineMedicalPlan().setEmergencyProcedures(procedures.getText());
			data.getCanineMedicalPlan().setAviationAssetsUsed(aviationUsed.isSelected());
			setPreparedAt(data.getCanineMedicalPlan());
			data.getCanineMedicalPlan().setReviewStatus(String.valueOf(reviewStatus.getSelectedItem()));
			saveDirectory(data.getVetDirectory());
			saveSelection(data.getCanineMedicalPlan());
		} finally {
			flushing = false;
		}
	}

	/**
	 * Builds the canine export toolbar and IAP inclusion note.
	 *
	 * @return top control panel.
	 */
	private JPanel buildTop() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton export = new JButton("Export ICS 206CANINE PDF");
		export.addActionListener(event -> {
			pushToModel();
			exportRequest.accept("ICS 206CANINE");
		});
		bar.add(export);
		bar.add(new JLabel("IAP inclusion is selected on ICS 202."));
		return bar;
	}

	/**
	 * Builds compact shared staging/base and route controls above the regional directory.
	 *
	 * @return staging and routing controls.
	 */
	private JPanel buildBaseLocationPanel() {
		JButton load = new JButton("1. Load regional JSON/CSV…");
		load.addActionListener(event -> importFiles());
		JButton save = new JButton("Save directory JSON/CSV…");
		save.addActionListener(event -> exportDirectory());
		JPanel base = new JPanel(new GridLayout(0, 6, 4, 3));
		base.setBorder(BorderFactory.createTitledBorder("Staging / base location"));
		base.add(new JLabel("Incident location"));
		base.add(routingBaseLocations);
		base.add(new JLabel(""));
		base.add(new JLabel(""));
		base.add(new JLabel(""));
		base.add(new JLabel(""));
		base.add(new JLabel("Label"));
		base.add(stagingLabel);
		base.add(new JLabel("Full address"));
		base.add(stagingAddress);
		geocodeBaseButton.setText("2. Look up base address…");
		geocodeBaseButton.addActionListener(event -> geocodeBase());
		base.add(geocodeBaseButton);
		setBaseCoordinatesButton.setText("2. Set coordinates");
		setBaseCoordinatesButton.addActionListener(event -> setManualBaseCoordinates());
		base.add(setBaseCoordinatesButton);
		base.add(new JLabel("Latitude"));
		base.add(stagingLatitude);
		base.add(new JLabel("Longitude"));
		base.add(stagingLongitude);
		base.add(new JLabel("Closest N"));
		base.add(shortlistLimit);
		calculateRoutesButton.addActionListener(event -> calculateRoutes());
		base.add(calculateRoutesButton);
		previewSelectionButton.addActionListener(event -> previewSelection());
		base.add(previewSelectionButton);
		base.add(load);
		base.add(save);
		JPanel root = new JPanel(new BorderLayout(4, 4));
		root.add(base, BorderLayout.CENTER);
		JPanel note = new JPanel(new GridLayout(0, 1, 2, 2));
		note.add(new JLabel("<html><b>Workflow:</b> 1. Load a regional JSON/CSV directory. "
				+ "2. Resolve the base coordinates and select facilities without coordinates to look up/confirm "
				+ "each address. 3. Calculate road routes for facilities with coordinates. "
				+ "4. Review the closest confirmed 24-hour facilities sorted by road distance and add the "
				+ "selected results to the plan. Availability does not limit route calculations.</html>"));
		note.add(createAttribution());
		root.add(note, BorderLayout.SOUTH);
		return root;
	}

	/**
	 * Rebuilds the routing-origin choices from incident-owned location snapshots.
	 *
	 * @param data active incident.
	 */
	private void loadRoutingBaseChoices(AppData data) {
		String selectedId = data.getCanineRoutingBaseLocationId();
		routingBaseLocations.removeAllItems();
		routingBaseLocations.addItem(new RoutingBaseChoice("", "Manual / existing staging base", "", ""));
		int selectedIndex = 0;
		for (IncidentLocation location : data.getIncidentLocations()) {
			if (location == null) {
				continue;
			}
			String name = location.getName().isBlank() ? location.getType() : location.getName();
			String display = location.getType().isBlank() ? name : location.getType() + " — " + name;
			routingBaseLocations.addItem(new RoutingBaseChoice(location.getId(), display, name,
					location.getAddress()));
			if (location.getId().equals(selectedId)) {
				selectedIndex = routingBaseLocations.getItemCount() - 1;
			}
		}
		routingBaseLocations.setSelectedIndex(selectedIndex);
	}

	/**
	 * Copies a selected shared incident location into the routing origin.
	 */
	private void applyRoutingBaseChoice() {
		if (loading || !(routingBaseLocations.getSelectedItem() instanceof RoutingBaseChoice choice)) {
			return;
		}
		AppData data = controller.getData();
		data.setCanineRoutingBaseLocationId(choice.locationId());
		if (!choice.locationId().isBlank()) {
			loading = true;
			try {
				StagingLocation staging = data.getStagingLocation();
				staging.clearResolution();
				staging.setLabel(choice.locationName());
				staging.setAddress(choice.address());
				stagingLabel.setText(choice.locationName());
				stagingAddress.setText(choice.address());
				stagingLatitude.setText("");
				stagingLongitude.setText("");
			} finally {
				loading = false;
			}
		}
		inputChanged(true);
	}

	/**
	 * Finds a facility's current model row after table sorting or editor commits.
	 *
	 * @param facilityId stable facility identifier.
	 * @return model row, or -1 if removed.
	 */
	private int directoryModelRow(String facilityId) {
		for (int row = 0; row < directoryModel.getRowCount(); row++) {
			if (facilityId.equals(record(directoryModel.getValueAt(row, 15)).getId())) {
				return row;
			}
		}
		return -1;
	}

	/**
	 * Builds the directory and accepted-plan tables without truncating source data.
	 *
	 * @return table area.
	 */
	private JPanel buildDirectoryArea() {
		configureTables();
		JPanel directory = new JPanel(new BorderLayout(4, 4));
		directory.setBorder(BorderFactory.createTitledBorder(
				"Regional veterinary directory — 24-hour availability is explicit and editable"));
		directory.add(new JScrollPane(directoryTable), BorderLayout.CENTER);
		JPanel directoryControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton add = new JButton("Add facility");
		add.addActionListener(event -> addFacility());
		JButton remove = new JButton("Remove selected");
		remove.addActionListener(event -> removeDirectoryRows());
		directoryControls.add(add);
		directoryControls.add(remove);
		geocodeVetButton.addActionListener(event -> geocodeSelectedVet());
		directoryControls.add(geocodeVetButton);
		directory.add(directoryControls, BorderLayout.SOUTH);

		includeSelectedButton.addActionListener(event -> addSelectedFacilities());
		directoryControls.add(includeSelectedButton);
		JPanel area = new JPanel(new BorderLayout(4, 4));
		area.add(buildBaseLocationPanel(), BorderLayout.NORTH);
		area.add(directory, BorderLayout.CENTER);
		return area;
	}

	/**
	 * Builds editable canine procedures and acknowledgment fields.
	 *
	 * @return canine plan details.
	 */
	private JPanel buildPlanFields() {
		aidStations.setLineWrap(true);
		aidStations.setWrapStyleWord(true);
		capabilityNotes.setLineWrap(true);
		capabilityNotes.setWrapStyleWord(true);
		procedures.setLineWrap(true);
		procedures.setWrapStyleWord(true);
		planSections.addTab("Aid Stations & Special Procedures", buildAidAndProceduresArea());
		planSections.addTab("Transportation", buildTransportArea());
		planSections.addTab("Veterinary Hospitals", buildVeterinaryHospitalArea());
		JPanel review = new JPanel(new GridLayout(2, 4, 4, 2));
		review.setBorder(BorderFactory.createTitledBorder("Preparation / Safety review"));
		review.add(new JLabel("Medical Unit Leader"));
		review.add(preparedBy);
		review.add(new JLabel("Prepared at"));
		review.add(preparedAt);
		review.add(new JLabel("Safety Officer"));
		review.add(safetyOfficer);
		review.add(new JLabel("Review status"));
		review.add(reviewStatus);
		JPanel root = new JPanel(new BorderLayout());
		root.add(planSections, BorderLayout.CENTER);
		root.add(review, BorderLayout.SOUTH);
		return root;
	}

	/**
	 * Builds a compact, wide aid-station and special-procedures editor with unused space below.
	 *
	 * @return combined aid-station and special-procedures section.
	 */
	private JPanel buildAidAndProceduresArea() {
		JPanel fields = new JPanel(new java.awt.GridBagLayout());
		fields.setBorder(BorderFactory.createTitledBorder("Medical aid stations and special procedures"));
		java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
		constraints.weightx = 1;
		constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		constraints.anchor = java.awt.GridBagConstraints.WEST;
		constraints.insets = new java.awt.Insets(2, 4, 2, 4);

		constraints.gridy = 0;
		fields.add(new JLabel("Medical aid stations"), constraints);
		constraints.gridy = 1;
		fields.add(new JScrollPane(aidStations), constraints);
		constraints.gridy = 2;
		fields.add(new JLabel("Poison-control contact"), constraints);
		constraints.gridy = 3;
		fields.add(poisonContact, constraints);
		constraints.gridy = 4;
		fields.add(new JLabel("Special medical emergency procedures"), constraints);
		constraints.gridy = 5;
		fields.add(new JScrollPane(procedures), constraints);
		constraints.gridy = 6;
		constraints.weighty = 1;
		constraints.fill = java.awt.GridBagConstraints.BOTH;
		fields.add(new JPanel(), constraints);
		return fields;
	}

	/**
	 * Builds structured canine transportation rows with optional notes and shared ambulance import.
	 *
	 * @return transportation editor.
	 */
	private JPanel buildTransportArea() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setBorder(BorderFactory.createTitledBorder("Canine transportation services"));
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton add = new JButton("Add service");
		add.addActionListener(event -> transportModel.addRow(new Object[] {"", "", "", "", "", ""}));
		JButton remove = new JButton("Remove selected");
		remove.addActionListener(event -> removeTransportRows());
		JButton importCsv = new JButton("Import transport CSV…");
		importCsv.addActionListener(event -> importTransportCsv());
		JButton exportCsv = new JButton("Export transport CSV…");
		exportCsv.addActionListener(event -> exportTransportCsv());
		JButton copyAmbulance = new JButton("Copy ambulance services from Medical Plan");
		copyAmbulance.addActionListener(event -> copyMedicalAmbulanceServices());
		for (JButton button : List.of(add, remove, importCsv, exportCsv, copyAmbulance)) {
			controls.add(button);
		}
		panel.add(controls, BorderLayout.NORTH);
		panel.add(new JScrollPane(transportTable), BorderLayout.CENTER);
		JPanel notes = new JPanel(new BorderLayout(4, 4));
		transportServices.setLineWrap(true);
		transportServices.setWrapStyleWord(true);
		notes.setBorder(BorderFactory.createTitledBorder("Additional canine transport notes"));
		notes.add(new JScrollPane(transportServices), BorderLayout.CENTER);
		JPanel lower = new JPanel(new BorderLayout(4, 4));
		lower.add(notes, BorderLayout.CENTER);
		JPanel aviationPanel = new JPanel(new BorderLayout(4, 4));
		aviationPanel.setBorder(BorderFactory.createTitledBorder("Aviation coordination"));
		aviationUsed.setText("Aviation assets are used for rescue; coordinate with Air Operations Branch");
		aviationPanel.add(aviationUsed, BorderLayout.CENTER);
		lower.add(aviationPanel, BorderLayout.SOUTH);
		panel.add(lower, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Builds the accepted veterinary hospital snapshot table and its plan controls.
	 *
	 * @return veterinary hospital section.
	 */
	private JPanel buildVeterinaryHospitalArea() {
		JPanel hospitals = new JPanel(new BorderLayout(4, 4));
		JPanel details = new JPanel(new GridLayout(0, 2, 6, 4));
		details.add(new JLabel("Onsite veterinarian"));
		details.add(onsiteVet);
		details.add(new JLabel("Linked veterinary personnel (recorded status/location only)"));
		veterinaryPersonnel.setVisibleRowCount(4);
		veterinaryPersonnel.getAccessibleContext().setAccessibleName("Linked veterinary personnel T-cards");
		details.add(new JScrollPane(veterinaryPersonnel));
		details.add(new JLabel("Veterinary capabilities / notes"));
		details.add(new JScrollPane(capabilityNotes));
		hospitals.add(details, BorderLayout.NORTH);
		JPanel snapshots = new JPanel(new BorderLayout(4, 4));
		snapshots.setBorder(BorderFactory.createTitledBorder(
				"Accepted veterinary hospital snapshots (directory records remain intact)"));
		snapshots.add(new JScrollPane(selectionTable), BorderLayout.CENTER);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton excludeSelected = new JButton("Exclude selected");
		excludeSelected.addActionListener(event -> removeSelectionRows());
		JButton up = new JButton("Move up");
		up.addActionListener(event -> moveSelection(-1));
		JButton down = new JButton("Move down");
		down.addActionListener(event -> moveSelection(1));
		controls.add(excludeSelected);
		controls.add(up);
		controls.add(down);
		snapshots.add(controls, BorderLayout.SOUTH);
		hospitals.add(snapshots, BorderLayout.CENTER);
		return hospitals;
	}

	/**
	 * Loads all personnel cards and selects the plan's stable veterinary links.
	 *
	 * @param data active incident.
	 * @param plan canine medical plan.
	 */
	private void loadVeterinaryPersonnel(AppData data, CanineMedicalPlan plan) {
		List<PersonChoice> people = data.getTCards().stream()
				.filter(card -> card.getCardType() == null
						|| card.getCardType() == org.sarmanagement.icsforms.model.TCardType.PERSONNEL)
				.map(card -> new PersonChoice(card.getResourceId(), card.getPersonName(), card.getStatus(),
						card.getLocation()))
				.toList();
		veterinaryPersonnel.setListData(people.toArray(PersonChoice[]::new));
		int[] selected = java.util.stream.IntStream.range(0, people.size())
				.filter(index -> plan.getVeterinaryPersonnelResourceIds().contains(people.get(index).resourceId()))
				.toArray();
		veterinaryPersonnel.setSelectedIndices(selected);
	}

	/**
	 * Personnel T-card choice preserving status and location semantics.
	 *
	 * @param resourceId stable resource identifier.
	 * @param name recorded person name.
	 * @param status recorded status.
	 * @param location recorded location.
	 */
	private record PersonChoice(String resourceId, String name, String status, String location) {
		/** @return recorded card details without availability inference. */
		@Override
		public String toString() {
			return name + " — recorded status: " + status + "; location: " + location;
		}
	}

	/**
	 * Creates a titled multiline editor used by a canine medical-plan section.
	 *
	 * @param title section label.
	 * @param area editable text area.
	 * @return titled text editor.
	 */
	private JPanel labeledTextArea(String title, JTextArea area) {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setBorder(BorderFactory.createTitledBorder(title));
		panel.add(new JScrollPane(area), BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Returns the parallel canine medical-plan section tabs for focused tests.
	 *
	 * @return canine medical-plan sections.
	 */
	JTabbedPane planSectionTabs() {
		return planSections;
	}

	/**
	 * Configures table columns and hides only internal object references.
	 */
	private void configureTables() {
		directoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		selectionTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		directoryTable.setAutoCreateRowSorter(true);
		directoryTable.getColumnModel().getColumn(0).setMinWidth(0);
		directoryTable.getColumnModel().getColumn(0).setMaxWidth(0);
		directoryTable.getColumnModel().getColumn(0).setWidth(0);
		directoryTable.getColumnModel().getColumn(15).setMinWidth(0);
		directoryTable.getColumnModel().getColumn(15).setMaxWidth(0);
		directoryTable.getColumnModel().getColumn(15).setWidth(0);
		if (directoryTable.getRowSorter() instanceof javax.swing.table.TableRowSorter<?> sorter) {
			sorter.setComparator(16, (first, second) ->
					Double.compare(sortValue(first), sortValue(second)));
			sorter.setComparator(17, (first, second) ->
					Double.compare(sortValue(first), sortValue(second)));
		}
		selectionTable.getColumnModel().getColumn(12).setMinWidth(0);
		selectionTable.getColumnModel().getColumn(12).setMaxWidth(0);
		selectionTable.getColumnModel().getColumn(12).setWidth(0);
		selectionTable.getColumnModel().getColumn(13).setMinWidth(0);
		selectionTable.getColumnModel().getColumn(13).setMaxWidth(0);
		selectionTable.getColumnModel().getColumn(13).setWidth(0);
		directoryTable.getColumnModel().getColumn(5).setCellEditor(
				new DefaultCellEditor(new JComboBox<>(new String[] {"Yes", "No", UNKNOWN})));
		selectionTable.getColumnModel().getColumn(7).setCellEditor(
				new DefaultCellEditor(new JComboBox<>(new String[] {"Yes", "No", UNKNOWN})));
		installDirectoryContextMenu();
	}

	/**
	 * Installs row-specific lookup, edit, and plan actions on the regional directory.
	 */
	private void installDirectoryContextMenu() {
		JMenuItem lookup = new JMenuItem("Lookup coordinates from address…");
		lookup.addActionListener(event -> geocodeSelectedVet());
		directoryPopupMenu.add(lookup);
		JMenuItem edit = new JMenuItem("Edit details…");
		edit.addActionListener(event -> {
			int row = directoryTable.getSelectedRow();
			if (row >= 0) {
				editFacilityDetails(row);
			}
		});
		directoryPopupMenu.add(edit);
		JMenuItem include = new JMenuItem("Include in plan");
		include.addActionListener(event -> addSelectedFacilities());
		directoryPopupMenu.add(include);
		directoryTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				selectContextRow(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				selectContextRow(event);
			}
		});
	}

	/**
	 * Selects the context-clicked row before displaying its action menu.
	 *
	 * @param event mouse event.
	 */
	private void selectContextRow(MouseEvent event) {
		if (!event.isPopupTrigger()) {
			return;
		}
		int viewRow = directoryTable.rowAtPoint(event.getPoint());
		if (viewRow < 0) {
			return;
		}
		directoryTable.setRowSelectionInterval(viewRow, viewRow);
		directoryPopupMenu.show(directoryTable, event.getX(), event.getY());
	}

	/**
	 * Attaches change listeners and cancels stale network work when data changes.
	 */
	private void configureListeners() {
		aviationUsed.addActionListener(event -> inputChanged(false));
		reviewStatus.addActionListener(event -> inputChanged(false));
		routingBaseLocations.addActionListener(event -> applyRoutingBaseChoice());
		for (JTextField field : List.of(stagingLabel, stagingAddress, stagingLatitude, stagingLongitude,
				shortlistLimit, poisonContact, onsiteVet, preparedAt, safetyOfficer)) {
			watch(field, field == stagingAddress || field == stagingLatitude || field == stagingLongitude);
		}
		watchMedicalUnitLeader();
		UiSupport.installNameAutocomplete(preparedBy, controller::getPersonnelNames,
				name -> MedicalRoleSupport.setMedicalUnitLeaderName(controller.getData(), name));
		UiSupport.installNameAutocomplete(safetyOfficer, controller::getPersonnelNames,
				name -> MedicalRoleSupport.setSafetyOfficerName(controller.getData(), name));
		watch(capabilityNotes, false);
		watch(procedures, false);
		watch(aidStations, false);
		watch(transportServices, false);
		directoryModel.addTableModelListener(event -> inputChanged(true));
		selectionModel.addTableModelListener(event -> inputChanged(false));
		transportModel.addTableModelListener(event -> inputChanged(false));
		directoryTable.getSelectionModel().addListSelectionListener(event -> refreshWorkflowControls());
	}

	/**
	 * Binds typed edits and explicit clearing directly to the shared role assignment.
	 */
	private void watchMedicalUnitLeader() {
		preparedBy.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				medicalUnitLeaderChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				medicalUnitLeaderChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				medicalUnitLeaderChanged();
			}
		});
	}

	/**
	 * Saves the live Medical Unit Leader edit before any panel state flush occurs.
	 */
	private void medicalUnitLeaderChanged() {
		if (!loading && !flushing) {
			MedicalRoleSupport.setMedicalUnitLeaderName(controller.getData(), preparedBy.getText());
			inputChanged(false);
		}
	}

	/**
	 * Registers a document listener, saving live Safety Officer edits before panel flushes.
	 *
	 * @param field editable field.
	 * @param invalidatesRouting whether an edit invalidates route requests.
	 */
	private void watch(JTextField field, boolean invalidatesRouting) {
		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				updateSafetyOfficer();
				inputChanged(invalidatesRouting);
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				updateSafetyOfficer();
				inputChanged(invalidatesRouting);
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				updateSafetyOfficer();
				inputChanged(invalidatesRouting);
			}

			/** Saves only direct Safety Officer edits, never stale fields during a refresh or flush. */
			private void updateSafetyOfficer() {
				if (field == safetyOfficer && !loading && !flushing) {
					MedicalRoleSupport.setSafetyOfficerName(controller.getData(), field.getText());
				}
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
			refreshWorkflowControls();
		}
	}

	/**
	 * Enables each veterinary workflow action only when its inputs are available.
	 */
	private void refreshWorkflowControls() {
		if (loading || flushing) {
			return;
		}
		AppData data = controller.getData();
		boolean working = activeWorker != null && !activeWorker.isDone();
		geocodeBaseButton.setEnabled(!working && !stagingAddress.getText().isBlank());
		setBaseCoordinatesButton.setEnabled(!working && !stagingAddress.getText().isBlank()
				&& coordinate(stagingLatitude.getText(), -90, 90) != null
				&& coordinate(stagingLongitude.getText(), -180, 180) != null);
		boolean selectedAddressNeedsCoordinates = false;
		int validSelectedRows = 0;
		for (int selectedViewRow : directoryTable.getSelectedRows()) {
			int selectedRow = directoryTable.convertRowIndexToModel(selectedViewRow);
			if (selectedRow < 0 || selectedRow >= directoryModel.getRowCount()) {
				continue;
			}
			validSelectedRows++;
			VetFacility selected = record(directoryModel.getValueAt(selectedRow, 15));
			selectedAddressNeedsCoordinates |= !geocodingAddress(selected).isBlank()
					&& !hasValidCoordinates(selected);
		}
		geocodeVetButton.setEnabled(!working && selectedAddressNeedsCoordinates);
		includeSelectedButton.setEnabled(!working && validSelectedRows > 0);
		boolean hasRoutableFacility = data.getVetDirectory().getFacilities().stream()
				.anyMatch(CanineMedicalPanel::hasValidCoordinates);
		calculateRoutesButton.setEnabled(!working && data.getStagingLocation().hasValidCoordinates()
				&& hasRoutableFacility);
		boolean hasShortlistCandidate = !shortlistSelector.propose(data.getVetDirectory().getFacilities(),
				integer(shortlistLimit.getText(), VetShortlistSelector.DEFAULT_LIMIT),
				data.getStagingLocation().getGeneration()).isEmpty();
		previewSelectionButton.setEnabled(!working && hasShortlistCandidate);
	}

	/**
	 * Returns whether a facility has a valid latitude/longitude pair.
	 *
	 * @param facility veterinary facility.
	 * @return true when both coordinates are in geographic range.
	 */
	private static boolean hasValidCoordinates(VetFacility facility) {
		return facility.getLatitude() != null && facility.getLongitude() != null
				&& Double.isFinite(facility.getLatitude()) && facility.getLatitude() >= -90
				&& facility.getLatitude() <= 90 && Double.isFinite(facility.getLongitude())
				&& facility.getLongitude() >= -180 && facility.getLongitude() <= 180;
	}

	/**
	 * Creates visible HeiGIT/OpenStreetMap attribution with a clickable copyright link.
	 *
	 * @return attributed service label.
	 */
	private JEditorPane createAttribution() {
		JEditorPane attribution = new JEditorPane("text/html",
				"<html>© openrouteservice by HeiGIT | Data from "
						+ "<a href=\"https://openstreetmap.org/copyright\">OpenStreetMap</a></html>");
		attribution.setEditable(false);
		attribution.setOpaque(false);
		attribution.setBorder(null);
		attribution.addHyperlinkListener(event -> {
			if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				try {
					if (Desktop.isDesktopSupported()) {
						Desktop.getDesktop().browse(URI.create(event.getURL().toString()));
					}
				} catch (IOException | UnsupportedOperationException exception) {
					status.setText("Could not open the OpenStreetMap copyright page.");
				}
			}
		});
		return attribution;
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
	 * Loads structured transport records into the canine plan table.
	 *
	 * @param services canine transport records.
	 */
	private void loadTransport(List<MedicalPlan.TransportService> services) {
		transportModel.setRowCount(0);
		for (MedicalPlan.TransportService service : services) {
			if (service != null) {
				transportModel.addRow(transportRow(service));
			}
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
			VetFacility facility = record(directoryModel.getValueAt(row, 15));
			String oldAddress = geocodingAddress(facility);
			facility.setName(cell(directoryModel, row, 1));
			facility.setTown(cell(directoryModel, row, 2));
			facility.setAddress(cell(directoryModel, row, 3));
			facility.setState(cell(directoryModel, row, 12));
			facility.setPostalCode(cell(directoryModel, row, 13));
			facility.setContactNumbers(contacts(cell(directoryModel, row, 4)));
			facility.setAvailable24Hours(parseBoolean(cell(directoryModel, row, 5)));
			facility.setAvailabilityNotes(cell(directoryModel, row, 6));
			facility.setCapabilities(cell(directoryModel, row, 7));
			Double latitude = coordinate(cell(directoryModel, row, 9), -90, 90);
			Double longitude = coordinate(cell(directoryModel, row, 10), -180, 180);
			if (!oldAddress.equals(geocodingAddress(facility))) {
				directoryModel.setValueAt("", row, 9);
				directoryModel.setValueAt("", row, 10);
			} else if (latitude != null && longitude != null
					&& (!latitude.equals(facility.getLatitude()) || !longitude.equals(facility.getLongitude()))) {
				facility.setCoordinates(latitude, longitude, "manual", "Operator-entered coordinates");
			}
			facility.setSourceName(cell(directoryModel, row, 11));
			facility.setFax(cell(directoryModel, row, 14));
			directoryModel.setValueAt(facility.copy(), row, 15);
			directoryModel.setValueAt(distanceMiles(facility), row, 16);
			directoryModel.setValueAt(durationMinutes(facility), row, 17);
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
			VetFacility facility = record(selectionModel.getValueAt(row, 13));
			facility.setName(cell(selectionModel, row, 0));
			facility.setTown(cell(selectionModel, row, 1));
			facility.setState(cell(selectionModel, row, 2));
			facility.setPostalCode(cell(selectionModel, row, 3));
			String previousAddress = geocodingAddress(facility);
			facility.setAddress(cell(selectionModel, row, 4));
			facility.setContactNumbers(contacts(cell(selectionModel, row, 5)));
			facility.setFax(cell(selectionModel, row, 6));
			facility.setAvailable24Hours(parseBoolean(cell(selectionModel, row, 7)));
			if (!previousAddress.equals(geocodingAddress(facility))) {
				facility.clearDistance();
				selectionModel.setValueAt("", row, 8);
				selectionModel.setValueAt("", row, 9);
				selectionModel.setValueAt("", row, 10);
			}
			facility.setCapabilities(cell(selectionModel, row, 11));
			selectionModel.setValueAt(facility.copy(), row, 13);
			facilities.add(facility);
		}
		plan.setSelectedFacilities(facilities);
	}

	/**
	 * Saves every structured canine transportation table row to the plan.
	 *
	 * @param plan canine medical plan.
	 */
	private void saveTransport(CanineMedicalPlan plan) {
		List<MedicalPlan.TransportService> services = new ArrayList<>();
		for (int row = 0; row < transportModel.getRowCount(); row++) {
			MedicalPlan.TransportService service = new MedicalPlan.TransportService();
			service.setService(cell(transportModel, row, 0));
			service.setLocation(cell(transportModel, row, 1));
			service.setContactNumbers(cell(transportModel, row, 2));
			service.setFrequency(cell(transportModel, row, 3));
			service.setAirOrGround(cell(transportModel, row, 4));
			service.setLevelOfService(cell(transportModel, row, 5));
			services.add(service);
		}
		plan.setTransportServiceEntries(services);
	}

	/**
	 * Creates one editable table row from a structured transport service.
	 *
	 * @param service source transport service.
	 * @return row cells.
	 */
	private Object[] transportRow(MedicalPlan.TransportService service) {
		return new Object[] {service.getService(), service.getLocation(), service.getContactNumbers(),
				service.getFrequency(), service.getAirOrGround(), service.getLevelOfService()};
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
				UiSupport.formatInstant(facility.getVerifiedAt()),
				value(facility.getLatitude()), value(facility.getLongitude()), facility.getSourceName(),
				facility.getState(), facility.getPostalCode(), facility.getFax(), facility.copy(),
				distanceMiles(facility), durationMinutes(facility)};
	}

	/**
	 * Builds one editable accepted-plan table row.
	 *
	 * @param facility source snapshot.
	 * @return display cells plus stable ID and record.
	 */
	private Object[] selectionRow(VetFacility facility) {
		return new Object[] {facility.getName(), facility.getTown(), facility.getState(), facility.getPostalCode(),
				facility.getAddress(), String.join("; ", facility.getContactNumbers()), facility.getFax(),
				availability(facility.getAvailable24Hours()), distanceMiles(facility), durationMinutes(facility),
				facility.getDistanceMethod(), facility.getCapabilities(), facility.getId(), facility.copy()};
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
		removeDirectoryRows(selected);
	}

	/**
	 * Removes selected table rows after resolving any active editor before structural changes.
	 *
	 * @param selectedViewRows selected JTable view row indexes.
	 */
	void removeDirectoryRows(int[] selectedViewRows) {
		stopEditing(directoryTable);
		for (int index = selectedViewRows.length - 1; index >= 0; index--) {
			int viewRow = selectedViewRows[index];
			if (viewRow >= 0 && viewRow < directoryTable.getRowCount()) {
				directoryModel.removeRow(directoryTable.convertRowIndexToModel(viewRow));
			}
		}
	}

	/**
	 * Manually adds selected facilities as plan snapshots, including explicit exceptions.
	 */
	private void addSelectedFacilities() {
		pushToModel();
		int[] selected = directoryTable.getSelectedRows();
		if (selected.length == 0) {
			return;
		}
		int exceptions = 0;
		for (int viewRow : selected) {
			int row = directoryTable.convertRowIndexToModel(viewRow);
			VetFacility facility = record(directoryModel.getValueAt(row, 15));
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
			addSnapshot(record(directoryModel.getValueAt(row, 15)));
		}
	}

	/**
	 * Opens an editable details form for one regional directory entry.
	 *
	 * @param viewRow selected JTable view row.
	 */
	private void editFacilityDetails(int viewRow) {
		if (viewRow < 0 || viewRow >= directoryTable.getRowCount()) {
			return;
		}
		String facilityId = record(directoryModel.getValueAt(
				directoryTable.convertRowIndexToModel(viewRow), 15)).getId();
		pushToModel();
		int row = directoryModelRow(facilityId);
		if (row < 0 || row >= directoryModel.getRowCount()) {
			return;
		}
		JTextField name = new JTextField(cell(directoryModel, row, 1), 32);
		JTextField address = new JTextField(cell(directoryModel, row, 3), 32);
		JTextField town = new JTextField(cell(directoryModel, row, 2), 24);
		JTextField state = new JTextField(cell(directoryModel, row, 12), 8);
		JTextField postalCode = new JTextField(cell(directoryModel, row, 13), 10);
		JTextField contacts = new JTextField(cell(directoryModel, row, 4), 24);
		JTextField fax = new JTextField(cell(directoryModel, row, 14), 20);
		JComboBox<String> availability = new JComboBox<>(new String[] {"Yes", "No", UNKNOWN});
		availability.setSelectedItem(cell(directoryModel, row, 5));
		JTextArea availabilityNotes = new JTextArea(cell(directoryModel, row, 6), 3, 32);
		JTextArea capabilities = new JTextArea(cell(directoryModel, row, 7), 4, 32);
		JTextField verifiedAt = new JTextField(cell(directoryModel, row, 8), 24);
		JTextField source = new JTextField(cell(directoryModel, row, 11), 24);
		JPanel fields = new JPanel(new GridLayout(0, 2, 6, 4));
		addDetailField(fields, "Hospital name", name);
		addDetailField(fields, "Street address", address);
		addDetailField(fields, "City / town", town);
		addDetailField(fields, "State", state);
		addDetailField(fields, "ZIP / postal code", postalCode);
		addDetailField(fields, "Contact numbers (; separated)", contacts);
		addDetailField(fields, "Fax", fax);
		addDetailField(fields, "Confirmed 24-hour availability", availability);
		addDetailField(fields, "Availability notes", new JScrollPane(availabilityNotes));
		addDetailField(fields, "Capabilities / notes", new JScrollPane(capabilities));
		addDetailField(fields, "Verified at", verifiedAt);
		addDetailField(fields, "Source", source);
		JScrollPane content = new JScrollPane(fields);
		content.setPreferredSize(new java.awt.Dimension(720, 480));
		if (JOptionPane.showConfirmDialog(this, content, "Edit veterinary facility details",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}
		loading = true;
		try {
			directoryModel.setValueAt(name.getText(), row, 1);
			directoryModel.setValueAt(town.getText(), row, 2);
			directoryModel.setValueAt(address.getText(), row, 3);
			directoryModel.setValueAt(contacts.getText(), row, 4);
			directoryModel.setValueAt(availability.getSelectedItem(), row, 5);
			directoryModel.setValueAt(availabilityNotes.getText(), row, 6);
			directoryModel.setValueAt(capabilities.getText(), row, 7);
			directoryModel.setValueAt(verifiedAt.getText(), row, 8);
			directoryModel.setValueAt(source.getText(), row, 11);
			directoryModel.setValueAt(state.getText(), row, 12);
			directoryModel.setValueAt(postalCode.getText(), row, 13);
			directoryModel.setValueAt(fax.getText(), row, 14);
		} finally {
			loading = false;
		}
		inputChanged(true);
	}

	/**
	 * Adds one labeled field to the veterinary details form.
	 *
	 * @param fields form panel.
	 * @param label field label.
	 * @param component editor component.
	 */
	private void addDetailField(JPanel fields, String label, java.awt.Component component) {
		fields.add(new JLabel(label));
		fields.add(component);
	}

	/**
	 * Adds one independent plan snapshot unless already present by stable ID.
	 *
	 * @param source source facility.
	 * @return true if a new plan snapshot was added.
	 */
	private boolean addSnapshot(VetFacility source) {
		for (int row = 0; row < selectionModel.getRowCount(); row++) {
			VetFacility existing = record(selectionModel.getValueAt(row, 13));
			if (source.getId().equals(existing.getId())) {
				return false;
			}
		}
		selectionModel.addRow(selectionRow(source.copy()));
		return true;
	}

	/**
	 * Adds unique facility snapshots and keeps all existing plan entries.
	 *
	 * @param facilities proposed facilities.
	 * @return number of new snapshots added.
	 */
	int addSnapshots(List<VetFacility> facilities) {
		int added = 0;
		for (VetFacility facility : facilities) {
			if (addSnapshot(facility)) {
				added++;
			}
		}
		return added;
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
		AppData incident = controller.getData();
		VetDirectory existing = copyDirectory(incident.getVetDirectory());
		startWorker(new SwingWorker<VetDirectoryFileService.ImportResult, Void>() {
			@Override
			protected VetDirectoryFileService.ImportResult doInBackground() {
				return fileService.importFiles(files, existing);
			}

			@Override
			protected void done() {
				try {
					VetDirectoryFileService.ImportResult result = get();
					if (controller.getData() != incident || !isActive(this)) {
						return;
					}
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
					if (JOptionPane.showConfirmDialog(CanineMedicalPanel.this, preview,
							"Preview regional directory merge", JOptionPane.OK_CANCEL_OPTION)
							!= JOptionPane.OK_OPTION || controller.getData() != incident || !isActive(this)) {
						return;
					}
					Map<String, VetDirectoryFileService.ConflictResolution> choices = new HashMap<>();
					for (VetDirectoryFileService.ConflictRecord conflict : result.conflictRecords()) {
						Object[] options = {"Keep both", "Replace existing", "Skip imported", "Cancel merge"};
						int choice = JOptionPane.showOptionDialog(CanineMedicalPanel.this,
								"An imported facility reuses an existing ID but has different details.\n"
										+ "Existing record: " + conflict.existingId() + "\nImported record: "
										+ conflict.importedName() + " (" + conflict.sourceName() + ")\n"
										+ "Choose explicitly; Keep both retains both complete records.",
								"Resolve regional record conflict", JOptionPane.DEFAULT_OPTION,
								JOptionPane.WARNING_MESSAGE, null, options, options[0]);
						if (choice < 0 || choice == 3) {
							return;
						}
						VetDirectoryFileService.ConflictResolution resolution = switch (choice) {
							case 1 -> VetDirectoryFileService.ConflictResolution.REPLACE_EXISTING;
							case 2 -> VetDirectoryFileService.ConflictResolution.SKIP_IMPORTED;
							default -> VetDirectoryFileService.ConflictResolution.KEEP_BOTH;
						};
						choices.put(conflict.importedId(), resolution);
					}
					if (controller.getData() == incident && isActive(this)) {
						incident.setVetDirectory(fileService.resolveConflicts(result, choices));
						refreshFromModel();
						controller.markDirty();
					}
				} catch (CancellationException exception) {
					status.setText("Directory import cancelled.");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException exception) {
					showFileError(exception.getCause());
				} finally {
					finishWorker(this);
				}
			}
		});
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
		boolean replaceExisting = overwrite;
		VetDirectory snapshot = copyDirectory(controller.getData().getVetDirectory());
		startWorker(new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws IOException {
				return fileService.save(target, snapshot, replaceExisting);
			}

			@Override
			protected void done() {
				try {
					if (get()) {
						status.setText("Regional directory explicitly saved to " + target.getFileName());
					}
				} catch (CancellationException exception) {
					status.setText("Directory export cancelled.");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException exception) {
					showFileError(exception.getCause());
				} finally {
					finishWorker(this);
				}
			}
		});
	}

	/**
	 * Imports ambulance rows from the human medical plan without assuming canine capability.
	 */
	private void copyMedicalAmbulanceServices() {
		pushToModel();
		int added = addAmbulanceServicesFromMedicalPlan();
		if (added > 0) {
			pushToModel();
			controller.markDirty();
			status.setText(added + " ambulance service(s) copied. Confirm canine transport capability.");
		} else {
			status.setText("No new ambulance services found in the human Medical Plan.");
		}
	}

	/**
	 * Copies unique ambulance records from the incident's human medical plan.
	 *
	 * @return number of new canine transport rows.
	 */
	int addAmbulanceServicesFromMedicalPlan() {
		List<MedicalPlan.TransportService> ambulanceServices = controller.getData().getMedicalPlan()
				.getTransportServices().stream()
				.filter(service -> service != null
						&& service.getService().toLowerCase(java.util.Locale.ROOT).contains("ambulance"))
				.toList();
		int added = 0;
		loading = true;
		try {
			for (MedicalPlan.TransportService source : ambulanceServices) {
				if (!hasTransportService(source)) {
					transportModel.addRow(transportRow(copyTransportService(source)));
					added++;
				}
			}
		} finally {
			loading = false;
		}
		return added;
	}

	/**
	 * Opens and merges a separate structured transport CSV into the canine plan.
	 */
	private void importTransportCsv() {
		JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
		chooser.setFileFilter(new FileNameExtensionFilter("Canine transport services (*.csv)", "csv"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		pushToModel();
		try {
			int added = appendTransportServices(fileService.importTransportCsv(chooser.getSelectedFile().toPath()));
			pushToModel();
			controller.markDirty();
			status.setText(added + " transport service(s) imported.");
		} catch (IOException | IllegalArgumentException exception) {
			showFileError(exception);
		}
	}

	/**
	 * Appends imported transport records without duplicating existing service details.
	 *
	 * @param services imported structured records.
	 * @return number of new rows appended.
	 */
	int appendTransportServices(List<MedicalPlan.TransportService> services) {
		int added = 0;
		loading = true;
		try {
			for (MedicalPlan.TransportService service : services) {
				if (service != null && !hasTransportService(service)) {
					transportModel.addRow(transportRow(copyTransportService(service)));
					added++;
				}
			}
		} finally {
			loading = false;
		}
		return added;
	}

	/**
	 * Explicitly exports structured canine transportation services to CSV.
	 */
	private void exportTransportCsv() {
		pushToModel();
		JFileChooser chooser = new JFileChooser(defaultDirectory.toFile());
		chooser.setFileFilter(new FileNameExtensionFilter("Canine transport services (*.csv)", "csv"));
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
			fileService.saveTransportCsv(target, controller.getData().getCanineMedicalPlan()
					.getTransportServiceEntries(), overwrite);
			status.setText("Canine transport services explicitly saved to " + target.getFileName());
		} catch (IOException exception) {
			showFileError(exception);
		}
	}

	/**
	 * Removes selected transport table rows after committing any active cell edit.
	 */
	private void removeTransportRows() {
		stopEditing(transportTable);
		int[] selected = transportTable.getSelectedRows();
		for (int index = selected.length - 1; index >= 0; index--) {
			transportModel.removeRow(transportTable.convertRowIndexToModel(selected[index]));
		}
	}

	/**
	 * Checks whether all displayed fields of a transport entry already exist in the editor.
	 *
	 * @param candidate transport entry to compare.
	 * @return true when an equivalent row exists.
	 */
	private boolean hasTransportService(MedicalPlan.TransportService candidate) {
		for (int row = 0; row < transportModel.getRowCount(); row++) {
			if (sameText(cell(transportModel, row, 0), candidate.getService())
					&& sameText(cell(transportModel, row, 1), candidate.getLocation())
					&& sameText(cell(transportModel, row, 2), candidate.getContactNumbers())
					&& sameText(cell(transportModel, row, 3), candidate.getFrequency())
					&& sameText(cell(transportModel, row, 4), candidate.getAirOrGround())
					&& sameText(cell(transportModel, row, 5), candidate.getLevelOfService())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Compares editable service text without case or surrounding whitespace differences.
	 *
	 * @param first first text value.
	 * @param second second text value.
	 * @return true when normalized values match.
	 */
	private boolean sameText(String first, String second) {
		return first.trim().equalsIgnoreCase(second == null ? "" : second.trim());
	}

	/**
	 * Copies all human medical transport fields into an independent record.
	 *
	 * @param source source human transport record.
	 * @return independent transport service.
	 */
	private MedicalPlan.TransportService copyTransportService(MedicalPlan.TransportService source) {
		MedicalPlan.TransportService copy = new MedicalPlan.TransportService();
		copy.setService(source.getService());
		copy.setLocation(source.getLocation());
		copy.setContactNumbers(source.getContactNumbers());
		copy.setFrequency(source.getFrequency());
		copy.setAirOrGround(source.getAirOrGround());
		copy.setLevelOfService(source.getLevelOfService());
		return copy;
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
								"Operator-confirmed candidate; confidence " + candidate.confidence(),
								UtcTimestamp.nowInstant());
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
	 * Geocodes selected facilities without coordinates and confirms each result on the EDT.
	 */
	private void geocodeSelectedVet() {
		pushToModel();
		int[] selectedRows = directoryTable.getSelectedRows();
		if (selectedRows.length == 0) {
			return;
		}
		AppData incident = controller.getData();
		List<FacilityAddress> addresses = new ArrayList<>();
		for (int viewRow : selectedRows) {
			int row = directoryTable.convertRowIndexToModel(viewRow);
			VetFacility facility = record(directoryModel.getValueAt(row, 15));
			String address = geocodingAddress(facility);
			if (!address.isBlank() && !hasValidCoordinates(facility)) {
				addresses.add(new FacilityAddress(facility.getId(), address));
			}
		}
		if (addresses.isEmpty()) {
			status.setText("Select at least one facility with an address and no coordinates.");
			return;
		}
		long baseGeneration = incident.getStagingLocation().getGeneration();
		startWorker(new SwingWorker<List<FacilityGeocodeResult>, Void>() {
			@Override
			protected List<FacilityGeocodeResult> doInBackground() throws Exception {
				List<FacilityGeocodeResult> results = new ArrayList<>();
				for (FacilityAddress address : addresses) {
					if (isCancelled() || Thread.currentThread().isInterrupted()) {
						throw new InterruptedException("Geocoding cancelled");
					}
					try {
						results.add(new FacilityGeocodeResult(address,
								geocoder.geocode(address.fullAddress()), ""));
					} catch (IOException exception) {
						results.add(new FacilityGeocodeResult(address, List.of(), "Lookup failed"));
					}
				}
				return results;
			}

			@Override
			protected void done() {
				try {
					List<FacilityGeocodeResult> results = get();
					if (!isCurrent(incident, baseGeneration) || !isActive(this)) {
						return;
					}
					int confirmed = 0;
					int failed = 0;
					for (FacilityGeocodeResult result : results) {
						if (!result.error().isBlank()) {
							failed++;
							continue;
						}
						AddressGeocoder.Candidate candidate = confirmCandidate(result.candidates());
						if (candidate != null && applyGeocodedVetCandidate(incident, result.address().facilityId(),
								result.address().fullAddress(), candidate)) {
							confirmed++;
						}
					}
					status.setText("Confirmed coordinates for " + confirmed + " of " + results.size()
							+ " selected facilities." + (failed == 0 ? "" : " " + failed + " lookups failed."));
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
	 * Applies a confirmed candidate only when the facility still has the queried full address.
	 *
	 * @param incident incident captured by the geocoding request.
	 * @param facilityId stable directory facility identifier.
	 * @param requestedAddress complete address sent to the geocoder.
	 * @param candidate operator-confirmed geocoding candidate.
	 * @return true when the candidate was applied to the current incident.
	 */
	boolean applyGeocodedVetCandidate(AppData incident, String facilityId, String requestedAddress,
			AddressGeocoder.Candidate candidate) {
		if (controller.getData() != incident) {
			return false;
		}
		VetFacility current = findFacility(incident, facilityId);
		if (current == null || !geocodingAddress(current).equals(requestedAddress)) {
			return false;
		}
		current.setCoordinates(candidate.point().latitude(), candidate.point().longitude(),
				"openrouteservice-geocoder", "Operator-confirmed: " + candidate.label());
		refreshFromModel();
		controller.markDirty();
		return true;
	}

	/**
	 * Captures the facility identity and full address for a geocoding request.
	 *
	 * @param facilityId stable incident-directory identifier.
	 * @param fullAddress complete address query.
	 */
	private record FacilityAddress(String facilityId, String fullAddress) {
	}

	/**
	 * Stores geocoding results or a sanitized failure for one selected directory row.
	 *
	 * @param address captured facility identity and query.
	 * @param candidates provider-ordered candidates.
	 * @param error sanitized failure summary, or blank on success.
	 */
	private record FacilityGeocodeResult(FacilityAddress address, List<AddressGeocoder.Candidate> candidates,
			String error) {
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
		int limit = integer(shortlistLimit.getText(), VetShortlistSelector.DEFAULT_LIMIT);
		startWorker(new SwingWorker<RouteWorkResult, Void>() {
			@Override
			protected RouteWorkResult doInBackground() throws Exception {
				VetRoutingService routing = new VetRoutingService(routeProvider);
				VetRoutingService.CalculationReport report = routing.calculate(base, snapshot, false);
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
							+ result.report().cached() + "; skipped: "
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
				new String[] {"Name", "Town", "Address", "24 Hours", "Road distance (mi)", "Drive time (min)"}, 0);
		for (VetFacility facility : proposal) {
			preview.addRow(new Object[] {facility.getName(), facility.getTown(), facility.getAddress(),
					availability(facility.getAvailable24Hours()), distanceMiles(facility),
					durationMinutes(facility)});
		}
		JTable previewTable = new JTable(preview);
		previewTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		Object[] options = {"Add selected to plan", "Add all to plan", "Cancel"};
		int choice = JOptionPane.showOptionDialog(this, new JScrollPane(previewTable),
				"Review nearest confirmed 24-hour road routes", JOptionPane.DEFAULT_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		if (choice < 0 || choice == 2) {
			return;
		}
		List<VetFacility> additions = new ArrayList<>();
		if (choice == 1) {
			additions.addAll(proposal);
		} else {
			for (int viewRow : previewTable.getSelectedRows()) {
				additions.add(proposal.get(viewRow));
			}
			if (additions.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Select one or more facilities, or choose Add all to plan.",
						"No facilities selected", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
		}
		int added = addSnapshots(additions);
		pushToModel();
		controller.markDirty();
		status.setText("Added " + added + " facility/facilities; existing plan entries and duplicates were preserved.");
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
				"manual", "Operator-entered coordinates", UtcTimestamp.nowInstant());
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
	 * Combines separately stored address fields into a complete geocoder query.
	 *
	 * @param facility veterinary facility.
	 * @return street, city, state, and postal code query.
	 */
	private String geocodingAddress(VetFacility facility) {
		return java.util.stream.Stream.of(facility.getAddress(), facility.getTown(), facility.getState(),
				facility.getPostalCode()).filter(value -> value != null && !value.isBlank())
				.collect(java.util.stream.Collectors.joining(", "));
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
		cancelRequest.setVisible(true);
		status.setText("Working with the selected address/route provider.");
		refreshWorkflowControls();
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
		cancelRequest.setVisible(false);
		refreshWorkflowControls();
	}

	/**
	 * Hides the progress indicator after a worker completes.
	 */
	private void finishWorker(SwingWorker<?, ?> worker) {
		if (activeWorker == worker) {
			activeWorker = null;
			progress.setVisible(false);
			cancelRequest.setVisible(false);
			refreshWorkflowControls();
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
	 * Displays a local regional-file error without changing incident state.
	 *
	 * @param failure file-service failure.
	 */
	private void showFileError(Throwable failure) {
		String message = failure == null || failure.getMessage() == null
				? "Regional directory file operation failed" : failure.getMessage();
		status.setText(message);
		JOptionPane.showMessageDialog(this, message, "Regional directory", JOptionPane.WARNING_MESSAGE);
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
			plan.setPreparedAt(UiSupport.parseIsoDateTime(preparedAt.getText().trim()));
		} catch (RuntimeException exception) {
			status.setText("Preparation date/time must use yyyy-MM-ddTHH:mm.");
		}
	}

	/**
	 * Commits an active cell editor before model replacement, retaining rejected input.
	 *
	 * @param table table to flush.
	 */
	private void stopEditing(JTable table) {
		UiSupport.commitTableEdits(table);
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
	 * Formats a road distance in miles.
	 *
	 * @param facility route record.
	 * @return miles to one decimal place, or blank when unavailable.
	 */
	private static String distanceMiles(VetFacility facility) {
		return Double.isFinite(facility.getDistanceMeters())
				? String.format(java.util.Locale.ROOT, "%.1f", facility.getDistanceMeters() / 1609.344)
				: "";
	}

	/**
	 * Formats a provider-supplied route duration in minutes.
	 *
	 * @param facility route record.
	 * @return minutes to one decimal place, or blank when unavailable.
	 */
	private static String durationMinutes(VetFacility facility) {
		return Double.isFinite(facility.getDurationSeconds())
				? String.format(java.util.Locale.ROOT, "%.1f", facility.getDurationSeconds() / 60)
				: "";
	}

	/**
	 * Converts a sortable display value to a number, placing unavailable values last.
	 *
	 * @param value displayed numeric value.
	 * @return parsed number or NaN when blank/invalid.
	 */
	private static double sortValue(Object value) {
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException exception) {
			return Double.NaN;
		}
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
