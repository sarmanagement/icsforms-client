/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ClueLogEntry;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.validation.IncidentValidator;

/** Crosses displayed clue editing, controller persistence and a real shared host. */
class ConnectedWorkflowAcceptanceTest {
	@TempDir
	Path directory;

	@Test
	void twoDisplayedClientsMergeClueEditsAndRefreshTheirTables() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			seedClue(host);
			initialize(host);
			RemoteIncidentStore a = host.pair();
			RemoteIncidentStore b = host.pair();
			MainFrame first = window(a, host);
			MainFrame second = window(b, host);
			try {
				edit(first, 4, "Description from A");
				edit(second, 3, "Location from B");
				controller(first).save();
				controller(second).save();
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller(first).refreshFromRemote());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller(second).refreshFromRemote());
				assertTable(first, "Description from A", "Location from B");
				assertTable(second, "Description from A", "Location from B");
				ClueLogEntry persisted = host.backing().load(host.incident()).getClueLogEntries().get(0);
				assertEquals("Description from A", persisted.getDescription());
				assertEquals("Location from B", persisted.getLocation());
			} finally {
				close(first);
				close(second);
			}
		}
	}

	@Test
	void separateClueAndPlanningWorkflowsMergeAndRefreshBothDisplays() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			seedClue(host);
			initialize(host);
			MainFrame first = window(host.pair(), host);
			MainFrame second = window(host.pair(), host);
			try {
				edit(first, 4, "Clue from operations");
				SwingUtilities.invokeAndWait(() -> {
					Ics202Panel panel = (Ics202Panel) Ics205PanelTest.field(second, "ics202Panel");
					((JTextArea) Ics205PanelTest.field(panel, "commandEmphasisArea")).setText("Planning emphasis");
					assertTrue(panel.pushToModel());
				});
				controller(first).save();
				controller(second).save();
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller(first).refreshFromRemote());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller(second).refreshFromRemote());
				for (MainFrame frame : new MainFrame[] {first, second}) {
					assertTable(frame, "Clue from operations", "Original location");
					SwingUtilities.invokeAndWait(() -> {
						Object panel = Ics205PanelTest.field(frame, "ics202Panel");
						assertEquals("Planning emphasis",
								((JTextArea) Ics205PanelTest.field(panel, "commandEmphasisArea")).getText());
					});
				}
				var persisted = host.backing().load(host.incident());
				assertEquals("Planning emphasis", persisted.getForm202().getCommandEmphasis());
				assertEquals("Clue from operations", persisted.getClueLogEntries().get(0).getDescription());
			} finally {
				close(first);
				close(second);
			}
		}
	}

	@Test
	void twoClientsMergeActivityClueResourcePlanningAndRootChanges() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			seedClue(host);
			seedActivityAndResource(host);
			initialize(host);
			MainFrame first = window(host.pair(), host);
			MainFrame second = window(host.pair(), host);
			try {
				editWorkflows(first, second);
				controller(first).save();
				controller(second).save();
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller(first).refreshFromRemote());
				assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller(second).refreshFromRemote());
				for (MainFrame frame : List.of(first, second)) {
					assertWorkflowViews(frame);
				}
				var persisted = host.backing().load(host.incident());
				assertEquals("Activity from A", persisted.getActivityLogs().get(0).getActivityLog().get(0)
						.getNotableActivity());
				assertEquals("Clue from B", persisted.getClueLogEntries().get(0).getDescription());
				assertTrue(persisted.getTCards().stream().anyMatch(card -> "Responder from B".equals(card.getPersonName())));
				assertEquals("Planning from A", persisted.getForm202().getCommandEmphasis());
				assertEquals(IapPhase.DURING_OP, persisted.getIapPhase());
			} finally {
				close(first);
				close(second);
			}
		}
	}

	@Test
	void twoOfflineClientsRestartAndReplayDifferentWorkflows() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			seedClue(host);
			seedActivityAndResource(host);
			initialize(host);
			Path cacheA = directory.resolve("cache-a");
			Path cacheB = directory.resolve("cache-b");
			RemoteIncidentGateway gatewayA = pairGateway(host);
			RemoteIncidentGateway gatewayB = pairGateway(host);
			String fingerprint = gatewayA.authorityFingerprint().orElseThrow();
			assertEquals(fingerprint, gatewayB.authorityFingerprint().orElseThrow());
			RemoteIncidentStore a = new RemoteIncidentStore(gatewayA, host.incident(), cacheA);
			RemoteIncidentStore b = new RemoteIncidentStore(gatewayB, host.incident(), cacheB);
			MainFrame first = window(a, host);
			MainFrame second = window(b, host);
			AtomicReference<String> createdId = new AtomicReference<>();
			try {
				a.detachGateway().close();
				b.detachGateway().close();
				editWorkflows(first, second);
				controller(first).save();
				controller(second).save();
				SwingUtilities.invokeAndWait(() -> {
					ClueLogEntry created = new ClueLogEntry();
					created.setDescription("Created offline");
					createdId.set(created.getEntryId());
					controller(first).getData().getClueLogEntries().add(created);
					controller(first).markDirty();
				});
				controller(first).save();
				SwingUtilities.invokeAndWait(() -> {
					controller(first).getData().getClueLogEntries().stream()
							.filter(clue -> createdId.get().equals(clue.getEntryId())).findFirst().orElseThrow()
							.setDescription("Edited after creation");
					controller(first).markDirty();
				});
				controller(first).save();
				assertEquals(3, a.pendingOperations().size());
				assertEquals(1, b.pendingOperations().size());
				assertEquals("Original activity", host.backing().load(host.incident())
						.getActivityLogs().get(0).getActivityLog().get(0).getNotableActivity());
			} finally {
				close(first);
				close(second);
			}
			try (RemoteIncidentStore restoredA = RemoteIncidentStore.openOffline(cacheA, fingerprint, host.incident());
					RemoteIncidentStore restoredB = RemoteIncidentStore.openOffline(cacheB, fingerprint, host.incident())) {
				assertEquals(3, restoredA.pendingOperations().size());
				assertEquals(1, restoredB.pendingOperations().size());
				assertEquals("Activity from A", restoredA.loadSnapshot(host.incident()).data()
						.getActivityLogs().get(0).getActivityLog().get(0).getNotableActivity());
				assertEquals("Clue from B", restoredB.loadSnapshot(host.incident()).data()
						.getClueLogEntries().get(0).getDescription());
				restoredA.attachGateway(pairGateway(host));
				restoredB.attachGateway(pairGateway(host));
				restoredA.replayPending();
				restoredB.replayPending();
				assertTrue(restoredA.pendingOperations().isEmpty());
				assertTrue(restoredB.pendingOperations().isEmpty());
				restoredA.replayPending();
				restoredB.replayPending();
				assertEquals(3, restoredA.acceptedReceiptCount());
				assertEquals(1, restoredB.acceptedReceiptCount());
				var persisted = host.backing().load(host.incident());
				assertEquals("Activity from A", persisted.getActivityLogs().get(0).getActivityLog().get(0)
						.getNotableActivity());
				assertEquals("Clue from B", persisted.getClueLogEntries().get(0).getDescription());
				assertEquals(1, persisted.getClueLogEntries().stream()
						.filter(clue -> createdId.get().equals(clue.getEntryId())).count());
				assertEquals("Edited after creation", persisted.getClueLogEntries().stream()
						.filter(clue -> createdId.get().equals(clue.getEntryId())).findFirst().orElseThrow()
						.getDescription());
				assertTrue(persisted.getTCards().stream().anyMatch(card -> "Responder from B".equals(card.getPersonName())));
				assertEquals("Planning from A", persisted.getForm202().getCommandEmphasis());
				assertEquals(IapPhase.DURING_OP, persisted.getIapPhase());
				MainFrame reopenedA = window(restoredA, host);
				MainFrame reopenedB = window(restoredB, host);
				try {
					assertWorkflowViews(reopenedA);
					assertWorkflowViews(reopenedB);
					SwingUtilities.invokeAndWait(() -> {
						for (MainFrame frame : List.of(reopenedA, reopenedB)) {
							assertEquals(1, java.util.stream.IntStream.range(0, table(frame).getRowCount())
									.filter(row -> "Edited after creation".equals(table(frame).getValueAt(row, 4)))
									.count());
						}
					});
				} finally {
					close(reopenedA);
					close(reopenedB);
				}
			}
		}
	}

	@Test
	void displayedOfflineDraftAndDependentEditSurviveRestartAndConflictResolution() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path cache = directory.resolve("cache");
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			seedClue(host);
			initialize(host);
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			RemoteIncidentStore durable = new RemoteIncidentStore(gateway, host.incident(), cache);
			MainFrame offlineWindow = window(durable, host);
			RemoteIncidentGateway otherGateway = pairGateway(host);
			try (RemoteIncidentStore other = new RemoteIncidentStore(otherGateway, host.incident(),
					directory.resolve("other-cache"))) {
				MainFrame otherWindow = window(other, host);
				try {
					durable.detachGateway().close();
					other.detachGateway().close();
					edit(offlineWindow, 4, "Offline description");
					controller(offlineWindow).save();
					edit(offlineWindow, 3, "Offline location");
					controller(offlineWindow).save();
					assertEquals(2, durable.pendingOperations().size());
					edit(otherWindow, 4, "Host description");
					controller(otherWindow).save();
					assertEquals(1, other.pendingOperations().size());
					assertEquals("Original description", host.backing().load(host.incident())
							.getClueLogEntries().get(0).getDescription());
					other.attachGateway(pairGateway(host));
					other.replayPending();
					assertTrue(other.pendingOperations().isEmpty());
					durable.attachGateway(pairGateway(host));
					durable.replayPending();
					assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT,
							durable.pendingOperations().get(0).status());
					assertEquals(2, durable.pendingOperations().size());
					assertEquals("Host description", host.backing().load(host.incident())
							.getClueLogEntries().get(0).getDescription());
				} finally {
					close(otherWindow);
				}
			} finally {
				close(offlineWindow);
			}
			try (RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, host.incident())) {
				MainFrame restoredWindow = window(restored, host);
				try {
					assertTable(restoredWindow, "Offline description", "Offline location");
					assertEquals(2, restored.pendingOperations().size());
					var conflict = restored.pendingOperations().get(0);
					assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, conflict.status());
					assertFalse(conflict.conflicts().isEmpty());
					restored.attachGateway(pairGateway(host));
					Map<RemoteIncidentStore.ConflictField, RemoteIncidentStore.ConflictChoice> choices =
							conflict.conflicts().stream().collect(java.util.stream.Collectors.toMap(
									field -> new RemoteIncidentStore.ConflictField(field.key(), field.field()),
									field -> RemoteIncidentStore.ConflictChoice.THEIRS));
					restored.resolvePending(conflict.operationId(), choices);
					restored.replayPending();
					assertTrue(restored.pendingOperations().isEmpty());
					assertEquals(AppController.RemoteRefreshOutcome.REFRESHED,
							controller(restoredWindow).refreshFromRemote());
					assertTable(restoredWindow, "Host description", "Offline location");
					ClueLogEntry persisted = host.backing().load(host.incident()).getClueLogEntries().get(0);
					assertEquals("Host description", persisted.getDescription());
					assertEquals("Offline location", persisted.getLocation());
				} finally {
					close(restoredWindow);
				}
			}
		}
	}

	/**
	 * Adds a shared row before opening the client windows.
	 *
	 * @param host real host fixture.
	 */
	private static void seedClue(HostedIncidentFixture host) {
		try (RemoteIncidentStore store = host.pair()) {
			var snapshot = store.loadSnapshot(host.incident());
			ClueLogEntry clue = new ClueLogEntry();
			clue.setDescription("Original description");
			clue.setLocation("Original location");
			snapshot.data().getClueLogEntries().add(clue);
			store.save(host.incident(), snapshot.data(), snapshot.revision());
		}
	}

	/**
	 * Adds an activity row and personnel card to the shared incident.
	 *
	 * @param host real host fixture.
	 */
	private static void seedActivityAndResource(HostedIncidentFixture host) {
		try (RemoteIncidentStore store = host.pair()) {
			var snapshot = store.loadSnapshot(host.incident());
			Ics214Form log = new Ics214Form();
			ActivityLogEntry entry = new ActivityLogEntry();
			entry.setNotableActivity("Original activity");
			log.getActivityLog().add(entry);
			snapshot.data().getActivityLogs().add(log);
			TCard card = new TCard();
			card.setPersonName("Original responder");
			snapshot.data().getTCards().add(card);
			store.save(host.incident(), snapshot.data(), snapshot.revision());
		}
	}

	/**
	 * Changes disjoint workflows in two controllers from the same base snapshot.
	 *
	 * @param first activity, planning and root editor.
	 * @param second resource and clue editor.
	 * @throws Exception if the event thread fails.
	 */
	private static void editWorkflows(MainFrame first, MainFrame second) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var data = controller(first).getData();
			data.getActivityLogs().get(0).getActivityLog().get(0).setNotableActivity("Activity from A");
			data.getForm202().setCommandEmphasis("Planning from A");
			data.setIapPhase(IapPhase.DURING_OP);
			controller(first).markDirty();
			var other = controller(second).getData();
			other.getTCards().stream().filter(card -> "Original responder".equals(card.getPersonName()))
					.findFirst().orElseThrow().setPersonName("Responder from B");
			other.getClueLogEntries().get(0).setDescription("Clue from B");
			controller(second).markDirty();
		});
	}

	/**
	 * Checks the displays of the separate workflows after remote refresh.
	 *
	 * @param frame displayed client.
	 * @throws Exception if the event thread fails.
	 */
	private static void assertWorkflowViews(MainFrame frame) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(java.util.stream.IntStream.range(0, table(frame).getRowCount())
					.anyMatch(row -> "Clue from B".equals(table(frame).getValueAt(row, 4))));
			Object cards = Ics205PanelTest.field(frame, "tCardPanel");
			JTable cardTable = (JTable) Ics205PanelTest.field(cards, "table");
			assertTrue(java.util.stream.IntStream.range(0, cardTable.getRowCount())
					.anyMatch(row -> "Responder from B".equals(cardTable.getValueAt(row, 1))));
			List<?> logs = (List<?>) Ics205PanelTest.field(frame, "ics214Panels");
			JTable activities = (JTable) Ics205PanelTest.field(logs.get(0), "activityLogTable");
			assertEquals("Activity from A", activities.getValueAt(0, 3));
			Object planning = Ics205PanelTest.field(frame, "ics202Panel");
			assertEquals("Planning from A",
					((JTextArea) Ics205PanelTest.field(planning, "commandEmphasisArea")).getText());
			assertEquals(IapPhase.DURING_OP, controller(frame).getData().getIapPhase());
		});
	}

	/**
	 * Persists the defaults initialized by the application before clients branch from the same state.
	 *
	 * @param host incident fixture.
	 * @throws Exception if the window cannot be opened or closed.
	 */
	private void initialize(HostedIncidentFixture host) throws Exception {
		MainFrame frame = window(host.session().loopbackStore(), host);
		try {
			controller(frame).save();
		} finally {
			close(frame);
		}
	}

	/**
	 * Opens a displayed window without automatic polling.
	 *
	 * @param store client store.
	 * @param host incident fixture.
	 * @return displayed window.
	 * @throws Exception if the event thread fails.
	 */
	private MainFrame window(RemoteIncidentStore store, HostedIncidentFixture host) throws Exception {
		var data = store.loadSnapshot(host.incident()).data();
		AtomicReference<MainFrame> frame = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			frame.set(new MainFrame(data, store, host.incident(), new PdfExportService(),
					new IncidentValidator(), directory));
			frame.get().setVisible(true);
		});
		return frame.get();
	}

	/**
	 * Transfers ownership of a newly paired gateway.
	 *
	 * @param host host fixture.
	 * @return paired gateway.
	 */
	private static RemoteIncidentGateway pairGateway(HostedIncidentFixture host) {
		RemoteIncidentStore paired = host.pair();
		RemoteIncidentGateway gateway = paired.detachGateway();
		paired.close();
		return gateway;
	}

	/**
	 * Obtains the controller attached to a displayed window.
	 *
	 * @param frame displayed window.
	 * @return its controller.
	 */
	private static AppController controller(MainFrame frame) {
		return (AppController) Ics205PanelTest.field(frame, "controller");
	}

	/**
	 * Obtains the actual clue table.
	 *
	 * @param frame displayed window.
	 * @return clue table.
	 */
	private static JTable table(MainFrame frame) {
		return (JTable) Ics205PanelTest.field(Ics205PanelTest.field(frame, "clueLogPanel"), "table");
	}

	/**
	 * Commits an inline table edit on the event thread.
	 *
	 * @param frame displayed window.
	 * @param column clue column.
	 * @param value edited value.
	 * @throws Exception if the event thread fails.
	 */
	private static void edit(MainFrame frame, int column, String value) throws Exception {
		SwingUtilities.invokeAndWait(() -> table(frame).setValueAt(value, 0, column));
	}

	/**
	 * Checks the actual displayed row, not just its backing model.
	 *
	 * @param frame displayed window.
	 * @param description expected description.
	 * @param location expected location.
	 * @throws Exception if the event thread fails.
	 */
	private static void assertTable(MainFrame frame, String description, String location) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(1, table(frame).getRowCount());
			assertEquals(description, table(frame).getValueAt(0, 4));
			assertEquals(location, table(frame).getValueAt(0, 3));
		});
	}

	/**
	 * Stops persistence and disposes a test window.
	 *
	 * @param frame displayed window.
	 * @throws Exception if the event thread fails.
	 */
	private static void close(MainFrame frame) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			controller(frame).close();
			controller(frame).closeStore();
			frame.dispose();
		});
	}
}
