/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.PersistenceStatus;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.RevisionToken;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies incident-bound live notifications and stale-edit rejection. */
class AppControllerLiveChangeTest {
	@TempDir
	Path directory;

	@Test
	void cleanRemoteRefreshInvalidatesOldBindingsAndPublishesBeforeReturning() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("clean"), "Shared")) {
			RemoteIncidentStore mine = host.pair();
			var snapshot = mine.loadSnapshot(host.incident());
			AppController controller = SwingTestLifecycleExtension.track(new AppController(snapshot.data(), mine,
					host.incident(), new PdfExportService(), new IncidentValidator()));
			AppController.EditorCommitBinding old = controller.captureEditorCommitBinding();
			List<AppController.IncidentChange> changes = new ArrayList<>();
			controller.addIncidentChangeListener(changes::add);
			RemoteIncidentStore other = host.pair();
			var remote = other.loadSnapshot(host.incident());
			remote.data().getIncidentContext().setIncidentName("Remote edit");
			other.save(host.incident(), remote.data(), remote.revision());

			assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
			assertEquals(1, changes.size());
			assertEquals(AppController.IncidentChangeKind.REMOTE_REFRESHED, changes.get(0).kind());
			assertTrue(controller.isCurrent(changes.get(0).binding()));
			assertFalse(controller.commitEditorIfCurrent(old, AppController.LinkSource.NONE,
					() -> controller.getData().getIncidentContext().setIncidentName("Stale edit")));
			assertEquals("Remote edit", controller.getData().getIncidentContext().getIncidentName());
		}
	}

	@Test
	void disconnectedSaveRetainsDraftAndOriginalBaseUntilReplayDetectsConflict() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("remote"), "Shared")) {
			RemoteIncidentStore paired = host.pair();
			RemoteIncidentGateway real = paired.detachGateway();
			paired.close();
			AtomicBoolean offline = new AtomicBoolean();
			RemoteIncidentGateway gateway = (RemoteIncidentGateway) Proxy.newProxyInstance(
					RemoteIncidentGateway.class.getClassLoader(), new Class<?>[] {RemoteIncidentGateway.class},
					(proxy, method, args) -> {
						if (offline.get() && (method.getName().equals("read")
								|| method.getName().equals("changesSince") || method.getName().equals("submit"))) {
							throw new StoreUnavailableException("Host unreachable", null);
						}
						try {
							return method.invoke(real, args);
						} catch (InvocationTargetException failure) {
							throw failure.getCause();
						}
					});
			RemoteIncidentStore mine = new RemoteIncidentStore(gateway, host.incident(), directory.resolve("cache"));
			var snapshot = mine.loadSnapshot(host.incident());
			IncidentService.ReadResult originalRecords = real.read(host.incident());
			CountDownLatch entered = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			List<RevisionToken> expectedTokens = new CopyOnWriteArrayList<>();
			IncidentStore gatedStore = (IncidentStore) Proxy.newProxyInstance(IncidentStore.class.getClassLoader(),
					new Class<?>[] {IncidentStore.class}, (proxy, method, args) -> {
						if (method.getName().equals("save") && args.length == 3) {
							expectedTokens.add((RevisionToken) args[2]);
							if (expectedTokens.size() == 1) {
								entered.countDown();
								if (!release.await(30, TimeUnit.SECONDS)) {
									throw new IllegalStateException("local save gate timed out");
								}
							}
						}
						try {
							return method.invoke(mine, args);
						} catch (InvocationTargetException problem) {
							throw problem.getCause();
						}
					});
			AppController controller = SwingTestLifecycleExtension.track(new AppController(snapshot.data(), gatedStore,
					host.incident(), new PdfExportService(), new IncidentValidator()));
			RemoteIncidentStore other = host.pair();
			var theirs = other.loadSnapshot(host.incident());
			theirs.data().getIncidentContext().setIncidentName("Remote edit");
			other.save(host.incident(), theirs.data(), theirs.revision());
			offline.set(true);
			CountDownLatch finished = new CountDownLatch(1);
			AtomicReference<RuntimeException> failure = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				controller.getData().getIncidentContext().setIncidentName("Local draft");
				controller.markDirty();
			});
			assertEquals(AppController.RemoteRefreshOutcome.UNCHANGED, controller.refreshFromRemote());
			assertEquals(snapshot.revision(), controller.getPersistenceStatus().revision());
			SwingUtilities.invokeAndWait(() -> {
				controller.saveAsync(AppController.LinkSource.NONE, result -> {
					failure.set(result);
					finished.countDown();
				});
			});
			try {
				assertTrue(entered.await(30, TimeUnit.SECONDS), "local save did not enter worker");
				assertEquals(AppController.RemoteRefreshOutcome.RETRY, controller.refreshFromRemote());
				assertEquals(snapshot.revision(), controller.getPersistenceStatus().revision());
				assertEquals("Local draft", controller.getData().getIncidentContext().getIncidentName());
			} finally {
				release.countDown();
			}
			assertTrue(finished.await(30, TimeUnit.SECONDS), "local save did not finish");
			assertNull(failure.get());
			assertEquals(PersistenceStatus.Durability.SAVED, controller.getPersistenceStatus().durability());
			assertNotEquals(snapshot.revision(), controller.getPersistenceStatus().revision());
			var localRevision = controller.getPersistenceStatus().revision();
			assertEquals(AppController.RemoteRefreshOutcome.UNCHANGED, controller.refreshFromRemote());
			assertEquals(localRevision, controller.getPersistenceStatus().revision());
			assertEquals(1, mine.pendingOperations().size());
			var pending = mine.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.PENDING, pending.status());
			assertEquals("Shared", pending.before().getIncidentContext().getIncidentName());
			assertEquals("Shared", pending.reviewBase().getIncidentContext().getIncidentName());
			assertEquals("Local draft", pending.draft().getIncidentContext().getIncidentName());
			assertEquals("Local draft", controller.getData().getIncidentContext().getIncidentName());
			SwingUtilities.invokeAndWait(() -> {
				controller.getData().getIncidentContext().setTaskMap("Later local edit");
				controller.markDirty();
			});
			controller.save();
			assertEquals(List.of(snapshot.revision(), localRevision), expectedTokens);
			assertEquals(2, mine.pendingOperations().size());
			assertEquals("Shared", mine.pendingOperations().get(0).reviewBase()
					.getIncidentContext().getIncidentName());
			assertEquals("Local draft", mine.pendingOperations().get(1).before()
					.getIncidentContext().getIncidentName());
			var laterRevision = controller.getPersistenceStatus().revision();
			assertThrows(StoreUnavailableException.class, mine::replayPending);
			var batch = mine.pendingOperations().get(0).batch();
			assertEquals(RemoteIncidentStore.PendingStatus.PENDING, mine.pendingOperations().get(0).status());
			assertFalse(batch.records().isEmpty());
			for (var record : batch.records()) {
				if (record.operation() == IncidentService.Operation.CREATE) {
					assertEquals(0, record.baseVersion());
				} else {
					assertEquals(originalRecords.records().stream().filter(item -> item.key().equals(record.key()))
							.findFirst().orElseThrow().version(), record.baseVersion());
				}
			}
			offline.set(false);
			mine.replayPending();
			var conflict = mine.pendingOperations().get(0);
			assertEquals(RemoteIncidentStore.PendingStatus.CONFLICT, conflict.status());
			assertEquals(batch, conflict.batch());
			assertEquals("Shared", conflict.reviewBase().getIncidentContext().getIncidentName());
			assertTrue(conflict.conflicts().stream().anyMatch(field -> field.field().equals("incidentName")));
			assertEquals(2, mine.pendingOperations().size());
			assertEquals(RemoteIncidentStore.PendingStatus.PENDING, mine.pendingOperations().get(1).status());
			assertEquals(laterRevision, controller.getPersistenceStatus().revision());
			assertEquals("Local draft", controller.getData().getIncidentContext().getIncidentName());
			assertEquals("Remote edit", other.loadSnapshot(host.incident()).data().getIncidentContext()
					.getIncidentName());
			other.close();
		}
	}

	@Test
	void deliversCommittedChangesAndDisposesListener() throws Exception {
		AppController controller = controller(new AppData());
		List<AppController.IncidentChange> changes = new ArrayList<>();
		AppController.IncidentChangeSubscription subscription = controller.addIncidentChangeListener(changes::add);

		controller.getData().getIncidentContext().setIncidentName("Committed");
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals(1, changes.size());
		assertEquals(AppController.IncidentChangeKind.MODEL_COMMITTED, changes.get(0).kind());
		assertTrue(controller.isCurrent(changes.get(0).binding()));

		subscription.close();
		subscription.close();
		controller.markDirty();
		SwingUtilities.invokeAndWait(() -> {
		});
		assertEquals(1, changes.size());
	}

	@Test
	void rejectsDetachedEditAfterIncidentReplacement() throws Exception {
		AppController controller = controller(new AppData());
		AppController.IncidentBinding oldBinding = controller.captureIncidentBinding();
		controller.setData(new AppData());
		SwingUtilities.invokeAndWait(() -> {
		});

		boolean applied = controller.commitIfCurrent(oldBinding,
				() -> controller.getData().getIncidentContext().setIncidentName("Wrong incident"));

		assertFalse(applied);
		assertEquals("", controller.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void rejectsDetachedEditAfterPeriodAdvanceWithoutReplacingModel() {
		AppController controller = controller(new AppData());
		AppController.IncidentBinding oldBinding = controller.captureIncidentBinding();

		controller.newOperationalPeriod();

		assertFalse(controller.isCurrent(oldBinding));
		assertFalse(controller.commitIfCurrent(oldBinding,
				() -> controller.getData().getIncidentContext().setIncidentName("Wrong period")));
	}

	@Test
	void appliesCurrentEditAndPublishesOnce() throws Exception {
		AppController controller = controller(new AppData());
		List<AppController.IncidentChange> changes = new ArrayList<>();
		controller.addIncidentChangeListener(changes::add);

		assertTrue(controller.commitIfCurrent(controller.captureIncidentBinding(),
				() -> controller.getData().getIncidentContext().setIncidentName("Current")));
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("Current", controller.getData().getIncidentContext().getIncidentName());
		assertEquals(1, changes.size());
	}

	@Test
	void remoteBackedStoreStillRejectsFullEditorCommitAfterNewerSharedCommit() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("stale"), "Shared")) {
			RemoteIncidentStore mine = host.pair();
			var snapshot = mine.loadSnapshot(host.incident());
			AppController controller = SwingTestLifecycleExtension.track(new AppController(snapshot.data(), mine,
					host.incident(), new PdfExportService(), new IncidentValidator()));
			AppController.EditorCommitBinding staleEditor = controller.captureEditorCommitBinding();
			controller.commitIfCurrent(controller.captureIncidentBinding(),
					() -> controller.getData().getIncidentContext().setIncidentName("Newer shared value"));

			assertFalse(controller.commitEditorIfCurrent(staleEditor, AppController.LinkSource.SHARED,
					() -> controller.getData().getIncidentContext().setIncidentName("Stale value")));
			assertEquals("Newer shared value", controller.getData().getIncidentContext().getIncidentName());
			((javax.swing.Timer) Ics205PanelTest.field(controller, "autosaveTimer")).stop();
		}
	}

	@Test
	void localStoreAcceptsFullEditorCommitAfterNewerSharedCommit() {
		AppController controller = controller(new AppData());
		AppController.EditorCommitBinding staleEditor = controller.captureEditorCommitBinding();
		controller.commitIfCurrent(controller.captureIncidentBinding(),
				() -> controller.getData().getIncidentContext().setIncidentName("Newer shared value"));

		boolean applied = controller.commitEditorIfCurrent(staleEditor, AppController.LinkSource.SHARED,
				() -> controller.getData().getIncidentContext().setIncidentName("Stale full-editor value"));

		assertTrue(applied);
		assertEquals("Stale full-editor value", controller.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void rejectsFullEditorCommitAfterIncidentOrPeriodReplacement() {
		AppController incidentController = controller(new AppData());
		AppController.EditorCommitBinding oldIncident = incidentController.captureEditorCommitBinding();
		incidentController.setData(new AppData());
		assertFalse(incidentController.commitEditorIfCurrent(oldIncident, AppController.LinkSource.SHARED,
				() -> incidentController.getData().getIncidentContext().setIncidentName("Wrong incident")));
		assertEquals("", incidentController.getData().getIncidentContext().getIncidentName());

		AppController periodController = controller(new AppData());
		AppController.EditorCommitBinding oldPeriod = periodController.captureEditorCommitBinding();
		periodController.newOperationalPeriod();
		assertFalse(periodController.commitEditorIfCurrent(oldPeriod, AppController.LinkSource.SHARED,
				() -> periodController.getData().getIncidentContext().setIncidentName("Wrong period")));
		assertEquals("", periodController.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void unchangedEditorFlushDoesNotDirtyOrNotify() throws Exception {
		AppController controller = controller(new AppData());
		List<AppController.IncidentChange> changes = new ArrayList<>();
		controller.addIncidentChangeListener(changes::add);
		AppController.EditorCommitBinding binding = controller.captureEditorCommitBinding();
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED, () -> {
				controller.getData().getIncidentContext().setIncidentName("");
				controller.markDirty();
				assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.NONE,
						controller::markDirty));
			}));
			assertEquals(binding, controller.captureEditorCommitBinding());
			assertFalse(controller.hasUnsavedChanges());
			assertFalse(((javax.swing.Timer) Ics205PanelTest.field(controller, "autosaveTimer")).isRunning());
		});
		SwingUtilities.invokeAndWait(() -> { });
		assertTrue(changes.isEmpty());
		controller.close();
	}

	@Test
	void changedNestedEditorFlushAdvancesRevisionOnlyOnce() throws Exception {
		AppController controller = controller(new AppData());
		var binding = controller.captureEditorCommitBinding();
		List<AppController.IncidentChange> changes = new ArrayList<>();
		controller.addIncidentChangeListener(changes::add);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED, () -> {
				assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.NONE, () -> {
					controller.getData().getForm202().setCommandEmphasis("Updated");
					controller.markDirty();
				}));
				controller.markDirty();
			}));
			assertEquals(binding.revision() + 1, controller.captureEditorCommitBinding().revision());
		});
		SwingUtilities.invokeAndWait(() -> assertEquals(1, changes.size()));
		controller.close();
	}

	@Test
	void localStoreFullEditorCommitIsNeverBlockedByEarlierCommits() {
		AppController controller = controller(new AppData());
		AppController.EditorCommitBinding binding = controller.captureEditorCommitBinding();

		assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED,
				() -> controller.getData().getIncidentContext().setIncidentName("Committed")));
		assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED,
				() -> controller.getData().getIncidentContext().setIncidentName("Second commit")));
		assertEquals("Second commit", controller.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void registeredEditorGuardRejectsOldIncidentAndPeriodBindingsButNotOldRevisionsInLocalStore() {
		AppController revisionController = controller(new AppData());
		EditorCommitGuard revisionGuard = revisionController.createEditorCommitGuard();
		revisionController.markDirty();
		assertTrue(revisionGuard.commit(AppController.LinkSource.NONE,
				() -> revisionController.getData().getIncidentContext().setIncidentName("Later revision")));
		assertEquals("Later revision", revisionController.getData().getIncidentContext().getIncidentName());

		AppController incidentController = controller(new AppData());
		EditorCommitGuard incidentGuard = incidentController.createEditorCommitGuard();
		incidentController.setData(new AppData());
		assertFalse(incidentGuard.commitField(
				() -> incidentController.getData().getIncidentContext().setIncidentName("Old incident")));
		assertEquals("", incidentController.getData().getIncidentContext().getIncidentName());

		AppController periodController = controller(new AppData());
		EditorCommitGuard periodGuard = periodController.createEditorCommitGuard();
		periodController.newOperationalPeriod();
		assertFalse(periodGuard.commitField(
				() -> periodController.getData().getIncidentContext().setIncidentName("Old period")));
		assertEquals("", periodController.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void defersAndCoalescesChangesPublishedOnEventDispatchThread() throws Exception {
		AppController controller = controller(new AppData());
		AtomicInteger deliveries = new AtomicInteger();
		controller.addIncidentChangeListener(change -> deliveries.incrementAndGet());

		SwingUtilities.invokeAndWait(() -> {
			controller.markDirty();
			controller.markDirty();
			assertEquals(0, deliveries.get());
		});
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals(1, deliveries.get());
	}

	@Test
	void closeInvalidatesPendingNotificationsAndRetainsDirtyState() throws Exception {
		AppController controller = controller(new AppData());
		AtomicInteger deliveries = new AtomicInteger();
		AppController.IncidentBinding binding = controller.captureIncidentBinding();
		controller.addIncidentChangeListener(change -> deliveries.incrementAndGet());

		SwingUtilities.invokeAndWait(() -> {
			controller.markDirty();
			controller.close();
			controller.close();
		});
		SwingUtilities.invokeAndWait(() -> {
		});

		assertTrue(controller.hasUnsavedChanges());
		assertTrue(controller.isDirty());
		assertFalse(controller.isCurrent(binding));
		assertEquals(0, deliveries.get());
		controller.markDirty();
		assertTrue(controller.hasUnsavedChanges());
	}

	@Test
	void isolatesListenerFailureAfterPersistingEditorChanges() throws Exception {
		AppController controller = controller(new AppData());
		AtomicInteger laterDeliveries = new AtomicInteger();
		controller.addIncidentChangeListener(change -> {
			throw new IllegalStateException("Broken presentation listener");
		});
		controller.addIncidentChangeListener(change -> laterDeliveries.incrementAndGet());

		controller.persistEditorChanges(
				() -> controller.getData().getIncidentContext().setIncidentName("Persist despite listener failure"));
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("Persist despite listener failure", controller.getData().getIncidentContext().getIncidentName());
		assertEquals("Persist despite listener failure",
				IncidentStoreTestFixtures.store(controller).load(controller.getActiveIncidentId()).getIncidentContext()
						.getIncidentName());
		assertEquals(1, laterDeliveries.get());
	}

	@Test
	void documentMutationPublishesAfterDocumentNotificationCompletes() throws Exception {
		AppController controller = controller(new AppData());
		JTextField source = new JTextField();
		AtomicBoolean insideDocumentNotification = new AtomicBoolean();
		AtomicBoolean deliveredInline = new AtomicBoolean();
		controller.addIncidentChangeListener(change -> {
			deliveredInline.set(insideDocumentNotification.get());
			if (!"Presented".equals(source.getText())) {
				source.setText("Presented");
			}
		});
		source.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				publishFromDocumentNotification();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				publishFromDocumentNotification();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				publishFromDocumentNotification();
			}

			private void publishFromDocumentNotification() {
				insideDocumentNotification.set(true);
				try {
					controller.markDirty();
				} finally {
					insideDocumentNotification.set(false);
				}
			}
		});

		SwingUtilities.invokeAndWait(() -> source.setText("Draft"));
		SwingUtilities.invokeAndWait(() -> {
		});

		assertFalse(deliveredInline.get());
		assertEquals("Presented", source.getText());
	}

	private AppController controller(AppData data) {
		return IncidentStoreTestFixtures.controller(data);
	}
}
