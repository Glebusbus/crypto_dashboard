package backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

public class WsPriceService {

    private static final String URL_TEMPLATE =
            "wss://stream.binance.com:9443/ws/%s@ticker";

    private final ObjectMapper mapper = new ObjectMapper();

    private final Consumer<Double> onPrice;
    private final Consumer<String> onStatus;

    private volatile WsClient current;
    private volatile String currentSymbol;

    public WsPriceService(Consumer<Double> onPrice, Consumer<String> onStatus) {
        this.onPrice = onPrice;
        this.onStatus = onStatus;
    }


    public synchronized void subscribe(String symbol) {
        if (symbol.equals(currentSymbol)) return;

        unsubscribe();

        currentSymbol = symbol;
        String url = String.format(URL_TEMPLATE, symbol.toLowerCase());

        WsClient client = new WsClient(
                url,
                this::handleMessage,
                status -> onStatus.accept(symbol + ":" + status)
        );
        current = client;
        client.start();
    }

    public synchronized void unsubscribe() {
        WsClient c = current;
        if (c != null) {
            c.stop();
            current = null;
        }
        currentSymbol = null;
    }

    private void handleMessage(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode c = root.get("c");
            if (c != null && c.isTextual()) {
                double price = Double.parseDouble(c.asText());
                onPrice.accept(price);
            }
        } catch (Exception e) {
            System.err.println("WsPriceService: bad JSON: " + e.getMessage());
        }
    }
}