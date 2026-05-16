package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public record SitePostRequest(
        @NotBlank String title,
        @NotBlank String content,
        String summary,
        LocalDateTime eventStartAt,
        LocalDateTime eventEndAt,
        String organizer,
        String eventTimeLabel,
        String venue,
        String eventCategory,
        LocalDate newsDate,
        List<Long> newsImageIds,
        @NotNull PostType type,
        boolean published,
        boolean pinned
) {
}
