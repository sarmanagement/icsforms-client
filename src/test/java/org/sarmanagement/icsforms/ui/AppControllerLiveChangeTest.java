/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.AppData;

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
	void rejectsFullEditorCommitAfterNewerSharedCommit() {
		AppController controller = controller(new AppData());
		AppController.EditorCommitBinding staleEditor = controller.captureEditorCommitBinding();
		controller.commitIfCurrent(controller.captureIncidentBinding(),
				() -> controller.getData().getIncidentContext().setIncidentName("Newer shared value"));

		boolean applied = controller.commitEditorIfCurrent(staleEditor, AppController.LinkSource.SHARED,
				() -> controller.getData().getIncidentContext().setIncidentName("Stale full-editor value"));

		assertFalse(applied);
		assertEquals("Newer shared value", controller.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void rejectsFullEditorCommitAfterIncidentOrPeriodReplacement() {
		AppController incidentController = controller(new AppData());
		AppController.EditorCommitBinding oldIncident = incidentController.captureEditorCommitBinding();
		incidentController.setData(new AppData());
		assertFalse(incidentController.commitEditorIfCurrent(oldIncident, AppController.LinkSource.SHARED,
				() -> incidentController.getData().getIncidentContext().setIncidentName("Wrong incident")));
		assertEquals("", incidentController.getData().getIncidentContext().getIncidentName());

		AppController periodController = controller(new AppData());
		AppController.EditorCommitBinding oldPeriod = periodController.captureEditorCommitBinding();
		periodController.newOperationalPeriod();
		assertFalse(periodController.commitEditorIfCurrent(oldPeriod, AppController.LinkSource.SHARED,
				() -> periodController.getData().getIncidentContext().setIncidentName("Wrong period")));
		assertEquals("", periodController.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void unchangedEditorFlushDoesNotDirtyOrNotify() throws Exception {
		AppController controller = controller(new AppData());
		List<AppController.IncidentChange> changes = new ArrayList<>();
		controller.addIncidentChangeListener(changes::add);
		AppController.EditorCommitBinding binding = controller.captureEditorCommitBinding();
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED, () -> {
				controller.getData().getIncidentContext().setIncidentName("");
				controller.markDirty();
				assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.NONE,
						controller::markDirty));
			}));
			assertEquals(binding, controller.captureEditorCommitBinding());
			assertFalse(controller.hasUnsavedChanges());
			assertFalse(((javax.swing.Timer) Ics205PanelTest.field(controller, "autosaveTimer")).isRunning());
		});
		SwingUtilities.invokeAndWait(() -> { });
		assertTrue(changes.isEmpty());
		controller.close();
	}

	@Test
	void changedNestedEditorFlushAdvancesRevisionOnlyOnce() throws Exception {
		AppController controller = controller(new AppData());
		var binding = controller.captureEditorCommitBinding();
		List<AppController.IncidentChange> changes = new ArrayList<>();
		controller.addIncidentChangeListener(changes::add);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED, () -> {
				assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.NONE, () -> {
					controller.getData().getForm202().setCommandEmphasis("Updated");
					controller.markDirty();
				}));
				controller.markDirty();
			}));
			assertEquals(binding.revision() + 1, controller.captureEditorCommitBinding().revision());
		});
		SwingUtilities.invokeAndWait(() -> assertEquals(1, changes.size()));
		controller.close();
	}

	@Test
	void fullEditorCommitAdvancesRevisionSoTheTokenCannotBeReused() {
		AppController controller = controller(new AppData());
		AppController.EditorCommitBinding binding = controller.captureEditorCommitBinding();

		assertTrue(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED,
				() -> controller.getData().getIncidentContext().setIncidentName("Committed")));
		assertFalse(controller.commitEditorIfCurrent(binding, AppController.LinkSource.SHARED,
				() -> controller.getData().getIncidentContext().setIncidentName("Reused token")));
		assertEquals("Committed", controller.getData().getIncidentContext().getIncidentName());
	}

	@Test
	void registeredEditorGuardRejectsOldRevisionIncidentAndPeriodBindings() {
		AppController revisionController = controller(new AppData());
		EditorCommitGuard revisionGuard = revisionController.createEditorCommitGuard();
		revisionController.markDirty();
		assertFalse(revisionGuard.commit(AppController.LinkSource.NONE,
				() -> revisionController.getData().getIncidentContext().setIncidentName("Stale revision")));
		assertEquals("", revisionController.getData().getIncidentContext().getIncidentName());

		AppController incidentController = controller(new AppData());
		EditorCommitGuard incidentGuard = incidentController.createEditorCommitGuard();
		incidentController.setData(new AppData());
		assertFalse(incidentGuard.commitField(
				() -> incidentController.getData().getIncidentContext().setIncidentName("Old incident")));
		assertEquals("", incidentController.getData().getIncidentContext().getIncidentName());

		AppController periodController = controller(new AppData());
		EditorCommitGuard periodGuard = periodController.createEditorCommitGuard();
		periodController.newOperationalPeriod();
		assertFalse(periodGuard.commitField(
				() -> periodController.getData().getIncidentContext().setIncidentName("Old period")));
		assertEquals("", periodController.getData().getIncidentContext().getIncidentName());
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
		AppController controller = controller(new AppData());
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
				IncidentStoreTestFixtures.store(controller).load(controller.getActiveIncidentId()).getIncidentContext()
						.getIncidentName());
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
		return IncidentStoreTestFixtures.controller(data);
	}
}
