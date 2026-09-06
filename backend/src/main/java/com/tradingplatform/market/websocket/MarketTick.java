package com.tradingplatform.market.websocket;
import java.math.BigDecimal; import java.time.Instant;
/** Provider-independent live event; absent upstream fields remain null. */
public record MarketTick(String instrumentKey,Instant timestamp,BigDecimal lastPrice,BigDecimal lastQuantity,BigDecimal volume,BigDecimal open,BigDecimal high,BigDecimal low,BigDecimal close) {}
