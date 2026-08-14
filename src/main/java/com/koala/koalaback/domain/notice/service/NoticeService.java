package com.koala.koalaback.domain.notice.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.domain.notice.dto.NoticeDto;
import com.koala.koalaback.domain.notice.entity.Notice;
import com.koala.koalaback.domain.notice.repository.NoticeRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final AdminService adminService;
    private final CodeGenerator codeGenerator;

    public List<NoticeDto.NoticeResponse> getPublicNotices() {
        return noticeRepository
                .findByIsActiveTrueAndDeletedAtIsNullOrderByIsPinnedDescCreatedAtDesc()
                .stream()
                .map(NoticeDto.NoticeResponse::from)
                .toList();
    }

    public NoticeDto.NoticeResponse getPublicNotice(String noticeCode) {
        Notice notice = getNoticeByCode(noticeCode);
        if (!notice.getIsActive()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return NoticeDto.NoticeResponse.from(notice);
    }

    public List<NoticeDto.NoticeResponse> getAllNotices() {
        return noticeRepository
                .findByDeletedAtIsNullOrderByIsPinnedDescCreatedAtDesc()
                .stream()
                .map(NoticeDto.NoticeResponse::from)
                .toList();
    }

    public NoticeDto.NoticeResponse getNotice(String noticeCode) {
        return NoticeDto.NoticeResponse.from(getNoticeByCode(noticeCode));
    }

    @Transactional
    public NoticeDto.NoticeResponse createNotice(Long adminId, NoticeDto.CreateRequest req) {
        Admin admin = adminService.getAdminById(adminId);
        Notice notice = Notice.builder()
                .noticeCode(codeGenerator.generateCode())
                .title(req.getTitle())
                .content(req.getContent())
                .isPinned(req.getIsPinned())
                .createdByAdmin(admin)
                .build();
        return NoticeDto.NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeDto.NoticeResponse updateNotice(Long adminId, String noticeCode,
                                                  NoticeDto.UpdateRequest req) {
        Admin admin = adminService.getAdminById(adminId);
        Notice notice = getNoticeByCode(noticeCode);
        notice.update(req.getTitle(), req.getContent(), req.getIsPinned(), admin);
        return NoticeDto.NoticeResponse.from(notice);
    }

    @Transactional
    public void activateNotice(String noticeCode) {
        getNoticeByCode(noticeCode).activate();
    }

    @Transactional
    public void deactivateNotice(String noticeCode) {
        getNoticeByCode(noticeCode).deactivate();
    }

    @Transactional
    public void deleteNotice(String noticeCode) {
        getNoticeByCode(noticeCode).softDelete();
    }

    private Notice getNoticeByCode(String noticeCode) {
        return noticeRepository.findByNoticeCodeAndDeletedAtIsNull(noticeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
