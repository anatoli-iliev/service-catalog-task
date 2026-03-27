package com.catalog.repository;

import com.catalog.domain.Release;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReleaseRepository extends JpaRepository<Release, Long> {

    @Query("SELECT r FROM Release r JOIN FETCH r.application WHERE r.externalReleaseId = :externalReleaseId")
    Optional<Release> findByExternalReleaseId(@Param("externalReleaseId") String externalReleaseId);

    @Query(value = """
            SELECT r FROM Release r JOIN FETCH r.application a
            WHERE a.externalApplicationId = :externalApplicationId
            ORDER BY r.versionMajor DESC, r.versionMinor DESC, r.versionPatch DESC,
                     CASE WHEN r.versionPrerelease IS NULL THEN 0 ELSE 1 END ASC,
                     r.versionPrereleaseSortKey DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM Release r
            WHERE r.application.externalApplicationId = :externalApplicationId
            """)
    Page<Release> findByApplicationExternalId(
            @Param("externalApplicationId") String externalApplicationId,
            Pageable pageable);
}
