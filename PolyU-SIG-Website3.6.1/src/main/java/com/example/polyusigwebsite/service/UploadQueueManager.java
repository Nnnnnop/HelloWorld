package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.entity.UploadSession;
import com.example.polyusigwebsite.entity.UploadSessionStatus;
import com.example.polyusigwebsite.entity.UploadTaskRecord;
import com.example.polyusigwebsite.entity.UploadTaskStatus;
import com.example.polyusigwebsite.entity.ResourceVisibility;
import com.example.polyusigwebsite.repository.UploadSessionRepository;
import com.example.polyusigwebsite.repository.UploadTaskRecordRepository;
import com.example.polyusigwebsite.service.ResourceFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class UploadQueueManager {
    private static final Logger log = LoggerFactory.getLogger(UploadQueueManager.class);

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadTaskRecordRepository uploadTaskRecordRepository;
    private final ResourceFileService resourceFileService;

    @Value("${sig.upload.max-concurrent:5}")
    private int maxConcurrentUploads;

    @Value("${sig.upload.chunk-size:5242880}")
    private long chunkSize; // 5MB default

    @Value("${sig.upload.max-retries:3}")
    private int maxRetries;

    // In-memory queues per session
    private final Map<String, BlockingQueue<UploadTaskRecord>> sessionQueues = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeUploadsPerSession = new ConcurrentHashMap<>();
    private final ExecutorService uploadExecutor;

    public UploadQueueManager(
            UploadSessionRepository uploadSessionRepository,
            UploadTaskRecordRepository uploadTaskRecordRepository,
            @Lazy ResourceFileService resourceFileService
    ) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.uploadTaskRecordRepository = uploadTaskRecordRepository;
        this.resourceFileService = resourceFileService;
        this.uploadExecutor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    @Transactional
    public UploadSession createSession(String sessionId, String uploader, long totalFiles, long totalBytes) {
        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setUploader(uploader);
        session.setTotalFiles(totalFiles);
        session.setTotalBytes(totalBytes);
        session.setStatus(UploadSessionStatus.IN_PROGRESS);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));

        UploadSession saved = uploadSessionRepository.save(session);
        sessionQueues.put(sessionId, new LinkedBlockingQueue<>());
        activeUploadsPerSession.put(sessionId, 0);

        log.info("Created upload session {} for user {} with {} files ({} bytes)",
                sessionId, uploader, totalFiles, totalBytes);

        return saved;
    }

    @Transactional
    public UploadTaskRecord createTask(UploadSession session, String clientPath, String displayName,
                                        Long folderId, Long fileSize, String category, String description,
                                        String tags, ResourceVisibility visibility) {
        UploadTaskRecord task = new UploadTaskRecord();
        task.setSession(session);
        task.setClientPath(clientPath);
        task.setDisplayName(displayName);
        task.setFolderId(folderId);
        task.setFileSize(fileSize);
        task.setCategory(category);
        task.setDescription(description);
        task.setTags(tags);
        task.setVisibility(visibility);
        task.setStatus(UploadTaskStatus.QUEUED);

        return uploadTaskRecordRepository.save(task);
    }

    @Transactional
    public void queueTask(UploadTaskRecord task, String sessionId) {
        try {
            BlockingQueue<UploadTaskRecord> queue = sessionQueues.computeIfAbsent(
                    sessionId,
                    k -> new LinkedBlockingQueue<>()
            );
            queue.put(task);
            log.debug("Queued task {} for session {}", task.getId(), sessionId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to queue upload task", e);
        }
    }

    @Async
    public void startProcessingSession(String sessionId) {
        log.info("Starting upload queue processing for session {}", sessionId);

        UploadSession session = uploadSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        BlockingQueue<UploadTaskRecord> queue = sessionQueues.get(sessionId);
        if (queue == null) {
            log.error("No queue found for session {}", sessionId);
            return;
        }

        log.info("Starting {} worker threads for session {}", maxConcurrentUploads, sessionId);
        // Start worker threads
        for (int i = 0; i < maxConcurrentUploads; i++) {
            uploadExecutor.submit(() -> processQueueWorker(sessionId, session));
            log.debug("Submitted worker thread #{} for session {}", i + 1, sessionId);
        }
    }

    private void processQueueWorker(String sessionId, UploadSession session) {
        BlockingQueue<UploadTaskRecord> queue = sessionQueues.get(sessionId);
        if (queue == null) {
            log.warn("Queue not found for session {}, worker exiting immediately", sessionId);
            return;
        }

        int idleCount = 0;
        final int MAX_IDLE_CYCLES = 4; // After 4 idle cycles (2 seconds), check if queue is really empty

        while (true) {
            try {
                // Try to get next task with timeout
                UploadTaskRecord task = queue.poll(500, TimeUnit.MILLISECONDS);

                if (task == null) {
                    idleCount++;
                    // Only query DB periodically, not every 500ms
                    if (idleCount >= MAX_IDLE_CYCLES) {
                        idleCount = 0;
                        // Check if session is still active (less frequent DB query)
                        UploadSession current = uploadSessionRepository.findBySessionId(sessionId).orElse(null);
                        if (current == null || current.getStatus() != UploadSessionStatus.IN_PROGRESS) {
                            log.info("Queue worker exiting: session {} status is {}", sessionId, 
                                    current == null ? "DELETED" : current.getStatus());
                            break;
                        }
                        // Verify queue still exists and has no tasks
                        if (!sessionQueues.containsKey(sessionId)) {
                            log.info("Queue removed for session {}, worker exiting", sessionId);
                            break;
                        }
                        // Queue can be empty while the last task was just finished on another worker;
                        // completion must be evaluated here so workers can exit (see checkSessionCompletion).
                        checkSessionCompletion(sessionId);
                        if (!sessionQueues.containsKey(sessionId)) {
                            log.info("Queue removed after completion check for session {}, worker exiting", sessionId);
                            break;
                        }
                    }
                    continue;
                }

                idleCount = 0;
                // Process single task
                processUploadTask(task, session);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Queue worker interrupted for session {}", sessionId);
                break;
            } catch (Exception e) {
                log.error("Error processing upload task in session {}: {}", sessionId, e.getMessage(), e);
            }
        }

        // Decrement active uploads when worker exits
        activeUploadsPerSession.computeIfPresent(sessionId, (k, v) -> Math.max(0, v - 1));

        log.debug("Queue worker exited for session {}. Checking session completion.", sessionId);
        // Check if all tasks are complete
        checkSessionCompletion(sessionId);
    }

    @Transactional
    private void processUploadTask(UploadTaskRecord task, UploadSession session) {
        log.info("Processing upload task: taskId={}, displayName={}, sessionId={}", task.getId(), task.getDisplayName(), session.getSessionId());

        try {
            task.setStatus(UploadTaskStatus.UPLOADING);
            uploadTaskRecordRepository.save(task);
            log.debug("Task status set to UPLOADING: taskId={}", task.getId());

            // Increment active count
            activeUploadsPerSession.compute(session.getSessionId(), (k, v) -> (v == null ? 0 : v) + 1);

            log.info("Calling completeFileUpload for taskId={}", task.getId());
            resourceFileService.completeFileUpload(task.getId(), session.getUploader());
            log.info("completeFileUpload succeeded for taskId={}", task.getId());

            task.setStatus(UploadTaskStatus.SUCCESS);
            task.setUploadedBytes(task.getFileSize());

            session.incrementUploadedFiles();
            session.addUploadedBytes(task.getFileSize());
            log.info("Task marked SUCCESS: taskId={}, session uploaded files now: {}", task.getId(), session.getUploadedFiles());

        } catch (Exception e) {
            log.error("Failed to upload task {}: {} (cause: {})", task.getId(), e.getMessage(), e.getClass().getSimpleName(), e);

            task.setErrorMessage(e.getMessage());
            task.incrementRetry();
            log.warn("Task failed, retry count: {}/{}", task.getRetryCount(), maxRetries);

            if (task.getRetryCount() < maxRetries) {
                task.setStatus(UploadTaskStatus.RETRYING);
                // Re-queue the task
                try {
                    BlockingQueue<UploadTaskRecord> queue = sessionQueues.get(session.getSessionId());
                    if (queue != null) {
                        queue.put(task);
                        log.info("Re-queued task {} (attempt {} of {})", task.getId(), task.getRetryCount(), maxRetries);
                    } else {
                        log.warn("Queue not found for session {}, cannot re-queue task {}", session.getSessionId(), task.getId());
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while re-queueing task {}", task.getId());
                }
            } else {
                task.setStatus(UploadTaskStatus.FAILED);
                session.incrementFailedFiles();
                log.error("Task exhausted retries and marked FAILED: taskId={}", task.getId());
            }

        } finally {
            uploadTaskRecordRepository.save(task);
            uploadSessionRepository.save(session);
            log.debug("Task and session persisted: taskId={}, status={}", task.getId(), task.getStatus());

            // Decrement active count
            activeUploadsPerSession.compute(session.getSessionId(), (k, v) -> Math.max(0, (v == null ? 0 : v) - 1));
        }

        // Must run after each task: workers otherwise stay in an idle poll loop while the session
        // stays IN_PROGRESS, so the session never reaches COMPLETED (frontend never sees success),
        // and threads are never released (starves the pool and breaks later uploads).
        checkSessionCompletion(session.getSessionId());
    }

    @Transactional
    private void checkSessionCompletion(String sessionId) {
        UploadSession session = uploadSessionRepository.findBySessionId(sessionId)
                .orElse(null);

        if (session == null) {
            return;
        }

        List<UploadTaskRecord> tasks = uploadTaskRecordRepository.findBySessionId(session.getId());
        long completedTasks = tasks.stream()
                .filter(t -> t.getStatus() == UploadTaskStatus.SUCCESS || t.getStatus() == UploadTaskStatus.FAILED)
                .count();

        if (completedTasks == tasks.size() && !tasks.isEmpty()) {
            long failedCount = tasks.stream()
                    .filter(t -> t.getStatus() == UploadTaskStatus.FAILED)
                    .count();

            if (failedCount == 0) {
                session.setStatus(UploadSessionStatus.COMPLETED);
                log.info("Upload session {} completed successfully", sessionId);
            } else {
                session.setStatus(UploadSessionStatus.FAILED);
                session.setErrorMessage(failedCount + " file(s) failed to upload");
                log.warn("Upload session {} completed with {} failures", sessionId, failedCount);
            }

            session.setUpdatedAt(LocalDateTime.now());
            uploadSessionRepository.save(session);

            // Clean up queue
            sessionQueues.remove(sessionId);
            activeUploadsPerSession.remove(sessionId);
        }
    }

    @Transactional
    public void pauseSession(String sessionId) {
        UploadSession session = uploadSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(UploadSessionStatus.PAUSED);
        uploadSessionRepository.save(session);

        log.info("Paused upload session {}", sessionId);
    }

    @Transactional
    public void resumeSession(String sessionId) {
        UploadSession session = uploadSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(UploadSessionStatus.IN_PROGRESS);
        uploadSessionRepository.save(session);

        startProcessingSession(sessionId);
        log.info("Resumed upload session {}", sessionId);
    }

    @Transactional
    public void cancelSession(String sessionId) {
        UploadSession session = uploadSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(UploadSessionStatus.CANCELLED);
        uploadSessionRepository.save(session);

        List<UploadTaskRecord> tasks = uploadTaskRecordRepository.findBySessionId(session.getId());
        for (UploadTaskRecord task : tasks) {
            if (task.getStatus() != UploadTaskStatus.SUCCESS && task.getStatus() != UploadTaskStatus.FAILED) {
                task.setStatus(UploadTaskStatus.CANCELLED);
                uploadTaskRecordRepository.save(task);
            }
        }

        sessionQueues.remove(sessionId);
        activeUploadsPerSession.remove(sessionId);

        log.info("Cancelled upload session {}", sessionId);
    }

    @Transactional(readOnly = true)
    public UploadSession getSessionStatus(String sessionId) {
        return uploadSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    // Cleanup expired sessions
    @Transactional
    @Async
    public void cleanupExpiredSessions() {
        List<UploadSession> expiredSessions = uploadSessionRepository.findByStatusAndExpiresAtBefore(
                UploadSessionStatus.COMPLETED, LocalDateTime.now()
        );

        for (UploadSession session : expiredSessions) {
            try {
                List<UploadTaskRecord> tasks = uploadTaskRecordRepository.findBySessionId(session.getId());
                uploadTaskRecordRepository.deleteAll(tasks);
                uploadSessionRepository.delete(session);
                log.info("Cleaned up expired session {}", session.getSessionId());
            } catch (Exception e) {
                log.error("Error cleaning up session {}: {}", session.getSessionId(), e.getMessage());
            }
        }
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
