package com.tradingplatform.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class OvernightMarketDataRepositoryTest {

    @Autowired
    private OvernightMarketDataRepository repository;

    @Test
    void persistsAppendOnlySnapshotsAndKeepsProviderAndRetrievalTimesDistinct() {
        Instant providerTimestamp = Instant.parse("2026-10-06T10:00:00Z");
        Instant firstRetrievedAt = Instant.parse("2026-10-06T10:00:05Z");
        Instant secondProviderTimestamp = Instant.parse("2026-10-06T10:05:00Z");
        Instant secondRetrievedAt = Instant.parse("2026-10-06T10:05:03Z");

        repository.save(snapshot(new BigDecimal("22650.5"), providerTimestamp, firstRetrievedAt));
        repository.save(snapshot(new BigDecimal("22680.5"), secondProviderTimestamp, secondRetrievedAt));
        repository.flush();

        var history = repository
                .findTop100ByInstrumentKeyAndLastPriceIsNotNullAndProviderTimestampIsNotNullOrderByRetrievedAtDescIdDesc(
                        "GLOBAL_INDEX|SGX NIFTY");

        assertEquals(2, history.size());
        assertEquals(0, new BigDecimal("22680.5").compareTo(history.get(0).lastPrice));
        assertEquals(secondProviderTimestamp, history.get(0).providerTimestamp);
        assertEquals(secondRetrievedAt, history.get(0).retrievedAt);
        assertEquals(providerTimestamp, history.get(1).providerTimestamp);
        assertEquals(firstRetrievedAt, history.get(1).retrievedAt);
    }

    private static OvernightMarketDataEntity snapshot(
            BigDecimal price,
            Instant providerTimestamp,
            Instant retrievedAt
    ) {
        OvernightMarketDataEntity entity = new OvernightMarketDataEntity();
        entity.instrumentName = "GIFT_NIFTY";
        entity.instrumentKey = "GLOBAL_INDEX|SGX NIFTY";
        entity.provider = "UPSTOX";
        entity.segment = "GLOBAL_INDEX";
        entity.lastPrice = price;
        entity.closePrice = new BigDecimal("22600");
        entity.netChange = price.subtract(entity.closePrice);
        entity.providerTimestamp = providerTimestamp;
        entity.retrievedAt = retrievedAt;
        entity.status = OvernightMarketDataEntity.Status.FRESH;
        return entity;
    }
}
