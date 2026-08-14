package com.koala.koalaback.domain.admin.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.entity.AdminAuditLog;
import com.koala.koalaback.domain.admin.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditLogRepository adminAuditLogRepository;

    @Transactional
    public void log(Admin admin, String actionType, String targetType,
                    Long targetId, String requestPath, String httpMethod,
                    String ipAddress, String beforeData, String afterData, String memo) {
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
