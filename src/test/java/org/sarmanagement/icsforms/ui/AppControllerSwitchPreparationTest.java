/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics201Form;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.StoredIncident;
import org.sarmanagement.icsforms.persistence.StorePersistenceException;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Candidate preparation must neither mutate nor replace the previous incident on failure. */
class AppControllerSwitchPreparationTest {
	@TempDir
	Path directory;

	@Test
	void successfulSwitchSynchronizesOnlyTheCandidate() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData active = new AppData();
			IncidentId activeId = store.create(active);
			AppData candidate = new AppData();
			candidate.getOrganizationalChart().setIncidentCommanders(List.of("Candidate commander"));
			IncidentId candidateId = store.create(candidate);
			try (AppController controller = controller(store, activeId, active)) {
				active.getOrganizationalChart().setIncidentCommanders(List.of("Unsaved chart commander"));
				active.getForm202().setApprovedByIncidentCommanderName("Unsaved form commander");
				controller.open(candidateId);
				assertEquals(List.of("Unsaved chart commander"),
						active.getOrganizationalChart().getIncidentCommanders());
				assertEquals("Unsaved form commander", active.getForm202().getApprovedByIncidentCommanderName());
				assertEquals("Candidate commander",
						controller.getData().getForm202().getApprovedByIncidentCommanderName());
			}
		}
	}

	@Test
	void failedLockAcquisitionRetainsTheActiveDocumentAndItsResources() throws Exception {
		try (FileIncidentStore store = new FileIncidentStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			AppData active = new AppData();
			IncidentId activeId = store.create(active);
			IncidentId candidateId = store.create(new AppData());
			Path lockPath = directory.resolve("incidents").resolve(candidateId.value() + ".lock");
			Files.createDirectory(lockPath);
			try (AppController controller = controller(store, activeId, active)) {
				controller.markDirty();
				assertThrows(StorePersistenceException.class, () -> controller.replaceActiveDocumentFromStore(candidateId));
				assertSame(active, controller.getData());
				assertEquals(activeId, controller.getActiveIncidentId());
				assertTrue(controller.isDirty());
				assertFalse(other.lock(activeId));
				assertFalse(store.isHeldByCaller(candidateId));
				Files.delete(lockPath);
				assertTrue(other.lock(candidateId));
			}
		}
	}

	@Test
	void failedPreparationRetainsActiveDocumentAuthorityAndOnlyReleasesCandidateLock() {
		try (CandidateStore store = new CandidateStore(directory);
				FileIncidentStore other = new FileIncidentStore(directory)) {
			AppData active = new AppData();
			IncidentId activeId = store.create(active);
			IncidentId candidateId = store.create(new AppData());
			try (AppController controller = controller(store, activeId, active)) {
				controller.markDirty(AppController.LinkSource.ICS202);
				FailingDocument candidate = new FailingDocument();
				candidate.setIncidentId(candidateId.value());
				candidate.getOrganizationalChart().setIncidentCommanders(List.of("Candidate commander"));
				store.candidateId = candidateId;
				store.candidate = new StoredIncident(candidate, store.loadSnapshot(candidateId).revision());
				IllegalStateException failure = assertThrows(IllegalStateException.class,
						() -> controller.replaceActiveDocumentFromStore(candidateId));
				assertEquals("Candidate preparation failed", failure.getMessage());
				assertSame(active, controller.getData());
				assertEquals(activeId, controller.getActiveIncidentId());
				assertTrue(controller.isDirty());
				assertFalse(other.lock(activeId));
				assertTrue(other.lock(candidateId));
				active.getOrganizationalChart().setIncidentCommanders(List.of("Chart commander"));
				active.getForm202().setApprovedByIncidentCommanderName("Form commander");
				controller.synchronizeLinkedFields(AppController.LinkSource.NONE);
				assertEquals(List.of("Form commander"), active.getOrganizationalChart().getIncidentCommanders());
			}
		}
	}

	/**
	 * Creates a tracked controller for the active caller snapshot.
	 *
	 * @param store incident backend.
	 * @param id active identity.
	 * @param data caller document.
	 * @return controller released by the test lifecycle.
	 */
	private AppController controller(FileIncidentStore store, IncidentId id, AppData data) {
		return SwingTestLifecycleExtension.track(
				new AppController(data, store, id, new PdfExportService(), new IncidentValidator()));
	}

	/** Supplies a malformed detached candidate while retaining real advisory locks. */
	private static final class CandidateStore extends FileIncidentStore {
		private IncidentId candidateId;
		private StoredIncident candidate;

		/**
		 * Opens the local test backend.
		 *
		 * @param root store directory.
		 */
		private CandidateStore(Path root) {
			super(root);
		}

		/** {@inheritDoc} */
		@Override
		public synchronized StoredIncident loadSnapshot(IncidentId id) {
			return id.equals(candidateId) && candidate != null ? candidate : super.loadSnapshot(id);
		}
	}

	/** Fails linked-field synchronization after structural defaults have been checked. */
	private static final class FailingDocument extends AppData {
		private int reads;

		/** Creates a candidate with a deterministic preparation failure. */
		private FailingDocument() {
		}

		/** {@inheritDoc} */
		@Override
		public Ics201Form getForm201() {
			if (++reads > 1) {
				throw new IllegalStateException("Candidate preparation failed");
			}
			return super.getForm201();
		}
	}
}
