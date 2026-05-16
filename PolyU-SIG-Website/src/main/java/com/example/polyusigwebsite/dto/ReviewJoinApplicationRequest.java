package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.MemberSiteTier;
import jakarta.validation.constraints.NotNull;

public record ReviewJoinApplicationRequest(
        @NotNull Boolean approved,
        /**
         * When approving: site tier to set for the applicant. Students promoted to {@link com.example.polyusigwebsite.entity.RoleType#MEMBER}
         * and existing {@link com.example.polyusigwebsite.entity.RoleType#MEMBER} accounts are updated. Defaults to {@link MemberSiteTier#L1}
         * if omitted. Ignored if rejected; ignored for {@link com.example.polyusigwebsite.entity.RoleType#ADMIN} accounts.
         */
        MemberSiteTier memberSiteTier
) {}
