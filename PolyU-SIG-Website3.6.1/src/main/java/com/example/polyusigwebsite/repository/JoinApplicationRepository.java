package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.JoinApplication;
import com.example.polyusigwebsite.entity.JoinApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JoinApplicationRepository extends JpaRepository<JoinApplication, Long> {

    @Query("""
            SELECT DISTINCT ja FROM JoinApplication ja
            JOIN FETCH ja.user
            JOIN FETCH ja.interestGroup
            WHERE ja.user.id = :userId
            ORDER BY ja.createdAt DESC
            """)
    List<JoinApplication> findMineWithDetails(@Param("userId") Long userId);

    Optional<JoinApplication> findByUser_IdAndInterestGroup_IdAndStatus(
            Long userId,
            Long interestGroupId,
            JoinApplicationStatus status
    );

    @Query("""
            SELECT DISTINCT ja FROM JoinApplication ja
            JOIN FETCH ja.user
            JOIN FETCH ja.interestGroup
            WHERE ja.status = :status
            ORDER BY ja.createdAt ASC
            """)
    List<JoinApplication> findByStatusWithDetails(@Param("status") JoinApplicationStatus status);

    @Query("""
            SELECT DISTINCT ja FROM JoinApplication ja
            JOIN FETCH ja.user
            JOIN FETCH ja.interestGroup
            ORDER BY ja.createdAt DESC
            """)
    List<JoinApplication> findAllWithDetails();

    @Query("""
            SELECT ja FROM JoinApplication ja
            JOIN FETCH ja.user
            JOIN FETCH ja.interestGroup
            WHERE ja.id = :id
            """)
    Optional<JoinApplication> findByIdWithDetails(@Param("id") Long id);
}
