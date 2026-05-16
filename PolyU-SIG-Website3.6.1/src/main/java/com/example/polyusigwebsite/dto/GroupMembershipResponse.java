package com.example.polyusigwebsite.dto;

import com.example.polyusigwebsite.entity.UserGroupMembership;

import java.time.LocalDateTime;

public record GroupMembershipResponse(
        Long id,
        InterestGroupResponse group,
        LocalDateTime joinedAt
) {
    public static GroupMembershipResponse from(UserGroupMembership m) {
        return new GroupMembershipResponse(
                m.getId(),
                InterestGroupResponse.from(m.getInterestGroup()),
                m.getCreatedAt()
        );
    }
}
