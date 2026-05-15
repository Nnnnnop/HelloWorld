package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.UploadTaskStatus;

public record UploadTaskResponse(
        Long id,
        String clientPath,
        String displayName,
        UploadTaskStatus status,
        Long fileSize,
        Long uploadedBytes,
        Integer progressPercentage,
        Long resourceFileId,
        String errorMessage,
        Integer retryCount
) {
}
