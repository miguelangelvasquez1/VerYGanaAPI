package com.verygana2.services.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.sendgrid.SendGrid;
import com.verygana2.mappers.finance.MoneyMapper;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.testsupport.PublicUrlBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendGridEmailService — correo de reclamo de premio")
class SendGridEmailServicePrizeClaimLogTest {

    @Mock private SendGrid sendGrid;
    @Mock private MoneyMapper moneyMapper;
    @Mock private EmailTemplateLoader templateLoader;
    @Mock private ProductCodeEncryptor productCodeEncryptor;

    private static final String CONSUMER_EMAIL = "ana.consumidora@example.com";

    private SendGridEmailService service;
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        service = new SendGridEmailService(sendGrid, moneyMapper, templateLoader, productCodeEncryptor,
                PublicUrlBuilders.forDomain("cdn-beta.verygana.com"));
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@test.com");
        ReflectionTestUtils.setField(service, "fromName", "verYganar");
        ReflectionTestUtils.setField(service, "supportEmail", "soporte@test.com");
    }

    @AfterEach
    void tearDown() {
        if (logger != null) {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    private void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger("com.verygana2");
        previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private Prize prize() {
        Prize prize = new Prize();
        prize.setId(77L);
        prize.setTitle("Consola");
        return prize;
    }

    /** Mensaje formateado y mensajes de la cadena de causas de cada evento. */
    private List<String> loggedTexts() {
        List<String> texts = new java.util.ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            texts.add(event.getFormattedMessage());
            var t = event.getThrowableProxy();
            while (t != null) {
                texts.add(String.valueOf(t.getMessage()));
                t = t.getCause();
            }
        }
        return texts;
    }

    @Test
    @DisplayName("envío exitoso: ninguna línea de log lleva el correo del consumidor")
    void successDoesNotLogConsumerEmail() throws Exception {
        captureLogs();
        com.sendgrid.Response ok = new com.sendgrid.Response();
        ok.setStatusCode(202);
        when(templateLoader.render(eq("prize-claim.html"), anyMap())).thenReturn("<html/>");
        when(sendGrid.api(org.mockito.ArgumentMatchers.any())).thenReturn(ok);

        service.sendPrizeClaimConfirmation(prize(), CONSUMER_EMAIL, "CODE-1");

        assertThat(appender.list).isNotEmpty();
        assertThat(loggedTexts()).noneMatch(t -> t.contains(CONSUMER_EMAIL));
    }

    @Test
    @DisplayName("IOException de SendGrid: el log de error no lleva el correo del consumidor")
    void sendGridIoErrorDoesNotLogConsumerEmail() throws Exception {
        captureLogs();
        when(templateLoader.render(eq("prize-claim.html"), anyMap())).thenReturn("<html/>");
        when(sendGrid.api(org.mockito.ArgumentMatchers.any())).thenThrow(new IOException("sin red"));

        service.sendPrizeClaimConfirmation(prize(), CONSUMER_EMAIL, "CODE-1");

        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR);
        assertThat(loggedTexts()).noneMatch(t -> t.contains(CONSUMER_EMAIL));
    }

    @Test
    @DisplayName("falla de la plantilla: el log de error no lleva el correo del consumidor")
    void templateErrorDoesNotLogConsumerEmail() {
        captureLogs();
        when(templateLoader.render(eq("prize-claim.html"), anyMap())).thenThrow(new IllegalStateException("plantilla"));

        service.sendPrizeClaimConfirmation(prize(), CONSUMER_EMAIL, "CODE-1");

        assertThat(appender.list).anyMatch(e -> e.getLevel() == Level.ERROR);
        assertThat(loggedTexts()).noneMatch(t -> t.contains(CONSUMER_EMAIL));
    }

    @Test
    @DisplayName("la traza sigue siendo útil: aparece el id del premio")
    void logsPrizeId() {
        captureLogs();
        when(templateLoader.render(eq("prize-claim.html"), anyMap())).thenThrow(new IllegalStateException("plantilla"));

        service.sendPrizeClaimConfirmation(prize(), CONSUMER_EMAIL, "CODE-1");

        assertThat(loggedTexts()).anyMatch(t -> t.contains("77"));
    }

    @Test
    @DisplayName("la imagen del premio en el correo usa el CDN configurado")
    @SuppressWarnings("unchecked")
    void prizeImageUsesConfiguredCdn() throws Exception {
        Prize prize = new Prize();
        prize.setId(1L);
        prize.setTitle("Consola");
        prize.setImageAsset(PrizeImageAsset.builder().objectKey("prizes/p.png").build());
        when(templateLoader.render(eq("prize-claim.html"), anyMap())).thenReturn("<html/>");
        // sendEmail llega a sendGrid.api: sin red, falla cerrado y no afecta a lo que se verifica.
        when(sendGrid.api(org.mockito.ArgumentMatchers.any())).thenReturn(mock(com.sendgrid.Response.class));

        service.sendPrizeClaimConfirmation(prize, "ana.consumidora@example.com", "CODE-1");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(templateLoader).render(eq("prize-claim.html"), captor.capture());
        assertThat(captor.getValue().get("prizeImageSection"))
                .contains("https://cdn-beta.verygana.com/public/prizes/p.png");
    }
}
