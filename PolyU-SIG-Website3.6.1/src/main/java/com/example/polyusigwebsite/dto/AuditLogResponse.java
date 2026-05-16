package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String action,
        String actor,
        String message,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction().name(), log.getActor(), log.getMessage(), log.getCreatedAt());
    }
}
