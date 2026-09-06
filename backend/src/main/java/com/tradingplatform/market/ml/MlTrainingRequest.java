package com.tradingplatform.market.ml;

import java.util.List;

public record MlTrainingRequest(
        String symbol,
        int horizonMinutes,
        double movementThresholdPercent,
        List<MlTrainingRow> rows
) {
}