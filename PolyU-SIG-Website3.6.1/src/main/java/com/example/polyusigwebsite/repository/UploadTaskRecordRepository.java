package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.UploadTaskRecord;
import com.example.polyusigwebsite.entity.UploadTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UploadTaskRecordRepository extends JpaRepository<UploadTaskRecord, Long> {
    List<UploadTaskRecord> findBySessionIdAndStatus(Long sessionId, UploadTaskStatus status);

    List<UploadTaskRecord> findBySessionId(Long sessionId);

    List<UploadTaskRecord> findByStatusInAndRetryCountLessThan(List<UploadTaskStatus> statuses, Integer maxRetries);
}
