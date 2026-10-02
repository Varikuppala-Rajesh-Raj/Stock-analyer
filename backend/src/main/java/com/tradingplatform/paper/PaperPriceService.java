package com.tradingplatform.paper;

import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.Quote;
import com.tradingplatform.market.websocket.MarketTick;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaperPriceService {
    public record Price(BigDecimal value, Instant timestamp) {}

    private final ConcurrentMap<String, Price> ticks = new ConcurrentHashMap<>();
    private final MarketDataService market;
    private final Duration maxAge;

    public PaperPriceService(
            MarketDataService market,
            @Value("${trading.paper.max-price-age-seconds:30}") long seconds
    ) {
        this.market = market;
        this.maxAge = Duration.ofSeconds(seconds);
    }

    public void accept(MarketTick tick) {
        if (tick != null && tick.instrumentKey() != null && tick.lastPrice() != null
                && tick.lastPrice().signum() > 0 && tick.timestamp() != null) {
            ticks.put(tick.instrumentKey(), new Price(tick.lastPrice(), tick.timestamp()));
        }
    }

    public void clear() {
        ticks.clear();
    }

    public Price current(Instrument instrument) {
        Instant now = Instant.now();
        Price tick = ticks.get(instrument.instrumentKey());
        if (fresh(tick, now)) return tick;

        Quote quote = market.quote(instrument);
        Price current = new Price(quote.price(), quote.timestamp());
        if (!fresh(current, Instant.now())) {
            throw new MarketDataUnavailableException("Market data is stale for " + instrument.symbol());
        }
        return current;
    }

    private boolean fresh(Price price, Instant now) {
        if (price == null || price.value() == null || price.value().signum() <= 0 || price.timestamp() == null) {
            return false;
        }
        Duration age = Duration.between(price.timestamp(), now);
        return !age.isNegative() && age.compareTo(maxAge) <= 0;
    }
}
