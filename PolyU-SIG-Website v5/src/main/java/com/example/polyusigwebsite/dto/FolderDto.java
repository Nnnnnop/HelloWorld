package com.example.polyusigwebsite.dto;

import java.util.List;

public record FolderDto(Long id, String name, Long parentId, String visibility, List<FolderDto> children) {
}