package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.ArchiveContentsResponse;
import com.example.polyusigwebsite.dto.BulkFolderUploadManifest;
import com.example.polyusigwebsite.dto.ResourceFileResponse;
import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.dto.ResourceSearchResponse;
import com.example.polyusigwebsite.dto.ResourceUpdateRequest;
import com.example.polyusigwebsite.dto.ResourceUploadRequest;
import com.example.polyusigwebsite.entity.ResourceVisibility;
import com.example.polyusigwebsite.security.SecurityUtils;
import com.example.polyusigwebsite.service.ResourceFileService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/files")
public class ResourceFileController {

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
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadDateTo
    ) {
        return ResponseEntity.ok(resourceFileService.search(
                new ResourceSearchRequest(keyword, fileType, category, uploader, uploadDateFrom, uploadDateTo),
                securityUtils.currentUsernameOrNull()
        ));
    }

    @GetMapping("/favourites")
    public ResponseEntity<List<ResourceFileResponse>> listFavourites() {
        return ResponseEntity.ok(resourceFileService.listFavourites(securityUtils.currentUsernameOrNull()));
    }

    @GetMapping("/favourites/ids")
    public ResponseEntity<List<Long>> listFavouriteResourceIds() {
        return ResponseEntity.ok(resourceFileService.listFavouriteResourceIds(securityUtils.currentUsernameOrNull()));
    }

    @PostMapping("/favourites/{resourceId}")
    public ResponseEntity<Void> addFavourite(@PathVariable @Min(1) long resourceId) {
        resourceFileService.addFavourite(resourceId, securityUtils.currentUsernameOrNull());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/favourites/{resourceId}")
    public ResponseEntity<Void> removeFavourite(@PathVariable @Min(1) long resourceId) {
        resourceFileService.removeFavourite(resourceId, securityUtils.currentUsernameOrNull());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceFileResponse> detail(
            @PathVariable @Min(1) Long id,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(resourceFileService.detail(id, keyword, securityUtils.currentUsernameOrNull()));
    }

    @GetMapping("/{id}/archive-list")
    public ResponseEntity<ArchiveContentsResponse> archiveList(@PathVariable @Min(1) Long id) {
        return ResponseEntity.ok(resourceFileService.listArchiveContents(id, securityUtils.currentUsernameOrNull()));
    }

    @GetMapping("/{id}/archive-entry")
    public ResponseEntity<org.springframework.core.io.Resource> archiveEntry(
            @PathVariable @Min(1) Long id,
            @RequestParam("path") String entryPath
    ) {
        ResourceFileService.ResourceDownload result = resourceFileService.streamArchiveEntry(
                id, entryPath, securityUtils.currentUsernameOrNull());
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
}
