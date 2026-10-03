package com.learn.datacomm.auth.jwt;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

/**
 * CONTOH VERIFIER YANG RENTAN -- hanya dipakai endpoint `/naive/**` untuk demo.
 *
 * Kesalahannya: percaya pada field `alg` di header token. Penyerang cukup membuat token
 * dengan header {"alg":"none"}, payload {"sub":"andi",...}, dan signature KOSONG --
 * verifier ini melewati pengecekan signature dan menerima token palsu tersebut.
 *
 * Ini bukan kasus teoretis: beberapa library JWT generasi awal (2015) rentan persis
 * seperti ini. Varian lainnya "algorithm confusion" (RS256 -> HS256 memakai public key
 * sebagai secret). Pencegahannya sama: server MENENTUKAN algoritma, bukan membaca dari token.
 */
@Component
public class NaiveJwtVerifier {

    private final JwtService jwtService;

    public NaiveJwtVerifier(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Map<String, Object> verify(String token) {
        String[] parts = token.split("\\.", -1);
        Map<String, Object> header = JwtService.decode(parts[0]);
        if ("none".equals(header.get("alg"))) {
            return JwtService.decode(parts[1]); // <-- BUG: tanpa signature pun diterima
        }
        byte[] expected = jwtService.hmac(parts[0] + "." + parts[1]);
        if (!Arrays.equals(expected, java.util.Base64.getUrlDecoder().decode(parts[2]))) {
            throw new InvalidTokenException("Signature tidak valid");
        }
        return JwtService.decode(parts[1]);
    }
}
