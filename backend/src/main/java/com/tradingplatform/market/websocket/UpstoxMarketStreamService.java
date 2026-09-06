package com.tradingplatform.market.websocket;

import com.tradingplatform.paper.PaperTickListener;
import com.upstox.ApiClient;
import com.upstox.Configuration;
import com.upstox.feeder.MarketDataStreamerV3;
import com.upstox.feeder.MarketUpdateV3;
import com.upstox.feeder.constants.Mode;
import com.upstox.feeder.listener.OnMarketUpdateV3Listener;
import com.upstox.feeder.listener.OnCloseListener;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service("upstoxMarketStreamService")
public class UpstoxMarketStreamService implements MarketStreamService {

    private static final Logger log =
            LoggerFactory.getLogger(UpstoxMarketStreamService.class);

    private final PaperTickListener listener;
    private final String accessToken;
    private final boolean enabled;
    private final Mode mode;
    private final Set<String> configuredInstruments;
    private final Set<String> subscribedInstruments =
            ConcurrentHashMap.newKeySet();

    private volatile StreamStatus status = StreamStatus.NOT_CONFIGURED;

    private MarketDataStreamerV3 streamer;

    public UpstoxMarketStreamService(
            PaperTickListener listener,
            @Value("${trading.upstox.UPSTOX_ACCESS_TOKEN:}") String accessToken,
            @Value("${trading.upstox.websocket.enabled:false}") boolean enabled,
            @Value("${trading.upstox.websocket.mode:ltpc}") String mode,
            @Value("${trading.upstox.websocket.instruments:NSE_INDEX|Nifty 50}") String instruments
    ) {
        this.listener = listener;
        this.accessToken = accessToken;
        this.enabled = enabled;
        this.mode = parseMode(mode);
        this.configuredInstruments = parseInstruments(instruments);

        // Do not make configured instruments implicit watchlist subscriptions.
        // The WatchlistController resolves symbols and owns subscriptions.
        if (!enabled) {
            status = StreamStatus.NOT_CONFIGURED;
        }
    }

    /**
     * Converts application configuration string into
     * the Upstox SDK Mode enum.
     *
     * Supported:
     * ltpc
     * full
     * full_d30
     * option_greeks
     */
    private Mode parseMode(String mode) {

        if (mode == null || mode.isBlank()) {
            return Mode.LTPC;
        }

        return switch (mode.trim().toLowerCase()) {

            case "full" ->
                    Mode.FULL;

            case "full_d30" ->
                    Mode.FULL_D30;

            case "option_greeks" ->
                    Mode.OPTION_GREEKS;

            case "ltpc" ->
                    Mode.LTPC;

            default -> {
                log.warn(
                        "Unknown Upstox WebSocket mode '{}'. Using LTPC.",
                        mode
                );

                yield Mode.LTPC;
            }
        };
    }

    @Override
    public synchronized void connect() {

        if (!enabled) {
            status = StreamStatus.NOT_CONFIGURED;
            log.info("Upstox WebSocket is disabled.");
            return;
        }

        if (accessToken == null || accessToken.isBlank()) {
            status = StreamStatus.NOT_CONFIGURED;
            log.warn(
                    "Upstox WebSocket is enabled but access token is missing."
            );
            return;
        }

        if (streamer != null) {
            log.info("Upstox WebSocket is already initialized.");
            return;
        }

        try {

            status = StreamStatus.CONNECTING;

            ApiClient apiClient =
                    Configuration.getDefaultApiClient();

            /*
             * Configure OAuth access token.
             *
             * Upstox SDK uses the authentication name "Oauth".
             */
            apiClient.setAccessToken(accessToken);

            /*
             * SDK 1.27 constructor.
             */
            streamer =
                    new MarketDataStreamerV3(apiClient);

            /*
             * Connection opened.
             */
            streamer.setOnOpenListener(() -> {

                status = StreamStatus.CONNECTED;

                try {

                    if (!subscribedInstruments.isEmpty()) {
                        streamer.subscribe(
                                new HashSet<>(subscribedInstruments),
                                mode
                        );
                    }

                    log.info(
                            "Subscribed to {} instruments using mode {}",
                            subscribedInstruments.size(),
                            mode
                    );

                } catch (Exception e) {

                    status = StreamStatus.ERROR;

                    log.error(
                            "Failed to subscribe to Upstox instruments",
                            e
                    );
                }
            });

            /*
             * Market update listener.
             */
            streamer.setOnMarketUpdateListener(
                    new OnMarketUpdateV3Listener() {

                        @Override
                        public void onUpdate(
                                MarketUpdateV3 update
                        ) {

                            handleUpdate(update);
                        }
                    }
            );

            /*
             * WebSocket closed.
             */
            streamer.setOnCloseListener(
        new OnCloseListener() {
            @Override
            public void onClose(int code, String reason) {
                status = StreamStatus.DISCONNECTED;

                log.warn(
                        "Upstox V3 WebSocket connection closed. code={}, reason={}",
                        code,
                        reason
                );
            }
        }
);

            /*
             * WebSocket error.
             */
            streamer.setOnErrorListener(error -> {

                status = StreamStatus.ERROR;

                log.error(
                        "Upstox V3 WebSocket error: {}",
                        error
                );
            });

            /*
             * Automatically reconnect.
             */
            streamer.autoReconnect(
                    true,
                    5,
                    10
            );

            /*
             * Start connection.
             */
            streamer.connect();

        } catch (Exception e) {

            status = StreamStatus.ERROR;

            log.error(
                    "Unable to start Upstox V3 WebSocket",
                    e
            );
        }
    }

