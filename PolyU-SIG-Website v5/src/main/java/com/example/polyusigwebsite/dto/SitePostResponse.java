package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.SitePost;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SitePostResponse(
        Long id,
        String displayId,
        String title,
        String content,
        String summary,
        LocalDateTime eventStartAt,
        LocalDateTime eventEndAt,
        String organizer,
        String eventTimeLabel,
        String venue,
        String eventCategory,
        LocalDate newsDate,
        List<Long> newsImageIds,
        String type,
        boolean published,
        boolean pinned,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SitePostResponse from(SitePost post) {
        return new SitePostResponse(
                post.getId(),
                buildDisplayId(post),
                post.getTitle(),
                post.getContent(),
                post.getSummary(),
                post.getEventStartAt(),
                post.getEventEndAt(),
                post.getOrganizer(),
                post.getEventTimeLabel(),
                post.getVenue(),
                post.getEventCategory(),
                post.getNewsDate(),
                parseNewsImageIds(post.getNewsImageIds()),
                post.getType().name(),
                post.isPublished(),
                post.isPinned(),
                post.getAuthor().getUsername(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private static String buildDisplayId(SitePost post) {
        int sequence = post.getTypeSequence() == null ? 0 : post.getTypeSequence();
        String prefix = switch (post.getType()) {
            case NEWS -> "N";
            case EVENT -> "E";
            case ANNOUNCEMENT -> "A";
        };
        return prefix + sequence;
    }

    private static List<Long> parseNewsImageIds(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(id -> id != null && id > 0)
                .toList();
    }
}
