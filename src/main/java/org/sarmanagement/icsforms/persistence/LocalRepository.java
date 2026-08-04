package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JSON-backed local persistence for the first-cut desktop workspace.
 * Saves under {@code ~/.icsforms/incident.json} by default and maintains a last-known-good backup.
 */
public class LocalRepository {
    private static final String DIRECTORY_NAME = ".icsforms";
    private static final String FILE_NAME = "incident.json";
    private static final String BACKUP_FILE_NAME = "incident.json.bak";

    private final Path filePath;
    private final Path backupPath;
    private final ObjectMapper objectMapper;

    /**
     * Creates a repository using the default workspace path in the user's home directory.
     */
    public LocalRepository() {
        this(defaultWorkspaceFile());
    }

    /**
     * Creates a repository for an explicit workspace file path.
     *
     * @param filePath target JSON file.
     */
    public LocalRepository(Path filePath) {
        this.filePath = filePath;
        this.backupPath = filePath.resolveSibling(BACKUP_FILE_NAME);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Returns the default workspace file path.
     *
     * @return default workspace file path.
     */
    public static Path defaultWorkspaceFile() {
        return Path.of(System.getProperty("user.home"), DIRECTORY_NAME, FILE_NAME);
    }

    /**
     * Saves an incident document and refreshes the last-known-good backup before overwrite.
     *
     * @param appData incident document to save.
     * @throws IllegalStateException when the workspace cannot be written.
     */
    public void save(AppData appData) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(filePath)) {
                Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), appData);
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save local data", e);
        }
    }

    /**
     * Loads the current workspace or an empty document if none exists.
     * Falls back to backup content when the primary file is corrupt.
     *
     * @return loaded or default incident document.
     * @throws IllegalStateException when both primary and backup files are unreadable.
     */
    public AppData loadOrDefault() {
        if (!Files.exists(filePath)) {
            return new AppData();
        }
        try {
            return objectMapper.readValue(filePath.toFile(), AppData.class);
        } catch (IOException primaryFailure) {
            if (Files.exists(backupPath)) {
                try {
                    return objectMapper.readValue(backupPath.toFile(), AppData.class);
                } catch (IOException backupFailure) {
                    throw new IllegalStateException("Failed to load local data or backup", backupFailure);
                }
            }
            return new AppData();
        }
    }

    /**
     * Returns the active workspace file path.
     *
     * @return workspace file path.
     */
    public Path getFilePath() {
        return filePath;
    }
}
