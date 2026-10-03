package com.learn.datacomm.gateway;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Error yang dibuat GATEWAY sendiri (bukan diteruskan dari upstream) memakai
 * application/problem+json, sama seperti rest-demo v2 -- client melihat satu format error.
 */
public final class GatewayErrors {

    private GatewayErrors() {
    }

    public static void write(HttpServletRequest req, HttpServletResponse res, int status, String type, String detail)
            throws IOException {
        Object requestId = req.getAttribute(com.learn.datacomm.gateway.filter.CorrelationIdFilter.ATTRIBUTE);
        String json = "{\"type\":\"https://data-comm-lab.local/problems/" + type + "\"," +
                "\"status\":" + status + ",\"detail\":\"" + detail.replace("\"", "'") + "\"," +
                "\"instance\":\"" + req.getRequestURI() + "\",\"requestId\":\"" + requestId + "\"}";
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
