package com.tradingplatform.market.context;

public record ResolvedOvernightInstrument(
        String instrumentKey,
        String exchange,
        String segment,
        String tradingSymbol
) {
}
