package com.tradingplatform.market.nse;

import java.time.Instant;

public record NseFuturesAlignmentAudit(
        int requestedRows,
        int alignedRows,
        int missingCurrentSnapshots,
        int missingFiveMinuteBaselines,
        int staleSnapshotPairs,
        int contractRolloverPairs,
        int sessionBoundaryPairs,
        int unavailableCumulativeFields,
        Instant earliestSnapshot,
        Instant latestSnapshot
) {}