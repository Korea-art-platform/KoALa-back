package com.koala.koalaback.domain.sku.repository;

import com.koala.koalaback.domain.sku.entity.SkuMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkuMediaRepository extends JpaRepository<SkuMedia, Long> {
    List<SkuMedia> findBySkuIdAndMediaRoleOrderByAngleDegreeAsc(Long skuId, String mediaRole);

    List<SkuMedia> findBySkuIdOrderByMediaRoleAscSortOrderAsc(Long skuId);

    Optional<SkuMedia> findBySkuIdAndIsPrimaryTrue(Long skuId);

    List<SkuMedia> findBySkuIdAndMediaRoleOrderBySortOrderAsc(Long skuId, String mediaRole);

    @Modifying
    @Query("DELETE FROM SkuMedia m WHERE m.sku.id = :skuId AND m.mediaRole = :mediaRole")
    void deleteBySkuIdAndMediaRole(@Param("skuId") Long skuId,
                                   @Param("mediaRole") String mediaRole);

    @Modifying
    @Query("DELETE FROM SkuMedia m WHERE m.sku.id = :skuId")
    void deleteAllBySkuId(@Param("skuId") Long skuId);
}
