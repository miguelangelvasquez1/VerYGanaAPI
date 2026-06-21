package com.verygana2.config.systemFeatures;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final FeatureFlagInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(interceptor).excludePathPatterns(

            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",

            "/auth/login",
            "/auth/refresh",

            "/public/**"
        );
    }
}