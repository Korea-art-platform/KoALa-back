package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.service.SkuCsvImportService;
import com.koala.koalaback.domain.sku.service.SkuCsvTemplate;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "어드민 · 상품/재고", description = "작품 등록·수정·공개, 미디어, 재고 조회, CSV 일괄 등록")
@RestController
@RequestMapping("/admin/api/v1/skus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSkuController {
    private final SkuService skuService;
    private final SkuCsvImportService skuCsvImportService;
    private final SkuCsvTemplate skuCsvTemplate;

    @Operation(summary = "작품 목록 (어드민)", description = """
            공개 목록과 달리 판매 중이 아닌 것까지 전부 보인다 — 미공개·품절·단종.
            관리자는 안 보이는 것을 봐야 고칠 수 있다.
            """)
    @GetMapping
    public ApiResponse<PageResponse<SkuDto.SummaryResponse>> getSkus(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(skuService.getAllSkus(pageable));
    }

    @Operation(summary = "작품 등록", description = """
            상품명과 슬러그는 입력하지 않는다. 모델·세부모델명·색상으로 서버가 만든다.
            사람이 적으면 같은 모델인데 표기가 갈린다. CSV 일괄 등록만 값을 직접 넘겨
            오므로 그때는 그대로 쓴다.

            슬러그는 영문명으로 만든다 — 한글로 만들면 주소창에서 퍼센트 인코딩되어
            읽을 수 없다. 같은 모델의 다른 색이 같은 슬러그를 만들 수 있어 뒤에 번호를
            붙인다. 그래도 겹치면 DUPLICATE_RESOURCE.

            대분류·소분류 코드, 에디션 번호, 가격을 검증한다. 할인가는 정가보다 클 수
            없다(같은 값은 할인 없음으로 허용).

            등록가는 부가세를 뺀 공급가액이다. 고객 화면에는 여기에 10% 를 더한 값이
            보인다. 원작처럼 면세로 표시된 분류에는 붙지 않는다.
            """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkuDto.SummaryResponse> createSku(
            @Valid @RequestBody SkuDto.CreateRequest req) {
        return ApiResponse.ok(skuService.createSku(req));
    }

    @Operation(summary = "작품 수정", description = """
            수정하면 이 상품의 360도 프레임 캐시를 비운다.
            """)
    @PutMapping("/{skuCode}")
    public ApiResponse<SkuDto.SummaryResponse> updateSku(
            @PathVariable String skuCode,
            @Valid @RequestBody SkuDto.UpdateRequest req) {
        return ApiResponse.ok(skuService.updateSku(skuCode, req));
    }

    @Operation(summary = "작품 공개", description = "공개해야 고객 목록에 나온다.")
    @PatchMapping("/{skuCode}/publish")
    public ApiResponse<Void> publishSku(@PathVariable String skuCode) {
        skuService.publishSku(skuCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "작품 단종", description = "고객 목록에서 내린다. 지우는 것이 아니라 상태만 바꾼다.")
    @PatchMapping("/{skuCode}/discontinue")
    public ApiResponse<Void> discontinueSku(@PathVariable String skuCode) {
        skuService.discontinueSku(skuCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "작품 삭제", description = """
            소프트 삭제다. 행을 지우지 않고 삭제 표시만 남긴다 — 지나간 주문이 이 상품을
            가리키고 있어, 실제로 지우면 그 주문의 상품 정보가 사라진다.
            """)
    @DeleteMapping("/{skuCode}")
    public ApiResponse<Void> deleteSku(@PathVariable String skuCode) {
        skuService.deleteSku(skuCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "미디어 목록", description = "역할(대표·상세 등)과 정렬 순서대로 돌려준다.")
    @GetMapping("/{skuCode}/media")
    public ApiResponse<List<SkuDto.MediaResponse>> getMedia(@PathVariable String skuCode) {
        return ApiResponse.ok(skuService.getMediaList(skuCode));
    }

    @Operation(summary = "미디어 등록", description = """
            파일(file)과 메타(meta)를 함께 보내는 multipart 요청이다. 업로드한 파일은
            S3 에 올라간다.
            """)
    @PostMapping(value = "/{skuCode}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkuDto.MediaResponse> addMedia(
            @PathVariable String skuCode,
            @RequestPart("file") MultipartFile file,
            @RequestPart("meta") @Valid SkuDto.MediaAddRequest req) {
        return ApiResponse.ok(skuService.addMedia(skuCode, file, req));
    }

    @Operation(summary = "미디어 삭제")
    @DeleteMapping("/{skuCode}/media/{mediaId}")
    public ApiResponse<Void> deleteMedia(
            @PathVariable String skuCode,
            @PathVariable Long mediaId) {
        skuService.deleteMedia(skuCode, mediaId);
        return ApiResponse.ok();
    }

    @Operation(summary = "360도 프레임 등록", description = """
            각도는 0 이상 360 미만이어야 한다. 360 을 허용하면 0 과 같은 자리에 두 장이
            겹친다. 벗어나면 INVALID_ANGLE_DEGREE.

            등록하면 이 상품의 프레임 캐시를 비운다.
            """)
    @PostMapping("/{skuCode}/360-frames")
    public ApiResponse<SkuDto.FrameListResponse> upload360Frames(
            @PathVariable String skuCode,
            @Valid @RequestBody java.util.List<SkuDto.FrameUploadItem> items) {
        return ApiResponse.ok(skuService.upload360Frames(skuCode, items));
    }

    @Operation(summary = "재고 조회", description = """
            재고는 숫자 한 칸이 아니라 장부(sku_stock_ledger)다. 현재 수량은 그 장부의
            delta 합계다. 숫자만 덮어쓰면 언제 왜 줄었는지 남지 않는데, 장부로 두면
            사유(INITIAL·PURCHASE·CANCEL_RESTORE·RETURN·ADMIN_ADJUST)와 참조
            (주문 항목 등)가 함께 남는다.

            합계를 매번 세지 않도록 캐시를 두고, 재고가 움직일 때마다 그 상품 키만 비운다.

            차감은 SKU 행을 FOR UPDATE 로 잠그고 한다. 재고가 0 이 되면 상품을 품절로
            바꾸고 관리자에게 알린다. 취소·반품으로 다시 0 을 넘으면 판매중으로 되돌린다.
            """)
    @GetMapping("/{skuCode}/stock")
    public ApiResponse<SkuDto.StockResponse> getStock(@PathVariable String skuCode) {
        return ApiResponse.ok(skuService.getStock(skuCode));
    }

    @Operation(summary = "CSV 일괄 등록", description = """
            전체를 먼저 검증하고, 오류가 하나라도 있으면 한 건도 저장하지 않는다. 절반만
            들어간 상태로 끝나면 무엇이 들어갔는지 사람이 다시 세야 한다.

            검증을 통과하면 청크 단위로 나눠 저장한다(기본 1000행, koala.csv.chunk-size).
            저장 중 실패하면 그 지점에서 멈추고, 몇 건까지 저장됐는지와 어디서 멈췄는지를
            응답에 담는다.
            """)
    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkuCsvDto.ImportResult> bulkImport(
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(skuCsvImportService.importCsv(file));
    }

    @Operation(summary = "CSV 양식 내려받기", description = """
            일괄 등록에 쓰는 빈 양식이다. UTF-8 로 내려준다.
            """)
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
