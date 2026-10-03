package com.learn.datacomm.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point server REST. Jalankan class ini untuk start server di
 * http://localhost:8080 -- lihat TransferController untuk endpoint-nya.
 */
@SpringBootApplication
public class RestDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestDemoApplication.class, args);
    }
}
