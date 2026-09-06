package com.tradingplatform.market.options;

import com.tradingplatform.market.indicators.CanonicalTechnicalFeatures;
import java.util.ArrayList;
import java.util.List;

/** Versioned, strict schema shared with the Python option-return model. */
public final class OptionFeatureSchema {
    public static final String VERSION = "nifty-option-features-v1";
    public static final List<String> NAMES;
    static {
        List<String> names = new ArrayList<>(CanonicalTechnicalFeatures.NAMES);
        names.addAll(List.of("strike", "option_type_encoded", "distance_from_atm_percent", "moneyness", "option_ltp", "bid", "ask", "spread", "spread_percent", "option_volume", "option_oi", "iv", "delta", "gamma", "theta", "vega", "time_to_expiry_minutes", "nifty_direction_down_probability", "nifty_direction_neutral_probability", "nifty_direction_up_probability", "nifty_predicted_return_percent", "nifty_expected_move_points"));
        NAMES = List.copyOf(names);
    }
    private OptionFeatureSchema() { }
}
