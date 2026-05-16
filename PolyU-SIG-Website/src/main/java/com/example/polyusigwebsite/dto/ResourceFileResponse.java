package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.ResourceFile;

import java.time.LocalDateTime;
import java.util.List;

public record ResourceFileResponse(
        Long id,
        String title,
        String fileName,
        String fileType,
        Long fileSize,
        String description,
        String category,
        List<String> tags,
        String visibility,
        String uploader,
        LocalDateTime uploadTime,
        Long folderId,
        String folderName,
        String highlight,
        String previewContent
) {
    public static ResourceFileResponse from(ResourceFile resourceFile, String highlight, String previewContent) {
        return new ResourceFileResponse(
                resourceFile.getId(),
                resourceFile.getTitle(),
                resourceFile.getFileName(),
                resourceFile.getFileType(),
                resourceFile.getFileSize(),
                resourceFile.getDescription(),
                resourceFile.getCategory(),
                parseTags(resourceFile.getTags()),
                resourceFile.getVisibility().name(),
                resourceFile.getUploader().getUsername(),
                resourceFile.getUploadTime(),
                resourceFile.getFolder().getId(),
                resourceFile.getFolder().getName(),
                highlight,
                previewContent
        );
    }

    public static ResourceFileResponse from(ResourceFile resourceFile, String highlight) {
        return from(resourceFile, highlight, null);
    }

    public static ResourceFileResponse from(ResourceFile resourceFile) {
        return from(resourceFile, null, null);
    }

    private static List<String> parseTags(String tagsValue) {
        if (tagsValue == null || tagsValue.isBlank()) {
            return List.of();
        }
        return List.of(tagsValue.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
