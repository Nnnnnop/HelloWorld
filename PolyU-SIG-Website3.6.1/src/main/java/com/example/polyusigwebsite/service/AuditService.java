package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.entity.AuditAction;

public interface AuditService {
    void record(AuditAction action, String actor, String message);
}
