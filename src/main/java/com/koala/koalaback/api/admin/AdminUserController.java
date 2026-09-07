package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.user.dto.UserDto;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 · 사용자", description = "회원 조회와 정지·해제")
@RestController
@RequestMapping("/admin/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserRepository userRepository;

    @Operation(summary = "회원 목록")
    @GetMapping
    public ApiResponse<PageResponse<UserDto.ProfileResponse>> getUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                userRepository.findAll(pageable)
                        .map(UserDto.ProfileResponse::from)
        ));
    }

    @Operation(summary = "회원 상세")
    @GetMapping("/{userId}")
    public ApiResponse<UserDto.ProfileResponse> getUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.ok(UserDto.ProfileResponse.from(user));
    }

    @Operation(summary = "회원 정지", description = """
            정지된 계정은 로그인에서 USER_SUSPENDED 로 막힌다. 자격증명이 틀린 경우와
            달리 이유를 그대로 알려준다 — 본인이 왜 못 들어오는지는 알아야 한다.
            """)
    @PatchMapping("/{userId}/suspend")
    @Transactional
    public ApiResponse<Void> suspendUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.suspend();
        return ApiResponse.ok();
    }

    @Operation(summary = "회원 정지 해제")
    @PatchMapping("/{userId}/activate")
    @Transactional
    public ApiResponse<Void> activateUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.activate();
        return ApiResponse.ok();
    }
}
