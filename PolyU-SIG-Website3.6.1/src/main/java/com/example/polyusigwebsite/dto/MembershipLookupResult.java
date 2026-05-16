package com.example.polyusigwebsite.dto;

import java.time.LocalDateTime;
import java.util.List;

/** One user matched by username / student-id search, with SIG group memberships. */
public record MembershipLookupResult(
        Long userId,
        String username,
        String email,
        String role,
        String status,
        /** Site access tier label for admins (Member = Level 1 / 2). */
        String siteAccessLevel,
        /** L1 / L2 when {@code role == MEMBER}; otherwise null. */
        String memberSiteTier,
        LocalDateTime createdAt,
        List<MemberGroupRow> memberships
) {}
