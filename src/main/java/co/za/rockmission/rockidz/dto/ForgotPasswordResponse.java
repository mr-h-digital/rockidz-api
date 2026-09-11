package co.za.rockmission.rockidz.dto;

public record ForgotPasswordResponse(
        String message,
        String resetToken,
        String resetUrl
) {
}
