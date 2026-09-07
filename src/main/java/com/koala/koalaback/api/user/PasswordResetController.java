package com.koala.koalaback.api.user;

import com.koala.koalaback.domain.user.dto.PasswordResetDto;
import com.koala.koalaback.domain.user.service.PasswordResetService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자 · 인증", description = "가입·로그인·토큰, 내 정보, 배송지")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetService passwordResetService;
    @Operation(summary = "비밀번호 재설정 코드 발송", description = """
            가입된 이메일이면 8자리 코드를 메일로 보낸다. 코드는 SecureRandom 으로 만든다.

            가입되지 않은 이메일이어도 똑같이 성공으로 답한다. "없는 계정입니다"로
            답하면 이메일만 넣어 보는 것으로 가입 여부를 알아낼 수 있다.

            소셜 로그인으로 가입한 계정은 보내지 않는다. 그쪽은 비밀번호가 없다.

            새로 보낼 때 이전 코드는 지운다. 여러 개가 동시에 살아 있으면 유효한
            코드가 그만큼 늘어난다.
            """)
    @PostMapping("/password-reset/send")
    public ApiResponse<Void> sendResetCode(
            @Valid @RequestBody PasswordResetDto.SendCodeRequest req) {
        passwordResetService.sendResetCode(req);
        return ApiResponse.ok();
    }
    @Operation(summary = "재설정 코드 확인", description = """
            코드가 맞고 만료되지 않았으면 확인 표시를 남긴다. 아직 비밀번호를 바꾸지는
            않는다.

            확인과 변경을 나눈 것은 화면 흐름 때문이다 — 코드가 맞는지 먼저 알려주고
            새 비밀번호를 받는다. 확인되지 않은 코드로는 변경 단계가 진행되지 않는다.

            이미 쓴 코드와 이미 확인된 코드는 이 단계에서 걸리지 않는다.
            """)
    @PostMapping("/password-reset/verify")
    public ApiResponse<Void> verifyCode(
            @Valid @RequestBody PasswordResetDto.VerifyCodeRequest req){
        passwordResetService.verifyCode(req);
        return ApiResponse.ok();
    }
    @Operation(summary = "비밀번호 재설정", description = """
            확인 단계를 통과한 코드로만 바꿀 수 있다. 코드를 바로 이 단계에 넣어도
            확인 표시가 없으면 INVALID_TOKEN 이다.

            바꾼 뒤에는 그 이메일의 코드를 전부 지운다. 쓴 코드를 사용됨으로만 표시하고
            두면 아직 안 쓴 코드가 남아 다시 바꿀 수 있다.
            """)
    @PostMapping("/password-reset/reset")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody PasswordResetDto.ResetPasswordRequest req){
        passwordResetService.resetPassword(req);
        return ApiResponse.ok();
    }
}
