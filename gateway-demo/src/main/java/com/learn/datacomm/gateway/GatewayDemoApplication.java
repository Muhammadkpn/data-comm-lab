package com.learn.datacomm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API gateway mini (bab 10 & 16 materi). Jalan di port 8084.
 *
 * Urutan pemrosesan setiap request /api/**:
 *   CorrelationIdFilter -> AccessLogFilter -> ApiKeyAuthFilter -> RateLimitFilter -> ProxyController
 *
 * Ditulis manual supaya tiap tanggung jawab gateway kelihatan sebagai satu class kecil.
 * Di production pakai produk jadi: Kong, Spring Cloud Gateway, APISIX, Envoy, AWS API Gateway.
 */
@SpringBootApplication
public class GatewayDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayDemoApplication.class, args);
    }
}
