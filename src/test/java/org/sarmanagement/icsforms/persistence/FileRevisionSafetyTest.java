/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Real filesystem failures and lock lifetime regressions for token-aware operations. */
class FileRevisionSafetyTest {
	private static final ObjectMapper RAW = new ObjectMapper().registerModule(new JavaTimeModule())
			.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
			.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
	@TempDir
	Path directory;

	@Test
	void lockReleaseIsIdempotentAfterStoreClose() {
		FileIncidentStore store = new FileIncidentStore(directory);
		IncidentId id = store.create(new AppData());
		assertTrue(store.lock(id));
		store.close();
		assertDoesNotThrow(() -> store.unlock(id));
		assertFalse(store.isHeldByCaller(id));
	}

	@Test
	void committedNormalizationPreservesChildObjectsAndExactAssignedIds() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			var context = data.getIncidentContext();
			var form = data.getForm201();
			var actions = form.getCurrentActions();
			var sections = data.getForm208().getSiteSafetyPlan().getTextSections();
			sections.put("briefing", "Preserve map identity");
			var action = new Ics201Form.ActionEntry();
			var idField = Ics201Form.ActionEntry.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(action, null);
			actions.add(action);
			IncidentId id = store.create(data);
			assertSame(context, data.getIncidentContext());
			assertSame(form, data.getForm201());
			assertSame(actions, data.getForm201().getCurrentActions());
			assertSame(sections, data.getForm208().getSiteSafetyPlan().getTextSections());
			assertSame(action, data.getForm201().getCurrentActions().get(0));
			assertEquals(store.loadSnapshot(id).data().getForm201().getCurrentActions().get(0).getId(),
					action.getId());
			idField.set(action, null);
			store.save(id, data, store.revisionToken(data));
			assertSame(form, data.getForm201());
			assertSame(actions, data.getForm201().getCurrentActions());
			assertSame(sections, data.getForm208().getSiteSafetyPlan().getTextSections());
			assertSame(action, data.getForm201().getCurrentActions().get(0));
			assertEquals(store.loadSnapshot(id).data().getForm201().getCurrentActions().get(0).getId(),
					action.getId());
		}
	}

	@Test
	void postCommitUnlockFailureIsLoggedWithoutReportingSaveFailure() {
		try (CleanupFailingStore store = new CleanupFailingStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			IncidentId id = store.create(new AppData());
			StoredIncident snapshot = store.loadSnapshot(id);
			store.failNextUnlock = true;
			RevisionToken committed = store.save(id, snapshot.data(), snapshot.revision());
			assertEquals(committed, store.loadSnapshot(id).revision());
			assertEquals(committed, store.revisionToken(snapshot.data()));
			assertTrue(other.lock(id));
		}
	}

	@Test
	void unlockCleanupFailureIsSuppressedOnOriginalSaveFailure() throws Exception {
		try (CleanupFailingStore store = new CleanupFailingStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			IncidentId id = store.create(new AppData());
			StoredIncident snapshot = store.loadSnapshot(id);
			var before = RAW.valueToTree(snapshot.data());
			Files.createDirectory(directory.resolve("incidents").resolve(id.value() + ".json.tmp"));
			store.failNextUnlock = true;
			StorePersistenceException failure = assertThrows(StorePersistenceException.class,
					() -> store.save(id, snapshot.data(), snapshot.revision()));
			assertEquals(1, failure.getSuppressed().length);
			assertInstanceOf(StorePersistenceException.class, failure.getSuppressed()[0]);
			assertEquals(before, RAW.valueToTree(snapshot.data()));
			assertTrue(other.lock(id));
		}
	}

	@Test
	void exhaustedCounterFailsWithTypedPersistenceErrorAndNullDocumentsWithValidationError() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId id = store.create(new AppData());
			Path file = directory.resolve("incidents").resolve(id.value() + ".json");
			var tree = (com.fasterxml.jackson.databind.node.ObjectNode) RAW.readTree(file.toFile());
			tree.put("revision", Long.MAX_VALUE);
			RAW.writeValue(file.toFile(), tree);
			StoredIncident snapshot = store.loadSnapshot(id);
			var before = RAW.valueToTree(snapshot.data());
			assertThrows(StorePersistenceException.class, () -> store.save(id, snapshot.data(), snapshot.revision()));
			assertEquals(before, RAW.valueToTree(snapshot.data()));
			assertThrows(StoreValidationException.class, () -> store.create(null));
			assertThrows(StoreValidationException.class, () -> store.save(id, null, snapshot.revision()));
		}
	}

	@Test
	void failedCreateDoesNotNormalizeOrAssignCallerMetadata() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			data.setSchemaVersion(1);
			var before = RAW.valueToTree(data);
			Path incidents = directory.resolve("incidents");
			Path held = directory.resolve("held");
			Files.move(incidents, held);
			try {
				assertThrows(StorePersistenceException.class, () -> store.create(data));
				assertEquals(before, RAW.valueToTree(data));
			} finally {
				Files.move(held, incidents);
			}
			assertTrue(store.list().isEmpty());
		}
	}

	@Test
	void saveFailureDoesNotNormalizeCallerAndReleasesTransientLock() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			IncidentId id = store.create(new AppData());
			StoredIncident snapshot = store.loadSnapshot(id);
			snapshot.data().setSchemaVersion(1);
			var form = snapshot.data().getForm201();
			var actions = form.getCurrentActions();
			var action = new Ics201Form.ActionEntry();
			var idField = Ics201Form.ActionEntry.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(action, null);
			actions.add(action);
			var before = RAW.valueToTree(snapshot.data());
			Path blocked = directory.resolve("incidents").resolve(id.value() + ".json.tmp");
			Files.createDirectory(blocked);
			assertThrows(StorePersistenceException.class,
					() -> store.save(id, snapshot.data(), snapshot.revision()));
			assertEquals(before, RAW.valueToTree(snapshot.data()));
			assertNull(idField.get(action));
			assertSame(form, snapshot.data().getForm201());
			assertSame(actions, snapshot.data().getForm201().getCurrentActions());
			assertEquals(snapshot.revision(), store.loadSnapshot(id).revision());
			assertTrue(other.lock(id));
			other.unlock(id);
		}
	}

	@Test
	void successfulSaveReleasesOnlyTransientLocks() {
		try (FileIncidentStore store = new FileIncidentStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			IncidentId id = store.create(new AppData());
			StoredIncident snapshot = store.loadSnapshot(id);
			store.save(id, snapshot.data(), snapshot.revision());
			assertTrue(other.lock(id));
			other.unlock(id);
			assertTrue(store.lock(id));
			snapshot = store.loadSnapshot(id);
			store.save(id, snapshot.data(), snapshot.revision());
			assertFalse(other.lock(id));
			store.unlock(id);
			assertTrue(other.lock(id));
		}
	}

	@Test
	void tokenlessDeleteUsesLastCallerObservationEvenAfterInternalConflictReads() {
		try (FileIncidentStore first = new FileIncidentStore(directory);
				FileIncidentStore second = new FileIncidentStore(directory)) {
			IncidentId id = first.create(new AppData());
			StoredIncident stale = first.loadSnapshot(id);
			first.list();
			StoredIncident latest = second.loadSnapshot(id);
			RevisionToken committed = second.save(id, latest.data(), latest.revision());
			assertThrows(StoreConflictException.class, () -> first.save(id, stale.data(), stale.revision()));
			StoreConflictException conflict = assertThrows(StoreConflictException.class, () -> first.delete(id));
			assertEquals(stale.revision(), conflict.expected());
			assertEquals(committed, conflict.current());
			assertThrows(StoreConflictException.class, () -> first.delete(id));
			assertTrue(second.lock(id));
			second.unlock(id);
			first.list();
			first.delete(id);
			assertThrows(IncidentNotFoundException.class, () -> second.loadSnapshot(id));
		}
	}

	@Test
	void exportCannotOverwriteAnotherLiveIncident() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			IncidentId first = store.create(new AppData());
			AppData named = new AppData();
			named.getIncidentContext().setIncidentName("Retained");
			IncidentId second = store.create(named);
			Path target = directory.resolve("incidents").resolve(second.value() + ".json");
			assertThrows(StoreValidationException.class, () -> store.exportFile(first, target));
			assertEquals("Retained", store.loadSnapshot(second).data().getIncidentContext().getIncidentName());
		}
	}

	/** Simulates a cleanup error after actually releasing the transient resource. */
	private static final class CleanupFailingStore extends FileIncidentStore {
		private boolean failNextUnlock;

		/**
		 * Opens the isolated file store.
		 *
		 * @param root test root
		 */
		private CleanupFailingStore(Path root) {
			super(root);
		}

		/** {@inheritDoc} */
		@Override
		public synchronized void unlock(IncidentId id) {
			super.unlock(id);
			if (failNextUnlock) {
				failNextUnlock = false;
				throw new StorePersistenceException("Scheduled unlock cleanup failure", null);
			}
		}
	}
}
