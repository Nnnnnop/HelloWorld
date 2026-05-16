package com.example.polyusigwebsite.search.impl;

import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.entity.ResourceFile;
import com.example.polyusigwebsite.search.ResourceSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "sig.search.enabled", havingValue = "false", matchIfMissing = true)
public class NoopResourceSearchService implements ResourceSearchService {
    @Override
    public void index(ResourceFile resourceFile, String extractedContent) {
    }

    @Override
    public void delete(Long id) {
    }

    @Override
    public SearchResult search(ResourceSearchRequest request) {
        return new SearchResult(List.of(), Map.of(), null);
    }
}
