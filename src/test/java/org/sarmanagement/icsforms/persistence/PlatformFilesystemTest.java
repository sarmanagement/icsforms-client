/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.host.HostConfig;
import org.sarmanagement.icsforms.host.HostGateway;
import org.sarmanagement.icsforms.host.HostSession;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.ui.MainFrame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in integration test against a filesystem mounted by the platform workflow. */
@Tag("platform-filesystem")
@EnabledIfSystemProperty(named = "icsforms.platformFilesystemTests", matches = "true")
class PlatformFilesystemTest {
	@TempDir
	Path directory;

	@Test
	void privateJournalOrNotifiedFailoverSurvivesReopening() throws Exception {
		String scenario = System.getenv("PLATFORM_FILESYSTEM_CASE");
		Path volume = Path.of(System.getenv("PLATFORM_FILESYSTEM_ROOT")).toRealPath();
		String type = Files.getFileStore(volume).type().toLowerCase(Locale.ROOT);
		System.out.println("Case: " + scenario + "; filesystem: " + type + "; root: " + volume);
		assertFilesystem(scenario, type);
		if (scenario.equals("linux-fat-owner")) {
			assertRestrictiveFatReadback(volume);
		}
		boolean fallback = scenario.contains("fat");
		Path home = volume.resolve("icsforms-" + UUID.randomUUID());
		Path profile = directory.resolve("profile");
		Files.createDirectories(profile);
		String originalUserHome = System.getProperty("user.home");
		System.setProperty("user.home", profile.toString());
		ControllableIncidentStore backing = new ControllableIncidentStore(directory.resolve("host"));
		IncidentId incident = backing.create(new AppData());
		HostSession session = new HostSession(backing, incident,
				HostConfig.defaults("127.0.0.1", 0, 0).withBindAddress("127.0.0.1")
						.withStopTiming(Duration.ZERO, Duration.ofSeconds(2)), null);
		session.start();
		try {
			List<Path> roots = cacheRoots(home);
			Path preferred = roots.get(0);
			Path alternate = roots.get(1);
			assertEquals(profile.resolve(".icsforms").resolve("remote-cache"), alternate);
			RemoteIncidentStore paired = HostGateway.pair(session.issueInvitation(), "platform-test");
			RemoteIncidentGateway gateway = paired.detachGateway();
			paired.close();
			String fingerprint = gateway.authorityFingerprint().orElseThrow();
			Path selected = fallback ? alternate : preferred;
			try (RemoteIncidentStore store = RemoteIncidentStore.connect(gateway, incident, roots)) {
				assertTrue(store.disconnectedStorageEnabled());
				assertEquals(fallback, store.disconnectedStorageNotice().isPresent());
				StoredIncident snapshot = store.loadSnapshot(incident);
				String hostName = snapshot.data().getIncidentContext().getIncidentName();
				store.detachGateway().close();
				snapshot.data().getIncidentContext().setIncidentName("offline filesystem edit");
				store.save(incident, snapshot.data(), snapshot.revision());
				assertEquals(1, store.pendingOperations().size());
				assertEquals(hostName, backing.loadSnapshot(incident).data().getIncidentContext().getIncidentName());
			}
			Path authority = selected.resolve(fingerprint);
			Path journal = authority.resolve(incident.value() + ".json");
			assertTrue(Files.isRegularFile(journal));
			if (fallback) {
				assertFalse(Files.exists(preferred.resolve(fingerprint).resolve(incident.value() + ".json")));
			}
			assertPrivate(selected, true);
			assertPrivate(authority, true);
			assertPrivate(journal, false);
			assertPrivate(authority.resolve(incident.value() + ".lock"), false);
			String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
			Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
					"-cp", System.getProperty("java.class.path"), RestartReader.class.getName(),
					preferred.toString(), alternate.toString(), fingerprint, incident.value())
					.redirectErrorStream(true).start();
			boolean finished = child.waitFor(60, TimeUnit.SECONDS);
			if (!finished) {
				child.destroyForcibly();
				child.waitFor(30, TimeUnit.SECONDS);
			}
			String output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(finished, "Restart reader timed out: " + output);
			assertEquals(0, child.exitValue(), output);
			assertTrue(output.contains("RESTART VERIFIED"), output);
			System.out.println("Outcome: " + (fallback ? "notified fallback" : "owner-only")
					+ "; offline edit survived reopening");
		} finally {
			System.setProperty("user.home", originalUserHome);
			session.stop();
			backing.close();
			if (Files.exists(home)) {
				try (var paths = Files.walk(home)) {
					for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
			}
		}
	}

