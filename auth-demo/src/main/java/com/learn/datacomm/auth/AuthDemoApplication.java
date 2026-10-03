package com.learn.datacomm.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo pola autentikasi & otorisasi (bab 7 materi). Jalan di port 8082.
 * Lihat SecurityConfig untuk peta path -> mekanisme autentikasi.
 */
@SpringBootApplication
public class AuthDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthDemoApplication.class, args);
    }
}
