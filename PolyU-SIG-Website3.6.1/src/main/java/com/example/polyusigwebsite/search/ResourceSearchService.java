package com.example.polyusigwebsite.search;

import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.entity.ResourceFile;

import java.util.List;
import java.util.Map;

public interface ResourceSearchService {
    void index(ResourceFile resourceFile, String extractedContent);

    void delete(Long id);

    SearchResult search(ResourceSearchRequest request);

    record SearchResult(List<Long> ids, Map<Long, String> highlights, String suggestion) {
    }
}
