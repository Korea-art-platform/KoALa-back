package com.koala.koalaback.domain.inquiry.repository;

import com.koala.koalaback.domain.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    Optional<Inquiry> findByInquiryCodeAndDeletedAtIsNull(String inquiryCode);

    Page<Inquiry> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Inquiry> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);
    Page<Inquiry> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(String status, Pageable pageable);
}
