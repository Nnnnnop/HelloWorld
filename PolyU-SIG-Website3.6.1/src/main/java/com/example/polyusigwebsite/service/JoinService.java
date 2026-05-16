package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.dto.*;

import com.example.polyusigwebsite.entity.MemberSiteTier;

import java.util.List;

public interface JoinService {

    List<InterestGroupResponse> listRecruitingGroups();

    List<JoinApplicationResponse> listMyApplications(String username);

    List<GroupMembershipResponse> listMyMemberships(String username);

    JoinApplicationResponse submitApplication(String username, JoinApplicationRequest request);

    JoinApplicationResponse withdrawApplication(String username, Long applicationId);

    List<InterestGroupResponse> listAllGroupsAdmin(String nameQuery);

    List<GroupMemberAdminResponse> listMembersForGroup(Long groupId);

    void removeGroupMembership(Long groupId, Long membershipId, String adminUsername);

    void adminSetMemberSiteTier(Long userId, MemberSiteTier tier, String adminUsername);

    /**
     * Admin member finder: optional {@code username} (NetID, exact or partial) and/or {@code memberSiteTier} (L1/L2).
     * At least one criterion is required.
     */
    MemberLookupResponse lookupMembers(String username, MemberSiteTier memberSiteTier);

    InterestGroupResponse createGroup(InterestGroupAdminRequest request, String adminUsername);

    InterestGroupResponse updateGroup(Long id, InterestGroupAdminRequest request, String adminUsername);

    void deactivateGroup(Long id, String adminUsername);

    List<JoinApplicationResponse> listApplicationsForAdmin(String statusFilter);

    JoinApplicationResponse reviewApplication(Long applicationId, ReviewJoinApplicationRequest request, String adminUsername);
}
