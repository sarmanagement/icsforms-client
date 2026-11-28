/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.AcceptedChanges;
import org.sarmanagement.icsforms.persistence.FullDocumentSnapshot;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore.PendingOperation;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore.PendingStatus;
import org.sarmanagement.icsforms.validation.IncidentValidator;

/** Covers workspace queue attribution and draft-safe indicator updates. */
class MainFrameSyncIndicatorsTest {
	@TempDir
	Path directory;

	@Test
	void attributesUnmaterializedOperationsAndDistinctLogIdentities() {
		AppData base = new AppData();
		Ics214Form first = new Ics214Form();
		Ics214Form second = new Ics214Form();
		base.getActivityLogs().add(first);
		base.getActivityLogs().add(second);
		AppData logEdit = FullDocumentSnapshot.detach(base);
		logEdit.getActivityLogs().get(1).setName("Field team");
		AppData objectiveEdit = FullDocumentSnapshot.detach(logEdit);
		objectiveEdit.getForm202().setCommandEmphasis("Safety first");
		AppData sharedEdit = FullDocumentSnapshot.detach(objectiveEdit);
		sharedEdit.getIncidentContext().setIncidentName("Incident");
		var result = MainFrame.workspaceQueueSnapshot(List.of(
				operation(base, logEdit, PendingStatus.PENDING),
				operation(logEdit, objectiveEdit, PendingStatus.REJECTED),
				operation(objectiveEdit, sharedEdit, PendingStatus.CONFLICT)));
		assertEquals(Set.of(PendingStatus.PENDING), result.logs().get(second.getId()));
		assertFalse(result.logs().containsKey(first.getId()));
		assertEquals(Set.of(PendingStatus.REJECTED), result.workspaces().get(WorkspaceId.ICS_202));
		assertEquals(Set.of(PendingStatus.CONFLICT), result.workspaces().get(WorkspaceId.SHARED));
		assertFalse(result.workspaces().containsKey(WorkspaceId.ICS_204));
	}

	@Test
	void attributesTcardsRootSettingsAndLayoutWithoutInventingAcceptance() {
		AppData base = new AppData();
		AppData cards = FullDocumentSnapshot.detach(base);
		TCard card = new TCard();
		card.setPersonName("Responder");
		cards.getTCards().add(card);
		AppData settings = FullDocumentSnapshot.detach(cards);
		settings.setConfiguredTimeZoneId("Europe/London");
		settings.getPdfLayoutSettings().setPageMarginPoints(42);
		var result = MainFrame.workspaceQueueSnapshot(List.of(
				operation(base, cards, PendingStatus.REVIEW_REQUIRED),
				operation(cards, settings, PendingStatus.PENDING)));
		assertEquals(Set.of(PendingStatus.REVIEW_REQUIRED), result.workspaces().get(WorkspaceId.TCARDS));
		assertEquals(Set.of(PendingStatus.PENDING), result.workspaces().get(WorkspaceId.SHARED));
		assertFalse(result.workspaces().containsKey(WorkspaceId.ICS_202));
		assertTrue(MainFrame.workspaceQueueSnapshot(List.of()).workspaces().isEmpty());
	}

	@Test
	void retainsMultipleQueueStatesAndAttributesSharedLogSettings() {
		AppData base = new AppData();
		AppData first = FullDocumentSnapshot.detach(base);
		first.getForm202().setCommandEmphasis("First");
		AppData second = FullDocumentSnapshot.detach(first);
		second.getForm202().setCommandEmphasis("Second");
		AppData third = FullDocumentSnapshot.detach(second);
		third.getActivityEventTypes().add(new ActivityEventType("field-event", "Field event", false));
		var result = MainFrame.workspaceQueueSnapshot(List.of(
				operation(base, first, PendingStatus.REJECTED),
				operation(first, second, PendingStatus.CONFLICT),
				operation(second, third, PendingStatus.PENDING),
				operation(third, third, PendingStatus.REVIEW_REQUIRED)));
		assertEquals(Set.of(PendingStatus.REJECTED, PendingStatus.CONFLICT),
				result.workspaces().get(WorkspaceId.ICS_202));
		assertEquals(Set.of(PendingStatus.PENDING), result.workspaces().get(WorkspaceId.ACTIVITY_LOG));
		assertFalse(result.workspaces().containsKey(WorkspaceId.SHARED));
	}

