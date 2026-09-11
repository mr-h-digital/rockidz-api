package co.za.rockmission.rockidz.dto;

public record UserProfileResponse(
        Long userId,
        String email,
        String displayName,
        String avatarUrl,
        String role,
        boolean active
) {
}
