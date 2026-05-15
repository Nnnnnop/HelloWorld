package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.MemberSiteTier;
import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.entity.UserGroupMembership;

import java.time.LocalDateTime;

public record GroupMemberAdminResponse(
        Long membershipId,
        Long userId,
        String username,
        String email,
        String role,
        /** L1/L2 only when {@link RoleType#MEMBER}; null for other roles. */
        String memberSiteTier,
        String siteAccessLevel,
        String status,
        LocalDateTime joinedAt
) {
    public static GroupMemberAdminResponse from(UserGroupMembership m) {
        UserAccount u = m.getUser();
        String tierLabel = tierCodeForRole(u);
        return new GroupMemberAdminResponse(
                m.getId(),
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getRole().name(),
                tierLabel,
                u.getSiteAccessLevelLabel(),
                u.getStatus().name(),
                m.getCreatedAt()
        );
    }

    /** Enum name L1/L2 for UI; MEMBER tier defaults when column null. */
    private static String tierCodeForRole(UserAccount u) {
        if (u.getRole() != RoleType.MEMBER) {
            return null;
        }
        MemberSiteTier t = u.getMemberSiteTier() != null ? u.getMemberSiteTier() : MemberSiteTier.L1;
        return t.name();
    }
}
