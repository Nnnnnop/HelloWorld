package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.dto.ArchiveContentsResponse;
import com.example.polyusigwebsite.dto.BulkFolderUploadManifest;
import com.example.polyusigwebsite.dto.ResourceFileResponse;
import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.dto.ResourceSearchResponse;
import com.example.polyusigwebsite.dto.ResourceUpdateRequest;
import com.example.polyusigwebsite.dto.ResourceUploadRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ResourceFileService {
    ResourceFileResponse upload(MultipartFile file, ResourceUploadRequest metadata, String actor);

    void uploadBulk(BulkFolderUploadManifest manifest, List<MultipartFile> files, String actor) throws IOException;

    ResourceDownload download(Long id, String actor);

    ResourceDownload downloadZip(List<Long> ids, String actor) throws IOException;

    ResourceDownload preview(Long id, String actor);

    /** Lists non-hidden entries inside a .zip (same access rules as download). */
    ArchiveContentsResponse listArchiveContents(Long id, String actor);

    /** Streams one non-directory entry from a .zip (same access as parent resource); capped size for safety. */
    ResourceDownload streamArchiveEntry(Long id, String entryPath, String actor);

    ResourceFileResponse detail(Long id, String keyword, String actor);

    ResourceSearchResponse search(ResourceSearchRequest request, String actor);

    List<ResourceFileResponse> listAll(String actor);

    void addFavourite(long resourceId, String actor);

    void removeFavourite(long resourceId, String actor);

    List<ResourceFileResponse> listFavourites(String actor);

    List<Long> listFavouriteResourceIds(String actor);

    ResourceFileResponse update(Long id, ResourceUpdateRequest request);

    void delete(Long id);

    record ResourceDownload(String originalFileName, Resource resource, String contentType) {}
}