	@Test
	void acknowledgedOperationsAreAttributedOnlyWhenReceiptsAccountForThem() {
		AppData base = new AppData();
		AppData objectives = FullDocumentSnapshot.detach(base);
		objectives.getForm202().setCommandEmphasis("Safety first");
		AppData named = FullDocumentSnapshot.detach(objectives);
		named.getIncidentContext().setIncidentName("Incident");
		PendingOperation first = operation("a", base, objectives, PendingStatus.PENDING);
		PendingOperation second = operation("b", objectives, named, PendingStatus.PENDING);
		var accepted = MainFrame.acceptedSnapshot(List.of(first, second), List.of(second), 4, 5);
		assertEquals(Set.of(WorkspaceId.ICS_202), accepted.workspaces().keySet());
		assertTrue(MainFrame.acceptedSnapshot(List.of(first, second), List.of(second), 4, 4).workspaces().isEmpty());
		assertTrue(MainFrame.acceptedSnapshot(List.of(first, second), List.of(), 4, 5).workspaces().isEmpty());
		assertTrue(MainFrame.acceptedSnapshot(List.of(first), List.of(first), 4, 5).workspaces().isEmpty());
	}

	@Test
	void durableReceiptsAreProjectedOnlyToTheirOwnWorkspaces() {
		Instant first = Instant.parse("2026-01-01T00:00:00Z");
		Instant second = first.plusSeconds(5);
		var changes = new AcceptedChanges(Map.of("form202", new AcceptedChanges.Receipt("a", first)),
				Map.of("form205", new AcceptedChanges.Receipt("b", second)),
				Map.of("log", new AcceptedChanges.Receipt("c", second)));
		var times = MainFrame.acceptedWorkspaceTimes(changes);
		assertEquals(Map.of(WorkspaceId.ICS_202, first, WorkspaceId.ICS_205, second), times);
		var cards = new AcceptedChanges(Map.of("tcards", new AcceptedChanges.Receipt("card", first)),
				Map.of(), Map.of());
		assertEquals(Map.of(WorkspaceId.TCARDS, first), MainFrame.acceptedWorkspaceTimes(cards));
	}

