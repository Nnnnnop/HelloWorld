package com.example.polyusigwebsite.controller;

import com.example.polyusigwebsite.dto.*;
import com.example.polyusigwebsite.security.SecurityUtils;
import com.example.polyusigwebsite.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;

    public AuthController(AuthService authService, SecurityUtils securityUtils) {
        this.authService = authService;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authService.login(request, httpServletRequest));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest httpServletRequest) {
        authService.logout(httpServletRequest);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        UserResponse user = authService.currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending-users")
    public ResponseEntity<List<UserResponse>> pendingUsers() {
        return ResponseEntity.ok(authService.pendingUsers());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/approve")
    public ResponseEntity<UserResponse> approve(@Valid @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(authService.approve(request, securityUtils.currentUsernameOrNull()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/role")
    public ResponseEntity<UserResponse> updateRole(@Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(authService.updateRole(request, securityUtils.currentUsernameOrNull()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users/search")
    public ResponseEntity<List<UserResponse>> searchUsersForRoleManagement(@RequestParam("q") String q) {
        return ResponseEntity.ok(authService.searchUsersForRoleManagement(q));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> passwordResetRequest(@Valid @RequestBody PasswordResetRequest request) {
        String token = authService.createPasswordResetToken(request);
        return ResponseEntity.ok(Map.of("message", "Password reset token generated", "token", token));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, String>> passwordResetConfirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }

    @GetMapping("/oauth2/url")
    public ResponseEntity<Map<String, String>> oauth2Url() {
        return ResponseEntity.ok(Map.of("url", "/oauth2/authorization/sig"));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }
}
