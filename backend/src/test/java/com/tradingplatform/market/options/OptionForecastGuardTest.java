package com.tradingplatform.market.options;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OptionForecastGuardTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsCompleteFiniteForecast() throws Exception {
        var direction = json.readTree("{\"prediction\":\"UP\",\"probabilities\":{\"DOWN\":0.1,\"NEUTRAL\":0.2,\"UP\":0.7}}");
        assertDoesNotThrow(() -> OptionForecastGuard.requireUsable(direction, 0.4));
    }

    @Test
    void rejectsMissingOrNonFiniteForecastInputs() throws Exception {
        var missing = json.readTree("{\"prediction\":\"UP\",\"probabilities\":{}} ");
        assertThrows(IllegalStateException.class, () -> OptionForecastGuard.requireUsable(missing, 0.4));
        var direction = json.readTree("{\"prediction\":\"UP\",\"probabilities\":{\"DOWN\":0.1,\"NEUTRAL\":0.2,\"UP\":0.7}}");
        assertThrows(IllegalStateException.class, () -> OptionForecastGuard.requireUsable(direction, Double.NaN));
    }
}