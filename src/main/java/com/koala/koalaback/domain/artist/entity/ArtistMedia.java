package com.koala.koalaback.domain.artist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "artist_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(nullable = false, length = 20)
    private String mediaType;

    @Column(nullable = false, length = 30)
    private String mediaRole;

    @Column(nullable = false, length = 700)
    private String fileUrl;

    @Column(length = 700)
    private String thumbnailUrl;

    @Column(length = 200)
    private String title;

    @Lob
    private String description;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(columnDefinition = "JSON")
    private String metaJson;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public ArtistMedia(Artist artist, String mediaType, String mediaRole,
                       String fileUrl, String thumbnailUrl, String title,
                       String description, Integer sortOrder, String metaJson) {
        this.artist = artist;
        this.mediaType = mediaType;
        this.mediaRole = mediaRole;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.metaJson = metaJson;
    }

    public void updateSort(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void updateThumbnail(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
