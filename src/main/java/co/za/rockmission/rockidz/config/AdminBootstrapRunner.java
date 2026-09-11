package co.za.rockmission.rockidz.config;

import co.za.rockmission.rockidz.model.Role;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        String email = requireValue(properties.email(), "ADMIN_EMAIL").toLowerCase().trim();
        String password = requireValue(properties.password(), "ADMIN_PASSWORD");
        String displayName = normalizedDisplayName(properties.displayName());

        User user = userRepository.findByEmail(email)
                .map(existing -> updateExistingAdmin(existing, password, displayName))
                .orElseGet(() -> createAdmin(email, password, displayName));

        userRepository.save(user);
    }

    private User updateExistingAdmin(User user, String password, String displayName) {
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return user;
    }

    private User createAdmin(String email, String password, String displayName) {
        return User.builder()
                .email(email)
                .displayName(displayName)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .active(true)
                .build();
    }

    private String normalizedDisplayName(String configuredDisplayName) {
        if (configuredDisplayName == null || configuredDisplayName.trim().isEmpty()) {
            return "Rock Mission Admin";
        }
        return configuredDisplayName.trim();
    }

    private String requireValue(String value, String envName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(envName + " must be configured when admin bootstrap is enabled.");
        }
        return value.trim();
    }
}
