package com.tradingplatform.market.ml;

import com.tradingplatform.market.indicators.FeatureVector;

public record MlTrainingRow(
        FeatureVector features,
        String target
) {
}