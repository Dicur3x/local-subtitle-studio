package io.github.dicur3x.lss.components;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

final class ComponentFileOperations {
    private ComponentFileOperations() {
    }

    static void writeJsonAtomically(ObjectMapper objectMapper, Path destination, Object value) throws IOException {
        Path normalized = destination.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temporary = Files.createTempFile(normalized.getParent(), ".metadata-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            moveReplacing(temporary, normalized);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void deleteTree(Path target, Path requiredParent) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedParent = requiredParent.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedParent) || normalizedTarget.equals(normalizedParent)) {
            throw new IOException("Refusing to remove a path outside the managed component directory");
        }
        if (!Files.exists(normalizedTarget)) {
            return;
        }
        try (var paths = Files.walk(normalizedTarget)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
