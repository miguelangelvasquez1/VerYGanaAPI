package com.verygana2.loadtest.stubs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.verygana2.loadtest.LoadTestProperties;
import com.verygana2.models.raffles.Prize;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("LoadTestSmsService - Twilio falso con contador")
class LoadTestSmsServiceTest {

    private static final String PHONE = "3009998877";

    private SimpleMeterRegistry registry;
    private LoadTestSmsService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        LoadTestProperties properties = new LoadTestProperties();
        properties.getStubs().setOtpCode("424242");
        properties.getStubs().setSmsLatencyMs(0);
        service = new LoadTestSmsService(properties, new StubCallCounter(registry));
    }

    private double smsCalls() {
        var counter = registry.find("loadtest.stub.calls").tag("provider", "sms").counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("verifyOtp acepta solo el código configurado")
    void verifyOtpAcceptsOnlyConfiguredCode() {
        assertThat(service.verifyOtp(PHONE, "424242")).isTrue();
        assertThat(service.verifyOtp(PHONE, "000000")).isFalse();
        assertThat(service.verifyOtp(PHONE, "")).isFalse();
        assertThat(service.verifyOtp(PHONE, null)).isFalse();
    }

    @Test
    @DisplayName("sendOtp y la confirmación de premio suman al contador y no escriben el teléfono")
    void sendOtpCountsCallAndDoesNotLogPhone() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.verygana2");
        Level previous = logger.getLevel();
        logger.setLevel(Level.TRACE);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sendOtp(PHONE);
            service.sendPrizeClaimConfirmation(mock(Prize.class), PHONE, "CODIGO-SECRETO");
            service.verifyOtp(PHONE, "424242");

            List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).noneMatch(m -> m.contains(PHONE) || m.contains("CODIGO-SECRETO"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }

        assertThat(smsCalls()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("espera la latencia configurada")
    void waitsConfiguredLatency() {
        LoadTestProperties properties = new LoadTestProperties();
        properties.getStubs().setSmsLatencyMs(60);
        LoadTestSmsService slow = new LoadTestSmsService(properties, new StubCallCounter(registry));

        long start = System.nanoTime();
        slow.sendOtp(PHONE);

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(55);
    }
}
