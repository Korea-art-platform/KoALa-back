package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.store.dto.PartnerStoreDto;
import com.koala.koalaback.domain.store.service.PartnerStoreService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "어드민 · 입점 매장", description = "매장 등록·수정·정렬. 주소와 연락처는 암호화해 저장한다")
@RestController
@RequestMapping("/admin/api/v1/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStoreController {
    private final PartnerStoreService storeService;

    @Operation(summary = "매장 목록 (어드민)", description = "운영 중이 아닌 매장까지 보인다.")
    @GetMapping
    public ApiResponse<List<PartnerStoreDto.StoreResponse>> getAllStores() {
        return ApiResponse.ok(storeService.getAllStores());
    }

    @Operation(summary = "매장 상세")
    @GetMapping("/{storeCode}")
    public ApiResponse<PartnerStoreDto.StoreResponse> getStore(@PathVariable String storeCode) {
        return ApiResponse.ok(storeService.getStore(storeCode));
    }

    @Operation(summary = "매장 등록", description = """
            우편번호·주소·상세주소·전화 등은 AES-GCM 으로 암호화해 저장한다. DB 를 그대로
            들여다봐도 값이 읽히지 않는다.
            """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PartnerStoreDto.StoreResponse> createStore(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody PartnerStoreDto.CreateRequest req) {
        return ApiResponse.ok(storeService.createStore(adminId, req));
    }

    @Operation(summary = "매장 수정")
    @PutMapping("/{storeCode}")
    public ApiResponse<PartnerStoreDto.StoreResponse> updateStore(
            @PathVariable String storeCode,
            @Valid @RequestBody PartnerStoreDto.UpdateRequest req) {
        return ApiResponse.ok(storeService.updateStore(storeCode, req));
    }

    @Operation(summary = "매장 정렬 순서 변경", description = """
            보낸 매장 코드 순서대로 정렬값을 0 부터 다시 매긴다. 한 매장만 옮겨도 전체를
            보내야 한다 — 부분만 보내면 나머지와 순서가 어긋난다.
            """)
    @PostMapping("/reorder")
    public ApiResponse<Void> reorderStores(@Valid @RequestBody PartnerStoreDto.ReorderRequest req) {
        storeService.reorderStores(req.getStoreCodes());
        return ApiResponse.ok();
    }

    @Operation(summary = "매장 노출")
    @PatchMapping("/{storeCode}/activate")
    public ApiResponse<Void> activateStore(@PathVariable String storeCode) {
        storeService.activateStore(storeCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "매장 내림", description = "고객 목록에서 사라진다.")
    @PatchMapping("/{storeCode}/deactivate")
    public ApiResponse<Void> deactivateStore(@PathVariable String storeCode) {
        storeService.deactivateStore(storeCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "매장 삭제", description = "소프트 삭제다.")
    @DeleteMapping("/{storeCode}")
    public ApiResponse<Void> deleteStore(@PathVariable String storeCode) {
        storeService.deleteStore(storeCode);
        return ApiResponse.ok();
    }
}
