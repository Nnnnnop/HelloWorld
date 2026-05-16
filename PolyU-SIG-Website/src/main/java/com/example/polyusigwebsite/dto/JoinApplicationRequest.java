package com.example.polyusigwebsite.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoinApplicationRequest(
        @NotNull Long groupId,
        @Size(max = 2000) String message
) {}
