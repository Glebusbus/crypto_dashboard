package backend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinInfo(
        String id,
        String symbol,
        String name,
        Description description,
        @JsonProperty("market_data") MarketData marketData,
        Links links
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(String en) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketData(
            @JsonProperty("current_price") java.util.Map<String, Double> currentPrice,
            @JsonProperty("market_cap")    java.util.Map<String, Double> marketCap,
            @JsonProperty("total_volume")  java.util.Map<String, Double> totalVolume,
            @JsonProperty("price_change_percentage_24h") Double priceChange24h
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(java.util.List<String> homepage) {}
}