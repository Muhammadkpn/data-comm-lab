package com.learn.datacomm.realtime.ws;

import com.learn.datacomm.realtime.tracker.TransferStatus;
import com.learn.datacomm.realtime.tracker.TransferTracker;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WEBSOCKET di ws://localhost:8083/ws/transfers
 *
 * Diawali HTTP request biasa dengan header `Upgrade: websocket`; server menjawab
 * `101 Switching Protocols`, lalu koneksi TCP yang sama dipakai untuk frame WebSocket
 * DUA ARAH -- bukan lagi request/response HTTP.
 *
 * Protokol aplikasi sederhana (teks):
 *   client -> server   SUBSCRIBE TRF-0001     mulai menerima update transfer itu
 *   client -> server   PING                   server menjawab PONG (bukti dua arah)
 *   server -> client   {"transferId":...,"stage":...}
 *
 * Perhatikan: semua hal yang di HTTP "gratis" (routing per URL, status code, format error)
 * harus didesain sendiri di atas WebSocket -- mirip kembali ke tcp-raw-demo, tapi
 * framing pesannya sudah diurus protokol WebSocket.
 */
@Component
public class TransferWebSocketHandler extends TextWebSocketHandler {

    private final TransferTracker tracker;
    private final Map<String, List<Runnable>> subscriptions = new ConcurrentHashMap<>();

    public TransferWebSocketHandler(TransferTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("[SERVER] WebSocket terhubung: " + session.getId());
        subscriptions.put(session.getId(), new CopyOnWriteArrayList<>());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String text = message.getPayload().trim();
        if ("PING".equals(text)) {
            send(session, "PONG");
        } else if (text.startsWith("SUBSCRIBE ")) {
            String id = text.substring("SUBSCRIBE ".length());
            TransferStatus current = tracker.current(id);
            if (current == null) {
                send(session, "{\"error\":\"transfer " + id + " tidak ditemukan\"}");
                return;
            }
            subscriptions.get(session.getId()).add(tracker.subscribe(id, s -> send(session, s.toJson())));
            send(session, current.toJson());
        } else {
            send(session, "{\"error\":\"perintah tidak dikenal\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        List<Runnable> subs = subscriptions.remove(session.getId());
        if (subs != null) {
            subs.forEach(Runnable::run);
        }
        System.out.println("[SERVER] WebSocket ditutup: " + session.getId() + " " + status);
    }

    private static void send(WebSocketSession session, String text) {
        // WebSocketSession tidak thread-safe untuk send bersamaan.
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(text));
                }
            } catch (IOException e) {
                System.out.println("[SERVER] Gagal kirim ke " + session.getId() + ": " + e.getMessage());
            }
        }
    }
}
