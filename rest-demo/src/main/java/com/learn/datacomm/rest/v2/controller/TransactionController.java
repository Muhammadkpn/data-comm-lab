package com.learn.datacomm.rest.v2.controller;

import com.learn.datacomm.rest.v2.error.ApiException;
import com.learn.datacomm.rest.v2.store.BankStore;
import com.learn.datacomm.rest.v2.store.Transaction;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Riwayat mutasi rekening dengan DUA gaya pagination, supaya bisa dibandingkan
 * langsung (lihat `PaginationClient`):
 *
 *   OFFSET  GET /v2/accounts/{id}/transactions?page=0&size=5
 *           "lewati page*size baris, ambil size berikutnya".
 *           + bisa lompat ke halaman mana saja, total mudah dihitung
 *           - kalau ada data baru masuk di antara dua request, semua baris
 *             bergeser -> item yang sama muncul dua kali di halaman berikutnya
 *           - di database, OFFSET 100000 tetap harus memindai 100000 baris
 *
 *   CURSOR  GET /v2/accounts/{id}/transactions/cursor?limit=5&cursor=...
 *           "ambil size baris yang lebih lama dari item terakhir yang sudah dilihat".
 *           Ini keyset pagination: di SQL menjadi `WHERE seq < :lastSeq ORDER BY seq DESC LIMIT :n`
 *           yang bisa memakai index langsung.
 *           + stabil walau ada insert baru, performa konstan di halaman sedalam apa pun
 *           - tidak bisa lompat ke "halaman 7", total tidak gratis
 *
 * Endpoint offset juga mendemokan CONTENT NEGOTIATION: path yang sama melayani
 * JSON atau CSV tergantung header `Accept`. Format lain (mis. application/xml)
 * dijawab 406 Not Acceptable.
 */
@RestController
@RequestMapping("/v2/accounts/{accountId}/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String TEXT_CSV = "text/csv";

    private final BankStore bankStore;

    public TransactionController(BankStore bankStore) {
        this.bankStore = bankStore;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> offsetPage(@PathVariable String accountId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "5") int size) {
        validateSize(size);
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-page", "page tidak boleh negatif");
        }

        List<Transaction> all = bankStore.transactionsNewestFirst(accountId);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", new ArrayList<>(all.subList(from, to)));
        body.put("page", page);
        body.put("size", size);
        body.put("totalElements", all.size());
        body.put("totalPages", (all.size() + size - 1) / size);
        System.out.println("[SERVER] OFFSET page=" + page + " size=" + size + " -> baris " + from + ".." + (to - 1));
        return body;
    }

    /** Path & parameter sama, beda `produces` -- Spring memilih method berdasarkan header Accept. */
    @GetMapping(produces = TEXT_CSV)
    public byte[] offsetPageCsv(@PathVariable String accountId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "5") int size) {
        @SuppressWarnings("unchecked")
        List<Transaction> rows = (List<Transaction>) offsetPage(accountId, page, size).get("content");
        StringBuilder csv = new StringBuilder("id,type,amount,transferId,timestamp\n");
        for (Transaction tx : rows) {
            csv.append(tx.getId()).append(',').append(tx.getType()).append(',').append(tx.getAmount())
                    .append(',').append(tx.getTransferId()).append(',').append(tx.getTimestamp()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/cursor")
    public Map<String, Object> cursorPage(@PathVariable String accountId,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "5") int limit) {
        validateSize(limit);
        long afterSeq = cursor == null ? Long.MAX_VALUE : decodeCursor(cursor);

        List<Transaction> page = new ArrayList<>();
        boolean hasMore = false;
        for (Transaction tx : bankStore.transactionsNewestFirst(accountId)) {
            if (tx.getSeq() >= afterSeq) {
                continue; // sudah dilihat client di halaman sebelumnya
            }
            if (page.size() == limit) {
                hasMore = true;
                break;
            }
            page.add(tx);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", page);
        body.put("nextCursor", hasMore ? encodeCursor(page.get(page.size() - 1).getSeq()) : null);
        System.out.println("[SERVER] CURSOR afterSeq=" + (cursor == null ? "-" : afterSeq) + " limit=" + limit +
                " -> " + page.size() + " baris, hasMore=" + hasMore);
        return body;
    }

    /**
     * Cursor dibuat OPAQUE (base64), bukan angka seq mentah: client tidak boleh
     * berasumsi isinya, sehingga server bebas mengganti strategi (mis. seq+timestamp)
     * tanpa merusak client.
     */
    static String encodeCursor(long seq) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("seq:" + seq).getBytes(StandardCharsets.UTF_8));
    }

    static long decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!raw.startsWith("seq:")) {
                throw new IllegalArgumentException();
            }
            return Long.parseLong(raw.substring(4));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-cursor", "cursor tidak valid");
        }
    }

    private static void validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-page-size",
                    "size/limit harus 1.." + MAX_PAGE_SIZE + " -- batas atas melindungi server dari request raksasa");
        }
    }
}
