package com.example.polyusigwebsite.service.impl;

import com.example.polyusigwebsite.dto.*;
import com.example.polyusigwebsite.entity.AuditAction;
import com.example.polyusigwebsite.entity.PasswordResetToken;
import com.example.polyusigwebsite.entity.MemberSiteTier;
import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.entity.UserStatus;
import com.example.polyusigwebsite.repository.PasswordResetTokenRepository;
import com.example.polyusigwebsite.repository.UserAccountRepository;
import com.example.polyusigwebsite.security.CustomUserPrincipal;
import com.example.polyusigwebsite.service.AuditService;
import com.example.polyusigwebsite.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    public AuthServiceImpl(
            UserAccountRepository userAccountRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            AuditService auditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userAccountRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (userAccountRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email already exists.");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProvider("LOCAL");
        user.setRole(RoleType.STUDENT);
        user.setStatus(UserStatus.PENDING);
        UserAccount saved = userAccountRepository.save(user);
        auditService.record(AuditAction.REGISTER, saved.getUsername(), "New user registered and waiting for approval");
        return UserResponse.from(saved);
    }

    @Override
    public UserResponse login(LoginRequest request, HttpServletRequest httpServletRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);

        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        auditService.record(AuditAction.LOGIN, principal.getUsername(), "Local login success");
        return UserResponse.from(principal.getUser());
    }

    @Override
    public UserResponse currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserPrincipal customUserPrincipal) {
            /** Always reload: {@code memberSiteTier} / role updates after login must reflect on {@code /me}. */
            return userAccountRepository.findByUsername(customUserPrincipal.getUsername())
                    .map(UserResponse::from)
                    .orElseGet(() -> UserResponse.from(customUserPrincipal.getUser()));
        }
        if (principal instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null) {
                return userAccountRepository.findByEmail(email.toLowerCase())
                        .map(UserResponse::from)
                        .orElse(null);
            }
        }

        return userAccountRepository.findByUsername(authentication.getName())
                .map(UserResponse::from)
                .orElse(null);
    }

    @Override
    public void logout(HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public List<UserResponse> pendingUsers() {
        return userAccountRepository.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public UserResponse approve(ApprovalRequest request, String adminActor) {
        UserAccount user = userAccountRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.userId()));
        if (Boolean.TRUE.equals(request.approved())) {
            user.setStatus(UserStatus.APPROVED);
            if (user.getRole() == RoleType.STUDENT) {
                user.setRole(RoleType.MEMBER);
                user.setMemberSiteTier(MemberSiteTier.L2);
            }
            user.setApprovedAt(LocalDateTime.now());
            auditService.record(AuditAction.APPROVE_MEMBER, adminActor, "Approved user " + user.getUsername());
        } else {
            user.setStatus(UserStatus.REJECTED);
            auditService.record(AuditAction.REJECT_MEMBER, adminActor, "Rejected user " + user.getUsername());
        }
        return UserResponse.from(userAccountRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateRole(RoleUpdateRequest request, String adminActor) {
        if (adminActor == null || adminActor.isBlank()) {
            throw new IllegalArgumentException("Login required.");
        }
        UserAccount actor = userAccountRepository.findByUsername(adminActor.trim())
                .orElseThrow(() -> new IllegalArgumentException("Admin account not found."));
        if (actor.getRole() != RoleType.ADMIN) {
            throw new IllegalArgumentException("Only site administrators may change roles.");
        }

        UserAccount user = userAccountRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.userId()));
        RoleType oldRole = user.getRole();
        RoleType newRole = request.role();
        if (oldRole == newRole) {
            return UserResponse.from(user);
        }

        if (oldRole == RoleType.ADMIN && newRole != RoleType.ADMIN) {
            if (actor.getId().equals(user.getId())) {
                throw new IllegalArgumentException("You cannot remove your own administrator role.");
            }
            if (userAccountRepository.countByRole(RoleType.ADMIN) <= 1) {
                throw new IllegalArgumentException("Cannot remove the last site administrator.");
            }
        }

        boolean approvedForAdminGrant = false;
        if (newRole == RoleType.ADMIN) {
            if (user.getStatus() == UserStatus.REJECTED) {
                throw new IllegalArgumentException("Cannot grant administrator role to a rejected account.");
            }
            if (user.getStatus() == UserStatus.PENDING) {
                user.setStatus(UserStatus.APPROVED);
                user.setApprovedAt(LocalDateTime.now());
                approvedForAdminGrant = true;
            }
        }

        user.setRole(newRole);
        UserAccount saved = userAccountRepository.save(user);
        String detail = "Changed role of " + saved.getUsername() + " from " + oldRole + " to " + newRole;
        if (approvedForAdminGrant) {
            detail += "; account approved as part of ADMIN grant";
        }
        auditService.record(AuditAction.UPDATE_ROLE, adminActor, detail);
        return UserResponse.from(saved);
    }

    @Override
    public List<UserResponse> searchUsersForRoleManagement(String query) {
        if (query == null) {
            return List.of();
        }
        String q = query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        return userAccountRepository.findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(q).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    public String createPasswordResetToken(PasswordResetRequest request) {
        UserAccount user = userAccountRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Email not found"));
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        passwordResetTokenRepository.save(token);
        return token.getToken();
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token expired or already used");
        }
        UserAccount user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);
    }

    @Override
    public void processOAuthLogin(String email, String displayName, String provider, String providerUserId) {
        if (email == null || email.isBlank()) {
            return;
        }
        UserAccount user = userAccountRepository.findByEmail(email.toLowerCase())
                .orElseGet(() -> {
                    UserAccount newUser = new UserAccount();
                    newUser.setEmail(email.toLowerCase());
                    newUser.setUsername(generateUsername(displayName, email));
                    newUser.setProvider(provider == null ? "OAUTH2" : provider);
                    newUser.setProviderUserId(providerUserId);
                    newUser.setRole(RoleType.STUDENT);
                    newUser.setStatus(UserStatus.PENDING);
                    return userAccountRepository.save(newUser);
                });

        if (user.getProviderUserId() == null && providerUserId != null) {
            user.setProviderUserId(providerUserId);
            user.setProvider(provider == null ? "OAUTH2" : provider);
            userAccountRepository.save(user);
        }
        auditService.record(AuditAction.OAUTH_LOGIN, user.getUsername(), "OAuth login callback");
    }

    @Override
    public boolean isPrivilegedUser(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userAccountRepository.findByUsername(username)
                .map(user -> user.getStatus() == UserStatus.APPROVED
                        && (user.getRole() == RoleType.ADMIN
                        || (user.getRole() == RoleType.MEMBER && user.getMemberSiteTier() == MemberSiteTier.L2)))
                .orElse(false);
    }

    @Override
    public boolean isApprovedMember(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userAccountRepository.findByUsername(username)
                .map(user -> user.getStatus() == UserStatus.APPROVED
                        && (user.getRole() == RoleType.ADMIN || user.getRole() == RoleType.MEMBER))
                .orElse(false);
    }

    @Override
    public RoleType roleOf(String username) {
        if (username == null || username.isBlank()) {
            return RoleType.STUDENT;
        }
        return userAccountRepository.findByUsername(username)
                .map(UserAccount::getRole)
                .orElse(RoleType.STUDENT);
    }

    private String generateUsername(String displayName, String email) {
        String candidate = displayName;
        if (candidate == null || candidate.isBlank()) {
            int index = email.indexOf("@");
            candidate = index > 0 ? email.substring(0, index) : email;
        }
        candidate = candidate.replaceAll("[^A-Za-z0-9_]", "_");
        if (candidate.isBlank()) {
            candidate = "user";
        }
        String base = candidate;
        int count = 1;
        while (userAccountRepository.findByUsername(candidate).isPresent()) {
            candidate = base + "_" + count++;
        }
        return candidate;
    }
}
