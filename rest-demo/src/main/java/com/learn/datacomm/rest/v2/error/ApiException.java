package com.learn.datacomm.rest.v2.error;

import org.springframework.http.HttpStatus;

/**
 * Exception bisnis yang SUDAH tahu status HTTP-nya. Controller cukup melempar
 * ini; {@link ProblemDetailsHandler} yang menerjemahkan ke body standar
 * `application/problem+json`. Dengan begitu tidak ada controller yang perlu
 * merakit body error sendiri-sendiri (sumber inkonsistensi format error).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;

    public ApiException(HttpStatus status, String type, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", detail);
    }

    public static ApiException businessRule(String type, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, type, detail);
    }

    public static ApiException conflict(String type, String detail) {
        return new ApiException(HttpStatus.CONFLICT, type, detail);
    }
}
