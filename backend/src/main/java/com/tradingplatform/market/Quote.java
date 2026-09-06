package com.tradingplatform.market;

import java.math.BigDecimal;
import java.time.Instant;
public record Quote(String symbol, String instrumentKey, BigDecimal price, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal previousClose, BigDecimal change, BigDecimal changePercent, BigDecimal volume, Instant timestamp) {}
