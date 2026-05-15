package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.UploadSession;
import com.example.polyusigwebsite.entity.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {
    Optional<UploadSession> findBySessionId(String sessionId);

    List<UploadSession> findByStatusAndExpiresAtBefore(UploadSessionStatus status, LocalDateTime expiresAt);

    List<UploadSession> findByUploaderAndStatus(String uploader, UploadSessionStatus status);

    List<UploadSession> findByStatus(UploadSessionStatus status);
}
