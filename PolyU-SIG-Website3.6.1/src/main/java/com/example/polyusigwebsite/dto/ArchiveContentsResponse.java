package com.example.polyusigwebsite.dto;

import java.util.List;

public record ArchiveContentsResponse(
        List<ArchiveEntryResponse> entries,
        /** True if more entries exist than returned (capped for safety). */
        boolean truncated,
        int totalListed
) {}
