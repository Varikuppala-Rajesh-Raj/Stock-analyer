package com.tradingplatform.market.context;

import java.time.Instant;

public record OvernightGlobalSnapshot(
        Instant asOf,
        OvernightQuote giftNifty,
        OvernightQuote sp500,
        OvernightQuote usTech100,
        OvernightQuote dow,
        OvernightQuote nikkei,
        OvernightQuote hangSeng,
        OvernightQuote usdInr,
        OvernightQuote crudeOil,
        OvernightQuote gold,
        OvernightQuote ftse100,
        OvernightQuote dax,
        OvernightQuote brent
) {
}
