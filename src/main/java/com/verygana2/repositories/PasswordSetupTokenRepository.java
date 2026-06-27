package com.verygana2.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.verygana2.models.PasswordSetupToken;
import com.verygana2.models.User;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, Long> {

    Optional<PasswordSetupToken> findByToken(String token);

    @Modifying
    void deleteByUser(User user);

    @Modifying
    void deleteByExpiresAtBefore(Instant cutoff);
}
