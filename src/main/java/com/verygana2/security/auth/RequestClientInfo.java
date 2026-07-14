package com.verygana2.security.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resuelve IP y User-Agent de un request, respetando X-Forwarded-For cuando
 * la app está detrás de un proxy/load balancer.
 */
public final class RequestClientInfo {

    private RequestClientInfo() {}

    public static String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

    public static String resolveUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
