package com.verygana2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

@Configuration
public class MultipartConfig {

    @Bean(name = "multipartResolver")
    public MultipartResolver multipartResolver() {
        // Use the servlet 3.0 based multipart resolver; limits and encoding should be set
        // via application.properties (spring.servlet.multipart.*) or the servlet container.
        return new StandardServletMultipartResolver();
    }
}
