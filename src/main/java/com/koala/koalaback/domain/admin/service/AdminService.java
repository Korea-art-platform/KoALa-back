package com.koala.koalaback.domain.admin.service;

import com.koala.koalaback.domain.admin.dto.AdminDto;
import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.entity.AdminAuditLog;
import com.koala.koalaback.domain.admin.repository.AdminAuditLogRepository;
import com.koala.koalaback.domain.admin.repository.AdminRepository;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.security.JwtProvider;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.global.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {
    private final AdminRepository adminRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CodeGenerator codeGenerator;
    private final SkuService skuService;
    private final StockService stockService;

    @Transactional
    public AdminDto.TokenResponse login(AdminDto.LoginRequest req, HttpServletRequest httpReq) {
        Admin admin = adminRepository.findByLoginId(req.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED));

        if ("LOCKED".equals(admin.getStatus())) {
            throw new BusinessException(ErrorCode.ADMIN_LOCKED);
        }

        if (!passwordEncoder.matches(req.getPassword(), admin.getPasswordHash())) {
            admin.incrementLoginFail();
            throw new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED);
        }

        if (!admin.isActive()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        String clientIp = resolveClientIp(httpReq);
        admin.updateLastLogin(clientIp);
        String token = jwtProvider.createAccessToken(admin.getId(), "ADMIN");

        saveAuditLog(admin, "LOGIN", "admins", admin.getId(),
                httpReq.getRequestURI(), httpReq.getMethod(),
                clientIp, null, null, null);

        log.info("Admin login: adminId={}", admin.getId());
        return new AdminDto.TokenResponse(token);
    }

    public AdminDto.AdminResponse getMyInfo(Long adminId) {
        return AdminDto.AdminResponse.from(getAdminById(adminId));
    }

    @Transactional
    public void adjustStock(Long adminId, AdminDto.StockAdjustRequest req,
                            HttpServletRequest httpReq) {
        var sku = skuService.getSkuEntityByCode(req.getSkuCode());
        int before = stockService.getStock(sku.getId());
        stockService.adminAdjust(sku.getId(), req.getDelta(), req.getMemo());
        int after = before + req.getDelta();

        Admin admin = getAdminById(adminId);
        int actualAfter = stockService.getStock(sku.getId());
        saveAuditLog(admin, "STOCK_ADJUST", "skus", sku.getId(),
                httpReq.getRequestURI(), httpReq.getMethod(),
                resolveClientIp(httpReq),
                "{\"stock\":" + before + "}",
                "{\"stock\":" + actualAfter + "}",
                req.getMemo());
    }

    public com.koala.koalaback.global.response.PageResponse<AdminAuditLog> getAuditLogs(
            Long adminId, Pageable pageable) {
        return com.koala.koalaback.global.response.PageResponse.of(
                adminAuditLogRepository.findByAdminIdOrderByCreatedAtDesc(adminId, pageable)
        );
    }

    public Admin getAdminById(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
    }

    private String resolveClientIp(HttpServletRequest request) {
        return IpResolverUtil.resolve(request);
    }

    private void saveAuditLog(Admin admin, String actionType, String targetType,
                              Long targetId, String requestPath, String httpMethod,
                              String ipAddress, String beforeData,
                              String afterData, String memo) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .admin(admin)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .requestPath(requestPath)
                .httpMethod(httpMethod)
                .ipAddress(ipAddress)
                .beforeData(beforeData)
                .afterData(afterData)
                .memo(memo)
                .build());
    }
}
