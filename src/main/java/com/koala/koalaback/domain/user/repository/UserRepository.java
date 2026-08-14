package com.koala.koalaback.domain.user.repository;

import com.koala.koalaback.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUserCode(String userCode);

    boolean existsByEmail(String email);

    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    Optional<User> findByEmailAndOauthProviderIsNull(String email);

    long countByCreatedAtAfter(LocalDateTime dateTime);
}
