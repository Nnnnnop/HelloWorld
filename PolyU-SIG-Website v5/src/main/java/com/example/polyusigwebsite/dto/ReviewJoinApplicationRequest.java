package com.example.polyusigwebsite.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewJoinApplicationRequest(
        @NotNull Boolean approved
) {}
