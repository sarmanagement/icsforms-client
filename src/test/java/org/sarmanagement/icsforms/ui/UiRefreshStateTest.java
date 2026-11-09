/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.RadioChannel;

import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies refresh comparisons are detached, read-only, and incident-sensitive. */
class UiRefreshStateTest {
	@Test
	void failedProjectionDoesNotAcceptAnUndisplayedBaseline() {
		AppData data = new AppData();
		UiRefreshState state = new UiRefreshState();
		assertTrue(state.refresh(data, () -> { }, "initial"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> state.refresh(data, () -> {
					throw new IllegalStateException("projection failed");
				}, "changed"));
		assertTrue(state.refresh(data, () -> { }, "changed"));
		assertFalse(state.refresh(data, () -> {
			throw new AssertionError("unchanged projection");
		}, "changed"));
		assertTrue(state.needsRefresh(data, "not displayed yet"));
		assertTrue(state.needsRefresh(data, "not displayed yet"));
		state.complete();
		assertFalse(state.needsRefresh(data, "not displayed yet"));
	}

	/**
	 * Captures stored model fields for precise no-op assertions.
	 *
	 * @param data model to snapshot.
	 * @return detached model fields.
	 */
	static com.fasterxml.jackson.databind.JsonNode modelState(AppData data) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readTree(UiRefreshState.serialize(data));
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}

	@Test
	void tracksMutationFilterAndIncidentReplacement() {
		AppData data = new AppData();
		UiRefreshState state = new UiRefreshState();
		assertTrue(state.changed(data, data.getForm202(), false));
		assertFalse(state.changed(data, data.getForm202(), false));
		data.getForm202().setCommandEmphasis("Changed");
		assertTrue(state.changed(data, data.getForm202(), false));
		assertTrue(state.changed(data, data.getForm202(), true));
		assertTrue(state.changed(new AppData(), data.getForm202(), true));
	}

	@Test
	void comparisonDoesNotGenerateMissingResourceIdentity() {
		AppData data = new AppData();
		TCard card = new TCard();
		data.getTCards().add(card);
		assertNull(card.peekResourceId());
		UiRefreshState state = new UiRefreshState();
		assertTrue(state.changed(data, card));
		assertFalse(state.changed(data, card));
		assertNull(card.peekResourceId());
	}

	@Test
	void unchangedRoleContentRetainsBoardsAndPreviewRequests() throws Exception {
		AppData data = new AppData();
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setAssignmentId("assignment-1");
		data.getForm204().getResourcesAssigned().add(assignment);
		SarTaskAssignment task = new SarTaskAssignment();
		task.setAssignmentId("assignment-1");
		data.getSarTaskAssignments().add(task);
		data.getForm205().getChannels().add(new RadioChannel());
		AppController controller = IncidentStoreTestFixtures.controller(data);
		controller.syncSarTasks();
		controller.syncTCards();
		data.getTCards().forEach(TCard::getResourceId);
		java.util.Map<String, AtomicInteger> changes = new java.util.LinkedHashMap<>();
		Runnable[] dispose = new Runnable[1];
		Object[] previews = new Object[2];
		Object[] workers = new Object[2];
		SwingUtilities.invokeAndWait(() -> {
			var initial = modelState(data);
			SafetyRolePanel safety = new SafetyRolePanel(controller, destination -> { });
			MedicalUnitLeaderRolePanel medical = new MedicalUnitLeaderRolePanel(controller, destination -> { });
			OperationsRolePanel operations = new OperationsRolePanel(controller, () -> null, id -> { }, id -> { },
					selected -> { }, () -> { }, id -> { }, () -> { }, () -> { });
			IncidentCommandRolePanel command = new IncidentCommandRolePanel(controller, destination -> { });
			CommunicationsRolePanel communications = new CommunicationsRolePanel(controller,
					destination -> { }, () -> { }, () -> { });
			CommunicationsConsolePanel console = (CommunicationsConsolePanel) Ics205PanelTest.field(
					communications, "console");
			var displayed = modelState(data);
			initial.fields().forEachRemaining(entry ->
					assertEquals(entry.getValue(), displayed.get(entry.getKey()), entry.getKey()));
			for (Object[] view : List.of(new Object[] {safety, "worklist"}, new Object[] {medical, "worklist"},
					new Object[] {operations, "taskBoard"}, new Object[] {operations, "availableResources"},
					new Object[] {console, "tasks"}, new Object[] {console, "contactChoices"},
					new Object[] {console, "logs"})) {
				String name = view[0].getClass().getSimpleName() + "." + view[1];
				AtomicInteger count = new AtomicInteger();
				changes.put(name, count);
				watchList((JList<?>) Ics205PanelTest.field(view[0], (String) view[1]), count);
			}
			for (Object[] view : List.of(new Object[] {command, "objectives"},
					new Object[] {communications, "channels"})) {
				String name = view[0].getClass().getSimpleName() + "." + view[1];
				AtomicInteger count = new AtomicInteger();
				changes.put(name, count);
				((JTable) Ics205PanelTest.field(view[0], (String) view[1])).getModel()
						.addTableModelListener(event -> count.incrementAndGet());
			}
			previews[0] = Ics205PanelTest.field(safety, "safetyPreview");
			previews[1] = Ics205PanelTest.field(medical, "humanPreview");
			for (int index = 0; index < previews.length; index++) {
				workers[index] = Ics205PanelTest.field(previews[index], "worker");
			}
			dispose[0] = () -> {
				safety.dispose();
				medical.dispose();
				operations.dispose();
				command.dispose();
				communications.dispose();
			};
			safety.refreshFromModel();
			medical.refreshFromModel();
			operations.refreshFromModel();
			command.refreshFromModel();
			communications.refreshFromModel();
			console.refreshFromModel();
			changes.forEach((name, count) -> assertEquals(0, count.get(), name));
			var beforeCommit = modelState(data);
			data.getForm201().setMapSketch("Unrelated incident information");
			controller.markDirty();
			var afterCommit = modelState(data);
			beforeCommit.fields().forEachRemaining(entry -> {
				if (!"form201".equals(entry.getKey())) {
					assertEquals(entry.getValue(), afterCommit.get(entry.getKey()), entry.getKey());
				}
			});
		});
		try {
			SwingUtilities.invokeAndWait(() -> {
				changes.forEach((name, count) -> assertEquals(0, count.get(), name));
				for (int index = 0; index < previews.length; index++) {
					assertSame(workers[index], Ics205PanelTest.field(previews[index], "worker"));
				}
			});
		} finally {
			SwingUtilities.invokeAndWait(dispose[0]);
			controller.close();
		}
	}

	/**
	 * Counts every structural or content notification from a displayed list.
	 *
	 * @param list displayed role worklist.
	 * @param changes notification counter.
	 */
	static void watchList(JList<?> list, AtomicInteger changes) {
		list.getModel().addListDataListener(new ListDataListener() {
			/** {@inheritDoc} */
			@Override
			public void intervalAdded(ListDataEvent event) {
				changes.incrementAndGet();
			}

			/** {@inheritDoc} */
			@Override
			public void intervalRemoved(ListDataEvent event) {
				changes.incrementAndGet();
			}

			/** {@inheritDoc} */
			@Override
			public void contentsChanged(ListDataEvent event) {
				changes.incrementAndGet();
			}
		});
	}
}
