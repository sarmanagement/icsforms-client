/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms;

import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.RadioChannel;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Headless coverage of the production startup transition and PDF registry. */
class AppIntegrationTest {
	@Test
	void productionRegistryIncludesSeparateRadioPlanAndLegacyCommunicationsList() {
		var service = App.createExportService();
		assertTrue(service.formKeys().contains("ICS 205"));
		assertTrue(service.formKeys().contains("ICS 205A"));
		assertEquals(1, service.formKeys().stream().filter("ICS 205"::equals).count());
	}

	@Test
	void startupNewPeriodPersistsExplicitCarryForwardWithoutReusingPreparation() throws Exception {
		Path directory = Files.createDirectories(Path.of("target", "app-period-test-" + UUID.randomUUID()));
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			for (boolean carry : new boolean[]{false, true}) {
				AppData data = preparedIncident();
				var id = store.create(data);
				AppData next = App.loadExistingIncident(store, id, true, carry);
				assertEquals(carry ? 1 : 0, next.getForm205().getChannels().size());
				assertEquals(carry ? "Original instructions" : "", next.getForm205().getSpecialInstructions());
				assertEquals("", next.getForm205().getPreparedBySignature());
				assertEquals("", next.getForm205().getIapPage());
				assertNull(next.getForm205().getDatePrepared());
				assertNull(next.getForm205().getPreparedDateTime());
				assertEquals(carry ? 1 : 0, store.load(id).getForm205().getChannels().size());
				assertEquals(1, next.getOperationalPeriodHistory().size());
				assertEquals("Recorded signature", next.getOperationalPeriodHistory().get(0).getForm205().getPreparedBySignature());
			}
		} finally {
			deleteTree(directory);
		}
	}

	@Test
	void openingWithoutNewPeriodDoesNotApplyCarryCheckboxOrClearPreparation() throws Exception {
		Path directory = Files.createDirectories(Path.of("target", "app-open-test-" + UUID.randomUUID()));
		try (FileIncidentStore store = new FileIncidentStore(directory)) {
			var id = store.create(preparedIncident());
			long revision = store.load(id).getRevision();
			AppData loaded = App.loadExistingIncident(store, id, false, true);
			assertEquals(revision, loaded.getRevision());
			assertEquals("Recorded signature", loaded.getForm205().getPreparedBySignature());
			assertEquals("7", loaded.getForm205().getIapPage());
			assertTrue(loaded.getOperationalPeriodHistory().isEmpty());
		} finally {
			deleteTree(directory);
		}
	}

	/**
	 * Creates a prepared incident whose approval must not leak into a new period.
	 *
	 * @return incident fixture.
	 */
	private static AppData preparedIncident() {
		AppData data = new AppData();
		RadioChannel channel = new RadioChannel();
		channel.setChannelName("Command");
		data.getForm205().getChannels().add(channel);
		data.getForm205().setSpecialInstructions("Original instructions");
		data.getForm205().setPreparedByName("Original preparer");
		data.getForm205().setPreparedBySignature("Recorded signature");
		data.getForm205().setDatePrepared(LocalDateTime.of(2026, 10, 24, 12, 0));
		data.getForm205().setPreparedDateTime(LocalDateTime.of(2026, 10, 24, 13, 0));
		data.getForm205().setIapPage("7");
		return data;
	}

	/**
	 * Removes project-local store fixtures after their handles have closed.
	 *
	 * @param directory fixture directory.
	 * @throws java.io.IOException when deletion fails.
	 */
	private static void deleteTree(Path directory) throws java.io.IOException {
		try (var files = Files.walk(directory)) {
			for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
