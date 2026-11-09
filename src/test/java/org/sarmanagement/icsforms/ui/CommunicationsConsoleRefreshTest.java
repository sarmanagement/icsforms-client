/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.CommunicationEntry;
import org.sarmanagement.icsforms.model.MedicalPlan;
import org.sarmanagement.icsforms.model.OrgChartEntry;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.TCard;

import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies shared contact projections update through notifications without incidental invalidation. */
class CommunicationsConsoleRefreshTest {
	@org.junit.jupiter.api.Test
	void eventTypeRenameUpdatesProjectedLogWithoutChangingEntriesOrTaskBoard() throws Exception {
		AppData data = incident();
		var log = new org.sarmanagement.icsforms.model.Ics214Form();
		var entry = new org.sarmanagement.icsforms.model.ActivityLogEntry();
		entry.setEventTypeId("custom-event");
		entry.setNotableActivity("Original narrative");
		log.getActivityLog().add(entry);
		data.getActivityLogs().add(log);
		var type = new org.sarmanagement.icsforms.model.ActivityEventType();
		type.setId("custom-event");
		type.setLabel("EVENT-OLD");
		data.getActivityEventTypes().add(type);
		AppController controller = IncidentStoreTestFixtures.controller(data);
		CommunicationsConsolePanel[] panel = new CommunicationsConsolePanel[1];
		AtomicInteger tasks = new AtomicInteger();
		try {
			SwingUtilities.invokeAndWait(() -> {
				controller.markDirty();
				data.getTCards().forEach(TCard::getResourceId);
				stopAutosave(controller);
				panel[0] = new CommunicationsConsolePanel(controller);
				((javax.swing.JCheckBox) Ics205PanelTest.field(panel[0], "allIncident")).doClick();
				assertTrue(list(panel[0], "logs").getModel().getElementAt(0).toString().contains("EVENT-OLD"));
				list(panel[0], "logs").setSelectedIndex(0);
				watch(list(panel[0], "tasks"), tasks);
			});
			String tracked = trackedInputs(data);
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(controller.commitIfCurrent(controller.captureIncidentBinding(), () ->
						data.getActivityEventTypes().stream().filter(candidate -> "custom-event".equals(candidate.getId()))
								.findFirst().orElseThrow().setLabel("EVENT-NEW")));
				stopAutosave(controller);
				assertEquals(tracked, trackedInputs(data));
			});
			SwingUtilities.invokeAndWait(() -> {
				String row = list(panel[0], "logs").getModel().getElementAt(0).toString();
				assertTrue(row.contains("EVENT-NEW"), row);
				assertFalse(row.contains("EVENT-OLD"), row);
				assertEquals(0, list(panel[0], "logs").getSelectedIndex());
				assertEquals(0, tasks.get());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (panel[0] != null) {
					panel[0].dispose();
				}
				controller.close();
			});
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"aid", "hospital", "transport", "canine", "leader", "organization",
			"form", "management"})
	void contactAdditionEditAndRemovalUpdateOnlyDisplayedProjections(String kind) throws Exception {
		AppData data = incident();
		setContact(data, kind, "MED-OLD");
		AppController controller = IncidentStoreTestFixtures.controller(data);
		CommunicationsConsolePanel[] panel = new CommunicationsConsolePanel[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				controller.markDirty();
				data.getTCards().forEach(TCard::getResourceId);
				stopAutosave(controller);
				panel[0] = new CommunicationsConsolePanel(controller);
				list(panel[0], "tasks").clearSelection();
				assertContains(panel[0], "MED-OLD", true);
			});
			var tracked = trackedInputs(data);
			AtomicInteger boardEvents = new AtomicInteger();
			SwingUtilities.invokeAndWait(() -> watch(list(panel[0], "tasks"), boardEvents));
			for (String value : List.of("MED-NEW", "", "MED-ADDED")) {
				SwingUtilities.invokeAndWait(() -> {
					assertTrue(controller.commitIfCurrent(controller.captureIncidentBinding(),
							() -> setContact(data, kind, value)));
					stopAutosave(controller);
					assertEquals(tracked, trackedInputs(data), "assignments, channels, cards and logs");
				});
				SwingUtilities.invokeAndWait(() -> {
					assertContains(panel[0], "MED-OLD", false);
					assertContains(panel[0], value.isEmpty() ? "MED-NEW" : value, !value.isEmpty());
					assertEquals(0, boardEvents.get(), "contact changes must not reset the task board");
					assertEquals(-1, list(panel[0], "tasks").getSelectedIndex());
				});
			}
			SwingUtilities.invokeAndWait(() -> {
				JList<?> contacts = list(panel[0], "contactChoices");
				contacts.setSelectedIndex(0);
				JTextArea text = (JTextArea) Ics205PanelTest.field(panel[0], "contacts");
				text.select(2, 9);
				AtomicInteger events = new AtomicInteger();
				watch(contacts, events);
				Object selected = contacts.getSelectedValue();
				data.getForm201().setMapSketch("Unrelated sketch");
				controller.markDirty();
				stopAutosave(controller);
				panel[0].putClientProperty("contactBaseline", List.of(events, selected, text.getText()));
			});
			SwingUtilities.invokeAndWait(() -> {
				List<?> baseline = (List<?>) panel[0].getClientProperty("contactBaseline");
				assertEquals(0, ((AtomicInteger) baseline.get(0)).get());
				assertEquals(baseline.get(1), list(panel[0], "contactChoices").getSelectedValue());
				JTextArea text = (JTextArea) Ics205PanelTest.field(panel[0], "contacts");
				assertEquals(baseline.get(2), text.getText());
				assertEquals(2, text.getSelectionStart());
				assertEquals(9, text.getSelectionEnd());
				assertEquals(tracked, trackedInputs(data));
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				if (panel[0] != null) {
					panel[0].dispose();
				}
				controller.close();
			});
		}
	}

	/**
	 * Changes one otherwise untracked shared contact source without modifying tracked console inputs.
	 *
	 * @param data incident under test.
	 * @param kind contact origin.
	 * @param value displayed radio contact, blank removes the contact.
	 */
	private static void setContact(AppData data, String kind, String value) {
		switch (kind) {
			case "aid" -> {
				if (value.isEmpty()) {
					data.getMedicalPlan().getAidStations().clear();
				} else {
					if (data.getMedicalPlan().getAidStations().isEmpty()) {
						MedicalPlan.AidStation station = new MedicalPlan.AidStation();
						station.setName("Aid station");
						data.getMedicalPlan().getAidStations().add(station);
					}
					data.getMedicalPlan().getAidStations().get(0).setFrequency(value);
				}
			}
			case "hospital" -> {
				data.getMedicalPlan().getHospitals().clear();
				if (!value.isEmpty()) {
					MedicalPlan.Hospital hospital = new MedicalPlan.Hospital();
					hospital.setName("Hospital");
					hospital.setFrequency(value);
					data.getMedicalPlan().getHospitals().add(hospital);
				}
			}
			case "transport", "canine" -> {
				List<MedicalPlan.TransportService> rows = "canine".equals(kind)
						? data.getCanineMedicalPlan().getTransportServiceEntries()
						: data.getMedicalPlan().getTransportServices();
				rows.clear();
				if (!value.isEmpty()) {
					MedicalPlan.TransportService service = new MedicalPlan.TransportService();
					service.setService("Transport");
					service.setFrequency(value);
					rows.add(service);
				}
			}
			case "leader" -> data.getMedicalUnitLeaderAssignment().setRadioChannel(value);
			case "organization" -> {
				data.getOrganizationalChart().getAdditionalPositions().clear();
				if (!value.isEmpty()) {
					OrgChartEntry entry = new OrgChartEntry();
					entry.setTitle("Additional contact");
					entry.setName("Contact");
					entry.setRadio(value);
					data.getOrganizationalChart().getAdditionalPositions().add(entry);
				}
			}
			case "form" -> {
				if (data.getAdditionalForms204().isEmpty()) {
					data.getAdditionalForms204().add(new org.sarmanagement.icsforms.model.Ics204Form());
				}
				var form = data.getAdditionalForms204().get(0);
				form.getCommunications().clear();
				if (!value.isEmpty()) {
					CommunicationEntry entry = new CommunicationEntry();
					entry.setName("Form contact");
					entry.setPrimaryContact(value);
					form.getCommunications().add(entry);
				}
			}
			case "management" -> data.getForm204().setBranchDirectorContact(value);
			default -> throw new IllegalArgumentException(kind);
		}
	}

	/**
	 * Creates populated, stable tracked inputs without the contact dependency under test.
	 *
	 * @return populated incident.
	 */
	private static AppData incident() {
		AppData data = new AppData();
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		data.getForm205().getChannels().add(channel);
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("assignment");
		assignment.setResourceIdentifier("Team");
		data.getForm204().getResourcesAssigned().add(assignment);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment");
		task.setResourceIdentifier("Team");
		data.getSarTaskAssignments().add(task);
		return data;
	}

	/**
	 * Snapshots exactly the inputs of the former incomplete guard.
	 *
	 * @param data incident under test.
	 * @return detached tracked inputs.
	 */
	private static String trackedInputs(AppData data) {
		try {
			return java.util.Base64.getEncoder().encodeToString(UiRefreshState.serialize(List.of(
					data.getSarTaskAssignments(), data.getAllAssignmentResources(), data.getForm205(),
					data.getTCards(), data.getActivityLogs())));
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}

	/**
	 * Verifies both the rendered contact text and its projected list labels.
	 *
	 * @param panel console under test.
	 * @param text expected or obsolete text.
	 * @param present whether the text must be displayed.
	 */
	private static void assertContains(CommunicationsConsolePanel panel, String text, boolean present) {
		String displayed = ((JTextArea) Ics205PanelTest.field(panel, "contacts")).getText();
		assertEquals(present, displayed.contains(text), displayed);
		JList<?> list = list(panel, "contactChoices");
		String rows = java.util.stream.IntStream.range(0, list.getModel().getSize())
				.mapToObj(index -> list.getModel().getElementAt(index).toString())
				.collect(java.util.stream.Collectors.joining("\n"));
		assertEquals(present, rows.contains(text), rows);
	}

	/**
	 * Obtains a displayed console list.
	 *
	 * @param panel console under test.
	 * @param name list field name.
	 * @return displayed list.
	 */
	private static JList<?> list(CommunicationsConsolePanel panel, String name) {
		return (JList<?>) Ics205PanelTest.field(panel, name);
	}

	/**
	 * Counts actual list mutations.
	 *
	 * @param list displayed projection.
	 * @param count mutation counter.
	 */
	private static void watch(JList<?> list, AtomicInteger count) {
		list.getModel().addListDataListener(new javax.swing.event.ListDataListener() {
			/** {@inheritDoc} */
			@Override
			public void intervalAdded(javax.swing.event.ListDataEvent event) {
				count.incrementAndGet();
			}

			/** {@inheritDoc} */
			@Override
			public void intervalRemoved(javax.swing.event.ListDataEvent event) {
				count.incrementAndGet();
			}

			/** {@inheritDoc} */
			@Override
			public void contentsChanged(javax.swing.event.ListDataEvent event) {
				count.incrementAndGet();
			}
		});
	}

	/**
	 * Prevents asynchronous persistence from changing the isolated update under test.
	 *
	 * @param controller test controller.
	 */
	private static void stopAutosave(AppController controller) {
		((Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
	}
}
