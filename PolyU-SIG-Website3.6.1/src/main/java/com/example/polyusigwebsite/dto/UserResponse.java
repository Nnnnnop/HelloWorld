package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.MemberSiteTier;
import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.UserAccount;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        String provider,
        LocalDateTime createdAt,
        /** L1 / L2 when role is MEMBER; null otherwise (used for Level 2 resource access). */
        String memberSiteTier
) {
    public static UserResponse from(UserAccount user) {
        String tierName = user.getRole() == RoleType.MEMBER
                ? (user.getMemberSiteTier() != null ? user.getMemberSiteTier().name() : MemberSiteTier.L1.name())
                : null;
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getProvider(),
                user.getCreatedAt(),
                tierName
        );
    }
}
