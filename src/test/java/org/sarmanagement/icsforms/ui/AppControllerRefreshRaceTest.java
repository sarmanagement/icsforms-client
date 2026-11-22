/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.ConnectionMonitor;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentService;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.InMemoryServerIncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RevisionToken;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises refresh reads that overlap local edits, persistence and incident replacement. */
class AppControllerRefreshRaceTest {
	@Test
	void savedEditCannotBeReplacedByAnEarlierRemoteRead() throws Exception {
		try (Fixture fixture = new Fixture()) {
			Future<AppController.RemoteRefreshOutcome> refresh = fixture.startRefresh();
			SwingUtilities.invokeAndWait(() -> {
				fixture.data.getIncidentContext().setIncidentName("Saved edit");
				fixture.controller.markDirty();
			});
			fixture.controller.save();
			var savedRevision = fixture.controller.getPersistenceStatus().revision();
			fixture.releaseLoad.countDown();

			assertEquals(AppController.RemoteRefreshOutcome.RETRY, refresh.get(5, TimeUnit.SECONDS));
			assertSame(fixture.data, fixture.controller.getData());
			assertEquals("Saved edit", fixture.controller.getData().getIncidentContext().getIncidentName());
			assertEquals(savedRevision, fixture.controller.getPersistenceStatus().revision());
			assertFalse(fixture.controller.isDirty());
			assertEquals(AppController.RemoteRefreshOutcome.UNCHANGED, fixture.controller.refreshFromRemote());
		}
	}

	@Test
	void saveAlreadyInFlightWhenReadStartsCannotBeOverwritten() throws Exception {
		try (Fixture fixture = new Fixture()) {
			CountDownLatch saved = new CountDownLatch(1);
			fixture.blockSave.set(true);
			SwingUtilities.invokeAndWait(() -> {
				fixture.data.getIncidentContext().setIncidentName("Saving edit");
				fixture.controller.markDirty();
				fixture.controller.saveAsync(AppController.LinkSource.NONE, failure -> saved.countDown());
			});
			assertTrue(fixture.saveEntered.await(5, TimeUnit.SECONDS));
			Future<AppController.RemoteRefreshOutcome> refresh = fixture.startRefresh();
			fixture.releaseSave.countDown();
			assertTrue(saved.await(5, TimeUnit.SECONDS));
			fixture.releaseLoad.countDown();

			assertEquals(AppController.RemoteRefreshOutcome.RETRY, refresh.get(5, TimeUnit.SECONDS));
			assertSame(fixture.data, fixture.controller.getData());
			assertEquals("Saving edit", fixture.data.getIncidentContext().getIncidentName());
			assertFalse(fixture.controller.isDirty());
			assertEquals(AppController.RemoteRefreshOutcome.UNCHANGED, fixture.controller.refreshFromRemote());
		}
	}

	@Test
	void unchangedReadStillRetriesWhenSaveStartsBeforeApplication() throws Exception {
		try (Fixture fixture = new Fixture()) {
			Future<AppController.RemoteRefreshOutcome> refresh = fixture.startRefresh();
			CountDownLatch saved = new CountDownLatch(1);
			fixture.blockSave.set(true);
			SwingUtilities.invokeAndWait(() ->
					fixture.controller.saveAsync(AppController.LinkSource.NONE, failure -> saved.countDown()));
			assertTrue(fixture.saveEntered.await(5, TimeUnit.SECONDS));
			fixture.releaseLoad.countDown();

			assertEquals(AppController.RemoteRefreshOutcome.RETRY, refresh.get(5, TimeUnit.SECONDS));
			fixture.releaseSave.countDown();
			assertTrue(saved.await(5, TimeUnit.SECONDS));
			assertSame(fixture.data, fixture.controller.getData());
		}
	}

	@Test
	void completedNoOpSaveInvalidatesReadEvenWhenTokenDoesNotChange() throws Exception {
		try (Fixture fixture = new Fixture()) {
			fixture.staleRead.set(true);
			fixture.noOpSave.set(true);
			Future<AppController.RemoteRefreshOutcome> refresh = fixture.startRefresh();
			var revision = fixture.controller.getPersistenceStatus().revision();
			fixture.controller.save();
			assertEquals(revision, fixture.controller.getPersistenceStatus().revision());
			fixture.releaseLoad.countDown();

			assertEquals(AppController.RemoteRefreshOutcome.RETRY, refresh.get(5, TimeUnit.SECONDS));
			assertSame(fixture.data, fixture.controller.getData());
			assertEquals(revision, fixture.controller.getPersistenceStatus().revision());
		}
	}

	@Test
	void monitorRetriesSameCursorAfterOverlappingNoOpSave() throws Exception {
		try (Fixture fixture = new Fixture()) {
			StoredIncident host = fixture.backing.loadSnapshot(fixture.id);
			host.data().getIncidentContext().setIncidentName("Host update");
			fixture.backing.save(fixture.id, host.data(), host.revision());
			fixture.noOpSave.set(true);
			List<Long> requestedCursors = new ArrayList<>();
			RemoteIncidentGateway gateway = (RemoteIncidentGateway) Proxy.newProxyInstance(
					RemoteIncidentGateway.class.getClassLoader(), new Class<?>[] {RemoteIncidentGateway.class},
					(proxy, method, args) -> {
						if (method.getName().equals("changesSince")) {
							requestedCursors.add((Long) args[1]);
							return new IncidentService.ChangeResult(1, List.of());
						}
						throw new UnsupportedOperationException(method.getName());
					});
			AtomicBoolean remoteNewer = new AtomicBoolean(true);
			try (ConnectionMonitor monitor = ConnectionMonitor.withDefaults(gateway, fixture.id, 0,
					MainFrame.monitorChangeHandler(fixture.controller, remoteNewer::set), status -> { })) {
				Future<?> firstPoll = fixture.executor.submit(monitor::pollOnce);
				assertTrue(fixture.loadEntered.await(5, TimeUnit.SECONDS));
				fixture.controller.save();
				fixture.releaseLoad.countDown();
				firstPoll.get(5, TimeUnit.SECONDS);
				assertFalse(remoteNewer.get());
				assertSame(fixture.data, fixture.controller.getData());
				monitor.pollOnce();
				assertEquals("Host update", fixture.controller.getData().getIncidentContext().getIncidentName());
				monitor.pollOnce();
				assertEquals(List.of(0L, 0L, 1L), requestedCursors);
				assertFalse(remoteNewer.get());
			}
		}
	}

