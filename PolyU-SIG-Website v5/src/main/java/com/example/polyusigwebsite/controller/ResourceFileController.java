package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.BulkFolderUploadManifest;
import com.example.polyusigwebsite.dto.ResourceFileResponse;
import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.dto.ResourceSearchResponse;
import com.example.polyusigwebsite.dto.ResourceUpdateRequest;
import com.example.polyusigwebsite.dto.ResourceUploadRequest;
import com.example.polyusigwebsite.dto.InitializeUploadRequest;
import com.example.polyusigwebsite.dto.UploadFileRequest;
import com.example.polyusigwebsite.dto.UploadSessionResponse;
import com.example.polyusigwebsite.dto.UploadTaskResponse;
import com.example.polyusigwebsite.entity.ResourceVisibility;
import com.example.polyusigwebsite.entity.UploadSession;
import com.example.polyusigwebsite.entity.UploadTaskRecord;
import com.example.polyusigwebsite.security.SecurityUtils;
import com.example.polyusigwebsite.service.ResourceFileService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.io.IOException;

@Validated
@RestController
@RequestMapping("/api/files")
public class ResourceFileController {

    private static final Logger log = LoggerFactory.getLogger(ResourceFileController.class);
    private final ResourceFileService resourceFileService;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    public ResourceFileController(ResourceFileService resourceFileService, SecurityUtils securityUtils, ObjectMapper objectMapper) {
        this.resourceFileService = resourceFileService;
        this.securityUtils = securityUtils;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResourceFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") @NotBlank String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("category") @NotBlank String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam("visibility") ResourceVisibility visibility,
            @RequestParam(value = "folderId", required = false) Long folderId
    ) {
        // Use default folder (root) if folderId is not provided
        if (folderId == null || folderId <= 0) {
            folderId = 1L; // Default to root folder (id=1)
        }
        ResourceUploadRequest metadata = new ResourceUploadRequest(title, description, category, tags, visibility, folderId);
        return ResponseEntity.ok(resourceFileService.upload(file, metadata, securityUtils.currentUsernameOrNull()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/upload-bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadBulk(
            @RequestParam("manifest") String manifestJson,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {
        BulkFolderUploadManifest manifest = objectMapper.readValue(manifestJson, BulkFolderUploadManifest.class);
        resourceFileService.uploadBulk(manifest, files, securityUtils.currentUsernameOrNull());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<ResourceFileResponse>> list() {
        return ResponseEntity.ok(resourceFileService.listAll(securityUtils.currentUsernameOrNull()));
    }

    @GetMapping("/search")
    public ResponseEntity<ResourceSearchResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String uploader
    ) {
        return ResponseEntity.ok(resourceFileService.search(
                new ResourceSearchRequest(keyword, fileType, category, uploader),
                securityUtils.currentUsernameOrNull()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceFileResponse> detail(
            @PathVariable @Min(1) Long id,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(resourceFileService.detail(id, keyword, securityUtils.currentUsernameOrNull()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable @Min(1) Long id
    ) {
        ResourceFileService.ResourceDownload result = resourceFileService.download(id, securityUtils.currentUsernameOrNull());

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(result.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.resource());
    }

    @GetMapping("/download-zip")
    public ResponseEntity<org.springframework.core.io.Resource> downloadZip(
            @RequestParam("ids") List<Long> ids
    ) throws IOException {
        ResourceFileService.ResourceDownload result = resourceFileService.downloadZip(ids, securityUtils.currentUsernameOrNull());

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename("selected-files.zip", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.resource());
    }

    @GetMapping("/download-folder/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFolder(
            @PathVariable @Min(1) Long id
    ) throws IOException {
        ResourceFileService.ResourceDownload result = resourceFileService.downloadFolder(id, securityUtils.currentUsernameOrNull());

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(result.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.resource());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<org.springframework.core.io.Resource> preview(
            @PathVariable @Min(1) Long id
    ) {
        ResourceFileService.ResourceDownload result = resourceFileService.preview(id, securityUtils.currentUsernameOrNull());
        MediaType mediaType;
        try {
            mediaType = (result.contentType() == null || result.contentType().isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(result.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(result.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(mediaType)
                .body(result.resource());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ResourceFileResponse> update(
            @PathVariable @Min(1) Long id,
            @Validated @RequestBody ResourceUpdateRequest request
    ) {
        return ResponseEntity.ok(resourceFileService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Min(1) Long id) {
        resourceFileService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ========== Session-Based Upload Endpoints ==========

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/upload/session/initialize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadSession> initializeUploadSession(
            @RequestBody InitializeUploadRequest request
    ) {
        UploadSession session = resourceFileService.initializeUploadSession(request, securityUtils.currentUsernameOrNull());
        return ResponseEntity.ok(session);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/upload/session/{sessionId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadTaskResponse> queueFileUpload(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientPath") String clientPath,
            @RequestParam("displayName") String displayName,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "visibility", required = false) ResourceVisibility visibility
    ) throws IOException {
        try {
            log.info("Queueing file upload: sessionId={}, displayName={}, folderId={}, fileSize={}", 
                    sessionId, displayName, folderId, file.getSize());

            if (folderId == null || folderId <= 0) {
                folderId = 1L;
            }

            UploadFileRequest request = new UploadFileRequest(
                    sessionId,
                    clientPath,
                    displayName,
                    folderId,
                    category,
                    description,
                    tags,
                    visibility != null ? visibility : ResourceVisibility.HIDDEN
            );

            UploadTaskRecord task = resourceFileService.queueFileUpload(request, file, securityUtils.currentUsernameOrNull());
            log.info("File upload queued successfully: taskId={}, displayName={}", task.getId(), displayName);
            return ResponseEntity.accepted().body(task.toDTO());
        } catch (Exception e) {
            log.error("Error queueing file upload: sessionId={}, displayName={}, error={}", 
                    sessionId, displayName, e.getMessage(), e);
            throw e;
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/upload/session/{sessionId}/complete")
    public ResponseEntity<Void> completeUploadSession(
            @PathVariable String sessionId
    ) {
        resourceFileService.completeUploadSession(sessionId, securityUtils.currentUsernameOrNull());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/upload/session/{sessionId}/status")
    public ResponseEntity<UploadSessionResponse> getUploadSessionStatus(
            @PathVariable String sessionId
    ) {
        UploadSessionResponse response = resourceFileService.getUploadSessionStatus(sessionId, securityUtils.currentUsernameOrNull());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/upload/session/{sessionId}/pause")
    public ResponseEntity<Void> pauseUploadSession(
            @PathVariable String sessionId
    ) {
        resourceFileService.pauseUploadSession(sessionId, securityUtils.currentUsernameOrNull());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/upload/session/{sessionId}/resume")
    public ResponseEntity<Void> resumeUploadSession(
            @PathVariable String sessionId
    ) {
        resourceFileService.resumeUploadSession(sessionId, securityUtils.currentUsernameOrNull());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/upload/session/{sessionId}/cancel")
    public ResponseEntity<Void> cancelUploadSession(
            @PathVariable String sessionId
    ) {
        resourceFileService.cancelUploadSession(sessionId, securityUtils.currentUsernameOrNull());
        return ResponseEntity.ok().build();
    }
}
