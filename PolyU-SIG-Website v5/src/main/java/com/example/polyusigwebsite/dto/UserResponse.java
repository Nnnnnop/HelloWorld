package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.UserAccount;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        String provider,
        LocalDateTime createdAt
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getProvider(),
                user.getCreatedAt()
        );
    }
}
