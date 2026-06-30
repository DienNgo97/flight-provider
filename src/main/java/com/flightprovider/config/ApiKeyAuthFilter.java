package com.flightprovider.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bao ve moi endpoint /api/* bang header X-API-KEY.
 *
 * <p>PROV-X1: chi cac path duoi {@code /api/} duoc bao ve bang API key. Cac
 * trang {@code /admin/**} cua provider nay la <b>read-only</b> (home / search /
 * detail) — khong co thao tac POST pha huy — nen rui ro thap va co tinh
 * de public cho dev. Neu sau nay them bat ky thao tac ghi nao duoi
 * {@code /admin/**} thi PHAI dua chung vao dien bao ve (auth/CSRF) o day.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-KEY";

    private final String validKey;

    public ApiKeyAuthFilter(@Value("${app.api-key}") String validKey) {
        this.validKey = validKey;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/")) {
            String provided = request.getHeader(HEADER);
            if (!keysMatch(validKey, provided)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Invalid API key\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * PROV-X2: constant-time comparison via {@link MessageDigest#isEqual} so the
     * key check does not leak length / prefix information through a timing
     * oracle (unlike {@code String.equals}, which short-circuits).
     */
    private static boolean keysMatch(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
