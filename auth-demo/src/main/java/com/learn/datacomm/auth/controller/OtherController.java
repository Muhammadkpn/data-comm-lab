package com.learn.datacomm.auth.controller;

import com.learn.datacomm.auth.jwt.InvalidTokenException;
import com.learn.datacomm.auth.jwt.NaiveJwtVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class OtherController {

    private final NaiveJwtVerifier naiveVerifier;

    public OtherController(NaiveJwtVerifier naiveVerifier) {
        this.naiveVerifier = naiveVerifier;
    }

    /** API key (X-API-Key) -- identitasnya PARTNER, bukan user. */
    @GetMapping("/partner/exchange-rates")
    public Map<String, Object> rates(Authentication auth) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("partner", auth.getName());
        res.put("USD_IDR", 16_250);
        res.put("SGD_IDR", 12_100);
        return res;
    }

    /** HTTP Basic -- username:password base64 di SETIAP request. Hanya aman di atas HTTPS. */
    @GetMapping("/internal/health-detail")
    public Map<String, Object> health(Authentication auth) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("checkedBy", auth.getName());
        res.put("db", "UP");
        res.put("coreBanking", "UP");
        return res;
    }

    /** Endpoint dengan verifier JWT yang RENTAN alg=none. Lihat NaiveJwtVerifier. */
    @GetMapping("/naive/accounts/me")
    public ResponseEntity<Map<String, Object>> naive(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        try {
            Map<String, Object> claims = naiveVerifier.verify(header.substring(7));
            System.out.println("[SERVER] (naive) token diterima untuk sub=" + claims.get("sub"));
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("user", claims.get("sub"));
            res.put("warning", "Verifier naive menerima token ini");
            return ResponseEntity.ok(res);
        } catch (InvalidTokenException e) {
            return ResponseEntity.status(401).build();
        }
    }
}
