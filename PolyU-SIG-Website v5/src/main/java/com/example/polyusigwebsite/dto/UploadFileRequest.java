package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.ResourceVisibility;

public record UploadFileRequest(
        String sessionId,
        String clientPath,
        String displayName,
        Long folderId,
        String category,
        String description,
        String tags,
        ResourceVisibility visibility
) {
}
