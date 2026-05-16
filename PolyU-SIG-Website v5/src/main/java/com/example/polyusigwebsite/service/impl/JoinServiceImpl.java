package com.example.polyusigwebsite.service.impl;

import com.example.polyusigwebsite.dto.*;
import com.example.polyusigwebsite.entity.*;
import com.example.polyusigwebsite.repository.InterestGroupRepository;
import com.example.polyusigwebsite.repository.JoinApplicationRepository;
import com.example.polyusigwebsite.repository.UserAccountRepository;
import com.example.polyusigwebsite.repository.UserGroupMembershipRepository;
import com.example.polyusigwebsite.service.AuditService;
import com.example.polyusigwebsite.service.JoinService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class JoinServiceImpl implements JoinService {

    private final InterestGroupRepository interestGroupRepository;
    private final JoinApplicationRepository joinApplicationRepository;
    private final UserGroupMembershipRepository userGroupMembershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditService auditService;

    public JoinServiceImpl(
            InterestGroupRepository interestGroupRepository,
            JoinApplicationRepository joinApplicationRepository,
            UserGroupMembershipRepository userGroupMembershipRepository,
            UserAccountRepository userAccountRepository,
            AuditService auditService
    ) {
        this.interestGroupRepository = interestGroupRepository;
        this.joinApplicationRepository = joinApplicationRepository;
        this.userGroupMembershipRepository = userGroupMembershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterestGroupResponse> listRecruitingGroups() {
        return interestGroupRepository.findByActiveTrueAndRecruitingTrueOrderBySortOrderAsc().stream()
                .map(InterestGroupResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinApplicationResponse> listMyApplications(String username) {
        UserAccount user = requireUser(username);
        return joinApplicationRepository.findMineWithDetails(user.getId()).stream()
                .map(JoinApplicationResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMembershipResponse> listMyMemberships(String username) {
        UserAccount user = requireUser(username);
        return userGroupMembershipRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(GroupMembershipResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public JoinApplicationResponse submitApplication(String username, JoinApplicationRequest request) {
        UserAccount user = requireUser(username);
        if (user.getStatus() != UserStatus.APPROVED) {
            throw new IllegalArgumentException("Your account must be approved before you can apply to join a group.");
        }
        InterestGroup group = interestGroupRepository.findById(request.groupId())
                .orElseThrow(() -> new IllegalArgumentException("Interest group not found."));
        if (!group.isActive() || !group.isRecruiting()) {
            throw new IllegalArgumentException("This group is not accepting applications.");
        }
        if (userGroupMembershipRepository.existsByUser_IdAndInterestGroup_Id(user.getId(), group.getId())) {
            throw new IllegalArgumentException("You are already a member of this group.");
        }
        joinApplicationRepository
                .findByUser_IdAndInterestGroup_IdAndStatus(user.getId(), group.getId(), JoinApplicationStatus.PENDING)
                .ifPresent(a -> {
                    throw new IllegalArgumentException("You already have a pending application for this group.");
                });

        JoinApplication app = new JoinApplication();
        app.setUser(user);
        app.setInterestGroup(group);
        app.setMessage(request.message() != null ? request.message().trim() : null);
        app.setStatus(JoinApplicationStatus.PENDING);
        JoinApplication saved = joinApplicationRepository.save(app);
        auditService.record(
                AuditAction.SUBMIT_JOIN_APPLICATION,
                username,
                "Applied to group " + group.getName() + " (id=" + group.getId() + ")"
        );
        return JoinApplicationResponse.from(refetchApplication(saved.getId()));
    }

    @Override
    @Transactional
    public JoinApplicationResponse withdrawApplication(String username, Long applicationId) {
        UserAccount user = requireUser(username);
        JoinApplication app = joinApplicationRepository.findByIdWithDetails(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (!app.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only withdraw your own applications.");
        }
        if (app.getStatus() != JoinApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending applications can be withdrawn.");
        }
        app.setStatus(JoinApplicationStatus.WITHDRAWN);
        joinApplicationRepository.save(app);
        auditService.record(
                AuditAction.WITHDRAW_JOIN_APPLICATION,
                username,
                "Withdrew join application id=" + applicationId
        );
        return JoinApplicationResponse.from(refetchApplication(applicationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterestGroupResponse> listAllGroupsAdmin() {
        return interestGroupRepository.findAllByOrderBySortOrderAsc().stream()
                .map(InterestGroupResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public InterestGroupResponse createGroup(InterestGroupAdminRequest request, String adminUsername) {
        InterestGroup g = new InterestGroup();
        applyGroupFields(g, request);
        InterestGroup saved = interestGroupRepository.save(g);
        auditService.record(
                AuditAction.CREATE_INTEREST_GROUP,
                adminUsername,
                "Created interest group " + saved.getName() + " (id=" + saved.getId() + ")"
        );
        return InterestGroupResponse.from(saved);
    }

    @Override
    @Transactional
    public InterestGroupResponse updateGroup(Long id, InterestGroupAdminRequest request, String adminUsername) {
        InterestGroup g = interestGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interest group not found."));
        applyGroupFields(g, request);
        InterestGroup saved = interestGroupRepository.save(g);
        auditService.record(
                AuditAction.UPDATE_INTEREST_GROUP,
                adminUsername,
                "Updated interest group " + saved.getName() + " (id=" + saved.getId() + ")"
        );
        return InterestGroupResponse.from(saved);
    }

    @Override
    @Transactional
    public void deactivateGroup(Long id, String adminUsername) {
        InterestGroup g = interestGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interest group not found."));
        g.setActive(false);
        g.setRecruiting(false);
        interestGroupRepository.save(g);
        auditService.record(
                AuditAction.DELETE_INTEREST_GROUP,
                adminUsername,
                "Deactivated interest group " + g.getName() + " (id=" + g.getId() + ")"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinApplicationResponse> listApplicationsForAdmin(String statusFilter) {
        String key = statusFilter == null || statusFilter.isBlank()
                ? "PENDING"
                : statusFilter.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(key)) {
            return joinApplicationRepository.findAllWithDetails().stream()
                    .map(JoinApplicationResponse::from)
                    .toList();
        }
        JoinApplicationStatus st;
        try {
            st = JoinApplicationStatus.valueOf(key);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid status filter. Use PENDING, APPROVED, REJECTED, WITHDRAWN, or ALL."
            );
        }
        return joinApplicationRepository.findByStatusWithDetails(st).stream()
                .map(JoinApplicationResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public JoinApplicationResponse reviewApplication(Long applicationId, ReviewJoinApplicationRequest request, String adminUsername) {
        JoinApplication app = joinApplicationRepository.findByIdWithDetails(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (app.getStatus() != JoinApplicationStatus.PENDING) {
            throw new IllegalArgumentException("This application is no longer pending.");
        }
        UserAccount applicant = app.getUser();
        InterestGroup group = app.getInterestGroup();
        boolean approved = Boolean.TRUE.equals(request.approved());
        LocalDateTime now = LocalDateTime.now();
        app.setReviewedAt(now);
        app.setReviewedBy(adminUsername);
        if (approved) {
            app.setStatus(JoinApplicationStatus.APPROVED);
            if (!userGroupMembershipRepository.existsByUser_IdAndInterestGroup_Id(applicant.getId(), group.getId())) {
                UserGroupMembership m = new UserGroupMembership();
                m.setUser(applicant);
                m.setInterestGroup(group);
                userGroupMembershipRepository.save(m);
            }
            if (applicant.getStatus() == UserStatus.APPROVED && applicant.getRole() == RoleType.GUEST) {
                applicant.setRole(RoleType.MEMBER);
                userAccountRepository.save(applicant);
            }
            auditService.record(
                    AuditAction.APPROVE_JOIN_APPLICATION,
                    adminUsername,
                    "Approved join application id=" + applicationId + " user=" + applicant.getUsername()
                            + " group=" + group.getName()
            );
        } else {
            app.setStatus(JoinApplicationStatus.REJECTED);
            auditService.record(
                    AuditAction.REJECT_JOIN_APPLICATION,
                    adminUsername,
                    "Rejected join application id=" + applicationId + " user=" + applicant.getUsername()
            );
        }
        joinApplicationRepository.save(app);
        return JoinApplicationResponse.from(refetchApplication(applicationId));
    }

    private JoinApplication refetchApplication(Long id) {
        return joinApplicationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalStateException("Application missing after save."));
    }

    private void applyGroupFields(InterestGroup g, InterestGroupAdminRequest request) {
        g.setName(request.name().trim());
        g.setDescription(request.description() != null ? request.description().trim() : null);
        g.setRecruiting(request.recruiting());
        g.setActive(request.active());
        g.setSortOrder(request.sortOrder());
    }

    private UserAccount requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Not authenticated.");
        }
        return userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }
}
