package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.JoinApplication;

import java.time.LocalDateTime;

public record JoinApplicationResponse(
        Long id,
        InterestGroupResponse interestGroup,
        String message,
        String status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        String reviewedBy,
        String applicantUsername
) {
    public static JoinApplicationResponse from(JoinApplication app) {
        return new JoinApplicationResponse(
                app.getId(),
                InterestGroupResponse.from(app.getInterestGroup()),
                app.getMessage(),
                app.getStatus().name(),
                app.getCreatedAt(),
                app.getReviewedAt(),
                app.getReviewedBy(),
                app.getUser().getUsername()
        );
    }
}
