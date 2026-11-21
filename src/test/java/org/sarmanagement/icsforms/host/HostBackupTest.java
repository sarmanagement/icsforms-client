/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.persistence.FileIncidentStore;
import org.sarmanagement.icsforms.persistence.IncidentId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Verifies that backup and restore keep the incident, authority key and device registry consistent. */
class HostBackupTest {
	@TempDir
	Path directory;

	@Test
	void restoreKeepsDevicesAdmittedAndRevocationsInForce(@TempDir Path elsewhere) throws IOException {
		Path backup = elsewhere.resolve("backup.zip");
		DeviceCredential admitted;
		DeviceCredential revokedBefore;
		DeviceCredential revokedAfter;
		IncidentId incident;
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			incident = fixture.incident;
			admitted = fixture.pair("Admitted");
			revokedBefore = fixture.pair("Revoked before");
			revokedAfter = fixture.pair("Revoked after");
			fixture.session.revoke(idOf(fixture, "Revoked before"));
			HostBackup.create(fixture.store, incident, fixture.session.storage(), backup);
			fixture.session.revoke(idOf(fixture, "Admitted"));
			fixture.session.stop();
			if (OwnerOnlyFiles.posix()) {
				assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
						Files.getPosixFilePermissions(backup));
			}
			HostBackup.restore(fixture.store, incident, fixture.session.storage(), backup);
		}
		try (HostFixture restored = new HostFixture(directory, incident)) {
			restored.start();
			assertEquals(HostConnectionState.CONNECTED, restored.client(admitted).diagnose(admitted));
			assertEquals(HostConnectionState.CREDENTIAL_REVOKED,
					restored.client(revokedBefore).diagnose(revokedBefore));
			assertEquals(HostConnectionState.CONNECTED, restored.client(revokedAfter).diagnose(revokedAfter));
		}
	}

	@Test
	void restoreIntoFreshInstallKeepsAuthorityAndRevocations(@TempDir Path fresh, @TempDir Path elsewhere)
			throws IOException {
		Path backup = elsewhere.resolve("backup.zip");
		DeviceCredential admitted;
		DeviceCredential revoked;
		IncidentId incident;
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			incident = fixture.incident;
			admitted = fixture.pair("Admitted");
			revoked = fixture.pair("Revoked");
			fixture.session.revoke(idOf(fixture, "Revoked"));
			HostBackup.create(fixture.store, incident, fixture.session.storage(), backup);
		}
		try (FileIncidentStore store = new FileIncidentStore(fresh)) {
			HostStorage storage = new HostStorage(fresh, new MutableClock());
			HostBackup.restore(store, incident, storage, backup);
		}
		try (HostFixture restored = new HostFixture(fresh, incident)) {
			restored.start();
			assertEquals(HostConnectionState.CONNECTED, restored.client(admitted).diagnose(admitted));
			assertEquals(HostConnectionState.CREDENTIAL_REVOKED, restored.client(revoked).diagnose(revoked));
		}
	}

	@Test
	void restoreRefusedWhileHosting(@TempDir Path elsewhere) {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			Path backup = elsewhere.resolve("backup.zip");
			HostBackup.create(fixture.store, fixture.incident, fixture.session.storage(), backup);
			assertThrows(HostStorageException.class,
					() -> HostBackup.restore(fixture.store, fixture.incident, fixture.session.storage(), backup));
		}
	}

	@Test
	void backupInsideLiveStoreAndTamperedBackupAreRejected(@TempDir Path elsewhere) throws IOException {
		try (HostFixture fixture = new HostFixture(directory)) {
			fixture.start();
			assertThrows(HostStorageException.class, () -> HostBackup.create(fixture.store, fixture.incident,
					fixture.session.storage(), directory.resolve("inside.zip")));
			Path backup = elsewhere.resolve("backup.zip");
			HostBackup.create(fixture.store, fixture.incident, fixture.session.storage(), backup);
			assertThrows(HostStorageException.class, () -> HostBackup.create(fixture.store, fixture.incident,
					fixture.session.storage(), backup));
			fixture.session.stop();

			Path unexpected = elsewhere.resolve("evil.zip");
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
				zip.putNextEntry(new ZipEntry("../../evil.txt"));
				zip.write(1);
				zip.closeEntry();
			}
			Files.write(unexpected, bytes.toByteArray());
			HostStorageException evil = assertThrows(HostStorageException.class, () -> HostBackup
					.restore(fixture.store, fixture.incident, fixture.session.storage(), unexpected));
			assertTrue(evil.getMessage().contains("unexpected"));

			byte[] original = Files.readAllBytes(backup);
			original[original.length / 2] ^= 0x55;
			Path corrupt = elsewhere.resolve("corrupt.zip");
			Files.write(corrupt, original);
			assertThrows(HostStorageException.class, () -> HostBackup.restore(fixture.store, fixture.incident,
					fixture.session.storage(), corrupt));
			assertTrue(fixture.store.editLocks().orElseThrow().lock(fixture.incident));
		}
	}

	/**
	 * Finds a device identifier by name.
	 *
	 * @param fixture running fixture
	 * @param name device name
	 * @return device id
	 */
	private static String idOf(HostFixture fixture, String name) {
		return fixture.session.devices().stream().filter(device -> device.name().equals(name)).findFirst()
				.orElseThrow().deviceId();
	}
}
