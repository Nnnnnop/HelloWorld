package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.UserResourceFavourite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserResourceFavouriteRepository extends JpaRepository<UserResourceFavourite, Long> {

    List<UserResourceFavourite> findByUser_IdOrderByCreatedAtDesc(long userId);

    Optional<UserResourceFavourite> findByUser_IdAndResourceFile_Id(long userId, long resourceFileId);

    boolean existsByUser_IdAndResourceFile_Id(long userId, long resourceFileId);

    void deleteByUser_IdAndResourceFile_Id(long userId, long resourceFileId);
}
