/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.sarmanagement.icsforms.model.RadioChannel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Transactional channel editor; unknown, receive-only and trunked programming stays raw unless
 * explicitly edited. Missing TX never implies simplex or permission to transmit.
 * Duplex arithmetic uses exact decimal MHz, with TX edits updating offset and RX/offset edits
 * updating TX. Invalid decimal input leaves the dependent field untouched and blocks a duplex
 * save, as does a nonpositive TX frequency; arbitrary programming strings remain valid in the
 * unchanged mode.
 */
final class RadioChannelEditorPanel extends JPanel {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Map<Field, JTextComponent> fields = new EnumMap<>(Field.class);
	private final Map<Field, JComponent> controls = new EnumMap<>(Field.class);
	private final JCheckBox include = new JCheckBox("Included in plan");
	private final JComboBox<String> txPolicy = new JComboBox<>(new String[]{
			RadioChannel.TX_POLICY_UNKNOWN, RadioChannel.TX_POLICY_TRANSMIT, RadioChannel.TX_POLICY_RECEIVE_ONLY});
	private final JComboBox<String> linkMode = new JComboBox<>(new String[]{"Unchanged", "Simplex", "Duplex"});
	private final JTextField offset = new JTextField(12);
	private final JLabel arithmeticStatus = new JLabel(" ");
	private final JTextArea metadata = new JTextArea(2, 40);
	private final RadioChannel original;
	private boolean updating;
	private String arithmeticError;

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
		for (Field field : Field.values()) {
			JTextComponent text;
			JComponent control;
			if (field.choices.length > 0 || field == Field.NAME) {
				JComboBox<String> combo = new JComboBox<>(field == Field.NAME
						? legacyLabels.toArray(String[]::new)
						: java.util.Arrays.stream(field.choices).map(field::displayValue).toArray(String[]::new));
				combo.setEditable(true);
				combo.setSelectedItem(field.displayValue(field.getter.apply(channel)));
				text = (JTextComponent) combo.getEditor().getEditorComponent();
				control = combo;
			} else if (field.multiline) {
				JTextArea area = new JTextArea(2, 16);
				area.setLineWrap(true);
				area.setWrapStyleWord(true);
				text = area;
				control = new JScrollPane(area);
			} else {
				text = new JTextField(14);
				control = text;
			}
			text.setText(field.displayValue(field.getter.apply(channel)));
			if (field == Field.ID) {
				text.setEditable(false);
				text.setToolTipText("Stable identity; duplicates receive a new ID automatically");
			}
			text.getAccessibleContext().setAccessibleName(field.label);
			fields.put(field, text);
			controls.put(field, control);
		}
		include.setSelected(channel.isIncludeOnForm());
		txPolicy.setSelectedItem(channel.getTxPolicy());
		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.add(fieldGroup("Channel", controls, 3, Field.NAME, Field.NUMBER, Field.ZONE,
				Field.FUNCTION, Field.MODE, Field.PROTOCOL));
		JPanel radio = new JPanel(new GridLayout(1, 3, 8, 0));
		radio.add(fieldGroup("Receive (RX)", controls, 1,
				Field.RX_FREQUENCY, Field.RX_BANDWIDTH, Field.RX_TONE));
		JPanel linking = new JPanel(new GridLayout(0, 1, 0, 4));
		linking.setBorder(BorderFactory.createTitledBorder("Frequency relationship"));
		linking.add(labeled("Simplex / duplex", linkMode));
		linking.add(labeled("Signed offset MHz (TX − RX)", offset));
		linking.add(arithmeticStatus);
		radio.add(linking);
		radio.add(fieldGroup("Transmit (TX)", controls, 1,
				Field.TX_FREQUENCY, Field.TX_BANDWIDTH, Field.TX_TONE));
		form.add(radio);
		JPanel policy = new JPanel(new GridLayout(1, 2, 8, 0));
		policy.add(labeled("Transmit policy", txPolicy));
		policy.add(labeled("Included in plan", include));
		form.add(policy);
		form.add(fieldGroup("Operational details", controls, 3, Field.ASSIGNMENT, Field.SYSTEM, Field.REMARKS));
		try {
			metadata.setText(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(channel.getProgrammingMetadata()));
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Unable to display programming metadata.", exception);
		}
		metadata.getAccessibleContext().setAccessibleName("Programming metadata (JSON string map)");
		form.add(labeled("Programming metadata (JSON)", new JScrollPane(metadata)));
		JScrollPane scroll = new JScrollPane(form);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		add(new JLabel("<html>Unknown/custom values are retained; legacy names infer no programming."
				+ "<br>Verify all radio programming before use.</html>"), BorderLayout.SOUTH);
		setPreferredSize(new Dimension(860, 520));
		offset.getAccessibleContext().setAccessibleName("Signed offset MHz (TX − RX)");
		linkMode.getAccessibleContext().setAccessibleName("Simplex / duplex");
		updateOffset();
		offset.setEnabled(false);
		linkMode.addActionListener(event -> changeLinkMode());
		for (Field field : List.of(Field.RX_FREQUENCY, Field.RX_BANDWIDTH, Field.RX_TONE, Field.TX_FREQUENCY)) {
			onEdit(fields.get(field), () -> programmingChanged(field));
		}
		onEdit(offset, () -> programmingChanged(null));
	}

	/**
	 * Wraps a control with its label above, keeping the three-column layout compact.
	 *
	 * @param title accessible label.
	 * @param control editor control.
	 * @return labeled control panel.
	 */
	private static JPanel labeled(String title, JComponent control) {
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		JLabel label = new JLabel(title);
		label.setLabelFor(control);
		panel.add(label, BorderLayout.NORTH);
		panel.add(control, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Groups a subset of fields without exposing the internal identity control.
	 *
	 * @param title section title.
	 * @param controls field controls.
	 * @param columns number of columns.
	 * @param members fields to display.
	 * @return compact field section.
	 */
	private static JPanel fieldGroup(String title, Map<Field, JComponent> controls, int columns, Field... members) {
		JPanel panel = new JPanel(new GridLayout(0, columns, 8, 4));
		panel.setBorder(BorderFactory.createTitledBorder(title));
		for (Field field : members) {
			panel.add(labeled(field.label, controls.get(field)));
		}
		return panel;
	}

	/**
	 * Observes raw editor changes, including editable combo text.
	 *
	 * @param text editor to observe.
	 * @param action change callback.
	 */
	private static void onEdit(JTextComponent text, Runnable action) {
		text.getDocument().addDocumentListener(new DocumentListener() {
			/** {@inheritDoc} */
			@Override
			public void insertUpdate(DocumentEvent event) {
				action.run();
			}

			/** {@inheritDoc} */
			@Override
			public void removeUpdate(DocumentEvent event) {
				action.run();
			}

			/** {@inheritDoc} */
			@Override
			public void changedUpdate(DocumentEvent event) {
				action.run();
			}
		});
	}

	/** Applies only an explicitly selected relationship; opening never copies or invents TX. */
	private void changeLinkMode() {
		boolean simplex = "Simplex".equals(linkMode.getSelectedItem());
		offset.setEnabled("Duplex".equals(linkMode.getSelectedItem()));
		for (Field field : List.of(Field.TX_FREQUENCY, Field.TX_BANDWIDTH, Field.TX_TONE)) {
			fields.get(field).setEditable(!simplex);
			controls.get(field).setEnabled(!simplex);
		}
		arithmeticError = null;
		arithmeticStatus.setText(" ");
		if (simplex) {
			copyRxToTx();
		} else if (offset.isEnabled()) {
			updateOffset();
		}
	}

	/**
	 * Updates only the dependent value, never normalizing the user's source programming string.
	 *
	 * @param field changed field; null denotes the signed offset editor.
	 */
	private void programmingChanged(Field field) {
		if (updating) {
			return;
		}
		if ("Simplex".equals(linkMode.getSelectedItem()) && field != Field.TX_FREQUENCY) {
			copySimplexValues();
		} else if ("Duplex".equals(linkMode.getSelectedItem())) {
			if (field == Field.TX_FREQUENCY) {
				updateOffset();
			} else if (field == null || field == Field.RX_FREQUENCY) {
				updating = true;
				try {
					BigDecimal tx = decimal(fields.get(Field.RX_FREQUENCY).getText()).add(decimal(offset.getText()));
					fields.get(Field.TX_FREQUENCY).setText(positiveTx(tx).toPlainString());
					arithmeticError = null;
				} catch (NumberFormatException exception) {
					arithmeticError = "RX + offset must be positive TX.";
				} finally {
					updating = false;
				}
				arithmeticStatus.setText(arithmeticError == null ? " " : arithmeticError);
			}
		}
	}

	/** Derives a signed offset exactly; malformed or unknown frequencies leave its old text intact. */
	private void updateOffset() {
		updating = true;
		try {
			BigDecimal difference = positiveTx(decimal(fields.get(Field.TX_FREQUENCY).getText()))
					.subtract(decimal(fields.get(Field.RX_FREQUENCY).getText()));
			offset.setText((difference.signum() >= 0 ? "+" : "") + difference.toPlainString());
			arithmeticError = null;
		} catch (NumberFormatException exception) {
			arithmeticError = "Enter decimal RX and positive TX.";
		} finally {
			updating = false;
		}
		arithmeticStatus.setText(offset.isEnabled() && arithmeticError != null ? arithmeticError : " ");
	}

	/**
	 * Parses a decimal MHz value without floating-point conversion, rounding or exponent expansion.
	 *
	 * @param value raw decimal input.
	 * @return exact decimal value.
	 * @throws NumberFormatException when input is blank or not plain decimal notation.
	 */
	private static BigDecimal decimal(String value) {
		if (!value.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")) {
			throw new NumberFormatException("Not a decimal MHz value.");
		}
		return new BigDecimal(value);
	}

	/**
	 * Rejects nonpositive TX values only during explicit duplex arithmetic, not raw round trips.
	 *
	 * @param value exact transmit frequency in MHz.
	 * @return unchanged positive frequency.
	 * @throws NumberFormatException when the transmit frequency is zero or negative.
	 */
	private static BigDecimal positiveTx(BigDecimal value) {
		if (value.signum() <= 0) {
			throw new NumberFormatException("TX frequency must be greater than zero.");
		}
		return value;
	}

	/** Copies RX raw values without recursive listeners or altering an explicit transmit policy. */
	private void copySimplexValues() {
		updating = true;
		try {
			fields.get(Field.TX_FREQUENCY).setText(fields.get(Field.RX_FREQUENCY).getText());
			fields.get(Field.TX_BANDWIDTH).setText(fields.get(Field.RX_BANDWIDTH).getText());
			fields.get(Field.TX_TONE).setText(fields.get(Field.RX_TONE).getText());
			offset.setText("+0");
		} finally {
			updating = false;
		}
	}

	/** Explicitly enables simplex transmission and follows subsequent RX programming edits. */
	void copyRxToTx() {
		if (!"Simplex".equals(linkMode.getSelectedItem())) {
			linkMode.setSelectedItem("Simplex");
			return;
		}
		copySimplexValues();
		txPolicy.setSelectedItem(RadioChannel.TX_POLICY_TRANSMIT);
	}

	/**
	 * Returns edited data with lossless arbitrary programming metadata.
	 *
	 * @return independent edited channel.
	 * @throws IllegalArgumentException when metadata is not a JSON string map or duplex arithmetic is invalid.
	 */
	RadioChannel editedChannel() {
		if ("Duplex".equals(linkMode.getSelectedItem()) && arithmeticError != null) {
			throw new IllegalArgumentException(arithmeticError);
		}
		RadioChannel edited = original.deepCopy();
		fields.forEach((field, text) -> field.setter.accept(edited,
				field.persistedValue(text.getText(), field.getter.apply(original))));
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

		/**
		 * Displays readable names while retaining the canonical suggestion API.
		 *
		 * @param value programming value.
		 * @return readable preset or unchanged custom text.
		 */
		String displayValue(String value) {
			if (this == MODE) {
				return switch (value) {
					case "A" -> "Analog";
					case "D" -> "Digital";
					case "M" -> "Mixed";
					default -> value;
				};
			}
			if (this == RX_BANDWIDTH || this == TX_BANDWIDTH) {
				return switch (value) {
					case "N" -> "N (narrow)";
					case "W" -> "W (wide)";
					default -> value;
				};
			}
			return value;
		}

		/**
		 * Maps readable presets back to codes without rewriting untouched custom programming.
		 *
		 * @param text displayed editor text.
		 * @param originalValue original raw programming value.
		 * @return canonical selected code or raw custom text.
		 */
		String persistedValue(String text, String originalValue) {
			if (text.equals(originalValue)) {
				return originalValue;
			}
			for (String choice : choices) {
				if (displayValue(choice).equals(text)) {
					return choice;
				}
			}
			return text;
		}
	}
}
