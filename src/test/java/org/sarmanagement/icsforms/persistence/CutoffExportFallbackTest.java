/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Verifies cutoff exports follow the selected protected journal rather than an unusable primary location. */
class CutoffExportFallbackTest {
	@TempDir
	Path directory;

	@Test
	void fallbackKeepsExactExportsAndFailuresLeaveQueueIntact() throws Exception {
		try (ControllableIncidentStore backing = new ControllableIncidentStore(directory.resolve("host"))) {
			IncidentId incident = backing.create(new AppData());
			HostSession session = new HostSession(backing, incident,
					HostConfig.defaults("127.0.0.1", 0, 0).withBindAddress("127.0.0.1")
							.withStopTiming(Duration.ZERO, Duration.ofSeconds(2)), null);
			session.start();
			try {
				RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "fallback");
				RemoteIncidentGateway gateway = paired.detachGateway();
				paired.close();
				Path fat = directory.resolve("fat");
				Path secure = directory.resolve("secure");
				PrivateFiles.setStoreResolver(path -> path.startsWith(fat)
						? new PrivateFilesTest.FakeStore(false, false) : realStore(path));
				try (RemoteIncidentStore remote = RemoteIncidentStore.connect(gateway, incident, List.of(fat, secure))) {
					assertTrue(remote.disconnectedStorageNotice().orElseThrow().contains(secure.toString()));
					StoredIncident snapshot = remote.loadSnapshot(incident);
					snapshot.data().getIncidentContext().setIncidentName("kept after cutoff");
					remote.save(incident, snapshot.data(), snapshot.revision());
					String operationId = remote.pendingOperations().get(0).operationId();
					Path reference = directory.resolve("reference.json");
					remote.exportOutbox(reference);
					Path exported = remote.exportCutoffOutbox();
					assertTrue(exported.startsWith(secure));
					assertFalse(exported.startsWith(fat));
					assertEquals(new ObjectMapper().readTree(reference.toFile()),
							new ObjectMapper().readTree(exported.toFile()));

					PrivateFiles.setStoreResolver(path -> new PrivateFilesTest.FakeStore(false, false));
					assertThrows(StorePersistenceException.class, remote::exportCutoffOutbox);
					assertEquals(operationId, remote.pendingOperations().get(0).operationId());
					assertTrue(Files.exists(exported));
				} finally {
					PrivateFiles.setStoreResolver(null);
				}
			} finally {
				session.stop();
			}
		}
	}

	/**
	 * Resolves the real store for a protected cache location.
	 *
	 * @param path existing path
	 * @return its file store
	 */
	private static java.nio.file.FileStore realStore(Path path) {
		try {
			return Files.getFileStore(path);
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}
}
