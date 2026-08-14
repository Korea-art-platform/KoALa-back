package com.koala.koalaback.domain.admin.repository;

import com.koala.koalaback.domain.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByLoginId(String loginId);

    Optional<Admin> findByAdminCode(String adminCode);

    boolean existsByLoginId(String loginId);
}
