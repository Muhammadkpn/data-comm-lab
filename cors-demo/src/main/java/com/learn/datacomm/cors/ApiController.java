package com.learn.datacomm.cors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Endpoint yang sama di tiga "kebijakan" CORS: tanpa CORS, CORS benar, CORS salah konfigurasi. */
@RestController
public class ApiController {

    @GetMapping({"/api/no-cors/balance", "/api/cors/balance", "/api/misconfig/balance"})
    public ResponseEntity<Map<String, Object>> balance() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", "1010001");
        body.put("balance", 10_000_000);
        return ResponseEntity.ok().header("X-Request-Id", UUID.randomUUID().toString().substring(0, 8)).body(body);
    }

    /**
     * POST dengan Content-Type application/json BUKAN "simple request", jadi browser
     * mengirim preflight OPTIONS lebih dulu dan baru mengirim POST kalau server mengizinkan.
     */
    @PostMapping({"/api/no-cors/transfers", "/api/cors/transfers"})
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody Map<String, Object> body) {
        System.out.println("[SERVER] Transfer DIPROSES: " + body);
        Map<String, Object> res = new LinkedHashMap<>(body);
        res.put("status", "SUCCESS");
        return ResponseEntity.created(URI.create("/api/cors/transfers/TRF-1"))
                .header("X-Request-Id", UUID.randomUUID().toString().substring(0, 8))
                .body(res);
    }
}
