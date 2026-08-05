package co.za.rockmission.learn.dto;

public record UserProfileResponse(
        Long userId,
        String email,
        String displayName,
        String role,
        boolean active
) {
}
