package com.example.polyusigwebsite.search.impl;

import com.example.polyusigwebsite.dto.ResourceSearchRequest;
import com.example.polyusigwebsite.entity.ResourceFile;
import com.example.polyusigwebsite.search.ResourceSearchDocument;
import com.example.polyusigwebsite.search.ResourceSearchRepository;
import com.example.polyusigwebsite.search.ResourceSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

@Service
@ConditionalOnProperty(name = "sig.search.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchResourceSearchService implements ResourceSearchService {

    private final ResourceSearchRepository resourceSearchRepository;

    public ElasticsearchResourceSearchService(ResourceSearchRepository resourceSearchRepository) {
        this.resourceSearchRepository = resourceSearchRepository;
    }

    @Override
    public void index(ResourceFile resourceFile, String extractedContent) {
        String id = String.valueOf(resourceFile.getId());
        ResourceSearchDocument existing = resourceSearchRepository.findById(id).orElse(null);
        ResourceSearchDocument document = new ResourceSearchDocument();
        document.setId(id);
        document.setTitle(resourceFile.getTitle());
        document.setDescription(resourceFile.getDescription());
        document.setTags(resourceFile.getTags());
        document.setContent(extractedContent != null ? extractedContent : (existing != null ? existing.getContent() : ""));
        document.setCategory(resourceFile.getCategory());
        document.setFileType(resourceFile.getFileType());
        document.setUploader(resourceFile.getUploader().getUsername());
        document.setVisibility(resourceFile.getVisibility().name());
        // Keep null to avoid mapping conflicts from legacy indices.
        document.setUploadTime(null);
        resourceSearchRepository.save(document);
    }

    @Override
    public void delete(Long id) {
        resourceSearchRepository.deleteById(String.valueOf(id));
    }

    @Override
    public SearchResult search(ResourceSearchRequest request) {
        if (request == null || request.keyword() == null || request.keyword().isBlank()) {
            return new SearchResult(List.of(), Map.of(), null);
        }

        String keyword = request.keyword().toLowerCase();
        Map<Long, String> highlights = new HashMap<>();
        List<ResourceSearchDocument> docs = StreamSupport.stream(resourceSearchRepository.findAll().spliterator(), false)
                .toList();

        List<Long> ids = docs.stream()
                .filter(doc -> matchesFilter(request, doc))
                .filter(doc -> contains(keyword, doc))
                .sorted(Comparator.comparingInt((ResourceSearchDocument doc) -> score(keyword, doc)).reversed())
                .map(doc -> {
                    Long id = Long.parseLong(doc.getId());
                    highlights.put(id, buildHighlight(keyword, doc));
                    return id;
                })
                .toList();

        String suggestion = buildEnglishSuggestion(
                request.keyword(),
                docs.stream().filter(doc -> matchesFilter(request, doc)).toList()
        );
        return new SearchResult(ids, highlights, suggestion);
    }

    private boolean matchesFilter(ResourceSearchRequest request, ResourceSearchDocument doc) {
        return matches(request.category(), doc.getCategory())
                && matches(request.fileType(), doc.getFileType())
                && matches(request.uploader(), doc.getUploader());
    }

    private boolean matches(String filter, String value) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return value != null && value.equalsIgnoreCase(filter.trim());
    }

    private boolean contains(String keyword, ResourceSearchDocument doc) {
        return containsField(doc.getTitle(), keyword)
                || containsField(doc.getDescription(), keyword)
                || containsField(doc.getTags(), keyword)
                || containsField(doc.getContent(), keyword);
    }

    private int score(String keyword, ResourceSearchDocument doc) {
        int score = 0;
        if (containsField(doc.getTitle(), keyword)) {
            score += 3;
        }
        if (containsField(doc.getDescription(), keyword)) {
            score += 2;
        }
        if (containsField(doc.getTags(), keyword)) {
            score += 1;
        }
        if (containsField(doc.getContent(), keyword)) {
            score += 1;
        }
        return score;
    }

    private String buildHighlight(String keyword, ResourceSearchDocument doc) {
        if (containsField(doc.getTitle(), keyword)) {
            return highlight(doc.getTitle(), keyword);
        }
        if (containsField(doc.getDescription(), keyword)) {
            return highlight(doc.getDescription(), keyword);
        }
        if (containsField(doc.getTags(), keyword)) {
            return highlight(doc.getTags(), keyword);
        }
        return highlightSnippet(doc.getContent(), keyword);
    }

    private boolean containsField(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String highlight(String text, String keyword) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?i)" + java.util.regex.Pattern.quote(keyword), "<em>$0</em>");
    }

    private String highlightSnippet(String text, String keyword) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int index = lowerText.indexOf(lowerKeyword);
        if (index < 0) {
            return null;
        }

        int contextSize = 60;
        int start = Math.max(0, index - contextSize);
        int end = Math.min(text.length(), index + keyword.length() + contextSize);
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

    private String buildEnglishSuggestion(String keyword, List<ResourceSearchDocument> docs) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim().toLowerCase();
        if (!normalized.matches("[a-z]{3,}")) {
            return null;
        }

        Set<String> candidates = new HashSet<>();
        for (ResourceSearchDocument doc : docs) {
            collectEnglishTokens(candidates, doc.getTitle());
            collectEnglishTokens(candidates, doc.getDescription());
            collectEnglishTokens(candidates, doc.getTags());
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
}
