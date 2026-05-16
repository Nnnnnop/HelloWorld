package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.dto.BulkFolderUploadManifest;
import com.example.polyusigwebsite.dto.ResourceFileResponse;
import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.dto.ResourceSearchResponse;
import com.example.polyusigwebsite.dto.ResourceUpdateRequest;
import com.example.polyusigwebsite.dto.ResourceUploadRequest;
import com.example.polyusigwebsite.dto.InitializeUploadRequest;
import com.example.polyusigwebsite.dto.UploadFileRequest;
import com.example.polyusigwebsite.dto.UploadSessionResponse;
import com.example.polyusigwebsite.entity.UploadSession;
import com.example.polyusigwebsite.entity.UploadTaskRecord;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ResourceFileService {
    ResourceFileResponse upload(MultipartFile file, ResourceUploadRequest metadata, String actor);

    void uploadBulk(BulkFolderUploadManifest manifest, List<MultipartFile> multipartFiles, String actor) throws IOException;

    // New session-based upload methods
    UploadSession initializeUploadSession(InitializeUploadRequest request, String actor);

    UploadTaskRecord queueFileUpload(UploadFileRequest request, MultipartFile file, String actor) throws IOException;

    ResourceFileResponse completeFileUpload(Long taskId, String actor) throws IOException;

    void completeUploadSession(String sessionId, String actor);

    UploadSessionResponse getUploadSessionStatus(String sessionId, String actor);

    void pauseUploadSession(String sessionId, String actor);

    void resumeUploadSession(String sessionId, String actor);

    void cancelUploadSession(String sessionId, String actor);

    ResourceDownload download(Long id, String actor);

    ResourceDownload downloadZip(List<Long> ids, String actor) throws IOException;

    ResourceDownload downloadFolder(Long folderId, String actor) throws IOException;

    ResourceDownload preview(Long id, String actor);

    ResourceFileResponse detail(Long id, String keyword, String actor);

    ResourceSearchResponse search(ResourceSearchRequest request, String actor);

    List<ResourceFileResponse> listAll(String actor);

    ResourceFileResponse update(Long id, ResourceUpdateRequest request);

    void delete(Long id);

    record ResourceDownload(String originalFileName, Resource resource, String contentType) {}
}
