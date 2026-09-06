package com.tradingplatform.market;
public record Instrument(String instrumentKey, String symbol, String companyName, String exchange, String segment, String isin, boolean active) {}
