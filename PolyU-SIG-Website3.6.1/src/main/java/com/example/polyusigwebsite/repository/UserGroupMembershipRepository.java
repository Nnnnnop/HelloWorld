package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.UserGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserGroupMembershipRepository extends JpaRepository<UserGroupMembership, Long> {

    Optional<UserGroupMembership> findByIdAndInterestGroup_Id(Long id, Long interestGroupId);

    List<UserGroupMembership> findByUser_IdOrderByCreatedAtDesc(Long userId);

    boolean existsByUser_IdAndInterestGroup_Id(Long userId, Long interestGroupId);

    @Query("""
            SELECT m FROM UserGroupMembership m
            JOIN FETCH m.user
            WHERE m.interestGroup.id = :groupId
            ORDER BY m.createdAt ASC
            """)
    List<UserGroupMembership> findByInterestGroup_IdWithUser(@Param("groupId") Long groupId);

    @Query("""
            SELECT m FROM UserGroupMembership m
            JOIN FETCH m.interestGroup
            WHERE m.user.id = :userId
            ORDER BY LOWER(m.interestGroup.name) ASC
            """)
    List<UserGroupMembership> findByUser_IdWithGroup(@Param("userId") Long userId);
}
