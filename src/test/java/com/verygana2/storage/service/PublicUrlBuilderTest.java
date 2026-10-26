package com.verygana2.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.verygana2.storage.config.R2Config;
import com.verygana2.testsupport.PublicUrlBuilders;

@DisplayName("PublicUrlBuilder")
class PublicUrlBuilderTest {

    @Test
    @DisplayName("arma la URL con el dominio del CDN configurado")
    void buildsUrlWithConfiguredDomain() {
        PublicUrlBuilder builder = PublicUrlBuilders.forDomain("cdn-beta.verygana.com");

        assertThat(builder.toPublicUrl("products/commercial-1/a.jpg"))
                .isEqualTo("https://cdn-beta.verygana.com/public/products/commercial-1/a.jpg");
    }

    @Test
    @DisplayName("una key nula da URL nula")
    void returnsNullForNullKey() {
        assertThat(PublicUrlBuilders.forDomain("cdn-beta.verygana.com").toPublicUrl(null)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    @DisplayName("sin dominio falla al construir, sin caer a r2.dev ni mostrar el accountId")
    void failsWithoutDomain(String domain) {
        R2Config config = new R2Config();
        config.setAccountId("acct-secreto-123");
        config.setCdnDomain(domain);

        assertThatThrownBy(() -> new PublicUrlBuilder(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_CDN_DOMAIN")
                .hasMessageContaining("cloudflare.r2.cdn-domain")
                .hasMessageNotContaining("acct-secreto-123")
                .hasMessageNotContaining("r2.dev");
    }

    @Test
    @DisplayName("recorta los espacios del dominio")
    void trimsConfiguredDomain() {
        assertThat(PublicUrlBuilders.forDomain(" cdn-beta.verygana.com ").toPublicUrl("k.png"))
                .isEqualTo("https://cdn-beta.verygana.com/public/k.png");
    }
}
