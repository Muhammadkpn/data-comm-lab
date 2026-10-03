package com.learn.datacomm.rest;

import com.learn.datacomm.rest.controller.TransferController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mengunci semantik status code yang dibahas di README: 201 / 400 / 422 / 200 / 404.
 */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mvc;

    private static String body(String src, String dst, long amount, String ref) {
        return String.format("{\"sourceAccount\":\"%s\",\"destinationAccount\":\"%s\",\"amount\":%d,\"referenceId\":\"%s\"}",
                src, dst, amount, ref);
    }

    @Test
    void postValidMenghasilkan201() throws Exception {
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(body("1010001", "2020002", 150000, "REF-T-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void amountNolGagalValidasi400() throws Exception {
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(body("1010001", "2020002", 0, "REF-T-2")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rekeningSamaDitolakBusinessRule422() throws Exception {
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(body("1010001", "1010001", 150000, "REF-T-3")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void getYangAda200DanYangTidakAda404() throws Exception {
        mvc.perform(get("/transfers/REF-REST-0001")).andExpect(status().isOk());
        mvc.perform(get("/transfers/TIDAK-ADA")).andExpect(status().isNotFound());
    }
}
