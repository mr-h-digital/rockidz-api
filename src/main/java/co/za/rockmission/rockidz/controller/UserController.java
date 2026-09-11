package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.UpdateMyProfileRequest;
import co.za.rockmission.rockidz.dto.UserProfileResponse;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal User currentUser) {
        return toProfile(currentUser);
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(
            @Valid @RequestBody UpdateMyProfileRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        boolean hasDisplayName = request.displayName() != null;
        boolean hasEmail = request.email() != null;
        boolean hasNewPassword = request.newPassword() != null && !request.newPassword().isBlank();

        if (!hasDisplayName && !hasEmail && !hasNewPassword) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide at least one field to update");
        }

        if (hasDisplayName) {
            String displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Display name cannot be blank");
            }
            currentUser.setDisplayName(displayName);
        }

        if (hasEmail) {
            String normalizedEmail = request.email().toLowerCase().trim();
            if (!normalizedEmail.equals(currentUser.getEmail()) && userRepository.existsByEmail(normalizedEmail)) {
                throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
            }
            currentUser.setEmail(normalizedEmail);
        }

        if (hasNewPassword) {
            String currentPassword = request.currentPassword() == null ? "" : request.currentPassword();
            if (!passwordEncoder.matches(currentPassword, currentUser.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
            }
            currentUser.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }

        userRepository.save(currentUser);
        return toProfile(currentUser);
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isActive()
        );
    }
}
