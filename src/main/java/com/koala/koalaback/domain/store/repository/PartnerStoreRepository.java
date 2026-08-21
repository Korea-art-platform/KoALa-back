package com.koala.koalaback.domain.store.repository;

import com.koala.koalaback.domain.store.entity.PartnerStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerStoreRepository extends JpaRepository<PartnerStore, Long> {

    List<PartnerStore> findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc();

    List<PartnerStore> findByDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc();

    Optional<PartnerStore> findByStoreCodeAndDeletedAtIsNull(String storeCode);
}
