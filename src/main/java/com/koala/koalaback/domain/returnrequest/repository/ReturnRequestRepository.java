package com.koala.koalaback.domain.returnrequest.repository;

import com.koala.koalaback.domain.returnrequest.entity.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Optional<ReturnRequest> findByReturnNo(String returnNo);

    boolean existsByOrderIdAndStatusNot(Long orderId, String status);

    List<ReturnRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<ReturnRequest> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);

    @Query("SELECT r FROM ReturnRequest r WHERE (:status IS NULL OR r.status = :status) ORDER BY r.createdAt DESC")
    Page<ReturnRequest> findByStatusFilter(@Param("status") String status, Pageable pageable);
}
