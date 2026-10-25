/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics208Form;
import org.sarmanagement.icsforms.model.Ics215aForm;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyHazardReport;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.SiteSafetyPlan;
import org.sarmanagement.icsforms.safety.SafetyHazardCsvService;
import org.sarmanagement.icsforms.safety.SafetyHazardLibraryService;
import org.sarmanagement.icsforms.safety.SafetyMessageComposer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.table.DefaultTableModel;

import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Shared safety workspace for the ICS 215A, ICS 208, and SAR hazard reports.
 */
public class SafetyPanel extends JPanel {
	private final AppController controller;
	private final SafetyHazardCsvService csvService = new SafetyHazardCsvService();
	private final SafetyHazardLibraryService libraryService = new SafetyHazardLibraryService();
	private final SafetyMessageComposer composer = new SafetyMessageComposer();
	private final JTabbedPane sections = new JTabbedPane();
	private final JTextField incidentNumberField = new JTextField();
	private final JTextField safetyOfficerField = new JTextField();
	private final JTextField operationsChiefField = new JTextField();
	private final JTextField analysisPreparedField = new JTextField();
	private final JLabel hazardReportCoverage = new JLabel("No SAR-reported hazards to account for.");
	private final JPanel reportedHazardsPanel;
	private final JPanel messagePanel;
	private final DefaultTableModel analysisTableModel = new DefaultTableModel(
			new String[] {"Incident Area", "Hazards / Risks", "Mitigations"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JTable analysisTable = new JTable(analysisTableModel);
	private final JComboBox<Ics208Form.AuthorRole> authorRole = new JComboBox<>(Ics208Form.AuthorRole.values());
	private final JComboBox<Ics208Form.MessageMode> messageMode = new JComboBox<>(Ics208Form.MessageMode.values());
	private final JTextArea freeText = textArea(5);
	private final JTextArea generalRequirements = textArea(3);
	private final DefaultTableModel messageTableModel = new DefaultTableModel(
			new String[] {"Area", "Hazard", "Precautions", "Review"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JTable messageTable = new JTable(messageTableModel);
	private final JComboBox<String> sitePlanRequired = new JComboBox<>(new String[] {"Not answered", "Yes", "No"});
	private final JTextField sitePlanName = new JTextField();
	private final JTextField sitePlanLocation = new JTextField();
	private final JTextField sitePlanRevision = new JTextField();
	private final JTextField sitePlanPreparer = new JTextField();
	private final JTextField sitePlanPreparerTitle = new JTextField();
	private final JTextField sitePlanSignature = new JTextField();
	private final JTextField sitePlanPreparedDateTime = new JTextField();
	private final JTextField preparedBy = new JTextField();
	private final JTextField preparedByPosition = new JTextField();
	private final JTextField preparedBySignature = new JTextField();
	private final JTextField preparedDateTime = new JTextField();
	private final JLabel messageCoverage = new JLabel("No analysis rows.");
	private final Map<String, JTextArea> siteTextAreas = new LinkedHashMap<>();
	private final DefaultTableModel reportTableModel = new DefaultTableModel(
			new String[] {"Operational Period", "Task", "Area", "Debrief Status", "Safety Review", "Reported Hazards"}, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JTable reportTable = new JTable(reportTableModel);
	private final List<HazardReportSource> reportSources = new ArrayList<>();
	private Consumer<String> exportRequest = this::exportPdf;

	/**
	 * Creates the shared Safety workspace.
	 *
	 * @param controller application controller.
	 */
	public SafetyPanel(AppController controller) {
		super(new BorderLayout());
		this.controller = controller;
		sections.addTab("ICS 215A Analysis", buildAnalysisPanel());
		reportedHazardsPanel = buildReportsPanel();
		messagePanel = buildMessagePanel();
		sections.addTab("Reported SAR Hazards", reportedHazardsPanel);
		sections.addTab("ICS 208 Message / Plan", messagePanel);
		configureReportedHazardsTab(controller.getIncidentMode() == IncidentMode.SAR);
		add(new PlanReviewStrip(controller, org.sarmanagement.icsforms.model.PlanReviewArea.SAFETY,
				this::pushToModel), BorderLayout.NORTH);
		add(sections, BorderLayout.CENTER);
	}

	/**
	 * Sets the handler used to open the shared forms export dialog.
	 *
	 * @param exportRequest callback receiving the requested form key.
	 */
	public void setExportRequest(Consumer<String> exportRequest) {
		this.exportRequest = exportRequest == null ? this::exportPdf : exportRequest;
	}

	/**
	 * Shows the reported-hazards subtab only for SAR incidents and keeps it between the forms.
	 *
	 * @param visible whether this incident is in SAR mode.
	 */
	private void configureReportedHazardsTab(boolean visible) {
		Component selected = sections.getSelectedComponent();
		int index = sections.indexOfComponent(reportedHazardsPanel);
		if (visible && index < 0) {
			sections.insertTab("Reported SAR Hazards", null, reportedHazardsPanel, null, 1);
		} else if (!visible && index >= 0) {
			sections.removeTabAt(index);
		}
		hazardReportCoverage.setVisible(visible);
		int selectedIndex = sections.indexOfComponent(selected);
		if (selectedIndex >= 0) {
			sections.setSelectedIndex(selectedIndex);
		}
	}

	/**
	 * Loads safety form values into the panel.
	 */
	public void refreshFromModel() {
		AppData data = controller.getData();
		Ics215aForm analysis = data.getForm215a();
		incidentNumberField.setText(analysis.getIncidentNumber());
		safetyOfficerField.setText(analysis.getSafetyOfficerName());
		operationsChiefField.setText(analysis.getOperationsSectionChiefName());
		analysisPreparedField.setText(isoDateTime(analysis.getPreparedDateTime()));
		analysisTableModel.setRowCount(0);
		for (SafetyAnalysisEntry entry : analysis.getEntries()) {
			analysisTableModel.addRow(new Object[] {entry.getIncidentArea(), entry.getHazardsRisks(), entry.getMitigations()});
		}
		Ics208Form message = data.getForm208();
		authorRole.setSelectedItem(message.getAuthorRole());
		messageMode.setSelectedItem(message.getMessageMode());
		freeText.setText(message.getFreeText());
		generalRequirements.setText(message.getGeneralRequirements());
		messageTableModel.setRowCount(0);
		for (SafetyMessageBlock block : message.getHazardBlocks()) {
			messageTableModel.addRow(new Object[] {block.getIncidentArea(), block.getHazard(), block.getPrecautions(),
					block.isNeedsReview() ? "Review" : ""});
		}
		sitePlanRequired.setSelectedItem(message.getSiteSafetyPlanRequired() == null ? "Not answered"
				: message.getSiteSafetyPlanRequired() ? "Yes" : "No");
		SiteSafetyPlan plan = message.getSiteSafetyPlan();
		sitePlanName.setText(plan.getPlanName());
		sitePlanLocation.setText(plan.getLocatedAt());
		preparedBy.setText(message.getPreparedByName());
		preparedByPosition.setText(message.getPreparedByPositionTitle());
		preparedBySignature.setText(message.getPreparedBySignature());
		preparedDateTime.setText(isoDateTime(message.getPreparedDateTime()));
		siteTextAreas.clear();
		sitePlanRevision.setText(plan.getRevision());
		sitePlanPreparer.setText(plan.getPreparedByName());
		sitePlanPreparerTitle.setText(plan.getPreparedByPositionTitle());
		sitePlanSignature.setText(plan.getPreparedBySignature());
		sitePlanPreparedDateTime.setText(isoDateTime(plan.getPreparedDateTime()));
		buildSiteSections(plan.getTextSections());
		refreshCoverage(data);
		refreshReportedHazards(data);
		configureReportedHazardsTab(data.getIncidentMode() == IncidentMode.SAR);
	}

	/**
	 * Writes panel values to the active incident.
	 */
	public void pushToModel() {
		AppData data = controller.getData();
		Ics215aForm analysis = data.getForm215a();
		analysis.setIncidentNumber(incidentNumberField.getText().trim());
		analysis.setSafetyOfficerName(safetyOfficerField.getText().trim());
		analysis.setOperationsSectionChiefName(operationsChiefField.getText().trim());
		analysis.setPreparedDateTime(parseDateTime(analysisPreparedField.getText()));
		List<SafetyAnalysisEntry> entries = new ArrayList<>();
		for (int i = 0; i < analysisTableModel.getRowCount(); i++) {
			SafetyAnalysisEntry entry = i < analysis.getEntries().size() ? analysis.getEntries().get(i)
					: new SafetyAnalysisEntry();
			entry.setIncidentArea(textAt(analysisTableModel, i, 0));
			entry.setHazardsRisks(textAt(analysisTableModel, i, 1));
			entry.setMitigations(textAt(analysisTableModel, i, 2));
			entries.add(entry);
		}
		analysis.setEntries(entries);

		Ics208Form message = data.getForm208();
		message.setAuthorRole((Ics208Form.AuthorRole) authorRole.getSelectedItem());
		message.setMessageMode((Ics208Form.MessageMode) messageMode.getSelectedItem());
		message.setMessageType(message.getAuthorRole() == Ics208Form.AuthorRole.HAZARDOUS_MATERIALS_GROUP_SUPERVISOR
				? Ics208Form.MessageType.HAZARDOUS_MATERIALS_SITE_SAFETY_PLAN
				: Ics208Form.MessageType.SAFETY_MESSAGE_PLAN);
		message.setFreeText(freeText.getText());
		message.setGeneralRequirements(generalRequirements.getText());
		List<SafetyMessageBlock> blocks = new ArrayList<>();
		for (int i = 0; i < messageTableModel.getRowCount(); i++) {
			SafetyMessageBlock block = i < message.getHazardBlocks().size() ? message.getHazardBlocks().get(i)
					: new SafetyMessageBlock();
			block.setIncidentArea(textAt(messageTableModel, i, 0));
			block.setHazard(textAt(messageTableModel, i, 1));
			block.setPrecautions(textAt(messageTableModel, i, 2));
			blocks.add(block);
		}
		message.setHazardBlocks(blocks);
		String answer = (String) sitePlanRequired.getSelectedItem();
		message.setSiteSafetyPlanRequired("Yes".equals(answer) ? Boolean.TRUE : "No".equals(answer) ? Boolean.FALSE : null);
		SiteSafetyPlan plan = message.getSiteSafetyPlan();
		plan.setPlanName(sitePlanName.getText().trim());
		plan.setLocatedAt(sitePlanLocation.getText().trim());
		plan.setRevision(sitePlanRevision.getText().trim());
		plan.setPreparedByName(sitePlanPreparer.getText().trim());
		plan.setPreparedByPositionTitle(sitePlanPreparerTitle.getText().trim());
		plan.setPreparedBySignature(sitePlanSignature.getText().trim());
		plan.setPreparedDateTime(parseDateTime(sitePlanPreparedDateTime.getText()));
		plan.setTextSections(readSiteSections());
		message.setPreparedByName(preparedBy.getText().trim());
		message.setPreparedByPositionTitle(preparedByPosition.getText().trim());
		message.setPreparedBySignature(preparedBySignature.getText().trim());
		message.setPreparedDateTime(parseDateTime(preparedDateTime.getText()));
		refreshReportedHazards(data);
	}

	/**
	 * Builds the ICS 215A editor and row-management controls.
	 *
	 * @return analysis editor panel.
	 */
	private JPanel buildAnalysisPanel() {
		JPanel panel = new JPanel(new BorderLayout(6, 6));
		JPanel metadata = UiSupport.formPanel();
		UiSupport.addRow(metadata, 0, "Incident number", incidentNumberField);
		UiSupport.addRow(metadata, 1, "Safety Officer", safetyOfficerField);
		UiSupport.addRow(metadata, 2, "Operations Section Chief", operationsChiefField);
		UiSupport.addRow(metadata, 3, "Prepared (yyyy-MM-dd'T'HH:mm)", analysisPreparedField);
		UiSupport.addRow(metadata, 4, "SAR hazard report coverage", hazardReportCoverage);
		analysisTable.setFillsViewportHeight(true);
		JPanel actions = new JPanel();
		button(actions, "Add hazard", () -> editAnalysisRow(-1));
		button(actions, "Edit selected", () -> editAnalysisRow(analysisTable.getSelectedRow()));
		button(actions, "Move up", () -> moveAnalysisRow(-1));
		button(actions, "Move down", () -> moveAnalysisRow(1));
		button(actions, "Remove selected", this::removeAnalysisRow);
		button(actions, "Load common CSV", this::importCsv);
		button(actions, "Add common item", this::addCommonHazard);
		button(actions, "Save common library CSV", this::exportCommonCsv);
		button(actions, "Save analysis as CSV", this::exportAnalysisCsv);
		button(actions, "Export ICS 215A PDF", () -> exportRequest.accept("ICS 215A"));
		installDoubleClickEditor(analysisTable, this::editAnalysisRow);
		panel.add(metadata, BorderLayout.NORTH);
		panel.add(new JScrollPane(analysisTable), BorderLayout.CENTER);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Builds the ICS 208 message editor, site-plan metadata, and extensible sections.
	 *
	 * @return message editor panel.
	 */
	private JPanel buildMessagePanel() {
		JPanel panel = new JPanel(new BorderLayout(6, 6));
		JPanel controls = UiSupport.formPanel();
		UiSupport.addRow(controls, 0, "Authoring role", authorRole);
		UiSupport.addRow(controls, 1, "Message mode", messageMode);
		UiSupport.addRow(controls, 2, "General requirements", new JScrollPane(generalRequirements));
		UiSupport.addRow(controls, 3, "Free-text message", new JScrollPane(freeText));
		UiSupport.addRow(controls, 4, "Site Safety Plan required?", sitePlanRequired);
		UiSupport.addRow(controls, 5, "Approved plan name", sitePlanName);
		UiSupport.addRow(controls, 6, "Approved plan located at", sitePlanLocation);
		UiSupport.addRow(controls, 7, "Prepared by", preparedBy);
		UiSupport.addRow(controls, 8, "Position/title", preparedByPosition);
		UiSupport.addRow(controls, 9, "Signature", preparedBySignature);
		UiSupport.addRow(controls, 10, "Prepared (yyyy-MM-dd'T'HH:mm)", preparedDateTime);
		UiSupport.addRow(controls, 11, "Site plan revision", sitePlanRevision);
		UiSupport.addRow(controls, 12, "Site plan prepared by", sitePlanPreparer);
		UiSupport.addRow(controls, 13, "Site plan preparer position", sitePlanPreparerTitle);
		UiSupport.addRow(controls, 14, "Site plan signature", sitePlanSignature);
		UiSupport.addRow(controls, 15, "Site plan prepared (yyyy-MM-dd'T'HH:mm)", sitePlanPreparedDateTime);
		UiSupport.addRow(controls, 16, "Message coverage", messageCoverage);
		JPanel blocks = new JPanel(new BorderLayout(4, 4));
		blocks.setBorder(BorderFactory.createTitledBorder("Hazard and precautions blocks"));
		messageTable.setFillsViewportHeight(true);
		blocks.add(new JScrollPane(messageTable), BorderLayout.CENTER);
		JPanel blockActions = new JPanel();
		button(blockActions, "Add block", () -> editMessageBlock(-1));
		button(blockActions, "Edit block", () -> editMessageBlock(messageTable.getSelectedRow()));
		button(blockActions, "Remove block", this::removeMessageBlock);
		button(blockActions, "Generate/update from 215A", this::generateMessage);
		button(blockActions, "Export ICS 208 PDF", () -> exportRequest.accept("ICS 208"));
		installDoubleClickEditor(messageTable, this::editMessageBlock);
		blocks.add(blockActions, BorderLayout.SOUTH);
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(controls), blocks);
		split.setResizeWeight(0.5);
		panel.add(split, BorderLayout.CENTER);
		panel.add(buildSiteSectionsPanel(), BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Opens a table row editor when a user double-clicks a valid row.
	 *
	 * @param table table with selectable rows.
	 * @param editor callback receiving the selected row index.
	 * @return installed mouse listener.
	 */
	static MouseAdapter installDoubleClickEditor(JTable table, IntConsumer editor) {
		MouseAdapter listener = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(event)) {
					int row = table.rowAtPoint(event.getPoint());
					if (row >= 0) {
						table.setRowSelectionInterval(row, row);
						editor.accept(row);
					}
				}
			}
		};
		table.addMouseListener(listener);
		return listener;
	}

	/**
	 * Builds the optional SAR-only source report table.
	 *
	 * @return SAR hazard report panel.
	 */
	private JPanel buildReportsPanel() {
		JPanel panel = new JPanel(new BorderLayout(6, 6));
		reportTable.setFillsViewportHeight(true);
		JButton promote = new JButton("Add selected report to ICS 215A");
		promote.addActionListener(event -> promoteHazardReport());
		panel.add(new JLabel("Reported hazards from all operational periods. Link each report to the 215A or record "
				+ "that the Safety Officer has noted it."), BorderLayout.NORTH);
		panel.add(new JScrollPane(reportTable), BorderLayout.CENTER);
		JPanel actions = new JPanel();
		actions.add(promote);
		button(actions, "Mark noted by Safety Officer", this::acknowledgeHazardReport);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * Builds a dynamic named-text section area for the optional site safety plan.
	 *
	 * @return site-plan sections panel.
	 */
	private JPanel buildSiteSectionsPanel() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setBorder(BorderFactory.createTitledBorder("Site Safety Plan text sections"));
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		JPanel controls = new JPanel(new BorderLayout());
		JButton add = new JButton("Add text area");
		add.addActionListener(event -> addSiteSection("", ""));
		controls.add(add, BorderLayout.WEST);
		panel.add(controls, BorderLayout.NORTH);
		panel.add(new JScrollPane(body), BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Rebuilds named site-plan text areas from stored section values.
	 *
	 * @param savedSections previously saved sections.
	 */
	private void buildSiteSections(Map<String, String> savedSections) {
		for (int index = 0; index < sections.getTabCount(); index++) {
			if ("ICS 208 Message / Plan".equals(sections.getTitleAt(index))) {
				JPanel page = (JPanel) sections.getComponentAt(index);
				JPanel south = (JPanel) ((BorderLayout) page.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
				JScrollPane scroll = (JScrollPane) ((BorderLayout) south.getLayout()).getLayoutComponent(BorderLayout.CENTER);
				JPanel body = (JPanel) scroll.getViewport().getView();
				body.removeAll();
				body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
				if (savedSections != null) {
					savedSections.forEach(this::addSiteSection);
				}
				body.revalidate();
				body.repaint();
				return;
			}
		}
	}

	/**
	 * Adds one named additional site-plan text area.
	 *
	 * @param name stored section name, or blank for a new section.
	 * @param value section text.
	 */
	private void addSiteSection(String name, String value) {
		JPanel parent = messagePanel;
		JPanel south = (JPanel) ((BorderLayout) parent.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
		JScrollPane scroll = (JScrollPane) ((BorderLayout) south.getLayout()).getLayoutComponent(BorderLayout.CENTER);
		JPanel sectionBody = (JPanel) scroll.getViewport().getView();
		String title = name == null || name.isBlank() ? "Additional section " + (siteTextAreas.size() + 1) : name;
		JPanel section = new JPanel(new BorderLayout(4, 4));
		JTextField heading = new JTextField(title);
		JTextArea text = textArea(3);
		text.setText(value == null ? "" : value);
		section.add(heading, BorderLayout.NORTH);
		section.add(new JScrollPane(text), BorderLayout.CENTER);
		JButton remove = new JButton("Remove");
		remove.addActionListener(event -> {
			siteTextAreas.remove(heading.getText());
			sectionBody.remove(section);
			sectionBody.revalidate();
			sectionBody.repaint();
		});
		section.add(remove, BorderLayout.EAST);
		sectionBody.add(section);
		sectionBody.add(Box.createVerticalStrut(4));
		siteTextAreas.put(title, text);
	}

	/**
	 * Reads the currently displayed named site-plan sections.
	 *
	 * @return section names and text.
	 */
	private Map<String, String> readSiteSections() {
		Map<String, String> sectionsByName = new LinkedHashMap<>();
		JPanel parent = messagePanel;
		JPanel south = (JPanel) ((BorderLayout) parent.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
		JScrollPane scroll = (JScrollPane) ((BorderLayout) south.getLayout()).getLayoutComponent(BorderLayout.CENTER);
		JPanel body = (JPanel) scroll.getViewport().getView();
		for (java.awt.Component component : body.getComponents()) {
			if (component instanceof JPanel section && section.getComponentCount() >= 2
					&& section.getComponent(0) instanceof JTextField heading) {
				JScrollPane sectionScroll = (JScrollPane) section.getComponent(1);
				JTextArea content = (JTextArea) sectionScroll.getViewport().getView();
				sectionsByName.put(heading.getText().trim(), content.getText());
			}
		}
		return sectionsByName;
	}

	/**
	 * Opens an editor for adding or changing one analysis row.
	 *
	 * @param selected selected table row, or -1 to add.
	 */
	private void editAnalysisRow(int selected) {
		SafetyAnalysisEntry entry = selected >= 0 ? controller.getData().getForm215a().getEntries().get(selected)
				: new SafetyAnalysisEntry();
		JTextField area = new JTextField(entry.getIncidentArea());
		JTextField hazard = new JTextField(entry.getHazardsRisks());
		JTextArea mitigation = textArea(6);
		mitigation.setText(entry.getMitigations());
		JTextArea equipment = textArea(3);
		equipment.setText(entry.getEquipmentPpe());
		JPanel form = UiSupport.formPanel();
		UiSupport.addRow(form, 0, "Incident area", area);
		UiSupport.addRow(form, 1, "Hazards / risks", hazard);
		UiSupport.addRow(form, 2, "Mitigations", new JScrollPane(mitigation));
		UiSupport.addRow(form, 3, "Equipment / PPE (explicit only)", new JScrollPane(equipment));
		if (JOptionPane.showConfirmDialog(this, form, "ICS 215A Hazard", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		entry.setIncidentArea(area.getText());
		entry.setHazardsRisks(hazard.getText());
		entry.setMitigations(mitigation.getText());
		entry.setEquipmentPpe(equipment.getText());
		if (selected >= 0) {
			controller.getData().getForm215a().getEntries().set(selected, entry);
			analysisTableModel.setValueAt(entry.getIncidentArea(), selected, 0);
			analysisTableModel.setValueAt(entry.getHazardsRisks(), selected, 1);
			analysisTableModel.setValueAt(entry.getMitigations(), selected, 2);
		} else {
			controller.getData().getForm215a().getEntries().add(entry);
			analysisTableModel.addRow(new Object[] {entry.getIncidentArea(), entry.getHazardsRisks(), entry.getMitigations()});
		}
		controller.markDirty();
		refreshCoverage(controller.getData());
	}

	/**
	 * Removes the selected analysis row.
	 */
	private void removeAnalysisRow() {
		int row = analysisTable.getSelectedRow();
		if (row >= 0) {
			controller.getData().getForm215a().getEntries().remove(row);
			analysisTableModel.removeRow(row);
			controller.markDirty();
			refreshCoverage(controller.getData());
		}
	}

	/**
	 * Reorders an analysis row and its corresponding table representation.
	 *
	 * @param offset row movement, normally -1 or 1.
	 */
	private void moveAnalysisRow(int offset) {
		int row = analysisTable.getSelectedRow();
		int destination = row + offset;
		List<SafetyAnalysisEntry> entries = controller.getData().getForm215a().getEntries();
		if (row < 0 || destination < 0 || destination >= entries.size()) {
			return;
		}
		SafetyAnalysisEntry selected = entries.remove(row);
		entries.add(destination, selected);
		Object[] values = new Object[analysisTableModel.getColumnCount()];
		for (int column = 0; column < values.length; column++) {
			values[column] = analysisTableModel.getValueAt(row, column);
		}
		analysisTableModel.removeRow(row);
		analysisTableModel.insertRow(destination, values);
		analysisTable.setRowSelectionInterval(destination, destination);
		controller.markDirty();
		refreshCoverage(controller.getData());
	}

	/**
	 * Opens a file picker and imports common hazard rows with an explicit merge policy.
	 */
	private void importCsv() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			List<SafetyAnalysisEntry> imported = csvService.read(chooser.getSelectedFile().toPath());
			Object[] options = {"Store library only", "Append to analysis", "Merge identical", "Replace analysis"};
			int choice = JOptionPane.showOptionDialog(this, "Load as a common library or apply rows to this analysis?",
					"Load common hazards",
					JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (choice < 0) {
				return;
			}
			if (choice == 0) {
				libraryService.storeCommonLibrary(controller.getData(), imported);
				controller.markDirty();
				return;
			}
			List<SafetyAnalysisEntry> current = controller.getData().getForm215a().getEntries();
			if (choice == 3) {
				current.clear();
				analysisTableModel.setRowCount(0);
			}
			for (SafetyAnalysisEntry item : imported) {
				boolean duplicate = current.stream().anyMatch(existing -> existing.getHazardsRisks().equals(item.getHazardsRisks())
						&& existing.getMitigations().equals(item.getMitigations())
						&& existing.getIncidentArea().equals(item.getIncidentArea())
						&& existing.getEquipmentPpe().equals(item.getEquipmentPpe()));
				if (choice != 2 || !duplicate) {
					current.add(item);
					analysisTableModel.addRow(new Object[] {item.getIncidentArea(), item.getHazardsRisks(), item.getMitigations()});
				}
			}
			controller.markDirty();
			refreshCoverage(controller.getData());
		} catch (IOException | IllegalArgumentException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Could not load hazard library",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Exports current analysis rows as a reusable common-hazards CSV library.
	 */
	private void exportCommonCsv() {
		exportCsv(controller.getData().getForm215a().getEntries(), true);
	}

	/**
	 * Saves the active analysis rows as a reusable CSV library.
	 */
	private void exportAnalysisCsv() {
		exportCsv(controller.getData().getForm215a().getEntries(), false);
	}

	/**
	 * Writes the selected reusable entries to a user-selected CSV path.
	 *
	 * @param entries rows to save.
	 */
	private void exportCsv(List<SafetyAnalysisEntry> entries, boolean saveAsCommonLibrary) {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			if (java.nio.file.Files.exists(chooser.getSelectedFile().toPath())
					&& JOptionPane.showConfirmDialog(this, "Replace the existing CSV file?", "Confirm overwrite",
							JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
				return;
			}
			if (saveAsCommonLibrary) {
				libraryService.saveAnalysisAsCommonLibrary(controller.getData(), chooser.getSelectedFile().toPath());
				controller.markDirty();
			} else {
				csvService.write(chooser.getSelectedFile().toPath(), entries);
			}
		} catch (IOException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Could not save hazard library",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Adds one selected common library entry as an independent analysis row.
	 */
	private void addCommonHazard() {
		List<SafetyAnalysisEntry> library = controller.getData().getCommonSafetyHazards();
		if (library.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Load a common hazards CSV library first.", "No common hazards",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String[] labels = library.stream()
				.map(entry -> entry.getIncidentArea() + " — " + entry.getHazardsRisks()).toArray(String[]::new);
		Object selected = JOptionPane.showInputDialog(this, "Select a common hazard to add to this analysis.",
				"Add common hazard", JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
		if (selected == null) {
			return;
		}
		SafetyAnalysisEntry source = library.get(java.util.Arrays.asList(labels).indexOf(selected));
		SafetyAnalysisEntry entry = copyEntry(source);
		controller.getData().getForm215a().getEntries().add(entry);
		analysisTableModel.addRow(new Object[] {entry.getIncidentArea(), entry.getHazardsRisks(), entry.getMitigations()});
		controller.markDirty();
		refreshCoverage(controller.getData());
	}

	/**
	 * Creates an incident-owned copy of a reusable library row.
	 *
	 * @param source reusable row.
	 * @return independent row with a new stable identifier.
	 */
	private static SafetyAnalysisEntry copyEntry(SafetyAnalysisEntry source) {
		SafetyAnalysisEntry copy = new SafetyAnalysisEntry();
		copy.setIncidentArea(source.getIncidentArea());
		copy.setHazardsRisks(source.getHazardsRisks());
		copy.setMitigations(source.getMitigations());
		copy.setEquipmentPpe(source.getEquipmentPpe());
		return copy;
	}

	/**
	 * Creates or updates the message draft while retaining edited blocks.
	 */
	private void generateMessage() {
		pushToModel();
		composer.updateDraft(controller.getData().getForm215a(), controller.getData().getForm208());
		refreshFromModel();
		controller.markDirty();
	}

	/**
	 * Opens a dialog to add or edit a message hazard block.
	 *
	 * @param selected selected row, or -1 to add.
	 */
	private void editMessageBlock(int selected) {
		SafetyMessageBlock block = selected >= 0 ? controller.getData().getForm208().getHazardBlocks().get(selected)
				: new SafetyMessageBlock();
		JTextField area = new JTextField(block.getIncidentArea());
		JTextField hazard = new JTextField(block.getHazard());
		JTextArea precautions = textArea(6);
		precautions.setText(block.getPrecautions());
		JPanel form = UiSupport.formPanel();
		UiSupport.addRow(form, 0, "Incident area", area);
		UiSupport.addRow(form, 1, "Hazard", hazard);
		UiSupport.addRow(form, 2, "Precautions", new JScrollPane(precautions));
		if (JOptionPane.showConfirmDialog(this, form, "ICS 208 Hazard Block",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
			return;
		}
		block.setIncidentArea(area.getText());
		block.setHazard(hazard.getText());
		block.setPrecautions(precautions.getText());
		block.setNeedsReview(false);
		if (selected >= 0) {
			controller.getData().getForm208().getHazardBlocks().set(selected, block);
			messageTableModel.setValueAt(block.getIncidentArea(), selected, 0);
			messageTableModel.setValueAt(block.getHazard(), selected, 1);
			messageTableModel.setValueAt(block.getPrecautions(), selected, 2);
		} else {
			controller.getData().getForm208().getHazardBlocks().add(block);
			messageTableModel.addRow(new Object[] {block.getIncidentArea(), block.getHazard(), block.getPrecautions(), ""});
		}
		controller.markDirty();
	}

	/**
	 * Removes the selected ICS 208 hazard block.
	 */
	private void removeMessageBlock() {
		int row = messageTable.getSelectedRow();
		if (row >= 0) {
			controller.getData().getForm208().getHazardBlocks().remove(row);
			messageTableModel.removeRow(row);
			controller.markDirty();
		}
	}

	/**
	 * Reports the number of analysis rows represented by linked message blocks.
	 *
	 * @param data incident data.
	 */
	private void refreshCoverage(AppData data) {
		List<String> represented = controller.getData().getForm208().getHazardBlocks().stream()
				.flatMap(block -> block.getSourceEntryIds().stream()).distinct().toList();
		long stale = controller.getData().getForm208().getHazardBlocks().stream()
				.filter(SafetyMessageBlock::isNeedsReview).count();
		messageCoverage.setText(represented.size() + " of " + data.getForm215a().getEntries().size()
				+ " analysis rows represented; " + stale + " block(s) need review.");
		refreshHazardReportCoverage();
	}

	/**
	 * Adds the selected SAR hazard report to analysis without inventing mitigations.
	 */
	private void promoteHazardReport() {
		int row = reportTable.getSelectedRow();
		if (row < 0) {
			return;
		}
		HazardReportSource source = reportSources.get(row);
		if (isHazardLinkedToAnalysis(source)) {
			JOptionPane.showMessageDialog(this, "This report is already linked to an ICS 215A row.",
					"Hazard already included", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		SafetyAnalysisEntry entry = new SafetyAnalysisEntry();
		entry.setIncidentArea(source.incidentArea());
		entry.setHazardsRisks(source.hazards());
		controller.getData().getForm215a().getEntries().add(entry);
		analysisTableModel.addRow(new Object[] {entry.getIncidentArea(), entry.getHazardsRisks(), ""});
		if (source.task() != null) {
			source.task().setSafetyHazardAnalysisEntryId(entry.getEntryId());
		} else {
			source.archivedReport().setAnalysisEntryId(entry.getEntryId());
		}
		controller.markDirty();
		refreshReportedHazards(controller.getData());
		sections.setSelectedIndex(0);
	}

	/**
	 * Records the Safety Officer's acknowledgement for the selected report.
	 */
	private void acknowledgeHazardReport() {
		int row = reportTable.getSelectedRow();
		if (row < 0) {
			return;
		}
		String defaultName = safetyOfficerField.getText().trim();
		String name = JOptionPane.showInputDialog(this, "Safety Officer noting this report:",
				defaultName.isBlank() ? "" : defaultName);
		if (name == null || name.isBlank()) {
			return;
		}
		HazardReportSource source = reportSources.get(row);
		if (source.task() != null) {
			source.task().setSafetyHazardNotedBy(name.trim());
			source.task().setSafetyHazardNotedDateTime(LocalDateTime.now());
		} else {
			source.archivedReport().setNotedBySafetyOfficer(name.trim());
			source.archivedReport().setNotedDateTime(LocalDateTime.now());
		}
		controller.markDirty();
		refreshReportedHazards(controller.getData());
		reportTable.setRowSelectionInterval(row, row);
	}

	/**
	 * Refreshes the SAR report list from all nonblank debrief hazard reports.
	 *
	 * @param data active incident.
	 */
	private void refreshReportedHazards(AppData data) {
		reportTableModel.setRowCount(0);
		reportSources.clear();
		if (data.getIncidentMode() != IncidentMode.SAR) {
			refreshHazardReportCoverage();
			return;
		}
		for (SafetyHazardReport report : data.getArchivedSafetyHazardReports()) {
			if (report != null && !report.getHazards().isBlank()) {
				HazardReportSource source = new HazardReportSource(null, report);
				reportSources.add(source);
				reportTableModel.addRow(new Object[] {periodLabel(report.getPeriodStart(), report.getPeriodEnd()),
						report.getTaskLabel(), report.getIncidentArea(),
						report.isDebriefingCompleted() ? "Completed" : "Unfinished",
						reportReviewStatus(source), report.getHazards()});
			}
		}
		LocalDateTime periodStart = data.getIncidentContext().getOperationalPeriodStart();
		LocalDateTime periodEnd = data.getIncidentContext().getOperationalPeriodEnd();
		for (SarTaskAssignment task : data.getSarTaskAssignments()) {
			if (task != null && task.getHazardsObserved() != null && !task.getHazardsObserved().isBlank()) {
				HazardReportSource source = new HazardReportSource(task, null);
				reportSources.add(source);
				reportTableModel.addRow(new Object[] {periodLabel(periodStart, periodEnd), taskLabel(task),
						reportArea(task), task.isDebriefingCompleted() ? "Completed" : "Unfinished",
						reportReviewStatus(source), task.getHazardsObserved()});
			}
		}
		refreshHazardReportCoverage();
	}

	/**
	 * Summarizes whether each SAR-reported hazard is linked to an analysis entry or acknowledged.
	 */
	private void refreshHazardReportCoverage() {
		int accounted = 0;
		for (HazardReportSource source : reportSources) {
			String notedBy = source.task() == null
					? source.archivedReport().getNotedBySafetyOfficer()
					: source.task().getSafetyHazardNotedBy();
			if (isHazardLinkedToAnalysis(source) || !notedBy.isBlank()) {
				accounted++;
			}
		}
		int total = reportSources.size();
		if (total == 0) {
			hazardReportCoverage.setText("No SAR-reported hazards to account for.");
		} else if (accounted == total) {
			hazardReportCoverage.setText("All SAR-reported hazards accounted for (" + accounted + " of " + total + ").");
		} else {
			hazardReportCoverage.setText("SAR-reported hazards accounted for: " + accounted + " of " + total + ".");
		}
	}

	/**
	 * Produces the review status for a reported hazard.
	 *
	 * @param source report source.
	 * @return coverage and acknowledgement status.
	 */
	private String reportReviewStatus(HazardReportSource source) {
		boolean linked = isHazardLinkedToAnalysis(source);
		String notedBy = source.task() == null
				? source.archivedReport().getNotedBySafetyOfficer()
				: source.task().getSafetyHazardNotedBy();
		if (linked && !notedBy.isBlank()) {
			return "Included in ICS 215A; noted by " + notedBy;
		}
		if (linked) {
			return "Included in ICS 215A";
		}
		return notedBy.isBlank() ? "Not yet reviewed" : "Noted by " + notedBy;
	}

	/**
	 * Checks whether a report's linked analysis row still exists.
	 *
	 * @param source report source.
	 * @return true when the report links to a current ICS 215A row.
	 */
	private boolean isHazardLinkedToAnalysis(HazardReportSource source) {
		String entryId = source.task() == null
				? source.archivedReport().getAnalysisEntryId()
				: source.task().getSafetyHazardAnalysisEntryId();
		return !entryId.isBlank() && controller.getData().getForm215a().getEntries().stream()
				.anyMatch(entry -> entryId.equals(entry.getEntryId()));
	}

	/**
	 * Formats an operational period range for the hazard report table.
	 *
	 * @param start period start.
	 * @param end period end.
	 * @return ISO-formatted period range.
	 */
	private String periodLabel(LocalDateTime start, LocalDateTime end) {
		String from = isoDateTime(start);
		String to = isoDateTime(end);
		if (from.isBlank() && to.isBlank()) {
			return "Unknown period";
		}
		return from + " – " + to;
	}

	/**
	 * Exports one safety PDF to a user-selected directory.
	 *
	 * @param formKey safety form key.
	 */
	private void exportPdf(String formKey) {
		pushToModel();
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			Path output = controller.exportSelected(formKey, chooser.getSelectedFile().toPath());
			JOptionPane.showMessageDialog(this, "Exported " + output, "PDF Export", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException | RuntimeException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Could not export PDF", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Creates a text area with word wrapping.
	 *
	 * @param rows preferred visible line count.
	 * @return configured text area.
	 */
	private static JTextArea textArea(int rows) {
		JTextArea area = new JTextArea(rows, 50);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		return area;
	}

	/**
	 * Creates and wires a safety-workspace action button.
	 *
	 * @param panel button container.
	 * @param label button label.
	 * @param action invoked when clicked.
	 */
	private static void button(JPanel panel, String label, Runnable action) {
		JButton button = new JButton(label);
		button.addActionListener(event -> action.run());
		panel.add(button);
	}

	/**
	 * Returns cell text from a table model.
	 *
	 * @param model table model.
	 * @param row row index.
	 * @param column column index.
	 * @return non-null cell text.
	 */
	private static String textAt(DefaultTableModel model, int row, int column) {
		Object value = model.getValueAt(row, column);
		return value == null ? "" : value.toString();
	}

	/**
	 * Parses an ISO local date/time entered in the safety form.
	 *
	 * @param value ISO date/time text.
	 * @return parsed time, or null when blank/invalid.
	 */
	private static LocalDateTime parseDateTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value.trim());
		} catch (java.time.format.DateTimeParseException exception) {
			return null;
		}
	}

	/**
	 * Formats date/time using ISO 8601 date ordering.
	 *
	 * @param dateTime source date/time.
	 * @return ISO local date/time text, or blank.
	 */
	private static String isoDateTime(LocalDateTime dateTime) {
		return dateTime == null ? "" : dateTime.toString();
	}

	/**
	 * Produces the best available incident-area label for a task report.
	 *
	 * @param task SAR task.
	 * @return branch/division/group context or {@code All}.
	 */
	private static String reportArea(SarTaskAssignment task) {
		List<String> labels = new ArrayList<>();
		if (!task.getBranch().isBlank()) {
			labels.add("Branch " + task.getBranch());
		}
		if (!task.getDivision().isBlank()) {
			labels.add("Division " + task.getDivision());
		}
		if (!task.getGroup().isBlank()) {
			labels.add("Group " + task.getGroup());
		}
		return labels.isEmpty() ? "All" : String.join(" / ", labels);
	}

	/**
	 * Produces a source label for a SAR report.
	 *
	 * @param task SAR task.
	 * @return assignment/team label.
	 */
	private static String taskLabel(SarTaskAssignment task) {
		String label = task.getAssignmentTeamNumber();
		if (label == null || label.isBlank()) {
			label = task.getAssignmentId();
		}
		return label == null || label.isBlank() ? task.getTaskId() : label;
	}

	/**
	 * Provides access to the live current-period or archived hazard report.
	 *
	 * @param task current-period task, or null for an archived report.
	 * @param archivedReport archived report, or null for a current-period task.
	 */
	private record HazardReportSource(SarTaskAssignment task, SafetyHazardReport archivedReport) {
		/**
		 * Returns the incident area associated with this report.
		 *
		 * @return incident area.
		 */
		private String incidentArea() {
			return task == null ? archivedReport.getIncidentArea() : reportArea(task);
		}

		/**
		 * Returns the reported hazard text.
		 *
		 * @return reported hazards.
		 */
		private String hazards() {
			return task == null ? archivedReport.getHazards() : task.getHazardsObserved();
		}
	}
}
