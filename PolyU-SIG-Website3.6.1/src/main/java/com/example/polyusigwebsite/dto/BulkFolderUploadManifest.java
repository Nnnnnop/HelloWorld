package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.ResourceVisibility;

import java.util.List;

public record BulkFolderUploadManifest(
        List<FolderNode> folders,
        List<FileNode> files
) {
    public record FolderNode(
            String tempId,
            String parentTempId,
            String name
    ) {
    }

    public record FileNode(
            String folderTempId,
            String clientPath,
            String displayName,
            ResourceVisibility minAccessLevel,
            String category,
            String description,
            String tags
    ) {
    }
}