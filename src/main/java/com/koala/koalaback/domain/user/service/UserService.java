package com.koala.koalaback.domain.user.service;

import com.koala.koalaback.domain.user.dto.UserDto;
import com.koala.koalaback.domain.user.event.UserSignedUpEvent;
import com.koala.koalaback.domain.user.entity.RefreshToken;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.entity.UserAddress;
import com.koala.koalaback.domain.user.repository.RefreshTokenRepository;
import com.koala.koalaback.domain.user.repository.UserAddressRepository;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.security.JwtProvider;
import com.koala.koalaback.global.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final UserAddressRepository userAddressRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CodeGenerator codeGenerator;

    @Transactional
    public UserDto.TokenResponse signup(UserDto.SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .userCode(codeGenerator.generateCode())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .phone(formatPhoneToE164(req.getPhone()))
                .build();

        userRepository.save(user);

        // 가입 전에 비회원으로 산 것이 있으면 이 계정에 붙인다.
        eventPublisher.publishEvent(new UserSignedUpEvent(user.getId(), user.getEmail()));

        return issueTokens(user);
    }

    @Transactional
    public UserDto.TokenResponse login(UserDto.LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if ("SUSPENDED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }

        if ("INACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_INACTIVE);
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.updateLastLoginAt();
        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(String.valueOf(userId));
    }

    @Transactional
    public UserDto.TokenResponse refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);

        RefreshToken savedToken = refreshTokenRepository.findById(String.valueOf(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (!savedToken.getRefreshToken().equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = getUserById(userId);
        return issueTokens(user);
    }

    public UserDto.ProfileResponse getProfile(Long userId) {
        return UserDto.ProfileResponse.from(getUserById(userId));
    }

    @Transactional
    public UserDto.ProfileResponse updateProfile(Long userId,
                                                 UserDto.UpdateProfileRequest req) {
        User user = getUserById(userId);
        String formattedPhone = req.getPhone() != null ? formatPhoneToE164(req.getPhone()) : user.getPhone();
        user.updateProfile(
                req.getName() != null ? req.getName() : user.getName(),
                formattedPhone
        );
        return UserDto.ProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, UserDto.ChangePasswordRequest req) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.updatePassword(passwordEncoder.encode(req.getNewPassword()));
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = getUserById(userId);
        user.softDelete();
        refreshTokenRepository.deleteByUserId(String.valueOf(userId));
    }

    public List<UserDto.AddressResponse> getAddresses(Long userId) {
        return userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(UserDto.AddressResponse::from)
                .toList();
    }

    @Transactional
    public UserDto.AddressResponse createAddress(Long userId,
                                                 UserDto.AddressCreateRequest req) {
        User user = getUserById(userId);

        boolean isDefault = user.getAddresses().isEmpty() ||
                Boolean.TRUE.equals(req.getIsDefault());

        if (isDefault) {
            userAddressRepository.clearDefaultByUserId(userId);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .label(req.getLabel())
                .recipientName(req.getRecipientName())
                .recipientPhone(req.getRecipientPhone())
                .zipCode(req.getZipCode())
                .address1(req.getAddress1())
                .address2(req.getAddress2())
                .isDefault(isDefault)
                .build();

        return UserDto.AddressResponse.from(userAddressRepository.save(address));
    }

    @Transactional
    public UserDto.AddressResponse updateAddress(Long userId, Long addressId,
                                                 UserDto.AddressUpdateRequest req) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (Boolean.TRUE.equals(req.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            userAddressRepository.clearDefaultByUserId(userId);
        }

        address.update(
                req.getLabel(),
                req.getRecipientName(),
                req.getRecipientPhone(),
                req.getZipCode(),
                req.getAddress1(),
                req.getAddress2(),
                req.getIsDefault()
        );

        return UserDto.AddressResponse.from(address);
    }

    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        userAddressRepository.clearDefaultByUserId(userId);
        address.setDefault(true);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        userAddressRepository.delete(address);
    }

    @Transactional
    public void saveFcmToken(Long userId, String token) {
        getUserById(userId).updateFcmToken(token);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private String formatPhoneToE164(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        String cleaned = phone.replaceAll("[^0-9]", "");

        if (cleaned.isEmpty()) {
            return null;
        }

        if (cleaned.startsWith("0")) {
            return "+82" + cleaned.substring(1);
        }

        if (cleaned.length() < 10) {
            return null;
        }

        return "+" + cleaned;
    }

    public UserDto.TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), "USER");
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .userId(String.valueOf(user.getId()))
                        .refreshToken(refreshToken)
                        .expiry(604800L)
                        .build()
        );

        return UserDto.TokenResponse.of(accessToken, refreshToken);
    }
}
