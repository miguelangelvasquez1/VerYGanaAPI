package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.game.InitGameRequestDTO;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.Game.DeliveryType;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.services.games.GameServiceImpl;

import jakarta.persistence.EntityManager;

/**
 * La URL de la preview tiene que quedar con un solo esquema.
 *
 * {@code generateGameUrl} antepone {@code https://} al host configurado. Con
 * {@code GAMES_CDN_URL=https://cdn.verygana.com} —que es como estaba el 2026-09-24—
 * la URL salía {@code https://https://cdn.verygana.com/…}: el navegador ni la intenta
 * abrir, la preview no arranca y el backend no reporta nada, porque para él la
 * petición nunca existió.
 */
@ExtendWith(MockitoExtension.class)
class PreviewUrlHostTest {

    @Mock private GameRepository gameRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks private GameServiceImpl gameService;

    private String previewUrl(String configurado, DeliveryType tipo, String slug) {
        ReflectionTestUtils.setField(gameService, "cdnUrl", configurado);

        BrandingRequest request = BrandingRequest.builder()
            .id(14L)
            .game(Game.builder().id(5L).title("Hangman").url(slug).deliveryType(tipo).build())
            .build();

        return gameService.generatePreviewUrl(request);
    }

    @Test
    @DisplayName("el host configurado con esquema no lo duplica")
    void doesNotDoubleTheScheme() {
        String url = previewUrl("https://games.verygana.com", DeliveryType.QUERY, "Hangman");

        assertThat(url).startsWith("https://games.verygana.com/");
        assertThat(url).doesNotContain("https://https://");
    }

    @Test
    @DisplayName("el host sin esquema funciona igual")
    void plainHostWorks() {
        assertThat(previewUrl("games.verygana.com", DeliveryType.QUERY, "Hangman"))
            .startsWith("https://games.verygana.com/");
    }

    @Test
    @DisplayName("una barra final de más no parte la ruta")
    void trailingSlashIsTrimmed() {
        assertThat(previewUrl("https://games.verygana.com/", DeliveryType.PATH, "trivia-quiz"))
            .startsWith("https://games.verygana.com/builds/")
            .doesNotContain("com//");
    }

    @Test
    @DisplayName("sin patrocinio: ni campaign_id ni modo brandeado")
    void notSponsoredPlaysTheGameDefaults() {
        // El juego tiene que arrancar con su contenido de fábrica. Antes iba con
        // is_branded_mode=true y campaign_id=20 fijo, así que pedía la configuración de
        // una campaña ajena —y, desde que no existe, un 400.
        ReflectionTestUtils.setField(gameService, "cdnUrl", "games.verygana.com");
        when(gameRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(
            Game.builder().id(5L).title("Hangman").url("Hangman").deliveryType(DeliveryType.QUERY).build()));

        InitGameRequestDTO request = new InitGameRequestDTO();
        request.setGameId(5L);

        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(77L);
        consumer.setUserHash("hash-del-consumidor");
        when(entityManager.find(ConsumerDetails.class, 77L)).thenReturn(consumer);
        // @InjectMocks usa el constructor y no llena el campo @PersistenceContext.
        ReflectionTestUtils.setField(gameService, "entityManager", entityManager);

        String url = gameService.initGameNotSponsored(request, 77L);

        assertThat(url).contains("is_branded_mode=false");
        assertThat(url).doesNotContain("campaign_id");
        // El hash, como en el camino patrocinado: nunca el id interno del usuario.
        assertThat(url).contains("user_hash=hash-del-consumidor");
        assertThat(url).doesNotContain("user_hash=77");
    }

    @Test
    @DisplayName("cali lleva el juego en game_title; bogotá, en la ruta")
    void perDeliveryType() {
        assertThat(previewUrl("games.verygana.com", DeliveryType.QUERY, "Hangman"))
            .contains("build-cali").contains("game_title=Hangman");
        assertThat(previewUrl("games.verygana.com", DeliveryType.PATH, "trivia-quiz"))
            .contains("build-bogota/").contains("/trivia-quiz/?");
    }
}
