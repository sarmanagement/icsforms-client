/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.RadioChannel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Transactional channel editor; custom radio-system values are never restricted to presets.
 */
final class RadioChannelEditorPanel extends JPanel {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Map<Field, JTextComponent> fields = new EnumMap<>(Field.class);
	private final JCheckBox include = new JCheckBox("Include on ICS 205");
	private final JComboBox<String> txPolicy = new JComboBox<>(new String[]{
			RadioChannel.TX_POLICY_UNKNOWN, RadioChannel.TX_POLICY_TRANSMIT, RadioChannel.TX_POLICY_RECEIVE_ONLY});
	private final JTextArea metadata = new JTextArea(4, 40);
	private final RadioChannel original;

	/**
	 * Builds a detached editor without modifying the supplied channel.
	 *
	 * @param channel channel to edit.
	 */
	RadioChannelEditorPanel(RadioChannel channel) {
		this(channel, List.of());
	}

	/**
	 * Builds a detached editor with unverified legacy labels available as name suggestions.
	 *
	 * @param channel channel to edit.
	 * @param legacyLabels existing radio/contact text; selection never infers programming values.
	 */
	RadioChannelEditorPanel(RadioChannel channel, List<String> legacyLabels) {
		super(new BorderLayout(6, 6));
		original = channel.deepCopy();
		JPanel form = UiSupport.formPanel();
		int row = 0;
		for (Field field : Field.values()) {
			JTextComponent text;
			javax.swing.JComponent control;
			if (field.choices.length > 0 || field == Field.NAME) {
				JComboBox<String> combo = new JComboBox<>(field == Field.NAME
						? legacyLabels.toArray(String[]::new) : field.choices);
				combo.setEditable(true);
				combo.setSelectedItem(field.getter.apply(channel));
				text = (JTextComponent) combo.getEditor().getEditorComponent();
				control = combo;
			} else if (field.multiline) {
				JTextArea area = new JTextArea(3, 40);
				area.setLineWrap(true);
				area.setWrapStyleWord(true);
				text = area;
				control = new JScrollPane(area);
			} else {
				text = new JTextField(30);
				control = text;
			}
			text.setText(field.getter.apply(channel));
			if (field == Field.ID) {
				text.setEditable(false);
				text.setToolTipText("Stable identity; duplicates receive a new ID automatically");
			}
			text.getAccessibleContext().setAccessibleName(field.label);
			fields.put(field, text);
			UiSupport.addRow(form, row++, field.label, control);
		}
		include.setSelected(channel.isIncludeOnForm());
		txPolicy.setSelectedItem(channel.getTxPolicy());
		UiSupport.addRow(form, row++, "Transmit policy", txPolicy);
		UiSupport.addRow(form, row++, "Printed rows", include);
		try {
			metadata.setText(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(channel.getProgrammingMetadata()));
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Unable to display programming metadata.", exception);
		}
		metadata.getAccessibleContext().setAccessibleName("Programming metadata (JSON string map)");
		UiSupport.addRow(form, row, "Programming metadata (JSON)", new JScrollPane(metadata));
		add(new JScrollPane(form), BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout());
		JButton simplex = new JButton("Simplex: copy RX to TX");
		simplex.addActionListener(event -> copyRxToTx());
		footer.add(simplex, BorderLayout.NORTH);
		footer.add(new JLabel("Channel-name suggestions copy unverified legacy text; no programming values are inferred."),
				BorderLayout.CENTER);
		footer.add(new JLabel("Unknown/custom values are retained. Verify all radio programming before use."),
				BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
	}

	/** Copies explicitly entered receive values to transmit fields without inferring a frequency. */
	void copyRxToTx() {
		fields.get(Field.TX_FREQUENCY).setText(fields.get(Field.RX_FREQUENCY).getText());
		fields.get(Field.TX_BANDWIDTH).setText(fields.get(Field.RX_BANDWIDTH).getText());
		fields.get(Field.TX_TONE).setText(fields.get(Field.RX_TONE).getText());
		txPolicy.setSelectedItem(RadioChannel.TX_POLICY_TRANSMIT);
	}

	/**
	 * Returns edited data with lossless arbitrary programming metadata.
	 *
	 * @return independent edited channel.
	 * @throws IllegalArgumentException when metadata is not a JSON string map.
	 */
	RadioChannel editedChannel() {
		RadioChannel edited = original.deepCopy();
		fields.forEach((field, text) -> field.setter.accept(edited, text.getText()));
		edited.setIncludeOnForm(include.isSelected());
		edited.setTxPolicy((String) txPolicy.getSelectedItem());
		try {
			com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(metadata.getText());
			if (node == null || !node.isObject()) {
				throw new IllegalArgumentException("Programming metadata must be a JSON object of string values.");
			}
			var values = node.elements();
			while (values.hasNext()) {
				if (!values.next().isTextual()) {
					throw new IllegalArgumentException("Programming metadata values must be strings.");
				}
			}
			edited.setProgrammingMetadata(MAPPER.convertValue(node, new TypeReference<Map<String, String>>() { }));
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Programming metadata must be valid JSON.", exception);
		}
		return edited;
	}

	/**
	 * Editable string fields shared by the table and detailed editor.
	 */
	enum Field {
		ID("Channel ID", RadioChannel::getChannelId, RadioChannel::setChannelId, false),
		ZONE("Zone / group", RadioChannel::getZoneGroup, RadioChannel::setZoneGroup, false),
		NUMBER("Channel number", RadioChannel::getChannelNumber, RadioChannel::setChannelNumber, false),
		FUNCTION("Function", RadioChannel::getFunction, RadioChannel::setFunction, false,
				"", "Command", "Tactical", "Ground-to-Air", "Air-to-Air", "Support", "Dispatch"),
		NAME("Channel name", RadioChannel::getChannelName, RadioChannel::setChannelName, false),
		ASSIGNMENT("Assignment", RadioChannel::getAssignment, RadioChannel::setAssignment, true),
		RX_FREQUENCY("RX frequency", RadioChannel::getRxFrequency, RadioChannel::setRxFrequency, false),
		RX_BANDWIDTH("RX bandwidth (N/W)", RadioChannel::getRxBandwidth, RadioChannel::setRxBandwidth, false,
				"", "N", "W"),
		RX_TONE("RX tone / NAC", RadioChannel::getRxToneNac, RadioChannel::setRxToneNac, false),
		TX_FREQUENCY("TX frequency", RadioChannel::getTxFrequency, RadioChannel::setTxFrequency, false),
		TX_BANDWIDTH("TX bandwidth (N/W)", RadioChannel::getTxBandwidth, RadioChannel::setTxBandwidth, false,
				"", "N", "W"),
		TX_TONE("TX tone / NAC", RadioChannel::getTxToneNac, RadioChannel::setTxToneNac, false),
		MODE("Mode", RadioChannel::getMode, RadioChannel::setMode, false,
				"", "A", "D", "M"),
		PROTOCOL("Digital protocol", RadioChannel::getDigitalProtocol, RadioChannel::setDigitalProtocol, false,
				"", "P25", "DMR", "NXDN", "D-STAR", "C4FM"),
		SYSTEM("System", RadioChannel::getSystem, RadioChannel::setSystem, true),
		REMARKS("Remarks", RadioChannel::getRemarks, RadioChannel::setRemarks, true);

		final String label;
		final Function<RadioChannel, String> getter;
		final BiConsumer<RadioChannel, String> setter;
		final boolean multiline;
		final String[] choices;

		/**
		 * Defines one editable field.
		 *
		 * @param label accessible label.
		 * @param getter model getter.
		 * @param setter model setter.
		 * @param multiline whether a multiline editor is needed.
		 * @param choices optional editable suggestions.
		 */
		Field(String label, Function<RadioChannel, String> getter, BiConsumer<RadioChannel, String> setter,
				boolean multiline, String... choices) {
			this.label = label;
			this.getter = getter;
			this.setter = setter;
			this.multiline = multiline;
			this.choices = choices;
		}
	}
}
