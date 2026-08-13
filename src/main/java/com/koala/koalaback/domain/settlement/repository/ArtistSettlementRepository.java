package com.koala.koalaback.domain.settlement.repository;

import com.koala.koalaback.domain.settlement.entity.ArtistSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtistSettlementRepository extends JpaRepository<ArtistSettlement, Long> {

    List<ArtistSettlement> findByPeriodYm(String periodYm);

    Optional<ArtistSettlement> findByArtistIdAndPeriodYm(Long artistId, String periodYm);

    boolean existsByPeriodYm(String periodYm);

    /** 지급 이력 — 작가 상세에서 본다 */
    List<ArtistSettlement> findByArtistIdOrderByPeriodYmDesc(Long artistId);
}
