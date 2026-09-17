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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopupService {
    static final int TITLE_MAX = 200;
    static final int BODY_MAX = 2000;
    static final int URL_MAX = 1024;

    private static final Set<String> LANGUAGES = Set.of(Popup.LANG_KO, Popup.LANG_EN);
    private static final Set<String> DISPLAY_TYPES = Set.of(Popup.TYPE_IMAGE, Popup.TYPE_TEMPLATE);
    private static final Set<String> PLACEMENTS = Set.of(Popup.PLACEMENT_HOME, Popup.PLACEMENT_ALL);

    private final PopupRepository popupRepository;
    private final AdminService adminService;
    private final CodeGenerator codeGenerator;
    private final StorageUploader storageUploader;

    public List<PopupDto.PublicPopupResponse> getVisiblePopups(String lang, String page) {
        String language = Popup.LANG_EN.equalsIgnoreCase(trim(lang)) ? Popup.LANG_EN : Popup.LANG_KO;
        List<String> placements = "home".equalsIgnoreCase(trim(page))
                ? List.of(Popup.PLACEMENT_HOME, Popup.PLACEMENT_ALL)
                : List.of(Popup.PLACEMENT_ALL);
        return popupRepository.findVisible(language, placements).stream()
                .map(PopupDto.PublicPopupResponse::from)
                .toList();
    }

    public List<PopupDto.PopupResponse> getAllPopups() {
        return popupRepository.findAllNotDeleted().stream()
                .map(PopupDto.PopupResponse::from)
                .toList();
    }

    public PopupDto.PopupResponse getPopup(String popupCode) {
        return PopupDto.PopupResponse.from(getPopupEntityByCode(popupCode));
    }

    @Transactional
    public PopupDto.PopupResponse createPopup(Long adminId, PopupDto.PopupRequest req) {
        Validated v = validate(req);
        Admin admin = adminService.getAdminById(adminId);

        Popup popup = Popup.builder()
                .popupCode(codeGenerator.generateCode())
                .title(v.title())
                .isActive(Boolean.TRUE.equals(req.getActive()))
                .showDismiss(req.getShowDismiss() == null || req.getShowDismiss())
                .language(v.language())
                .displayType(v.displayType())
                .imageUrl(v.imageUrl())
                .body(v.body())
                .showLinkButton(v.showLinkButton())
                .placement(v.placement())
                .landingUrl(v.landingUrl())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .createdByAdmin(admin)
                .build();

        return PopupDto.PopupResponse.from(popupRepository.save(popup));
    }

    @Transactional
    public PopupDto.PopupResponse updatePopup(Long adminId, String popupCode, PopupDto.PopupRequest req) {
        Validated v = validate(req);
        Admin admin = adminService.getAdminById(adminId);
        Popup popup = getPopupEntityByCode(popupCode);

        popup.update(v.title(), req.getActive(), req.getShowDismiss(),
                v.language(), v.displayType(), v.imageUrl(), v.body(),
                v.showLinkButton(), v.placement(), v.landingUrl(),
                req.getSortOrder(), admin);

        return PopupDto.PopupResponse.from(popup);
    }

    @Transactional
    public void activatePopup(String popupCode) {
        getPopupEntityByCode(popupCode).activate();
    }

    @Transactional
    public void deactivatePopup(String popupCode) {
        getPopupEntityByCode(popupCode).deactivate();
    }

    @Transactional
    public void deletePopup(String popupCode) {
        getPopupEntityByCode(popupCode).softDelete();
    }

    public String uploadImage(MultipartFile file) {
        return storageUploader.upload(file, "popups");
    }

    private Popup getPopupEntityByCode(String popupCode) {
        return popupRepository.findByPopupCodeAndDeletedAtIsNull(popupCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private record Validated(String title, String language, String displayType,
                             String imageUrl, String body, boolean showLinkButton,
                             String placement, String landingUrl) {
    }

    private Validated validate(PopupDto.PopupRequest req) {
        if (req == null) throw invalid("요청 내용이 비어 있습니다.");

        String title = trim(req.getTitle());
        if (title == null) throw invalid("제목을 입력해 주세요.");
        if (title.length() > TITLE_MAX) throw invalid("제목은 " + TITLE_MAX + "자 이하로 입력해 주세요.");

        String language = lower(req.getLanguage());
        if (language == null || !LANGUAGES.contains(language)) {
            throw invalid("언어는 ko 또는 en 만 선택할 수 있습니다.");
        }

        String displayType = upper(req.getDisplayType());
        if (displayType == null || !DISPLAY_TYPES.contains(displayType)) {
            throw invalid("표시 방식은 IMAGE 또는 TEMPLATE 만 선택할 수 있습니다.");
        }

        String placement = upper(req.getPlacement());
        if (placement == null || !PLACEMENTS.contains(placement)) {
            throw invalid("노출 위치는 HOME 또는 ALL 만 선택할 수 있습니다.");
        }

        String imageUrl = trim(req.getImageUrl());
        if (Popup.TYPE_IMAGE.equals(displayType) && imageUrl == null) {
            throw invalid("이미지 방식은 이미지가 필수입니다.");
        }
        if (imageUrl != null && imageUrl.length() > URL_MAX) {
            throw invalid("이미지 주소는 " + URL_MAX + "자 이하여야 합니다.");
        }

        String body = req.getBody() == null || req.getBody().isBlank() ? null : req.getBody();
        if (body != null && body.length() > BODY_MAX) {
            throw invalid("본문은 " + BODY_MAX + "자 이하로 입력해 주세요.");
        }

        boolean showLinkButton = Boolean.TRUE.equals(req.getShowLinkButton());
        String landingUrl = trim(req.getLandingUrl());
        if (showLinkButton && landingUrl == null) {
            throw invalid("바로가기 버튼을 노출하려면 이동할 주소가 필요합니다.");
        }
        if (landingUrl != null) {
            if (landingUrl.length() > URL_MAX) {
                throw invalid("이동할 주소는 " + URL_MAX + "자 이하여야 합니다.");
            }
            if (!isSafeLandingUrl(landingUrl)) {
                throw invalid("이동할 주소는 http(s):// 로 시작하는 주소나 / 로 시작하는 내부 경로만 쓸 수 있습니다.");
            }
        }

        return new Validated(title, language, displayType, imageUrl, body,
                showLinkButton, placement, landingUrl);
    }

    static boolean isSafeLandingUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == 0x7F || c == '\\') return false;
        }
        if (url.startsWith("/")) {
            return !url.startsWith("//");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            String s = scheme.toLowerCase(Locale.ROOT);
            if (!s.equals("http") && !s.equals("https")) return false;
            return uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String lower(String s) {
        String t = trim(s);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String upper(String s) {
        String t = trim(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }
}
