/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.RadioChannel;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.sarmanagement.icsforms.ui.RadioChannelEditorPanel.Field.*;

/** Headless-safe compact editor, exact arithmetic and conservative programming regressions. */
class RadioChannelEditorPanelTest {
	@Test
	void explicitPowerFieldEnablesChirpExportAndClearingRestoresUnknown() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("155.16000", "155.16000");
			original.setChannelNumber("1");
			original.setMode("A");
			original.setRxBandwidth("N");
			original.setTxBandwidth("N");
			original.setTxPolicy(RadioChannel.TX_POLICY_TRANSMIT);
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
			JTextField power = (JTextField) member(editor, "chirpPower");
			assertEquals("", power.getText());
			var form = new org.sarmanagement.icsforms.model.Ics205Form();
			form.getChannels().add(editor.editedChannel());
			var service = new org.sarmanagement.icsforms.comms.ChirpCsvService();
			assertEquals(1, service.exportCsv(form).excludedChannels().size());
			power.setText("2.5W");
			form.getChannels().set(0, editor.editedChannel());
			assertEquals("2.5W", form.getChannels().get(0).getProgrammingMetadata().get("chirp.Power"));
			assertTrue(service.exportCsv(form).excludedChannels().isEmpty());
			assertTrue(service.exportCsv(form).csv().contains("2.5W"));
			assertFalse(original.getProgrammingMetadata().containsKey("chirp.Power"));
			RadioChannelEditorPanel reopened = new RadioChannelEditorPanel(form.getChannels().get(0));
			assertEquals("2.5W", ((JTextField) member(reopened, "chirpPower")).getText());
			((JTextField) member(reopened, "chirpPower")).setText("");
			form.getChannels().set(0, reopened.editedChannel());
			assertFalse(form.getChannels().get(0).getProgrammingMetadata().containsKey("chirp.Power"));
			assertEquals(1, service.exportCsv(form).excludedChannels().size());
		});
	}

	@Test
	void powerValidationPreservesLegacyMetadataAndAllowsExplicitCorrection() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("", "");
			original.setProgrammingMetadata(Map.of("chirp.Power", "vendor high", "other", "retained"));
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
			assertEquals(original.getProgrammingMetadata(), editor.editedChannel().getProgrammingMetadata());
			JTextField power = (JTextField) member(editor, "chirpPower");
			for (String invalid : List.of("0", "-5", "0W", "high", "NaN", "1e3", "5 watts")) {
				power.setText(invalid);
				assertThrows(IllegalArgumentException.class, editor::editedChannel);
				assertEquals("vendor high", original.getProgrammingMetadata().get("chirp.Power"));
			}
			power.setText(" 5w ");
			assertEquals(Map.of("chirp.Power", "5w", "other", "retained"),
					editor.editedChannel().getProgrammingMetadata());
			assertEquals(RadioChannel.TX_POLICY_UNKNOWN, editor.editedChannel().getTxPolicy());
			var json = (javax.swing.JTextArea) member(editor, "metadata");
			json.setText("{\"chirp.Power\":\"10W\",\"other\":\"updated\"}");
			assertEquals("5w", editor.editedChannel().getProgrammingMetadata().get("chirp.Power"));
			power.setText("vendor high");
			assertEquals(Map.of("chirp.Power", "10W", "other", "updated"),
					editor.editedChannel().getProgrammingMetadata());
		});
	}

	@Test
	void openingUnknownReceiveOnlyTrunkedAndUnsupportedProgrammingNeverInfersTx() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (String policy : List.of(RadioChannel.TX_POLICY_UNKNOWN, RadioChannel.TX_POLICY_RECEIVE_ONLY,
					RadioChannel.TX_POLICY_TRANSMIT)) {
				for (String tx : List.of("", "Unknown", "trunked / system controlled", "155.123456789012345678900")) {
					RadioChannel original = channel("155.123456789012345678900", tx);
					original.setTxPolicy(policy);
					original.setRxBandwidth("custom RX bandwidth");
					original.setTxBandwidth("custom TX bandwidth");
					original.setRxToneNac("NAC $293");
					original.setTxToneNac("unsupported vendor code");
					original.setMode("custom mode");
					original.setSystem("Trunked\nsystem");
					original.setProgrammingMetadata(Map.of("vendor", "opaque raw programming"));
					RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
					assertEquals("Unchanged", combo(editor, "linkMode").getSelectedItem());
					assertFalse(offset(editor).isEnabled());
					RadioChannel result = editor.editedChannel();
					for (RadioChannelEditorPanel.Field field : RadioChannelEditorPanel.Field.values()) {
						assertEquals(field.getter.apply(original), field.getter.apply(result), field.name());
					}
					assertEquals(policy, result.getTxPolicy());
					assertEquals(original.getProgrammingMetadata(), result.getProgrammingMetadata());
					assertEquals("", result.getDigitalProtocol());
					assertEquals(tx, original.getTxFrequency());
				}
			}
		});
	}

	@Test
	void positiveAndNegativeOffsetsUpdateExactlyWithoutNormalizingSourceStrings() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("0155.123456789012345678900", "160.123456789012345678900");
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
			combo(editor, "linkMode").setSelectedItem("Duplex");
			assertEquals("+5.000000000000000000000", offset(editor).getText());
			assertEquals(original.getRxFrequency(), text(editor, RX_FREQUENCY).getText());
			offset(editor).setText("-0.000000000000000000001");
			assertEquals("155.123456789012345678899", editor.editedChannel().getTxFrequency());
			assertEquals("-0.000000000000000000001", offset(editor).getText());
			offset(editor).setText("+0.000000000000000000002");
			assertEquals("155.123456789012345678902", editor.editedChannel().getTxFrequency());
			assertEquals("+0.000000000000000000002", offset(editor).getText());
			text(editor, RX_FREQUENCY).setText("145.123456789012345678900");
			assertEquals("145.123456789012345678902", editor.editedChannel().getTxFrequency());
			assertEquals("0155.123456789012345678900", original.getRxFrequency());
			assertEquals("160.123456789012345678900", original.getTxFrequency());
			assertEquals(RadioChannel.TX_POLICY_UNKNOWN, editor.editedChannel().getTxPolicy());
		});
	}

	@Test
	void editingTxUpdatesSignedOffsetAndLeavesEnteredTxProgrammingStringExact() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(channel("155.00000", "160.00000"));
			combo(editor, "linkMode").setSelectedItem("Duplex");
			text(editor, TX_FREQUENCY).setText("0150.00000000000000000001");
			assertEquals("-4.99999999999999999999", offset(editor).getText());
			assertEquals("0150.00000000000000000001", editor.editedChannel().getTxFrequency());
			assertEquals("155.00000", editor.editedChannel().getRxFrequency());
			text(editor, TX_FREQUENCY).setText("155.00000");
			assertEquals("+0.00000", offset(editor).getText());
			text(editor, TX_FREQUENCY).setText("160.00000");
			assertEquals("+5.00000", offset(editor).getText());
		});
	}

	@Test
	void malformedDuplexInputDoesNotOverwriteDependentValuesOrOriginalAndCanRecover() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("155.25000", "160.25000");
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
			combo(editor, "linkMode").setSelectedItem("Duplex");
			for (String invalid : List.of("", "not a decimal", "1e999999999", "--5", "+", "5 MHz")) {
				offset(editor).setText(invalid);
				assertEquals("160.25000", text(editor, TX_FREQUENCY).getText());
				assertThrows(IllegalArgumentException.class, editor::editedChannel);
			}
			offset(editor).setText("-5.00000");
			assertEquals("150.25000", editor.editedChannel().getTxFrequency());
			text(editor, TX_FREQUENCY).setText("unknown");
			assertEquals("-5.00000", offset(editor).getText());
			assertThrows(IllegalArgumentException.class, editor::editedChannel);
			text(editor, TX_FREQUENCY).setText("160.25000");
			text(editor, RX_FREQUENCY).setText("invalid");
			assertEquals("160.25000", text(editor, TX_FREQUENCY).getText());
			assertThrows(IllegalArgumentException.class, editor::editedChannel);
			text(editor, RX_FREQUENCY).setText("155.25000");
			assertEquals("160.25000", editor.editedChannel().getTxFrequency());
			assertEquals("155.25000", original.getRxFrequency());
			assertEquals("160.25000", original.getTxFrequency());
		});
	}

	@Test
	void duplexRejectsZeroAndNegativeTxWithoutDiscardingCustomModeOrReceiveOnlyPolicy() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("5.00000", "10.00000");
			original.setMode("native unknown mode");
			original.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
			combo(editor, "linkMode").setSelectedItem("Duplex");
			for (String invalidOffset : List.of("-5.00000", "-6.00000")) {
				offset(editor).setText(invalidOffset);
				assertEquals("10.00000", text(editor, TX_FREQUENCY).getText());
				assertThrows(IllegalArgumentException.class, editor::editedChannel);
			}
			offset(editor).setText("-4.00000");
			assertEquals("1.00000", editor.editedChannel().getTxFrequency());
			for (String invalidTx : List.of("0.00000", "-1.00000")) {
				text(editor, TX_FREQUENCY).setText(invalidTx);
				assertEquals("-4.00000", offset(editor).getText());
				assertThrows(IllegalArgumentException.class, editor::editedChannel);
			}
			text(editor, TX_FREQUENCY).setText("1.00000");
			RadioChannel result = editor.editedChannel();
			assertEquals("native unknown mode", result.getMode());
			assertEquals(RadioChannel.TX_POLICY_RECEIVE_ONLY, result.getTxPolicy());
			assertEquals("", result.getDigitalProtocol());
			assertEquals("10.00000", original.getTxFrequency());
			assertEquals("native unknown mode", original.getMode());
			RadioChannel raw = channel("unknown", "-1.00000");
			raw.setMode("native unknown mode");
			assertEquals("-1.00000", new RadioChannelEditorPanel(raw).editedChannel().getTxFrequency());
		});
	}

	@Test
	void explicitSimplexFollowsFrequencyAndBandwidthButKeepsTonesAndPolicyIndependent() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("155.1234500", "");
			original.setRxBandwidth("N");
			original.setRxToneNac("NAC $293");
			original.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
			editor.copyRxToTx();
			assertEquals("Simplex", combo(editor, "linkMode").getSelectedItem());
			assertEquals("155.1234500", editor.editedChannel().getTxFrequency());
			assertEquals("N", editor.editedChannel().getTxBandwidth());
			assertEquals("", editor.editedChannel().getTxToneNac());
			assertEquals(RadioChannel.TX_POLICY_TRANSMIT, editor.editedChannel().getTxPolicy());
			assertFalse(text(editor, TX_FREQUENCY).isEditable());
			assertFalse(offset(editor).isEnabled());
			combo(editor, "txPolicy").setSelectedItem(RadioChannel.TX_POLICY_RECEIVE_ONLY);
			text(editor, RX_FREQUENCY).setText("154.987650000");
			text(editor, RX_BANDWIDTH).setText("W (wide)");
			text(editor, RX_TONE).setText("123.0");
			RadioChannel changed = editor.editedChannel();
			assertEquals("154.987650000", changed.getTxFrequency());
			assertEquals("W", changed.getTxBandwidth());
			assertEquals("123.0", changed.getRxToneNac());
			assertEquals("", changed.getTxToneNac());
			assertEquals(RadioChannel.TX_POLICY_RECEIVE_ONLY, changed.getTxPolicy());
			assertEquals("", changed.getDigitalProtocol());
			assertEquals("", original.getTxFrequency());
			combo(editor, "linkMode").setSelectedItem("Unchanged");
			assertTrue(text(editor, TX_FREQUENCY).isEditable());
			text(editor, RX_FREQUENCY).setText("100");
			assertEquals("154.987650000", editor.editedChannel().getTxFrequency());
		});
	}

	@Test
	void tonesStayEditableAndIndependentAcrossSimplexDuplexAndFrequencyChanges() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (String rx : List.of("", "88.5")) {
				for (String tx : List.of("", "123.0")) {
					RadioChannel original = channel("155.16000", "155.16000");
					original.setRxToneNac(rx);
					original.setTxToneNac(tx);
					RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
					for (String mode : List.of("Simplex", "Duplex", "Unchanged", "Simplex")) {
						combo(editor, "linkMode").setSelectedItem(mode);
						assertTrue(text(editor, RX_TONE).isEditable());
						assertTrue(text(editor, RX_TONE).isEnabled());
						assertTrue(text(editor, TX_TONE).isEditable());
						assertTrue(text(editor, TX_TONE).isEnabled());
						assertEquals(rx, editor.editedChannel().getRxToneNac());
						assertEquals(tx, editor.editedChannel().getTxToneNac());
					}
					text(editor, TX_TONE).setText("DTCS:023:R");
					text(editor, RX_FREQUENCY).setText("154.98765");
					text(editor, RX_BANDWIDTH).setText("N (narrow)");
					assertEquals("DTCS:023:R", editor.editedChannel().getTxToneNac());
					text(editor, RX_TONE).setText("");
					assertEquals("DTCS:023:R", editor.editedChannel().getTxToneNac());
					text(editor, TX_TONE).setText("");
					text(editor, RX_TONE).setText("NAC $293");
					assertEquals("", editor.editedChannel().getTxToneNac());
					assertEquals("NAC $293", editor.editedChannel().getRxToneNac());
					assertEquals(rx, original.getRxToneNac());
					assertEquals(tx, original.getTxToneNac());
				}
			}
		});
	}

	@Test
	void readablePresetsPersistCanonicalCodesAndNeverInferDigitalProtocol() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("", "");
			original.setMode("A");
			original.setRxBandwidth("N");
			original.setTxBandwidth("W");
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(original);
			assertEquals("Analog", text(editor, MODE).getText());
			assertEquals("N (narrow)", text(editor, RX_BANDWIDTH).getText());
			assertEquals("W (wide)", text(editor, TX_BANDWIDTH).getText());
			assertEquals("A", editor.editedChannel().getMode());
			assertEquals("N", editor.editedChannel().getRxBandwidth());
			assertEquals("W", editor.editedChannel().getTxBandwidth());
			for (String mode : List.of("Digital", "Mixed", "Analog")) {
				text(editor, MODE).setText(mode);
				assertEquals(Map.of("Analog", "A", "Digital", "D", "Mixed", "M").get(mode),
						editor.editedChannel().getMode());
				assertEquals("", editor.editedChannel().getDigitalProtocol());
			}
			text(editor, MODE).setText("vendor custom digital");
			text(editor, RX_BANDWIDTH).setText("12.500 kHz custom");
			assertEquals("vendor custom digital", editor.editedChannel().getMode());
			assertEquals("12.500 kHz custom", editor.editedChannel().getRxBandwidth());
			assertEquals(List.of("", "A", "D", "M"), List.of(MODE.choices));
			assertEquals(List.of("", "N", "W"), List.of(RX_BANDWIDTH.choices));
		});
	}

	@Test
	void customStringsMatchingReadableLabelsArePreservedOnUnchangedSave() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannel original = channel("trunked", "unsupported");
			original.setMode("Digital");
			original.setRxBandwidth("N (narrow)");
			original.setTxBandwidth("W (wide)");
			RadioChannel result = new RadioChannelEditorPanel(original).editedChannel();
			assertEquals("Digital", result.getMode());
			assertEquals("N (narrow)", result.getRxBandwidth());
			assertEquals("W (wide)", result.getTxBandwidth());
			assertEquals("", result.getDigitalProtocol());
		});
	}

	@Test
	void compactScrollableDialogHidesInternalIdentityAndUsesIncludedInPlanLabels() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(new RadioChannel());
			assertTrue(editor.getPreferredSize().width <= 900);
			assertTrue(editor.getPreferredSize().height <= 600);
			assertNotNull(find(editor, JScrollPane.class));
			assertNull(text(editor, ID).getParent());
			assertFalse(text(editor, ID).isEditable());
			assertEquals("Included in plan", ((JCheckBox) member(editor, "include")).getText());
			List<JLabel> labels = new ArrayList<>();
			collect(editor, JLabel.class, labels);
			assertTrue(labels.stream().anyMatch(label -> label.getText().equals("Included in plan")));
			assertFalse(labels.stream().anyMatch(label -> label.getText().contains("Printed rows")));
		});
	}

	/**
	 * Creates a detached channel fixture with raw frequency strings.
	 *
	 * @param rx receive frequency.
	 * @param tx transmit frequency.
	 * @return new channel.
	 */
	private static RadioChannel channel(String rx, String tx) {
		RadioChannel channel = new RadioChannel();
		channel.setRxFrequency(rx);
		channel.setTxFrequency(tx);
		return channel;
	}

	/**
	 * Returns a programming text editor without changing the production field API.
	 *
	 * @param editor editor panel.
	 * @param field programming field.
	 * @return text editor.
	 */
	@SuppressWarnings("unchecked")
	private static JTextComponent text(RadioChannelEditorPanel editor, RadioChannelEditorPanel.Field field) {
		return ((Map<RadioChannelEditorPanel.Field, JTextComponent>) member(editor, "fields")).get(field);
	}

	/**
	 * Returns an explicit policy or frequency-relationship selector.
	 *
	 * @param editor editor panel.
	 * @param name selector member name.
	 * @return combo selector.
	 */
	private static JComboBox<?> combo(RadioChannelEditorPanel editor, String name) {
		return (JComboBox<?>) member(editor, name);
	}

	/**
	 * Returns the signed MHz offset input.
	 *
	 * @param editor editor panel.
	 * @return offset text field.
	 */
	private static JTextField offset(RadioChannelEditorPanel editor) {
		return (JTextField) member(editor, "offset");
	}

	/**
	 * Reads private Swing controls for headless interaction.
	 *
	 * @param target control owner.
	 * @param name member name.
	 * @return member value.
	 */
	private static Object member(Object target, String name) {
		try {
			java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Finds the first component of a type.
	 *
	 * @param root component tree.
	 * @param type component class.
	 * @param <T> component type.
	 * @return first matching component.
	 */
	private static <T extends Component> T find(Container root, Class<T> type) {
		List<T> matches = new ArrayList<>();
		collect(root, type, matches);
		return matches.isEmpty() ? null : matches.get(0);
	}

	/**
	 * Collects matching controls recursively.
	 *
	 * @param root component tree.
	 * @param type component class.
	 * @param result matching controls.
	 * @param <T> component type.
	 */
	private static <T extends Component> void collect(Container root, Class<T> type, List<T> result) {
		for (Component child : root.getComponents()) {
			if (type.isInstance(child)) {
				result.add(type.cast(child));
			}
			if (child instanceof Container container) {
				collect(container, type, result);
			}
		}
	}
}
