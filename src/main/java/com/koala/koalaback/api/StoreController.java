package com.koala.koalaback.api;

import com.koala.koalaback.domain.store.dto.PartnerStoreDto;
import com.koala.koalaback.domain.store.service.PartnerStoreService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "입점 매장", description = "직접 보고 고를 수 있는 곳")
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {
    private final PartnerStoreService storeService;

    @Operation(summary = "입점 매장 목록", description = """
            운영 중이고 삭제되지 않은 매장만, 관리자가 정한 정렬 순서대로 내려간다.
            """)
    @GetMapping
    public ApiResponse<List<PartnerStoreDto.StoreResponse>> getStores() {
        return ApiResponse.ok(storeService.getPublicStores());
    }
}
