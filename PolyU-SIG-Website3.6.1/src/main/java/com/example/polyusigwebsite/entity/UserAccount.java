package com.example.polyusigwebsite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType role = RoleType.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING;

    @Column(nullable = false)
    private String provider = "LOCAL";

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_site_tier", nullable = false, length = 10)
    private MemberSiteTier memberSiteTier;

    private LocalDateTime approvedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (role == null) {
            role = RoleType.STUDENT;
        }
        if (memberSiteTier == null) {
            memberSiteTier = role == RoleType.STUDENT ? MemberSiteTier.L1 : MemberSiteTier.L2;
        }
        if (status == null) {
            status = UserStatus.PENDING;
        }
        if (provider == null || provider.isBlank()) {
            provider = "LOCAL";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public RoleType getRole() {
        return role;
    }

    public void setRole(RoleType role) {
        this.role = role;
    }

    public MemberSiteTier getMemberSiteTier() {
        return memberSiteTier;
    }

    public void setMemberSiteTier(MemberSiteTier memberSiteTier) {
        this.memberSiteTier = memberSiteTier;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    /** Human-readable site access tier for admins (Member L1 vs L2, Admin, Student). */
    public String getSiteAccessLevelLabel() {
        if (role == RoleType.ADMIN) {
            return "Administrator";
        }
        if (role == RoleType.MEMBER) {
            MemberSiteTier t = memberSiteTier != null ? memberSiteTier : MemberSiteTier.L1;
            return t == MemberSiteTier.L2 ? "Level 2 (Member)" : "Level 1 (Member)";
        }
        return "Student";
    }
}
