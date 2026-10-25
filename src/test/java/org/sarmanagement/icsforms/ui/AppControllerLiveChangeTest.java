/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.persistence.LocalRepository;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies incident-bound live notifications and stale-edit rejection. */
class AppControllerLiveChangeTest {
	@TempDir
	Path directory;

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

	private AppController controller(AppData data) {
		return new AppController(data, new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator());
	}
}
