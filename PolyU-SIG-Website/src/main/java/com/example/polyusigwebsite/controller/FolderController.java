package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.FolderDto;
import com.example.polyusigwebsite.service.FolderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<FolderDto> listFolders() {
        return folderService.listFolders();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public FolderDto createFolder(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String parentIdRaw = body.get("parentId");
        Long parentId = StringUtils.hasText(parentIdRaw) ? Long.valueOf(parentIdRaw) : null;
        return folderService.createFolder(name, parentId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public FolderDto updateFolder(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String parentRaw = body.get("parentId");
        Long parentId = StringUtils.hasText(parentRaw) ? Long.valueOf(parentRaw) : null;
        return folderService.updateFolder(id, name, parentId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void deleteFolder(@PathVariable("id") Long id) {
        folderService.deleteFolder(id);
    }
}