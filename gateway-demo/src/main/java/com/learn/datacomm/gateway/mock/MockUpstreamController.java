package com.learn.datacomm.gateway.mock;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Upstream TIRUAN yang hidup di proses yang sama, supaya demo gateway bisa jalan sendiri.
 * Diakses gateway lewat route /api/echo dan /api/slow (lihat application.properties).
 */
@RestController
@RequestMapping("/_mock")
public class MockUpstreamController {

    /** Memantulkan apa yang DITERIMA upstream -- memperlihatkan header yang ditambah/dibuang gateway. */
    @RequestMapping("/echo/**")
    public Map<String, Object> echo(HttpServletRequest req) {
        Map<String, String> headers = new TreeMap<>();
        for (String name : Collections.list(req.getHeaderNames())) {
            headers.put(name.toLowerCase(), req.getHeader(name));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("upstreamSaw", req.getMethod() + " " + req.getRequestURI()
                + (req.getQueryString() == null ? "" : "?" + req.getQueryString()));
        res.put("headers", headers);
        return res;
    }

    @RequestMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "5000") long ms) throws InterruptedException {
        Thread.sleep(ms);
        return Collections.singletonMap("sleptMs", ms);
    }
}
