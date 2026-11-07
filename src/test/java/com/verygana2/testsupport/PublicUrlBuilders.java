package com.verygana2.testsupport;

import com.verygana2.storage.config.R2Config;
import com.verygana2.storage.service.PublicUrlBuilder;

/** Fixture: arma un {@link PublicUrlBuilder} real para un dominio de CDN dado. */
public final class PublicUrlBuilders {

    private PublicUrlBuilders() {
    }

    public static PublicUrlBuilder forDomain(String cdnDomain) {
        R2Config config = new R2Config();
        config.setCdnDomain(cdnDomain);
        return new PublicUrlBuilder(config);
    }
}
