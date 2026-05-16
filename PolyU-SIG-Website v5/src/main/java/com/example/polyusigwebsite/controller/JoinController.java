package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.GroupMembershipResponse;
import com.example.polyusigwebsite.dto.InterestGroupResponse;
import com.example.polyusigwebsite.dto.JoinApplicationRequest;
import com.example.polyusigwebsite.dto.JoinApplicationResponse;
import com.example.polyusigwebsite.security.SecurityUtils;
import com.example.polyusigwebsite.service.JoinService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/join")
public class JoinController {

    private final JoinService joinService;
    private final SecurityUtils securityUtils;

    public JoinController(JoinService joinService, SecurityUtils securityUtils) {
        this.joinService = joinService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/groups")
    public ResponseEntity<List<InterestGroupResponse>> recruitingGroups() {
        return ResponseEntity.ok(joinService.listRecruitingGroups());
    }

    @GetMapping("/applications/mine")
    public ResponseEntity<List<JoinApplicationResponse>> myApplications() {
        String u = securityUtils.currentUsernameOrNull();
        return ResponseEntity.ok(joinService.listMyApplications(u));
    }

    @GetMapping("/memberships/mine")
    public ResponseEntity<List<GroupMembershipResponse>> myMemberships() {
        String u = securityUtils.currentUsernameOrNull();
        return ResponseEntity.ok(joinService.listMyMemberships(u));
    }

    @PostMapping("/applications")
    public ResponseEntity<JoinApplicationResponse> submit(@Valid @RequestBody JoinApplicationRequest request) {
        String u = securityUtils.currentUsernameOrNull();
        return ResponseEntity.ok(joinService.submitApplication(u, request));
    }

    @DeleteMapping("/applications/{id}")
    public ResponseEntity<JoinApplicationResponse> withdraw(@PathVariable Long id) {
        String u = securityUtils.currentUsernameOrNull();
        return ResponseEntity.ok(joinService.withdrawApplication(u, id));
    }
}
