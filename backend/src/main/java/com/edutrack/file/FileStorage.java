package com.edutrack.file;

import java.io.IOException;
import java.util.UUID;

/** Storage boundary; an S3-compatible implementation can replace local storage. */
public interface FileStorage {
    void put(UUID key, byte[] bytes) throws IOException;
    byte[] get(UUID key) throws IOException;
    void delete(UUID key) throws IOException;
}
