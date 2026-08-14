package com.koala.koalaback.domain.artist.repository;

import com.koala.koalaback.domain.artist.entity.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
    Optional<Artist> findByArtistCode(String artistCode);

    Optional<Artist> findBySlug(String slug);

    Page<Artist> findByDeletedAtIsNull(Pageable pageable);

    Page<Artist> findByDeletedAtIsNullAndIsActiveTrue(Pageable pageable);

    List<Artist> findAllByArtistCodeIn(List<String> artistCodes);
}
