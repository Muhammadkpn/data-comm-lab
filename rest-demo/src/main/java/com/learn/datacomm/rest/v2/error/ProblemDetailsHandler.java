package com.learn.datacomm.rest.v2.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Satu tempat yang memetakan SEMUA error di API v2 ke format yang sama.
 *
 * Sengaja di-scope hanya ke package v2: endpoint lama `/transfers` (v1) tetap
 * memakai format error lamanya. Mengganti format error itu sendiri adalah
 * BREAKING CHANGE bagi client yang sudah mem-parsing body error lama -- salah
 * satu alasan nyata kenapa perlu versi API baru.
 */
@RestControllerAdvice(basePackages = "com.learn.datacomm.rest.v2")
public class ProblemDetailsHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getType(), ex.getMessage(), req, null);
    }

    /** @Valid gagal: kembalikan SEMUA field yang salah sekaligus, bukan satu per satu. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "validation-error",
                "Request tidak lolos validasi", req, errors);
    }

    /** JSON rusak atau tipe salah (mis. amount berisi teks). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "malformed-body",
                "Body request tidak bisa dibaca sebagai JSON yang sesuai kontrak", req, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "missing-header",
                "Header wajib tidak dikirim: " + ex.getHeaderName(), req, null);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String type, String detail,
                                                HttpServletRequest req, Map<String, String> errors) {
        // traceId: id yang bisa client sebutkan ke tim support, lalu dicari di log/APM.
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[SERVER] " + status.value() + " " + type + " traceId=" + traceId + " -- " + detail);
        ProblemDetail body = new ProblemDetail(type, status.getReasonPhrase(), status.value(), detail,
                req.getRequestURI(), traceId, errors);
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(ProblemDetail.MEDIA_TYPE))
                .body(body);
    }
}
