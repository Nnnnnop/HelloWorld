package com.example.polyusigwebsite.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "upload_session")
public class UploadSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false)
    private String uploader;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadSessionStatus status = UploadSessionStatus.IN_PROGRESS;

    @Column(nullable = false)
    private Long totalFiles = 0L;

    @Column(nullable = false)
    private Long uploadedFiles = 0L;

    @Column(nullable = false)
    private Long totalBytes = 0L;

    @Column(nullable = false)
    private Long uploadedBytes = 0L;

    @Column(nullable = false)
    private Long failedFiles = 0L;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UploadTaskRecord> tasks = new ArrayList<>();

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public UploadSessionStatus getStatus() {
        return status;
    }

    public void setStatus(UploadSessionStatus status) {
        this.status = status;
    }

    public Long getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(Long totalFiles) {
        this.totalFiles = totalFiles;
    }

    public Long getUploadedFiles() {
        return uploadedFiles;
    }

    public void setUploadedFiles(Long uploadedFiles) {
        this.uploadedFiles = uploadedFiles;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public Long getUploadedBytes() {
        return uploadedBytes;
    }

    public void setUploadedBytes(Long uploadedBytes) {
        this.uploadedBytes = uploadedBytes;
    }

    public Long getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(Long failedFiles) {
        this.failedFiles = failedFiles;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public List<UploadTaskRecord> getTasks() {
        return tasks;
    }

    public void setTasks(List<UploadTaskRecord> tasks) {
        this.tasks = tasks;
    }

    public void incrementUploadedFiles() {
        this.uploadedFiles++;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementFailedFiles() {
        this.failedFiles++;
        this.updatedAt = LocalDateTime.now();
    }

    public void addUploadedBytes(long bytes) {
        this.uploadedBytes += bytes;
        this.updatedAt = LocalDateTime.now();
    }

    public int getProgressPercentage() {
        if (totalBytes <= 0) {
            return 0;
        }
        return Math.min(100, (int) ((uploadedBytes * 100) / totalBytes));
    }
}
