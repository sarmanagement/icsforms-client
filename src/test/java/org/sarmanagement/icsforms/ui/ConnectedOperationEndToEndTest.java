/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.pdf.Ics201PdfRenderer;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.ConnectionMonitor;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.RemoteIncidentGateway;
import org.sarmanagement.icsforms.persistence.RemoteIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end connected operation: a host controller over the loopback store and two connected controllers share
 * one incident over real HTTPS/mTLS, observe each other through polling, recover from a failed save and produce
 * PDFs locally. Also covers root-field refresh through {@link AppController}.
 */
class ConnectedOperationEndToEndTest {
	@TempDir
	Path directory;

	@Test
	void hostAndTwoConnectedInstancesShareEditsRecoverFromFailedSavesAndExportLocally() throws Exception {
		String previous = System.setProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY, "50");
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			AtomicBoolean submitsFail = new AtomicBoolean();
			RemoteIncidentStore hostStore = host.session().loopbackStore();
			RemoteIncidentStore storeA = host.pair();
			RemoteIncidentStore storeB = host.pairThrough(real -> failingSubmit(real, submitsFail));
			AppController hostController = open(hostStore, host);
			hostController.save();
			assertFalse(host.backing().load(host.incident()).getActivityEventTypes().isEmpty());
			assertEquals("Shared", host.backing().load(host.incident()).getForm201().getIncidentName());
			AppController clientA = open(storeA, host);
			AppController clientB = open(storeB, host);
			try (ConnectionMonitor monitorHost = monitor(hostStore, hostController, host);
					ConnectionMonitor monitorA = monitor(storeA, clientA, host);
					ConnectionMonitor monitorB = monitor(storeB, clientB, host)) {
				SwingUtilities.invokeAndWait(() -> {
					clientA.getData().getIncidentContext().setIncidentName("Edited by A");
					clientA.markDirty();
				});
				clientA.save();
				assertEquals("Shared", clientB.getData().getIncidentContext().getIncidentName());
				SwingUtilities.invokeAndWait(() -> {
					clientB.getData().setIapPhase(IapPhase.DURING_OP);
					clientB.markDirty();
				});
				clientB.save();
				monitorHost.start();
				monitorA.start();
				monitorB.start();
				for (AppController controller : List.of(hostController, clientA, clientB)) {
					waitFor(() -> "Edited by A".equals(controller.getData().getIncidentContext().getIncidentName())
							&& controller.getData().getIapPhase() == IapPhase.DURING_OP);
				}

				submitsFail.set(true);
				SwingUtilities.invokeAndWait(() -> {
					clientB.getData().setIncidentMode(IncidentMode.GENERIC);
					clientB.markDirty();
				});
				assertThrows(RuntimeException.class, clientB::save);
				assertTrue(clientB.isDirty(), "A failed save must leave the edit unsaved");
				assertEquals(IncidentMode.GENERIC, clientB.getData().getIncidentMode());
				assertEquals(IncidentMode.SAR, host.backing().load(host.incident()).getIncidentMode());
				submitsFail.set(false);
				clientB.save();
				assertFalse(clientB.isDirty());
				for (AppController controller : List.of(hostController, clientA)) {
					waitFor(() -> controller.getData().getIncidentMode() == IncidentMode.GENERIC);
				}
				assertEquals(IncidentMode.GENERIC, host.backing().load(host.incident()).getIncidentMode());

				PdfExportService pdf = new PdfExportService(new Ics201PdfRenderer());
				String form = pdf.formKeys().get(0);
				int index = 0;
				for (AppController controller : List.of(hostController, clientA, clientB)) {
					Path output = Files.createDirectory(directory.resolve("pdf-" + index++));
					Path file = pdf.exportSelected(form, controller.getData(), output);
					assertTrue(Files.size(file) > 0);
					assertTrue(file.startsWith(output), "PDFs are written only to the local chosen directory");
				}
			}
		} finally {
			if (previous == null) {
				System.clearProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY);
			} else {
				System.setProperty(ConnectionMonitor.POLL_INTERVAL_PROPERTY, previous);
			}
		}
	}

	@Test
	void aRootFieldEditRefreshesACleanViewThroughTheController() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore mine = host.pair();
			AppController controller = open(mine, host);
			IapPhase other = changed(controller.getData().getIapPhase(), null);
			editRoot(host, other);

			assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
			assertEquals(other, controller.getData().getIapPhase());
			assertFalse(controller.isDirty());
		}
	}

	@Test
	void aRootFieldEditNeverOverwritesADirtyViewAndExposesTheConflictOnSave() throws Exception {
		try (HostedIncidentFixture host = new HostedIncidentFixture(directory.resolve("host"), "Shared")) {
			RemoteIncidentStore mine = host.pair();
			AppController controller = open(mine, host);
			AppData draft = controller.getData();
			IapPhase base = draft.getIapPhase();
			IapPhase other = changed(base, null);
			IapPhase mineValue = changed(base, other);
			draft.setIapPhase(mineValue);
			controller.markDirty();
			editRoot(host, other);

			assertEquals(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED, controller.refreshFromRemote());
			assertEquals(mineValue, controller.getData().getIapPhase());
			assertTrue(controller.isDirty());
			assertThrows(StoreConflictException.class, controller::save);
			assertEquals(mineValue, controller.getData().getIapPhase());
			assertEquals(other, host.backing().load(host.incident()).getIapPhase());
		}
	}

	/**
	 * Commits a root-field edit from another connected device.
	 *
	 * @param host hosted incident
	 * @param phase new phase
	 */
	private static void editRoot(HostedIncidentFixture host, IapPhase phase) {
		RemoteIncidentStore other = host.pair();
		StoredIncident snapshot = other.loadSnapshot(host.incident());
		snapshot.data().setIapPhase(phase);
		other.save(host.incident(), snapshot.data(), snapshot.revision());
	}

	/**
	 * Picks a phase different from the given ones.
	 *
	 * @param first phase to avoid
	 * @param second another phase to avoid or null
	 * @return a distinct phase
	 */
	private static IapPhase changed(IapPhase first, IapPhase second) {
		for (IapPhase phase : IapPhase.values()) {
			if (phase != first && phase != second) {
				return phase;
			}
		}
		throw new AssertionError("no distinct phase");
	}

	/**
	 * Opens a tracked controller on the remote snapshot.
	 *
	 * @param store remote store
	 * @param host hosted incident
	 * @return controller
	 */
	private static AppController open(IncidentStore store, HostedIncidentFixture host) {
		AppData data = store.loadSnapshot(host.incident()).data();
		return SwingTestLifecycleExtension
				.track(new AppController(data, store, host.incident(), new PdfExportService(), new IncidentValidator()));
	}

	/**
	 * Creates a polling monitor that refreshes the controller the way the main window does.
	 *
	 * @param store remote store
	 * @param controller controller to refresh
	 * @param host hosted incident
	 * @return unstarted monitor
	 */
	private static ConnectionMonitor monitor(RemoteIncidentStore store, AppController controller,
			HostedIncidentFixture host) {
		return ConnectionMonitor.withDefaults(store.gateway(), host.incident(), 0, cursor -> {
			return controller.refreshFromRemote() != AppController.RemoteRefreshOutcome.DRAFT_PRESERVED;
		}, status -> { });
	}

	/**
	 * Wraps a gateway so submissions fail while the flag is set.
	 *
	 * @param real real gateway
	 * @param fail failure switch
	 * @return wrapping gateway
	 */
	private static RemoteIncidentGateway failingSubmit(RemoteIncidentGateway real, AtomicBoolean fail) {
		return (RemoteIncidentGateway) Proxy.newProxyInstance(RemoteIncidentGateway.class.getClassLoader(),
				new Class<?>[] {RemoteIncidentGateway.class}, (proxy, method, args) -> {
					if (fail.get() && method.getName().equals("submit")) {
						throw new StoreUnavailableException("The host is unreachable", null);
					}
					try {
						return method.invoke(real, args);
					} catch (InvocationTargetException failure) {
						throw failure.getCause();
					}
				});
	}

	/**
	 * Waits for a condition.
	 *
	 * @param condition condition to await
	 * @throws Exception when interrupted or the event-thread check fails
	 */
	private static void waitFor(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		AtomicBoolean reached = new AtomicBoolean();
		do {
			SwingUtilities.invokeAndWait(() -> reached.set(condition.getAsBoolean()));
			if (reached.get()) {
				return;
			}
			assertTrue(System.nanoTime() < deadline, "condition not reached");
			Thread.sleep(25);
		} while (true);
	}
}
