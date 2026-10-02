package com.tradingplatform.market.nse;

import com.tradingplatform.persistence.NseFuturesObservationEntity;
import com.tradingplatform.persistence.NseFuturesObservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NseFuturesObservationService {
    private static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");
    private final NseFuturesObservationRepository observations;

    public NseFuturesObservationService(NseFuturesObservationRepository observations) {
        this.observations = observations;
    }

    @Transactional
    public void persist(NseFuturesObservation observation) {
        if (observation == null || observation.timestamp() == null
                || observation.identifier() == null || observation.identifier().isBlank()) {
            throw new IllegalArgumentException("Timestamped NIFTY futures observation is required.");
        }
        NseFuturesObservationEntity entity = observations
                .findByIdentifierAndTimestamp(observation.identifier(), observation.timestamp())
                .orElseGet(NseFuturesObservationEntity::new);
        entity.update(observation);
        observations.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<NseFuturesObservation> latestAtOrBefore(Instant asOf, Duration maxAge) {
        if (!validQuery(asOf, maxAge)) return Optional.empty();
        return observationAtOrBefore(asOf)
                .filter(entity -> !entity.timestamp.isAfter(asOf))
                .filter(entity -> Duration.between(entity.timestamp, asOf).compareTo(maxAge) <= 0)
                .map(NseFuturesObservationEntity::toObservation);
    }

    @Transactional(readOnly = true)
    public Optional<NseFuturesM5Features> fiveMinuteFeaturesAt(Instant asOf, Duration maxAge) {
        if (!validQuery(asOf, maxAge)) return Optional.empty();

        Instant fiveMinutesAgo = asOf.minus(Duration.ofMinutes(5));
        Optional<NseFuturesObservationEntity> latest = observationAtOrBefore(asOf);
        Optional<NseFuturesObservationEntity> prior = observationAtOrBefore(fiveMinutesAgo);
        if (latest.isEmpty() || prior.isEmpty()) return Optional.empty();

        NseFuturesObservationEntity current = latest.get();
        NseFuturesObservationEntity previous = prior.get();
        if (!freshAt(current, asOf, maxAge) || !freshAt(previous, fiveMinutesAgo, maxAge)
                || !current.identifier.equals(previous.identifier)
                || !current.expiry.equals(previous.expiry)
                || !current.timestamp.atZone(NSE_ZONE).toLocalDate()
                        .equals(previous.timestamp.atZone(NSE_ZONE).toLocalDate())) {
            return Optional.empty();
        }

        Long currentVolume = current.cumulativeVolume;
        Long priorVolume = previous.cumulativeVolume;
        if (currentVolume == null || priorVolume == null || currentVolume < priorVolume
                || current.openInterest == null || previous.openInterest == null) {
            return Optional.empty();
        }

        return Optional.of(new NseFuturesM5Features(
                asOf,
                current.timestamp,
                current.identifier,
                current.expiry,
                current.lastPrice.doubleValue(),
                currentVolume - priorVolume,
                current.openInterest,
                current.openInterest - previous.openInterest,
                current.changeInOpenInterest,
                current.underlyingValue.doubleValue()));
    }

    @Transactional(readOnly = true)
    public NseFuturesAlignmentAudit auditFiveMinuteAlignment(List<Instant> predictionTimestamps, Duration maxAge) {
        if (predictionTimestamps == null || predictionTimestamps.isEmpty() || maxAge == null || maxAge.isNegative()) {
            return new NseFuturesAlignmentAudit(0, 0, 0, 0, 0, 0, 0, 0, null, null);
        }
        List<Instant> orderedTimes = predictionTimestamps.stream().filter(java.util.Objects::nonNull)
                .distinct().sorted().toList();
        if (orderedTimes.isEmpty()) return new NseFuturesAlignmentAudit(0, 0, 0, 0, 0, 0, 0, 0, null, null);

        Instant earliest = orderedTimes.get(0);
        Instant latest = orderedTimes.get(orderedTimes.size() - 1);
        Instant queryFrom = earliest.minus(Duration.ofMinutes(5)).minus(maxAge);
        List<NseFuturesObservationEntity> rows = observations
                .findByTimestampBetweenOrderByTimestampAscIdAsc(queryFrom, latest);
        NavigableMap<Instant, NseFuturesObservationEntity> history = new TreeMap<>();
        for (NseFuturesObservationEntity row : rows) {
            NseFuturesObservationEntity existing = history.get(row.timestamp);
            if (existing == null || (row.id != null && existing.id != null && row.id > existing.id)) {
                history.put(row.timestamp, row);
            }
        }

        int aligned = 0;
        int missingCurrent = 0;
        int missingBaseline = 0;
        int stale = 0;
        int rollover = 0;
        int sessionBoundary = 0;
        int unavailable = 0;
        for (Instant predictionTime : orderedTimes) {
            var currentEntry = history.floorEntry(predictionTime);
            if (currentEntry == null) {
                missingCurrent++;
                continue;
            }
            NseFuturesObservationEntity current = currentEntry.getValue();
            Instant priorTime = predictionTime.minus(Duration.ofMinutes(5));
            var priorEntry = history.floorEntry(priorTime);
            if (priorEntry == null) {
                missingBaseline++;
                continue;
            }
            NseFuturesObservationEntity previous = priorEntry.getValue();
            if (!freshAt(current, predictionTime, maxAge) || !freshAt(previous, priorTime, maxAge)) {
                stale++;
                continue;
            }
            if (!current.identifier.equals(previous.identifier) || !current.expiry.equals(previous.expiry)) {
                rollover++;
                continue;
            }
            if (!current.timestamp.atZone(NSE_ZONE).toLocalDate()
                    .equals(previous.timestamp.atZone(NSE_ZONE).toLocalDate())) {
                sessionBoundary++;
                continue;
            }
            if (current.cumulativeVolume == null || previous.cumulativeVolume == null
                    || current.cumulativeVolume < previous.cumulativeVolume
                    || current.openInterest == null || previous.openInterest == null) {
                unavailable++;
                continue;
            }
            aligned++;
        }
        return new NseFuturesAlignmentAudit(orderedTimes.size(), aligned, missingCurrent, missingBaseline,
                stale, rollover, sessionBoundary, unavailable,
                rows.isEmpty() ? null : rows.get(0).timestamp,
                rows.isEmpty() ? null : rows.get(rows.size() - 1).timestamp);
    }

    private Optional<NseFuturesObservationEntity> observationAtOrBefore(Instant timestamp) {
        return observations.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(timestamp);
    }

    private boolean freshAt(NseFuturesObservationEntity observation, Instant asOf, Duration maxAge) {
        return !observation.timestamp.isAfter(asOf)
                && Duration.between(observation.timestamp, asOf).compareTo(maxAge) <= 0;
    }

    private boolean validQuery(Instant asOf, Duration maxAge) {
        return asOf != null && maxAge != null && !maxAge.isNegative();
    }
}