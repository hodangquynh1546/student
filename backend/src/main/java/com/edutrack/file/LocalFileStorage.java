package com.edutrack.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {
    private final Path root;

    public LocalFileStorage(@Value("${edutrack.file.local-path:./uploads}") String configured) throws IOException {
        root = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public void put(UUID key, byte[] bytes) throws IOException {
        Files.write(path(key), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    public byte[] get(UUID key) throws IOException { return Files.readAllBytes(path(key)); }
    public void delete(UUID key) throws IOException { Files.deleteIfExists(path(key)); }
    private Path path(UUID key) { return root.resolve(key.toString()); }
}
