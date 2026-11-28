/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.host;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Creates and replaces sensitive host files so that only the owning operating system user can read them.
 */
final class OwnerOnlyFiles {
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");

	/**
	 * Prevents construction of this utility class.
	 */
	private OwnerOnlyFiles() {
	}

	/**
	 * Reports whether POSIX permissions are available on the default file system.
	 *
	 * @return true when owner-only permission bits can be applied
	 */
	static boolean posix() {
		return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
	}

	/**
	 * Creates a directory tree and restricts the final directory to its owner.
	 *
	 * @param directory directory to create
	 * @throws IOException when the directory cannot be created or restricted
	 */
	static void createDirectory(Path directory) throws IOException {
		Files.createDirectories(directory);
		restrict(directory, true);
	}

	/**
	 * Atomically replaces a file with owner-only content. The temporary file is created with restricted
	 * permissions so the content is never readable by other users.
	 *
	 * @param target destination file
	 * @param content bytes to store
	 * @throws IOException when the file cannot be written
	 */
	static void write(Path target, byte[] content) throws IOException {
		Path parent = target.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temporary = parent.resolve(target.getFileName() + ".tmp");
		Files.deleteIfExists(temporary);
		if (posix()) {
			Files.createFile(temporary, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
		} else {
			Files.createFile(temporary);
			restrict(temporary, false);
		}
		try {
			Files.write(temporary, content);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		restrict(target, false);
	}

	/**
	 * Applies owner-only permissions to an existing path, using POSIX bits where available and otherwise a single
	 * owner-only access control entry.
	 *
	 * @param path file or directory
	 * @param directory whether the path is a directory
	 * @throws IOException when permissions cannot be applied
	 */
	static void restrict(Path path, boolean directory) throws IOException {
		if (posix()) {
			Files.setPosixFilePermissions(path, directory ? DIRECTORY_PERMISSIONS : FILE_PERMISSIONS);
			return;
		}
		AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS);
		if (view == null) {
			throw new IOException("Cannot restrict file to its owner: " + path.getFileName());
		}
		AclEntry.Builder entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(view.getOwner())
				.setPermissions(EnumSet.allOf(AclEntryPermission.class));
		if (directory) {
			entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
		}
		view.setAcl(List.of(entry.build()));
	}
}
