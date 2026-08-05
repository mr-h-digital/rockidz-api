package co.za.rockmission.learn.dto;

public record AdminUserRoleResponse(
        Long userId,
        String email,
        String displayName,
        String role,
        boolean active
) {
}
