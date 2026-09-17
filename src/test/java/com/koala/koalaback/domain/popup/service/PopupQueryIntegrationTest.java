package com.koala.koalaback.domain.popup.service;

import com.koala.koalaback.domain.popup.dto.PopupDto;
import com.koala.koalaback.domain.popup.entity.Popup;
import com.koala.koalaback.domain.popup.repository.PopupRepository;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("공개 팝업 조회 (DB)")
class PopupQueryIntegrationTest extends IntegrationTestSupport {
    @Autowired private PopupService popupService;
    @Autowired private PopupRepository popupRepository;

    private String uid;

    @BeforeEach
    void setUp() {
        popupRepository.deleteAll();
        uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        save("KO_HOME_2", "ko", "HOME", 2, true);
        save("KO_ALL_1", "ko", "ALL", 1, true);
        save("KO_HOME_0", "ko", "HOME", 0, true);
        save("KO_OFF", "ko", "ALL", 0, false);
        save("EN_ALL", "en", "ALL", 0, true);
        Popup deleted = save("KO_DELETED", "ko", "ALL", 0, true);
        deleted.softDelete();
        popupRepository.flush();
    }

    @Test
    @DisplayName("홈에서는 해당 언어의 HOME·ALL 팝업이 정렬 순서대로 나온다")
    void homeShowsHomeAndAll() {
        assertThat(titles(popupService.getVisiblePopups("ko", "home")))
                .containsExactly("KO_HOME_0", "KO_ALL_1", "KO_HOME_2");
    }

    @Test
    @DisplayName("홈이 아니면 ALL 팝업만 나온다")
    void otherPageShowsAllOnly() {
        assertThat(titles(popupService.getVisiblePopups("ko", "store"))).containsExactly("KO_ALL_1");
        assertThat(titles(popupService.getVisiblePopups("ko", null))).containsExactly("KO_ALL_1");
    }

    @Test
    @DisplayName("언어별로 나뉘고, 모르는 언어는 한국어로 본다")
    void languageFilter() {
        assertThat(titles(popupService.getVisiblePopups("en", "home"))).containsExactly("EN_ALL");
        assertThat(titles(popupService.getVisiblePopups("ja", "store"))).containsExactly("KO_ALL_1");
    }

    @Test
    @DisplayName("어드민 목록에는 비활성도 나오고 삭제된 것은 빠진다")
    void adminListExcludesDeleted() {
        assertThat(popupService.getAllPopups())
                .extracting(PopupDto.PopupResponse::getTitle)
                .contains("KO_OFF")
                .doesNotContain("KO_DELETED")
                .hasSize(5);
    }

    private List<String> titles(List<PopupDto.PublicPopupResponse> popups) {
        return popups.stream().map(PopupDto.PublicPopupResponse::getTitle).toList();
    }

    private Popup save(String title, String language, String placement, int sortOrder, boolean active) {
        return popupRepository.save(Popup.builder()
                .popupCode("PT" + uid + title.hashCode())
                .title(title)
                .isActive(active)
                .showDismiss(true)
                .language(language)
                .displayType(Popup.TYPE_TEMPLATE)
                .body("본문")
                .showLinkButton(false)
                .placement(placement)
                .sortOrder(sortOrder)
                .build());
    }
}
