package com.learn.datacomm.auth.jwt;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refresh token = string acak OPAQUE yang disimpan server (bukan JWT). Umurnya panjang,
 * jadi justru harus bisa dicabut -- karena itu stateful.
 *
 * Diterapkan REFRESH TOKEN ROTATION + REUSE DETECTION (rekomendasi OAuth 2.0 Security BCP):
 *   - Setiap refresh menghasilkan refresh token BARU; yang lama ditandai `used`.
 *   - Kalau refresh token yang sudah `used` dipakai lagi, berarti ada dua pihak yang
 *     memegangnya (kemungkinan dicuri). Seluruh "family" token login itu dicabut, sehingga
 *     baik penyerang maupun user asli harus login ulang.
 */
@Component
public class RefreshTokenStore {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public String issue(String username) {
        return issue(username, UUID.randomUUID().toString());
    }

    /** Tukar refresh token lama dengan yang baru; melempar exception bila tidak valid / reuse terdeteksi. */
    public synchronized Rotation rotate(String refreshToken) {
        Entry entry = tokens.get(refreshToken);
        if (entry == null) {
            throw new InvalidTokenException("Refresh token tidak dikenal");
        }
        if (entry.used) {
            revokeFamily(entry.family);
            System.out.println("[SERVER] REUSE refresh token terdeteksi -> seluruh sesi " + entry.username + " dicabut");
            throw new InvalidTokenException("Refresh token sudah pernah dipakai -- semua sesi dicabut");
        }
        entry.used = true;
        return new Rotation(entry.username, issue(entry.username, entry.family));
    }

    public synchronized void revoke(String refreshToken) {
        Entry entry = tokens.get(refreshToken);
        if (entry != null) {
            revokeFamily(entry.family);
        }
    }

    private String issue(String username, String family) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Entry(username, family));
        return token;
    }

    private void revokeFamily(String family) {
        for (Iterator<Entry> it = tokens.values().iterator(); it.hasNext(); ) {
            if (it.next().family.equals(family)) {
                it.remove();
            }
        }
    }

    private static final class Entry {
        final String username;
        final String family;
        boolean used;

        Entry(String username, String family) {
            this.username = username;
            this.family = family;
        }
    }

    public static final class Rotation {
        private final String username;
        private final String newRefreshToken;

        Rotation(String username, String newRefreshToken) {
            this.username = username;
            this.newRefreshToken = newRefreshToken;
        }

        public String getUsername() {
            return username;
        }

        public String getNewRefreshToken() {
            return newRefreshToken;
        }
    }
}
