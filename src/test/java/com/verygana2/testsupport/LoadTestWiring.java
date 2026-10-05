package com.verygana2.testsupport;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import com.twilio.http.TwilioRestClient;
import com.verygana2.config.zapsign.ZapSignConfig;
import com.verygana2.config.zapsign.ZapSignWebClientConfig;
import com.verygana2.security.ClaimCodeEncryptor;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.security.recaptcha.RecaptchaService;
import com.verygana2.services.TwilioSmsServiceImpl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Contexto mínimo (sin BD ni {@code @SpringBootTest}) para tests de aislamiento del modo
 * prueba de carga: escanea el paquete {@code loadtest} junto a las piezas reales que los
 * falsos reemplazan. Vive fuera del paquete {@code loadtest} para que el propio escaneo
 * no lo recoja.
 */
@Configuration
@ComponentScan("com.verygana2.loadtest")
@EnableConfigurationProperties
@Import({RecaptchaService.class, ZapSignConfig.class, ZapSignWebClientConfig.class, TwilioSmsServiceImpl.class})
public class LoadTestWiring {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    TwilioRestClient twilioRestClient() {
        return Mockito.mock(TwilioRestClient.class);
    }

    // Dependencias del sembrado (LoadTestSeeder): el contexto no tiene BD, y los runners
    // de ApplicationContextRunner no se ejecutan, así que basta con que existan.
    @Bean
    DataSource dataSource() {
        return Mockito.mock(DataSource.class);
    }

    @Bean
    JdbcTemplate jdbcTemplate() {
        return Mockito.mock(JdbcTemplate.class);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Mockito.mock(PasswordEncoder.class);
    }

    @Bean
    ProductCodeEncryptor productCodeEncryptor() {
        return Mockito.mock(ProductCodeEncryptor.class);
    }

    @Bean
    ClaimCodeEncryptor claimCodeEncryptor() {
        return Mockito.mock(ClaimCodeEncryptor.class);
    }
}
