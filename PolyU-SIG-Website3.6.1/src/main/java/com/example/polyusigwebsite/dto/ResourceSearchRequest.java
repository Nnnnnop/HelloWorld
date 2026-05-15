package com.example.polyusigwebsite.dto;

import java.time.LocalDate;

public record ResourceSearchRequest(
        String keyword,
        String fileType,
        String category,
        String uploader,
        LocalDate uploadDateFrom,
        LocalDate uploadDateTo
) {
}
