package com.koala.koalaback.domain.store.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.domain.store.dto.PartnerStoreDto;
import com.koala.koalaback.domain.store.entity.PartnerStore;
import com.koala.koalaback.domain.store.repository.PartnerStoreRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerStoreService {
    private final PartnerStoreRepository storeRepository;
    private final AdminService adminService;
    private final CodeGenerator codeGenerator;

    public List<PartnerStoreDto.StoreResponse> getPublicStores() {
        return storeRepository
                .findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc()
                .stream().map(PartnerStoreDto.StoreResponse::from).toList();
    }

    public List<PartnerStoreDto.StoreResponse> getAllStores() {
        return storeRepository
                .findByDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc()
                .stream().map(PartnerStoreDto.StoreResponse::from).toList();
    }

    public PartnerStoreDto.StoreResponse getStore(String storeCode) {
        return PartnerStoreDto.StoreResponse.from(getStoreByCode(storeCode));
    }

    @Transactional
    public PartnerStoreDto.StoreResponse createStore(Long adminId, PartnerStoreDto.CreateRequest req) {
        Admin admin = adminService.getAdminById(adminId);
        PartnerStore store = PartnerStore.builder()
                .storeCode(codeGenerator.generateCode())
                .name(req.getName())
                .zipCode(req.getZipCode())
                .address(req.getAddress())
                .addressDetail(req.getAddressDetail())
                .phone(req.getPhone())
                .phone2(req.getPhone2())
                .email(req.getEmail())
                .description(req.getDescription())
                .mapUrl(req.getMapUrl())
                .snsUrl(req.getSnsUrl())
                .imageUrl(req.getImageUrl())
                .sortOrder(req.getSortOrder())
                .createdByAdmin(admin)
                .build();
        return PartnerStoreDto.StoreResponse.from(storeRepository.save(store));
    }

    @Transactional
    public PartnerStoreDto.StoreResponse updateStore(String storeCode, PartnerStoreDto.UpdateRequest req) {
        PartnerStore store = getStoreByCode(storeCode);
        store.update(req.getName(), req.getZipCode(), req.getAddress(), req.getAddressDetail(),
                req.getPhone(), req.getPhone2(), req.getEmail(), req.getDescription(),
                req.getMapUrl(), req.getSnsUrl(), req.getImageUrl(), req.getSortOrder());
        return PartnerStoreDto.StoreResponse.from(store);
    }

    @Transactional
    public void reorderStores(List<String> storeCodes) {
        for (int i = 0; i < storeCodes.size(); i++) {
            getStoreByCode(storeCodes.get(i)).changeSortOrder(i);
        }
    }

    @Transactional
    public void activateStore(String storeCode) {
        getStoreByCode(storeCode).activate();
    }

    @Transactional
    public void deactivateStore(String storeCode) {
        getStoreByCode(storeCode).deactivate();
    }

    @Transactional
    public void deleteStore(String storeCode) {
        getStoreByCode(storeCode).softDelete();
    }

    private PartnerStore getStoreByCode(String storeCode) {
        return storeRepository.findByStoreCodeAndDeletedAtIsNull(storeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
