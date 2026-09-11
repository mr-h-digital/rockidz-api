package co.za.rockmission.rockidz.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
        String displayName,
        @Email String email,
        @Size(min = 8, message = "New password must be at least 8 characters") String newPassword,
        String currentPassword
) {
}
