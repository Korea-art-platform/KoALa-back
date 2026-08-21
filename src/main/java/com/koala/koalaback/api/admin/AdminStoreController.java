package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.store.dto.PartnerStoreDto;
import com.koala.koalaback.domain.store.service.PartnerStoreService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api/v1/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStoreController {
    private final PartnerStoreService storeService;

    @GetMapping
    public ApiResponse<List<PartnerStoreDto.StoreResponse>> getAllStores() {
        return ApiResponse.ok(storeService.getAllStores());
    }

    @GetMapping("/{storeCode}")
    public ApiResponse<PartnerStoreDto.StoreResponse> getStore(@PathVariable String storeCode) {
        return ApiResponse.ok(storeService.getStore(storeCode));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PartnerStoreDto.StoreResponse> createStore(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody PartnerStoreDto.CreateRequest req) {
        return ApiResponse.ok(storeService.createStore(adminId, req));
    }

    @PutMapping("/{storeCode}")
    public ApiResponse<PartnerStoreDto.StoreResponse> updateStore(
            @PathVariable String storeCode,
            @Valid @RequestBody PartnerStoreDto.UpdateRequest req) {
        return ApiResponse.ok(storeService.updateStore(storeCode, req));
    }

    @PostMapping("/reorder")
    public ApiResponse<Void> reorderStores(@Valid @RequestBody PartnerStoreDto.ReorderRequest req) {
        storeService.reorderStores(req.getStoreCodes());
        return ApiResponse.ok();
    }

    @PatchMapping("/{storeCode}/activate")
    public ApiResponse<Void> activateStore(@PathVariable String storeCode) {
        storeService.activateStore(storeCode);
        return ApiResponse.ok();
    }

    @PatchMapping("/{storeCode}/deactivate")
    public ApiResponse<Void> deactivateStore(@PathVariable String storeCode) {
        storeService.deactivateStore(storeCode);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{storeCode}")
    public ApiResponse<Void> deleteStore(@PathVariable String storeCode) {
        storeService.deleteStore(storeCode);
        return ApiResponse.ok();
    }
}
