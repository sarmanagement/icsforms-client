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
					controller.setData(replacement);
					replacement.getIncidentContext().setIncidentName("Unsaved replacement");
					controller.markDirty();
				});
				store.release.countDown();
				controller.close();
				assertSame(replacement, controller.getData());
				assertEquals("Unsaved replacement", replacement.getIncidentContext().getIncidentName());
				assertTrue(controller.isDirty());
				assertEquals(1, replacement.getRevision());
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

	/** Real local store with a controllable worker write boundary. */
	static final class BlockingStore extends FileIncidentStore {
		private CountDownLatch entered = new CountDownLatch(0);
		private CountDownLatch release = new CountDownLatch(0);
		private volatile AppData savedDocument;
		private volatile boolean saveOnEdt;
		private volatile RuntimeException failure;

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
