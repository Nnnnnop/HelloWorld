package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.ResourceFile;
import com.example.polyusigwebsite.entity.ResourceVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResourceFileRepository extends JpaRepository<ResourceFile, Long> {
    @Query("""
            SELECT f FROM ResourceFile f
            JOIN FETCH f.uploader
            """)
    List<ResourceFile> findAllWithUploader();

    List<ResourceFile> findByFolderId(Long folderId);

    @Query("""
            SELECT f FROM ResourceFile f
            WHERE (:keyword IS NULL OR LOWER(f.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(f.category) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:fileType IS NULL OR LOWER(f.fileType) LIKE LOWER(CONCAT('%', :fileType, '%')))
              AND (:category IS NULL OR LOWER(f.category) = LOWER(:category))
              AND (:uploader IS NULL OR LOWER(f.uploader.username) = LOWER(:uploader))
              AND (:isPrivileged = TRUE OR f.visibility = :publicVisibility)
            ORDER BY f.uploadTime DESC
            """)
    List<ResourceFile> search(
            @Param("keyword") String keyword,
            @Param("fileType") String fileType,
            @Param("category") String category,
            @Param("uploader") String uploader,
            @Param("isPrivileged") boolean isPrivileged,
            @Param("publicVisibility") ResourceVisibility publicVisibility
    );

    @Query("""
            SELECT f FROM ResourceFile f
            WHERE (:isPrivileged = TRUE OR f.visibility = :publicVisibility)
            ORDER BY f.uploadTime DESC
            """)
    List<ResourceFile> findAccessibleResources(
            @Param("isPrivileged") boolean isPrivileged,
            @Param("publicVisibility") ResourceVisibility publicVisibility
    );
}
