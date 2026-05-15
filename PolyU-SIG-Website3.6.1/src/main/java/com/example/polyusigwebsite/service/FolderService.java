package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.dto.FolderDto;
import com.example.polyusigwebsite.entity.Folder;
import com.example.polyusigwebsite.entity.ResourceFile;
import com.example.polyusigwebsite.entity.ResourceVisibility;
import com.example.polyusigwebsite.repository.FolderRepository;
import com.example.polyusigwebsite.repository.ResourceFileRepository;
import com.example.polyusigwebsite.search.ResourceSearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final ResourceFileRepository resourceFileRepository;
    private final ResourceSearchService resourceSearchService;

    public FolderService(FolderRepository folderRepository,
                         ResourceFileRepository resourceFileRepository,
                         ResourceSearchService resourceSearchService) {
        this.folderRepository = folderRepository;
        this.resourceFileRepository = resourceFileRepository;
        this.resourceSearchService = resourceSearchService;
    }

    public List<FolderDto> listFolders() {
        List<Folder> all = folderRepository.findAll();

        Map<Long, FolderDto> dtoById = new HashMap<>();
        for (Folder folder : all) {
            Long parentId = folder.getParent() != null ? folder.getParent().getId() : null;
            String visibility = folder.getVisibility() != null ? folder.getVisibility().name() : ResourceVisibility.HIDDEN.name();
            dtoById.put(folder.getId(), new FolderDto(folder.getId(), folder.getName(), parentId, visibility, new ArrayList<>()));
        }

        List<FolderDto> roots = new ArrayList<>();
        for (FolderDto dto : dtoById.values()) {
            if (dto.parentId() == null) {
                roots.add(dto);
                continue;
            }
            FolderDto parent = dtoById.get(dto.parentId());
            if (parent == null) {
                // orphaned parent reference; treat as root
                roots.add(dto);
                continue;
            }
            parent.children().add(dto);
        }

        return roots;
    }

    public FolderDto createFolder(String name, Long parentId, ResourceVisibility visibility) {
        String cleanedName = name == null ? "" : name.trim();
        if (!StringUtils.hasText(cleanedName)) {
            throw new IllegalArgumentException("Folder name cannot be empty");
        }
        Folder folder = new Folder();
        folder.setName(cleanedName);
        folder.setVisibility(visibility != null ? visibility : ResourceVisibility.HIDDEN);
        if (parentId != null) {
            Folder parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
            folder.setParent(parent);
        }
        Folder saved = folderRepository.save(folder);

        Long savedParentId = saved.getParent() != null ? saved.getParent().getId() : null;
        return new FolderDto(saved.getId(), saved.getName(), savedParentId, saved.getVisibility().name(), List.of());
    }

    public FolderDto updateFolder(Long id, String name, Long parentId, ResourceVisibility visibility) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        String cleanedName = name == null ? "" : name.trim();
        if (!StringUtils.hasText(cleanedName)) {
            throw new IllegalArgumentException("Folder name cannot be empty");
        }

        folder.setName(cleanedName);
        if (visibility != null) {
            folder.setVisibility(visibility);
        }

        if (parentId != null) {
            if (parentId.equals(folder.getId())) {
                throw new IllegalArgumentException("Folder cannot be its own parent");
            }
            Folder parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
            if (isDescendant(folder, parent)) {
                throw new IllegalArgumentException("Cannot move folder into its own subfolder");
            }
            folder.setParent(parent);
        } else {
            folder.setParent(null);
        }

        Folder saved = folderRepository.save(folder);
        Long updatedParentId = saved.getParent() != null ? saved.getParent().getId() : null;
        return new FolderDto(saved.getId(), saved.getName(), updatedParentId, saved.getVisibility().name(), List.of());
    }

    public void deleteFolder(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Folder id is required");
        }
        if (id.equals(1L)) {
            throw new IllegalArgumentException("Cannot delete root folder");
        }
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        deleteFolderRecursive(folder.getId());
    }

    private boolean isDescendant(Folder source, Folder target) {
        Folder current = target;
        while (current != null) {
            if (current.getId().equals(source.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void deleteFolderRecursive(Long folderId) {
        Folder current = folderRepository.findById(folderId).orElse(null);
        if (current == null) {
            return;
        }

        // delete child folders first
        for (Folder child : folderRepository.findByParentId(folderId)) {
            deleteFolderRecursive(child.getId());
        }

        // delete files belonging to this folder
        deleteFilesInFolder(folderId);

        folderRepository.deleteById(folderId);
    }

    private void deleteFilesInFolder(Long folderId) {
        List<ResourceFile> files = resourceFileRepository.findByFolderId(folderId);
        for (ResourceFile file : files) {
            try {
                Path path = Paths.get(file.getFilePath());
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
            try {
                resourceSearchService.delete(file.getId());
            } catch (Exception ignored) {
            }
        }
        if (!files.isEmpty()) {
            resourceFileRepository.deleteAll(files);
        }
    }
}
