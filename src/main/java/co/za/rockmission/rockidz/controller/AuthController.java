package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.AuthResponse;
import co.za.rockmission.rockidz.dto.ForgotPasswordRequest;
import co.za.rockmission.rockidz.dto.ForgotPasswordResponse;
import co.za.rockmission.rockidz.dto.LoginRequest;
import co.za.rockmission.rockidz.dto.ResetPasswordRequest;
import co.za.rockmission.rockidz.dto.SignupRequest;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.PasswordResetToken;
import co.za.rockmission.rockidz.model.Role;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.PasswordResetTokenRepository;
import co.za.rockmission.rockidz.repository.UserRepository;
import co.za.rockmission.rockidz.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Sign-up and sign-in for both students and educators.
 * New accounts default to STUDENT — promoting a user to EDUCATOR is an admin action
 * (see AdminController, added in a later phase), not something a user can self-select
 * at sign-up.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.reset-token-minutes:30}")
    private long resetTokenMinutes;

    @Value("${app.auth.expose-reset-token:false}")
    private boolean exposeResetToken;

    @Value("${app.auth.reset-base-url:https://rockidz.rockmission.co.za/reset-password}")
    private String resetBaseUrl;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        User user = User.builder()
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName().trim())
                .role(Role.STUDENT)
                .active(true)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAuthResponse(user, token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email().toLowerCase().trim(),
                        request.password()
                )
        );

        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(toAuthResponse(user, token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        String exposedToken = null;
        String exposedResetUrl = null;

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user != null) {
            revokeOutstandingResetTokens(user);

            String plainToken = generateResetToken();
            String tokenHash = sha256(plainToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plusSeconds(resetTokenMinutes * 60))
                    .build();

            passwordResetTokenRepository.save(resetToken);

            if (exposeResetToken) {
                exposedToken = plainToken;
                exposedResetUrl = resetBaseUrl + "?token=" + plainToken;
            }
        }

        return ResponseEntity.ok(new ForgotPasswordResponse(
                "If an account exists for this email, a reset link has been prepared.",
                exposedToken,
                exposedResetUrl
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String tokenHash = sha256(request.token().trim());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        revokeOutstandingResetTokens(user);

        return ResponseEntity.ok(Map.of("message", "Password reset successful. Please sign in."));
    }

    private AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }

    private void revokeOutstandingResetTokens(User user) {
        var tokens = passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
        Instant now = Instant.now();
        tokens.forEach(t -> t.setUsedAt(now));
        passwordResetTokenRepository.saveAll(tokens);
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
