package com.koala.koalaback.domain.popup.repository;

import com.koala.koalaback.domain.popup.entity.Popup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PopupRepository extends JpaRepository<Popup, Long> {
    Optional<Popup> findByPopupCodeAndDeletedAtIsNull(String popupCode);

    @Query("""
        SELECT p FROM Popup p
        WHERE p.deletedAt IS NULL
        ORDER BY p.sortOrder ASC, p.createdAt DESC
        """)
    List<Popup> findAllNotDeleted();

    @Query("""
        SELECT p FROM Popup p
        WHERE p.isActive = true
          AND p.deletedAt IS NULL
          AND p.language = :language
          AND p.placement IN :placements
        ORDER BY p.sortOrder ASC, p.createdAt DESC
        """)
    List<Popup> findVisible(@Param("language") String language,
                            @Param("placements") Collection<String> placements);
}
