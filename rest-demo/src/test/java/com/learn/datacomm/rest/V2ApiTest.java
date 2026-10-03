package com.learn.datacomm.rest;

import com.learn.datacomm.rest.v2.idempotency.IdempotencyStore;
import com.learn.datacomm.rest.v2.store.BankStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class V2ApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BankStore bankStore;
    @Autowired
    private IdempotencyStore idempotencyStore;

    @BeforeEach
    void reset() {
        bankStore.reset();
        idempotencyStore.clear();
    }

    private static String transfer(String ref, long amount) {
        return "{\"sourceAccount\":\"1010001\",\"destinationAccount\":\"2020002\",\"amount\":" + amount +
                ",\"referenceId\":\"" + ref + "\"}";
    }

    // ---------- idempotency ----------

    @Test
    void tanpaKeyRetryMendebitDuaKali() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/v2/transfers").contentType(MediaType.APPLICATION_JSON).content(transfer("R", 100_000)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/v2/accounts/1010001")).andExpect(jsonPath("$.balance").value(9_800_000));
    }

    @Test
    void denganKeyRetryDiReplayDanSaldoHanyaTerdebitSekali() throws Exception {
        mvc.perform(post("/v2/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(transfer("R", 100_000)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(header().string("Location", "/v2/transfers/TRF-0001"));

        mvc.perform(post("/v2/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(transfer("R", 100_000)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value("TRF-0001"));

        mvc.perform(get("/v2/accounts/1010001")).andExpect(jsonPath("$.balance").value(9_900_000));
    }

    @Test
    void keySamaIsiBedaDitolak422() throws Exception {
        mvc.perform(post("/v2/transfers").header("Idempotency-Key", "k2")
                .contentType(MediaType.APPLICATION_JSON).content(transfer("R", 1)));
        mvc.perform(post("/v2/transfers").header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON).content(transfer("R", 2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", endsWith("idempotency-key-reuse")));
    }

    @Test
    void gagalBisnisTidakMengunciKey() throws Exception {
        mvc.perform(post("/v2/transfers").header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON).content(transfer("R", 999_999_999)))
                .andExpect(status().isUnprocessableEntity());
        // Key yang sama boleh dipakai lagi karena hasil gagal tidak disimpan.
        mvc.perform(post("/v2/transfers").header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON).content(transfer("R", 999_999_999)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", endsWith("insufficient-balance")));
    }

    // ---------- problem+json ----------

    @Test
    void validasiGagalMengembalikanProblemJsonDenganSemuaField() throws Exception {
        mvc.perform(post("/v2/transfers").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.sourceAccount").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void rekeningTidakAda404ProblemJson() throws Exception {
        mvc.perform(get("/v2/accounts/9999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    // ---------- versioning & ETag ----------

    @Test
    void headerVersioningMengubahBentukBalance() throws Exception {
        mvc.perform(get("/v2/accounts/1010001"))
                .andExpect(jsonPath("$.balance").value(10_000_000))
                .andExpect(header().string("Vary", "X-API-Version"));
        mvc.perform(get("/v2/accounts/1010001").header("X-API-Version", "2"))
                .andExpect(jsonPath("$.balance.amount").value(10_000_000))
                .andExpect(jsonPath("$.balance.currency").value("IDR"));
        mvc.perform(get("/v2/accounts/1010001").header("X-API-Version", "9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void etagMenghasilkan304SampaiSaldoBerubah() throws Exception {
        MvcResult first = mvc.perform(get("/v2/accounts/1010001")).andReturn();
        String etag = first.getResponse().getHeader("ETag");

        mvc.perform(get("/v2/accounts/1010001").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));

        mvc.perform(post("/v2/transfers").contentType(MediaType.APPLICATION_JSON).content(transfer("R", 1)));

        mvc.perform(get("/v2/accounts/1010001").header("If-None-Match", etag))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", not(etag)));
    }

    @Test
    void endpointV1MembawaHeaderDeprecation() throws Exception {
        mvc.perform(get("/transfers/REF-REST-0001"))
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().exists("Sunset"))
                .andExpect(header().string("Link", containsString("/v2/transfers")));
        mvc.perform(get("/v2/accounts/1010001")).andExpect(header().doesNotExist("Deprecation"));
    }

    // ---------- pagination & content negotiation ----------

    @Test
    void offsetPaginationMenghitungTotal() throws Exception {
        mvc.perform(get("/v2/accounts/1010001/transactions?page=1&size=5"))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value("TX-0007"));
    }

    @Test
    void cursorPaginationStabilWalauAdaDataBaru() throws Exception {
        MvcResult first = mvc.perform(get("/v2/accounts/1010001/transactions/cursor?limit=5"))
                .andExpect(jsonPath("$.content[4].id").value("TX-0008"))
                .andReturn();
        String cursor = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");

        mvc.perform(post("/v2/transfers").contentType(MediaType.APPLICATION_JSON).content(transfer("R", 1)));

        mvc.perform(get("/v2/accounts/1010001/transactions/cursor?limit=5&cursor=" + cursor))
                .andExpect(jsonPath("$.content[0].id").value("TX-0007"));
    }

    @Test
    void cursorRusak400DanSizeTerlaluBesar400() throws Exception {
        mvc.perform(get("/v2/accounts/1010001/transactions/cursor?cursor=bukan-cursor"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v2/accounts/1010001/transactions?size=1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptMenentukanFormatResponse() throws Exception {
        mvc.perform(get("/v2/accounts/1010001/transactions?size=2").accept("text/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith("id,type,amount,transferId,timestamp\nTX-0012,")));
        mvc.perform(get("/v2/accounts/1010001/transactions").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    // ---------- method semantics ----------

    @Test
    void putMenggantiTotalPatchMengubahSebagianDeleteIdempotent() throws Exception {
        mvc.perform(post("/v2/beneficiaries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"3030003\",\"alias\":\"Citra\",\"bankCode\":\"153\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/v2/beneficiaries/BNF-")));
        String id = "/v2/beneficiaries/" + com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/v2/beneficiaries")).andReturn().getResponse().getContentAsString(), "$[-1].id");

        mvc.perform(patch(id).contentType("application/merge-patch+json").content("{\"alias\":\"Citra L\"}"))
                .andExpect(jsonPath("$.alias").value("Citra L"))
                .andExpect(jsonPath("$.bankCode").value("153"));

        mvc.perform(put(id).contentType(MediaType.APPLICATION_JSON).content("{\"accountNumber\":\"3030003\"}"))
                .andExpect(jsonPath("$.alias").doesNotExist())
                .andExpect(jsonPath("$.bankCode").doesNotExist());

        mvc.perform(head(id)).andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(options(id)).andExpect(header().string("Allow", containsString("PATCH")));

        mvc.perform(delete(id)).andExpect(status().isNoContent());
        mvc.perform(delete(id)).andExpect(status().isNotFound());
    }

    @Test
    void antiPatternSelalu200() throws Exception {
        mvc.perform(post("/anti-pattern/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccount\":\"1\",\"destinationAccount\":\"1\",\"amount\":5,\"referenceId\":\"X\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
