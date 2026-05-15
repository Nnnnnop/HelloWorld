package com.example.polyusigwebsite.dto;

public record InitializeUploadRequest(
        String sessionId,
        int totalFiles,
        long totalBytes
) {
}
