package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.PostType;
import com.example.polyusigwebsite.entity.SitePost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SitePostRepository extends JpaRepository<SitePost, Long> {

    @Query("""
            SELECT p FROM SitePost p
            JOIN FETCH p.author
            WHERE (:type IS NULL OR p.type = :type)
              AND (:published IS NULL OR p.published = :published)
            ORDER BY p.pinned DESC, p.createdAt DESC
            """)
    List<SitePost> listPosts(
            @Param("type") PostType type,
            @Param("published") Boolean published,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM SitePost p
            JOIN FETCH p.author
            WHERE p.id = :id
            """)
    Optional<SitePost> findByIdWithAuthor(@Param("id") Long id);

    @Query("""
            SELECT COALESCE(MAX(p.typeSequence), 0) FROM SitePost p
            WHERE p.type = :type
            """)
    Integer maxTypeSequenceByType(@Param("type") PostType type);
}
