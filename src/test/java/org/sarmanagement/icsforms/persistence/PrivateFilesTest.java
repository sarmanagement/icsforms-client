/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests owner-only storage on POSIX and Windows-style file systems. */
class PrivateFilesTest {
	@TempDir
	Path directory;

	/** File store reporting a fixed set of attribute views. */
	static final class FakeStore extends FileStore {
		private final boolean posix;
		private final boolean acl;

		/**
		 * Creates a store.
		 *
		 * @param posix whether POSIX permissions are reported
		 * @param acl whether ACLs are reported
		 */
		FakeStore(boolean posix, boolean acl) {
			this.posix = posix;
			this.acl = acl;
		}

		/** {@inheritDoc} */
		@Override
		public String name() {
			return "fake";
		}

		/** {@inheritDoc} */
		@Override
		public String type() {
			return "fake";
		}

		/** {@inheritDoc} */
		@Override
		public boolean isReadOnly() {
			return false;
		}

		/** {@inheritDoc} */
		@Override
		public long getTotalSpace() {
			return 0;
		}

		/** {@inheritDoc} */
		@Override
		public long getUsableSpace() {
			return 0;
		}

		/** {@inheritDoc} */
		@Override
		public long getUnallocatedSpace() {
			return 0;
		}

		/** {@inheritDoc} */
		@Override
		public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
			return acl && type == AclFileAttributeView.class;
		}

		/** {@inheritDoc} */
		@Override
		public boolean supportsFileAttributeView(String name) {
			return posix && name.equals("posix") || acl && name.equals("acl");
		}

		/** {@inheritDoc} */
		@Override
		public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
			return null;
		}

		/** {@inheritDoc} */
		@Override
		public Object getAttribute(String attribute) throws IOException {
			throw new IOException("unsupported");
		}
	}

	@Test
	void windowsStyleStoreUsesAclAndPosixStoreUsesModeBits() throws Exception {
		assertEquals(PrivateFiles.Mode.ACL, PrivateFiles.modeFor(new FakeStore(false, true)));
		assertEquals(PrivateFiles.Mode.POSIX, PrivateFiles.modeFor(new FakeStore(true, true)));
	}

	@Test
	void storeWithoutOwnerRestrictionFailsClosed() {
		assertThrows(IOException.class, () -> PrivateFiles.modeFor(new FakeStore(false, false)));
	}

	@Test
	void journalOnUnsecurableVolumeRefusesWithAnOperatorFacingNotice() {
		PrivateFiles.setStoreResolver(path -> new FakeStore(false, false));
		try {
			var failure = assertThrows(StorageNotSecurableException.class, () -> new DisconnectedStateFile(
					directory.resolve("fat"), "a".repeat(64), new IncidentId(java.util.UUID.randomUUID().toString())));
			assertTrue(failure.getMessage().contains("FAT"));
			try (var files = Files.walk(directory.resolve("fat"))) {
				assertFalse(files.anyMatch(Files::isRegularFile));
			} catch (java.io.IOException unexpected) {
				throw new java.io.UncheckedIOException(unexpected);
			}
		} finally {
			PrivateFiles.setStoreResolver(null);
		}
	}

	@Test
	void ownerAclIsASingleFullControlEntryAndDirectoriesPropagateIt() throws Exception {
		UserPrincipal owner = directory.getFileSystem().getUserPrincipalLookupService()
				.lookupPrincipalByName(System.getProperty("user.name"));
		List<AclEntry> file = PrivateFiles.ownerOnlyAcl(owner, false);
		assertEquals(1, file.size());
		assertEquals(AclEntryType.ALLOW, file.get(0).type());
		assertEquals(owner, file.get(0).principal());
		assertTrue(file.get(0).permissions().containsAll(Set.of(AclEntryPermission.values())));
		assertTrue(file.get(0).flags().isEmpty());
		Set<AclEntryFlag> inherited = PrivateFiles.ownerOnlyAcl(owner, true).get(0).flags();
		assertEquals(Set.of(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT), inherited);
	}

	@Test
	void secureDirectoryAndFileAreOwnerOnlyOnPosix() throws Exception {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		Path nested = directory.resolve("a/b");
		PrivateFiles.secureDirectory(nested);
		assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(nested));
		Path file = nested.resolve("f");
		try (var channel = PrivateFiles.openPrivate(file,
				Set.of(java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE))) {
			assertTrue(channel.isOpen());
		}
		assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
		PrivateFiles.syncDirectory(nested);
		assertFalse(Files.isSymbolicLink(file));
	}
}
