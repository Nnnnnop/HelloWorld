package com.example.polyusigwebsite.service;

import com.example.polyusigwebsite.dto.*;

import java.util.List;

public interface JoinService {

    List<InterestGroupResponse> listRecruitingGroups();

    List<JoinApplicationResponse> listMyApplications(String username);

    List<GroupMembershipResponse> listMyMemberships(String username);

    JoinApplicationResponse submitApplication(String username, JoinApplicationRequest request);

    JoinApplicationResponse withdrawApplication(String username, Long applicationId);

    List<InterestGroupResponse> listAllGroupsAdmin();

    InterestGroupResponse createGroup(InterestGroupAdminRequest request, String adminUsername);

    InterestGroupResponse updateGroup(Long id, InterestGroupAdminRequest request, String adminUsername);

    void deactivateGroup(Long id, String adminUsername);

    List<JoinApplicationResponse> listApplicationsForAdmin(String statusFilter);

    JoinApplicationResponse reviewApplication(Long applicationId, ReviewJoinApplicationRequest request, String adminUsername);
}
