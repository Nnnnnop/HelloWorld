package com.example.polyusigwebsite.dto;

/**
 * One entry inside a ZIP archive (name, size, directory flag).
 */
public record ArchiveEntryResponse(
        String path,
        long uncompressedSize,
        boolean directory
) {}
