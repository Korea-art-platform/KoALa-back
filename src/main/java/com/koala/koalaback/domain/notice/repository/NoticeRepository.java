package com.koala.koalaback.domain.notice.repository;

import com.koala.koalaback.domain.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Optional<Notice> findByNoticeCodeAndDeletedAtIsNull(String noticeCode);

    List<Notice> findByDeletedAtIsNullOrderByIsPinnedDescCreatedAtDesc();

    List<Notice> findByIsActiveTrueAndDeletedAtIsNullOrderByIsPinnedDescCreatedAtDesc();
}
