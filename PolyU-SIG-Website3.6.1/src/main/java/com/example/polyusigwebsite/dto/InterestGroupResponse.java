package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.InterestGroup;

import java.time.LocalDateTime;

public record InterestGroupResponse(
        Long id,
        String name,
        String description,
        boolean recruiting,
        boolean active,
        LocalDateTime createdAt
) {
    public static InterestGroupResponse from(InterestGroup g) {
        return new InterestGroupResponse(
                g.getId(),
                g.getName(),
                g.getDescription(),
                g.isRecruiting(),
                g.isActive(),
                g.getCreatedAt()
        );
    }
}
