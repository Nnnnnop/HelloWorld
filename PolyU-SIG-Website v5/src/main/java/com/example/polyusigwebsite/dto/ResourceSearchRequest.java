package com.example.polyusigwebsite.dto;

public record ResourceSearchRequest(
        String keyword,
        String fileType,
        String category,
        String uploader
) {
}
