package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.AdminUserRoleResponse;
import co.za.rockmission.rockidz.dto.AdminUserRoleUpdateRequest;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only user role management.
 *
 * Current use case: promote a registered student to EDUCATOR so they can create
 * and publish courses from the Teach workflow.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    @PatchMapping("/users/role")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserRoleResponse updateUserRole(@Valid @RequestBody AdminUserRoleUpdateRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found for email: " + normalizedEmail));

        user.setRole(request.role());
        userRepository.save(user);

        return new AdminUserRoleResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isActive()
        );
    }
}
