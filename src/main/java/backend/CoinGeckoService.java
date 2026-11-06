package backend;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;


public class CoinGeckoService {

    private static final Map<String, String> BINANCE_TO_GECKO = Map.of(
            "BTC", "bitcoin",
            "ETH", "ethereum",
            "SOL", "solana",
            "BNB", "binancecoin",
            "XRP", "ripple"
    );

    private static final long RETRY_PERIOD_SEC = 5;
    private static final int MAX_ATTEMPTS_UNTIL_OK = Integer.MAX_VALUE;

    private final CoinGeckoClient client = new CoinGeckoClient();
    private final BiConsumer<String, String> onDescription;
    private final BiConsumer<String, String> onStatus;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "coingecko-retry");
                t.setDaemon(true);
                return t;
            });

    private volatile ScheduledFuture<?> currentPoll;
    private final AtomicLong requestId = new AtomicLong();
    private volatile String currentTicker;
    private volatile int attemptsSinceOk = 0;

    public CoinGeckoService(BiConsumer<String, String> onDescription,
                            BiConsumer<String, String> onStatus) {
        this.onDescription = onDescription;
        this.onStatus = onStatus;
    }

    public synchronized void load(String coinTicker) {
        stopPolling();

        currentTicker = coinTicker;
        long myId = requestId.incrementAndGet();
        attemptsSinceOk = 0;

        String geckoId = BINANCE_TO_GECKO.get(coinTicker);
        if (geckoId == null) {
            onStatus.accept(coinTicker, "error");
            onDescription.accept(coinTicker, null);
            return;
        }

        onStatus.accept(coinTicker, "loading");


        currentPoll = scheduler.scheduleAtFixedRate(() -> {

            if (attemptsSinceOk > 0 && lastOkFor == myId) return;
            fetchOnce(coinTicker, geckoId, myId);
        }, 0, RETRY_PERIOD_SEC, TimeUnit.SECONDS);
    }

    private volatile long lastOkFor = -1;

    private void fetchOnce(String coinTicker, String geckoId, long myId) {
        if (myId != requestId.get()) return;

        client.fetchInfo(geckoId)
                .thenAccept(info -> {
                    if (myId != requestId.get()) return;

                    String desc = info != null
                            && info.description() != null
                            && info.description().en() != null
                            && !info.description().en().isBlank()
                            ? cleanHtml(info.description().en())
                            : null;

                    lastOkFor = myId;
                    onStatus.accept(coinTicker, "ok");
                    onDescription.accept(coinTicker, desc);
                })
                .exceptionally(ex -> {
                    if (myId != requestId.get()) return null;

                    System.err.println("CoinGecko error for " + coinTicker
                            + " (attempt " + (++attemptsSinceOk) + "): " + ex.getMessage());
                    onStatus.accept(coinTicker, "error");
                    // Не шлём onDescription(null) — оставим предыдущее описание или "Загрузка…"
                    return null;
                });
    }

    public synchronized void stopPolling() {
        ScheduledFuture<?> f = currentPoll;
        if (f != null) {
            f.cancel(false);
            currentPoll = null;
        }
        requestId.incrementAndGet(); // инвалидируем «в полёте»
    }


    private static String cleanHtml(String html) {
        String noTags = html.replaceAll("<[^>]+>", "");
        String decoded = noTags
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        return decoded.replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}

