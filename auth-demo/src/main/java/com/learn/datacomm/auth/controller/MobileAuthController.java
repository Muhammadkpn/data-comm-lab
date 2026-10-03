package com.learn.datacomm.auth.controller;

import com.learn.datacomm.auth.jwt.InvalidTokenException;
import com.learn.datacomm.auth.jwt.JwtService;
import com.learn.datacomm.auth.jwt.RefreshTokenStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoint token untuk mobile app. Mirip "Resource Owner Password" yang disederhanakan --
 * di production mobile app sebaiknya memakai OAuth 2.0 Authorization Code + PKCE lewat
 * browser sistem, supaya password tidak pernah diketik di dalam app.
 *
 *   POST /mobile/auth/login    username+password  -> access token (pendek) + refresh token (panjang)
 *   POST /mobile/auth/refresh  refresh token      -> pasangan token BARU (rotation)
 */
@RestController
@RequestMapping("/mobile/auth")
public class MobileAuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService users;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokens;

    public MobileAuthController(AuthenticationManager authenticationManager, UserDetailsService users,
                                JwtService jwtService, RefreshTokenStore refreshTokens) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.get("username"), body.get("password")));
            System.out.println("[SERVER] Login mobile sukses: " + auth.getName());
            return ResponseEntity.ok(tokens(auth.getName(), auth.getAuthorities(), refreshTokens.issue(auth.getName())));
        } catch (AuthenticationException e) {
            // Pesan sengaja generik: jangan bocorkan apakah username-nya ada atau tidak.
            return ResponseEntity.status(401).body(error("invalid_grant", "Username atau password salah"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        try {
            RefreshTokenStore.Rotation rotation = refreshTokens.rotate(body.get("refreshToken"));
            Collection<? extends GrantedAuthority> authorities =
                    users.loadUserByUsername(rotation.getUsername()).getAuthorities();
            System.out.println("[SERVER] Refresh token dirotasi untuk " + rotation.getUsername());
            return ResponseEntity.ok(tokens(rotation.getUsername(), authorities, rotation.getNewRefreshToken()));
        } catch (InvalidTokenException e) {
            return ResponseEntity.status(401).body(error("invalid_grant", e.getMessage()));
        }
    }

    private Map<String, Object> tokens(String username, Collection<? extends GrantedAuthority> authorities,
                                       String refreshToken) {
        String scope = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("SCOPE_"))
                .map(a -> a.substring("SCOPE_".length()))
                .collect(Collectors.joining(" "));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("tokenType", "Bearer");
        res.put("accessToken", jwtService.issueAccessToken(username, scope));
        res.put("expiresIn", jwtService.getAccessTtlSeconds());
        res.put("refreshToken", refreshToken);
        res.put("scope", scope);
        return res;
    }

    private static Map<String, Object> error(String code, String description) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("error", code);
        res.put("error_description", description);
        return res;
    }
}
