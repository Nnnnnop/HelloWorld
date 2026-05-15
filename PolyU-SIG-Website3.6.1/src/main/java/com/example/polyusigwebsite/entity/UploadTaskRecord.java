package com.example.polyusigwebsite.entity;

import jakarta.persistence.*;
import com.example.polyusigwebsite.dto.UploadTaskResponse;
import java.time.LocalDateTime;

@Entity
@Table(name = "upload_task_record")
public class UploadTaskRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private UploadSession session;

    @Column(nullable = false)
    private String clientPath;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadTaskStatus status = UploadTaskStatus.QUEUED;

    @Column(nullable = false)
    private Long fileSize = 0L;

    @Column(nullable = false)
    private Long uploadedBytes = 0L;

    @Column
    private String fileHash;

    @Column
    private Long resourceFileId;

    @Column(nullable = false)
    private Long folderId;

    @Column
    private String category;

    @Column
    private String description;

    @Column
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column
    private ResourceVisibility visibility;

    @Column
    private Integer retryCount = 0;

    @Column
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UploadSession getSession() {
        return session;
    }

    public void setSession(UploadSession session) {
        this.session = session;
    }

    public String getClientPath() {
        return clientPath;
    }

    public void setClientPath(String clientPath) {
        this.clientPath = clientPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UploadTaskStatus getStatus() {
        return status;
    }

    public void setStatus(UploadTaskStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getUploadedBytes() {
        return uploadedBytes;
    }

    public void setUploadedBytes(Long uploadedBytes) {
        this.uploadedBytes = uploadedBytes;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public Long getResourceFileId() {
        return resourceFileId;
    }

    public void setResourceFileId(Long resourceFileId) {
        this.resourceFileId = resourceFileId;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public ResourceVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(ResourceVisibility visibility) {
        this.visibility = visibility;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
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

    public int getProgressPercentage() {
        if (fileSize <= 0) {
            return 0;
        }
        return Math.min(100, (int) ((uploadedBytes * 100) / fileSize));
    }

    public void incrementRetry() {
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
    }

    public UploadTaskResponse toDTO() {
        return new UploadTaskResponse(
                this.id,
                this.clientPath,
                this.displayName,
                this.status,
                this.fileSize,
                this.uploadedBytes,
                this.getProgressPercentage(),
                this.resourceFileId,
                this.errorMessage,
                this.retryCount
        );
    }
}
