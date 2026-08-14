package com.koala.koalaback.domain.artist.repository;

import com.koala.koalaback.domain.artist.entity.ArtistFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArtistFollowRepository extends JpaRepository<ArtistFollow, Long> {
    boolean existsByUserIdAndArtistId(Long userId, Long artistId);

    Optional<ArtistFollow> findByUserIdAndArtistId(Long userId, Long artistId);

    long countByArtistId(Long artistId);

    @Query("SELECT f.artist.id AS artistId, COUNT(f) AS cnt " +
           "FROM ArtistFollow f WHERE f.artist.id IN :ids GROUP BY f.artist.id")
    List<FollowCountProjection> countsByArtistIds(@Param("ids") List<Long> ids);

    interface FollowCountProjection {
        Long getArtistId();
        Long getCnt();
    }
}
