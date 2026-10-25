/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics202Form;
import org.sarmanagement.icsforms.pdf.PdfText;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * First-cut editor for ICS 202 Incident Objectives content.
 */
public class Ics202Panel extends JPanel {
	private final AppController controller;
	private final JTextArea objectivesArea = UiSupport.textArea(5);
	private final JLabel objectiveWarning = new JLabel(" ");
	private final Color objectiveBackground = objectivesArea.getBackground();
	private final JTextArea commandEmphasisArea = UiSupport.textArea(3);
	private final JTextArea situationalAwarenessArea = UiSupport.textArea(3);
	private final JCheckBox siteSafetyPlanRequired = new JCheckBox("Site safety plan required");
	private final JCheckBox includeIcs202 = new JCheckBox("ICS 202");
	private final JCheckBox includeIcs204 = new JCheckBox("ICS 204");
	private final JCheckBox includeIcs208 = new JCheckBox("ICS 208");
	private final JCheckBox includeIcs215a = new JCheckBox("ICS 215A");
	private final JCheckBox includeIcs206 = new JCheckBox("ICS 206");
	private final JCheckBox includeCanine206 = new JCheckBox("ICS 206CANINE (adapted)");
	private final JCheckBox includeSarTaskAssignment = new JCheckBox("SAR Task Assignment");
	private final JCheckBox includeMapPacket = new JCheckBox("Map packet");
	private final JTextArea additionalFormsArea = UiSupport.textArea(2);
	private final JTextField preparedByNameField = UiSupport.textField();
	private final JTextField preparedByPositionField = UiSupport.textField();
	private final JTextField approvedByNameField = UiSupport.textField();
	private final javax.swing.JSpinner approvedDateTimeField = UiSupport.dateTimeSpinner();
	private final JTextField iapPageField = UiSupport.textField();

