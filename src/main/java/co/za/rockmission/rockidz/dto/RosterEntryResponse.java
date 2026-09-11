package co.za.rockmission.rockidz.dto;

import java.time.Instant;

public record RosterEntryResponse(
        Long userId,
        String displayName,
        String email,
        String enrollmentStatus,
        Instant enrolledAt,
        long completedLessons,
        long totalLessons
) {}
