/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.MedicalPlan;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared editor for the standard human ICS 206 Medical Plan.
 */
public class Ics206Panel extends JPanel {
	private static final String UNKNOWN = "Unknown";
	private static final String SIMPLE_MODE = "simple";
	private static final String DETAILED_MODE = "detailed";
	private final AppController controller;
	private final JComboBox<String> mode = new JComboBox<>(new String[] {"Simple", "Detailed"});
	private final JTextField medicalRole = new JTextField();
	private final JTextField medicalName = new JTextField();
	private final JTextField medicalContact = new JTextField();
	private final JTextArea call911 = new JTextArea(3, 60);
	private final JTextArea procedures = new JTextArea(3, 60);
	private final JTextArea airOperations = new JTextArea(2, 60);
	private final JCheckBox aviationUsed = new JCheckBox("Aviation assets are used for rescue");
	private final JTextField preparedBy = new JTextField();
	private final JTextField preparedAt = new JTextField();
	private final JTextField reviewedBy = new JTextField();
	private final JComboBox<String> reviewStatus = new JComboBox<>(
			new String[] {"Not reviewed", "Reviewed", "Approved"});
	private final JLabel validation = new JLabel(" ");
	private final JTabbedPane detailTabs = new JTabbedPane();
	private final JPanel modeContent = new JPanel(new CardLayout());
	private final DefaultTableModel aidModel = tableModel(
			"Name", "Location", "Contact numbers", "Frequency", "Paramedics (Yes/No/Unknown)");
	private final DefaultTableModel transportModel = tableModel(
			"Service", "Location", "Contact numbers", "Frequency", "Air/Ground", "Level of service");
	private final DefaultTableModel hospitalModel = tableModel(
			"Name", "Address", "Contact numbers", "Frequency", "Latitude", "Longitude",
			"Coordinate format", "Helipad coordinates", "Air travel time", "Air provenance",
			"Ground travel time", "Ground provenance", "Trauma center (Yes/No/Unknown)", "Trauma level",
			"Burn center (Yes/No/Unknown)", "Helipad (Yes/No/Unknown)");
	private final JTable aidTable = new JTable(aidModel);
	private final JTable transportTable = new JTable(transportModel);
	private final JTable hospitalTable = new JTable(hospitalModel);
	private Consumer<String> exportRequest = ignored -> { };
	private boolean loading;
	private boolean flushing;

	/**
	 * Creates the editor and binds it to the active incident's human plan.
	 *
	 * @param controller application controller.
	 */
	public Ics206Panel(AppController controller) {
		super(new BorderLayout(6, 6));
		this.controller = controller;
		RadioChannelPicker.install(aidTable, 3, controller);
		RadioChannelPicker.install(transportTable, 3, controller);
		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
		header.add(new JLabel("Plan detail:"));
		header.add(mode);
		JButton export = new JButton("Export ICS 206 PDF");
		export.addActionListener(event -> {
			pushToModel();
			exportRequest.accept("ICS 206");
		});
		header.add(export);
		add(header, BorderLayout.NORTH);
		add(buildEditor(), BorderLayout.CENTER);
		add(validation, BorderLayout.SOUTH);
		configureListeners();
		refreshFromModel();
	}

	/**
	 * Sets the shared form export action.
	 *
	 * @param exportRequest export callback; null disables the shortcut.
	 */
	public void setExportRequest(Consumer<String> exportRequest) {
		this.exportRequest = exportRequest == null ? ignored -> { } : exportRequest;
	}

	/**
	 * Refreshes editor controls from the active incident without marking it dirty.
	 */
	public void refreshFromModel() {
		loading = true;
		try {
			MedicalPlan plan = controller.getData().getMedicalPlan();
			mode.setSelectedItem(plan.isSimpleMode() ? "Simple" : "Detailed");
			medicalRole.setText(plan.getDesignatedMedicalUnitRole());
			medicalName.setText(MedicalRoleSupport.medicalUnitLeaderName(controller.getData()));
			medicalContact.setText(plan.getDesignatedMedicalUnitContact());
			call911.setText(plan.getCall911Procedure());
			procedures.setText(plan.getEmergencyProcedures());
			aviationUsed.setSelected(plan.isAviationAssetsUsed());
			airOperations.setText(plan.getAirOperationsReminder());
			preparedBy.setText(MedicalRoleSupport.medicalUnitLeaderName(controller.getData()));
			preparedAt.setText(plan.getPreparedAt() == null ? "" : plan.getPreparedAt().toString());
			reviewedBy.setText(MedicalRoleSupport.safetyOfficerName(controller.getData()));
			reviewStatus.setSelectedItem(plan.getReviewStatus());
			loadAidStations(plan);
			loadTransport(plan);
			loadHospitals(plan);
			showMode(plan.isSimpleMode());
			validation.setText(" ");
		} finally {
			loading = false;
		}
	}