	/**
	 * Creates the ICS 202 editor panel.
	 *
	 * @param controller
	 *            application controller.
	 */
	public Ics202Panel(AppController controller) {
		super(new BorderLayout());
		this.controller = controller;
		JPanel form = UiSupport.formPanel();
		form.setBorder(BorderFactory.createTitledBorder("ICS 202 Incident Objectives"));
		UiSupport.addRow(form, 0, "Objectives (one per line)", objectiveEditor());
		UiSupport.addRow(form, 1, "Command emphasis", new JScrollPane(commandEmphasisArea));
		UiSupport.addRow(form, 2, "General situational awareness", new JScrollPane(situationalAwarenessArea));
		UiSupport.addRow(form, 3, "Safety plan", siteSafetyPlanRequired);
		UiSupport.addRow(form, 4, "Included forms / attachments", includedFormsPanel());
		UiSupport.addRow(form, 5, "Prepared by name", preparedByNameField);
		UiSupport.addRow(form, 6, "Prepared by position/title", preparedByPositionField);
		preparedByNameField.setEditable(false);
		preparedByPositionField.setEditable(false);
		UiSupport.addRow(form, 7, "Incident commander", approvedByNameField);
		UiSupport.addRow(form, 8, "IC Approval date/time", approvedDateTimeField);
		iapPageField.setEditable(false);
		iapPageField.setToolTipText("Assigned automatically when the IAP bundle PDF is exported");
		UiSupport.addRow(form, 9, "IAP page (auto)", iapPageField);
		JScrollPane scrollPane = new JScrollPane(form);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Creates the objective editor with a nonblocking, accessible PDF warning.
	 *
	 * @return objective text editor and inline warning.
	 */
	private JPanel objectiveEditor() {
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		objectiveWarning.setForeground(new Color(160, 40, 40));
		objectiveWarning.getAccessibleContext().setAccessibleName("Objective PDF export warning");
		panel.add(new JScrollPane(objectivesArea), BorderLayout.CENTER);
		panel.add(objectiveWarning, BorderLayout.SOUTH);
		objectivesArea.getDocument().addDocumentListener(new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				updateObjectiveWarning();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				updateObjectiveWarning();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				updateObjectiveWarning();
			}
		});
		updateObjectiveWarning();
		return panel;
	}

	/**
	 * Updates export guidance without rejecting edits, paste, tab switches, or saves.
	 */
	private void updateObjectiveWarning() {
		try {
			PdfText.validate(objectivesArea.getText(), "ICS 202", "Objectives");
			objectiveWarning.setText(" ");
			objectiveWarning.setToolTipText(null);
			objectivesArea.setToolTipText(null);
			objectivesArea.setBackground(objectiveBackground);
		} catch (IllegalArgumentException exception) {
			String message = exception.getMessage();
			int advice = message.indexOf(". Edit this field");
			objectiveWarning.setText("PDF export warning: " + (advice < 0 ? message : message.substring(0, advice + 1)));
			objectiveWarning.setToolTipText(message);
			objectivesArea.setToolTipText(message);
			objectivesArea.setBackground(new Color(255, 242, 224));
		}
	}

	/** Loads values from the model. */
	public void refreshFromModel() {
		Ics202Form form = controller.getData().getForm202();
		objectivesArea.setText(String.join("\n", form.getObjectives()));
		commandEmphasisArea.setText(nullSafe(form.getCommandEmphasis()));
		situationalAwarenessArea.setText(nullSafe(form.getSituationalAwareness()));
		siteSafetyPlanRequired.setSelected(form.isSiteSafetyPlanRequired());
		setIncludedForms(form.getIncidentActionPlanAttachments());
		preparedByNameField.setText(nullSafe(form.getPreparedByName()));
		preparedByPositionField.setText(nullSafe(form.getPreparedByPositionTitle()));
		approvedByNameField.setText(nullSafe(form.getApprovedByIncidentCommanderName()));
		approvedDateTimeField.setValue(AppController.toDate(form.getApprovedDateTime()));
		iapPageField.setText(nullSafe(form.getIapPage()));
	}

	/**
	 * Applies values without discarding objective text or blocking incident saves.
	 * Unsupported PDF characters remain in the model for export-time diagnostics.
	 */
	public void pushToModel() {
		Ics202Form form = controller.getData().getForm202();
		form.setObjectives(objectiveLines(objectivesArea.getText()));
		form.setCommandEmphasis(commandEmphasisArea.getText().trim());
		form.setSituationalAwareness(situationalAwarenessArea.getText().trim());
		form.setSiteSafetyPlanRequired(siteSafetyPlanRequired.isSelected());
		List<String> includedForms = getIncludedForms();
		form.setIncidentActionPlanAttachments(includedForms);
		synchronizeMedicalPlanInclusion(controller.getData(), includedForms);
		form.setApprovedByIncidentCommanderName(approvedByNameField.getText().trim());
		form.setPreparedByName(preparedByNameField.getText().trim());
		form.setPreparedByPositionTitle(preparedByPositionField.getText().trim());
		form.setApprovedDateTime(AppController.toLocalDateTime((java.util.Date) approvedDateTimeField.getValue()));
		form.setIapPage(iapPageField.getText().trim());
	}

	/**
	 * Splits logical objective lines without trimming source characters.
	 *
	 * @param value original objective input.
	 * @return nonempty objectives preserving tabs, spaces, and unwanted controls.
	 */
	private List<String> objectiveLines(String value) {
		List<String> items = new ArrayList<>();
		for (String line : value.split("\r\n|\r|\n", -1)) {
			if (!line.matches("[ \t]*")) {
				items.add(line);
			}
		}
		return items;
	}

	private JPanel includedFormsPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		JPanel checks = new JPanel(new GridLayout(0, 2, 4, 4));
		checks.setOpaque(false);
		checks.add(includeIcs202);
		checks.add(includeIcs204);
		checks.add(includeIcs208);
		checks.add(includeIcs215a);
		checks.add(includeIcs206);
		checks.add(includeCanine206);
		checks.add(includeSarTaskAssignment);
		checks.add(includeMapPacket);
		panel.add(checks, BorderLayout.NORTH);
		JScrollPane scrollPane = new JScrollPane(additionalFormsArea);
		scrollPane.setPreferredSize(new java.awt.Dimension(100, 60));
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private void setIncludedForms(List<String> items) {
		includeIcs202.setSelected(false);
		includeIcs204.setSelected(false);
		includeIcs208.setSelected(false);
		includeIcs215a.setSelected(false);
		includeIcs206.setSelected(false);
		includeCanine206.setSelected(false);
		includeSarTaskAssignment.setSelected(false);
		includeMapPacket.setSelected(false);
		List<String> additional = new ArrayList<>();
		for (String item : items) {
			if ("ICS 202".equalsIgnoreCase(item)) {
				includeIcs202.setSelected(true);
			} else if ("ICS 204".equalsIgnoreCase(item)) {
				includeIcs204.setSelected(true);
			} else if ("ICS 208".equalsIgnoreCase(item)) {
				includeIcs208.setSelected(true);
			} else if ("ICS 215A".equalsIgnoreCase(item)) {
				includeIcs215a.setSelected(true);
			} else if ("ICS 206".equalsIgnoreCase(item)) {
				includeIcs206.setSelected(true);
			} else if ("ICS 206CANINE".equalsIgnoreCase(item)) {
				includeCanine206.setSelected(true);
			} else if ("SAR Task Assignment".equalsIgnoreCase(item)) {
				includeSarTaskAssignment.setSelected(true);
			} else if ("Map packet".equalsIgnoreCase(item)) {
				includeMapPacket.setSelected(true);
			} else {
				additional.add(item);
			}
		}
		additionalFormsArea.setText(String.join("\n", additional));
	}

	private List<String> getIncludedForms() {
		List<String> items = new ArrayList<>();
		if (includeIcs202.isSelected()) {
			items.add("ICS 202");
		}
		if (includeIcs204.isSelected()) {
			items.add("ICS 204");
		}
		if (includeIcs208.isSelected()) {
			items.add("ICS 208");
		}
		if (includeIcs215a.isSelected()) {
			items.add("ICS 215A");
		}
		if (includeIcs206.isSelected()) {
			items.add("ICS 206");
		}
		if (includeCanine206.isSelected()) {
			items.add("ICS 206CANINE");
		}
		if (includeSarTaskAssignment.isSelected()) {
			items.add("SAR Task Assignment");
		}
		if (includeMapPacket.isSelected()) {
			items.add("Map packet");
		}
		items.addAll(lines(additionalFormsArea.getText()));
		return items;
	}

	/**
	 * Keeps legacy active flags aligned with the ICS 202 attachment selections.
	 *
	 * @param data incident whose medical plans are being configured.
	 * @param includedForms selected ICS 202 attachments.
	 */
	static void synchronizeMedicalPlanInclusion(AppData data, List<String> includedForms) {
		data.getMedicalPlan().setActive(includedForms.stream().anyMatch(item -> "ICS 206".equalsIgnoreCase(item)));
		data.getCanineMedicalPlan().setActive(
				includedForms.stream().anyMatch(item -> "ICS 206CANINE".equalsIgnoreCase(item)));
	}

	/**
	 * Splits textarea input into non-empty lines.
	 *
	 * @param value
	 *            textarea content.
	 * @return non-empty lines.
	 */
	private List<String> lines(String value) {
		List<String> items = new ArrayList<>();
		for (String line : PdfText.normalize(value).split("\n")) {
			if (!line.isBlank()) {
				items.add(line.trim());
			}
		}
		return items;
	}

	/**
	 * Converts null text to empty text.
	 *
	 * @param value
	 *            input value.
	 * @return safe text.
	 */
	private String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