	@Test
	void onlyPendingRefreshOutcomesPreventCursorAcknowledgement() {
		assertFalse(AppController.RemoteRefreshOutcome.RETRY.acknowledgesRemoteChange());
		assertFalse(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED.acknowledgesRemoteChange());
		assertTrue(AppController.RemoteRefreshOutcome.IGNORED.acknowledgesRemoteChange());
		assertTrue(AppController.RemoteRefreshOutcome.UNCHANGED.acknowledgesRemoteChange());
		assertTrue(AppController.RemoteRefreshOutcome.REFRESHED.acknowledgesRemoteChange());
	}

	@Test
	void unsavedEditMadeDuringReadIsPreserved() throws Exception {
		try (Fixture fixture = new Fixture()) {
			Future<AppController.RemoteRefreshOutcome> refresh = fixture.startRefresh();
			SwingUtilities.invokeAndWait(() -> {
				fixture.data.getIncidentContext().setIncidentName("Draft");
				fixture.controller.markDirty();
			});
			fixture.releaseLoad.countDown();

			assertEquals(AppController.RemoteRefreshOutcome.RETRY, refresh.get(5, TimeUnit.SECONDS));
			assertSame(fixture.data, fixture.controller.getData());
			assertEquals("Draft", fixture.data.getIncidentContext().getIncidentName());
			assertTrue(fixture.controller.isDirty());
		}
	}

	@Test
	void replacingSameIncidentInvalidatesEarlierRead() throws Exception {
		try (Fixture fixture = new Fixture()) {
			Future<AppController.RemoteRefreshOutcome> refresh = fixture.startRefresh();
			AppData replacement = new AppData();
			replacement.getIncidentContext().setIncidentName("Replacement");
			fixture.controller.setData(replacement);
			fixture.releaseLoad.countDown();

			assertEquals(AppController.RemoteRefreshOutcome.RETRY, refresh.get(5, TimeUnit.SECONDS));
			assertSame(replacement, fixture.controller.getData());
			assertEquals("Replacement", replacement.getIncidentContext().getIncidentName());
		}
	}

	/** Provides a store that can pause after a read or before a write without holding a store lock. */
	private static final class Fixture implements AutoCloseable {
		private final InMemoryServerIncidentStore backing = new InMemoryServerIncidentStore();
		private final AppData data = new AppData();
		private final IncidentId id = backing.create(data);
		private final CountDownLatch loadEntered = new CountDownLatch(1);
		private final CountDownLatch releaseLoad = new CountDownLatch(1);
		private final CountDownLatch saveEntered = new CountDownLatch(1);
		private final CountDownLatch releaseSave = new CountDownLatch(1);
		private final AtomicBoolean blockLoad = new AtomicBoolean(true);
		private final AtomicBoolean blockSave = new AtomicBoolean();
		private final AtomicBoolean staleRead = new AtomicBoolean();
		private final AtomicBoolean noOpSave = new AtomicBoolean();
		private final ExecutorService executor = Executors.newSingleThreadExecutor();
		private final IncidentStore store = (IncidentStore) Proxy.newProxyInstance(IncidentStore.class.getClassLoader(),
				new Class<?>[] {IncidentStore.class}, (proxy, method, args) -> {
					try {
						if (method.getName().equals("save") && blockSave.compareAndSet(true, false)) {
							saveEntered.countDown();
							await(releaseSave);
						}
						if (method.getName().equals("save") && noOpSave.get()) {
							return args[2];
						}
						Object result = method.invoke(backing, args);
						if (method.getName().equals("loadSnapshot") && blockLoad.compareAndSet(true, false)) {
							if (staleRead.get()) {
								result = new StoredIncident(((StoredIncident) result).data(), new RevisionToken("older"));
							}
							loadEntered.countDown();
							await(releaseLoad);
						}
						return result;
					} catch (InvocationTargetException failure) {
						throw failure.getCause();
					}
				});
		private final AppController controller =
				new AppController(data, store, id, new PdfExportService(), new IncidentValidator());

		/**
		 * Starts a refresh and waits until its old snapshot has been captured.
		 *
		 * @return the pending refresh result.
		 * @throws Exception if the read fails to start.
		 */
		private Future<AppController.RemoteRefreshOutcome> startRefresh() throws Exception {
			Future<AppController.RemoteRefreshOutcome> result = executor.submit(controller::refreshFromRemote);
			assertTrue(loadEntered.await(5, TimeUnit.SECONDS));
			return result;
		}

		/**
		 * Awaits a test gate with a bounded timeout.
		 *
		 * @param gate gate to await.
		 * @throws InterruptedException if interrupted.
		 */
		private static void await(CountDownLatch gate) throws InterruptedException {
			if (!gate.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for test gate");
			}
		}

		/** Releases blocked operations and closes the controller and test store. */
		@Override
		public void close() {
			releaseLoad.countDown();
			releaseSave.countDown();
			executor.shutdownNow();
			controller.close();
			backing.close();
		}
	}
}
