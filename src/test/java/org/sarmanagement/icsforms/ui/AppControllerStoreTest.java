package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.IncidentMode;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies store-backed controller transitions without opening Swing dialogs. */
class AppControllerStoreTest {
	@TempDir
	Path directory;

	@Test
	void createsAndDuplicatesIndependentIncidents() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData original = new AppData();
			IncidentId first = store.create(original);
			AppController controller = controller(store, first, original);
			controller.getData().getIncidentContext().setIncidentName("Search Alpha");
			assertTrue(controller.getStoreDisplayLabel().contains(first.value()));

			IncidentId duplicate = controller.duplicateIncident();

			assertNotEquals(first, duplicate);
			assertEquals(duplicate, controller.getActiveIncidentId());
			assertNotEquals("Search Alpha", store.load(first).getIncidentContext().getIncidentName());
			assertEquals("Search Alpha", store.load(duplicate).getIncidentContext().getIncidentName());

			controller.newDocument(IapPhase.INITIAL_RESPONSE, IncidentMode.GENERIC);
			assertNotEquals(duplicate, controller.getActiveIncidentId());
			assertEquals(IapPhase.INITIAL_RESPONSE, store.load(controller.getActiveIncidentId()).getIapPhase());
			assertEquals(IncidentMode.GENERIC, store.load(controller.getActiveIncidentId()).getIncidentMode());
			assertTrue(controller.getStoreDisplayLabel().contains(controller.getActiveIncidentId().value()));
		}
	}

	@Test
	void exportsAndImportsWithoutRebindingTheActiveIncident() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId first = store.create(data);
			AppController controller = controller(store, first, data);
			Path exported = directory.resolve("export.json");

			controller.exportIncident(exported, AppController.LinkSource.NONE);
			assertEquals(first, controller.getActiveIncidentId());
			assertTrue(controller.importIncident(exported));
			assertNotEquals(first, controller.getActiveIncidentId());
			assertEquals(2, store.list().size());
		}
	}

	@Test
	void duplicatePreservesUnsavedEditsAfterRevisionConflict() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId first = store.create(data);
			AppController controller = controller(store, first, data);
			AppData external = store.load(first);
			external.getIncidentContext().setIncidentName("External");
			store.save(first, external);
			controller.getData().getIncidentContext().setIncidentName("Local");
			controller.markDirty();

			assertThrows(StoreConflictException.class, controller::save);
			assertTrue(controller.isDirty());
			assertEquals(first, controller.getActiveIncidentId());
			assertEquals("Local", controller.getData().getIncidentContext().getIncidentName());
			assertEquals("External", store.load(first).getIncidentContext().getIncidentName());
			assertFalse(store.list().isEmpty());

			IncidentId preserved = controller.duplicateIncident();
			assertNotEquals(first, preserved);
			assertEquals("Local", store.load(preserved).getIncidentContext().getIncidentName());
			assertEquals("External", store.load(first).getIncidentContext().getIncidentName());
		}
	}

	@Test
	void declinesToEditIncidentLockedByAnotherStore() {
		try (FileIncidentStore firstStore = new FileIncidentStore(directory);
				FileIncidentStore secondStore = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId id = firstStore.create(data);
			AppController firstController = controller(firstStore, id, data);
			assertEquals(id, firstController.getActiveIncidentId());

			assertThrows(StoreConflictException.class, () -> controller(secondStore, id, secondStore.load(id)));
		}
	}

	@Test
	void releasesPreviousLockOnlyAfterSuccessfulSwitch() {
		try (FileIncidentStore firstStore = new FileIncidentStore(directory);
				FileIncidentStore secondStore = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			IncidentId original = firstStore.create(data);
			AppController controller = controller(firstStore, original, data);
			IncidentId duplicate = controller.duplicateIncident();
			assertTrue(secondStore.lock(original));

			assertThrows(StoreConflictException.class, () -> controller.open(original));
			assertEquals(duplicate, controller.getActiveIncidentId());
			assertTrue(secondStore.isLockedElsewhere(duplicate));

			controller.newDocument(IapPhase.PRE_OP, IncidentMode.SAR);
			assertTrue(secondStore.lock(duplicate));
			IncidentId fresh = controller.getActiveIncidentId();
			secondStore.unlock(original);
			controller.open(original);
			assertTrue(secondStore.lock(fresh));
		}
	}

	/**
	 * Creates a controller for a previously persisted incident.
	 *
	 * @param store file store.
	 * @param id existing incident identifier.
	 * @param data loaded incident data.
	 * @return initialized controller.
	 */
	private AppController controller(FileIncidentStore store, IncidentId id, AppData data) {
		return new AppController(data, store, id, new PdfExportService(), new IncidentValidator());
	}
}
