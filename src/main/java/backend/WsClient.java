package backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WsClient {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    private final String url;
    private final Consumer<String> onMessage;
    private final Consumer<String> onStatus;

    private volatile WebSocket webSocket;
    private volatile boolean running = false;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> pendingReconnect;
    private volatile int attempt = 0;

    public WsClient(String url, Consumer<String> onMessage, Consumer<String> onStatus) {
        this.url = url;
        this.onMessage = onMessage;
        this.onStatus = onStatus;
    }

    public void start() {
        running = true;
        openSocket();
    }

    public void stop() {
        running = false;
        reconnectScheduled.set(false);

        ScheduledFuture<?> r = pendingReconnect;
        if (r != null) {
            r.cancel(false);
            pendingReconnect = null;
        }

        WebSocket ws = webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                        .exceptionally(ex -> null);
            } catch (Exception ignored) { }
            webSocket = null;
        }
    }

    private void openSocket() {
        if (!running) return;

        onStatus.accept("connecting");
        System.out.println("→ WS: подключение к " + url);

        try {
            CLIENT.newWebSocketBuilder()
                    .buildAsync(URI.create(url), new Listener())
                    .thenAccept(ws -> {
                        if (!running) {
                            // stop() пришёл, пока мы подключались
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
                            return;
                        }
                        this.webSocket = ws;
                    })
                    .exceptionally(ex -> {
                        System.out.println("✗ WS: не удалось подключиться: " + ex.getMessage());
                        if (running) scheduleReconnect();
                        return null;
                    });
        } catch (Throwable t) {
            System.err.println("✗ WS: openSocket threw: " + t.getMessage());
            if (running) scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running) return;
        if (!reconnectScheduled.compareAndSet(false, true)) return;

        long delaySec = Math.min(32, 1L << Math.min(attempt, 5));
        System.out.println("→ WS: переподключение через " + delaySec
                + " сек (attempt=" + attempt + ")");

        pendingReconnect = SCHEDULER.schedule(() -> {
            reconnectScheduled.set(false);
            pendingReconnect = null;
            if (!running) return;
            try {
                openSocket();
            } catch (Throwable t) {
                System.err.println("✗ WS: reconnect threw: " + t.getMessage());
                scheduleReconnect();
            }
        }, delaySec, TimeUnit.SECONDS);

        attempt++;
    }

    private class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket ws) {
            System.out.println("✓ WS: подключено");
            attempt = 0;
            onStatus.accept("connected");
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            onMessage.accept(data.toString());
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            System.out.println("✗ WS: закрыто " + code + " " + reason);
            if (thisIsCurrent(ws)) webSocket = null;
            if (!running) return null;
            onStatus.accept("disconnected");
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            System.out.println("✗ WS: ошибка " + error.getMessage());
            if (thisIsCurrent(ws)) webSocket = null;
            if (!running) return;
            onStatus.accept("error");
            scheduleReconnect();
        }

        private boolean thisIsCurrent(WebSocket ws) {
            return webSocket == ws;
        }
    }
}