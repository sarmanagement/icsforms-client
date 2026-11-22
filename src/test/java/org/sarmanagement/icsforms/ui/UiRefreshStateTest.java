/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.IncidentLocation;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.model.SarTaskAssignment;
import org.sarmanagement.icsforms.model.RadioChannel;

import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.List;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.InMemoryServerIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies refresh comparisons are detached, read-only, and incident-sensitive. */
class UiRefreshStateTest {
	@TempDir
	Path directory;

	@Test
	void remoteRefreshRetainsCleanTextFocusSelectionAndScroll() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("view"), "Shared")) {
			var store = host.pair();
			var remote = host.pair();
			var initial = remote.loadSnapshot(host.incident());
			initial.data().getForm202().setCommandEmphasis("Line of text for scrolling\n".repeat(80));
			remote.save(host.incident(), initial.data(), initial.revision());
			AppData local = store.loadSnapshot(host.incident()).data();
			AtomicReference<MainFrame> frameRef = new AtomicReference<>();
			AtomicReference<JTextArea> editorRef = new AtomicReference<>();
			AtomicReference<JScrollPane> scrollRef = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(local, store, host.incident(),
						new PdfExportService(), new IncidentValidator(), directory);
				frame.setSize(800, 600);
				frame.setVisible(true);
				frame.openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_202));
				JTextArea editor = (JTextArea) Ics205PanelTest.field(
						Ics205PanelTest.field(frame, "ics202Panel"), "commandEmphasisArea");
				editor.requestFocusInWindow();
				frameRef.set(frame);
				editorRef.set(editor);
				scrollRef.set((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, editor));
			});
			try {
				JTextArea editor = editorRef.get();
				AppController controller = (AppController) Ics205PanelTest.field(frameRef.get(), "controller");
				controller.save();
				for (int attempt = 0; attempt < 100 && !editor.isFocusOwner(); attempt++) {
					Thread.sleep(20);
				}
				assertTrue(editor.isFocusOwner(), "editor did not acquire focus");
				AtomicReference<Point> position = new AtomicReference<>();
				SwingUtilities.invokeAndWait(() -> editor.select(2, 8));
				SwingUtilities.invokeAndWait(() -> { });
				SwingUtilities.invokeAndWait(() -> {
					scrollRef.get().getViewport().setViewPosition(new Point(0, 100));
					position.set(scrollRef.get().getViewport().getViewPosition());
				});
				assertTrue(position.get().y > 0);
				var changed = remote.loadSnapshot(host.incident());
				changed.data().getForm202().setCommandEmphasis("Remote text for scrolling\n".repeat(80));
				remote.save(host.incident(), changed.data(), changed.revision());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
				SwingUtilities.invokeAndWait(() -> {
					assertTrue(editor.getText().startsWith("Remote text"));
					assertEquals(2, editor.getSelectionStart());
					assertEquals(8, editor.getSelectionEnd());
					assertEquals(position.get(), scrollRef.get().getViewport().getViewPosition());
					assertSame(Ics205PanelTest.field(frameRef.get(), "ics202Panel"),
							((JTabbedPane) Ics205PanelTest.field(frameRef.get(), "tabs")).getSelectedComponent());
				});
				assertTrue(editor.isFocusOwner());
			} finally {
				SwingUtilities.invokeAndWait(frameRef.get()::dispose);
			}
		}
	}

	@Test
	void remoteRefreshKeepsLogByIdentityInsteadOfReorderedIndex() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (InMemoryServerIncidentStore store = new InMemoryServerIncidentStore()) {
			AppData initial = new AppData();
			initial.setIapPhase(IapPhase.DURING_OP);
			Ics214Form first = new Ics214Form();
			first.setName("First log");
			Ics214Form second = new Ics214Form();
			second.setName("Second log");
			initial.getActivityLogs().addAll(List.of(first, second));
			var incident = store.create(initial);
			AtomicReference<MainFrame> frameRef = new AtomicReference<>();
			AtomicReference<Ics214Panel> panelRef = new AtomicReference<>();
			AtomicReference<String> selectedId = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(initial, store, incident,
						new PdfExportService(), new IncidentValidator(), directory);
				frame.setVisible(true);
				@SuppressWarnings("unchecked")
				List<Ics214Panel> panels = (List<Ics214Panel>) Ics205PanelTest.field(frame, "ics214Panels");
				Ics214Panel panel = panels.get(0);
				selectedId.set(panel.getCurrentForm().getLogId());
				((JTabbedPane) Ics205PanelTest.field(frame, "tabs")).setSelectedComponent(panel);
				frameRef.set(frame);
				panelRef.set(panel);
			});
			try {
				AppController controller = (AppController) Ics205PanelTest.field(frameRef.get(), "controller");
				controller.save();
				var changed = store.loadSnapshot(incident);
				java.util.Collections.reverse(changed.data().getActivityLogs());
				changed.data().getIncidentContext().setIncidentName("Remote change");
				store.save(incident, changed.data(), changed.revision());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
				SwingUtilities.invokeAndWait(() -> {
					assertEquals(second.getLogId(), controller.getData().getActivityLogs().get(0).getLogId());
					assertSame(panelRef.get(),
							((JTabbedPane) Ics205PanelTest.field(frameRef.get(), "tabs")).getSelectedComponent());
					assertEquals(selectedId.get(), panelRef.get().getCurrentForm().getLogId());
					assertEquals(panelRef.get().getCurrentForm().getName(),
							((JTextField) Ics205PanelTest.field(panelRef.get(), "nameField")).getText());
				});
				var removed = store.loadSnapshot(incident);
				removed.data().getActivityLogs().removeIf(log -> log.getLogId().equals(selectedId.get()));
				store.save(incident, removed.data(), removed.revision());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
				SwingUtilities.invokeAndWait(() -> {
					JTabbedPane tabs = (JTabbedPane) Ics205PanelTest.field(frameRef.get(), "tabs");
					assertEquals(-1, tabs.indexOfComponent(panelRef.get()));
					assertFalse(tabs.getSelectedComponent() instanceof Ics214Panel);
					assertEquals(second.getLogId(), controller.getData().getActivityLogs().get(0).getLogId());
				});
			} finally {
				SwingUtilities.invokeAndWait(frameRef.get()::dispose);
			}
		}
	}

	@Test
	void remoteRefreshDoesNotTransferTableSelectionToReorderedRecords() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (InMemoryServerIncidentStore store = new InMemoryServerIncidentStore()) {
			AppData initial = new AppData();
			IncidentLocation first = new IncidentLocation();
			first.setName("First");
			IncidentLocation second = new IncidentLocation();
			second.setName("Second");
			initial.getIncidentLocations().addAll(List.of(first, second));
			var incident = store.create(initial);
			AtomicReference<MainFrame> frameRef = new AtomicReference<>();
			AtomicReference<JTable> tableRef = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(initial, store, incident, new PdfExportService(),
						new IncidentValidator(), directory);
				frame.setVisible(true);
				JTable table = (JTable) Ics205PanelTest.field(
						Ics205PanelTest.field(frame, "incidentContextPanel"), "locationsTable");
				table.setRowSelectionInterval(0, 0);
				frameRef.set(frame);
				tableRef.set(table);
			});
			try {
				AppController controller = (AppController) Ics205PanelTest.field(frameRef.get(), "controller");
				var changed = store.loadSnapshot(incident);
				java.util.Collections.reverse(changed.data().getIncidentLocations());
				store.save(incident, changed.data(), changed.revision());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
				SwingUtilities.invokeAndWait(() -> {
					assertEquals(1, tableRef.get().getSelectedRow());
					assertEquals("Second", tableRef.get().getValueAt(0, 1));
					assertEquals("First", tableRef.get().getValueAt(tableRef.get().getSelectedRow(), 1));
					frameRef.get().openWorkspace(WorkspaceDestination.of(WorkspaceId.ICS_202));
					assertEquals(List.of("Second", "First"), controller.getData().getIncidentLocations().stream()
							.map(IncidentLocation::getName).toList());
				});
				controller.save();
				var replaced = store.loadSnapshot(incident);
				replaced.data().getIncidentLocations().removeIf(location -> location.getId().equals(first.getId()));
				IncidentLocation replacement = new IncidentLocation();
				replacement.setName("Replacement");
				replaced.data().getIncidentLocations().add(replacement);
				store.save(incident, replaced.data(), replaced.revision());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
				SwingUtilities.invokeAndWait(() -> assertEquals(-1, tableRef.get().getSelectedRow()));
			} finally {
				SwingUtilities.invokeAndWait(frameRef.get()::dispose);
			}
		}
	}

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
		org.sarmanagement.icsforms.TestIdentitySupport.assign(card, null);
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
		ResourceAssignment assignment = new ResourceAssignment("assignment-1");
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
