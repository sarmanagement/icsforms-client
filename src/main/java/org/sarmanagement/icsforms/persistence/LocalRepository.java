package org.sarmanagement.icsforms.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sarmanagement.icsforms.model.AppData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalRepository {
    private final Path filePath;
    private final ObjectMapper objectMapper;

    public LocalRepository(Path filePath) {
        this.filePath = filePath;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void save(AppData appData) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), appData);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save local data", e);
        }
    }

    public AppData loadOrDefault() {
        if (!Files.exists(filePath)) {
            return new AppData();
        }
        try {
            return objectMapper.readValue(filePath.toFile(), AppData.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load local data", e);
        }
    }
}
