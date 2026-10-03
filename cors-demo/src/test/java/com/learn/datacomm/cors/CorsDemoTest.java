package com.learn.datacomm.cors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "cors.frontend-port=0")
@AutoConfigureMockMvc
class CorsDemoTest {

    private static final String FRONTEND = "http://localhost:8086";
    private static final String EVIL = "https://evil.example";

    @Autowired
    private MockMvc mvc;

    @Test
    void originYangDiizinkanMendapatHeaderCors() throws Exception {
        mvc.perform(get("/api/cors/balance").header("Origin", FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Request-Id")));
    }

    @Test
    void originLainDitolak() throws Exception {
        mvc.perform(get("/api/cors/balance").header("Origin", EVIL))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightMengizinkanMethodDanHeaderYangTerdaftar() throws Exception {
        mvc.perform(options("/api/cors/transfers").header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("idempotency-key")))
                .andExpect(header().string("Access-Control-Max-Age", "600"));
    }

    @Test
    void preflightUntukMethodTidakTerdaftarDitolak() throws Exception {
        mvc.perform(options("/api/cors/transfers").header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tanpaKonfigurasiCorsRequestTetapDiprosesTapiTanpaHeader() throws Exception {
        // Server menjawab 200 -- browser-lah yang kemudian menyembunyikan response dari JavaScript.
        mvc.perform(get("/api/no-cors/balance").header("Origin", FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/no-cors/transfers").header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void miskonfigurasiMemantulkanOriginPenyerangDenganCredentials() throws Exception {
        mvc.perform(get("/api/misconfig/balance").header("Origin", EVIL))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", EVIL))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void postJsonDenganOriginBenarDiproses() throws Exception {
        mvc.perform(post("/api/cors/transfers").header("Origin", FRONTEND)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }
}
