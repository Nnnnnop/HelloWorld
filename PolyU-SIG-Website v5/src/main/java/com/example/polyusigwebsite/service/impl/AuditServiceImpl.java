package com.example.polyusigwebsite.service.impl;

import com.example.polyusigwebsite.entity.AuditAction;
import com.example.polyusigwebsite.entity.AuditLog;
import com.example.polyusigwebsite.repository.AuditLogRepository;
import com.example.polyusigwebsite.service.AuditService;
import org.springframework.stereotype.Service;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void record(AuditAction action, String actor, String message) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setActor(actor == null || actor.isBlank() ? "anonymous" : actor);
        log.setMessage(message);
        auditLogRepository.save(log);
    }
}
