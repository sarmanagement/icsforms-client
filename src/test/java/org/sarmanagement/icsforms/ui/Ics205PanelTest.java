/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.Ics205PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Headless-safe communications workspace, channel editor and legacy picker regressions. */
class Ics205PanelTest {
	private final List<AppController> controllers = new ArrayList<>();

	@AfterEach
	void stopAutosave() throws Exception {
		SwingUtilities.invokeAndWait(() -> controllers.forEach(controller ->
				((Timer) field(controller, "autosaveTimer")).stop()));
	}

	@Test
	void refreshDoesNotFabricatePreparationOrOverwriteLeaderOverride() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().setCommunicationsUnitLeaderName("Comms leader");
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics205Panel panel = new Ics205Panel(controller);
			assertNotNull(((JTextField) field(panel, "preparedBy")).getClientProperty("uiSupport.namePicker.open"));
			panel.pushToModel();
			assertEquals("", data.getForm205().getPreparedByName());
			assertEquals("", data.getForm205().getPreparedBySignature());
			assertNull(data.getForm205().getPreparedDateTime());
			assertNull(data.getForm205().getDatePrepared());
			assertFalse(controller.isDirty());
			button(panel, "Use Communications Unit Leader").doClick();
			assertEquals("Comms leader", data.getForm205().getPreparedByName());
			((JTextField) field(panel, "preparedBy")).setText("Explicit override");
			data.getForm205().setPreparedBySignature("Supplied signature");
			panel.refreshFromModel();
			panel.pushToModel();
			assertEquals("Explicit override", data.getForm205().getPreparedByName());
			assertEquals("Supplied signature", data.getForm205().getPreparedBySignature());
			assertNull(data.getForm205().getPreparedDateTime());
		});
	}

	@Test
	void optionalPreparationDateUsesUtcConversionAndCanBeCleared() throws Exception {
		AppData data = new AppData();
		LocalDateTime utc = LocalDateTime.of(2026, 7, 12, 16, 45);
		data.getForm205().setPreparedDateTime(utc);
		data.getForm205().setDatePrepared(utc.minusHours(1));
		AppController controller = controller(data);
		ZoneId previous = UiSupport.getDateTimeDisplayZone();
		try {
			SwingUtilities.invokeAndWait(() -> {
				UiSupport.setDateTimeDisplayZone(ZoneId.of("America/New_York"));
				Ics205Panel panel = new Ics205Panel(controller);
				assertEquals(AppController.toDate(utc), ((JSpinner) field(panel, "preparedDate")).getValue());
				panel.pushToModel();
				assertEquals(utc, data.getForm205().getPreparedDateTime());
				assertEquals(utc.minusHours(1), data.getForm205().getDatePrepared());
				((JCheckBox) field(panel, "preparedDatePresent")).doClick();
				assertNull(data.getForm205().getPreparedDateTime());
				assertNull(data.getForm205().getDatePrepared());
			});
		} finally {
			UiSupport.setDateTimeDisplayZone(previous);
		}
	}

	@Test
	void invalidDateTextRejectsAllMetadataUntilPreparationDateIsValid() throws Exception {
		AppData data = new AppData();
		LocalDateTime timestamp = LocalDateTime.of(2026, 10, 24, 12, 30, 17);
		data.getForm205().setPreparedDateTime(timestamp);
		data.getForm205().setDatePrepared(timestamp.minusHours(1));
		data.getForm205().setPreparedByName("Original preparer");
		data.getForm205().setPreparedBySignature("Original signature");
		data.getForm205().setSpecialInstructions("Original instructions");
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics205Panel panel = new Ics205Panel(controller);
			JSpinner date = (JSpinner) field(panel, "preparedDate");
			((JSpinner.DateEditor) date.getEditor()).getTextField().setText("2026-02-31 12:30");
			((JTextField) field(panel, "preparedBy")).setText("Edited preparer");
			((JTextArea) field(panel, "instructions")).setText("Edited instructions");
			panel.pushToModel();
			assertEquals("Original preparer", data.getForm205().getPreparedByName());
			assertEquals("Original signature", data.getForm205().getPreparedBySignature());
			assertEquals("Original instructions", data.getForm205().getSpecialInstructions());
			assertEquals(timestamp, data.getForm205().getPreparedDateTime());
			assertEquals(timestamp.minusHours(1), data.getForm205().getDatePrepared());
			assertFalse(controller.isDirty());
			((JSpinner.DateEditor) date.getEditor()).getTextField().setText("2026-10-24 13:30 trailing");
			panel.pushToModel();
			assertEquals("Original preparer", data.getForm205().getPreparedByName());
			((JSpinner.DateEditor) date.getEditor()).getTextField().setText(
					((JSpinner.DateEditor) date.getEditor()).getFormat().format(AppController.toDate(timestamp)));
			panel.pushToModel();
			assertEquals("Edited preparer", data.getForm205().getPreparedByName());
			assertEquals("Original signature", data.getForm205().getPreparedBySignature());
			assertEquals("Edited instructions", data.getForm205().getSpecialInstructions());
			assertEquals(timestamp, data.getForm205().getPreparedDateTime());
			assertEquals(timestamp.minusHours(1), data.getForm205().getDatePrepared());
		});
	}

	@Test
	void singlePreparationControlUpdatesBothPrintedDatesWithoutEditingPaperworkFields() throws Exception {
		AppData data = new AppData();
		LocalDateTime timestamp = LocalDateTime.of(2026, 10, 25, 12, 30, 17);
		data.getForm205().setDatePrepared(timestamp);
		data.getForm205().setPreparedBySignature("Saved signature");
		data.getForm205().setIapPage("9");
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics205Panel panel = new Ics205Panel(controller);
			List<JSpinner> spinners = new ArrayList<>();
			collectComponents(panel, JSpinner.class, spinners);
			assertEquals(1, spinners.size());
			List<JTextField> textFields = new ArrayList<>();
			collectComponents(panel, JTextField.class, textFields);
			assertFalse(textFields.stream().anyMatch(input ->
					"Saved signature".equals(input.getText()) || "9".equals(input.getText())));
			JSpinner date = (JSpinner) field(panel, "preparedDate");
			assertEquals(AppController.toDate(timestamp), date.getValue());
			panel.pushToModel();
			assertNull(data.getForm205().getPreparedDateTime());
			assertEquals(timestamp, data.getForm205().getDatePrepared());
			LocalDateTime changed = timestamp.plusHours(1);
			date.setValue(AppController.toDate(changed));
			assertEquals(changed, data.getForm205().getPreparedDateTime());
			assertEquals(changed, data.getForm205().getDatePrepared());
			assertEquals("Saved signature", data.getForm205().getPreparedBySignature());
			assertEquals("9", data.getForm205().getIapPage());
			((JCheckBox) field(panel, "preparedDatePresent")).doClick();
			assertNull(data.getForm205().getPreparedDateTime());
			assertNull(data.getForm205().getDatePrepared());
			((JCheckBox) field(panel, "preparedDatePresent")).doClick();
			assertEquals(changed, data.getForm205().getPreparedDateTime());
			assertEquals(changed, data.getForm205().getDatePrepared());
		});
	}

	@Test
	void channelEditorRoundTripsEveryFieldCustomChoicesMultilineAndMetadataWithoutMutation() throws Exception {
		RadioChannel channel = channel("Custom name");
		for (RadioChannelEditorPanel.Field field : RadioChannelEditorPanel.Field.values()) {
			if (field != RadioChannelEditorPanel.Field.ID) {
				field.setter.accept(channel, "custom " + field.name() + (field.multiline ? "\nsecond line" : ""));
			}
		}
		channel.setProgrammingMetadata(Map.of("vendor=key\nmultiline", "value\nwith=equals", "unknown", "\"quoted\""));
		channel.setIncludeOnForm(false);
		channel.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		SwingUtilities.invokeAndWait(() -> {
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(channel);
			RadioChannel edited = editor.editedChannel();
			assertNotSame(channel, edited);
			for (RadioChannelEditorPanel.Field field : RadioChannelEditorPanel.Field.values()) {
				assertEquals(field.getter.apply(channel), field.getter.apply(edited), field.label);
			}
			assertEquals(channel.getProgrammingMetadata(), edited.getProgrammingMetadata());
			assertEquals(RadioChannel.TX_POLICY_RECEIVE_ONLY, edited.getTxPolicy());
			assertFalse(edited.isIncludeOnForm());
			@SuppressWarnings("unchecked")
			Map<RadioChannelEditorPanel.Field, JTextComponent> fields =
					(Map<RadioChannelEditorPanel.Field, JTextComponent>) field(editor, "fields");
			assertFalse(fields.get(RadioChannelEditorPanel.Field.ID).isEditable());
			assertInstanceOf(JTextArea.class, fields.get(RadioChannelEditorPanel.Field.ASSIGNMENT));
			assertInstanceOf(JTextArea.class, fields.get(RadioChannelEditorPanel.Field.SYSTEM));
			assertInstanceOf(JTextArea.class, fields.get(RadioChannelEditorPanel.Field.REMARKS));
			fields.get(RadioChannelEditorPanel.Field.RX_FREQUENCY).setText("155.12345");
			fields.get(RadioChannelEditorPanel.Field.RX_BANDWIDTH).setText("Custom bandwidth");
			fields.get(RadioChannelEditorPanel.Field.RX_TONE).setText("NAC $123");
			editor.copyRxToTx();
			RadioChannel simplex = editor.editedChannel();
			assertEquals("155.12345", simplex.getTxFrequency());
			assertEquals("Custom bandwidth", simplex.getTxBandwidth());
			assertEquals("NAC $123", simplex.getTxToneNac());
			assertEquals(RadioChannel.TX_POLICY_TRANSMIT, simplex.getTxPolicy());
			assertEquals(RadioChannel.TX_POLICY_RECEIVE_ONLY, channel.getTxPolicy());
		});
	}

	@Test
	void channelSuggestionsUseCanonicalCodesAndAllStandardFunctions() {
		assertEquals(List.of("", "N", "W"), List.of(RadioChannelEditorPanel.Field.RX_BANDWIDTH.choices));
		assertEquals(List.of("", "N", "W"), List.of(RadioChannelEditorPanel.Field.TX_BANDWIDTH.choices));
		assertEquals(List.of("", "A", "D", "M"), List.of(RadioChannelEditorPanel.Field.MODE.choices));
		assertTrue(List.of(RadioChannelEditorPanel.Field.FUNCTION.choices).containsAll(
				List.of("Command", "Tactical", "Ground-to-Air", "Air-to-Air", "Support", "Dispatch")));
	}

	@Test
	void invalidMetadataDoesNotChangeOriginalAndIncompleteChannelsRemainEditable() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics205Panel panel = new Ics205Panel(controller);
			RadioChannel draft = channel("Draft");
			draft.setProgrammingMetadata(Map.of("original", "retained"));
			panel.applyChannel(-1, draft);
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(draft);
			((JTextArea) field(editor, "metadata")).setText("{invalid");
			assertThrows(IllegalArgumentException.class, editor::editedChannel);
			assertEquals(Map.of("original", "retained"), draft.getProgrammingMetadata());
			assertEquals(1, ((JTable) field(panel, "table")).getRowCount());
			assertEquals("", draft.getRxFrequency());
			assertTrue(controller.isDirty());
		});
	}

	@Test
	void addEditDuplicateReorderRemovePreserveUnchangedChannelsAndCreateNewIdentity() throws Exception {
		AppData data = new AppData();
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics205Panel panel = new Ics205Panel(controller);
			RadioChannel first = channel("First");
			first.setProgrammingMetadata(Map.of("vendor", "detail"));
			panel.applyChannel(-1, first);
			panel.applyChannel(-1, channel("Second"));
			panel.duplicateChannel(0);
			RadioChannel duplicate = data.getForm205().getChannels().get(2);
			assertNotEquals(first.getChannelId(), duplicate.getChannelId());
			assertEquals(first.getProgrammingMetadata(), duplicate.getProgrammingMetadata());
			duplicate.getProgrammingMetadata().put("new", "separate");
			assertFalse(first.getProgrammingMetadata().containsKey("new"));
			panel.moveChannel(2, -1);
			assertSame(duplicate, data.getForm205().getChannels().get(1));
			RadioChannel edited = first.deepCopy();
			edited.setChannelName("Edited");
			panel.applyChannel(0, edited);
			assertEquals("First", first.getChannelName());
			assertEquals(first.getChannelId(), edited.getChannelId());
			panel.removeChannel(1);
			assertEquals(List.of("Edited", "Second"), data.getForm205().getChannels().stream()
					.map(RadioChannel::getChannelName).toList());
			panel.moveChannel(-1, 1);
			panel.moveChannel(0, -1);
			panel.duplicateChannel(-1);
			panel.removeChannel(-1);
			assertEquals(2, data.getForm205().getChannels().size());
			assertThrows(IllegalArgumentException.class, () -> panel.applyChannel(-1, edited.deepCopy()));
			assertEquals(2, data.getForm205().getChannels().size());
		});
	}

	@Test
	void sharedPickerFiltersHiddenChannelsAndRetainsLegacyFreeText() throws Exception {
		AppData data = new AppData();
		data.setIcs205Enabled(false);
		RadioChannel channel = channel("Command One");
		channel.setZoneGroup("North zone");
		channel.setFunction("Command");
		channel.setRxFrequency("155.500");
		channel.setIncludeOnForm(false);
		data.getForm205().getChannels().add(channel);
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			JTextField legacy = new JTextField("Legacy talkgroup / phone 555-0100");
			RadioChannelPicker.install(legacy, controller);
			RadioChannelPicker.install(legacy, controller);
			assertEquals("Legacy talkgroup / phone 555-0100", legacy.getText());
			assertEquals(1, legacy.getComponentPopupMenu().getComponentCount());
			assertNotNull(legacy.getActionMap().get("pick-radio-channel"));
			RadioChannelPicker.restoreReference(legacy, channel.getChannelId());
			assertEquals(channel.getChannelId(), RadioChannelPicker.referenceId(legacy));
			legacy.setText("Explicit legacy override");
			assertEquals("", RadioChannelPicker.referenceId(legacy));
			assertEquals(List.of(channel), RadioChannelPicker.matches(controller, "NORTH"));
			assertEquals(List.of(channel), RadioChannelPicker.matches(controller, "155.500"));
			assertTrue(RadioChannelPicker.matches(controller, "missing").isEmpty());
			assertFalse(data.isIcs205Enabled());
			assertFalse(controller.isDirty());
		});
	}

	@Test
	void legacyRadioFieldsBecomeUnverifiedNameChoicesWithoutDeduplicatingPlanIdentities() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().setSafetyOfficerRadio("Legacy command");
		data.getOrganizationalChart().setLiaisonOfficerRadio("Legacy command");
		var additional = new org.sarmanagement.icsforms.model.OrgChartEntry();
		additional.setRadio("Additional organization radio");
		data.getOrganizationalChart().getAdditionalPositions().add(additional);
		var card = new org.sarmanagement.icsforms.model.TCard();
		card.setRadioChannel("Personnel radio");
		data.getTCards().add(card);
		var entry = new org.sarmanagement.icsforms.model.CommunicationEntry();
		entry.setPrimaryContact("Assignment radio / phone");
		data.getForm204().getCommunications().add(entry);
		var station = new org.sarmanagement.icsforms.model.MedicalPlan.AidStation();
		station.setFrequency("Medical legacy frequency");
		data.getMedicalPlan().getAidStations().add(station);
		data.getForm205().getChannels().add(channel("Same display name"));
		data.getForm205().getChannels().add(channel("Same display name"));
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			List<String> suggestions = RadioChannelPicker.legacyLabels(controller);
			assertTrue(suggestions.containsAll(List.of("Legacy command", "Additional organization radio",
					"Personnel radio", "Assignment radio / phone", "Medical legacy frequency")));
			assertEquals(1, suggestions.stream().filter("Legacy command"::equals).count());
			assertEquals(2, RadioChannelPicker.matches(controller, "Same display name").size());
			RadioChannelEditorPanel editor = new RadioChannelEditorPanel(channel(""), suggestions);
			@SuppressWarnings("unchecked")
			Map<RadioChannelEditorPanel.Field, JTextComponent> fields =
					(Map<RadioChannelEditorPanel.Field, JTextComponent>) field(editor, "fields");
			fields.get(RadioChannelEditorPanel.Field.NAME).setText("Personnel radio");
			RadioChannel selected = editor.editedChannel();
			assertEquals("Personnel radio", selected.getChannelName());
			assertEquals("", selected.getRxFrequency());
			assertEquals("", selected.getTxFrequency());
			assertEquals(RadioChannel.TX_POLICY_UNKNOWN, selected.getTxPolicy());
			assertEquals("Personnel radio", card.getRadioChannel());
			assertEquals(2, data.getForm205().getChannels().size());
			assertFalse(controller.isDirty());
		});
	}

	@Test
	void orgRolesAdditionalPositionsAndMedicalFrequenciesOfferPickerWithoutChangingLegacyText() throws Exception {
		AppData data = new AppData();
		data.getOrganizationalChart().setSafetyOfficerName("Safety role");
		data.getOrganizationalChart().setSafetyOfficerRadio("Legacy unknown talkgroup");
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			OrganizationalChartPanel org = new OrganizationalChartPanel(controller);
			org.refreshFromModel();
			JTextField roleRadio = (JTextField) field(org, "safetyOfficerRadioField");
			assertEquals(Boolean.TRUE, roleRadio.getClientProperty("radioChannelPicker"));
			org.pushToModel();
			assertEquals("Legacy unknown talkgroup", data.getOrganizationalChart().getSafetyOfficerRadio());
			JTable positions = (JTable) field(org, "customPositionsTable");
			javax.swing.DefaultCellEditor positionEditor = (javax.swing.DefaultCellEditor)
					positions.getColumnModel().getColumn(3).getCellEditor();
			assertEquals(Boolean.TRUE, ((JTextField) positionEditor.getComponent()).getClientProperty("radioChannelPicker"));
			var agency = field(org, "agencyRepresentatives");
			JTable agencyTable = (JTable) field(agency, "table");
			assertNotNull(agencyTable.getColumnModel().getColumn(2).getCellEditor());
			Ics206Panel medical = new Ics206Panel(controller);
			JTable transport = (JTable) field(medical, "transportTable");
			javax.swing.DefaultCellEditor frequencyEditor = (javax.swing.DefaultCellEditor)
					transport.getColumnModel().getColumn(3).getCellEditor();
			assertEquals(Boolean.TRUE, ((JTextField) frequencyEditor.getComponent()).getClientProperty("radioChannelPicker"));
		});
	}

	@Test
	void pdfShortcutRequestsExplicitRadioFormWithoutChangingWorkspaceAvailability() throws Exception {
		AppData data = new AppData();
		data.setIcs205Enabled(false);
		AppController controller = controller(data);
		List<String> requests = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			Ics205Panel panel = new Ics205Panel(controller);
			panel.setExportRequest(requests::add);
			button(panel, "Export ICS 205 PDF").doClick();
			assertEquals(List.of("ICS 205"), requests);
			assertFalse(data.isIcs205Enabled());
			assertNull(data.getForm205().getPreparedDateTime());
			panel.setExportRequest(null);
			button(panel, "Export ICS 205 PDF").doClick();
			assertEquals(1, requests.size());
		});
	}

	@Test
	void ics204PlanSuggestionAppendsWithoutReplacingExistingEntries() throws Exception {
		AppData data = new AppData();
		var existing = new org.sarmanagement.icsforms.model.CommunicationEntry();
		existing.setName("Existing");
		existing.setPrimaryContact("Legacy radio / phone");
		data.getForm204().getCommunications().add(existing);
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics204Panel panel = new Ics204Panel(controller);
			panel.refreshFromModel();
			JTable communications = (JTable) field(panel, "communicationsTable");
			communications.editCellAt(0, 2);
			((JTextField) communications.getEditorComponent()).setText("Pending legacy radio edit");
			RadioChannel suggestion = channel("Plan channel");
			suggestion.setFunction("Tactical");
			panel.addPlanChannel(suggestion);
			assertEquals(2, data.getForm204().getCommunications().size());
			assertEquals("Pending legacy radio edit", data.getForm204().getCommunications().get(0).getPrimaryContact());
			assertEquals("Plan channel", data.getForm204().getCommunications().get(1).getPrimaryContact());
			assertEquals("Tactical", data.getForm204().getCommunications().get(1).getFunction());
			assertEquals(suggestion.getChannelId(), data.getForm204().getCommunications().get(1).getRadioChannelId());
			communications.editCellAt(1, 2);
			JTextField radio = (JTextField) communications.getEditorComponent();
			assertEquals(suggestion.getChannelId(), RadioChannelPicker.referenceId(radio));
			communications.getCellEditor().stopCellEditing();
			assertEquals(suggestion.getChannelId(), data.getForm204().getCommunications().get(1).getRadioChannelId());
			communications.editCellAt(1, 2);
			radio = (JTextField) communications.getEditorComponent();
			radio.setText("Manual phone / radio override");
			communications.getCellEditor().stopCellEditing();
			assertEquals("", data.getForm204().getCommunications().get(1).getRadioChannelId());
			assertEquals("Manual phone / radio override", data.getForm204().getCommunications().get(1).getPrimaryContact());
		});
	}

	@Test
	void cancelSafePersonnelCopyPreservesStableChannelReferenceAndTextFallback() throws Exception {
		var card = new org.sarmanagement.icsforms.model.TCard();
		card.setPersonName("Person");
		card.setRadioChannel("Recorded label");
		card.setRadioChannelId("stable-channel-id");
		var method = TCardPanel.class.getDeclaredMethod("copyCard", org.sarmanagement.icsforms.model.TCard.class);
		method.setAccessible(true);
		var copy = (org.sarmanagement.icsforms.model.TCard) method.invoke(null, card);
		assertEquals("Recorded label", copy.getRadioChannel());
		assertEquals("stable-channel-id", copy.getRadioChannelId());
		copy.setRadioChannel("Manual override");
		assertEquals("", copy.getRadioChannelId());
		assertEquals("stable-channel-id", card.getRadioChannelId());
	}

	@Test
	void linked204DisplayTracksOnlyExactIdentityAndFallsBackAfterDeletion() throws Exception {
		AppData data = new AppData();
		RadioChannel linked = channel("Duplicate name");
		RadioChannel unrelated = channel("Duplicate name");
		data.getForm205().getChannels().addAll(List.of(linked, unrelated));
		var entry = new org.sarmanagement.icsforms.model.CommunicationEntry();
		entry.setPrimaryContact("Saved fallback");
		entry.setRadioChannelId(linked.getChannelId());
		var legacy = new org.sarmanagement.icsforms.model.CommunicationEntry();
		legacy.setPrimaryContact("Duplicate name");
		data.getForm204().getCommunications().addAll(List.of(entry, legacy));
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			Ics204Panel panel = new Ics204Panel(controller);
			panel.refreshFromModel();
			JTable table = (JTable) field(panel, "communicationsTable");
			java.util.function.IntFunction<String> displayed = row ->
					((javax.swing.JLabel) table.prepareRenderer(table.getCellRenderer(row, 2), row, 2)).getText();
			assertEquals("Duplicate name", displayed.apply(0));
			linked.setChannelName("Renamed exact target");
			assertEquals("Renamed exact target", displayed.apply(0));
			assertEquals("Duplicate name", displayed.apply(1));
			assertEquals("Saved fallback", entry.getPrimaryContact());
			assertEquals("Duplicate name", unrelated.getChannelName());
			data.getForm205().getChannels().remove(linked);
			assertEquals("Saved fallback", displayed.apply(0));
			assertEquals(linked.getChannelId(), entry.getRadioChannelId());
		});
	}

	@Test
	void sarCommunicationReconstructionRetainsOnlyUnchangedExplicitLinks() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				var entry = new org.sarmanagement.icsforms.model.CommunicationEntry();
				entry.setPrimaryContact("Recorded radio");
				entry.setRadioChannelId("stable-channel-id");
				List<Object> fields = new java.util.ArrayList<>();
				var build = SarTaskPanel.class.getDeclaredMethod("communicationEditorPanel", List.class, List.class);
				build.setAccessible(true);
				build.invoke(null, List.of(entry), fields);
				var values = SarTaskPanel.class.getDeclaredMethod("communicationValuesFrom", List.class);
				values.setAccessible(true);
				var preserved = (List<?>) values.invoke(null, fields);
				assertEquals("stable-channel-id",
						((org.sarmanagement.icsforms.model.CommunicationEntry) preserved.get(0)).getRadioChannelId());
				((JTextField) field(fields.get(0), "primaryContactField")).setText("Manual override");
				var changed = (List<?>) values.invoke(null, fields);
				assertEquals("", ((org.sarmanagement.icsforms.model.CommunicationEntry) changed.get(0)).getRadioChannelId());
				assertEquals("stable-channel-id", entry.getRadioChannelId());
			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
		});
	}

	@Test
	void hiddenWorkspaceIsStillExplicitlyExportableInEitherIncidentMode() {
		AppData data = new AppData();
		for (IncidentMode mode : IncidentMode.values()) {
			data.setIncidentMode(mode);
			data.setIcs205Enabled(false);
			assertTrue(MainFrame.isExportFormVisible("ICS 205", data));
			assertFalse(MainFrame.isExportFormSelectedByDefault("ICS 205", data));
			data.setIcs205Enabled(true);
			assertTrue(MainFrame.isExportFormSelectedByDefault("ICS 205", data));
		}
	}

	@Test
	void controllerRequiresExplicitCarryForwardAndClearsPreparationWhenSelected() throws Exception {
		AppData data = new AppData();
		data.getForm205().getChannels().add(channel("Current"));
		data.getForm205().setSpecialInstructions("Carry these instructions");
		data.getForm205().setPreparedByName("Prior preparer");
		data.getForm205().setPreparedBySignature("Prior signature");
		data.getForm205().setPreparedDateTime(LocalDateTime.of(2026, 10, 24, 12, 0));
		data.getForm205().setIapPage("7");
		AppController controller = controller(data);
		SwingUtilities.invokeAndWait(() -> {
			controller.newOperationalPeriod(true);
			assertEquals("Current", data.getForm205().getChannels().get(0).getChannelName());
			assertEquals("Carry these instructions", data.getForm205().getSpecialInstructions());
			assertEquals("Prior preparer", data.getForm205().getPreparedByName());
			assertEquals("", data.getForm205().getPreparedBySignature());
			assertNull(data.getForm205().getPreparedDateTime());
			assertEquals("", data.getForm205().getIapPage());
			controller.newOperationalPeriod();
			assertTrue(data.getForm205().getChannels().isEmpty());
			assertEquals("", data.getForm205().getSpecialInstructions());
		});
	}

	/**
	 * Creates a controller whose test autosave can be stopped.
	 *
	 * @param data incident under test.
	 * @return controller.
	 */
	private AppController controller(AppData data) {
		AppController controller = new AppController(data,
				new LocalRepository(Path.of("target/ics205-panel-test.json")),
				new PdfExportService(new Ics205PdfRenderer()), new IncidentValidator());
		controllers.add(controller);
		return controller;
	}

	/**
	 * Creates a channel with a generated stable identifier.
	 *
	 * @param name channel name.
	 * @return channel.
	 */
	static RadioChannel channel(String name) {
		RadioChannel channel = new RadioChannel();
		channel.setChannelName(name);
		return channel;
	}

	/**
	 * Reads a private Swing control.
	 *
	 * @param target panel or controller.
	 * @param name field name.
	 * @return stored field.
	 */
	static Object field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Collects controls of a given type from the visible editor hierarchy.
	 *
	 * @param root component hierarchy.
	 * @param type control type.
	 * @param matches collected controls.
	 * @param <T> control type.
	 */
	private static <T extends Component> void collectComponents(Component root, Class<T> type, List<T> matches) {
		if (type.isInstance(root)) {
			matches.add(type.cast(root));
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				collectComponents(child, type, matches);
			}
		}
	}

	/**
	 * Finds a named action recursively.
	 *
	 * @param root component hierarchy.
	 * @param label button text.
	 * @return button or null.
	 */
	static JButton button(Component root, String label) {
		if (root instanceof JButton button && label.equals(button.getText())) {
			return button;
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				JButton result = button(child, label);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}
}
