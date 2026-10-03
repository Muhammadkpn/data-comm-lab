package com.learn.datacomm.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pola EBizbanking: browser login sekali, server menyimpan identitas di SESSION
 * (memori server), browser hanya memegang cookie JSESSIONID.
 *
 *   + Logout/cabut akses instan: hapus session di server.
 *   + Cookie HttpOnly tidak bisa dicuri JavaScript (beda dengan token di localStorage).
 *   - Server stateful: scale-out butuh sticky session atau session store bersama (Redis).
 *   - Karena browser melampirkan cookie OTOMATIS, butuh proteksi CSRF untuk request yang
 *     mengubah state (POST/PUT/PATCH/DELETE).
 */
@RestController
@RequestMapping("/web")
public class WebController {

    private final AuthenticationManager authenticationManager;

    public WebController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.get("username"), body.get("password")));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).build();
        }

        // Session fixation: kalau session sudah ada sebelum login, ganti id-nya. Penyerang
        // yang sempat "menanam" session id lama ke korban tidak ikut ter-login.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        System.out.println("[SERVER] Login web sukses: " + auth.getName() + " -> session dibuat");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("user", auth.getName());
        res.put("message", "Session aktif. Ambil token CSRF dari GET /web/csrf untuk request yang mengubah data.");
        return ResponseEntity.ok(res);
    }

    /** Token CSRF juga dikirim sebagai cookie XSRF-TOKEN; endpoint ini memudahkan demo curl. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        Map<String, String> res = new LinkedHashMap<>();
        res.put("headerName", token.getHeaderName());
        res.put("token", token.getToken());
        return res;
    }

    @GetMapping("/accounts/me")
    public Map<String, Object> me(Authentication auth, HttpSession session) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("user", auth.getName());
        String id = session.getId();
        res.put("sessionId", id.length() > 8 ? id.substring(0, 8) + "..." : id);
        return res;
    }

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> transfer(Authentication auth, @RequestBody Map<String, Object> body) {
        System.out.println("[SERVER] Transfer web oleh " + auth.getName() + " (CSRF token valid)");
        Map<String, Object> res = new LinkedHashMap<>(body);
        res.put("status", "SUCCESS");
        res.put("initiatedBy", auth.getName());
        return ResponseEntity.status(201).body(res);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate(); // langsung berlaku: cookie lama tidak berguna lagi
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
