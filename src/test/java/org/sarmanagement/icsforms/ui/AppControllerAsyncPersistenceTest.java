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
	void oldGenerationCompletionCannotTouchTheReplacementDocument() throws Exception {
		try (BlockingStore store = new BlockingStore(directory())) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			try (AppController controller = controller(store, id, live)) {
				store.blockNextSave();
				SwingUtilities.invokeAndWait(() -> {
					live.getIncidentContext().setIncidentName("Old generation");
					controller.markDirty();
					controller.saveAsync(AppController.LinkSource.NONE,
							failure -> fail("Old generation UI completion must be discarded"));
				});
				await(store.entered);
				AppData replacement = new AppData();
				SwingUtilities.invokeAndWait(() -> {
					assertThrows(IllegalStateException.class, () -> controller.setData(replacement));
					controller.discardUnsavedChangesAndSetData(replacement);
					replacement.getIncidentContext().setIncidentName("Unsaved replacement");
					controller.markDirty();
				});
				PersistenceStatus replacementStatus = controller.getPersistenceStatus();
				store.release.countDown();
				controller.close();
				assertSame(replacement, controller.getData());
				assertEquals("Unsaved replacement", replacement.getIncidentContext().getIncidentName());
				assertTrue(controller.isDirty());
				assertEquals(1, replacement.getRevision());
				assertEquals(replacementStatus, controller.getPersistenceStatus());
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
	void legacyRepositoryAlsoReceivesDetachedDataOffEdt() throws Exception {
		AppData live = new AppData();
		AtomicReference<AppData> saved = new AtomicReference<>();
		AtomicReference<Boolean> savedOnEdt = new AtomicReference<>();
		LocalRepository repository = new LocalRepository(directory().resolve("workspace.json")) {
			/** {@inheritDoc} */
			@Override
			public void save(AppData snapshot) {
				saved.set(snapshot);
				savedOnEdt.set(SwingUtilities.isEventDispatchThread());
			}
		};
		try (AppController controller = new AppController(live, repository,
				new PdfExportService(), new IncidentValidator())) {
			SwingUtilities.invokeAndWait(controller::save);
			assertNotSame(live, saved.get());
			assertFalse(savedOnEdt.get());
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
		public RevisionToken save(IncidentId id, AppData snapshot, RevisionToken expected) {
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
			if (failure != null) {
				throw failure;
			}
			return super.save(id, snapshot, expected);
		}
	}
}
