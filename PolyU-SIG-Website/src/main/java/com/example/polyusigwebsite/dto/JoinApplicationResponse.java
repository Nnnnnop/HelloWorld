package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.JoinApplication;
import com.example.polyusigwebsite.entity.MemberSiteTier;
import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.UserAccount;

import java.time.LocalDateTime;

public record JoinApplicationResponse(
        Long id,
        InterestGroupResponse interestGroup,
        String message,
        String status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        String reviewedBy,
        String applicantUsername,
        /** Site role of the applicant (e.g. STUDENT, MEMBER, ADMIN). */
        String applicantRole,
        /**
         * Current {@link MemberSiteTier} name (L1 / L2) when the applicant is a Student or Member; null for Admin.
         * Used to prefill “tier if approve” with the same value as Interest groups / Find Members.
         */
        String applicantMemberSiteTier
) {
    public static JoinApplicationResponse from(JoinApplication app) {
        UserAccount user = app.getUser();
        RoleType role = user.getRole();
        String tierName = null;
        if (role == RoleType.MEMBER || role == RoleType.STUDENT) {
            MemberSiteTier t = user.getMemberSiteTier();
            tierName = (t != null ? t : MemberSiteTier.L1).name();
        }
        return new JoinApplicationResponse(
                app.getId(),
                InterestGroupResponse.from(app.getInterestGroup()),
                app.getMessage(),
                app.getStatus().name(),
                app.getCreatedAt(),
                app.getReviewedAt(),
                app.getReviewedBy(),
                user.getUsername(),
                role != null ? role.name() : null,
                tierName
        );
    }
}
