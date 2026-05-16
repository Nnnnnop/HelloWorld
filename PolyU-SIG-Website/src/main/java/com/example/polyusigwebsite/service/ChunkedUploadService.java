package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.entity.UploadTaskRecord;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ChunkedUploadService {
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);

    @Value("${sig.upload.chunk-size:5242880}")
    private long chunkSize; // 5MB default

    @Value("${sig.upload.temp-dir:temp-uploads}")
    private String tempUploadDir;

    @PostConstruct
    public void initializeUploadDirectory() throws IOException {
        Files.createDirectories(Path.of(tempUploadDir));
    }

    /**
     * Upload a file in chunks and calculate hash for deduplication
     */
    public UploadChunkResult uploadFileInChunks(
            MultipartFile file,
            UploadTaskRecord task,
            ChunkProgressCallback progressCallback
    ) throws IOException {
        try {
            log.debug("Starting chunked upload for file {} ({} bytes)", file.getOriginalFilename(), file.getSize());
            log.debug("Temp upload directory: {}", tempUploadDir);

            Path tempFile = Path.of(tempUploadDir, task.getSession().getSessionId() + "_" + task.getId() + ".tmp");
            log.debug("Temp file path: {}", tempFile);
            
            String fileHash = null;

            try (InputStream inputStream = file.getInputStream()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long uploadedBytes = 0;
                byte[] buffer = new byte[(int) Math.min(chunkSize, 1024 * 1024)]; // Read 1MB at a time
                int bytesRead;

                // Write file to temp location and calculate hash
                Files.deleteIfExists(tempFile);
                Files.createFile(tempFile);

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    try (var outputStream = Files.newOutputStream(tempFile, StandardOpenOption.APPEND)) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    digest.update(buffer, 0, bytesRead);
                    uploadedBytes += bytesRead;

                    // Update progress
                    if (progressCallback != null) {
                        int progressPercent = (int) ((uploadedBytes * 100) / file.getSize());
                        progressCallback.onProgress(uploadedBytes, file.getSize(), progressPercent);
                    }
                }

                // Generate file hash
                byte[] hashBytes = digest.digest();
                fileHash = HexFormat.of().formatHex(hashBytes);

                log.debug("Completed chunked upload for file {} with hash {}", file.getOriginalFilename(), fileHash);

                return new UploadChunkResult(tempFile, fileHash, file.getSize());

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 algorithm not available", e);
            } catch (Exception e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
        } catch (IOException e) {
            log.error("IO error during chunked upload of file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during chunked upload of file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new IOException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate hash for an existing file (for deduplication)
     */
    public String calculateFileHash(Path filePath) throws IOException {
        log.debug("Calculating hash for file {}", filePath);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
            int bytesRead;

            try (InputStream inputStream = Files.newInputStream(filePath)) {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            byte[] hashBytes = digest.digest();
            String hash = HexFormat.of().formatHex(hashBytes);
            log.debug("File hash for {} is {}", filePath, hash);
            return hash;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Move temp file to final destination
     */
    public void moveToFinalLocation(Path tempFile, Path finalFile) throws IOException {
        if (!Files.exists(tempFile)) {
            throw new IllegalArgumentException("Temp file not found: " + tempFile);
        }

        Files.createDirectories(finalFile.getParent());
        Files.move(tempFile, finalFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.debug("Moved upload file from {} to {}", tempFile, finalFile);
    }

    /**
     * Clean up temp file
     */
    public void cleanupTempFile(Path tempFile) {
        try {
            if (Files.exists(tempFile)) {
                Files.delete(tempFile);
                log.debug("Cleaned up temp file {}", tempFile);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up temp file {}: {}", tempFile, e.getMessage());
        }
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public String getTempUploadDir() {
        return tempUploadDir;
    }

    public static class UploadChunkResult {
        public final Path tempFile;
        public final String fileHash;
        public final long fileSize;

        public UploadChunkResult(Path tempFile, String fileHash, long fileSize) {
            this.tempFile = tempFile;
            this.fileHash = fileHash;
            this.fileSize = fileSize;
        }
    }

    @FunctionalInterface
    public interface ChunkProgressCallback {
        void onProgress(long uploadedBytes, long totalBytes, int progressPercent);
    }
}
