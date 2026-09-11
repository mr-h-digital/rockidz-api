package co.za.rockmission.rockidz.dto;

public record AdminUserRoleResponse(
        Long userId,
        String email,
        String displayName,
        String role,
        boolean active
) {
}
