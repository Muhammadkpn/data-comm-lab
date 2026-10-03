package com.learn.datacomm.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "realtime.step-ms=100")
class RealtimeDemoTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate http;

    private String start() {
        Matcher m = Pattern.compile("\"transferId\":\"([\\w-]+)\"").matcher(http.postForObject("/transfers", null, String.class));
        assertTrue(m.find());
        return m.group(1);
    }

    @Test
    void longPollMenungguSampaiAdaVersiBaru() {
        String id = start();
        long t0 = System.currentTimeMillis();
        ResponseEntity<String> res = http.getForEntity("/transfers/" + id + "/status/long-poll?since=1", String.class);
        assertEquals(200, res.getStatusCodeValue());
        assertTrue(res.getBody().contains("\"version\":2"));
        assertTrue(System.currentTimeMillis() - t0 >= 50, "harus ditahan sampai stage berikutnya");
    }

    @Test
    void longPollTimeoutTanpaPerubahan204() throws Exception {
        String id = start();
        Thread.sleep(700); // transfer sudah COMPLETED (version 5), tidak akan berubah lagi
        ResponseEntity<String> res = http.getForEntity(
                "/transfers/" + id + "/status/long-poll?since=5&timeoutMs=300", String.class);
        assertEquals(204, res.getStatusCodeValue());
    }

    @Test
    void sseMengirimSemuaStageDalamSatuResponse() {
        String id = start();
        String stream = http.getForObject("/transfers/" + id + "/events", String.class);
        assertEquals(5, stream.split("event:status").length - 1);
        assertTrue(stream.contains("id:5"));
    }

    @Test
    void sseLastEventIdHanyaMengirimYangTerlewat() throws Exception {
        String id = start();
        Thread.sleep(700);
        HttpHeaders h = new HttpHeaders();
        h.set("Last-Event-ID", "3");
        String stream = http.exchange("/transfers/" + id + "/events", HttpMethod.GET,
                new HttpEntity<>(h), String.class).getBody();
        assertFalse(stream.contains("id:3"));
        assertTrue(stream.contains("id:5"));
    }

    @Test
    void webSocketDuaArah() throws Exception {
        String id = start();
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        WebSocketSession session = new StandardWebSocketClient().doHandshake(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                received.add(message.getPayload());
                if (message.getPayload().contains("COMPLETED")) {
                    completed.countDown();
                }
            }
        }, "ws://localhost:" + port + "/ws/transfers").get(5, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("PING"));
        session.sendMessage(new TextMessage("SUBSCRIBE " + id));
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        session.close();

        assertEquals("PONG", received.get(0));
        assertTrue(received.stream().anyMatch(m -> m.contains("DEBIT_SOURCE")));
    }

    @Test
    void transferTidakAda404() {
        assertEquals(404, http.getForEntity("/transfers/NOPE/status", String.class).getStatusCodeValue());
        assertEquals(404, http.getForEntity("/transfers/NOPE/status/long-poll", String.class).getStatusCodeValue());
    }
}
