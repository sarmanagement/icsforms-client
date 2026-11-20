/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Per-incident JSON record history stored alongside managed incident files.
 */
public final class FileRecordVersionStore implements RecordVersionStore {
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	private final Path incidentsDirectory;

	/**
	 * Creates a file-backed history store.
	 *
	 * @param incidentsDirectory managed incidents directory
	 */
	public FileRecordVersionStore(Path incidentsDirectory) {
		this.incidentsDirectory = Objects.requireNonNull(incidentsDirectory, "incidentsDirectory").toAbsolutePath();
	}

	/** {@inheritDoc} */
	@Override
	public synchronized IncidentState load(IncidentId incident) {
		Path state = path(incident);
		Path backup = backup(state);
		if (!Files.exists(state) && !Files.exists(backup)) {
			return null;
		}
		Path source = Files.exists(state) ? state : backup;
		try {
			return MAPPER.readValue(source.toFile(), IncidentState.class);
		} catch (IOException primaryFailure) {
			if (source.equals(state) && Files.exists(backup)) {
				try {
					return MAPPER.readValue(backup.toFile(), IncidentState.class);
				} catch (IOException backupFailure) {
					primaryFailure.addSuppressed(backupFailure);
				}
			}
			throw new StorePersistenceException("Cannot read record history: " + incident, primaryFailure);
		}
	}

	/** {@inheritDoc} */
	@Override
	public synchronized void save(IncidentId incident, IncidentState state) {
		Objects.requireNonNull(state, "state");
		Path destination = path(incident);
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		try {
			Files.createDirectories(incidentsDirectory);
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
			if (readable(destination)) {
				Files.copy(destination, backup(destination), StandardCopyOption.REPLACE_EXISTING);
			}

			try {
				Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new StorePersistenceException("Cannot save record history: " + incident, exception);
		} finally {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException exception) {
				throw new StorePersistenceException("Cannot remove temporary record history: " + incident, exception);
			}
		}
	}

	/**
	 * Checks whether a current state file is valid before replacing its backup.
	 *
	 * @param state candidate state file
	 * @return true when it contains a valid record state
	 */
	private boolean readable(Path state) {
		if (!Files.exists(state)) {
			return false;
		}
		try {
			MAPPER.readValue(state.toFile(), IncidentState.class);
			return true;
		} catch (IOException exception) {
			return false;
		}
	}

	/**
	 * Resolves the managed history path for an incident.
	 *
	 * @param incident incident identity
	 * @return managed state file
	 */
	private Path path(IncidentId incident) {
		return incidentsDirectory.resolve(incident.value() + ".records.json");
	}

	/**
	 * Resolves the recovery backup path for a state file.
	 *
	 * @param state state file path
	 * @return backup file path
	 */
	private Path backup(Path state) {
		return state.resolveSibling(state.getFileName() + ".bak");
	}
}
