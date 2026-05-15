package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.UserGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserGroupMembershipRepository extends JpaRepository<UserGroupMembership, Long> {

    List<UserGroupMembership> findByUser_IdOrderByCreatedAtDesc(Long userId);

    boolean existsByUser_IdAndInterestGroup_Id(Long userId, Long interestGroupId);
}
