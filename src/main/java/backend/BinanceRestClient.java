package backend;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class BinanceRestClient {

    private static final String BASE = "https://api.binance.com/api/v3";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    public CompletableFuture<BinanceTicker24h> fetch24h(String symbol) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/ticker/24hr?symbol=" + symbol))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new RuntimeException("Binance REST HTTP " + resp.statusCode()
                                + " для " + symbol + ": " + resp.body());
                    }
                    try {
                        return mapper.readValue(resp.body(), BinanceTicker24h.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Binance REST: не удалось распарсить JSON", e);
                    }
                });
    }
}