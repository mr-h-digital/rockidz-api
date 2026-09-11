package co.za.rockmission.rockidz.dto;

import co.za.rockmission.rockidz.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(
        @Email String email,
        @NotNull Role role
) {
}
