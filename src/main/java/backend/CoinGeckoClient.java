package backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;

public class CoinGeckoClient {

    private static final String BASE = "https://api.coingecko.com/api/v3/coins/";
    private static final String FIELDS =
            "?localization=false&tickers=false&market_data=true"
          + "&community_data=false&developer_data=false&sparkline=false";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();


    public CompletableFuture<CoinInfo> fetchInfo(String coinId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + coinId + FIELDS))
                .header("Accept", "application/json")
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new RuntimeException("HTTP " + resp.statusCode()
                                + " для " + coinId);
                    }
                    try {
                        return mapper.readValue(resp.body(), CoinInfo.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Не удалось распарсить JSON", e);
                    }
                });
    }

}