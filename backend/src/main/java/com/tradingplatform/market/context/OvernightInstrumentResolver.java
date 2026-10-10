package com.tradingplatform.market.context;

import java.util.List;

public interface OvernightInstrumentResolver {
    List<ResolvedOvernightInstrument> resolve(String instrument, String configuredKey, String fallbackSegment);
}
