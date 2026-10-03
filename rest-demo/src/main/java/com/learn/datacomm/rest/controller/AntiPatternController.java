package com.learn.datacomm.rest.controller;

import com.learn.datacomm.rest.dto.TransferRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CONTOH YANG SALAH -- jangan ditiru. Disimpan supaya bisa dibandingkan dengan
 * `/transfers` dan `/v2/transfers`.
 *
 * Anti-pattern "200 OK dengan {success:false}": status HTTP selalu 200, sukses/gagal
 * disembunyikan di dalam body. Akibatnya:
 *   - Load balancer, API gateway, APM, dan dashboard error-rate menganggap SEMUA request sukses.
 *     Alert "5xx/4xx naik" tidak akan pernah berbunyi.
 *   - Cache/proxy boleh menyimpan response "gagal" ini karena 200 dianggap sukses.
 *   - Retry policy di HTTP client (yang biasanya melihat status code) tidak bekerja.
 *   - Setiap client harus mem-parsing body dulu untuk tahu hasilnya, dengan format yang
 *     berbeda-beda antar endpoint.
 */
@RestController
@RequestMapping("/anti-pattern/transfers")
public class AntiPatternController {

    @PostMapping
    public Map<String, Object> transfer(@RequestBody TransferRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.getAmount() <= 0 || request.getSourceAccount().equals(request.getDestinationAccount())) {
            System.out.println("[SERVER] (anti-pattern) gagal, tapi tetap 200 OK");
            body.put("success", false);
            body.put("errorCode", "E-042");
            body.put("errorMessage", "Transfer gagal");
            return body;
        }
        body.put("success", true);
        body.put("referenceId", request.getReferenceId());
        return body;
    }
}
