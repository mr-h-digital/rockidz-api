package co.za.rockmission.learn.dto;

public record ForgotPasswordResponse(
        String message,
        String resetToken,
        String resetUrl
) {
}
