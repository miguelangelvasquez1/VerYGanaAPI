package com.verygana2.loadtest.stubs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.verygana2.loadtest.LoadTestProperties;
import com.verygana2.services.interfaces.EmailService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("LoadTestEmailService - SendGrid falso con contador")
class LoadTestEmailServiceTest {

    private static final String RECIPIENT = "persona.secreta@loadtest.invalid";

    private SimpleMeterRegistry registry;
    private LoadTestEmailService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        LoadTestProperties properties = new LoadTestProperties();
        properties.getStubs().setEmailLatencyMs(0);
        service = new LoadTestEmailService(properties, new StubCallCounter(registry));
    }

    private static List<Method> emailMethods() {
        return Arrays.stream(EmailService.class.getMethods()).toList();
    }

    /** Argumentos de relleno por tipo: texto con el destinatario, ceros, la primera constante de un enum, mocks. */
    private static Object[] argumentsFor(Method method) {
        return Arrays.stream(method.getParameterTypes()).map(type -> {
            if (type == String.class) {
                return RECIPIENT;
            } else if (type == int.class) {
                return 0;
            } else if (type == long.class) {
                return 0L;
            } else if (type == boolean.class) {
                return false;
            } else if (type == ZonedDateTime.class) {
                return ZonedDateTime.now();
            } else if (type.isEnum()) {
                return type.getEnumConstants()[0];
            }
            return mock(type);
        }).toArray();
    }

    private void invoke(Method method) {
        try {
            method.invoke(service, argumentsFor(method));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Falló " + method.getName(), e);
        }
    }

    @Test
    @DisplayName("cada método de EmailService suma exactamente uno al contador de correo")
    void everyMethodCountsAsEmailCall() {
        List<Method> methods = emailMethods();
        assertThat(methods).isNotEmpty();

        double expected = 0;
        for (Method method : methods) {
            invoke(method);
            expected++;
            assertThat(registry.get("loadtest.stub.calls").tag("provider", "email").counter().count())
                    .as(method.getName())
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("ningún método escribe el destinatario en el log")
    void neverLogsRecipient() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.verygana2");
        Level previous = logger.getLevel();
        logger.setLevel(Level.TRACE);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            emailMethods().forEach(this::invoke);

            assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
                    .noneMatch(m -> m.contains("persona.secreta") || m.contains(RECIPIENT));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }
}
