package co.za.rockmission.learn.repository;

import co.za.rockmission.learn.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);
}
