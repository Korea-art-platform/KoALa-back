package com.koala.koalaback.domain.popup.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.domain.popup.dto.PopupDto;
import com.koala.koalaback.domain.popup.entity.Popup;
import com.koala.koalaback.domain.popup.repository.PopupRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.infra.storage.StorageUploader;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PopupServiceTest {
    @InjectMocks
    private PopupService popupService;

    @Mock private PopupRepository popupRepository;
    @Mock private AdminService adminService;
    @Mock private CodeGenerator codeGenerator;
    @Mock private StorageUploader storageUploader;

    @Nested
    @DisplayName("등록 검사")
    class Validation {
        @Test
        @DisplayName("이미지 방식인데 이미지가 없으면 거절한다")
        void imageRequiredForImageType() {
            PopupDto.PopupRequest req = request("IMAGE", null, false, null);

            assertInvalid(req, "이미지 방식은 이미지가 필수입니다.");
        }

        @Test
        @DisplayName("템플릿 방식은 이미지 없이도 등록된다")
        void templateWithoutImage() {
            stubSave();
            PopupDto.PopupRequest req = request("TEMPLATE", null, false, null);

            PopupDto.PopupResponse res = popupService.createPopup(1L, req);

            assertThat(res.getDisplayType()).isEqualTo("TEMPLATE");
            assertThat(res.getImageUrl()).isNull();
        }

        @Test
        @DisplayName("바로가기 버튼을 켰는데 주소가 없으면 거절한다")
        void landingUrlRequiredWhenButtonShown() {
            PopupDto.PopupRequest req = request("IMAGE", "popup.png", true, "  ");

            assertInvalid(req, null);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "javascript:alert(1)",
                "JavaScript:alert(1)",
                "//evil.com",
                "//evil.com/path",
                "data:text/html;base64,PHNjcmlwdD4=",
                "/\\evil.com",
                "ftp://example.com",
                "evil.com",
                "https://",
                "/store path"
        })
        @DisplayName("위험하거나 형식이 틀린 이동 주소는 거절한다")
        void unsafeLandingUrlRejected(String url) {
            PopupDto.PopupRequest req = request("IMAGE", "popup.png", true, url);

            assertInvalid(req, null);
            then(popupRepository).should(never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/store",
                "/skus/ABC123?from=popup",
                "https://koala.example.com/event",
                "http://example.com"
        })
        @DisplayName("내부 경로와 http(s) 주소는 받는다")
        void safeLandingUrlAccepted(String url) {
            stubSave();
            PopupDto.PopupRequest req = request("IMAGE", "popup.png", true, url);

            PopupDto.PopupResponse res = popupService.createPopup(1L, req);

            assertThat(res.getLandingUrl()).isEqualTo(url);
            assertThat(res.getShowLinkButton()).isTrue();
        }

        @Test
        @DisplayName("버튼을 꺼도 주소가 있으면 형식을 검사한다")
        void landingUrlCheckedEvenWithoutButton() {
            PopupDto.PopupRequest req = request("IMAGE", "popup.png", false, "javascript:alert(1)");

            assertInvalid(req, null);
        }

        @Test
        @DisplayName("제목이 비었거나 200자를 넘으면 거절한다")
        void titleChecked() {
            PopupDto.PopupRequest blank = request("TEMPLATE", null, false, null);
            given(blank.getTitle()).willReturn("   ");
            assertInvalid(blank, null);

            PopupDto.PopupRequest tooLong = request("TEMPLATE", null, false, null);
            given(tooLong.getTitle()).willReturn("가".repeat(201));
            assertInvalid(tooLong, null);
        }

        @Test
        @DisplayName("제목은 앞뒤 공백을 지워 저장한다")
        void titleTrimmed() {
            stubSave();
            PopupDto.PopupRequest req = request("TEMPLATE", null, false, null);
            given(req.getTitle()).willReturn("  가을 기획전  ");

            assertThat(popupService.createPopup(1L, req).getTitle()).isEqualTo("가을 기획전");
        }

        @Test
        @DisplayName("본문이 2000자를 넘으면 거절한다")
        void bodyChecked() {
            PopupDto.PopupRequest req = request("TEMPLATE", null, false, null);
            given(req.getBody()).willReturn("a".repeat(2001));

            assertInvalid(req, null);
        }

        @Test
        @DisplayName("언어·표시 방식·노출 위치는 정해진 값만 받는다")
        void enumsChecked() {
            PopupDto.PopupRequest lang = request("IMAGE", "popup.png", false, null);
            given(lang.getLanguage()).willReturn("ja");
            assertInvalid(lang, null);

            PopupDto.PopupRequest type = request("VIDEO", "popup.png", false, null);
            assertInvalid(type, null);

            PopupDto.PopupRequest placement = request("IMAGE", "popup.png", false, null);
            given(placement.getPlacement()).willReturn("CART");
            assertInvalid(placement, null);
        }

        @Test
        @DisplayName("요청 값이 그대로 저장된다")
        void savesFields() {
            stubSave();
            PopupDto.PopupRequest req = request("IMAGE", "popup.png", true, "/store");
            given(req.getLanguage()).willReturn("en");
            given(req.getPlacement()).willReturn("ALL");
            given(req.getActive()).willReturn(true);
            given(req.getShowDismiss()).willReturn(false);
            given(req.getSortOrder()).willReturn(3);

            PopupDto.PopupResponse res = popupService.createPopup(1L, req);

            assertThat(res.getPopupCode()).isEqualTo("POP-1");
            assertThat(res.getActive()).isTrue();
            assertThat(res.getShowDismiss()).isFalse();
            assertThat(res.getLanguage()).isEqualTo("en");
            assertThat(res.getPlacement()).isEqualTo("ALL");
            assertThat(res.getSortOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("수정도 같은 규칙으로 검사한다")
        void updateValidated() {
            PopupDto.PopupRequest req = request("IMAGE", null, false, null);

            assertInvalid(() -> popupService.updatePopup(1L, "POP-1", req));
            then(popupRepository).should(never()).findByPopupCodeAndDeletedAtIsNull(anyString());
        }

        @Test
        @DisplayName("삭제됐거나 없는 팝업은 찾을 수 없다")
        void notFound() {
            given(popupRepository.findByPopupCodeAndDeletedAtIsNull("NOPE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> popupService.getPopup("NOPE"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("공개 조회")
    class PublicQuery {
        @Test
        @DisplayName("page=home 이면 HOME 과 ALL 을 함께 찾는다")
        void homeIncludesHomeAndAll() {
            given(popupRepository.findVisible(eq("ko"), anyCollection())).willReturn(List.of());

            popupService.getVisiblePopups("ko", "home");

            assertThat(capturedPlacements("ko")).containsExactlyInAnyOrder("HOME", "ALL");
        }

        @Test
        @DisplayName("page 가 home 이 아니거나 비면 ALL 만 찾는다")
        void otherPagesOnlyAll() {
            given(popupRepository.findVisible(eq("ko"), anyCollection())).willReturn(List.of());

            popupService.getVisiblePopups("ko", "store");
            popupService.getVisiblePopups("ko", null);

            ArgumentCaptor<Collection<String>> captor = placementsCaptor();
            then(popupRepository).should(times(2)).findVisible(eq("ko"), captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(p -> assertThat(p).containsExactly("ALL"));
        }

        @Test
        @DisplayName("en 이면 영어 팝업을, 그 밖의 값이면 한국어 팝업을 찾는다")
        void languageFilter() {
            given(popupRepository.findVisible(anyString(), anyCollection())).willReturn(List.of());

            popupService.getVisiblePopups("en", "home");
            popupService.getVisiblePopups("EN", "home");
            popupService.getVisiblePopups("ja", "home");
            popupService.getVisiblePopups(null, "home");

            ArgumentCaptor<String> lang = ArgumentCaptor.forClass(String.class);
            then(popupRepository).should(times(4)).findVisible(lang.capture(), anyCollection());
            assertThat(lang.getAllValues()).containsExactly("en", "en", "ko", "ko");
        }

        @Test
        @DisplayName("고객용 응답에는 노출에 필요한 값만 담긴다")
        void publicResponseFields() {
            Popup popup = Popup.builder()
                    .popupCode("POP-1")
                    .title("가을 기획전")
                    .isActive(true)
                    .showDismiss(true)
                    .language("ko")
                    .displayType("TEMPLATE")
                    .body("본문")
                    .showLinkButton(true)
                    .placement("HOME")
                    .landingUrl("/store")
                    .build();
            given(popupRepository.findVisible(eq("ko"), anyCollection())).willReturn(List.of(popup));

            List<PopupDto.PublicPopupResponse> res = popupService.getVisiblePopups("ko", "home");

            assertThat(res).singleElement().satisfies(p -> {
                assertThat(p.getPopupCode()).isEqualTo("POP-1");
                assertThat(p.getTitle()).isEqualTo("가을 기획전");
                assertThat(p.getDisplayType()).isEqualTo("TEMPLATE");
                assertThat(p.getBody()).isEqualTo("본문");
                assertThat(p.getShowDismiss()).isTrue();
                assertThat(p.getShowLinkButton()).isTrue();
                assertThat(p.getLandingUrl()).isEqualTo("/store");
            });
        }
    }

    private Collection<String> capturedPlacements(String lang) {
        ArgumentCaptor<Collection<String>> captor = placementsCaptor();
        then(popupRepository).should().findVisible(eq(lang), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Collection<String>> placementsCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
    }

    private void stubSave() {
        given(adminService.getAdminById(1L)).willReturn(mock(Admin.class));
        given(codeGenerator.generateCode()).willReturn("POP-1");
        given(popupRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private void assertInvalid(PopupDto.PopupRequest req, String message) {
        var assertion = assertThatThrownBy(() -> popupService.createPopup(1L, req))
                .isInstanceOf(BusinessException.class);
        assertion.extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        if (message != null) assertion.hasMessage(message);
    }

    private void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private PopupDto.PopupRequest request(String displayType, String imageUrl,
                                          boolean showLinkButton, String landingUrl) {
        PopupDto.PopupRequest req = mock(PopupDto.PopupRequest.class);
        lenient().when(req.getTitle()).thenReturn("가을 기획전");
        lenient().when(req.getLanguage()).thenReturn("ko");
        lenient().when(req.getDisplayType()).thenReturn(displayType);
        lenient().when(req.getImageUrl()).thenReturn(imageUrl);
        lenient().when(req.getShowLinkButton()).thenReturn(showLinkButton);
        lenient().when(req.getLandingUrl()).thenReturn(landingUrl);
        lenient().when(req.getPlacement()).thenReturn("HOME");
        return req;
    }
}
