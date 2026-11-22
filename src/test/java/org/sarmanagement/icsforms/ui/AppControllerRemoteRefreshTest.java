/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.InMemoryServerIncidentStore;
import org.sarmanagement.icsforms.persistence.RevisionToken;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies draft-preserving refresh from a remote store, store switching and off-EDT network I/O. */
class AppControllerRemoteRefreshTest {
	@Test
	void cleanViewRefreshesAndUnchangedStateIsRecognised() throws Exception {
		try (InMemoryServerIncidentStore store = new InMemoryServerIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			AppController controller = controller(store, id, data);
			List<AppController.IncidentChangeKind> kinds = Collections.synchronizedList(new ArrayList<>());
			controller.addIncidentChangeListener(change -> kinds.add(change.kind()));
			assertEquals(AppController.RemoteRefreshOutcome.UNCHANGED, controller.refreshFromRemote());
			rename(store, id, "Edited elsewhere");

			assertEquals(AppController.RemoteRefreshOutcome.REFRESHED, controller.refreshFromRemote());
			SwingUtilities.invokeAndWait(() -> { });

			assertEquals("Edited elsewhere", controller.getData().getIncidentContext().getIncidentName());
			assertTrue(kinds.contains(AppController.IncidentChangeKind.REMOTE_REFRESHED));
			assertEquals(AppController.RemoteRefreshOutcome.UNCHANGED, controller.refreshFromRemote());
		}
	}

	@Test
	void dirtyViewKeepsDraftAndOldTokenSoTheDraftCanOnlyConflict() {
		try (InMemoryServerIncidentStore store = new InMemoryServerIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			RevisionToken based = store.revisionToken(data);
			AppController controller = controller(store, id, data);
			data.getIncidentContext().setIncidentName("Operator draft");
			controller.markDirty();
			RevisionToken newer = rename(store, id, "Edited elsewhere");

			assertEquals(AppController.RemoteRefreshOutcome.DRAFT_PRESERVED, controller.refreshFromRemote());
			assertSame(data, controller.getData());
			assertEquals("Operator draft", data.getIncidentContext().getIncidentName());
			assertTrue(controller.isDirty());

			StoreConflictException conflict = assertThrows(StoreConflictException.class, controller::save);
			assertEquals(based, conflict.expected());
			assertEquals(newer, conflict.current());
			assertEquals("Edited elsewhere", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			assertEquals("Operator draft", data.getIncidentContext().getIncidentName());
		}
	}

	@Test
	void refreshRefusesToRunOnTheEventThread() throws Exception {
		try (InMemoryServerIncidentStore store = new InMemoryServerIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = store.create(data);
			AppController controller = controller(store, id, data);
			AtomicBoolean refused = new AtomicBoolean();
			SwingUtilities.invokeAndWait(() -> {
				try {
					controller.refreshFromRemote();
				} catch (IllegalStateException expected) {
					refused.set(true);
				}
			});
			assertTrue(refused.get());
		}
	}

	@Test
	void switchingStoresLoadsTheNewStoreAndSavesThere() {
		try (InMemoryServerIncidentStore local = new InMemoryServerIncidentStore();
				InMemoryServerIncidentStore loopback = new InMemoryServerIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = local.create(data);
			AppData remoteCopy = new AppData();
			remoteCopy.setIncidentId(id.value());
			remoteCopy.getIncidentContext().setIncidentName("Hosted state");
			loopback.create(remoteCopy);
			AppController controller = controller(local, id, data);

			controller.switchStore(loopback, id);

			assertSame(loopback, controller.getStore());
			assertEquals("Hosted state", controller.getData().getIncidentContext().getIncidentName());
			controller.getData().getIncidentContext().setIncidentName("Saved through loopback");
			controller.markDirty();
			controller.save();
			assertEquals("Saved through loopback",
					loopback.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			assertFalse("Saved through loopback".equals(local.loadSnapshot(id).data().getIncidentContext()
					.getIncidentName()));
		}
	}

	@Test
	void switchRefusedWhileWorkIsUnsavedKeepsStoreAndDraft() {
		try (InMemoryServerIncidentStore local = new InMemoryServerIncidentStore();
				InMemoryServerIncidentStore loopback = new InMemoryServerIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = local.create(data);
			AppData remoteCopy = new AppData();
			remoteCopy.setIncidentId(id.value());
			loopback.create(remoteCopy);
			AppController controller = controller(local, id, data);
			data.getIncidentContext().setIncidentName("Draft");
			controller.markDirty();

			assertThrows(IllegalStateException.class, () -> controller.switchStore(loopback, id));

			assertSame(local, controller.getStore());
			assertSame(data, controller.getData());
			assertEquals("Draft", data.getIncidentContext().getIncidentName());
		}
	}

	@Test
	void storeCallsNeverRunOnTheEventThread() throws Exception {
		try (InMemoryServerIncidentStore backing = new InMemoryServerIncidentStore()) {
			AppData data = new AppData();
			IncidentId id = backing.create(data);
			List<String> onEdt = Collections.synchronizedList(new ArrayList<>());
			IncidentStore watched = (IncidentStore) Proxy.newProxyInstance(IncidentStore.class.getClassLoader(),
					new Class<?>[] {IncidentStore.class}, (proxy, method, args) -> {
						if (SwingUtilities.isEventDispatchThread() && (method.getName().equals("loadSnapshot")
								|| method.getName().equals("save") || method.getName().equals("list"))) {
							onEdt.add(method.getName());
						}
						try {
							return method.invoke(backing, args);
						} catch (InvocationTargetException failure) {
							throw failure.getCause();
						}
					});
			AppController controller = controller(watched, id, data);
			SwingUtilities.invokeAndWait(() -> controller.switchStore(watched, id));
			controller.getData().getIncidentContext().setIncidentName("Slow store edit");
			controller.markDirty();
			SwingUtilities.invokeAndWait(controller::save);
			rename(backing, id, "Other");
			controller.refreshFromRemote();

			assertTrue(onEdt.isEmpty(), "Store calls ran on the event thread: " + onEdt);
			assertFalse(controller.isDirty());
		}
	}

	/**
	 * Commits an external rename through a separate snapshot.
	 *
	 * @param store backing store
	 * @param id incident
	 * @param name new name
	 * @return the committed token
	 */
	private static RevisionToken rename(InMemoryServerIncidentStore store, IncidentId id, String name) {
		StoredIncident external = store.loadSnapshot(id);
		external.data().getIncidentContext().setIncidentName(name);
		return store.save(id, external.data(), external.revision());
	}

	/**
	 * Creates a tracked controller.
	 *
	 * @param store store
	 * @param id incident
	 * @param data document
	 * @return controller
	 */
	private AppController controller(IncidentStore store, IncidentId id, AppData data) {
		return SwingTestLifecycleExtension.track(
				new AppController(data, store, id, new PdfExportService(), new IncidentValidator()));
	}
}
