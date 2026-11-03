package com.verygana2.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * El IVA de VERYGANA se enlaza a TreasuryConfig bajo "treasury.distribution".
 * Si la clave queda en otro bloque, Spring la ignora sin avisar y rige el default
 * de Java: cambiar la tarifa en el yml no tendría ningún efecto.
 */
class TreasuryConfigYamlTest {

    @ParameterizedTest
    @ValueSource(strings = { "application-dev.yml", "application-prod.yml" })
    void vatPctLivesUnderTreasuryDistribution(String file) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(file, new ClassPathResource(file));

        assertThat(sources).anySatisfy(source ->
                assertThat(source.getProperty("treasury.distribution.vat-pct")).isNotNull());
        assertThat(sources).allSatisfy(source ->
                assertThat(source.getProperty("treasury.issuance-settlement.vat-pct")).isNull());
    }
}
