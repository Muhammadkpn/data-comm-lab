package com.learn.datacomm.rest.v2.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Body error mengikuti RFC 7807 "Problem Details for HTTP APIs".
 *
 * Spring 6 sudah punya class bawaan bernama sama; demo ini memakai Spring Boot 2.7
 * (Spring 5.3) jadi ditulis manual -- sekalian kelihatan bahwa standar ini hanya
 * kesepakatan bentuk JSON, bukan fitur ajaib framework:
 *
 *   type     URI yang mengidentifikasi JENIS error (stabil, bisa didokumentasikan)
 *   title    ringkasan jenis error, sama untuk semua kejadian type yang sama
 *   status   salinan HTTP status code (berguna kalau body di-log terpisah)
 *   detail   penjelasan kejadian SPESIFIK ini
 *   instance path request yang memicu error
 *
 * Extension member (di sini `errors` dan `traceId`) boleh ditambahkan.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {

    public static final String MEDIA_TYPE = "application/problem+json";
    private static final String TYPE_PREFIX = "https://data-comm-lab.local/problems/";

    private final String type;
    private final String title;
    private final int status;
    private final String detail;
    private final String instance;
    private final String traceId;
    private final Map<String, String> errors;

    public ProblemDetail(String type, String title, int status, String detail, String instance,
                         String traceId, Map<String, String> errors) {
        this.type = TYPE_PREFIX + type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.traceId = traceId;
        this.errors = errors;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
