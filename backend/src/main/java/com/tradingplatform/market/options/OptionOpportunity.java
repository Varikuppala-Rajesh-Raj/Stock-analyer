package com.tradingplatform.market.options;

import java.math.BigDecimal;

public record OptionOpportunity(

        String instrumentKey,

        String optionType,

        BigDecimal strikePrice,

        BigDecimal spotPrice,

        BigDecimal ltp,

        BigDecimal bidPrice,

        BigDecimal askPrice,

        BigDecimal spreadPercent,

        BigDecimal delta,

        BigDecimal gamma,

        BigDecimal theta,

        BigDecimal vega,

        BigDecimal iv,

        BigDecimal oi,

        BigDecimal volume,

        BigDecimal score,

        String reason
) {
}