package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.MemberSiteTier;
import jakarta.validation.constraints.NotNull;

public record AdminMemberTierRequest(
        @NotNull Long userId,
        @NotNull MemberSiteTier memberSiteTier
) {}
