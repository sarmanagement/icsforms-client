/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.RevisionToken;
import org.sarmanagement.icsforms.persistence.RetryPolicy;
import org.sarmanagement.icsforms.persistence.StoreUnavailableException;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Displayed persistence state and save actions must preserve editor text during worker IO. */
class MainFramePersistenceStatusTest {
	@Test
	void atomicPersistenceDisablesIncidentInputUntilTheDetachedCandidateIsAccepted() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path root = Files.createDirectories(Path.of("target", "desktop-persistence", java.util.UUID.randomUUID().toString()));
		try (DelayedStore store = new DelayedStore(root)) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			AtomicReference<MainFrame> reference = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> reference.set(new MainFrame(live, store, id,
					new PdfExportService(), new IncidentValidator(), root)));
			MainFrame frame = reference.get();
			CountDownLatch completed = new CountDownLatch(1);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			store.entered = new CountDownLatch(1);
			store.release = new CountDownLatch(1);
			SwingUtilities.invokeLater(() -> {
				try {
					AppController controller = field(frame, "controller");
					controller.persistEditorChanges(() -> live.getForm201().setIncidentNumber("Atomic candidate"));
				} catch (Throwable problem) {
					failure.set(problem);
				} finally {
					completed.countDown();
				}
			});
			try {
				assertTrue(store.entered.await(10, TimeUnit.SECONDS));
				SwingUtilities.invokeAndWait(() -> {
					assertFalse(frame.isEnabled());
					assertEquals("", live.getForm201().getIncidentNumber());
				});
				store.release.countDown();
				assertTrue(completed.await(10, TimeUnit.SECONDS));
				assertNull(failure.get());
				waitUntil(frame::isEnabled);
				SwingUtilities.invokeAndWait(() -> {
					assertTrue(frame.isEnabled());
					assertEquals("Atomic candidate", live.getForm201().getIncidentNumber());
				});
			} finally {
				store.release.countDown();
				SwingUtilities.invokeAndWait(frame::dispose);
			}
		}
	}

	@Test
	void saveMenuReturnsImmediatelyAndPreservesTextWhileDetachedSaveRuns() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path root = Files.createDirectories(Path.of("target", "desktop-persistence", java.util.UUID.randomUUID().toString()));
		try (DelayedStore store = new DelayedStore(root)) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			AtomicReference<MainFrame> reference = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(live, store, id, new PdfExportService(), new IncidentValidator(), root);
				reference.set(frame);
				JTextField input = field(field(frame, "incidentContextPanel"), "incidentNameField");
				input.setText("First editor text");
				store.entered = new CountDownLatch(1);
				store.release = new CountDownLatch(1);
				saveItem(frame).doClick();
				assertEquals(1, store.release.getCount(), "Save action must return before IO completes");
			});
			MainFrame frame = reference.get();
			try {
				assertTrue(store.entered.await(10, TimeUnit.SECONDS));
				SwingUtilities.invokeAndWait(() -> {
					JLabel status = field(frame, "persistenceLabel");
					assertTrue(status.getText().contains("saving"));
					assertTrue(status.getText().contains("Synchronization: not applicable"));
					assertTrue(frame.isEnabled(), "Asynchronous saves must not pause editor input");
					JTextField input = field(field(frame, "incidentContextPanel"), "incidentNameField");
					input.setText("Newer partial editor text");
				});
				store.release.countDown();
				waitUntil(() -> !((JLabel) field(frame, "persistenceLabel")).getText().contains("saving"));
				SwingUtilities.invokeAndWait(() -> {
					JTextField input = field(field(frame, "incidentContextPanel"), "incidentNameField");
					assertEquals("Newer partial editor text", input.getText());
					AppController controller = field(frame, "controller");
					assertTrue(controller.isDirty());
					assertFalse(store.saveOnEdt);
				});
			} finally {
				store.release.countDown();
				SwingUtilities.invokeAndWait(frame::dispose);
			}
		}
	}

	@Test
	void unavailableStatusOffersRetryAndSuccessfulRetryClearsTheFailure() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Path root = Files.createDirectories(Path.of("target", "desktop-persistence", java.util.UUID.randomUUID().toString()));
		try (DelayedStore store = new DelayedStore(root)) {
			AppData live = new AppData();
			IncidentId id = store.create(live);
			AtomicReference<MainFrame> reference = new AtomicReference<>();
			CountDownLatch completed = new CountDownLatch(1);
			SwingUtilities.invokeAndWait(() -> {
				MainFrame frame = new MainFrame(live, store, id, new PdfExportService(), new IncidentValidator(), root);
				reference.set(frame);
				JTextField input = field(field(frame, "incidentContextPanel"), "incidentNameField");
				input.setText("Retained offline editor text");
				store.unavailable = true;
				AppController controller = field(frame, "controller");
				controller.setAutosaveErrorHandler(failure -> fail("Retry must succeed"));
				controller.saveAsync(AppController.LinkSource.NONE, failure -> {
					assertInstanceOf(StoreUnavailableException.class, failure);
					completed.countDown();
				});
			});
			MainFrame frame = reference.get();
			try {
				assertTrue(completed.await(10, TimeUnit.SECONDS));
				SwingUtilities.invokeAndWait(() -> {
					JLabel status = field(frame, "persistenceLabel");
					JButton retry = field(frame, "retryPersistenceButton");
					assertTrue(status.getText().contains("unavailable"));
					assertTrue(retry.isEnabled());
					retry.doClick();
				});
				waitUntil(() -> ((JLabel) field(frame, "persistenceLabel")).getText().contains("saved"));
				SwingUtilities.invokeAndWait(() -> {
					JButton retry = field(frame, "retryPersistenceButton");
					assertFalse(retry.isEnabled());
					JTextField input = field(field(frame, "incidentContextPanel"), "incidentNameField");
					assertEquals("Retained offline editor text", input.getText());
				});
				assertEquals("Retained offline editor text", store.loadSnapshot(id).data().getIncidentContext().getIncidentName());
			} finally {
				SwingUtilities.invokeAndWait(frame::dispose);
			}
		}
	}

	/**
	 * Finds the explicit asynchronous save menu entry.
	 *
	 * @param frame displayed editor.
	 * @return save action.
	 */
	private JMenuItem saveItem(MainFrame frame) {
		var fileMenu = frame.getJMenuBar().getMenu(0);
		for (int index = 0; index < fileMenu.getItemCount(); index++) {
			JMenuItem item = fileMenu.getItem(index);
			if (item != null && "Save".equals(item.getText())) {
				return item;
			}
		}
		throw new AssertionError("Save menu item not found");
	}

	/**
	 * Reads an established editor test seam without changing visibility in production.
	 *
	 * @param target editor or panel.
	 * @param name field name.
	 * @param <T> expected field type.
	 * @return field value.
	 */
	@SuppressWarnings("unchecked")
	private <T> T field(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return (T) field.get(target);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError(failure);
		}
	}

	/**
	 * Polls presentation only from the test thread with reads marshaled to Swing.
	 *
	 * @param condition expected presentation state.
	 * @throws Exception when Swing dispatch or waiting fails.
	 */
	private void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
		java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
		for (int attempts = 0; attempts < 200; attempts++) {
			SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
			if (ready.get()) {
				return;
			}
			Thread.sleep(20);
		}
		fail("Persistence presentation did not reach its expected state");
	}

	/** Backend with explicit retry, exposing a delayed and one-shot unavailable write. */
	private static final class DelayedStore extends FileIncidentStore {
		private volatile CountDownLatch entered = new CountDownLatch(0);
		private volatile CountDownLatch release = new CountDownLatch(0);
		private volatile boolean unavailable;
		private volatile boolean saveOnEdt;

		/**
		 * Opens a test backend.
		 *
		 * @param root local directory.
		 */
		private DelayedStore(Path root) {
			super(root);
		}

		/** {@inheritDoc} */
		@Override
		public RetryPolicy saveRetryPolicy() {
			return RetryPolicy.explicit();
		}

		/** {@inheritDoc} */
		@Override
		public RevisionToken save(IncidentId id, AppData snapshot, RevisionToken expected) {
			saveOnEdt |= SwingUtilities.isEventDispatchThread();
			entered.countDown();
			try {
				if (!release.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Worker release timed out");
				}
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(failure);
			}
			if (unavailable) {
				unavailable = false;
				throw new StoreUnavailableException("Offline test backend");
			}
			return super.save(id, snapshot, expected);
		}
	}
}
