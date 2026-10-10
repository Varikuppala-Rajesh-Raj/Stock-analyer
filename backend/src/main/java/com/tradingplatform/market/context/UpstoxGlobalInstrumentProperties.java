package com.tradingplatform.market.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.upstox.instruments")
public record UpstoxGlobalInstrumentProperties(
        String giftNifty,
        String sp500,
        String usTech100,
        String dow,
        String nikkei,
        String hangSeng,
        String usdInr,
        String crudeOil,
        String brent,
        String wti
) {
}
