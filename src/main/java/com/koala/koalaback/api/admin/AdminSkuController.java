package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.service.SkuCsvImportService;
import com.koala.koalaback.domain.sku.service.SkuCsvTemplate;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/admin/api/v1/skus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSkuController {
    private final SkuService skuService;
    private final SkuCsvImportService skuCsvImportService;
    private final SkuCsvTemplate skuCsvTemplate;

    @GetMapping
    public ApiResponse<PageResponse<SkuDto.SummaryResponse>> getSkus(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(skuService.getAllSkus(pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkuDto.SummaryResponse> createSku(
            @Valid @RequestBody SkuDto.CreateRequest req) {
        return ApiResponse.ok(skuService.createSku(req));
    }

    @PutMapping("/{skuCode}")
    public ApiResponse<SkuDto.SummaryResponse> updateSku(
            @PathVariable String skuCode,
            @Valid @RequestBody SkuDto.UpdateRequest req) {
        return ApiResponse.ok(skuService.updateSku(skuCode, req));
    }

    @PatchMapping("/{skuCode}/publish")
    public ApiResponse<Void> publishSku(@PathVariable String skuCode) {
        skuService.publishSku(skuCode);
        return ApiResponse.ok();
    }

    @PatchMapping("/{skuCode}/discontinue")
    public ApiResponse<Void> discontinueSku(@PathVariable String skuCode) {
        skuService.discontinueSku(skuCode);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{skuCode}")
    public ApiResponse<Void> deleteSku(@PathVariable String skuCode) {
        skuService.deleteSku(skuCode);
        return ApiResponse.ok();
    }

    @GetMapping("/{skuCode}/media")
    public ApiResponse<List<SkuDto.MediaResponse>> getMedia(@PathVariable String skuCode) {
        return ApiResponse.ok(skuService.getMediaList(skuCode));
    }

    @PostMapping(value = "/{skuCode}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkuDto.MediaResponse> addMedia(
            @PathVariable String skuCode,
            @RequestPart("file") MultipartFile file,
            @RequestPart("meta") @Valid SkuDto.MediaAddRequest req) {
        return ApiResponse.ok(skuService.addMedia(skuCode, file, req));
    }

    @DeleteMapping("/{skuCode}/media/{mediaId}")
    public ApiResponse<Void> deleteMedia(
            @PathVariable String skuCode,
            @PathVariable Long mediaId) {
        skuService.deleteMedia(skuCode, mediaId);
        return ApiResponse.ok();
    }

    @PostMapping("/{skuCode}/360-frames")
    public ApiResponse<SkuDto.FrameListResponse> upload360Frames(
            @PathVariable String skuCode,
            @Valid @RequestBody java.util.List<SkuDto.FrameUploadItem> items) {
        return ApiResponse.ok(skuService.upload360Frames(skuCode, items));
    }

    @GetMapping("/{skuCode}/stock")
    public ApiResponse<SkuDto.StockResponse> getStock(@PathVariable String skuCode) {
        return ApiResponse.ok(skuService.getStock(skuCode));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkuCsvDto.ImportResult> bulkImport(
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(skuCsvImportService.importCsv(file));
    }

    @GetMapping("/bulk/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] body = skuCsvTemplate.build();

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("sku-bulk-template.csv", StandardCharsets.UTF_8)
                                .build().toString())
                .body(body);
    }
}
