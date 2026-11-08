/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.persistence.PersistenceStatus;
import org.sarmanagement.icsforms.persistence.RevisionToken;
import org.sarmanagement.icsforms.persistence.SaveCoordinator;
import org.sarmanagement.icsforms.persistence.FullDocumentSnapshot;
import org.sarmanagement.icsforms.persistence.RetryPolicy;
import org.sarmanagement.icsforms.persistence.StorePersistenceException;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Detached persistence must keep Swing responsive without losing concurrent local edits. */
class AppControllerAsyncPersistenceTest {
	@Test
	void queuedSnapshotRebasesOnTheAcceptedTokenAndPersistsTheNewestEdit() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			try (AppController controller = controller(store, id, live)) {
				CountDownLatch completed = new CountDownLatch(1);
				store.blockNextSave();
				SwingUtilities.invokeAndWait(() -> {
					live.getIncidentContext().setIncidentName("First snapshot");
					controller.markDirty();
					controller.saveAsync(AppController.LinkSource.NONE, failure -> assertNull(failure));
				});
				await(store.entered);
				SwingUtilities.invokeAndWait(() -> {
					live.getIncidentContext().setIncidentName("Second snapshot");
					controller.markDirty();
					controller.saveAsync(AppController.LinkSource.NONE, failure -> {
						assertNull(failure);
						completed.countDown();
					});
				});
				store.release.countDown();
				await(completed);
				assertEquals("Second snapshot", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
				assertEquals(store.loadSnapshot(id).revision(), controller.getPersistenceStatus().revision());
				assertFalse(controller.isDirty());
			}
		}
	}

	@Test
	void explicitDiscardWaitsForIssuedWritesBeforeReplacingAndClearingRetainedWork() throws Exception {
		for (boolean failSave : java.util.List.of(false, true)) {
			try (BlockingStore store = new BlockingStore(directory())) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				try (AppController controller = controller(store, id, live)) {
					SaveCoordinator coordinator = coordinator(controller);
					RuntimeException problem = new StorePersistenceException("Issued save failed", null);
					store.failure = failSave ? problem : null;
					CountDownLatch saveCompleted = new CountDownLatch(1);
					store.blockNextSave();
					SwingUtilities.invokeAndWait(() -> {
						live.getIncidentContext().setIncidentName("Old generation");
						controller.markDirty();
						controller.saveAsync(AppController.LinkSource.NONE, failure -> {
							assertSame(live, controller.getData());
							assertSame(failSave ? problem : null, failure);
							saveCompleted.countDown();
						});
					});
					await(store.entered);
					long oldGeneration = controller.getPersistenceStatus().generation();
					AppData replacement = new AppData();
					AtomicReference<PersistenceStatus> settledStatus = new AtomicReference<>();
					AtomicReference<PersistenceStatus> replacementStatus = new AtomicReference<>();
					CountDownLatch draining = new CountDownLatch(1);
					CountDownLatch replaced = new CountDownLatch(1);
					AtomicReference<Throwable> replacementFailure = new AtomicReference<>();
					SwingUtilities.invokeAndWait(() -> {
						live.getIncidentContext().setIncidentName("Queued old edit");
						controller.markDirty();
						controller.saveAsync(AppController.LinkSource.NONE, failure -> { });
						assertThrows(IllegalStateException.class, () -> controller.setData(replacement));
						controller.setPersistenceWaitHandler(waiting -> {
							if (waiting) {
								draining.countDown();
							}
						});
						controller.setPersistenceStatusHandler(status -> {
							if (status.generation() == oldGeneration
									&& status.durability() == PersistenceStatus.Durability.FAILED) {
								assertSame(live, controller.getData());
								assertEquals(2, coordinator.pendingChanges().size());
								settledStatus.set(status);
							}
						});
					});
					SwingUtilities.invokeLater(() -> {
						try {
							controller.discardUnsavedChangesAndSetData(replacement);
							assertEquals(0, saveCompleted.getCount());
							assertFalse(coordinator.isBusy());
							assertTrue(coordinator.pendingChanges().isEmpty());
							assertFalse(controller.isDirty());
							assertEquals(PersistenceStatus.Durability.SAVED,
									controller.getPersistenceStatus().durability());
							assertNull(controller.getPersistenceStatus().failure());
							replacement.getIncidentContext().setIncidentName("Unsaved replacement");
							controller.markDirty();
							replacementStatus.set(controller.getPersistenceStatus());
						} catch (Throwable failure) {
							replacementFailure.set(failure);
						} finally {
							replaced.countDown();
						}
					});
					await(draining);
					SwingUtilities.invokeAndWait(() -> {
						assertSame(live, controller.getData());
						assertTrue(controller.isDirty());
						assertTrue(coordinator.isBusy());
						assertEquals(2, coordinator.pendingChanges().size());
						assertEquals(1, replaced.getCount());
						assertEquals(oldGeneration, controller.getPersistenceStatus().generation());
						assertEquals("", replacement.getIncidentContext().getIncidentName());
					});
					store.release.countDown();
					await(replaced);
					assertNull(replacementFailure.get());
					if (failSave) {
						assertNotNull(settledStatus.get());
						assertSame(problem, settledStatus.get().failure());
					}
					RevisionToken persistedRevision = store.loadSnapshot(id).revision();
					assertEquals(failSave ? "" : "Queued old edit",
							store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
					SwingUtilities.invokeAndWait(() -> {
						assertEquals(id, controller.getPersistenceStatus().incidentId());
						assertEquals(persistedRevision, controller.getPersistenceStatus().revision());
						assertEquals(oldGeneration + 1, controller.getPersistenceStatus().generation());
					});
					controller.close();
					assertSame(replacement, controller.getData());
					assertEquals("Unsaved replacement", replacement.getIncidentContext().getIncidentName());
					assertTrue(controller.isDirty());
					assertEquals(1, replacement.getRevision());
					assertEquals(replacementStatus.get(), controller.getPersistenceStatus());
					assertEquals(persistedRevision, store.loadSnapshot(id).revision());
				}
			}
		}
	}

	@Test
	void failedSavesRejectDirectReplacementWithoutDroppingRecoveryWork() throws Exception {
		for (RuntimeException problem : replacementFailures()) {
			try (BlockingStore store = new BlockingStore(directory())) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				IncidentId candidate = store.create(new AppData());
				try (AppController controller = controller(store, id, live)) {
					SaveCoordinator coordinator = coordinator(controller);
					SwingUtilities.invokeAndWait(() -> {
						live.getIncidentContext().setIncidentName("Retained unsaved edits");
						controller.markDirty();
						store.failure = problem;
						assertSame(problem, assertThrows(problem.getClass(), controller::save));
						PersistenceStatus status = controller.getPersistenceStatus();
						var pending = coordinator.pendingChanges();
						var binding = controller.captureEditorCommitBinding();
						long revision = live.getRevision();
						assertFalse(pending.isEmpty());
						int loads = store.loads;
						assertSame(problem, assertThrows(problem.getClass(), () -> controller.open(candidate)));
						assertSame(problem, assertThrows(problem.getClass(), () -> controller.setData(new AppData())));
						assertSame(problem, assertThrows(problem.getClass(), controller::newOperationalPeriod));
						assertEquals(loads, store.loads);
						assertSame(live, controller.getData());
						assertEquals(id, controller.getActiveIncidentId());
						assertTrue(controller.isDirty());
						assertEquals("Retained unsaved edits", live.getIncidentContext().getIncidentName());
						assertEquals(status, controller.getPersistenceStatus());
						assertEquals(pending, coordinator.pendingChanges());
						assertEquals(binding, controller.captureEditorCommitBinding());
						assertEquals(revision, live.getRevision());
						store.failure = null;
						controller.save();
						assertFalse(controller.isDirty());
						assertTrue(coordinator.pendingChanges().isEmpty());
						AppData replacement = new AppData();
						controller.setData(replacement);
						assertSame(replacement, controller.getData());
						controller.open(candidate);
						assertEquals(candidate, controller.getActiveIncidentId());
						assertEquals(PersistenceStatus.Durability.SAVED, controller.getPersistenceStatus().durability());
					});
				}
			}
		}
	}

	@Test
	void explicitDiscardAndRecoveryReloadAuthorizeReplacementAfterEachSaveFailure() throws Exception {
		for (RuntimeException problem : replacementFailures()) {
			try (BlockingStore store = new BlockingStore(directory())) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				try (AppController controller = controller(store, id, live)) {
					SaveCoordinator coordinator = coordinator(controller);
					SwingUtilities.invokeAndWait(() -> {
						controller.markDirty();
						store.failure = problem;
						assertThrows(problem.getClass(), controller::save);
						PersistenceStatus retainedStatus = controller.getPersistenceStatus();
						var pending = coordinator.pendingChanges();
						store.loadFailure = new org.sarmanagement.icsforms.persistence.StoreUnavailableException("Reload failed");
						assertSame(store.loadFailure,
								assertThrows(store.loadFailure.getClass(), controller::replaceActiveDocumentFromStore));
						assertSame(live, controller.getData());
						assertTrue(controller.isDirty());
						assertEquals(retainedStatus, controller.getPersistenceStatus());
						assertEquals(pending, coordinator.pendingChanges());
						store.loadFailure = null;
						AppData replacement = new AppData();
						controller.discardUnsavedChangesAndSetData(replacement);
						assertSame(replacement, controller.getData());
						assertFalse(controller.isDirty());
						assertTrue(coordinator.pendingChanges().isEmpty());
						replacement.getIncidentContext().setIncidentName("Discard for reload");
						controller.markDirty();
						assertThrows(problem.getClass(), controller::save);
						controller.replaceActiveDocumentFromStore();
						assertNotSame(replacement, controller.getData());
						assertEquals("", controller.getData().getIncidentContext().getIncidentName());
						assertEquals(id, controller.getActiveIncidentId());
						assertFalse(controller.isDirty());
						assertTrue(coordinator.pendingChanges().isEmpty());
						assertNull(controller.getPersistenceStatus().failure());
						assertEquals(store.loadSnapshot(id).revision(), controller.getPersistenceStatus().revision());
					});
				}
			}
		}
	}

	@Test
	void unscheduledEditsRejectOpenAndSetDataUntilSaved() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			IncidentId candidate = store.create(new AppData());
			try (AppController controller = controller(store, id, live)) {
				SwingUtilities.invokeAndWait(() -> {
					controller.markDirty();
					PersistenceStatus status = controller.getPersistenceStatus();
					assertThrows(IllegalStateException.class, () -> controller.open(candidate));
					assertThrows(IllegalStateException.class, () -> controller.setData(new AppData()));
					assertThrows(IllegalStateException.class, controller::newOperationalPeriod);
					assertSame(live, controller.getData());
					assertEquals(id, controller.getActiveIncidentId());
					assertTrue(controller.isDirty());
					assertEquals(status, controller.getPersistenceStatus());
					controller.save();
					controller.open(candidate);
					assertEquals(candidate, controller.getActiveIncidentId());
				});
			}
		}
	}

	@Test
	void openDrainsIssuedSaveAndRequiresTheLatestEditToBeDurable() throws Exception {
		for (int outcome : java.util.List.of(0, 1, 2)) {
			try (BlockingStore store = new BlockingStore(directory())) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				IncidentId candidate = store.create(new AppData());
				try (AppController controller = controller(store, id, live)) {
					SaveCoordinator coordinator = coordinator(controller);
					store.blockNextSave();
					SwingUtilities.invokeAndWait(() -> {
						controller.markDirty();
						controller.saveAsync(AppController.LinkSource.NONE, failure -> { });
					});
					await(store.entered);
					CountDownLatch draining = new CountDownLatch(1);
					controller.setPersistenceWaitHandler(waiting -> {
						if (waiting) {
							draining.countDown();
						}
					});
					CountDownLatch finished = new CountDownLatch(1);
					AtomicReference<Throwable> openFailure = new AtomicReference<>();
					SwingUtilities.invokeLater(() -> {
						try {
							controller.open(candidate);
						} catch (Throwable problem) {
							openFailure.set(problem);
						} finally {
							finished.countDown();
						}
					});
					await(draining);
					SwingUtilities.invokeAndWait(() -> {
						if (outcome == 1) {
							store.failure = new StorePersistenceException("Save failed while opening", null);
						} else if (outcome == 0) {
							live.getIncidentContext().setIncidentName("Newer edit during drain");
							controller.markDirty();
						}
					});
					store.release.countDown();
					await(finished);
					SwingUtilities.invokeAndWait(() -> {
						if (outcome == 2) {
							assertNull(openFailure.get());
							assertNotSame(live, controller.getData());
							assertEquals(candidate, controller.getActiveIncidentId());
							assertFalse(controller.isDirty());
							assertEquals(store.loadSnapshot(candidate).revision(),
									controller.getPersistenceStatus().revision());
							return;
						}
						assertNotNull(openFailure.get());
						assertSame(live, controller.getData());
						assertEquals(id, controller.getActiveIncidentId());
						assertTrue(controller.isDirty());
						if (outcome == 1) {
							assertSame(store.failure, openFailure.get());
							assertFalse(coordinator.pendingChanges().isEmpty());
						} else {
							assertInstanceOf(IllegalStateException.class, openFailure.get());
							assertEquals(PersistenceStatus.Durability.PENDING,
									controller.getPersistenceStatus().durability());
						}
					});
				}
			}
		}
	}

	@Test
	void asynchronousSaveFlushesOnEdtAndDoesNotClearANewerEdit() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			try (AppController controller = controller(store, id, live)) {
				CountDownLatch completed = new CountDownLatch(1);
				controller.setPlanReuseEditorCallbacks(() -> {
					assertTrue(SwingUtilities.isEventDispatchThread());
					live.getIncidentContext().setIncidentName("Flushed editor text");
				}, () -> fail("Persistence must not refresh editors"));
				store.blockNextSave();
				SwingUtilities.invokeAndWait(() -> {
					controller.markDirty();
					controller.saveAsync(AppController.LinkSource.NONE, failure -> {
						assertNull(failure);
						assertTrue(SwingUtilities.isEventDispatchThread());
						completed.countDown();
					});
				});
				await(store.entered);
				assertNotSame(live, store.savedDocument);
				SwingUtilities.invokeAndWait(() -> {
					live.getIncidentContext().setIncidentName("Newer unsaved edit");
					controller.markDirty();
				});
				store.release.countDown();
				await(completed);
				SwingUtilities.invokeAndWait(() -> {
					assertTrue(controller.isDirty());
					assertEquals("Newer unsaved edit", live.getIncidentContext().getIncidentName());
					controller.setPlanReuseEditorCallbacks(() -> { }, () -> { });
					controller.save();
					assertFalse(controller.isDirty());
				});
				assertEquals("Newer unsaved edit", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
				assertFalse(store.saveOnEdt);
			}
		}
	}

	@Test
	void synchronousSaveDispatchesSwingEventsWhileWorkerWritesDetachedData() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			try (AppController controller = controller(store, id, live)) {
				store.blockNextSave();
				java.util.concurrent.atomic.AtomicBoolean inputPaused = new java.util.concurrent.atomic.AtomicBoolean();
				controller.setPersistenceWaitHandler(inputPaused::set);
				CountDownLatch completed = new CountDownLatch(1);
				AtomicReference<Throwable> failure = new AtomicReference<>();
				SwingUtilities.invokeLater(() -> {
					try {
						controller.save();
					} catch (Throwable problem) {
						failure.set(problem);
					} finally {
						completed.countDown();
					}
				});
				await(store.entered);
				SwingUtilities.invokeAndWait(() -> {
					assertNotSame(live, store.savedDocument);
					assertEquals(PersistenceStatus.Durability.SAVING, controller.getPersistenceStatus().durability());
					assertTrue(inputPaused.get());
				});
				store.release.countDown();
				await(completed);
				assertNull(failure.get());
				assertFalse(store.saveOnEdt);
				assertFalse(controller.isDirty());
				assertFalse(inputPaused.get());
			}
		}
	}

	@Test
	void atomicSaveNeverExposesTransientEditsDuringWorkerWaitAndRollsBackFailure() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			try (AppController controller = controller(store, id, live)) {
				var originalContext = live.getIncidentContext();
				store.blockNextSave();
				store.failure = new StorePersistenceException("Cannot write", null);
				CountDownLatch completed = new CountDownLatch(1);
				AtomicReference<Throwable> failure = new AtomicReference<>();
				SwingUtilities.invokeLater(() -> {
					try {
						controller.persistEditorChanges(() -> originalContext.setIncidentName("Transient transaction"));
					} catch (Throwable problem) {
						failure.set(problem);
					} finally {
						completed.countDown();
					}
				});
				await(store.entered);
				SwingUtilities.invokeAndWait(() -> {
					assertSame(originalContext, live.getIncidentContext());
					assertEquals("", originalContext.getIncidentName());
					assertEquals("Transient transaction", store.savedDocument.getIncidentContext().getIncidentName());
					assertFalse(controller.commitEditorIfCurrent(controller.captureEditorCommitBinding(),
							AppController.LinkSource.NONE, () -> fail("Reentrant transaction must not run")));
				});
				store.release.countDown();
				await(completed);
				assertSame(store.failure, failure.get());
				assertSame(originalContext, live.getIncidentContext());
				assertEquals("", originalContext.getIncidentName());
				assertFalse(controller.isDirty());
			}
		}
	}

	@Test
	void failedSaveRecoveryExportsUnsavedDocumentWithoutConsultingStoredContent() throws Exception {
		for (RuntimeException problem : java.util.List.of(new StorePersistenceException("Cannot write", null),
				new org.sarmanagement.icsforms.persistence.StoreUnavailableException("Unavailable"),
				new org.sarmanagement.icsforms.persistence.StoreConflictException("Conflict",
						new RevisionToken("expected"), new RevisionToken("current")))) {
			Path root = directory();
			try (BlockingStore store = new BlockingStore(root)) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				try (AppController controller = controller(store, id, live)) {
					live.getIncidentContext().setIncidentName("Unsaved recovery content");
					controller.markDirty();
					store.failure = problem;
					assertSame(problem, assertThrows(problem.getClass(), controller::save));
					Path recovery = root.resolve("recovery.json");
					controller.exportUnsavedIncident(recovery, AppController.LinkSource.NONE);
					assertEquals("Unsaved recovery content",
							FileIncidentStore.readImport(recovery).data().getIncidentContext().getIncidentName());
					assertEquals("", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
					assertTrue(controller.isDirty());
					assertSame(problem, controller.getPersistenceStatus().failure());
				}
			}
		}
	}

	@Test
	void reloadUsesExplicitEditorFlushAndRefreshCallbacks() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			try (AppController controller = controller(store, id, live)) {
				java.util.List<String> events = new java.util.ArrayList<>();
				controller.setPlanReuseEditorCallbacks(() -> {
					assertTrue(SwingUtilities.isEventDispatchThread());
					events.add("flush");
				}, () -> events.add("refresh"));
				controller.replaceActiveDocumentFromStore();
				assertEquals(java.util.List.of("flush", "refresh"), events);
				assertNotSame(live, controller.getData());
				assertFalse(controller.isDirty());
			}
		}
	}

	@Test
	void rejectedCreationHasNoStoreOrLockSideEffectsAndPreservesActiveWork() throws Exception {
		for (RuntimeException problem : replacementFailures()) {
			try (BlockingStore store = new BlockingStore(directory())) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				try (AppController controller = controller(store, id, live)) {
					SaveCoordinator coordinator = coordinator(controller);
					SwingUtilities.invokeAndWait(() -> {
						live.getIncidentContext().setIncidentName("Retained active work");
						controller.markDirty();
						int creations = store.creations;
						AppData candidate = new AppData();
						IncidentId candidateId = IncidentId.newId();
						candidate.setIncidentId(candidateId.value());
						PersistenceStatus pendingStatus = controller.getPersistenceStatus();
						assertThrows(IllegalStateException.class, () -> controller.createNewIncident(candidate));
						assertEquals(pendingStatus, controller.getPersistenceStatus());
						assertEquals(creations, store.creations);
						store.failure = problem;
						assertSame(problem, assertThrows(problem.getClass(), controller::save));
						PersistenceStatus failedStatus = controller.getPersistenceStatus();
						var pending = coordinator.pendingChanges();
						var binding = controller.captureEditorCommitBinding();
						assertSame(problem, assertThrows(problem.getClass(),
								() -> controller.createNewIncident(candidate)));
						assertSame(live, controller.getData());
						assertTrue(controller.isDirty());
						assertEquals(id, controller.getActiveIncidentId());
						assertEquals(failedStatus, controller.getPersistenceStatus());
						assertEquals(pending, coordinator.pendingChanges());
						assertEquals(binding, controller.captureEditorCommitBinding());
						assertEquals(creations, store.creations);
						assertEquals(1, store.list().size());
						assertFalse(store.isHeldByCaller(candidateId));
						store.failure = null;
						controller.save();
						controller.createNewIncident(candidate);
						assertEquals(creations + 1, store.creations);
						assertEquals(2, store.list().size());
						assertEquals(candidateId, controller.getActiveIncidentId());
						assertTrue(store.isHeldByCaller(candidateId));
						assertFalse(controller.isDirty());
						assertEquals(store.loadSnapshot(candidateId).revision(),
								controller.getPersistenceStatus().revision());
					});
				}
			}
		}
	}

	@Test
	void localTransientSavesRetryWithinTheBoundBeforeReportingSuccess() throws Exception {
		for (int failures : java.util.List.of(1, 2)) {
			try (BlockingStore store = new BlockingStore(directory())) {
				AppData live = new AppData();
				IncidentId id = store.create(live);
				try (AppController controller = controller(store, id, live)) {
					SaveCoordinator coordinator = coordinator(controller);
					store.failure = new StorePersistenceException("Transient local write failure", null);
					store.failuresRemaining = failures;
					SwingUtilities.invokeAndWait(() -> {
						live.getIncidentContext().setIncidentName("Retry content");
						controller.markDirty();
						controller.save();
						assertEquals(failures + 1, store.saveAttempts);
						assertFalse(controller.isDirty());
						assertNull(controller.getPersistenceStatus().failure());
						assertTrue(coordinator.pendingChanges().isEmpty());
						assertEquals(PersistenceStatus.Durability.SAVED, controller.getPersistenceStatus().durability());
					});
					assertEquals("Retry content", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
				}
			}
		}
	}

	@Test
	void exhaustedLocalRetriesRetainWorkAndConfiguredExplicitRetryDoesNotRetryAutomatically() throws Exception {
		for (boolean explicit : java.util.List.of(false, true)) {
			try (BlockingStore store = new BlockingStore(directory())) {
				if (explicit) {
					store.retryPolicy = RetryPolicy.explicit();
				}
				AppData live = new AppData();
				IncidentId id = store.create(live);
				try (AppController controller = controller(store, id, live)) {
					SaveCoordinator coordinator = coordinator(controller);
					store.failure = new StorePersistenceException("Cannot write", null);
					SwingUtilities.invokeAndWait(() -> {
						live.getIncidentContext().setIncidentName("Recoverable edit");
						controller.markDirty();
						assertSame(store.failure, assertThrows(store.failure.getClass(), controller::save));
						assertEquals(explicit ? 1 : 3, store.saveAttempts);
						assertSame(live, controller.getData());
						assertTrue(controller.isDirty());
						assertEquals(1, coordinator.pendingChanges().size());
						assertSame(store.failure, controller.getPersistenceStatus().failure());
						store.failure = null;
						controller.save();
						assertEquals(explicit ? 2 : 4, store.saveAttempts);
						assertTrue(coordinator.pendingChanges().isEmpty());
						assertFalse(controller.isDirty());
					});
					assertEquals("Recoverable edit", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
				}
			}
		}
	}

	@Test
	void localConflictsNeverRetryAutomatically() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			try (AppController controller = controller(store, id, live)) {
				store.failure = new org.sarmanagement.icsforms.persistence.StoreConflictException("Conflict",
						new RevisionToken("expected"), new RevisionToken("current"));
				SwingUtilities.invokeAndWait(() -> {
					controller.markDirty();
					assertSame(store.failure, assertThrows(store.failure.getClass(), controller::save));
					assertEquals(1, store.saveAttempts);
					assertTrue(controller.isDirty());
					assertEquals(PersistenceStatus.Durability.CONFLICTED, controller.getPersistenceStatus().durability());
				});
			}
		}
	}

	@Test
	void legacyRepositoryAlsoReceivesDetachedDataOffEdt() throws Exception {
		AppData live = new AppData();
		AtomicReference<AppData> saved = new AtomicReference<>();
		AtomicReference<Boolean> savedOnEdt = new AtomicReference<>();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch completed = new CountDownLatch(1);
		LocalRepository repository = new LocalRepository(directory().resolve("workspace.json")) {
			/** {@inheritDoc} */
			@Override
			public void save(AppData snapshot) {
				saved.set(snapshot);
				savedOnEdt.set(SwingUtilities.isEventDispatchThread());
				entered.countDown();
				try {
					assertTrue(release.await(10, TimeUnit.SECONDS));
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(failure);
				}
			}
		};
		try (AppController controller = new AppController(live, repository,
				new PdfExportService(), new IncidentValidator())) {
			SaveCoordinator coordinator = coordinator(controller);
			SwingUtilities.invokeAndWait(() -> {
				controller.markDirty();
				controller.saveAsync(AppController.LinkSource.NONE, failure -> {
					assertNull(failure);
					completed.countDown();
				});
			});
			await(entered);
			SwingUtilities.invokeAndWait(() -> {
				assertInstanceOf(FullDocumentSnapshot.class, coordinator.pendingChanges().get(0));
				assertEquals(PersistenceStatus.Durability.SAVING, controller.getPersistenceStatus().durability());
			});
			release.countDown();
			await(completed);
			assertNotSame(live, saved.get());
			assertFalse(savedOnEdt.get());
			assertFalse(controller.isDirty());
			assertEquals(PersistenceStatus.Durability.SAVED, controller.getPersistenceStatus().durability());
		}
	}

	/**
	 * Creates a repository-local test directory.
	 *
	 * @return unique test directory beneath build output.
	 * @throws java.io.IOException when the directory cannot be created.
	 */
	private Path directory() throws java.io.IOException {
		return Files.createDirectories(Path.of("target", "async-persistence", java.util.UUID.randomUUID().toString()));
	}

	/**
	 * Creates a controller released by each test.
	 *
	 * @param store test backend.
	 * @param id active identity.
	 * @param live editor-owned document.
	 * @return initialized controller.
	 */
	private AppController controller(BlockingStore store, IncidentId id, AppData live) {
		return new AppController(live, store, id, new PdfExportService(), new IncidentValidator());
	}

	/**
	 * Waits only on a test thread, never on Swing.
	 *
	 * @param latch deterministic completion signal.
	 * @throws InterruptedException when interrupted.
	 */
	private void await(CountDownLatch latch) throws InterruptedException {
		assertTrue(latch.await(10, TimeUnit.SECONDS), "Persistence signal timed out");
	}

	/**
	 * Supplies the distinct retained-work failure states.
	 *
	 * @return deterministic backend failures.
	 */
	private java.util.List<RuntimeException> replacementFailures() {
		return java.util.List.of(new StorePersistenceException("Cannot write", null),
				new org.sarmanagement.icsforms.persistence.StoreUnavailableException("Unavailable"),
				new org.sarmanagement.icsforms.persistence.StoreConflictException("Conflict",
						new RevisionToken("expected"), new RevisionToken("current")));
	}

	/**
	 * Reads the scheduler to assert retained request identities without exposing a UI API.
	 *
	 * @param controller controller under test.
	 * @return active scheduler.
	 * @throws ReflectiveOperationException when the scheduler cannot be inspected.
	 */
	private SaveCoordinator coordinator(AppController controller) throws ReflectiveOperationException {
		var field = AppController.class.getDeclaredField("saves");
		field.setAccessible(true);
		return (SaveCoordinator) field.get(controller);
	}

	/** Real local store with a controllable worker write boundary. */
	static final class BlockingStore extends FileIncidentStore {
		private CountDownLatch entered = new CountDownLatch(0);
		private CountDownLatch release = new CountDownLatch(0);
		private volatile AppData savedDocument;
		private volatile boolean saveOnEdt;
		private volatile RuntimeException failure;
		private volatile RuntimeException loadFailure;
		private volatile int loads;
		private volatile int creations;
		private volatile int saveAttempts;
		private volatile int failuresRemaining = -1;
		private RetryPolicy retryPolicy;

		/**
		 * Opens a local backend.
		 *
		 * @param root store root.
		 */
		BlockingStore(Path root) {
			super(root);
		}

		/** Holds the next detached save until the test releases it. */
		private void blockNextSave() {
			entered = new CountDownLatch(1);
			release = new CountDownLatch(1);
		}

		/** {@inheritDoc} */
		@Override
		public org.sarmanagement.icsforms.persistence.StoredIncident loadSnapshot(IncidentId id) {
			loads++;
			if (loadFailure != null) {
				throw loadFailure;
			}
			return super.loadSnapshot(id);
		}

		/** {@inheritDoc} */
		@Override
		public RetryPolicy saveRetryPolicy() {
			return retryPolicy == null ? super.saveRetryPolicy() : retryPolicy;
		}

		/** {@inheritDoc} */
		@Override
		public IncidentId create(AppData data) {
			creations++;
			return super.create(data);
		}

		/** {@inheritDoc} */
		@Override
		public RevisionToken save(IncidentId id, AppData snapshot, RevisionToken expected) {
			saveAttempts++;
			saveOnEdt |= SwingUtilities.isEventDispatchThread();
			savedDocument = snapshot;
			entered.countDown();
			try {
				if (!release.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Test did not release worker");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			if (failure != null && failuresRemaining != 0) {
				if (failuresRemaining > 0) {
					failuresRemaining--;
				}
				throw failure;
			}
			return super.save(id, snapshot, expected);
		}
	}
}
