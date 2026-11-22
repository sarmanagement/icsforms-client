/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Creates owner-only directories and files on POSIX file systems (mode bits) and on Windows-style file systems
 * (an explicit access control list that replaces any inherited entries). Storage fails closed, with an
 * {@link IOException}, on file systems offering neither mechanism.
 */
final class PrivateFiles {
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE);

	/** How owner-only access is enforced on a file system. */
	enum Mode {
		/** POSIX permission bits. */
		POSIX,
		/** Windows-style access control list. */
		ACL
	}

	/** The file system cannot enforce owner-only access. */
	static final class UnsecurableStorageException extends IOException {
		private static final long serialVersionUID = 1L;

		/**
		 * Creates the failure.
		 *
		 * @param message explanation
		 */
		UnsecurableStorageException(String message) {
			super(message);
		}
	}

	private static volatile java.util.function.Function<Path, FileStore> storeResolver = PrivateFiles::defaultStore;

	private PrivateFiles() {
	}

	/**
	 * Resolves the file store of a path.
	 *
	 * @param path existing path
	 * @return its file store
	 */
	private static FileStore defaultStore(Path path) {
		try {
			return Files.getFileStore(path);
		} catch (IOException failure) {
			throw new java.io.UncheckedIOException(failure);
		}
	}

	/**
	 * Substitutes file store resolution so that tests can simulate volumes such as FAT.
	 *
	 * @param resolver replacement resolver, or null to restore the default
	 */
	static void setStoreResolver(java.util.function.Function<Path, FileStore> resolver) {
		storeResolver = resolver == null ? PrivateFiles::defaultStore : resolver;
	}

	/**
	 * Resolves the file store for a path through the active resolver.
	 *
	 * @param path existing path
	 * @return its file store
	 * @throws IOException when the store cannot be resolved
	 */
	private static FileStore storeOf(Path path) throws IOException {
		try {
			return storeResolver.apply(path);
		} catch (java.io.UncheckedIOException failure) {
			throw failure.getCause();
		}
	}

	/**
	 * Reports whether the default file system creates files with POSIX permissions atomically.
	 *
	 * @return true on POSIX platforms
	 */
	private static boolean posixDefault() {
		return java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
	}

	/**
	 * Chooses the owner-only mechanism a file store supports.
	 *
	 * @param store file store holding the private storage
	 * @return the mechanism to use
	 * @throws IOException when the store supports neither POSIX permissions nor ACLs
	 */
	static Mode modeFor(FileStore store) throws IOException {
		if (store.supportsFileAttributeView("posix")) {
			return Mode.POSIX;
		}
		if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
			return Mode.ACL;
		}
		throw new UnsecurableStorageException("The file system cannot restrict storage to its owner");
	}

	/**
	 * Builds the sole access control entry granting the owner full control, inherited by children.
	 *
	 * @param owner current user
	 * @param directory whether the entry guards a directory, so that children inherit it
	 * @return one-entry list replacing every other entry
	 */
	static List<AclEntry> ownerOnlyAcl(UserPrincipal owner, boolean directory) {
		AclEntry.Builder builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
				.setPermissions(EnumSet.allOf(AclEntryPermission.class));
		if (directory) {
			builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
		}
		return List.of(builder.build());
	}

	/**
	 * Creates, if needed, and secures a directory.
	 *
	 * @param directory directory to create
	 * @throws IOException when the directory cannot be created or secured
	 */
	static void secureDirectory(Path directory) throws IOException {
		if (posixDefault()) {
			Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
		} else {
			Files.createDirectories(directory);
		}
		secure(directory, true);
	}

	/**
	 * Opens a file for writing, creating it if needed, then restricts it to the owner.
	 *
	 * @param file file to open
	 * @param options open options, without symbolic link following
	 * @return open channel
	 * @throws IOException when the file cannot be opened or secured
	 */
	static FileChannel openPrivate(Path file, Set<StandardOpenOption> options) throws IOException {
		Set<java.nio.file.OpenOption> all = new java.util.HashSet<>(options);
		all.add(LinkOption.NOFOLLOW_LINKS);
		FileChannel channel = posixDefault()
				? FileChannel.open(file, all, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
				: FileChannel.open(file, all);
		try {
			secure(file, false);
			return channel;
		} catch (IOException | RuntimeException failure) {
			channel.close();
			throw failure;
		}
	}

	/**
	 * Restricts an existing path to its owner.
	 *
	 * @param path existing file or directory
	 * @param directory whether the path is a directory
	 * @throws IOException when the restriction cannot be applied
	 */
	static void secure(Path path, boolean directory) throws IOException {
		FileStore store = storeOf(path);
		if (modeFor(store) == Mode.POSIX) {
			Set<PosixFilePermission> wanted = directory ? DIRECTORY_PERMISSIONS : FILE_PERMISSIONS;
			Files.setPosixFilePermissions(path, wanted);
			if (!Files.getPosixFilePermissions(path).equals(wanted)) {
				throw new UnsecurableStorageException("The file system ignores permission changes");
			}
			return;
		}
		AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS);
		UserPrincipal owner = view.getOwner();
		view.setAcl(ownerOnlyAcl(owner, directory));
	}

	/**
	 * Flushes directory metadata where the platform allows it. Windows does not permit opening a directory as a
	 * channel, and its atomic rename is already metadata-durable.
	 *
	 * @param directory directory to flush
	 * @throws IOException when a supported flush fails
	 */
	static void syncDirectory(Path directory) throws IOException {
		if (modeFor(storeOf(directory)) != Mode.POSIX) {
			return;
		}
		try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) {
			parent.force(true);
		}
	}
}
