package co.za.rockmission.rockidz.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    @Builder.Default
    private ContentType contentType = ContentType.VIDEO;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "questions", columnDefinition = "TEXT")
    private String questions;

    @Column(name = "asset_url")
    private String assetUrl;

    @Column(name = "download_url")
    private String downloadUrl;

    @Column(name = "game_type")
    private String gameType;

    @Column(name = "game_prompt", columnDefinition = "TEXT")
    private String gamePrompt;

    @Column(name = "game_options", columnDefinition = "TEXT")
    private String gameOptions;

    @Column(name = "game_answer", columnDefinition = "TEXT")
    private String gameAnswer;

    @Column(name = "success_message", columnDefinition = "TEXT")
    private String successMessage;

    @Column(name = "retry_message", columnDefinition = "TEXT")
    private String retryMessage;

    /** For YOUTUBE this is the video ID (e.g. "pm3kChroXDk"), not the full URL. */
    @Column(name = "video_ref")
    private String videoRef;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public enum ContentType {
        VIDEO,
        STORY,
        QUESTIONS,
        ACTIVITY,
        GAME,
        COLOURING_PAGE,
        DOWNLOAD
    }
}
