package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.SitePostRequest;
import com.example.polyusigwebsite.dto.SitePostResponse;
import com.example.polyusigwebsite.entity.AuditAction;
import com.example.polyusigwebsite.entity.PostType;
import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.SitePost;
import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.repository.SitePostRepository;
import com.example.polyusigwebsite.repository.UserAccountRepository;
import com.example.polyusigwebsite.security.SecurityUtils;
import com.example.polyusigwebsite.service.AuditService;
import com.example.polyusigwebsite.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/posts")
public class SitePostController {

    private final SitePostRepository sitePostRepository;
    private final UserAccountRepository userAccountRepository;
    private final SecurityUtils securityUtils;
    private final AuthService authService;
    private final AuditService auditService;

    public SitePostController(
            SitePostRepository sitePostRepository,
            UserAccountRepository userAccountRepository,
            SecurityUtils securityUtils,
            AuthService authService,
            AuditService auditService
    ) {
        this.sitePostRepository = sitePostRepository;
        this.userAccountRepository = userAccountRepository;
        this.securityUtils = securityUtils;
        this.authService = authService;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<SitePostResponse>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean published,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        PostType postType = parseType(type);
        String actor = securityUtils.currentUsernameOrNull();
        boolean privileged = authService.isPrivilegedUser(actor);
        Boolean effectivePublished = privileged ? published : Boolean.TRUE;
        List<SitePostResponse> response = sitePostRepository
                .listPosts(postType, effectivePublished, PageRequest.of(0, limit))
                .stream()
                .map(SitePostResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SitePostResponse> detail(@PathVariable Long id) {
        SitePost post = sitePostRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        String actor = securityUtils.currentUsernameOrNull();
        if (!post.isPublished() && !authService.isPrivilegedUser(actor)) {
            throw new IllegalArgumentException("Post is not published.");
        }
        return ResponseEntity.ok(SitePostResponse.from(post));
    }

    @PostMapping
    public ResponseEntity<SitePostResponse> create(@Valid @RequestBody SitePostRequest request) {
        assertAdmin();
        UserAccount author = requireCurrentUser();
        SitePost post = new SitePost();
        applyRequest(post, request);
        post.setTypeSequence(nextTypeSequence(post.getType()));
        post.setAuthor(author);
        SitePost saved = sitePostRepository.save(post);
        auditService.record(AuditAction.CREATE_POST, author.getUsername(), "Created post #" + saved.getId());
        SitePost responsePost = sitePostRepository.findByIdWithAuthor(saved.getId()).orElse(saved);
        return ResponseEntity.ok(SitePostResponse.from(responsePost));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SitePostResponse> update(@PathVariable Long id, @Valid @RequestBody SitePostRequest request) {
        assertAdmin();
        SitePost post = sitePostRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        PostType oldType = post.getType();
        applyRequest(post, request);
        if (oldType != post.getType()) {
            post.setTypeSequence(nextTypeSequence(post.getType()));
        }
        SitePost saved = sitePostRepository.save(post);
        auditService.record(AuditAction.UPDATE_POST, securityUtils.currentUsernameOrNull(), "Updated post #" + saved.getId());
        SitePost responsePost = sitePostRepository.findByIdWithAuthor(saved.getId()).orElse(saved);
        return ResponseEntity.ok(SitePostResponse.from(responsePost));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assertAdmin();
        SitePost post = sitePostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        sitePostRepository.delete(post);
        auditService.record(AuditAction.DELETE_POST, securityUtils.currentUsernameOrNull(), "Deleted post #" + id);
        return ResponseEntity.noContent().build();
    }

    private void applyRequest(SitePost post, SitePostRequest request) {
        PostType type = request.type();
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.setSummary(request.summary() == null ? "" : request.summary().trim());
        post.setType(type);
        if (type == PostType.EVENT) {
            post.setEventStartAt(request.eventStartAt());
            post.setEventEndAt(request.eventEndAt());
            post.setOrganizer(trimToNull(request.organizer()));
            post.setEventTimeLabel(trimToNull(request.eventTimeLabel()));
            post.setVenue(trimToNull(request.venue()));
            post.setEventCategory(trimToNull(request.eventCategory()));
        } else {
            post.setEventStartAt(null);
            post.setEventEndAt(null);
            post.setOrganizer(null);
            post.setEventTimeLabel(null);
            post.setVenue(null);
            post.setEventCategory(null);
        }
        if (type == PostType.NEWS) {
            if (request.newsDate() == null) {
                throw new IllegalArgumentException("News date is required for news posts.");
            }
            List<Long> imageIds = sanitizeNewsImageIds(request.newsImageIds());
            post.setNewsDate(request.newsDate());
            post.setNewsImageIds(joinNewsImageIds(imageIds));
        } else {
            post.setNewsDate(null);
            post.setNewsImageIds("");
        }
        post.setPublished(request.published());
        post.setPinned(request.pinned());
    }

    private List<Long> sanitizeNewsImageIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            if (!normalized.contains(id)) {
                normalized.add(id);
            }
        }
        if (normalized.size() > 10) {
            throw new IllegalArgumentException("At most 10 photos are allowed for one news post.");
        }
        return normalized;
    }

    private String joinNewsImageIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int nextTypeSequence(PostType type) {
        Integer max = sitePostRepository.maxTypeSequenceByType(type);
        return (max == null ? 0 : max) + 1;
    }

    private UserAccount requireCurrentUser() {
        var current = authService.currentUser();
        String username = current == null ? null : current.username();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Login required");
        }
        return userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    private void assertAdmin() {
        var current = authService.currentUser();
        if (current == null) {
            throw new IllegalArgumentException("Login required");
        }
        if (RoleType.valueOf(current.role()) != RoleType.ADMIN) {
            throw new IllegalArgumentException("Admin role required");
        }
    }

    private PostType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PostType.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unknown post type: " + value);
        }
    }
}
