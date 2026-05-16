package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.ResourceVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResourceUpdateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String category,
        String tags,
        @NotNull ResourceVisibility visibility,
        @NotNull Long folderId
) {
}
