package com.tradingplatform.market;
public record InstrumentImportReport(int total,int valid,int inserted,int updated,int skipped,int failed) {}
