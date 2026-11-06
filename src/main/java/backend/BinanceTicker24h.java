package backend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTicker24h(
        String symbol,
        @JsonProperty("priceChange")        String priceChange,
        @JsonProperty("priceChangePercent") String priceChangePercent,
        @JsonProperty("weightedAvgPrice")   String weightedAvgPrice,
        @JsonProperty("lastPrice")          String lastPrice,
        @JsonProperty("openPrice")          String openPrice,
        @JsonProperty("highPrice")          String highPrice,
        @JsonProperty("lowPrice")           String lowPrice,
        @JsonProperty("volume")             String volume,
        @JsonProperty("quoteVolume")        String quoteVolume,
        @JsonProperty("openTime")           long openTime,
        @JsonProperty("closeTime")          long closeTime
) {}