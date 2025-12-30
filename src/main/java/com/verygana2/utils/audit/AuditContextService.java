package com.verygana2.utils.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servicio para extraer información de contexto para auditoría
 */
@Service
@Slf4j
public class AuditContextService {

    /**
     * Obtiene el ID del usuario actual desde Spring Security
     */
    public Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                
                // Asume que tu UserDetails custom tiene getId()
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    // Castear a tu UserDetails custom que tiene getId()
                    // return ((CustomUserDetails) principal).getId();
                    
                    // Fallback: extraer de username si es numérico
                    String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                    try {
                        return Long.parseLong(username);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                
                // Si el principal es directamente el username/id
                if (principal instanceof String) {
                    try {
                        return Long.parseLong((String) principal);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener user ID: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Obtiene el username del usuario actual
     */
    public String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener username: {}", e.getMessage());
        }
        
        return "anonymous";
    }

    /**
     * Obtiene el email del usuario actual
     */
    public String getCurrentUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                
                // Si tu UserDetails custom tiene getEmail()
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    // return ((CustomUserDetails) principal).getEmail();
                }
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener email: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Obtiene la IP del cliente
     */
    public String getClientIpAddress() {
        try {
            HttpServletRequest request = getCurrentHttpRequest();
            if (request == null) return null;

            // Intentar obtener IP real detrás de proxies
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For puede contener múltiples IPs, tomar la primera
                return ip.split(",")[0].trim();
            }

            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            return request.getRemoteAddr();

        } catch (Exception e) {
            log.debug("No se pudo obtener IP: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el User-Agent del cliente
     */
    public String getUserAgent() {
        try {
            HttpServletRequest request = getCurrentHttpRequest();
            if (request == null) return null;

            return request.getHeader("User-Agent");

        } catch (Exception e) {
            log.debug("No se pudo obtener User-Agent: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el Session ID
     */
    public String getSessionId() {
        try {
            HttpServletRequest request = getCurrentHttpRequest();
            if (request == null) return null;

            return request.getSession(false) != null ? 
                request.getSession().getId() : null;

        } catch (Exception e) {
            log.debug("No se pudo obtener Session ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el HttpServletRequest actual
     */
    private HttpServletRequest getCurrentHttpRequest() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            return attributes != null ? attributes.getRequest() : null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verifica si hay un contexto de request HTTP actual
     */
    public boolean hasHttpContext() {
        return getCurrentHttpRequest() != null;
    }
}