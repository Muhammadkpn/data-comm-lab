package com.learn.datacomm.auth.controller;

import com.learn.datacomm.auth.jwt.JwtService;
import com.learn.datacomm.auth.jwt.RefreshTokenStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resource yang dilindungi Bearer JWT. Aturan scope ada di SecurityConfig.mobileChain. */
@RestController
@RequestMapping("/mobile")
public class MobileResourceController {

    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokens;

    public MobileResourceController(JwtService jwtService, RefreshTokenStore refreshTokens) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    @GetMapping("/accounts/me")
    public Map<String, Object> me(Authentication auth) {
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) auth.getDetails();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("user", auth.getName());
        res.put("scope", claims.get("scope"));
        res.put("tokenExpiresAt", claims.get("exp"));
        return res;
    }

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> transfer(Authentication auth, @RequestBody Map<String, Object> body) {
        System.out.println("[SERVER] Transfer oleh " + auth.getName() + ": " + body);
        Map<String, Object> res = new LinkedHashMap<>(body);
        res.put("status", "SUCCESS");
        res.put("initiatedBy", auth.getName());
        return ResponseEntity.status(201).body(res);
    }

    /**
     * Logout untuk token stateless butuh STATE tambahan: jti access token masuk denylist,
     * dan refresh token dicabut. Tanpa ini, access token tetap berlaku sampai exp.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication auth, @RequestBody(required = false) Map<String, String> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) auth.getDetails();
        jwtService.revoke(String.valueOf(claims.get("jti")));
        if (body != null && body.get("refreshToken") != null) {
            refreshTokens.revoke(body.get("refreshToken"));
        }
        System.out.println("[SERVER] Logout " + auth.getName() + " -> jti masuk denylist, refresh token dicabut");
        return ResponseEntity.noContent().build();
    }
}
