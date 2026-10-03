package com.learn.datacomm.auth.config;

import com.learn.datacomm.auth.jwt.InvalidTokenException;
import com.learn.datacomm.auth.jwt.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Membaca `Authorization: Bearer <jwt>`, memverifikasinya, lalu menaruh identitas user
 * di SecurityContext untuk request INI SAJA. Tidak ada session yang dibuat -- request
 * berikutnya harus membawa token lagi (stateless).
 *
 * Setiap scope di token ("accounts:read transfers:write") menjadi authority
 * "SCOPE_accounts:read", dst -- konvensi yang sama dengan Spring Resource Server.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    static final String ERROR_ATTRIBUTE = "bearer.error";

    private final JwtService jwtService;

    public BearerTokenFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Map<String, Object> claims = jwtService.verify(header.substring(7));
                List<GrantedAuthority> authorities = new ArrayList<>();
                for (String scope : String.valueOf(claims.get("scope")).split(" ")) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(claims.get("sub"), null, authorities);
                auth.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (InvalidTokenException e) {
                // Jangan langsung tulis response di sini; biarkan entry point yang
                // menjawab 401 dengan header WWW-Authenticate yang benar.
                System.out.println("[SERVER] Bearer token ditolak: " + e.getMessage());
                request.setAttribute(ERROR_ATTRIBUTE, e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
