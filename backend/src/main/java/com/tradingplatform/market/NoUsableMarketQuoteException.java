package com.tradingplatform.market;

public class NoUsableMarketQuoteException extends MarketDataUnavailableException {
    public NoUsableMarketQuoteException(String message) {
        super(message);
    }
}
