package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.dto.*;
import com.example.polyusigwebsite.entity.RoleType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface AuthService {
    UserResponse register(RegisterRequest request);

    UserResponse login(LoginRequest request, HttpServletRequest httpServletRequest);

    UserResponse currentUser();

    void logout(HttpServletRequest httpServletRequest);

    List<UserResponse> pendingUsers();

    UserResponse approve(ApprovalRequest request, String adminActor);

    UserResponse updateRole(RoleUpdateRequest request, String adminActor);

    /**
     * ADMIN-only lookup for changing site roles ({@link #updateRole}). Query must be non-blank (&ge; 2 chars after trim).
     */
    List<UserResponse> searchUsersForRoleManagement(String query);

    String createPasswordResetToken(PasswordResetRequest request);

    void resetPassword(PasswordResetConfirmRequest request);

    void processOAuthLogin(String email, String displayName, String provider, String providerUserId);

    boolean isPrivilegedUser(String username);

    /**
     * Site-wide {@code ADMIN}, or approved site {@link com.example.polyusigwebsite.entity.RoleType#MEMBER}.
     * Used for resources labeled {@code Member: Level1} in the UI.
     */
    boolean isApprovedMember(String username);

    RoleType roleOf(String username);
}
