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

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

	@Test
	void defersAndCoalescesChangesPublishedOnEventDispatchThread() throws Exception {
		AppController controller = controller(new AppData());
		AtomicInteger deliveries = new AtomicInteger();
		controller.addIncidentChangeListener(change -> deliveries.incrementAndGet());

		SwingUtilities.invokeAndWait(() -> {
			controller.markDirty();
			controller.markDirty();
			assertEquals(0, deliveries.get());
		});
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals(1, deliveries.get());
	}

	@Test
	void closeInvalidatesPendingNotificationsAndRetainsDirtyState() throws Exception {
		AppController controller = controller(new AppData());
		AtomicInteger deliveries = new AtomicInteger();
		AppController.IncidentBinding binding = controller.captureIncidentBinding();
		controller.addIncidentChangeListener(change -> deliveries.incrementAndGet());

		SwingUtilities.invokeAndWait(() -> {
			controller.markDirty();
			controller.close();
			controller.close();
		});
		SwingUtilities.invokeAndWait(() -> {
		});

		assertTrue(controller.hasUnsavedChanges());
		assertTrue(controller.isDirty());
		assertFalse(controller.isCurrent(binding));
		assertEquals(0, deliveries.get());
		controller.markDirty();
		assertTrue(controller.hasUnsavedChanges());
	}

	@Test
	void isolatesListenerFailureAfterPersistingEditorChanges() throws Exception {
		Path incident = directory.resolve("persisted-incident.json");
		LocalRepository repository = new LocalRepository(incident);
		AppController controller = new AppController(new AppData(), repository, new PdfExportService(),
				new IncidentValidator());
		AtomicInteger laterDeliveries = new AtomicInteger();
		controller.addIncidentChangeListener(change -> {
			throw new IllegalStateException("Broken presentation listener");
		});
		controller.addIncidentChangeListener(change -> laterDeliveries.incrementAndGet());

		controller.persistEditorChanges(
				() -> controller.getData().getIncidentContext().setIncidentName("Persist despite listener failure"));
		SwingUtilities.invokeAndWait(() -> {
		});

		assertEquals("Persist despite listener failure", controller.getData().getIncidentContext().getIncidentName());
		assertEquals("Persist despite listener failure",
				repository.loadOrDefault().getIncidentContext().getIncidentName());
		assertEquals(1, laterDeliveries.get());
	}

	@Test
	void documentMutationPublishesAfterDocumentNotificationCompletes() throws Exception {
		AppController controller = controller(new AppData());
		JTextField source = new JTextField();
		AtomicBoolean insideDocumentNotification = new AtomicBoolean();
		AtomicBoolean deliveredInline = new AtomicBoolean();
		controller.addIncidentChangeListener(change -> {
			deliveredInline.set(insideDocumentNotification.get());
			if (!"Presented".equals(source.getText())) {
				source.setText("Presented");
			}
		});
		source.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				publishFromDocumentNotification();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				publishFromDocumentNotification();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				publishFromDocumentNotification();
			}

			private void publishFromDocumentNotification() {
				insideDocumentNotification.set(true);
				try {
					controller.markDirty();
				} finally {
					insideDocumentNotification.set(false);
				}
			}
		});

		SwingUtilities.invokeAndWait(() -> source.setText("Draft"));
		SwingUtilities.invokeAndWait(() -> {
		});

		assertFalse(deliveredInline.get());
		assertEquals("Presented", source.getText());
	}

	private AppController controller(AppData data) {
		return SwingTestLifecycleExtension.track(new AppController(data,
				new LocalRepository(directory.resolve("incident.json")), new PdfExportService(),
				new IncidentValidator()));
	}
}
