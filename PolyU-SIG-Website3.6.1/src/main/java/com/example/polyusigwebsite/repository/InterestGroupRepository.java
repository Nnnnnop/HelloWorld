package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.InterestGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterestGroupRepository extends JpaRepository<InterestGroup, Long> {

    List<InterestGroup> findByActiveTrueAndRecruitingTrueOrderBySortOrderAsc();

    List<InterestGroup> findAllByOrderBySortOrderAsc();
}
