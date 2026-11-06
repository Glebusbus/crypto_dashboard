package backend;

import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicLong;

public class BinanceRestService {

    private static final long POLL_PERIOD_SEC = 5;

    private final BinanceRestClient client = new BinanceRestClient();

    private final BiConsumer<String, BinanceTicker24h> onData;

    private final BiConsumer<String, String> onStatus;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "binance-rest-poll");
                t.setDaemon(true);
                return t;
            });

    private volatile ScheduledFuture<?> currentPoll;

    private final AtomicLong subscriptionId = new AtomicLong();
    private volatile String currentSymbol;

    public BinanceRestService(BiConsumer<String, BinanceTicker24h> onData,
                              BiConsumer<String, String> onStatus) {
        this.onData = onData;
        this.onStatus = onStatus;
    }


    public synchronized void subscribe(String coinTicker) {
        String symbol = coinTicker + "USDT";

        if (symbol.equals(currentSymbol)) return;

        stopPolling();

        currentSymbol = symbol;
        long mySubId = subscriptionId.incrementAndGet();

        onStatus.accept(coinTicker, "loading");

        currentPoll = scheduler.scheduleAtFixedRate(
                () -> pollOnce(coinTicker, symbol, mySubId),
                0, POLL_PERIOD_SEC, TimeUnit.SECONDS
        );
    }

    public synchronized void unsubscribe() {
        stopPolling();
        currentSymbol = null;
    }

    private void stopPolling() {
        ScheduledFuture<?> f = currentPoll;
        if (f != null) {
            f.cancel(false);
            currentPoll = null;
        }
        subscriptionId.incrementAndGet();
    }

    private void pollOnce(String coinTicker, String symbol, long mySubId) {
        if (mySubId != subscriptionId.get()) return;

        client.fetch24h(symbol)
                .thenAccept(data -> {
                    if (mySubId != subscriptionId.get()) return;
                    onStatus.accept(coinTicker, "ok");
                    onData.accept(coinTicker, data);
                })
                .exceptionally(ex -> {
                    if (mySubId != subscriptionId.get()) return null;
                    System.err.println("Binance REST error for " + symbol
                            + ": " + ex.getMessage());
                    onStatus.accept(coinTicker, "error");
                    return null;
                });
    }
}