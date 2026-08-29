package io.github.dicur3x.lss.components;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class ManagedComponentStore {
    private final Path componentsDirectory;
    private final ObjectMapper objectMapper;

    public ManagedComponentStore(Path componentsDirectory, ObjectMapper objectMapper) {
        this.componentsDirectory = Objects.requireNonNull(componentsDirectory, "componentsDirectory")
                .toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Path componentDirectory(ManagedComponent component) {
        return componentsDirectory.resolve(component.id());
    }

    public Optional<InstalledComponent> current(ManagedComponent component) throws ComponentException {
        Path metadata = componentDirectory(component).resolve("current.json");
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        try {
            InstalledComponent installed = objectMapper.readValue(metadata.toFile(), InstalledComponent.class);
            if (installed.component() != component
                    || !Files.isRegularFile(installed.primaryExecutablePath())) {
                return Optional.empty();
            }
            if (component == ManagedComponent.FFMPEG
                    && !Files.isRegularFile(installed.secondaryExecutablePath())) {
                return Optional.empty();
            }
            return Optional.of(installed);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ComponentException("Could not read the managed " + component.displayName()
                    + " installation metadata.", exception);
        }
    }

    void activate(InstalledComponent installed, Path installationDirectory) throws ComponentException {
        try {
            ComponentFileOperations.writeJsonAtomically(
                    objectMapper, installationDirectory.resolve("installation.json"), installed);
            ComponentFileOperations.writeJsonAtomically(
                    objectMapper, componentDirectory(installed.component()).resolve("current.json"), installed);
        } catch (IOException exception) {
            throw new ComponentException("Could not save managed component metadata.", exception);
        }
    }
}
