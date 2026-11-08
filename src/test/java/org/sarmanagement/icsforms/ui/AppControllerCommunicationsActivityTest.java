// SPDX-License-Identifier: GPL-3.0-or-later
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.ActivityEventType;
import org.sarmanagement.icsforms.model.ActivityLogEntry;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.Ics214Form;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.model.RadioCheckStatus;
import org.sarmanagement.icsforms.model.ResourceAssignment;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;
import org.sarmanagement.icsforms.persistence.IncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentSummary;
import org.sarmanagement.icsforms.persistence.ControllableIncidentStore;
import org.sarmanagement.icsforms.persistence.StoreConflictException;
import org.sarmanagement.icsforms.persistence.StoreDescriptor;
import org.sarmanagement.icsforms.pdf.PdfExportService;
import org.sarmanagement.icsforms.validation.IncidentValidator;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies atomic channel-check activity persistence and focused stale-edit rejection. */
class AppControllerCommunicationsActivityTest {
	@TempDir
	Path directory;

	@Test
	void checkAndOrdinaryIcpEntryPersistTogetherWithoutReplacingLiveObjects() {
		AppData data = new AppData();
		RadioChannel channel = channel(data);
		ResourceAssignment assignment = new ResourceAssignment();
		assignment.setResourceIdentifier("Search team");
		data.getForm204().getResourcesAssigned().add(assignment);
		AppController controller = controller(data);
		Ics214Form log = new Ics214Form();
		ActivityLogEntry entry = entry();
		entry.setAssignmentId(assignment.getAssignmentId());
		entry.setResourceIdentifier("Robin");
		var binding = controller.captureIncidentBinding();
		RadioChannel baseline = channel.deepCopy();
		channel.setRxFrequency("155.500");
		data.getForm202().setObjectives(List.of("Latest independent edit"));
		controller.markDirty();

		commit(controller, binding, channel, baseline, log, entry);

		assertSame(data, controller.getData());
		assertSame(channel, data.getForm205().getChannels().get(0));
		assertSame(log, data.getActivityLogs().get(0));
		assertSame(entry, log.getActivityLog().get(0));
		assertEquals(RadioCheckStatus.SUCCESSFUL, channel.getRadioCheckStatus());
		assertEquals(entry.getTimestamp(), channel.getRadioCheckedAt());
		assertEquals("COML", channel.getRadioCheckedBy());
		assertEquals("Coverage", channel.getCoverageGuidance());
		assertEquals("Relay", channel.getRelayGuidance());
		assertEquals("155.500", channel.getRxFrequency());
		assertEquals("Robin", entry.getResourceIdentifier());
		assertFalse(controller.isDirty());
		AppData restored = IncidentStoreTestFixtures.store(controller).load(controller.getActiveIncidentId());
		assertEquals(RadioCheckStatus.SUCCESSFUL, restored.getForm205().getChannels().get(0).getRadioCheckStatus());
		assertEquals("155.500", restored.getForm205().getChannels().get(0).getRxFrequency());
		assertEquals(entry.getEntryId(), restored.getActivityLogs().get(0).getActivityLog().get(0).getEntryId());
		assertEquals(assignment.getAssignmentId(),
				restored.getActivityLogs().get(0).getActivityLog().get(0).getAssignmentId());
		assertEquals(List.of("Latest independent edit"), restored.getForm202().getObjectives());
	}

	@Test
	void saveFailureRestoresLiveCollectionsCheckAndPreviousDirtyStateIncludingDetachedDraft() {
		for (boolean dirty : List.of(false, true)) {
			for (boolean attached : List.of(false, true)) {
				AppData data = new AppData();
				RadioChannel channel = channel(data);
				Ics214Form log = new Ics214Form();
				ActivityLogEntry previous = new ActivityLogEntry();
				if (attached) {
					log.getActivityLog().add(previous);
					data.getActivityLogs().add(log);
				}
				IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
				AppController controller = fixture.controller();
				var channels = data.getForm205().getChannels();
				var logs = data.getActivityLogs();
				var activities = log.getActivityLog();
				fixture.store().failNext(ControllableIncidentStore.Operation.SAVE,
						new IllegalStateException("Injected write failure"));
				if (dirty) {
					controller.markDirty();
				}
				data.getForm202().setObjectives(List.of("Keep latest edits"));
				ActivityLogEntry draft = entry();

				assertThrows(IllegalStateException.class, () -> commit(controller,
						controller.captureIncidentBinding(), channel, channel.deepCopy(), log, draft));

				assertEquals(dirty, controller.isDirty());
				assertSame(data, controller.getData());
				assertSame(channels, data.getForm205().getChannels());
				assertSame(channel, channels.get(0));
				assertSame(logs, data.getActivityLogs());
				assertSame(activities, log.getActivityLog());
				assertEquals(attached ? List.of(previous) : List.of(), activities);
				assertEquals(attached ? List.of(log) : List.of(), logs);
				assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
				assertEquals("", channel.getRadioCheckedBy());
				assertEquals(null, channel.getRadioCheckedAt());
				assertEquals(List.of("Keep latest edits"), data.getForm202().getObjectives());
				assertEquals("Clear radio check", draft.getNotableActivity());
				controller.close();
			}
		}
	}

