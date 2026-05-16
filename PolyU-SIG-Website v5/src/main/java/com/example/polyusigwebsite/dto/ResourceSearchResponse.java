package com.example.polyusigwebsite.dto;

import java.util.List;

public record ResourceSearchResponse(
        List<ResourceFileResponse> items,
        String suggestion
) {
}
