package com.example.polyusigwebsite.dto;

import jakarta.validation.constraints.NotNull;

public record ApprovalRequest(
        @NotNull Long userId,
        @NotNull Boolean approved
) {
}
