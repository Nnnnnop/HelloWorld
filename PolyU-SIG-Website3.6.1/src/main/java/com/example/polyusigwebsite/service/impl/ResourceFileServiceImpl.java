package com.example.polyusigwebsite.service.impl;

import com.example.polyusigwebsite.dto.ArchiveContentsResponse;
import com.example.polyusigwebsite.dto.ArchiveEntryResponse;
import com.example.polyusigwebsite.dto.BulkFolderUploadManifest;
import com.example.polyusigwebsite.dto.ResourceFileResponse;
import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.dto.ResourceSearchResponse;
import com.example.polyusigwebsite.dto.ResourceUpdateRequest;
import com.example.polyusigwebsite.dto.ResourceUploadRequest;
import com.example.polyusigwebsite.entity.Folder;
import com.example.polyusigwebsite.entity.ResourceFile;
import com.example.polyusigwebsite.entity.ResourceVisibility;
import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.entity.UserResourceFavourite;
import com.example.polyusigwebsite.entity.AuditAction;
import com.example.polyusigwebsite.repository.FolderRepository;
import com.example.polyusigwebsite.exception.ResourceFileNotFoundException;
import com.example.polyusigwebsite.repository.ResourceFileRepository;
import com.example.polyusigwebsite.repository.UserResourceFavouriteRepository;
import com.example.polyusigwebsite.repository.UserAccountRepository;
import com.example.polyusigwebsite.search.ResourceSearchService;
import com.example.polyusigwebsite.service.AuditService;
import com.example.polyusigwebsite.service.AuthService;
import com.example.polyusigwebsite.service.DocumentPreviewService;
import com.example.polyusigwebsite.service.FileContentExtractor;
import com.example.polyusigwebsite.service.ResourceFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ResourceFileServiceImpl implements ResourceFileService {
    private static final Logger log = LoggerFactory.getLogger(ResourceFileServiceImpl.class);

    private static final int ZIP_ARCHIVE_LIST_MAX_ENTRIES = 3000;

    private static final long ZIP_ENTRY_PREVIEW_MAX_BYTES = 15L * 1024 * 1024;

    private final ResourceFileRepository resourceFileRepository;
    private final FolderRepository folderRepository;
    private final UserResourceFavouriteRepository userResourceFavouriteRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final ResourceSearchService resourceSearchService;
    private final FileContentExtractor fileContentExtractor;
    private final DocumentPreviewService documentPreviewService;
    private final Path uploadDir;
    private final List<String> allowedExtensions;

    public ResourceFileServiceImpl(
            ResourceFileRepository resourceFileRepository,
            FolderRepository folderRepository,
            UserResourceFavouriteRepository userResourceFavouriteRepository,
            UserAccountRepository userAccountRepository,
            AuthService authService,
            AuditService auditService,
            ResourceSearchService resourceSearchService,
            FileContentExtractor fileContentExtractor,
            DocumentPreviewService documentPreviewService,
            @Value("${app.upload-dir:files}") String uploadDir,
            @Value("${sig.security.allowed-file-types:pdf,doc,docx,xls,xlsx,ppt,pptx,png,jpg,jpeg,gif,webp,bmp,svg,java,py,c,cpp,js,ts,r,m,go,rs,txt,md,json,xml,yaml,yml,sql,csv,ipynb,zip,rar,7z,tar,gz}") String allowedFileTypes
    ) throws IOException {
        this.resourceFileRepository = resourceFileRepository;
        this.folderRepository = folderRepository;
        this.userResourceFavouriteRepository = userResourceFavouriteRepository;
        this.userAccountRepository = userAccountRepository;
        this.authService = authService;
        this.auditService = auditService;
        this.resourceSearchService = resourceSearchService;
        this.fileContentExtractor = fileContentExtractor;
        this.documentPreviewService = documentPreviewService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
        this.allowedExtensions = List.of(allowedFileTypes.split(","))
                .stream().map(String::trim).filter(s -> !s.isBlank()).map(String::toLowerCase).toList();
        log.info("Allowed file extensions loaded: {}", this.allowedExtensions);
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
        String extractedContent = fileContentExtractor.extractForIndexing(targetPath, originalName);
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
            } catch (Exception ex) {
                log.error("Failed to upload file '{}': {}", part.getOriginalFilename(), ex.getMessage(), ex);
                // Continue with next file instead of failing the entire batch
            }
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void uploadBulkFileInOwnTransaction(MultipartFile part, BulkFolderUploadManifest.FileNode fn, Folder folder, String actor) throws IOException {
        String requestedName = StringUtils.hasText(fn.displayName())
                ? fn.displayName().trim()
                : StringUtils.cleanPath(part.getOriginalFilename());
        String safeName = safeFileName(requestedName);
        ResourceVisibility visibility = fn.minAccessLevel() != null ? fn.minAccessLevel() : ResourceVisibility.L1;

        Path folderPath = uploadDir.resolve(folderRelativePath(folder));
        Files.createDirectories(folderPath);

        String storageFileName = UUID.randomUUID() + "-" + safeName;
        Path targetPath = folderPath.resolve(storageFileName);
        
        // Explicitly close the input stream after copying
        try (var inputStream = part.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

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
        String extractedContent = fileContentExtractor.extractForIndexing(targetPath, safeName);
        resourceFile.setContentText(extractedContent);

        ResourceFile saved = resourceFileRepository.save(resourceFile);
        try {
            resourceSearchService.index(saved, extractedContent);
        } catch (Exception ex) {
            log.warn("Failed to index resource {} to Elasticsearch: {}", saved.getId(), ex.getMessage());
        }
        auditService.record(AuditAction.UPLOAD_RESOURCE, actor, "Bulk uploaded resource #" + saved.getId() + " (" + saved.getTitle() + ")");
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
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (ResourceFile file : files) {
                Path filePath = Paths.get(file.getFilePath());
                if (java.nio.file.Files.exists(filePath)) {
                    String filename = buildDownloadName(file);
                    zos.putNextEntry(new java.util.zip.ZipEntry(filename));
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
    public ArchiveContentsResponse listArchiveContents(Long id, String actor) {
        ResourceFile resourceFile = resourceFileRepository.findById(id)
                .orElseThrow(() -> new ResourceFileNotFoundException(id));
        assertCanAccess(resourceFile, actor);
        String fileName = resourceFile.getFileName();
        String ext = extractExtension(fileName == null ? "" : fileName).replace(".", "").toLowerCase();
        if (fileName == null || !"zip".equals(ext)) {
            throw new IllegalArgumentException("Archive listing is only available for .zip files.");
        }
        Path diskPath = Paths.get(resourceFile.getFilePath());
        if (!Files.isRegularFile(diskPath)) {
            throw new IllegalArgumentException("Stored file is missing on disk.");
        }

        try (ZipFile zf = new ZipFile(diskPath.toFile(), StandardCharsets.UTF_8)) {
            List<ZipEntry> sorted = zf.stream()
                    .filter(ze -> isSafeZipEntryPath(ze.getName()))
                    .filter(ze -> !ze.getName().startsWith("__MACOSX/"))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

            boolean truncated = sorted.size() > ZIP_ARCHIVE_LIST_MAX_ENTRIES;
            List<ArchiveEntryResponse> entries = new ArrayList<>(Math.min(sorted.size(), ZIP_ARCHIVE_LIST_MAX_ENTRIES));
            int limit = Math.min(sorted.size(), ZIP_ARCHIVE_LIST_MAX_ENTRIES);
            for (int i = 0; i < limit; i++) {
                ZipEntry ze = sorted.get(i);
                String rawName = ze.getName();
                boolean dir = ze.isDirectory() || rawName.endsWith("/");
                String pathStr = rawName;
                if (dir && pathStr.endsWith("/")) {
                    pathStr = pathStr.substring(0, pathStr.length() - 1);
                }
                long size = 0L;
                if (!dir) {
                    long u = ze.getSize();
                    if (u < 0) {
                        u = ze.getCompressedSize();
                    }
                    size = u >= 0 ? u : 0L;
                }
                entries.add(new ArchiveEntryResponse(pathStr, size, dir));
            }
            return new ArchiveContentsResponse(entries, truncated, entries.size());
        } catch (IOException ex) {
            log.warn("Failed to read ZIP resource id={}: {}", id, ex.getMessage());
            throw new IllegalArgumentException("Could not read ZIP file (invalid or corrupted archive).");
        }
    }

    private static boolean isSafeZipEntryPath(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.contains("..")) {
            return false;
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            return false;
        }
        for (String seg : name.split("/")) {
            if ("..".equals(seg)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceDownload streamArchiveEntry(Long id, String entryPath, String actor) {
        ResourceFile zipRf = resourceFileRepository.findById(id)
                .orElseThrow(() -> new ResourceFileNotFoundException(id));
        assertCanAccess(zipRf, actor);
        String fn = zipRf.getFileName();
        String zipExt = extractExtension(fn == null ? "" : fn).replace(".", "").toLowerCase(Locale.ROOT);
        if (fn == null || !"zip".equals(zipExt)) {
            throw new IllegalArgumentException("Archive entry streaming is only available for .zip resources.");
        }
        String normalized = normalizeClientZipEntryPath(entryPath);
        Path diskPath = Paths.get(zipRf.getFilePath());
        if (!Files.isRegularFile(diskPath)) {
            throw new IllegalArgumentException("Stored file is missing on disk.");
        }

        String innerBaseName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;

        try (ZipFile zf = new ZipFile(diskPath.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry ze = zf.getEntry(normalized);
            if (ze == null) {
                throw new IllegalArgumentException("Entry not found in archive.");
            }
            if (ze.isDirectory() || normalized.endsWith("/")) {
                throw new IllegalArgumentException("Cannot preview a directory.");
            }
            long declared = ze.getSize();
            if (declared >= 0 && declared > ZIP_ENTRY_PREVIEW_MAX_BYTES) {
                throw new IllegalArgumentException("Entry is too large to preview (max 15 MB). Download the ZIP instead.");
            }
            byte[] bytes;
            try (InputStream in = zf.getInputStream(ze)) {
                bytes = readZipEntryLimited(in, declared, ZIP_ENTRY_PREVIEW_MAX_BYTES);
            }
            String mime = guessMimeForArchiveInnerName(innerBaseName);
            auditService.record(AuditAction.DOWNLOAD_RESOURCE, actor,
                    "Previewed ZIP entry #" + id + " path=" + normalized);
            return new ResourceDownload(innerBaseName, new ByteArrayResource(bytes), mime);
        } catch (IOException ex) {
            log.warn("ZIP entry read failed id={} path={}: {}", id, normalized, ex.getMessage());
            throw new IllegalArgumentException("Could not read archive entry.");
        }
    }

    private static String normalizeClientZipEntryPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Entry path is required.");
        }
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (!isSafeZipEntryPath(p)) {
            throw new IllegalArgumentException("Invalid entry path.");
        }
        return p;
    }

    private static byte[] readZipEntryLimited(InputStream in, long declaredSize, long maxBytes) throws IOException {
        if (declaredSize >= 0 && declaredSize > maxBytes) {
            throw new IllegalArgumentException("Entry is too large to preview.");
        }
        int initialCap = 8192;
        if (declaredSize >= 0 && declaredSize <= Integer.MAX_VALUE) {
            initialCap = (int) Math.min(declaredSize, maxBytes);
            if (initialCap <= 0) {
                initialCap = 8192;
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(initialCap);
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalArgumentException("Entry is too large to preview (max 15 MB).");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static String guessMimeForArchiveInnerName(String innerFileName) {
        int dot = innerFileName.lastIndexOf('.');
        if (dot < 0 || dot == innerFileName.length() - 1) {
            return "application/octet-stream";
        }
        String innerExt = innerFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (innerExt) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "txt", "csv", "log" -> "text/plain; charset=UTF-8";
            case "json" -> "application/json; charset=UTF-8";
            case "md", "markdown" -> "text/markdown; charset=UTF-8";
            case "xml" -> "application/xml; charset=UTF-8";
            case "html", "htm" -> "text/html; charset=UTF-8";
            case "css" -> "text/css; charset=UTF-8";
            case "js" -> "text/javascript; charset=UTF-8";
            case "ts" -> "text/plain; charset=UTF-8";
            case "yml", "yaml" -> "application/yaml; charset=UTF-8";
            default -> "application/octet-stream";
        };
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
        if (normalizedKeyword != null) {
            validateKeywordLength(normalizedKeyword);
        }
        validateUploadDateRange(request.uploadDateFrom(), request.uploadDateTo());

        boolean keywordPresent = normalizedKeyword != null;
        boolean anyCriteria =
                keywordPresent
                || normalizedFileType != null
                || normalizedCategory != null
                || normalizedUploader != null
                || request.uploadDateFrom() != null
                || request.uploadDateTo() != null;
        if (!anyCriteria) {
            return new ResourceSearchResponse(List.of(), null);
        }

        if (keywordPresent) {
            try {
                ResourceSearchService.SearchResult result = resourceSearchService.search(request);
                if (!result.ids().isEmpty()) {
                    Map<Long, ResourceFile> fileMap = resourceFileRepository.findAllById(result.ids())
                            .stream().collect(java.util.stream.Collectors.toMap(ResourceFile::getId, f -> f));
                    return new ResourceSearchResponse(
                            result.ids().stream()
                                    .map(fileMap::get)
                                    .filter(java.util.Objects::nonNull)
                                    .filter(file -> canAccess(file, actor))
                                    .filter(file -> matchesUploadDateRange(request.uploadDateFrom(), request.uploadDateTo(), file))
                                    .map(file -> ResourceFileResponse.from(file, result.highlights().get(file.getId())))
                                    .toList(),
                            result.suggestion()
                    );
                }
            } catch (Exception ignored) {
                // fallback to DB search below
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
                .filter(file -> matchesUploadDateRange(request.uploadDateFrom(), request.uploadDateTo(), file))
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
    public void addFavourite(long resourceId, String actor) {
        UserAccount user = requireActor(actor);
        ResourceFile file = resourceFileRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceFileNotFoundException(resourceId));
        assertCanAccess(file, actor);
        if (userResourceFavouriteRepository.existsByUser_IdAndResourceFile_Id(user.getId(), resourceId)) {
            return;
        }
        UserResourceFavourite row = new UserResourceFavourite();
        row.setUser(user);
        row.setResourceFile(file);
        userResourceFavouriteRepository.save(row);
    }

    @Override
    @Transactional
    public void removeFavourite(long resourceId, String actor) {
        UserAccount user = requireActor(actor);
        userResourceFavouriteRepository.deleteByUser_IdAndResourceFile_Id(user.getId(), resourceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceFileResponse> listFavourites(String actor) {
        UserAccount user = requireActor(actor);
        List<UserResourceFavourite> favourites = userResourceFavouriteRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        if (favourites.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = favourites.stream()
                .map(f -> f.getResourceFile().getId())
                .toList();
        Map<Long, ResourceFile> fileMap = resourceFileRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(ResourceFile::getId, f -> f));
        List<ResourceFileResponse> out = new ArrayList<>();
        for (Long id : orderedIds) {
            ResourceFile file = fileMap.get(id);
            if (file != null && canAccess(file, actor)) {
                out.add(ResourceFileResponse.from(file));
            }
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listFavouriteResourceIds(String actor) {
        UserAccount user = requireActor(actor);
        return userResourceFavouriteRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(f -> f.getResourceFile().getId())
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
        if (file.getVisibility() == ResourceVisibility.L1) {
            return true;
        }
        if (file.getVisibility() == ResourceVisibility.L2) {
            return authService.isApprovedMember(actor);
        }
        if (file.getVisibility() == ResourceVisibility.L3) {
            return authService.isPrivilegedUser(actor);
        }
        if (file.getVisibility() == ResourceVisibility.HIDDEN) {
            if (actor == null || actor.isBlank()) {
                return false;
            }
            return authService.roleOf(actor) == RoleType.ADMIN
                    || file.getUploader().getUsername().equals(actor);
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
        String fileName = file.getFileName();
        if (fileName != null && fileName.toLowerCase().endsWith(".zip")) {
            return null;
        }
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

    private void validateUploadDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("uploadDateFrom must be on or before uploadDateTo.");
        }
    }

    /** Stored upload time uses Asia/Hong_Kong wall clock; compare by calendar date only. */
    private boolean matchesUploadDateRange(LocalDate from, LocalDate to, ResourceFile file) {
        if (from == null && to == null) {
            return true;
        }
        java.time.LocalDateTime t = file.getUploadTime();
        if (t == null) {
            return false;
        }
        LocalDate d = t.toLocalDate();
        if (from != null && d.isBefore(from)) {
            return false;
        }
        if (to != null && d.isAfter(to)) {
            return false;
        }
        return true;
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
}
