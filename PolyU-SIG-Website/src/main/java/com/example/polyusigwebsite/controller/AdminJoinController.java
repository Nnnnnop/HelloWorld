package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.InterestGroupAdminRequest;
import com.example.polyusigwebsite.dto.InterestGroupResponse;
import com.example.polyusigwebsite.dto.JoinApplicationResponse;
import com.example.polyusigwebsite.dto.ReviewJoinApplicationRequest;
import com.example.polyusigwebsite.security.SecurityUtils;
import com.example.polyusigwebsite.service.JoinService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/join")
@PreAuthorize("hasRole('ADMIN')")
public class AdminJoinController {

    private final JoinService joinService;
    private final SecurityUtils securityUtils;

    public AdminJoinController(JoinService joinService, SecurityUtils securityUtils) {
        this.joinService = joinService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/groups")
    public ResponseEntity<List<InterestGroupResponse>> allGroups() {
        return ResponseEntity.ok(joinService.listAllGroupsAdmin());
    }

    @PostMapping("/groups")
    public ResponseEntity<InterestGroupResponse> createGroup(@Valid @RequestBody InterestGroupAdminRequest request) {
        String admin = securityUtils.currentUsernameOrNull();
        return ResponseEntity.ok(joinService.createGroup(request, admin != null ? admin : "admin"));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<InterestGroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody InterestGroupAdminRequest request
    ) {
        String admin = securityUtils.currentUsernameOrNull();
        return ResponseEntity.ok(joinService.updateGroup(id, request, admin != null ? admin : "admin"));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deactivateGroup(@PathVariable Long id) {
        String admin = securityUtils.currentUsernameOrNull();
        joinService.deactivateGroup(id, admin != null ? admin : "admin");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/applications")
    public ResponseEntity<List<JoinApplicationResponse>> applications(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(joinService.listApplicationsForAdmin(status));
    }

    @PostMapping("/applications/{id}/review")
    public ResponseEntity<JoinApplicationResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewJoinApplicationRequest request
    ) {
        String admin = securityUtils.currentUsernameOrNull();
        return ResponseEntity.ok(
                joinService.reviewApplication(id, request, admin != null ? admin : "admin")
        );
    }
}
