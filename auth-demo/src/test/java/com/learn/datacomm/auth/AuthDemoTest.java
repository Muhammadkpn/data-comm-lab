package com.learn.datacomm.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthDemoTest {

    @Autowired
    private MockMvc mvc;

    private String login(String user, String pass) throws Exception {
        return mvc.perform(post("/mobile/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- mobile / JWT ----------

    @Test
    void tanpaToken401DenganWwwAuthenticate() throws Exception {
        mvc.perform(get("/mobile/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
    }

    @Test
    void tokenValidBisaAksesResource() throws Exception {
        String access = JsonPath.read(login("andi", "andi123"), "$.accessToken");
        mvc.perform(get("/mobile/accounts/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("andi"));
    }

    @Test
    void passwordSalah401() throws Exception {
        mvc.perform(post("/mobile/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"andi\",\"password\":\"salah\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void payloadDiubahSignatureTidakCocok401() throws Exception {
        String access = JsonPath.read(login("budi", "budi123"), "$.accessToken");
        String[] p = access.split("\\.");
        String forgedPayload = b64(new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8)
                .replace("accounts:read", "accounts:read transfers:write"));
        mvc.perform(get("/mobile/accounts/me").header("Authorization", "Bearer " + p[0] + "." + forgedPayload + "." + p[2]))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Signature tidak valid")));
    }

    @Test
    void algNoneDitolakVerifierBenarTapiDiterimaVerifierNaive() throws Exception {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String forged = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + b64("{\"sub\":\"andi\",\"scope\":\"accounts:read transfers:write\",\"exp\":" + exp + ",\"jti\":\"x\"}") + ".";

        mvc.perform(get("/mobile/accounts/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/naive/accounts/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("andi"));
    }

    @Test
    void scopeKurang403BukanLagi401() throws Exception {
        String access = JsonPath.read(login("budi", "budi123"), "$.accessToken");
        mvc.perform(post("/mobile/transfers").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", containsString("insufficient_scope")));

        String andi = JsonPath.read(login("andi", "andi123"), "$.accessToken");
        mvc.perform(post("/mobile/transfers").header("Authorization", "Bearer " + andi)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void refreshRotationDanReuseDetectionMencabutSemuaSesi() throws Exception {
        String first = JsonPath.read(login("andi", "andi123"), "$.refreshToken");

        String second = JsonPath.read(mvc.perform(post("/mobile/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");

        // Token lama dipakai lagi (mis. dicuri) -> ditolak, dan token terbaru ikut dicabut.
        mvc.perform(post("/mobile/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/mobile/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + second + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutMencabutAccessTokenSebelumExp() throws Exception {
        String body = login("andi", "andi123");
        String access = JsonPath.read(body, "$.accessToken");
        String refresh = JsonPath.read(body, "$.refreshToken");

        mvc.perform(post("/mobile/logout").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/mobile/accounts/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("dicabut")));
    }

    // ---------- web / session + CSRF ----------

    @Test
    void sessionLoginDanCsrfWajibUntukPost() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/web/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"andi\",\"password\":\"andi123\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/web/accounts/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("andi"));

        // Cookie session ikut, tapi tanpa token CSRF -> ditolak. Ini yang dialami form di situs penyerang.
        mvc.perform(post("/web/transfers").session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        Cookie xsrf = mvc.perform(get("/web/csrf").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/web/transfers").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void tanpaSession401BukanRedirect() throws Exception {
        mvc.perform(get("/web/accounts/me")).andExpect(status().isUnauthorized());
    }

    // ---------- API key & Basic ----------

    @Test
    void apiKeyPartner() throws Exception {
        mvc.perform(get("/partner/exchange-rates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/partner/exchange-rates").header("X-API-Key", "salah")).andExpect(status().isUnauthorized());
        mvc.perform(get("/partner/exchange-rates").header("X-API-Key", "pk_test_mitra_ewallet_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner").value("mitra-ewallet"));
    }

    @Test
    void basicAuthInternal() throws Exception {
        mvc.perform(get("/internal/health-detail"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
        mvc.perform(get("/internal/health-detail").header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString("ops:ops-secret".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk());
        // User valid tapi bukan OPS -> 403
        mvc.perform(get("/internal/health-detail").header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString("andi:andi123".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isForbidden());
    }
}
