package com.verygana2.config.systemFeatures;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.verygana2.security.systemFeatures.FeatureFlagService;

import jakarta.servlet.http.*;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeatureFlagInterceptor
        implements HandlerInterceptor {

    private final FeatureFlagService service;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        service.validateFeature(request.getRequestURI(), request.getMethod());
        return true;
    }
}