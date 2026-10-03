package com.learn.datacomm.auth.config;

import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.http.HttpServletResponse;

/**
 * Error untuk Bearer token mengikuti RFC 6750: alasan penolakan ada di header
 * `WWW-Authenticate`, dan BEDA status untuk dua kasus yang sering tertukar:
 *
 *   401 Unauthorized  "siapa kamu?"     token tidak ada / rusak / kedaluwarsa / dicabut
 *                                       -> client sebaiknya refresh token atau login ulang
 *   403 Forbidden     "aku tahu kamu, tapi tidak boleh"  scope tidak cukup
 *                                       -> refresh token TIDAK akan membantu
 */
final class BearerErrorHandlers {

    private BearerErrorHandlers() {
    }

    static AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            Object reason = request.getAttribute(BearerTokenFilter.ERROR_ATTRIBUTE);
            String header = reason == null
                    ? "Bearer realm=\"mobile\""
                    : "Bearer realm=\"mobile\", error=\"invalid_token\", error_description=\"" + reason + "\"";
            response.setHeader("WWW-Authenticate", header);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }

    static AccessDeniedHandler accessDenied() {
        return (request, response, ex) -> {
            response.setHeader("WWW-Authenticate",
                    "Bearer realm=\"mobile\", error=\"insufficient_scope\", scope=\"transfers:write\"");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        };
    }
}
