package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.UploadSessionStatus;
import com.example.polyusigwebsite.entity.UploadTaskStatus;

import java.time.LocalDateTime;
import java.util.List;

public record UploadSessionResponse(
        String sessionId,
        UploadSessionStatus status,
        Long totalFiles,
        Long uploadedFiles,
        Long failedFiles,
        Long totalBytes,
        Long uploadedBytes,
        Integer progressPercentage,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expiresAt,
        List<UploadTaskResponse> tasks
) {
}
