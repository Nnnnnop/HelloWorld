package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.InterestGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterestGroupRepository extends JpaRepository<InterestGroup, Long> {

    @Query("""
            SELECT g FROM InterestGroup g
            WHERE g.active = true AND g.recruiting = true
            ORDER BY LOWER(g.name) ASC
            """)
    List<InterestGroup> findActiveRecruitingOrderByNameCaseInsensitive();

    @Query("""
            SELECT g FROM InterestGroup g
            ORDER BY LOWER(g.name) ASC
            """)
    List<InterestGroup> findAllOrderByNameCaseInsensitive();

    @Query("""
            SELECT g FROM InterestGroup g
            WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :namePart, '%'))
            ORDER BY LOWER(g.name) ASC
            """)
    List<InterestGroup> findByNamePartOrderByNameCaseInsensitive(@Param("namePart") String namePart);
}
