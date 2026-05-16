package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.RoleType;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull Long userId,
        @NotNull RoleType role
) {
}
