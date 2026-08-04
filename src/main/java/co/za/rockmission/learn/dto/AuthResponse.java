package co.za.rockmission.learn.dto;

public record AuthResponse(
        String token,
        Long userId,
        String email,
        String displayName,
        String role
) {}
