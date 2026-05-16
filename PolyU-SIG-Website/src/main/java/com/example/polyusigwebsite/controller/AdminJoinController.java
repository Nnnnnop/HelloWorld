package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.AdminMemberTierRequest;
import com.example.polyusigwebsite.dto.GroupMemberAdminResponse;
import com.example.polyusigwebsite.dto.InterestGroupAdminRequest;
import com.example.polyusigwebsite.dto.InterestGroupResponse;
import com.example.polyusigwebsite.dto.JoinApplicationResponse;
import com.example.polyusigwebsite.dto.MemberLookupResponse;
import com.example.polyusigwebsite.dto.ReviewJoinApplicationRequest;
import com.example.polyusigwebsite.entity.MemberSiteTier;
import com.example.polyusigwebsite.security.SecurityUtils;
import com.example.polyusigwebsite.service.JoinService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

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
    public ResponseEntity<List<InterestGroupResponse>> allGroups(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(joinService.listAllGroupsAdmin(q));
    }

    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<List<GroupMemberAdminResponse>> membersOfGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(joinService.listMembersForGroup(groupId));
    }

    @DeleteMapping("/groups/{groupId}/memberships/{membershipId}")
    public ResponseEntity<Void> removeMembership(@PathVariable Long groupId, @PathVariable Long membershipId) {
        String admin = securityUtils.currentUsernameOrNull();
        joinService.removeGroupMembership(groupId, membershipId, admin != null ? admin : "admin");
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/members/site-tier")
    public ResponseEntity<Void> setMemberSiteTier(@Valid @RequestBody AdminMemberTierRequest body) {
        String admin = securityUtils.currentUsernameOrNull();
        joinService.adminSetMemberSiteTier(body.userId(), body.memberSiteTier(), admin != null ? admin : "admin");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/members/lookup")
    public ResponseEntity<MemberLookupResponse> lookupMember(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String memberSiteTier
    ) {
        MemberSiteTier tier = parseOptionalMemberSiteTier(memberSiteTier);
        String u = username != null ? username.trim() : "";
        return ResponseEntity.ok(joinService.lookupMembers(u.isBlank() ? null : u, tier));
    }

    private static MemberSiteTier parseOptionalMemberSiteTier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return MemberSiteTier.valueOf(t);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("memberSiteTier must be L1 or L2.");
        }
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