    /**
     * Processes a single Upstox V3 market update.
     */
    private void handleUpdate(
            MarketUpdateV3 update
    ) {

        try {
                log.info("🔥 MARKET UPDATE RECEIVED: {}", update);

        if (update == null || update.getFeeds() == null) {
            log.warn("Market update has no feeds");
            return;
        }


            Map<String, MarketUpdateV3.Feed> feeds =
                    update.getFeeds();

            if (feeds == null || feeds.isEmpty()) {
                return;
            }

            for (
                    Map.Entry<String, MarketUpdateV3.Feed> entry
                    : feeds.entrySet()
            ) {

                String instrumentKey =
                        entry.getKey();

                MarketUpdateV3.Feed feed =
                        entry.getValue();

                if (feed == null) {
                    continue;
                }

                processFeed(
                        instrumentKey,
                        feed
                );
            }

        } catch (Exception e) {

            log.warn(
                    "Unable to process Upstox market update",
                    e
            );
        }
    }

    /**
     * Extracts LTP information from the Upstox Feed.
     */
    private void processFeed(
        String instrumentKey,
        MarketUpdateV3.Feed feed
) {

    BigDecimal ltp = BigDecimal.ZERO;
    BigDecimal closePrice = BigDecimal.ZERO;

    long timestamp = System.currentTimeMillis();
    long quantity = 0L;

    MarketUpdateV3.LTPC ltpc = feed.getLtpc();

    if (ltpc != null) {

        ltp = BigDecimal.valueOf(ltpc.getLtp());

        closePrice = BigDecimal.valueOf(ltpc.getCp());

        timestamp = ltpc.getLtt();

        quantity = ltpc.getLtq();
    }

    if (ltpc == null && feed.getFullFeed() != null) {

        var fullFeed = feed.getFullFeed();

        if (fullFeed.getMarketFF() != null) {

            var marketFullFeed = fullFeed.getMarketFF();

            var fullLtpc = marketFullFeed.getLtpc();

            if (fullLtpc != null) {

                ltp = BigDecimal.valueOf(
                        fullLtpc.getLtp()
                );

                closePrice = BigDecimal.valueOf(
                        fullLtpc.getCp()
                );

                timestamp = fullLtpc.getLtt();

                quantity = fullLtpc.getLtq();
            }
        }
    }

    if (ltp.compareTo(BigDecimal.ZERO) == 0) {
        return;
    }

    MarketTick tick = new MarketTick(
            instrumentKey,
            Instant.ofEpochMilli(timestamp),
            ltp,
            BigDecimal.valueOf(quantity),
            null,
            null,
            null,
            null,
            closePrice
    );

    listener.accept(tick);

    log.debug(
            "Paper tick sent: instrument={}, price={}, quantity={}",
            instrumentKey,
            ltp,
            quantity
    );
}


    @Override
    public synchronized void disconnect() {

        if (streamer == null) {

            status =
                    StreamStatus.DISCONNECTED;

            return;
        }

        try {

            streamer.disconnect();

        } catch (Exception e) {

            log.warn(
                    "Error while disconnecting Upstox stream",
                    e
            );

        } finally {

            streamer = null;

            status =
                    StreamStatus.DISCONNECTED;
        }
    }

    @Override
    public synchronized void subscribe(Set<String> keys) {

    if (keys == null || keys.isEmpty()) {
        return;
    }

    // Always remember requested instruments.
    subscribedInstruments.addAll(keys);

    // If WebSocket isn't ready yet, the onOpen callback
    // will subscribe to all instruments automatically.
    if (streamer == null || status != StreamStatus.CONNECTED) {

        log.info(
                "Queued {} instruments for WebSocket subscription: {}",
                keys.size(),
                keys
        );

        if (streamer == null) {
            connect();
        }

        return;
    }

    try {

        streamer.subscribe(
                new HashSet<>(keys),
                mode
        );

        log.info(
                "Dynamically subscribed to: {}",
                keys
        );

    } catch (Exception e) {

        status = StreamStatus.ERROR;

        log.error(
                "Failed to dynamically subscribe to Upstox instruments",
                e
        );
    }
}
   

    @Override
    public synchronized void unsubscribe(Set<String> keys) {

    if (keys == null || keys.isEmpty()) {
        return;
    }

    subscribedInstruments.removeAll(keys);

    if (streamer == null || status != StreamStatus.CONNECTED) {
        log.info(
                "Removed instruments from pending WebSocket subscriptions: {}",
                keys
        );
        return;
    }

    try {

        streamer.unsubscribe(
                new HashSet<>(keys)
        );

        log.info(
                "Dynamically unsubscribed from: {}",
                keys
        );

    } catch (Exception e) {

        status = StreamStatus.ERROR;

        log.error(
                "Failed to dynamically unsubscribe from Upstox instruments",
                e
        );
    }
}


    @Override
    public StreamStatus status() {

        return status;
    }



    public Set<String> subscribedInstruments() {
    return Set.copyOf(subscribedInstruments);   
    }
    /**
     * Parses comma-separated instrument keys.
     *
     * Example:
     *
     * NSE_INDEX|Nifty 50,NSE_EQ|INE009A01021
     */
    private Set<String> parseInstruments(
            String instruments
    ) {

        if (
                instruments == null
                        || instruments.isBlank()
        ) {

            return Collections.emptySet();
        }

        return new HashSet<>(
                Arrays.stream(
                                instruments.split(",")
                        )
                        .map(String::trim)
                        .filter(
                                value ->
                                        !value.isBlank()
                        )
                        .toList()
        );
    }
}
