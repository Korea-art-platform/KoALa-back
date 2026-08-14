package com.koala.koalaback.domain.banner.repository;

import com.koala.koalaback.domain.banner.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    Optional<Banner> findByBannerCode(String bannerCode);

    @Query("""
        SELECT b FROM Banner b
        WHERE b.bannerType = :bannerType
          AND b.isActive = true
          AND b.deletedAt IS NULL
          AND (b.visibleFrom IS NULL OR b.visibleFrom <= :now)
          AND (b.visibleTo   IS NULL OR b.visibleTo   >= :now)
        ORDER BY b.sortOrder ASC
        """)
    List<Banner> findVisibleByType(@Param("bannerType") String bannerType,
                                   @Param("now") LocalDateTime now);

    List<Banner> findByDeletedAtIsNullOrderBySortOrderAsc();
}
