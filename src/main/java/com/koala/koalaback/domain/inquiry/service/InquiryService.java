package com.koala.koalaback.domain.inquiry.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.domain.inquiry.dto.InquiryDto;
import com.koala.koalaback.domain.inquiry.entity.Inquiry;
import com.koala.koalaback.domain.inquiry.repository.InquiryRepository;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.global.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {
    private final InquiryRepository inquiryRepository;
    private final UserRepository    userRepository;
    private final OrderRepository   orderRepository;
    private final AdminService      adminService;
    private final CodeGenerator     codeGenerator;

    @Transactional
    public InquiryDto.InquiryResponse createInquiry(Long userId, InquiryDto.CreateRequest req) {
        User user = getUserById(userId);

        Order order = null;
        if (req.getOrderNo() != null && !req.getOrderNo().isBlank()) {
            order = orderRepository.findByOrderNoAndUserId(req.getOrderNo(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        }

        Inquiry inquiry = Inquiry.builder()
                .inquiryCode(codeGenerator.generateCode())
                .user(user)
                .order(order)
                .category(req.getCategory())
                .title(req.getTitle())
                .content(req.getContent())
                .isSecret(req.getIsSecret())
                .build();

        return InquiryDto.InquiryResponse.from(inquiryRepository.save(inquiry));
    }

    public PageResponse<InquiryDto.InquiryResponse> getMyInquiries(Long userId, Pageable pageable) {
        return PageResponse.of(
                inquiryRepository
                        .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                        .map(q -> InquiryDto.InquiryResponse.from(q))
        );
    }

    public InquiryDto.InquiryResponse getMyInquiry(Long userId, String inquiryCode) {
        Inquiry inquiry = getByCode(inquiryCode);
        if (!inquiry.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return InquiryDto.InquiryResponse.from(inquiry);
    }

    @Transactional
    public void deleteMyInquiry(Long userId, String inquiryCode) {
        Inquiry inquiry = getByCode(inquiryCode);
        if (!inquiry.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if ("ANSWERED".equals(inquiry.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        inquiry.softDelete();
    }

    public PageResponse<InquiryDto.InquiryResponse> getAllInquiries(String status, Pageable pageable) {
        var page = (status != null && !status.isBlank())
                ? inquiryRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(status, pageable)
                : inquiryRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable);

        return PageResponse.of(page.map(InquiryDto.InquiryResponse::from));
    }

    public InquiryDto.InquiryResponse getInquiry(String inquiryCode) {
        return InquiryDto.InquiryResponse.from(getByCode(inquiryCode));
    }

    @Transactional
    public InquiryDto.InquiryResponse answerInquiry(Long adminId, String inquiryCode,
                                                     InquiryDto.AnswerRequest req) {
        Admin admin = adminService.getAdminById(adminId);
        Inquiry inquiry = getByCode(inquiryCode);
        inquiry.answer(admin, req.getAnswerContent());
        return InquiryDto.InquiryResponse.from(inquiry);
    }

    @Transactional
    public void closeInquiry(String inquiryCode) {
        getByCode(inquiryCode).close();
    }

    private Inquiry getByCode(String inquiryCode) {
        return inquiryRepository.findByInquiryCodeAndDeletedAtIsNull(inquiryCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