	/**
	 * Uses the same candidate ordering as the application, with an isolated user profile.
	 *
	 * @param home preferred application home
	 * @return ordered application cache roots
	 * @throws Exception if the application candidate method cannot be invoked
	 */
	@SuppressWarnings("unchecked")
	private static List<Path> cacheRoots(Path home) throws Exception {
		var method = MainFrame.class.getDeclaredMethod("cacheRoots", Path.class);
		method.setAccessible(true);
		return (List<Path>) method.invoke(null, home);
	}

	/**
	 * Confirms that this mount reports 0700 for a file even after requesting 0600.
	 *
	 * @param volume mounted FAT filesystem
	 * @throws Exception if the permission probe cannot run
	 */
	private static void assertRestrictiveFatReadback(Path volume) throws Exception {
		Path probe = Files.createTempFile(volume, "icsforms-mode-", ".probe");
		try {
			Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("rw-------"));
			var actual = Files.getPosixFilePermissions(probe);
			System.out.println("Restrictive FAT file mode after chmod 0600: "
					+ PosixFilePermissions.toString(actual));
			assertEquals(PosixFilePermissions.fromString("rwx------"), actual);
		} finally {
			Files.deleteIfExists(probe);
		}
	}

	/** Separate JVM that reopens the journal after the writer closes. */
	public static final class RestartReader {
		/**
		 * Verifies the pending edit from a fresh JVM.
		 *
		 * @param args preferred and fallback roots, authority fingerprint and incident ID
		 */
		public static void main(String[] args) {
			try (RemoteIncidentStore reopened = RemoteIncidentStore.openOffline(
					List.of(Path.of(args[0]), Path.of(args[1])), args[2], new IncidentId(args[3]))) {
				if (!"offline filesystem edit".equals(
						reopened.loadSnapshot(new IncidentId(args[3])).data().getIncidentContext().getIncidentName())
						|| reopened.pendingOperations().size() != 1) {
					throw new IllegalStateException("Offline edit or pending operation was not recovered");
				}
				System.out.println("RESTART VERIFIED");
			}
		}
	}

	/**
	 * Rejects a test accidentally pointed at the runner's ordinary filesystem.
	 *
	 * @param scenario workflow case name
	 * @param type provider-reported filesystem type
	 */
	private static void assertFilesystem(String scenario, String type) {
		switch (scenario) {
		case "linux-fat-default", "linux-fat-owner" -> assertTrue(type.equals("vfat"), type);
		case "linux-ntfs" -> assertTrue(type.equals("fuseblk") || type.equals("ntfs"), type);
		case "linux-nfs" -> assertTrue(type.startsWith("nfs"), type);
		case "windows-ntfs" -> assertTrue(type.contains("ntfs"), type);
		case "windows-exfat", "macos-exfat" -> assertTrue(type.contains("exfat"), type);
		case "macos-apfs" -> assertTrue(type.contains("apfs"), type);
		default -> throw new IllegalArgumentException("Unknown filesystem case: " + scenario);
		}
	}

	/**
	 * Checks the persisted permissions rather than merely the requested permissions.
	 *
	 * @param path journal path to inspect
	 * @param directoryPath whether this is a directory
	 * @throws Exception if attributes cannot be read
	 */
	private static void assertPrivate(Path path, boolean directoryPath) throws Exception {
		if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
			assertEquals(PosixFilePermissions.fromString(directoryPath ? "rwx------" : "rw-------"),
					Files.getPosixFilePermissions(path), path.toString());
		} else {
			AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
			var acl = view.getAcl();
			assertEquals(1, acl.size(), path.toString());
			assertEquals(AclEntryType.ALLOW, acl.get(0).type());
			assertEquals(view.getOwner(), acl.get(0).principal());
		}
	}
}
