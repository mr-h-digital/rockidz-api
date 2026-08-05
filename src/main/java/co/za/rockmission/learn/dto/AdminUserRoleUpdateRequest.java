package co.za.rockmission.learn.dto;

import co.za.rockmission.learn.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(
        @Email String email,
        @NotNull Role role
) {
}