	@Test
	void revisionConflictRollsBackCheckAndEntryWithoutReplacingExistingIdentity() {
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			AppData data = new AppData();
			RadioChannel channel = channel(data);
			Ics214Form log = new Ics214Form();
			data.getActivityLogs().add(log);
			var id = store.create(data);
			AppController controller = SwingTestLifecycleExtension.track(
					new AppController(data, store, id, new PdfExportService(), new IncidentValidator()));
			AppData external = store.load(id);
			external.getForm202().setObjectives(List.of("External"));
			store.save(id, external);
			controller.markDirty();
			long revision = data.getRevision();

			assertThrows(StoreConflictException.class, () -> commit(controller,
					controller.captureIncidentBinding(), channel, channel.deepCopy(), log, entry()));

			assertSame(channel, data.getForm205().getChannels().get(0));
			assertSame(log, data.getActivityLogs().get(0));
			assertTrue(log.getActivityLog().isEmpty());
			assertTrue(controller.isDirty());
			assertEquals(revision, data.getRevision());
			assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
			assertEquals(List.of("External"), store.load(id).getForm202().getObjectives());
			assertTrue(store.load(id).getActivityLogs().get(0).getActivityLog().isEmpty());
		}
	}

	@Test
	void backendNeutralStoreReceivesOneCompleteTransactionAndRejectedSaveRestoresLiveState() {
		for (boolean previousDirty : List.of(false, true)) {
			AppData data = new AppData();
			data.setRevision(17);
			RadioChannel channel = channel(data);
			Ics214Form log = new Ics214Form();
			ActivityLogEntry previous = new ActivityLogEntry();
			log.getActivityLog().add(previous);
			data.getActivityLogs().add(log);
			ActivityLogEntry draft = entry();
			IncidentId id = IncidentId.newId();
			RejectingIncidentStore store = new RejectingIncidentStore((savedId, savedData) -> {
				assertEquals(id, savedId);
				assertNotSame(data, savedData);
				assertFalse(javax.swing.SwingUtilities.isEventDispatchThread());
				assertEquals(17, savedData.getRevision());
				RadioChannel savedChannel = savedData.getForm205().getChannels().get(0);
				assertNotSame(channel, savedChannel);
				assertEquals(RadioCheckStatus.SUCCESSFUL, savedChannel.getRadioCheckStatus());
				assertEquals("COML", savedChannel.getRadioCheckedBy());
				assertEquals(draft.getTimestamp(), savedChannel.getRadioCheckedAt());
				Ics214Form savedLog = savedData.getActivityLogs().get(0);
				assertNotSame(log, savedLog);
				assertEquals(List.of(previous.getNotableActivity(), draft.getNotableActivity()),
						savedLog.getActivityLog().stream().map(ActivityLogEntry::getNotableActivity).toList());
				assertEquals(List.of("Latest independent edits"), savedData.getForm202().getObjectives());
			});
			AppController controller = SwingTestLifecycleExtension.track(
					new AppController(data, store, id, new PdfExportService(), new IncidentValidator()));
			data.getForm202().setObjectives(List.of("Latest independent edits"));
			if (previousDirty) {
				controller.markDirty();
			}
			var channels = data.getForm205().getChannels();
			var logs = data.getActivityLogs();
			var activities = log.getActivityLog();

			IllegalStateException failure = assertThrows(IllegalStateException.class, () -> commit(controller,
					controller.captureIncidentBinding(), channel, channel.deepCopy(), log, draft));

			assertEquals("Backend rejected transaction", failure.getMessage());
			assertEquals(1, store.saveCalls);
			assertSame(data, controller.getData());
			assertSame(channels, data.getForm205().getChannels());
			assertSame(channel, channels.get(0));
			assertSame(logs, data.getActivityLogs());
			assertSame(log, logs.get(0));
			assertSame(activities, log.getActivityLog());
			assertEquals(List.of(previous), activities);
			assertEquals(17, data.getRevision());
			assertEquals(previousDirty, controller.isDirty());
			assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
			assertEquals("", channel.getRadioCheckedBy());
			assertEquals(null, channel.getRadioCheckedAt());
			assertEquals(List.of("Latest independent edits"), data.getForm202().getObjectives());
			assertEquals("Clear radio check", draft.getNotableActivity());
			controller.close();
		}
	}

	@Test
	void focusedConcurrentCheckMissingAndAmbiguousChannelsRejectWithoutAddingEntry() {
		AppData data = new AppData();
		RadioChannel channel = channel(data);
		AppController controller = controller(data);
		RadioChannel baseline = channel.deepCopy();
		channel.setRadioCheckNotes("Other operator");
		assertThrows(IllegalStateException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, baseline, new Ics214Form(), entry()));
		assertEquals("Other operator", channel.getRadioCheckNotes());
		data.getForm205().getChannels().clear();
		assertThrows(IllegalStateException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, baseline, new Ics214Form(), entry()));
		data.getForm205().getChannels().addAll(List.of(channel, channel.deepCopy()));
		assertThrows(IllegalStateException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, channel.deepCopy(), new Ics214Form(), entry()));
		assertTrue(data.getActivityLogs().isEmpty());
		assertFalse(controller.isDirty());
	}

	@Test
	void staleIncidentGenerationAndMissingAssignmentRejectWithoutMutation() {
		AppData data = new AppData();
		RadioChannel channel = channel(data);
		AppController controller = controller(data);
		var stale = controller.captureIncidentBinding();
		controller.setData(new AppData());
		controller.setData(data);
		assertThrows(IllegalStateException.class,
				() -> commit(controller, stale, channel, channel.deepCopy(), new Ics214Form(), entry()));
		ActivityLogEntry missingTask = entry();
		missingTask.setAssignmentId("removed-assignment");
		assertThrows(IllegalStateException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, channel.deepCopy(), new Ics214Form(), missingTask));
		assertTrue(data.getActivityLogs().isEmpty());
		assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
	}

	@Test
	void invalidCheckAndProblemEventCannotMutateChannel() {
		AppData data = new AppData();
		RadioChannel channel = channel(data);
		AppController controller = controller(data);
		ActivityLogEntry problem = entry();
		problem.setEventTypeId(ActivityEventType.ID_COMMUNICATIONS_PROBLEM);
		assertThrows(IllegalArgumentException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, channel.deepCopy(), new Ics214Form(), problem));
		assertThrows(IllegalArgumentException.class, () -> controller.commitRadioCheckWithActivity(
				controller.captureIncidentBinding(), channel.getChannelId(), channel.deepCopy(),
				RadioCheckStatus.SUCCESSFUL, "", null, "", new Ics214Form(), entry()));
		assertTrue(data.getActivityLogs().isEmpty());
		assertFalse(controller.isDirty());
		assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
	}

	@Test
	void blankCheckNarrativeAndMismatchedChannelIdentityRejectBeforeMutation() {
		AppData data = new AppData();
		RadioChannel channel = channel(data);
		AppController controller = controller(data);
		Ics214Form log = new Ics214Form();
		data.getActivityLogs().add(log);
		ActivityLogEntry blank = entry();
		blank.setNotableActivity(" \t\n ");
		assertThrows(IllegalArgumentException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, channel.deepCopy(), log, blank));
		RadioChannel wrongIdentity = channel.deepCopy();
		wrongIdentity.setChannelId("another-channel");
		assertThrows(IllegalArgumentException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, wrongIdentity, log, entry()));
		Ics214Form taskLog = new Ics214Form();
		taskLog.setLinkedSarTaskAssignmentId("task-log");
		assertThrows(IllegalArgumentException.class, () -> commit(controller,
				controller.captureIncidentBinding(), channel, channel.deepCopy(), taskLog, entry()));
		assertSame(log, data.getActivityLogs().get(0));
		assertSame(channel, data.getForm205().getChannels().get(0));
		assertTrue(log.getActivityLog().isEmpty());
		assertEquals(RadioCheckStatus.NOT_RECORDED, channel.getRadioCheckStatus());
		assertFalse(controller.isDirty());
	}

	/**
	 * Adds a channel with independent guidance to a test incident.
	 *
	 * @param data incident receiving the channel
	 * @return live channel
	 */
	private static RadioChannel channel(AppData data) {
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		channel.setCoverageGuidance("Coverage");
		channel.setRelayGuidance("Relay");
		data.getForm205().getChannels().add(channel);
		return channel;
	}

	/**
	 * Creates an accepted detached communications-check draft.
	 *
	 * @return activity draft
	 */
	private static ActivityLogEntry entry() {
		ActivityLogEntry entry = new ActivityLogEntry();
		entry.setTimestamp(LocalDateTime.of(2026, 11, 4, 9, 30));
		entry.setEventTypeId(ActivityEventType.ID_COMMUNICATIONS_CHECK);
		entry.setNotableActivity("Clear radio check");
		return entry;
	}

	/**
	 * Commits a recorded successful check using the supplied focused baseline.
	 *
	 * @param controller incident controller
	 * @param binding initiating incident binding
	 * @param channel selected channel
	 * @param baseline detached focused baseline
	 * @param log destination
	 * @param entry accepted draft
	 */
	private static void commit(AppController controller, AppController.IncidentBinding binding,
			RadioChannel channel, RadioChannel baseline, Ics214Form log, ActivityLogEntry entry) {
		controller.commitRadioCheckWithActivity(binding, channel.getChannelId(), baseline,
				RadioCheckStatus.SUCCESSFUL, "COML", entry.getTimestamp(), "Clear", log, entry);
	}

	/**
	 * Builds a tracked controller for a test repository.
	 *
	 * @param data incident data
	 * @param data incident model to edit
	 * @return controller closed by the existing Swing lifecycle extension
	 */
	private AppController controller(AppData data) {
		return IncidentStoreTestFixtures.controller(data, new PdfExportService(), new IncidentValidator());
	}

	/** Rejects an observed save through the backend-neutral store seam without file or network access. */
	private static final class RejectingIncidentStore implements IncidentStore {
		private final BiConsumer<IncidentId, AppData> observer;
		private int saveCalls;

		/**
		 * Creates a rejecting store that inspects the complete attempted document.
		 *
		 * @param observer assertion callback invoked once per attempted save
		 */
		private RejectingIncidentStore(BiConsumer<IncidentId, AppData> observer) {
			this.observer = observer;
		}

		/** {@inheritDoc} */
		@Override
		public void save(IncidentId id, AppData data) {
			saveCalls++;
			observer.accept(id, data);
			data.setRevision(data.getRevision() + 1);
			throw new IllegalStateException("Backend rejected transaction");
		}

		/** {@inheritDoc} */
		@Override
		public IncidentId create(AppData data) {
			throw new UnsupportedOperationException("Creation is outside this focused store test.");
		}

		/** {@inheritDoc} */
		@Override
		public AppData load(IncidentId id) {
			throw new UnsupportedOperationException("Loading is outside this focused store test.");
		}

		/** {@inheritDoc} */
		@Override
		public List<IncidentSummary> list() {
			throw new UnsupportedOperationException("Listing is outside this focused store test.");
		}

		/** {@inheritDoc} */
		@Override
		public void delete(IncidentId id) {
			throw new UnsupportedOperationException("Deletion is outside this focused store test.");
		}

		/** {@inheritDoc} */
		@Override
		public Optional<IncidentId> importFile(Path jsonFile) {
			throw new UnsupportedOperationException("Import is outside this focused store test.");
		}

		/** {@inheritDoc} */
		@Override
		public void exportFile(IncidentId id, Path target) {
			throw new UnsupportedOperationException("Export is outside this focused store test.");
		}

		/** {@inheritDoc} */
		@Override
		public StoreDescriptor describe() {
			return new StoreDescriptor("Test", "Backend-neutral rejecting store");
		}

		/** {@inheritDoc} */
		@Override
		public void close() {
		}
	}
}
