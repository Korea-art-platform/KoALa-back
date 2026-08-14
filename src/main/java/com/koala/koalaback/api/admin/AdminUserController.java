package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.user.dto.UserDto;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<PageResponse<UserDto.ProfileResponse>> getUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                userRepository.findAll(pageable)
                        .map(UserDto.ProfileResponse::from)
        ));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserDto.ProfileResponse> getUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.ok(UserDto.ProfileResponse.from(user));
    }

    @PatchMapping("/{userId}/suspend")
    @Transactional
    public ApiResponse<Void> suspendUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.suspend();
        return ApiResponse.ok();
    }

    @PatchMapping("/{userId}/activate")
    @Transactional
    public ApiResponse<Void> activateUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.activate();
        return ApiResponse.ok();
    }
}
