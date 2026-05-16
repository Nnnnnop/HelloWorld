package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.AuditLogResponse;
import com.example.polyusigwebsite.repository.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogRepository auditLogRepository;

    public AdminController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> auditLogs() {
        return ResponseEntity.ok(
                auditLogRepository.findAllByOrderByCreatedAtDesc()
                        .stream()
                        .map(AuditLogResponse::from)
                        .toList()
        );
    }
}
