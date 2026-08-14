package com.koala.koalaback.domain.admin.repository;

import com.koala.koalaback.domain.admin.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {
    Optional<AdminRole> findByRoleCode(String roleCode);

    List<AdminRole> findByIsActiveTrue();
}
