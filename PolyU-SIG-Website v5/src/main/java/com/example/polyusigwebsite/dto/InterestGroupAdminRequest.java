package com.example.polyusigwebsite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterestGroupAdminRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 4000) String description,
        boolean recruiting,
        boolean active,
        int sortOrder
) {}
