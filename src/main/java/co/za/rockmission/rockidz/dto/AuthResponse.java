package co.za.rockmission.rockidz.dto;

public record AuthResponse(
        String token,
        Long userId,
        String email,
        String displayName,
        String role
) {}