	/**
	 * Flushes all controls and pending table edits into the incident model.
	 */
	public void pushToModel() {
		if (flushing) {
			return;
		}
		flushing = true;
		try {
			UiSupport.commitTableEdits(aidTable, transportTable, hospitalTable);
			MedicalPlan plan = controller.getData().getMedicalPlan();
			plan.setSimpleMode("Simple".equals(mode.getSelectedItem()));
			plan.setDesignatedMedicalUnitRole(medicalRole.getText());
			plan.setDesignatedMedicalUnitContact(medicalContact.getText());
			plan.setCall911Procedure(call911.getText());
			plan.setEmergencyProcedures(procedures.getText());
			plan.setAviationAssetsUsed(aviationUsed.isSelected());
			plan.setAirOperationsReminder(airOperations.getText());
			plan.setReviewStatus(String.valueOf(reviewStatus.getSelectedItem()));
			setPreparedAt(plan);
			saveAidStations(plan);
			saveTransport(plan);
			saveHospitals(plan);
		} finally {
			flushing = false;
		}
	}

	/**
	 * Builds the mode-independent emergency instruction and detailed form sections.
	 *
	 * @return editor content.
	 */
	private JPanel buildEditor() {
		JPanel editor = new JPanel(new BorderLayout(6, 6));
		JPanel roleFields = new JPanel(new GridLayout(0, 2, 4, 4));
		roleFields.add(new JLabel("Medical unit role"));
		roleFields.add(medicalRole);
		roleFields.add(new JLabel("Assigned person (optional)"));
		roleFields.add(medicalName);
		roleFields.add(new JLabel("Contact"));
		roleFields.add(medicalContact);
		editor.add(roleFields, BorderLayout.NORTH);

		JPanel simple = new JPanel(new BorderLayout(4, 4));
		simple.setBorder(BorderFactory.createTitledBorder("1. Emergency medical instructions"));
		simple.add(new JScrollPane(call911), BorderLayout.CENTER);
		modeContent.add(simple, SIMPLE_MODE);

		detailTabs.addTab("3. Aid Stations", tablePanel(aidTable, aidModel));
		JPanel transportation = new JPanel(new BorderLayout(4, 4));
		transportation.add(tablePanel(transportTable, transportModel), BorderLayout.CENTER);
		JPanel aviationPanel = new JPanel(new BorderLayout(4, 4));
		aviationPanel.setBorder(BorderFactory.createTitledBorder("Aviation coordination"));
		aviationUsed.setText("Aviation assets are used for rescue; coordinate with Air Operations Branch");
		aviationPanel.add(aviationUsed, BorderLayout.NORTH);
		aviationPanel.add(new JScrollPane(airOperations), BorderLayout.CENTER);
		transportation.add(aviationPanel, BorderLayout.SOUTH);
		detailTabs.addTab("4. Transportation", transportation);
		detailTabs.addTab("5. Hospitals", tablePanel(hospitalTable, hospitalModel));
		JPanel procedurePanel = new JPanel(new BorderLayout(4, 4));
		procedurePanel.setBorder(BorderFactory.createTitledBorder("6. Special medical emergency procedures"));
		procedurePanel.add(new JScrollPane(procedures), BorderLayout.CENTER);
		detailTabs.addTab("6. Special Procedures", procedurePanel);
		detailTabs.setBorder(BorderFactory.createTitledBorder("Detailed ICS 206 entries"));
		modeContent.add(detailTabs, DETAILED_MODE);
		editor.add(modeContent, BorderLayout.CENTER);
		editor.add(buildApprovalPanel(), BorderLayout.SOUTH);
		return editor;
	}

	/**
	 * Builds preparer/reviewer fields without deriving signatures from role data.
	 *
	 * @return metadata editor.
	 */
	private JPanel buildApprovalPanel() {
		JPanel panel = new JPanel(new GridLayout(2, 4, 4, 2));
		panel.setBorder(BorderFactory.createTitledBorder("7. Prepared by / 8. Reviewed by"));
		panel.add(new JLabel("Medical Unit Leader"));
		panel.add(preparedBy);
		panel.add(new JLabel("Prepared at (yyyy-MM-ddTHH:mm)"));
		panel.add(preparedAt);
		panel.add(new JLabel("Safety Officer"));
		panel.add(reviewedBy);
		panel.add(new JLabel("Review status"));
		panel.add(reviewStatus);
		return panel;
	}

