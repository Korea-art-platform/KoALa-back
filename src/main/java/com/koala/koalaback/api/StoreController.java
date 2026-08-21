package com.koala.koalaback.api;

import com.koala.koalaback.domain.store.dto.PartnerStoreDto;
import com.koala.koalaback.domain.store.service.PartnerStoreService;
import com.koala.koalaback.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {
    private final PartnerStoreService storeService;

    @GetMapping
    public ApiResponse<List<PartnerStoreDto.StoreResponse>> getStores() {
        return ApiResponse.ok(storeService.getPublicStores());
    }
}
