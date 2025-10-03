package com.verygana2.security.auth.refreshToken;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.verygana2.models.User;

//NOT USED
@Service
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public Optional<RefreshToken> validateRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(rt -> !rt.getRevoked() && rt.getExpiresAt().isAfter(Instant.now()));
    }

    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.deleteByUsername(user);
    }
}
