package com.learn.datacomm.auth.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT HS256 yang ditulis manual SUPAYA KELIHATAN isinya. Di production pakai library
 * yang sudah diaudit (Nimbus JOSE, jjwt, spring-security-oauth2-resource-server) --
 * jangan menulis kode kriptografi sendiri.
 *
 * Bentuk token: base64url(header) . base64url(payload) . base64url(signature)
 *
 *   header  {"alg":"HS256","typ":"JWT"}
 *   payload {"sub":"andi","scope":"accounts:read transfers:write","iat":..,"exp":..,"jti":".."}
 *
 * Yang perlu diingat:
 *   - Payload hanya DI-ENCODE, bukan dienkripsi. Siapa pun bisa membacanya (coba tempel
 *     ke jwt.io). Jangan taruh data sensitif (nomor rekening lengkap, NIK, PIN) di sini.
 *   - Signature menjamin INTEGRITAS: payload yang diubah satu karakter pun akan ditolak.
 *   - Stateless = server tidak perlu lookup untuk memverifikasi. Harganya: token tidak
 *     bisa "dicabut" sebelum exp, kecuali server menambah state lagi (denylist `jti`).
 */
@Component
public class JwtService {

    private static final String ALG = "HS256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final ObjectMapper json = new ObjectMapper();
    private final byte[] secret;
    private final long accessTtlSeconds;
    /** jti token yang sudah di-logout. Di production: Redis dengan TTL = sisa umur token. */
    private final Set<String> revokedJti = ConcurrentHashMap.newKeySet();

    public JwtService(@Value("${auth.jwt.secret}") String secret,
                      @Value("${auth.jwt.access-ttl-seconds}") long accessTtlSeconds) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Secret HS256 minimal 256 bit (32 byte)");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public String issueAccessToken(String subject, String scope) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", ALG);
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", subject);
        payload.put("scope", scope);
        payload.put("iat", now);
        payload.put("exp", now + accessTtlSeconds);
        payload.put("jti", UUID.randomUUID().toString());

        String signingInput = encode(header) + "." + encode(payload);
        return signingInput + "." + B64.encodeToString(hmac(signingInput));
    }

    /**
     * Verifikasi yang BENAR. Urutan pemeriksaan penting:
     *   1. Bentuk 3 bagian
     *   2. `alg` di header HARUS sama dengan yang server harapkan -- server yang menentukan
     *      algoritma, bukan token. (Mencegah serangan alg=none / algorithm confusion.)
     *   3. Signature dibandingkan constant-time (mencegah timing attack)
     *   4. Baru setelah itu isi payload dipercaya: exp, lalu denylist jti
     */
    public Map<String, Object> verify(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new InvalidTokenException("Format token salah");
        }
        Map<String, Object> header = decode(parts[0]);
        if (!ALG.equals(header.get("alg"))) {
            throw new InvalidTokenException("Algoritma " + header.get("alg") + " tidak diterima");
        }
        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] actual;
        try {
            actual = B64D.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Signature bukan base64url");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidTokenException("Signature tidak valid");
        }

        Map<String, Object> claims = decode(parts[1]);
        long exp = ((Number) claims.get("exp")).longValue();
        if (Instant.now().getEpochSecond() >= exp) {
            throw new InvalidTokenException("Token kedaluwarsa");
        }
        if (revokedJti.contains(String.valueOf(claims.get("jti")))) {
            throw new InvalidTokenException("Token sudah dicabut (logout)");
        }
        return claims;
    }

    public void revoke(String jti) {
        revokedJti.add(jti);
    }

    String encode(Map<String, Object> part) {
        try {
            return B64.encodeToString(json.writeValueAsBytes(part));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, Object> decode(String part) {
        try {
            return new ObjectMapper().readValue(B64D.decode(part), new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new InvalidTokenException("Bagian token bukan JSON base64url");
        }
    }

    byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
