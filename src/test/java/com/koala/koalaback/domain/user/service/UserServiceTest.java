package com.koala.koalaback.domain.user.service;

import com.koala.koalaback.domain.user.dto.UserDto;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.RefreshTokenRepository;
import com.koala.koalaback.domain.user.repository.UserAddressRepository;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.security.JwtProvider;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.global.util.PhoneNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @InjectMocks
    private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.koala.koalaback.domain.cart.repository.CartRepository cartRepository;
    @Mock private UserAddressRepository userAddressRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private CodeGenerator codeGenerator;
    @Mock private PhoneNormalizer phoneNormalizer;

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        UserDto.SignupRequest req = mock(UserDto.SignupRequest.class);
        given(req.getEmail()).willReturn("test@koala.com");
        given(req.getPassword()).willReturn("password123");
        given(req.getName()).willReturn("테스터");
        given(req.getPhone()).willReturn(null);

        given(userRepository.existsByEmail(any())).willReturn(false);
        given(codeGenerator.generateCode()).willReturn("TESTCODE1234");
        given(passwordEncoder.encode(any())).willReturn("encoded_password");
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access_token");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh_token");
        given(refreshTokenRepository.save(any())).willReturn(null);

        UserDto.TokenResponse result = userService.signup(req);

        assertThat(result.getAccessToken()).isEqualTo("access_token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh_token");
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 — 이메일 중복")
    void signup_fail_duplicate_email() {
        UserDto.SignupRequest req = mock(UserDto.SignupRequest.class);
        given(req.getEmail()).willReturn("test@koala.com");
        given(userRepository.existsByEmail("test@koala.com")).willReturn(true);

        assertThatThrownBy(() -> userService.signup(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    }

    @Test
    @DisplayName("로그인 성공")
    void login_success() {
        UserDto.LoginRequest req = mock(UserDto.LoginRequest.class);
        given(req.getEmail()).willReturn("test@koala.com");
        given(req.getPassword()).willReturn("password123");

        User user = User.builder()
                .userCode("TESTCODE")
                .email("test@koala.com")
                .passwordHash("encoded_password")
                .name("테스터")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByEmail("test@koala.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded_password")).willReturn(true);
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access_token");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh_token");
        given(refreshTokenRepository.save(any())).willReturn(null);

        UserDto.TokenResponse result = userService.login(req);

        assertThat(result.getAccessToken()).isEqualTo("access_token");
    }

    @Test
    @DisplayName("로그인 실패 — 비밀번호 불일치")
    void login_fail_wrong_password() {
        UserDto.LoginRequest req = mock(UserDto.LoginRequest.class);
        given(req.getEmail()).willReturn("test@koala.com");
        given(req.getPassword()).willReturn("wrong_password");

        User user = User.builder()
                .userCode("TESTCODE")
                .email("test@koala.com")
                .passwordHash("encoded_password")
                .name("테스터")
                .build();

        given(userRepository.findByEmail("test@koala.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong_password", "encoded_password")).willReturn(false);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
}
