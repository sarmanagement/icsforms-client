/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.model.IncidentLocation;
import org.sarmanagement.icsforms.model.IncidentObjective;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.OpenActionItem;
import org.sarmanagement.icsforms.model.SafetyAnalysisEntry;
import org.sarmanagement.icsforms.model.SafetyMessageBlock;
import org.sarmanagement.icsforms.model.VetFacility;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JTabbedPane;
import javax.swing.DefaultCellEditor;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Cross-form contracts: visiting or flushing another editor must not erase entered incident content.
 */
class MainFrameDataRetentionTest {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
	private static final List<String> CONTENT_PROPERTIES = List.of("incidentContext", "organizationalChart",
			"form201", "form202", "form204", "additionalForms204", "form205", "form207", "form208", "form215a",
			"medicalUnitLeaderAssignment", "medicalPlan", "canineMedicalPlan", "activityLogs", "sarTaskAssignments",
			"clueLogEntries", "tcards", "openActionItems", "vetDirectory", "stagingLocation", "incidentLocations");
	@TempDir
	Path directory;

	@ParameterizedTest
	@EnumSource(IncidentMode.class)
	void editsAndClearingSurviveAllWorkspacesFullFlushesAndReload(IncidentMode mode) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory.resolve(mode.name()))) {
			AppData data = populatedIncident(mode);
			var id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
						new IncidentValidator(), directory);
				try {
					AppController controller = (AppController) field(frame, "controller");
					List<EditorCase> cases = editorCases();
					for (String prefix : List.of("Entered", "Replaced", "")) {
						for (EditorCase editor : cases) {
							frame.openWorkspace(destination(frame, editor.destination().workspaceId()));
							Object panel = editorPanel(frame, editor);
							for (String name : editor.fields()) {
								JTextComponent input = (JTextComponent) field(panel, name);
								assertTrue(input.isEditable(), editor.panel() + "." + name);
								input.setText(value(prefix, editor, name));
							}
							frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.SHARED));
							frame.openWorkspace(destination(frame, editor.destination().workspaceId()));
							assertEditor(frame, editor, prefix);
						}
						for (EditorCase editor : cases) {
							assertEditor(frame, editor, prefix);
						}
						// Snapshot complete persisted content, not only the selected example fields.
						JsonNode expected = content(controller.getData());
						for (WorkspaceId workspace : WorkspaceId.values()) {
							frame.openWorkspace(destination(frame, workspace));
							assertEquals(expected, content(controller.getData()), "Typed navigation to " + workspace + " in " + mode);
						}
						JTabbedPane tabs = (JTabbedPane) field(frame, "tabs");
						for (int index = 0; index < tabs.getTabCount(); index++) {
							tabs.setSelectedIndex(index);
							assertEquals(expected, content(controller.getData()), "Tab navigation in " + mode);
						}
						controller.save();
						assertEquals(expected, content(store.load(id)), "Persisted content after " + prefix);
						controller.setData(store.load(id));
						var refresh = MainFrame.class.getDeclaredMethod("refreshFromModel");
						refresh.setAccessible(true);
						refresh.invoke(frame);
						for (EditorCase editor : cases) {
							assertEditor(frame, editor, prefix);
						}
					}
				} catch (Exception exception) {
					throw new AssertionError(exception);
				} finally {
					((Timer) field(field(frame, "controller"), "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		}
	}

	@ParameterizedTest
	@EnumSource(InlineTable.class)
	void pendingInlineEditsSurviveNavigationAndPersistence(InlineTable source) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = populatedIncident(IncidentMode.SAR);
			var id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
						new IncidentValidator(), directory);
				try {
					for (String text : List.of("Pending inline edit", "")) {
						frame.openWorkspace(source.destination());
						JTable table = (JTable) field(field(frame, source.panel), source.table);
						assertTrue(table.getRowCount() > 0, source.name());
						table.getColumnModel().getColumn(source.column).setCellEditor(new DefaultCellEditor(new JTextField()));
						assertTrue(table.editCellAt(0, source.column), source.name());
						((JTextComponent) table.getEditorComponent()).setText(text);
						frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ORG_CHART));
						frame.openWorkspace(source.destination());
						assertFalse(table.isEditing(), "Flush must finish the pending editor");
						assertEquals(text, table.getValueAt(0, source.column), source.name());
						assertEquals(text, source.persisted(data), "Canonical data for " + source);
						store.save(id, data);
						assertEquals(text, source.persisted(store.load(id)), "Saved data for " + source);
					}
				} catch (Exception exception) {
					throw new AssertionError(exception);
				} finally {
					((Timer) field(field(frame, "controller"), "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		}
	}

	@ParameterizedTest
	@EnumSource(InlineTable.class)
	void rejectedInlineEditBlocksFlushWithoutDiscardingInput(InlineTable source) throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = populatedIncident(IncidentMode.SAR);
			var id = store.create(data);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(data, store, id, new PdfExportService(),
						new IncidentValidator(), directory);
				try {
					frame.openWorkspace(source.destination());
					Object panel = field(frame, source.panel);
					JTable table = (JTable) field(panel, source.table);
					String previous = source.persisted(data);
					boolean[] reject = {true};
					table.getColumnModel().getColumn(source.column).setCellEditor(new DefaultCellEditor(new JTextField()) {
						/** {@inheritDoc} */
						@Override
						public boolean stopCellEditing() {
							return !reject[0] && super.stopCellEditing();
						}
					});
					assertTrue(table.editCellAt(0, source.column));
					((JTextComponent) table.getEditorComponent()).setText("Uncommitted value");
					var flush = panel.getClass().getMethod("pushToModel");
					var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> flush.invoke(panel));
					assertInstanceOf(IllegalStateException.class, failure.getCause(), source.name());
					assertTrue(table.isEditing(), source.name());
					assertEquals("Uncommitted value", ((JTextComponent) table.getEditorComponent()).getText());
					assertEquals(previous, source.persisted(data));
					reject[0] = false;
					flush.invoke(panel);
					assertFalse(table.isEditing());
					assertEquals("Uncommitted value", source.persisted(data));
				} catch (Exception exception) {
					throw new AssertionError(exception);
				} finally {
					((Timer) field(field(frame, "controller"), "autosaveTimer")).stop();
					frame.dispose();
				}
			});
		}
	}

	/**
	 * Populates nonempty independent collections so a stale refresh cannot silently pass an empty-fixture test.
	 *
	 * @param mode operating mode under test.
	 * @return incident with representative form rows, activity, clue, task and personnel data.
	 */
	private static AppData populatedIncident(IncidentMode mode) {
		AppData data = new AppData();
		data.setIncidentMode(mode);
		data.setIcs205Enabled(true);
		data.setOpenActionTrackerEnabled(true);
		data.getIncidentContext().setIncidentName("Retention exercise");
		data.getIncidentContext().setCurrentUser("Fixture preparer");
		data.getIncidentContext().setCurrentUserPositionTitle("Documentation Unit Leader");
		data.getIncidentContext().setOperationalPeriodStart(LocalDateTime.of(2026, 10, 25, 8, 0));
		data.getIncidentContext().setOperationalPeriodEnd(LocalDateTime.of(2026, 10, 25, 20, 0));
		data.getForm201().getCurrentActions().add(new Ics201Form.ActionEntry());
		data.getForm201().getResources().add(new Ics201Form.ResourceSummaryEntry());
		data.getForm202().setStructuredObjectives(List.of(new IncidentObjective("Retain objective", 0)));
		data.getMedicalPlan().getAidStations().add(new MedicalPlan.AidStation());
		data.getMedicalPlan().getTransportServices().add(new MedicalPlan.TransportService());
		data.getMedicalPlan().getHospitals().add(new MedicalPlan.Hospital());
		data.getCanineMedicalPlan().getTransportServiceEntries().add(new MedicalPlan.TransportService());
		VetFacility vet = new VetFacility();
		vet.setName("Retain directory hospital");
		vet.setAddress("100 Fictional Way");
		data.getVetDirectory().getFacilities().add(vet);
		data.getCanineMedicalPlan().getSelectedFacilities().add(vet.copy());
		OpenActionItem action = new OpenActionItem();
		action.setItem("Retain action description");
		action.setPoc("Fixture responder");
		data.getOpenActionItems().add(action);
		SafetyAnalysisEntry hazard = new SafetyAnalysisEntry();
		hazard.setIncidentArea("Retain analysis area");
		hazard.setHazardsRisks("Retain hazard");
		hazard.setMitigations("Retain mitigation");
		data.getForm215a().getEntries().add(hazard);
		SafetyMessageBlock block = new SafetyMessageBlock();
		block.setIncidentArea("Retain message area");
		block.setHazard("Retain message hazard");
		data.getForm208().getHazardBlocks().add(block);
		IncidentLocation location = new IncidentLocation();
		location.setName("Retain staging location");
		data.getIncidentLocations().add(location);
		CommunicationEntry communication = new CommunicationEntry();
		communication.setName("Dispatch");
		communication.setPrimaryContact("Command 1");
		data.getForm204().getCommunications().add(communication);
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command 1");
		channel.setRxFrequency("155.123456");
		channel.setTxPolicy(RadioChannel.TX_POLICY_RECEIVE_ONLY);
		data.getForm205().getChannels().add(channel);
		Ics214Form log = new Ics214Form();
		log.setName("Independent incident log");
		ActivityLogEntry activity = new ActivityLogEntry();
		activity.setNotableActivity("Retain activity details");
		log.getActivityLog().add(activity);
		data.getActivityLogs().add(log);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignment("Retain assignment instructions");
		task.setResourceIdentifier("Team retention");
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setResourceIdentifier("Team retention");
		task.setAssignmentId(assignment.getAssignmentId());
		data.getForm204().getResourcesAssigned().add(assignment);
		data.getSarTaskAssignments().add(task);
		ClueLogEntry clue = new ClueLogEntry();
		clue.setDescription("Retain independently entered clue");
		clue.setLocation("Trail junction");
		data.getClueLogEntries().add(clue);
		TCard person = new TCard();
		person.setCardType(TCardType.PERSONNEL);
		person.setPersonName("Fixture responder");
		person.setPhoneNumber("555-0198");
		person.setRadioChannel("Command 1");
		data.getTCards().add(person);
		return data;
	}

	/**
	 * Lists independent editable scalar fields across form families; linked fields have separate role-matrix tests.
	 *
	 * @return cross-form edit fixtures.
	 */
	private static List<EditorCase> editorCases() {
		return List.of(
				new EditorCase("incidentContextPanel", WorkspaceDestination.of(WorkspaceId.SHARED), List.of("taskMapField")),
				new EditorCase("organizationalChartPanel", WorkspaceDestination.of(WorkspaceId.ORG_CHART),
						List.of("pioNameField", "pioRadioField", "pioPhoneField")),
				new EditorCase("ics201Panel", WorkspaceDestination.of(WorkspaceId.ICS_201),
						List.of("incidentNumberField", "mapSketchArea", "situationSummaryArea", "objectivesArea")),
				new EditorCase("ics202Panel", WorkspaceDestination.of(WorkspaceId.ICS_202),
						List.of("objectiveTextArea", "commandEmphasisArea", "situationalAwarenessArea", "additionalFormsArea")),
				new EditorCase("ics204Panel", WorkspaceDestination.of(WorkspaceId.ICS_204),
						List.of("sharedAssignmentArea", "specialInstructionsArea")),
				new EditorCase("ics205Panel", WorkspaceDestination.of(WorkspaceId.ICS_205),
						List.of("instructions", "preparedBy")),
				new EditorCase("ics214Panels", WorkspaceDestination.activityLog("existing-log"),
						List.of("nameField", "icsPositionField", "homeAgencyField", "preparedByNameField", "preparedByPositionField")),
				new EditorCase("medicalPlanPanel", WorkspaceDestination.humanMedicalPlan(),
						List.of("call911", "procedures", "airOperations", "medicalContact")),
				new EditorCase("canineMedicalPanel", WorkspaceDestination.canineMedicalPlan(),
						List.of("procedures", "aidStations", "transportServices", "capabilityNotes", "poisonContact", "onsiteVet")),
				new EditorCase("safetyPanel", WorkspaceDestination.of(WorkspaceId.SAFETY),
						List.of("incidentNumberField", "safetyOfficerField", "operationsChiefField", "freeText",
								"generalRequirements", "sitePlanName", "sitePlanLocation", "sitePlanRevision")));
	}

	/**
	 * Asserts that all explicitly edited fields survived the complete cross-panel lifecycle.
	 *
	 * @param frame incident frame.
	 * @param editor tested editor.
	 * @param prefix edit phase, blank for intentional clearing.
	 */
	private static void assertEditor(MainFrame frame, EditorCase editor, String prefix) {
		Object panel = editorPanel(frame, editor);
		for (String name : editor.fields()) {
			assertEquals(value(prefix, editor, name), ((JTextComponent) field(panel, name)).getText(),
					editor.panel() + "." + name);
		}
	}

	/**
	 * Returns the existing editor, including the first independent activity-log instance.
	 *
	 * @param frame incident frame.
	 * @param editor editor identity.
	 * @return active editor component.
	 */
	private static Object editorPanel(MainFrame frame, EditorCase editor) {
		Object panel = field(frame, editor.panel());
		return panel instanceof List<?> logs ? logs.get(0) : panel;
	}

	/**
	 * Creates distinguishable entered content or an intentional empty value.
	 *
	 * @param prefix edit phase.
	 * @param editor editor identity.
	 * @param name control identity.
	 * @return expected user content.
	 */
	private static String value(String prefix, EditorCase editor, String name) {
		return prefix.isEmpty() ? "" : prefix + " " + editor.panel() + " " + name;
	}

	/**
	 * Captures persisted content while omitting only document bookkeeping and view preferences.
	 *
	 * @param data incident document.
	 * @return detached form-content snapshot with every configured property present.
	 */
	private static JsonNode content(AppData data) {
		JsonNode serialized = MAPPER.valueToTree(data);
		ObjectNode result = MAPPER.createObjectNode();
		for (String property : CONTENT_PROPERTIES) {
			assertTrue(serialized.has(property), "Missing retention coverage property " + property);
			result.set(property, serialized.get(property));
		}
		return result;
	}

	/**
	 * Resolves base, nested medical and per-instance activity-log destinations.
	 *
	 * @param frame incident frame.
	 * @param workspace workspace to visit.
	 * @return existing workspace destination.
	 */
	private static WorkspaceDestination destination(MainFrame frame, WorkspaceId workspace) {
		return switch (workspace) {
			case HUMAN_MEDICAL_PLAN -> WorkspaceDestination.humanMedicalPlan();
			case CANINE_MEDICAL_PLAN -> WorkspaceDestination.canineMedicalPlan();
			case ACTIVITY_LOG -> {
				Map<?, ?> logs = (Map<?, ?>) field(frame, "activityLogFormsById");
				var first = ((AppController) field(frame, "controller")).getData().getActivityLogs().get(0);
				yield WorkspaceDestination.activityLog(logs.entrySet().stream()
						.filter(entry -> entry.getValue() == first).findFirst().orElseThrow().getKey().toString());
			}
			default -> WorkspaceDestination.of(workspace);
		};
	}

	/**
	 * Reads a named Swing control or controller for integration assertions.
	 *
	 * @param owner object declaring the field.
	 * @param name declared field name.
	 * @return field value.
	 */
	private static Object field(Object owner, String name) {
		try {
			Field declared = owner.getClass().getDeclaredField(name);
			declared.setAccessible(true);
			return declared.get(owner);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Declares an editor and its independently editable text fields.
	 *
	 * @param panel MainFrame panel field.
	 * @param destination workspace navigation target.
	 * @param fields editable controls.
	 */
	private record EditorCase(String panel, WorkspaceDestination destination, List<String> fields) {
	}

	/** Inline-editing tables whose live cell editors must be committed before any navigation or save. */
	private enum InlineTable {
		BRIEFING_ACTION("ics201Panel", "actionTable", 1, WorkspaceId.ICS_201),
		BRIEFING_RESOURCE("ics201Panel", "resourceSummaryTable", 0, WorkspaceId.ICS_201),
		COMMUNICATIONS("ics204Panel", "communicationsTable", 0, WorkspaceId.ICS_204),
		AID_STATION("medicalPlanPanel", "aidTable", 0, WorkspaceId.HUMAN_MEDICAL_PLAN),
		HUMAN_TRANSPORT("medicalPlanPanel", "transportTable", 0, WorkspaceId.HUMAN_MEDICAL_PLAN),
		HOSPITAL("medicalPlanPanel", "hospitalTable", 0, WorkspaceId.HUMAN_MEDICAL_PLAN),
		CANINE_TRANSPORT("canineMedicalPanel", "transportTable", 0, WorkspaceId.CANINE_MEDICAL_PLAN),
		VET_DIRECTORY("canineMedicalPanel", "directoryTable", 1, WorkspaceId.CANINE_MEDICAL_PLAN),
		VET_SELECTION("canineMedicalPanel", "selectionTable", 0, WorkspaceId.CANINE_MEDICAL_PLAN),
		CLUE("clueLogPanel", "table", 3, WorkspaceId.CLUE_LOG),
		TASK("sarTaskPanel", "table", 1, WorkspaceId.SAR_TASKS),
		LOCATION("incidentContextPanel", "locationsTable", 1, WorkspaceId.SHARED);

		private final String panel;
		private final String table;
		private final int column;
		private final WorkspaceId workspace;

		/**
		 * Identifies the UI control and persisted value covered by a pending-edit case.
		 *
		 * @param panel MainFrame panel field.
		 * @param table table field in that panel.
		 * @param column editable text column.
		 * @param workspace owning workspace.
		 */
		InlineTable(String panel, String table, int column, WorkspaceId workspace) {
			this.panel = panel;
			this.table = table;
			this.column = column;
			this.workspace = workspace;
		}

		/**
		 * Returns the correctly typed navigation target.
		 *
		 * @return owning editor destination.
		 */
		WorkspaceDestination destination() {
			return switch (workspace) {
				case HUMAN_MEDICAL_PLAN -> WorkspaceDestination.humanMedicalPlan();
				case CANINE_MEDICAL_PLAN -> WorkspaceDestination.canineMedicalPlan();
				default -> WorkspaceDestination.of(workspace);
			};
		}

		/**
		 * Reads the independently persisted cell value rather than trusting the visible editor.
		 *
		 * @param data incident model, optionally reloaded from disk.
		 * @return stored text for the edited row.
		 */
		String persisted(AppData data) {
			return switch (this) {
				case BRIEFING_ACTION -> data.getForm201().getCurrentActions().get(0).getActions();
				case BRIEFING_RESOURCE -> data.getForm201().getResources().get(0).getResource();
				case COMMUNICATIONS -> data.getForm204().getCommunications().get(0).getName();
				case AID_STATION -> data.getMedicalPlan().getAidStations().get(0).getName();
				case HUMAN_TRANSPORT -> data.getMedicalPlan().getTransportServices().get(0).getService();
				case HOSPITAL -> data.getMedicalPlan().getHospitals().get(0).getName();
				case CANINE_TRANSPORT -> data.getCanineMedicalPlan().getTransportServiceEntries().get(0).getService();
				case VET_DIRECTORY -> data.getVetDirectory().getFacilities().get(0).getName();
				case VET_SELECTION -> data.getCanineMedicalPlan().getSelectedFacilities().get(0).getName();
				case CLUE -> data.getClueLogEntries().get(0).getLocation();
				case TASK -> data.getSarTaskAssignments().get(0).getAssignmentTeamNumber();
				case LOCATION -> data.getIncidentLocations().get(0).getName();
			};
		}
	}
}
