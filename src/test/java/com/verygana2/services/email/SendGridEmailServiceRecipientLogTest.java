package com.verygana2.services.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.verygana2.mappers.finance.MoneyMapper;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.testsupport.PublicUrlBuilders;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * RN-8 / CA-11: ningún correo que envía la API escribe la dirección del destinatario en los logs.
 * Recorre por reflexión todos los métodos públicos de envío cuyo primer parámetro es la dirección.
 */
@DisplayName("SendGridEmailService — la dirección del destinatario no aparece en los logs")
class SendGridEmailServiceRecipientLogTest {

    private static final String RECIPIENT = "ana.consumidora@example.com";

    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (logger != null) {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    private enum Scenario { SUCCESS, SENDGRID_REJECTS, IO_ERROR, TEMPLATE_ERROR }

    private SendGridEmailService build(Scenario scenario) throws Exception {
        SendGrid sendGrid = mock(SendGrid.class);
        EmailTemplateLoader loader = mock(EmailTemplateLoader.class);
        if (scenario == Scenario.TEMPLATE_ERROR) {
            when(loader.render(anyString(), anyMap())).thenThrow(new IllegalStateException("plantilla caída"));
        } else {
            when(loader.render(anyString(), anyMap())).thenReturn("<html/>");
        }
        if (scenario == Scenario.IO_ERROR) {
            when(sendGrid.api(any())).thenThrow(new IOException("sin red"));
        } else {
            Response response = new Response();
            response.setStatusCode(scenario == Scenario.SUCCESS ? 202 : 400);
            response.setBody("{\"errors\":[]}");
            when(sendGrid.api(any())).thenReturn(response);
        }
        SendGridEmailService service = new SendGridEmailService(sendGrid, mock(MoneyMapper.class), loader,
                mock(ProductCodeEncryptor.class), PublicUrlBuilders.forDomain("cdn-beta.verygana.com"));
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@test.com");
        ReflectionTestUtils.setField(service, "fromName", "verYganar");
        ReflectionTestUtils.setField(service, "supportEmail", "soporte@test.com");
        return service;
    }

    private void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger("com.verygana2");
        previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private static Object argFor(Class<?> type, boolean first) {
        if (type == String.class) {
            return first ? RECIPIENT : "dato";
        }
        if (type == int.class) return 1;
        if (type == long.class) return 1L;
        if (type == boolean.class) return false;
        if (type == ZonedDateTime.class) return ZonedDateTime.now();
        if (type.isEnum()) return type.getEnumConstants()[0];
        return null;
    }

    private static List<Method> recipientFirstMethods() {
        return Arrays.stream(SendGridEmailService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getParameterCount() > 0 && m.getParameterTypes()[0] == String.class)
                .toList();
    }

    private List<String> loggedTexts() {
        List<String> texts = new ArrayList<>();
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
    @DisplayName("se cubren todos los métodos de envío con dirección (guardia contra métodos nuevos sin cubrir)")
    void coversEveryRecipientMethod() {
        assertThat(recipientFirstMethods().size()).isGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("éxito, rechazo de SendGrid, IOException y error de plantilla: ningún log lleva la dirección")
    void noMethodLogsRecipientAddress() throws Exception {
        for (Scenario scenario : Scenario.values()) {
            for (Method method : recipientFirstMethods()) {
                SendGridEmailService service = build(scenario);
                captureLogs();
                Class<?>[] types = method.getParameterTypes();
                Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    args[i] = argFor(types[i], i == 0);
                }
                method.invoke(service, args);

                assertThat(appender.list).as("%s / %s emite algún log", method.getName(), scenario).isNotEmpty();
                assertThat(loggedTexts()).as("%s / %s", method.getName(), scenario)
                        .noneMatch(t -> t.contains(RECIPIENT));
                tearDown();
                logger = null;
            }
        }
    }
}
