package com.learn.datacomm.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Empat cara client mengikuti status transfer yang berubah di server (bab 14 materi).
 * Jalan di port 8083.
 */
@SpringBootApplication
public class RealtimeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeDemoApplication.class, args);
    }
}