	/**
	 * Creates an editable directory table with row-add/remove controls.
	 *
	 * @param table editable table.
	 * @param model table data.
	 * @return table editor.
	 */
	private JPanel tablePanel(JTable table, DefaultTableModel model) {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton add = new JButton("Add row");
		add.addActionListener(event -> model.addRow(new Object[model.getColumnCount()]));
		JButton remove = new JButton("Remove selected row");
		remove.addActionListener(event -> {
			int row = table.getSelectedRow();
			if (row >= 0) {
				model.removeRow(table.convertRowIndexToModel(row));
			}
		});
		controls.add(add);
		controls.add(remove);
		panel.add(controls, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Attaches dirty tracking to controls that can change persisted values.
	 */
	private void configureListeners() {
		mode.addActionListener(event -> {
			if (!loading && !flushing) {
				showMode("Simple".equals(mode.getSelectedItem()));
				inputChanged();
			}
		});
		aviationUsed.addActionListener(event -> inputChanged());
		reviewStatus.addActionListener(event -> inputChanged());
		watch(medicalRole);
		watchMedicalRole(medicalName, preparedBy, true);
		watch(medicalContact);
		watch(call911);
		watch(procedures);
		watch(airOperations);
		watchMedicalRole(preparedBy, medicalName, true);
		watch(preparedAt);
		watchMedicalRole(reviewedBy, null, false);
		UiSupport.installNameAutocomplete(medicalName, controller::getPersonnelNames,
				name -> synchronizeMedicalRole(medicalName, preparedBy, name, true));
		UiSupport.installNameAutocomplete(preparedBy, controller::getPersonnelNames,
				name -> synchronizeMedicalRole(preparedBy, medicalName, name, true));
		UiSupport.installNameAutocomplete(reviewedBy, controller::getPersonnelNames,
				name -> synchronizeMedicalRole(reviewedBy, null, name, false));
		aidModel.addTableModelListener(event -> inputChanged());
		transportModel.addTableModelListener(event -> inputChanged());
		hospitalModel.addTableModelListener(event -> inputChanged());
	}

	/**
	 * Watches a role field and keeps its shared medical-plan assignment current.
	 *
	 * @param field source role field.
	 * @param otherField corresponding display field, if any.
	 * @param medicalLeader true for Medical Unit Leader, false for Safety Officer.
	 */
	private void watchMedicalRole(JTextField field, JTextField otherField, boolean medicalLeader) {
		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				roleFieldChanged(field, otherField, medicalLeader);
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				roleFieldChanged(field, otherField, medicalLeader);
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				roleFieldChanged(field, otherField, medicalLeader);
			}
		});
	}

	/**
	 * Synchronizes a selected role name or explicit clearing across shared form fields and models.
	 *
	 * @param source edited role field.
	 * @param otherField corresponding display field, if any.
	 * @param medicalLeader true for Medical Unit Leader, false for Safety Officer.
	 */
	private void roleFieldChanged(JTextField source, JTextField otherField, boolean medicalLeader) {
		if (loading || flushing) {
			return;
		}
		String name = source.getText().trim();
		if (!name.isBlank()) {
			setSharedRole(name, medicalLeader);
			if (otherField != null) {
				javax.swing.SwingUtilities.invokeLater(() -> {
					if (!loading && name.equals(source.getText().trim())) {
						synchronizeMedicalRole(source, otherField, name, medicalLeader);
					}
				});
			}
		} else {
			setSharedRole("", medicalLeader);
		}
		inputChanged();
	}

	/**
	 * Applies the selected role name and updates the paired display field.
	 *
	 * @param source selected field.
	 * @param otherField paired display field, if any.
	 * @param name selected person.
	 * @param medicalLeader true for Medical Unit Leader, false for Safety Officer.
	 */
	private void synchronizeMedicalRole(JTextField source, JTextField otherField, String name,
			boolean medicalLeader) {
		loading = true;
		try {
			source.setText(name);
			if (otherField != null) {
				otherField.setText(name);
			}
		} finally {
			loading = false;
		}
		setSharedRole(name, medicalLeader);
		controller.markDirty();
	}

	/**
	 * Writes a selected name to its shared incident role.
	 *
	 * @param name selected person.
	 * @param medicalLeader true for Medical Unit Leader, false for Safety Officer.
	 */
	private void setSharedRole(String name, boolean medicalLeader) {
		if (medicalLeader) {
			MedicalRoleSupport.setMedicalUnitLeaderName(controller.getData(), name);
		} else {
			MedicalRoleSupport.setSafetyOfficerName(controller.getData(), name);
		}
	}

	/**
	 * Switches between the simple emergency instruction and detailed ICS 206 entries.
	 *
	 * @param simpleMode true to display the simple instruction editor.
	 */
	private void showMode(boolean simpleMode) {
		CardLayout layout = (CardLayout) modeContent.getLayout();
		layout.show(modeContent, simpleMode ? SIMPLE_MODE : DETAILED_MODE);
		modeContent.revalidate();
		modeContent.repaint();
	}

	/**
	 * Registers document listeners for a text field.
	 *
	 * @param field editable text field.
	 */
	private void watch(JTextField field) {
		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				inputChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				inputChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				inputChanged();
			}
		});
	}

	/**
	 * Registers document listeners for a multiline editor.
	 *
	 * @param area editable text area.
	 */
	private void watch(JTextArea area) {
		area.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				inputChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				inputChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				inputChanged();
			}
		});
	}

	/**
	 * Flushes an edit and schedules the controller's normal autosave.
	 */
	private void inputChanged() {
		if (!loading) {
			pushToModel();
			controller.markDirty();
		}
	}

	/**
	 * Loads aid station rows into the editable table.
	 *
	 * @param plan medical plan.
	 */
	private void loadAidStations(MedicalPlan plan) {
		aidModel.setRowCount(0);
		for (MedicalPlan.AidStation station : plan.getAidStations()) {
			aidModel.addRow(new Object[] {station.getName(), station.getLocation(), station.getContactNumbers(),
					station.getFrequency(), status(station.getParamedicsOnSite())});
		}
	}

	/**
	 * Loads transport rows into the editable table.
	 *
	 * @param plan medical plan.
	 */
	private void loadTransport(MedicalPlan plan) {
		transportModel.setRowCount(0);
		for (MedicalPlan.TransportService service : plan.getTransportServices()) {
			transportModel.addRow(new Object[] {service.getService(), service.getLocation(),
					service.getContactNumbers(), service.getFrequency(), service.getAirOrGround(),
					service.getLevelOfService()});
		}
	}

	/**
	 * Loads hospital rows into the editable table.
	 *
	 * @param plan medical plan.
	 */
	private void loadHospitals(MedicalPlan plan) {
		hospitalModel.setRowCount(0);
		for (MedicalPlan.Hospital hospital : plan.getHospitals()) {
			hospitalModel.addRow(new Object[] {hospital.getName(), hospital.getAddress(), hospital.getContactNumbers(),
					hospital.getFrequency(), value(hospital.getLatitude()), value(hospital.getLongitude()),
					hospital.getCoordinateFormat(), hospital.getHelipadCoordinates(), hospital.getAirTravelTime(),
					hospital.getAirTravelProvenance(), hospital.getGroundTravelTime(),
					hospital.getGroundTravelProvenance(), status(hospital.getTraumaCenter()), hospital.getTraumaLevel(),
					status(hospital.getBurnCenter()), status(hospital.getHelipad())});
		}
	}

	/**
	 * Saves aid station rows while retaining nullable status.
	 *
	 * @param plan medical plan.
	 */
	private void saveAidStations(MedicalPlan plan) {
		List<MedicalPlan.AidStation> stations = new ArrayList<>();
		for (int row = 0; row < aidModel.getRowCount(); row++) {
			MedicalPlan.AidStation station = new MedicalPlan.AidStation();
			station.setName(cell(aidModel, row, 0));
			station.setLocation(cell(aidModel, row, 1));
			station.setContactNumbers(cell(aidModel, row, 2));
			station.setFrequency(cell(aidModel, row, 3));
			station.setParamedicsOnSite(parseStatus(cell(aidModel, row, 4)));
			stations.add(station);
		}
		plan.setAidStations(stations);
	}

	/**
	 * Saves transport rows.
	 *
	 * @param plan medical plan.
	 */
	private void saveTransport(MedicalPlan plan) {
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
		plan.setTransportServices(services);
	}

	/**
	 * Saves hospital rows with human-entered capabilities and travel-time provenance.
	 *
	 * @param plan medical plan.
	 */
	private void saveHospitals(MedicalPlan plan) {
		List<MedicalPlan.Hospital> hospitals = new ArrayList<>();
		boolean invalidCoordinates = false;
		for (int row = 0; row < hospitalModel.getRowCount(); row++) {
			MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
			hospital.setName(cell(hospitalModel, row, 0));
			hospital.setAddress(cell(hospitalModel, row, 1));
			hospital.setContactNumbers(cell(hospitalModel, row, 2));
			hospital.setFrequency(cell(hospitalModel, row, 3));
			Double latitude = parseCoordinate(cell(hospitalModel, row, 4), -90, 90);
			Double longitude = parseCoordinate(cell(hospitalModel, row, 5), -180, 180);
			if ((!cell(hospitalModel, row, 4).isBlank() && latitude == null)
					|| (!cell(hospitalModel, row, 5).isBlank() && longitude == null)) {
				invalidCoordinates = true;
			}
			hospital.setLatitude(latitude);
			hospital.setLongitude(longitude);
			hospital.setCoordinateFormat(cell(hospitalModel, row, 6));
			hospital.setHelipadCoordinates(cell(hospitalModel, row, 7));
			hospital.setAirTravelTime(cell(hospitalModel, row, 8));
			hospital.setAirTravelProvenance(cell(hospitalModel, row, 9));
			hospital.setGroundTravelTime(cell(hospitalModel, row, 10));
			hospital.setGroundTravelProvenance(cell(hospitalModel, row, 11));
			hospital.setTraumaCenter(parseStatus(cell(hospitalModel, row, 12)));
			hospital.setTraumaLevel(cell(hospitalModel, row, 13));
			hospital.setBurnCenter(parseStatus(cell(hospitalModel, row, 14)));
			hospital.setHelipad(parseStatus(cell(hospitalModel, row, 15)));
			hospitals.add(hospital);
		}
		plan.setHospitals(hospitals);
		validation.setText(invalidCoordinates ? "Hospital coordinates must use valid latitude/longitude ranges." : " ");
	}

	/**
	 * Parses a preparation timestamp without clearing the prior value on invalid input.
	 *
	 * @param plan medical plan.
	 */
	private void setPreparedAt(MedicalPlan plan) {
		String text = preparedAt.getText().trim();
		if (text.isEmpty()) {
			plan.setPreparedAt(null);
			return;
		}
		try {
			plan.setPreparedAt(LocalDateTime.parse(text));
		} catch (RuntimeException exception) {
			validation.setText("Preparation date/time must use yyyy-MM-ddTHH:mm.");
		}
	}

	/**
	 * Converts a nullable capability into a readable table choice.
	 *
	 * @param value nullable capability.
	 * @return Yes, No, or Unknown.
	 */
	private static String status(Boolean value) {
		return value == null ? UNKNOWN : value ? "Yes" : "No";
	}

	/**
	 * Parses a table capability without treating blank text as No.
	 *
	 * @param value table text.
	 * @return nullable boolean.
	 */
	private static Boolean parseStatus(String value) {
		if ("yes".equalsIgnoreCase(value)) {
			return true;
		}
		if ("no".equalsIgnoreCase(value)) {
			return false;
		}
		return null;
	}

	/**
	 * Parses one coordinate if it is finite and within its geographic range.
	 *
	 * @param value table text.
	 * @param minimum minimum value.
	 * @param maximum maximum value.
	 * @return parsed coordinate or null when empty/invalid.
	 */
	private static Double parseCoordinate(String value, double minimum, double maximum) {
		try {
			double coordinate = Double.parseDouble(value);
			return Double.isFinite(coordinate) && coordinate >= minimum && coordinate <= maximum ? coordinate : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	/**
	 * Returns one table cell as non-null text.
	 *
	 * @param model source table model.
	 * @param row row index.
	 * @param column column index.
	 * @return normalized cell text.
	 */
	private static String cell(DefaultTableModel model, int row, int column) {
		Object value = model.getValueAt(row, column);
		return value == null ? "" : value.toString();
	}

	/**
	 * Returns the display value for a nullable coordinate.
	 *
	 * @param value coordinate.
	 * @return coordinate text or blank.
	 */
	private static String value(Double value) {
		return value == null ? "" : value.toString();
	}

	/**
	 * Creates an editable table model with the supplied headings.
	 *
	 * @param columns table headings.
	 * @return editable table model.
	 */
	private static DefaultTableModel tableModel(String... columns) {
		return new DefaultTableModel(columns, 0);
	}
}
