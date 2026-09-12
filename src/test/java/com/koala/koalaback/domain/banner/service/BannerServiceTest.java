package com.koala.koalaback.domain.banner.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.banner.dto.BannerDto;
import com.koala.koalaback.domain.banner.entity.Banner;
import com.koala.koalaback.domain.banner.repository.BannerRepository;
import com.koala.koalaback.domain.pricing.VatPolicy;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.infra.storage.StorageUploader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {
    @InjectMocks
    private BannerService bannerService;

    @Mock private BannerRepository bannerRepository;
    @Mock private SkuRepository skuRepository;
    @Mock private VatPolicy vatPolicy;
    @Mock private AdminService adminService;
    @Mock private CodeGenerator codeGenerator;
    @Mock private StorageUploader storageUploader;

    @Test
    @DisplayName("작품을 고르면 작품·작가·표시가가 응답에 담긴다")
    void createShowcase_withSku() {
        given(adminService.getAdminById(1L)).willReturn(mock(Admin.class));
        given(codeGenerator.generateCode()).willReturn("BNR-1");
        given(skuRepository.findBySkuCode("SKU-1")).willReturn(Optional.of(sku("SKU-1")));
        given(bannerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(vatPolicy.exemptMainCategories()).willReturn(Set.of());
        given(vatPolicy.grossOf(any(), any(), any())).willReturn(new BigDecimal("330000"));

        BannerDto.BannerResponse res = bannerService.createBanner(1L, showcaseRequest("SKU-1"));

        assertThat(res.getSkuCode()).isEqualTo("SKU-1");
        assertThat(res.getSkuModel()).isEqualTo("버즈");
        assertThat(res.getArtistCode()).isEqualTo("ART-1");
        assertThat(res.getArtistName()).isEqualTo("박준상");
        assertThat(res.getDisplayPrice()).isEqualByComparingTo("330000");
        assertThat(res.getEffectImageUrl1()).isEqualTo("effect1.png");
        assertThat(res.getBgColor()).isEqualTo("#8a3141");
    }

    @Test
    @DisplayName("없는 작품이면 거절한다")
    void createShowcase_unknownSku() {
        given(adminService.getAdminById(1L)).willReturn(mock(Admin.class));
        given(codeGenerator.generateCode()).willReturn("BNR-1");
        given(skuRepository.findBySkuCode("NOPE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> bannerService.createBanner(1L, showcaseRequest("NOPE")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SKU_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 작품은 고를 수 없다")
    void createShowcase_deletedSku() {
        Sku deleted = sku("SKU-1");
        deleted.softDelete();
        given(adminService.getAdminById(1L)).willReturn(mock(Admin.class));
        given(codeGenerator.generateCode()).willReturn("BNR-1");
        given(skuRepository.findBySkuCode("SKU-1")).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> bannerService.createBanner(1L, showcaseRequest("SKU-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SKU_NOT_FOUND);
    }

    @Test
    @DisplayName("작품을 비우면 연결하지 않는다 — 다른 배너는 그대로 쓴다")
    void createBanner_withoutSku() {
        given(adminService.getAdminById(1L)).willReturn(mock(Admin.class));
        given(codeGenerator.generateCode()).willReturn("BNR-1");
        given(bannerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BannerDto.BannerResponse res = bannerService.createBanner(1L, showcaseRequest(null));

        assertThat(res.getSkuCode()).isNull();
        assertThat(res.getDisplayPrice()).isNull();
        then(skuRepository).shouldHaveNoInteractions();
        then(vatPolicy).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("걸어 둔 작품이 나중에 삭제되면 응답에서 뺀다")
    void response_hidesDeletedSku() {
        Sku sku = sku("SKU-1");
        Banner banner = Banner.builder()
                .bannerCode("BNR-1")
                .bannerType("MAIN")
                .title("버즈")
                .sku(sku)
                .build();
        sku.softDelete();

        BannerDto.BannerResponse res = BannerDto.BannerResponse.from(banner, vatPolicy, Set.of());

        assertThat(res.getSkuCode()).isNull();
        assertThat(res.getArtistName()).isNull();
        assertThat(res.getDisplayPrice()).isNull();
    }

    private BannerDto.CreateRequest showcaseRequest(String skuCode) {
        BannerDto.CreateRequest req = mock(BannerDto.CreateRequest.class);
        lenient().when(req.getBannerType()).thenReturn("MAIN");
        lenient().when(req.getTitle()).thenReturn("버즈");
        lenient().when(req.getImageUrl()).thenReturn("main.png");
        lenient().when(req.getSkuCode()).thenReturn(skuCode);
        lenient().when(req.getEffectImageUrl1()).thenReturn("effect1.png");
        lenient().when(req.getBgColor()).thenReturn("#8a3141");
        return req;
    }

    private Sku sku(String skuCode) {
        Artist artist = Artist.builder()
                .artistCode("ART-1")
                .name("박준상")
                .slug("park")
                .build();
        return Sku.builder()
                .skuCode(skuCode)
                .artist(artist)
                .name("버즈 자기감매기 흰색")
                .model("버즈")
                .slug("buzz-white")
                .listPrice(new BigDecimal("300000"))
                .build();
    }
}
