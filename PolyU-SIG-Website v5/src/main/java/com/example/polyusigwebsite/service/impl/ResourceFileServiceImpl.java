package com.example.polyusigwebsite.service.impl;

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
import com.example.polyusigwebsite.entity.Folder;
import com.example.polyusigwebsite.entity.ResourceFile;
import com.example.polyusigwebsite.entity.ResourceVisibility;
import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.entity.AuditAction;
import com.example.polyusigwebsite.entity.UploadSession;
import com.example.polyusigwebsite.entity.UploadSessionStatus;
import com.example.polyusigwebsite.entity.UploadTaskRecord;
import com.example.polyusigwebsite.entity.UploadTaskStatus;
import com.example.polyusigwebsite.repository.FolderRepository;
import com.example.polyusigwebsite.exception.ResourceFileNotFoundException;
import com.example.polyusigwebsite.repository.ResourceFileRepository;
import com.example.polyusigwebsite.repository.UserAccountRepository;
import com.example.polyusigwebsite.repository.UploadTaskRecordRepository;
import com.example.polyusigwebsite.search.ResourceSearchService;
import com.example.polyusigwebsite.service.AuditService;
import com.example.polyusigwebsite.service.AuthService;
import com.example.polyusigwebsite.service.DocumentPreviewService;
import com.example.polyusigwebsite.service.FileContentExtractor;
import com.example.polyusigwebsite.service.ResourceFileService;
import com.example.polyusigwebsite.service.UploadQueueManager;
import com.example.polyusigwebsite.service.ChunkedUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ResourceFileServiceImpl implements ResourceFileService {
    private static final Logger log = LoggerFactory.getLogger(ResourceFileServiceImpl.class);

    private final ResourceFileRepository resourceFileRepository;
    private final FolderRepository folderRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final ResourceSearchService resourceSearchService;
    private final FileContentExtractor fileContentExtractor;
    private final DocumentPreviewService documentPreviewService;
    private final UploadQueueManager uploadQueueManager;
    private final ChunkedUploadService chunkedUploadService;
    private final UploadTaskRecordRepository uploadTaskRecordRepository;
    private final Path uploadDir;
    private final List<String> allowedExtensions;

    public ResourceFileServiceImpl(
            ResourceFileRepository resourceFileRepository,
            FolderRepository folderRepository,
            UserAccountRepository userAccountRepository,
            AuthService authService,
            AuditService auditService,
            ResourceSearchService resourceSearchService,
            FileContentExtractor fileContentExtractor,
            DocumentPreviewService documentPreviewService,
            UploadQueueManager uploadQueueManager,
            ChunkedUploadService chunkedUploadService,
            UploadTaskRecordRepository uploadTaskRecordRepository,
            @Value("${app.upload-dir:files}") String uploadDir,
            @Value("${sig.security.allowed-file-types:pdf,doc,docx,xls,xlsx,ppt,pptx,png,jpg,jpeg,gif,webp,bmp,svg,java,py,c,cpp,js,ts,r,m,go,rs,txt,md,json,xml,yaml,yml,sql,csv,ipynb,zip,rar,7z,tar,gz}") String allowedFileTypes
    ) throws IOException {
        this.resourceFileRepository = resourceFileRepository;
        this.folderRepository = folderRepository;
        this.userAccountRepository = userAccountRepository;
        this.authService = authService;
        this.auditService = auditService;
        this.resourceSearchService = resourceSearchService;
        this.fileContentExtractor = fileContentExtractor;
        this.documentPreviewService = documentPreviewService;
        this.uploadQueueManager = uploadQueueManager;
        this.chunkedUploadService = chunkedUploadService;
        this.uploadTaskRecordRepository = uploadTaskRecordRepository;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
        this.allowedExtensions = List.of(allowedFileTypes.split(","))
                .stream().map(String::trim).filter(s -> !s.isBlank()).map(String::toLowerCase).toList();
        log.info("Allowed file extensions loaded: {}", this.allowedExtensions);
    }

    private void verifyStorageFile(Path targetPath, String originalName) {
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new IllegalStateException("Uploaded file not found in storage volume: " + originalName);
        }
        try {
            long size = Files.size(targetPath);
            log.debug("Verified uploaded file '{}' exists with {} bytes", originalName, size);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to verify stored file: " + originalName, ex);
        }
    }

    @Override
    @Transactional
    public ResourceFileResponse upload(MultipartFile file, ResourceUploadRequest metadata, String actor) {
        validateUpload(file);

        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        String storageFileName = UUID.randomUUID() + extension;
        Path targetPath = uploadDir.resolve(storageFileName).normalize();

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store file: " + originalName, ex);
        }
        verifyStorageFile(targetPath, originalName);

        ResourceFile resourceFile = new ResourceFile();
        resourceFile.setTitle(metadata.title());
        resourceFile.setDescription(metadata.description());
        resourceFile.setCategory(metadata.category().trim().toLowerCase());
        resourceFile.setTags(normalizeTags(metadata.tags()));
        resourceFile.setVisibility(metadata.visibility());
        resourceFile.setFileName(originalName);
        resourceFile.setFileType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        resourceFile.setFileSize(file.getSize());
        resourceFile.setStorageFileName(storageFileName);
        resourceFile.setFilePath(targetPath.toString());
        resourceFile.setUploader(requireActor(actor));
        resourceFile.setFolder(requireFolder(metadata.folderId()));
        String extractedContent = fileContentExtractor.extract(targetPath, originalName);
        resourceFile.setContentText(extractedContent);

        ResourceFile saved = resourceFileRepository.save(resourceFile);
        try {
            resourceSearchService.index(saved, extractedContent);
        } catch (Exception ex) {
            // Elasticsearch may not be reachable during local setup; DB search still works.
            log.warn("Failed to index resource {} to Elasticsearch: {}", saved.getId(), ex.getMessage());
        }
        auditService.record(AuditAction.UPLOAD_RESOURCE, actor, "Uploaded resource #" + saved.getId() + " (" + saved.getTitle() + ")");
        return ResourceFileResponse.from(saved);
    }

    @Override
    @Transactional
    public void uploadBulk(BulkFolderUploadManifest manifest, List<MultipartFile> multipartFiles, String actor) throws IOException {
        if (manifest == null) throw new IllegalArgumentException("Manifest is required");
        if (manifest.files() == null || manifest.files().isEmpty()) return;

        Map<String, MultipartFile> fileByClientPath = new HashMap<>();
        for (MultipartFile f : multipartFiles) {
            String clientPath = normalizeClientPath(f.getOriginalFilename());
            if (StringUtils.hasText(clientPath)) {
                fileByClientPath.put(clientPath, f);
            }
        }

        // Validate all files before processing
        for (MultipartFile file : multipartFiles) {
            if (file != null) {
                if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
                    throw new IllegalArgumentException("File name must not be empty.");
                }
                log.debug("Bulk validating file '{}'", file.getOriginalFilename());
                // Allow all files including empty ones
            }
        }

        Map<String, Folder> folderByTempId = new HashMap<>();
        folderByTempId.put("ROOT", null); // special marker for root (no parent)

        // Create folder tree
        if (manifest.folders() != null) {
            for (BulkFolderUploadManifest.FolderNode node : manifest.folders()) {
                createFolderNode(node, folderByTempId, actor);
            }
        }

        Map<String, BulkFolderUploadManifest.FolderNode> manifestFolderByTempId = new HashMap<>();
        if (manifest.folders() != null) {
            for (BulkFolderUploadManifest.FolderNode node : manifest.folders()) {
                manifestFolderByTempId.put(node.tempId(), node);
            }
        }

        Map<String, Integer> filesPerTopLevelFolder = new HashMap<>();

        // Process files in separate transactions to avoid resource exhaustion
        for (BulkFolderUploadManifest.FileNode fn : manifest.files()) {
            String clientPath = normalizeClientPath(fn.clientPath());
            MultipartFile part = fileByClientPath.get(clientPath);
            if (part == null) {
                continue;
            }

            Folder folder = folderByTempId.get(fn.folderTempId());
            if (folder == null) {
                folder = requireFolder(1L); // default to root
            }

            try {
                uploadBulkFileInOwnTransaction(part, fn, folder, actor);

                String topLevelTempId = fn.folderTempId();
                while (topLevelTempId != null && !"ROOT".equals(topLevelTempId)) {
                    BulkFolderUploadManifest.FolderNode current = manifestFolderByTempId.get(topLevelTempId);
                    if (current == null || current.parentTempId() == null || "ROOT".equals(current.parentTempId())) {
                        break;
                    }
                    topLevelTempId = current.parentTempId();
                }

                String topLevelName = "Root";
                if (topLevelTempId != null && !"ROOT".equals(topLevelTempId)) {
                    BulkFolderUploadManifest.FolderNode topLevelNode = manifestFolderByTempId.get(topLevelTempId);
                    if (topLevelNode != null) {
                        topLevelName = topLevelNode.name();
                    }
                }

                filesPerTopLevelFolder.put(topLevelName, filesPerTopLevelFolder.getOrDefault(topLevelName, 0) + 1);
            } catch (Exception ex) {
                log.error("Failed to upload file '{}': {}", part.getOriginalFilename(), ex.getMessage(), ex);
                // Continue with next file instead of failing the entire batch
            }
        }

        for (Map.Entry<String, Integer> entry : filesPerTopLevelFolder.entrySet()) {
            String folderName = entry.getKey();
            int fileCount = entry.getValue();
            String message = String.format("Uploaded folder %s with %d file%s", folderName, fileCount, fileCount == 1 ? "" : "s");
            auditService.record(AuditAction.UPLOAD_RESOURCE, actor, message);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void uploadBulkFileInOwnTransaction(MultipartFile part, BulkFolderUploadManifest.FileNode fn, Folder folder, String actor) throws IOException {
        String requestedName = StringUtils.hasText(fn.displayName())
                ? fn.displayName().trim()
                : StringUtils.cleanPath(part.getOriginalFilename());
        String safeName = safeFileName(requestedName);
        ResourceVisibility visibility = fn.minAccessLevel() != null ? fn.minAccessLevel() : ResourceVisibility.PUBLIC;

        Path folderPath = uploadDir.resolve(folderRelativePath(folder));
        Files.createDirectories(folderPath);

        String storageFileName = UUID.randomUUID() + "-" + safeName;
        Path targetPath = folderPath.resolve(storageFileName);
        
        // Explicitly close the input stream after copying
        try (var inputStream = part.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        verifyStorageFile(targetPath, safeName);

        ResourceFile resourceFile = new ResourceFile();
        resourceFile.setTitle(safeName);
        String normalizedCategory = StringUtils.hasText(fn.category()) ? fn.category().trim().toLowerCase() : "bulk";
        resourceFile.setCategory(normalizedCategory);
        resourceFile.setDescription(fn.description());
        resourceFile.setTags(normalizeTags(fn.tags()));
        resourceFile.setVisibility(visibility);
        resourceFile.setFileName(safeName);
        resourceFile.setFileType(part.getContentType() == null ? "application/octet-stream" : part.getContentType());
        resourceFile.setFileSize(part.getSize());
        resourceFile.setStorageFileName(storageFileName);
        resourceFile.setFilePath(targetPath.toString());
        resourceFile.setUploader(requireActor(actor));
        resourceFile.setFolder(folder);
        String extractedContent = fileContentExtractor.extract(targetPath, safeName);
        resourceFile.setContentText(extractedContent);

        ResourceFile saved = resourceFileRepository.save(resourceFile);
        try {
            resourceSearchService.index(saved, extractedContent);
        } catch (Exception ex) {
            log.warn("Failed to index resource {} to Elasticsearch: {}", saved.getId(), ex.getMessage());
        }
        // Audit logging is now handled at the folder level in uploadBulk method
    }

    @Override
    @Transactional
    public ResourceDownload download(Long id, String actor) {
        ResourceFile resourceFile = loadAccessibleResource(id, actor);
        Path path = Paths.get(resourceFile.getFilePath());
        Resource resource = toResource(path, id);
        auditService.record(AuditAction.DOWNLOAD_RESOURCE, actor, "Downloaded resource #" + resourceFile.getId());
        return new ResourceDownload(buildDownloadName(resourceFile), resource, resourceFile.getFileType());
    }

    @Override
    @Transactional
    public ResourceDownload downloadZip(List<Long> ids, String actor) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No files selected");
        }

        List<ResourceFile> files = ids.stream()
                .map(id -> loadAccessibleResource(id, actor))
                .toList();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.Set<String> usedEntryNames = new java.util.HashSet<>();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (ResourceFile file : files) {
                Path filePath = Paths.get(file.getFilePath());
                if (java.nio.file.Files.exists(filePath)) {
                    Path folderPath = folderRelativePath(file.getFolder());

                    String filename = buildDownloadName(file);

                    // normalize spaces and slashes safely
                    String normalizedFolder =
                            folderPath == null
                                    ? ""
                                    : folderPath.toString()
                                        .replace("\\", "/")
                                        .replaceAll("/+", "/")
                                        .trim();

                    String zipEntryName =
                            normalizedFolder.isBlank()
                                    ? filename
                                    : normalizedFolder + "/" + filename;

                    // ensure unique entry names
                    String finalZipEntryName = zipEntryName;

                    if (usedEntryNames.contains(finalZipEntryName)) {
                        String ext = extractExtension(filename);

                        String base =
                                ext.isEmpty()
                                        ? filename
                                        : filename.substring(0, filename.length() - ext.length());

                        finalZipEntryName =
                                (normalizedFolder.isBlank()
                                        ? ""
                                        : normalizedFolder + "/")
                                + base
                                + "_"
                                + file.getId()
                                + ext;
                    }

                    usedEntryNames.add(finalZipEntryName);

                    zos.putNextEntry(new java.util.zip.ZipEntry(finalZipEntryName));
                    if (!java.nio.file.Files.isRegularFile(filePath)) {
                        continue;
                    }

                    java.nio.file.Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
        }

        byte[] zipBytes = baos.toByteArray();
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(zipBytes);
        auditService.record(AuditAction.DOWNLOAD_RESOURCE, actor, "Downloaded zip with " + ids.size() + " files");
        return new ResourceDownload("selected-files.zip", resource, "application/zip");
    }

    @Override
    @Transactional
    public ResourceDownload downloadFolder(Long folderId, String actor) throws IOException {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + folderId));

        List<Long> folderIds = collectFolderIds(folder);
        List<ResourceFile> files = new java.util.ArrayList<>();
        for (Long id : folderIds) {
            files.addAll(resourceFileRepository.findByFolderId(id));
        }

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            Path rootFolderPath = Paths.get(safePathSegment(folder.getName()));
            Path baseFolderPath = folderRelativePath(folder);
            java.util.Set<String> addedEntries = new java.util.LinkedHashSet<>();

            // Add all folder entries first so empty folders are preserved in the zip
            List<Folder> folders = folderRepository.findAllById(folderIds);
            for (Folder nestedFolder : folders) {
                Path nestedFolderPath = folderRelativePath(nestedFolder);
                Path relativePath = baseFolderPath.relativize(nestedFolderPath);
                Path entryPath = rootFolderPath.resolve(relativePath);
                String zipEntryName = entryPath.toString().replace('\\', '/') + "/";
                if (addedEntries.add(zipEntryName)) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(zipEntryName));
                    zos.closeEntry();
                }
            }

            for (ResourceFile file : files) {
                assertCanAccess(file, actor);
                Path fileFolderPath = folderRelativePath(file.getFolder());
                Path relativePath = baseFolderPath.relativize(fileFolderPath);
                Path entryPath = rootFolderPath.resolve(relativePath).resolve(buildDownloadName(file));
                String zipEntryName = entryPath.toString().replace('\\', '/');
                if (addedEntries.add(zipEntryName)) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(zipEntryName));
                    java.nio.file.Files.copy(Paths.get(file.getFilePath()), zos);
                    zos.closeEntry();
                }
            }
        }

        byte[] zipBytes = baos.toByteArray();
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(zipBytes);
        auditService.record(AuditAction.DOWNLOAD_RESOURCE, actor, "Downloaded folder zip: " + folder.getName());
        String downloadName = safePathSegment(folder.getName()) + ".zip";
        return new ResourceDownload(downloadName, resource, "application/zip");
    }

    @Override
    @Transactional
    public ResourceDownload preview(Long id, String actor) {
        ResourceFile resourceFile = loadAccessibleResource(id, actor);
        Path sourcePath = Paths.get(resourceFile.getFilePath());

        Path previewPath = sourcePath;
        String previewFileName = resourceFile.getFileName();
        String previewContentType = resourceFile.getFileType();

        if (documentPreviewService.shouldConvertToPdf(resourceFile.getFileName())) {
            previewPath = documentPreviewService.resolvePreviewPath(sourcePath);
            previewFileName = toPdfName(resourceFile.getFileName());
            previewContentType = "application/pdf";
        }

        Resource resource = toResource(previewPath, id);
        auditService.record(AuditAction.DOWNLOAD_RESOURCE, actor, "Previewed resource #" + resourceFile.getId());
        return new ResourceDownload(previewFileName, resource, previewContentType);
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceFileResponse detail(Long id, String keyword, String actor) {
        ResourceFile resourceFile = resourceFileRepository.findById(id)
                .orElseThrow(() -> new ResourceFileNotFoundException(id));
        assertCanAccess(resourceFile, actor);
        return ResourceFileResponse.from(
                resourceFile,
                fallbackHighlight(resourceFile, normalize(keyword)),
                buildPreviewContent(resourceFile)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceSearchResponse search(ResourceSearchRequest request, String actor) {
        String normalizedKeyword = normalize(request.keyword());
        String normalizedFileType = normalize(request.fileType());
        String normalizedCategory = normalize(request.category());
        String normalizedUploader = normalize(request.uploader());
        validateKeywordLength(normalizedKeyword);

        if (normalizedKeyword != null) {
            try {
                ResourceSearchService.SearchResult result = resourceSearchService.search(request);
                if (!result.ids().isEmpty()) {
                    Map<Long, ResourceFile> fileMap = resourceFileRepository.findAllById(result.ids())
                            .stream().collect(java.util.stream.Collectors.toMap(ResourceFile::getId, f -> f));
                    return new ResourceSearchResponse(
                            result.ids().stream()
                                    .map(fileMap::get)
                                    .filter(java.util.Objects::nonNull)
                                    .map(file -> ResourceFileResponse.from(file, result.highlights().get(file.getId())))
                                    .toList(),
                            result.suggestion()
                    );
                }
            } catch (Exception ignored) {
                // fallback to SQL search
            }
        }

        // DB schema may contain legacy bytea columns that break LOWER(...) in SQL.
        // To keep search available, perform filtering in Java over all resources.
        List<ResourceFile> baseFiles = resourceFileRepository.findAllWithUploader()
                .stream()
                .filter(file -> canAccess(file, actor))
                .filter(file -> matchesFileType(file, normalizedFileType))
                .filter(file -> matchesEqualsIgnoreCase(file.getCategory(), normalizedCategory))
                .filter(file -> matchesEqualsIgnoreCase(file.getUploader().getUsername(), normalizedUploader))
                .toList();

        List<ResourceFileResponse> items = baseFiles.stream()
                .filter(file -> matchesKeyword(file, normalizedKeyword))
                .map(file -> ResourceFileResponse.from(file, fallbackHighlight(file, normalizedKeyword)))
                .toList();
        return new ResourceSearchResponse(items, fallbackEnglishSuggestion(normalizedKeyword, baseFiles));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceFileResponse> listAll(String actor) {
        return resourceFileRepository.findAllWithUploader()
                .stream()
                .filter(file -> canAccess(file, actor))
                .map(file -> ResourceFileResponse.from(file))
                .toList();
    }

    @Override
    @Transactional
    public ResourceFileResponse update(Long id, ResourceUpdateRequest request) {
        ResourceFile resourceFile = resourceFileRepository.findById(id)
                .orElseThrow(() -> new ResourceFileNotFoundException(id));
        resourceFile.setTitle(request.title());
        resourceFile.setDescription(request.description());
        resourceFile.setCategory(request.category().trim().toLowerCase());
        resourceFile.setTags(normalizeTags(request.tags()));
        resourceFile.setVisibility(request.visibility());
        resourceFile.setFolder(requireFolder(request.folderId()));
        ResourceFile saved = resourceFileRepository.save(resourceFile);
        try {
            resourceSearchService.index(saved, saved.getContentText());
        } catch (Exception ex) {
            log.warn("Failed to re-index resource {} after update: {}", saved.getId(), ex.getMessage());
        }
        return ResourceFileResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ResourceFile resourceFile = resourceFileRepository.findById(id)
                .orElseThrow(() -> new ResourceFileNotFoundException(id));
        resourceFileRepository.delete(resourceFile);
        try {
            Files.deleteIfExists(Paths.get(resourceFile.getFilePath()));
        } catch (IOException ignored) {
        }
        try {
            resourceSearchService.delete(id);
        } catch (Exception ignored) {
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null) {
            throw new IllegalArgumentException("File must not be null.");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("File name must not be empty.");
        }
        // Allow empty files and all extensions
        log.debug("File validation passed for '{}'", file.getOriginalFilename());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf(".");
        return index >= 0 ? fileName.substring(index) : "";
    }

    private String normalizeTags(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private UserAccount requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Login required");
        }
        return userAccountRepository.findByUsername(actor)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + actor));
    }

    private Folder requireFolder(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + folderId));
    }

    private boolean canAccess(ResourceFile file, String actor) {
        if (actor == null || actor.isBlank()) {
            return false;
        }
        if (file.getVisibility() == ResourceVisibility.PUBLIC) {
            return true;
        }
        if (file.getVisibility() == ResourceVisibility.L1 || file.getVisibility() == ResourceVisibility.L2) {
            return authService.isPrivilegedUser(actor);
        }
        if (file.getVisibility() == ResourceVisibility.HIDDEN) {
            return file.getUploader().getUsername().equals(actor);
        }
        return false;
    }

    private void assertCanAccess(ResourceFile file, String actor) {
        if (!canAccess(file, actor)) {
            throw new IllegalArgumentException("You do not have permission to access this resource.");
        }
    }

    private boolean matchesLike(String value, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    private boolean matchesEqualsIgnoreCase(String value, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.equalsIgnoreCase(expected);
    }

    private boolean matchesKeyword(ResourceFile file, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return matchesLike(file.getTitle(), keyword)
                || matchesLike(file.getCategory(), keyword)
                || matchesLike(file.getContentText(), keyword);
    }

    private boolean matchesFileType(ResourceFile file, String expectedFileType) {
        if (expectedFileType == null || expectedFileType.isBlank()) {
            return true;
        }
        String expected = expectedFileType.trim().toLowerCase();
        if (expected.startsWith(".")) {
            expected = expected.substring(1);
        }
        if (expected.isBlank()) {
            return true;
        }

        String fileType = file.getFileType() == null ? "" : file.getFileType().toLowerCase();
        String extension = extractExtension(file.getFileName()).replace(".", "").toLowerCase();

        // If user inputs a MIME type fragment (e.g. application/pdf), keep substring matching behavior.
        if (expected.contains("/")) {
            return fileType.contains(expected);
        }
        // For normal user input like docx/pdf/pptx, prioritize exact extension match.
        if (!extension.isBlank() && extension.equals(expected)) {
            return true;
        }
        return matchesMimeForExtension(expected, fileType);
    }

    private boolean matchesMimeForExtension(String extension, String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        return switch (extension) {
            case "pdf" -> mimeType.contains("pdf");
            case "doc" -> mimeType.contains("msword");
            case "docx" -> mimeType.contains("wordprocessingml");
            case "xls" -> mimeType.contains("vnd.ms-excel");
            case "xlsx" -> mimeType.contains("spreadsheetml");
            case "ppt" -> mimeType.contains("vnd.ms-powerpoint");
            case "pptx" -> mimeType.contains("presentationml");
            case "jpg" -> mimeType.contains("jpeg");
            case "md" -> mimeType.contains("markdown");
            case "yml" -> mimeType.contains("yaml");
            default -> false;
        };
    }

    private String fallbackHighlight(ResourceFile file, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        if (matchesLike(file.getTitle(), keyword)) {
            return highlight(file.getTitle(), keyword);
        }
        if (matchesLike(file.getDescription(), keyword)) {
            return highlight(file.getDescription(), keyword);
        }
        if (matchesLike(file.getTags(), keyword)) {
            return highlight(file.getTags(), keyword);
        }
        if (matchesLike(file.getContentText(), keyword)) {
            return snippetHighlight(file.getContentText(), keyword);
        }
        return null;
    }

    private String highlight(String text, String keyword) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?i)" + java.util.regex.Pattern.quote(keyword), "<em>$0</em>");
    }

    private String snippetHighlight(String text, String keyword) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int index = lowerText.indexOf(lowerKeyword);
        if (index < 0) {
            return null;
        }
        int context = 60;
        int start = Math.max(0, index - context);
        int end = Math.min(text.length(), index + keyword.length() + context);
        String snippet = text.substring(start, end);
        String highlighted = highlight(snippet, keyword);
        if (start > 0) {
            highlighted = "..." + highlighted;
        }
        if (end < text.length()) {
            highlighted = highlighted + "...";
        }
        return highlighted;
    }

    private String buildPreviewContent(ResourceFile file) {
        String content = file.getContentText();
        if (content == null || content.isBlank()) {
            return null;
        }
        int maxPreviewLength = 8000;
        if (content.length() <= maxPreviewLength) {
            return content;
        }
        return content.substring(0, maxPreviewLength) + "\n...(content truncated)";
    }

    private ResourceFile loadAccessibleResource(Long id, String actor) {
        ResourceFile resourceFile = resourceFileRepository.findById(id)
                .orElseThrow(() -> new ResourceFileNotFoundException(id));
        assertCanAccess(resourceFile, actor);
        return resourceFile;
    }

    private Resource toResource(Path path, Long id) {
        if (!Files.exists(path)) {
            throw new ResourceFileNotFoundException(id);
        }
        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Invalid file path: " + path, ex);
        }
    }

    private String toPdfName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "preview.pdf";
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base + ".pdf";
    }

    private String buildDownloadName(ResourceFile resourceFile) {
        String originalFileName = resourceFile.getFileName();
        String extension = extractExtension(originalFileName);
        String title = sanitizeFileNamePart(resourceFile.getTitle());
        if (title.isBlank()) {
            return originalFileName;
        }
        if (extension.isEmpty()) {
            return title;
        }
        // Title often already includes the extension (e.g. copied from original file name); avoid ".py.py".
        if (title.toLowerCase().endsWith(extension.toLowerCase())) {
            return title;
        }
        return title + extension;
    }

    private String sanitizeFileNamePart(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ");
        return sanitized;
    }

    private void validateKeywordLength(String keyword) {
        if (keyword == null) {
            return;
        }
        int codePoints = keyword.codePointCount(0, keyword.length());
        if (codePoints < 2) {
            throw new IllegalArgumentException("Keyword must contain at least 2 characters.");
        }
    }

    private String fallbackEnglishSuggestion(String keyword, List<ResourceFile> files) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim().toLowerCase();
        if (!normalized.matches("[a-z]{3,}")) {
            return null;
        }
        Set<String> candidates = new HashSet<>();
        for (ResourceFile item : files) {
            collectEnglishTokens(candidates, item.getTitle());
            collectEnglishTokens(candidates, item.getDescription());
            collectEnglishTokens(candidates, item.getTags());
        }
        if (candidates.contains(normalized)) {
            return null;
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = levenshtein(normalized, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        return bestDistance <= 2 ? best : null;
    }

    private void collectEnglishTokens(Set<String> sink, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String[] tokens = text.toLowerCase().split("[^a-z]+");
        for (String token : tokens) {
            if (token.length() >= 3) {
                sink.add(token);
            }
        }
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private void createFolderNode(
            BulkFolderUploadManifest.FolderNode node,
            Map<String, Folder> folderByTempId,
            String actor
    ) {
        if (node == null) return;
        if (!StringUtils.hasText(node.tempId())) return;
        if (folderByTempId.containsKey(node.tempId())) return;

        String name = node.name() == null ? "" : node.name().trim();
        if (!StringUtils.hasText(name)) return;

        Folder parent = null;
        if (StringUtils.hasText(node.parentTempId())) {
            parent = folderByTempId.get(node.parentTempId());
        }

        Folder created = new Folder();
        created.setName(name);
        created.setParent(parent);
        folderByTempId.put(node.tempId(), folderRepository.save(created));
    }

    private Path folderRelativePath(Folder folder) {
        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        Folder cur = folder;
        while (cur != null && cur.getId() != 1) { // assuming id 1 is root
            parts.addFirst(safePathSegment(cur.getName()));
            cur = cur.getParent();
        }
        Path rel = Paths.get("");
        for (String p : parts) rel = rel.resolve(p);
        return rel;
    }

    private String buildFolderDisplayPath(Folder folder) {
        if (folder == null || folder.getId() == 1) {
            return "Root";
        }
        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        Folder cur = folder;
        while (cur != null && cur.getId() != 1) {
            parts.addFirst(cur.getName());
            cur = cur.getParent();
        }
        return String.join(" / ", parts);
    }

    private List<Long> collectFolderIds(Folder root) {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(root.getId());
        collectChildFolderIds(root, ids);
        return ids;
    }

    private void collectChildFolderIds(Folder folder, List<Long> ids) {
        List<Folder> children = folderRepository.findByParentId(folder.getId());
        for (Folder child : children) {
            ids.add(child.getId());
            collectChildFolderIds(child, ids);
        }
    }

    private String safePathSegment(String raw) {
        if (!StringUtils.hasText(raw)) return "untitled";
        String s = raw.trim();
        s = s.replaceAll("[\\\\/]+", "-");
        s = s.replaceAll("[:*?\"<>|]", "");
        s = s.replaceAll("\\s+", " ");
        if (s.isBlank()) return "untitled";
        return s;
    }

    private String safeFileName(String raw) {
        if (!StringUtils.hasText(raw)) return "file";
        String s = raw.trim();
        s = s.replaceAll("[\\\\/]+", "-");
        s = s.replaceAll("[:*?\"<>|]", "");
        s = s.replaceAll("\\s+", " ");
        if (s.isBlank()) return "file";
        return s;
    }

    private String normalizeClientPath(String raw) {
        if (!StringUtils.hasText(raw)) return "";
        String s = raw.replace('\\', '/').trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    // ========== New Session-Based Upload Methods ==========

    @Override
    @Transactional
    public UploadSession initializeUploadSession(InitializeUploadRequest request, String actor) {
        if (!authService.isPrivilegedUser(actor)) {
            throw new IllegalArgumentException("Only administrators can upload folders");
        }

        UploadSession session = uploadQueueManager.createSession(
                request.sessionId(),
                actor,
                request.totalFiles(),
                request.totalBytes()
        );

        return session;
    }

    @Override
    @Transactional
    public UploadTaskRecord queueFileUpload(UploadFileRequest request, MultipartFile file, String actor) throws IOException {
        try {
            log.info("queueFileUpload: sessionId={}, displayName={}, folderId={}, fileSize={}, actor={}", 
                    request.sessionId(), request.displayName(), request.folderId(), file.getSize(), actor);

            UploadSession session = uploadQueueManager.getSessionStatus(request.sessionId());

            if (!session.getUploader().equals(actor)) {
                throw new IllegalArgumentException("Upload session belongs to another user");
            }

            Folder folder = requireFolder(request.folderId());
            log.debug("Folder found: id={}, name={}", folder.getId(), folder.getName());

            UploadTaskRecord task = uploadQueueManager.createTask(
                    session,
                    request.clientPath(),
                    request.displayName(),
                    request.folderId(),
                    file.getSize(),
                    request.category(),
                    request.description(),
                    request.tags(),
                    request.visibility()
            );
            log.debug("Task created: id={}, status={}", task.getId(), task.getStatus());

            // Upload file in chunks and calculate hash
            log.debug("Starting chunked upload for taskId={}", task.getId());
            ChunkedUploadService.UploadChunkResult chunkResult = chunkedUploadService.uploadFileInChunks(
                    file,
                    task,
                    null
            );
            log.debug("Chunked upload completed for taskId={}, hash={}", task.getId(), chunkResult.fileHash);

            task.setFileHash(chunkResult.fileHash);
            task = uploadTaskRecordRepository.save(task);
            log.info("Task updated with hash: taskId={}", task.getId());

            // Queue the task for processing
            uploadQueueManager.queueTask(task, request.sessionId());
            log.info("Task queued successfully: taskId={}, displayName={}", task.getId(), request.displayName());

            return task;
        } catch (IOException e) {
            log.error("IO error during queueFileUpload for {}: {}", request.displayName(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error during queueFileUpload for {}: {}", request.displayName(), e.getMessage(), e);
            throw new IOException("Failed to queue file upload: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ResourceFileResponse completeFileUpload(Long taskId, String actor) throws IOException {
        log.info("Starting completeFileUpload: taskId={}, actor={}", taskId, actor);
        UploadTaskRecord task = uploadTaskRecordRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Upload task not found: " + taskId));

        if (!task.getSession().getUploader().equals(actor)) {
            throw new IllegalArgumentException("You do not have permission to access this upload task");
        }

        if (task.getStatus() == UploadTaskStatus.FAILED || task.getStatus() == UploadTaskStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot complete upload task with status: " + task.getStatus());
        }

        if (task.getResourceFileId() != null) {
            log.info("Task {} already has resourceFileId={}, returning existing resource", taskId, task.getResourceFileId());
            ResourceFile existingFile = resourceFileRepository.findById(task.getResourceFileId())
                    .orElseThrow(() -> new IllegalStateException("Resource file record not found: " + task.getResourceFileId()));
            return ResourceFileResponse.from(existingFile);
        }

        Folder folder = requireFolder(task.getFolderId());
        log.debug("Target folder: id={}, name={}", folder.getId(), folder.getName());

        // Get temp file path
        Path tempFilePath = Paths.get(chunkedUploadService.getTempUploadDir(), 
                task.getSession().getSessionId() + "_" + task.getId() + ".tmp");
        log.debug("Temp file path: {}", tempFilePath);

        if (!Files.exists(tempFilePath)) {
            log.error("Temp file missing for taskId={}: {}", taskId, tempFilePath);
            throw new IllegalStateException("Uploaded file not found in temp location: " + tempFilePath);
        }

        long tempFileSize = Files.size(tempFilePath);
        log.debug("Temp file exists with size: {} bytes", tempFileSize);

        // Create folder directory structure
        Path folderPath = uploadDir.resolve(folderRelativePath(folder));
        log.debug("Creating folder structure at: {}", folderPath);
        Files.createDirectories(folderPath);

        // Generate unique storage filename
        String safeName = safeFileName(task.getDisplayName());
        String storageFileName = UUID.randomUUID() + "-" + safeName;
        Path targetPath = folderPath.resolve(storageFileName);
        log.debug("Target path: {}", targetPath);

        // Move file to final location
        try {
            log.info("Moving temp file to final location for taskId={}: {} -> {}", taskId, tempFilePath, targetPath);
            Files.move(tempFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File moved successfully for taskId={}", taskId);
        } catch (IOException e) {
            log.error("Failed to move file for taskId={}: {}", taskId, e.getMessage(), e);
            throw new IOException("Failed to move uploaded file from temp location to final location: " + e.getMessage(), e);
        }

        verifyStorageFile(targetPath, safeName);
        log.debug("Storage file verified for taskId={}", taskId);

        // Create resource file from the uploaded task
        ResourceFile resourceFile = new ResourceFile();
        resourceFile.setTitle(task.getDisplayName());
        resourceFile.setDescription(task.getDescription());
        resourceFile.setCategory(task.getCategory() != null ? task.getCategory() : "bulk");
        resourceFile.setTags(normalizeTags(task.getTags()));
        resourceFile.setVisibility(task.getVisibility() != null ? task.getVisibility() : ResourceVisibility.HIDDEN);
        resourceFile.setFileName(safeName);
        resourceFile.setFileType("application/octet-stream");
        resourceFile.setFileSize(task.getFileSize());
        resourceFile.setStorageFileName(storageFileName);
        resourceFile.setFilePath(targetPath.toString());
        resourceFile.setFolder(folder);
        resourceFile.setUploader(requireActor(actor));

        // Extract content for search indexing
        String extractedContent = fileContentExtractor.extract(targetPath, safeName);
        resourceFile.setContentText(extractedContent);

        ResourceFile saved = resourceFileRepository.save(resourceFile);
        log.info("Resource file saved for taskId={}, resourceFileId={}, path={}", taskId, saved.getId(), targetPath);

        // Update task with resource file ID and status
        task.setResourceFileId(saved.getId());
        task.setStatus(UploadTaskStatus.SUCCESS);
        uploadTaskRecordRepository.save(task);
        log.info("Task updated: taskId={}, status=SUCCESS, resourceFileId={}", taskId, saved.getId());

        try {
            resourceSearchService.index(saved, extractedContent);
        } catch (Exception ex) {
            log.warn("Failed to index resource {} to Elasticsearch: {}", saved.getId(), ex.getMessage());
        }

        log.info("completeFileUpload finished successfully for taskId={}", taskId);
        return ResourceFileResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UploadSessionResponse getUploadSessionStatus(String sessionId, String actor) {
        UploadSession session = uploadQueueManager.getSessionStatus(sessionId);

        if (!session.getUploader().equals(actor) && !authService.isPrivilegedUser(actor)) {
            throw new IllegalArgumentException("You do not have permission to access this session");
        }

        List<UploadTaskRecord> tasks = uploadTaskRecordRepository.findBySessionId(session.getId());

        var taskResponses = tasks.stream()
                .map(t -> new UploadTaskResponse(
                        t.getId(),
                        t.getClientPath(),
                        t.getDisplayName(),
                        t.getStatus(),
                        t.getFileSize(),
                        t.getUploadedBytes(),
                        t.getProgressPercentage(),
                        t.getResourceFileId(),
                        t.getErrorMessage(),
                        t.getRetryCount()
                ))
                .toList();

        return new UploadSessionResponse(
                session.getSessionId(),
                session.getStatus(),
                session.getTotalFiles(),
                session.getUploadedFiles(),
                session.getFailedFiles(),
                session.getTotalBytes(),
                session.getUploadedBytes(),
                session.getProgressPercentage(),
                session.getErrorMessage(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getExpiresAt(),
                taskResponses
        );
    }

    @Override
    @Transactional
    public void pauseUploadSession(String sessionId, String actor) {
        UploadSession session = uploadQueueManager.getSessionStatus(sessionId);

        if (!session.getUploader().equals(actor)) {
            throw new IllegalArgumentException("You do not have permission to pause this session");
        }

        uploadQueueManager.pauseSession(sessionId);
        auditService.record(AuditAction.UPLOAD_RESOURCE, actor, "Paused upload session " + sessionId);
    }

    @Override
    @Transactional
    public void resumeUploadSession(String sessionId, String actor) {
        UploadSession session = uploadQueueManager.getSessionStatus(sessionId);

        if (!session.getUploader().equals(actor)) {
            throw new IllegalArgumentException("You do not have permission to resume this session");
        }

        uploadQueueManager.resumeSession(sessionId);
        auditService.record(AuditAction.UPLOAD_RESOURCE, actor, "Resumed upload session " + sessionId);
    }

    @Override
    public void completeUploadSession(String sessionId, String actor) {
        UploadSession session = uploadQueueManager.getSessionStatus(sessionId);

        if (!session.getUploader().equals(actor)) {
            throw new IllegalArgumentException("You do not have permission to complete this session");
        }

        // Immediately start async processing; do not block
        uploadQueueManager.resumeSession(sessionId);
        log.info("Upload session {} started async processing. Frontend should poll status endpoint.", sessionId);
    }

    @Override
    @Transactional
    public void cancelUploadSession(String sessionId, String actor) {
        UploadSession session = uploadQueueManager.getSessionStatus(sessionId);

        if (!session.getUploader().equals(actor)) {
            throw new IllegalArgumentException("You do not have permission to cancel this session");
        }

        uploadQueueManager.cancelSession(sessionId);
        auditService.record(AuditAction.UPLOAD_RESOURCE, actor, "Cancelled upload session " + sessionId);
    }

    // Helper record class for upload task response (inner class)
}
