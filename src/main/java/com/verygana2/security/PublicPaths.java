package com.verygana2.security;


public class PublicPaths {
    public static final String [] PATHS = {
        "/avatars",
        "/categories/all",
        "/products/filter",
        "/products/{id}",
        "/legal-documents/**",
        "/auth/**",
        "/users/exists/**",
        "/locations/**",
        "/api/webhooks/**",
        "/wompi/events",
        "/wompi/payouts/events",
        "/zapsign/events",
        "/test/wompi/**",
        "/ads/assets/orphan/**",
        "/games/assets",
        "/games/metrics",
        "/game-designers/password/reset",
        "/api/payments/webhook",
        "/api/raffles/**",
        "/ws/**",
        "/ws/info/**",
            "/pet/catalog",
            "/pet/scenes",
            "/pet/scenes-objects",
            "/pet/notifications",
            "/pet/notifications/*/read",
            "/api/levels/config"
        };
}