	@Test
	void queuedStatusUpdatesTabsWithoutReplacingAnEditorDraft() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			var gateway = paired.detachGateway();
			paired.close();
			RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(), directory.resolve("cache"));
			var initial = remote.loadSnapshot(host.incident());
			initial.data().getIncidentContext().setIncidentName("Queued");
			remote.save(host.incident(), initial.data(), initial.revision());
			var local = remote.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			AtomicReference<JTextField> draft = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				frame.set(new MainFrame(local.data(), remote, host.incident(), new PdfExportService(),
						new IncidentValidator(), directory));
				draft.set((JTextField) field(field(frame.get(), "incidentContextPanel"), "incidentNameField"));
				draft.get().setText("Uncommitted typing");
				invokeRefresh(frame.get(), remote);
			});
			try {
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				AtomicReference<String> label = new AtomicReference<>("");
				while (!label.get().endsWith("\u2022") && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> label.set(((JTabbedPane) field(frame.get(), "tabs")).getTitleAt(0)));
					Thread.sleep(20);
				}
				assertTrue(label.get().endsWith("\u2022"), label.get());
				SwingUtilities.invokeAndWait(() -> {
					JTabbedPane tabs = (JTabbedPane) field(frame.get(), "tabs");
					assertEquals("Uncommitted typing", draft.get().getText());
					assertTrue(tabs.getToolTipTextAt(0).contains("Last accepted by the host: none"));
					assertTrue(tabs.getToolTipTextAt(0).contains("Last successful sync: "));
					assertTrue(frame.get().connectionStatusText().contains("last successful sync "));
					assertFalse(tabs.getTitleAt(1).contains("accepted"));
					assertFalse(tabs.getTitleAt(1).endsWith("\u2022"));
				});
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					AppController controller = (AppController) field(frame.get(), "controller");
					controller.close();
					controller.closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	@Test
	void acceptedLabelsSurviveRestartWithoutMarkingOtherWorkspaces() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			var gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			Path cache = directory.resolve("cache");
			try (RemoteIncidentStore remote = new RemoteIncidentStore(gateway, host.incident(), cache)) {
				var initial = remote.loadSnapshot(host.incident());
				initial.data().getIncidentContext().setIncidentName("Accepted");
				remote.save(host.incident(), initial.data(), initial.revision());
				remote.replayPending();
			}
			RemoteIncidentStore restored = RemoteIncidentStore.openOffline(cache, fingerprint, host.incident());
			var snapshot = restored.loadSnapshot(host.incident());
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				frame.set(new MainFrame(snapshot.data(), restored, host.incident(), new PdfExportService(),
						new IncidentValidator(), directory));
				invokeRefresh(frame.get(), restored);
			});
			try {
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				AtomicReference<String> tooltip = new AtomicReference<>("");
				while (!tooltip.get().contains("Queue: accepted") && System.nanoTime() < deadline) {
					SwingUtilities.invokeAndWait(() -> tooltip.set(String.valueOf(
							((JTabbedPane) field(frame.get(), "tabs")).getToolTipTextAt(0))));
					Thread.sleep(20);
				}
				assertTrue(tooltip.get().contains("Queue: accepted"), tooltip.get());
				SwingUtilities.invokeAndWait(() -> {
					JTabbedPane tabs = (JTabbedPane) field(frame.get(), "tabs");
					assertTrue(tabs.getToolTipTextAt(1).contains("Last accepted by the host: none"));
				});
			} finally {
				SwingUtilities.invokeAndWait(() -> {
					AppController controller = (AppController) field(frame.get(), "controller");
					controller.close();
					controller.closeStore();
					frame.get().dispose();
				});
			}
		}
	}

	@Test
	void standaloneTabsRemainUndecorated() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		try (FileIncidentStore local = new FileIncidentStore(directory.resolve("local"))) {
			AppData data = new AppData();
			var id = local.create(data);
			AtomicReference<MainFrame> frame = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				frame.set(new MainFrame(local.load(id), local, id, new PdfExportService(),
						new IncidentValidator(), directory));
				JTabbedPane tabs = (JTabbedPane) field(frame.get(), "tabs");
				for (int index = 0; index < tabs.getTabCount(); index++) {
					assertFalse(tabs.getTitleAt(index).contains("["));
					assertFalse(tabs.getTitleAt(index).endsWith("\u2022"));
				}
				javax.swing.JMenu sharing = null;
				for (int index = 0; index < frame.get().getJMenuBar().getMenuCount(); index++) {
					if ("Sharing".equals(frame.get().getJMenuBar().getMenu(index).getText())) {
						sharing = frame.get().getJMenuBar().getMenu(index);
					}
				}
				for (javax.swing.event.MenuListener listener : sharing.getMenuListeners()) {
					listener.menuSelected(new javax.swing.event.MenuEvent(sharing));
				}
				assertFalse(sharing.getItem(0).isEnabled(), "Stop sharing needs an active host");
				assertEquals("Standalone", frame.get().connectionStatusText());
				((AppController) field(frame.get(), "controller")).close();
				frame.get().dispose();
			});
		}
	}

	/**
	 * Builds a detached queue entry without an already materialized batch.
	 *
	 * @param before previous document
	 * @param draft edited document
	 * @param status queue state
	 * @return operation for attribution
	 */
	private static PendingOperation operation(AppData before, AppData draft, PendingStatus status) {
		return operation("test", before, draft, status);
	}

	/**
	 * Builds a detached queue entry with a chosen identity.
	 *
	 * @param id operation identifier
	 * @param before previous document
	 * @param draft edited document
	 * @param status queue state
	 * @return operation for attribution
	 */
	private static PendingOperation operation(String id, AppData before, AppData draft, PendingStatus status) {
		return new PendingOperation(id, before, draft, null, status, List.of(), null);
	}

	/**
	 * Reads one private field for the displayed integration test.
	 *
	 * @param target containing object
	 * @param name field name
	 * @return field value
	 */
	private static Object field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}

	/**
	 * Starts the normal asynchronous outbox refresh.
	 *
	 * @param frame window to update
	 * @param remote active remote store
	 */
	private static void invokeRefresh(MainFrame frame, RemoteIncidentStore remote) {
		try {
			Method method = MainFrame.class.getDeclaredMethod("refreshOutbox", RemoteIncidentStore.class);
			method.setAccessible(true);
			method.invoke(frame, remote);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}
}
