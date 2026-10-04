package com.tradingplatform.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.MarketDataUnavailableException;
import com.tradingplatform.market.options.NiftyOptionChainService;
import com.tradingplatform.persistence.ManualTradeEntity;
import com.tradingplatform.persistence.ManualTradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tradingplatform.paper.ManualTradeDtos.*;

@Service
public class ManualTradingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Duration MAX_OPTION_SNAPSHOT_AGE = Duration.ofMinutes(15);

    private final ManualTradeRepository trades;
    private final MarketDataService market;
    private final NiftyOptionChainService optionChains;
    private final ChargesCalculator charges;

    public ManualTradingService(
            ManualTradeRepository trades,
            MarketDataService market,
            NiftyOptionChainService optionChains,
            ChargesCalculator charges
    ) {
        this.trades = trades;
        this.market = market;
        this.optionChains = optionChains;
        this.charges = charges;
    }

    @Transactional
    public Trade buy(TradeRequest request) {
        validateRequest(request);
        ManualTradeEntity trade = new ManualTradeEntity();
        trade.id = UUID.randomUUID();
        trade.quantity = request.quantity();
        trade.status = ManualTradeStatus.OPEN;
        trade.openedAt = Instant.now();
        trade.closedAt = null;
        trade.exitPrice = null;
        trade.exitCharges = ZERO;
        trade.realizedPnl = ZERO;
        trade.unrealizedPnl = ZERO;

        String type = request.instrumentType().trim().toUpperCase();
        if ("STOCK".equals(type)) {
            buyStock(request, trade);
        } else if ("OPTION".equals(type)) {
            buyOption(request, trade, new HashMap<>());
        } else {
            throw new IllegalArgumentException("Instrument type must be STOCK or OPTION.");
        }

        trade.entryCharges = charges.charge(trade.entryPrice.multiply(BigDecimal.valueOf(trade.quantity)));
        trade.priceUpdatedAt = Instant.now();
        trade.unrealizedPnl = netOpenPnl(trade, trade.currentPrice);
        return toDto(trades.save(trade));
    }

    @Transactional
    public Portfolio portfolio() {
        List<ManualTradeEntity> savedTrades = trades.findAllByOrderByOpenedAtDesc();
        Map<String, JsonNode> optionChainByExpiry = new HashMap<>();
        for (ManualTradeEntity trade : savedTrades) {
            if (trade.status != ManualTradeStatus.OPEN) continue;
            BigDecimal price = currentPrice(trade, optionChainByExpiry);
            trade.currentPrice = price;
            trade.priceUpdatedAt = Instant.now();
            trade.unrealizedPnl = netOpenPnl(trade, price);
            trades.save(trade);
        }

        List<Trade> results = savedTrades.stream().map(ManualTradingService::toDto).toList();
        BigDecimal realized = savedTrades.stream()
                .filter(trade -> trade.status == ManualTradeStatus.CLOSED)
                .map(trade -> trade.realizedPnl)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal unrealized = savedTrades.stream()
                .filter(trade -> trade.status == ManualTradeStatus.OPEN)
                .map(trade -> trade.unrealizedPnl)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int closed = (int) savedTrades.stream()
                .filter(trade -> trade.status == ManualTradeStatus.CLOSED).count();
        int winners = (int) savedTrades.stream()
                .filter(trade -> trade.status == ManualTradeStatus.CLOSED && trade.realizedPnl.signum() > 0).count();
        int losers = (int) savedTrades.stream()
                .filter(trade -> trade.status == ManualTradeStatus.CLOSED && trade.realizedPnl.signum() < 0).count();

        return new Portfolio(
                results,
                realized,
                unrealized,
                realized.add(unrealized).setScale(2, RoundingMode.HALF_UP),
                savedTrades.size() - closed,
                closed,
                winners,
                losers
        );
    }

    @Transactional
    public Trade sell(UUID id) {
        ManualTradeEntity trade = trades.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown manual trade."));
        if (trade.status != ManualTradeStatus.OPEN) {
            throw new IllegalArgumentException("Manual trade is already closed.");
        }

        BigDecimal exitPrice = currentPrice(trade, new HashMap<>());
        trade.currentPrice = exitPrice;
        trade.exitPrice = exitPrice;
        trade.exitCharges = charges.charge(exitPrice.multiply(BigDecimal.valueOf(trade.quantity)));
        trade.realizedPnl = grossPnl(trade, exitPrice)
                .subtract(trade.entryCharges)
                .subtract(trade.exitCharges)
                .setScale(2, RoundingMode.HALF_UP);
        trade.unrealizedPnl = ZERO;
        trade.status = ManualTradeStatus.CLOSED;
        trade.closedAt = Instant.now();
        trade.priceUpdatedAt = trade.closedAt;
        return toDto(trades.save(trade));
    }

    private void buyStock(TradeRequest request, ManualTradeEntity trade) {
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("A stock symbol is required.");
        }
        Instrument instrument = market.instrument(request.symbol().trim());
        if (instrument.segment() != null && instrument.segment().toUpperCase().contains("FO")) {
            throw new IllegalArgumentException("Choose a stock or use the options selector for option contracts.");
        }
        trade.instrumentType = "STOCK";
        trade.instrumentKey = instrument.instrumentKey();
        trade.symbol = instrument.symbol();
        trade.exchange = instrument.exchange();
        trade.expiry = null;
        trade.optionType = null;
        trade.strike = null;
        trade.entryPrice = market.quote(instrument).price();
        trade.currentPrice = trade.entryPrice;
    }

    private void buyOption(TradeRequest request, ManualTradeEntity trade, Map<String, JsonNode> chainCache) {
        if (request.expiry() == null || request.expiry().isBlank()) {
            throw new IllegalArgumentException("An option expiry is required.");
        }
        LocalDate expiry;
        try {
            expiry = LocalDate.parse(request.expiry());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Option expiry must be a valid date.", exception);
        }
        if (expiry.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot open a position in an expired option.");
        }
        if (!"CE".equalsIgnoreCase(request.optionType()) && !"PE".equalsIgnoreCase(request.optionType())) {
            throw new IllegalArgumentException("Option type must be CE or PE.");
        }

        JsonNode chain = optionChain(expiry.toString(), chainCache);
        JsonNode data = chain.path("data");
        String optionType = request.optionType().toUpperCase();
        for (JsonNode row : data) {
            JsonNode option = row.path("CE".equals(optionType) ? "call_options" : "put_options");
            String key = option.path("instrument_key").asText("");
            BigDecimal price = option.path("market_data").path("ltp").decimalValue();
            if (!key.equals(request.instrumentKey())) continue;
            if (key.isBlank() || price.signum() <= 0) continue;

            trade.instrumentType = "OPTION";
            trade.instrumentKey = key;
            trade.symbol = "NIFTY " + row.path("strike_price").asText() + " " + optionType;
            trade.exchange = "NSE_FO";
            trade.expiry = expiry;
            trade.optionType = optionType;
            trade.strike = row.path("strike_price").decimalValue();
            trade.entryPrice = price;
            trade.currentPrice = price;
            return;
        }
        throw new IllegalArgumentException("The selected NIFTY option is not available in the current option chain.");
    }

    private BigDecimal currentPrice(ManualTradeEntity trade, Map<String, JsonNode> chainCache) {
        if ("STOCK".equals(trade.instrumentType)) {
            Instrument instrument = market.instrument(trade.instrumentKey);
            BigDecimal price = market.quote(instrument).price();
            requirePrice(price, trade.symbol);
            return price;
        }

        JsonNode chain = optionChain(trade.expiry.toString(), chainCache);
        String optionSide = "CE".equals(trade.optionType) ? "call_options" : "put_options";
        for (JsonNode row : chain.path("data")) {
            JsonNode option = row.path(optionSide);
            if (trade.instrumentKey.equals(option.path("instrument_key").asText())) {
                BigDecimal price = option.path("market_data").path("ltp").decimalValue();
                requirePrice(price, trade.symbol);
                return price;
            }
        }
        throw new MarketDataUnavailableException("Current option-chain price is unavailable for " + trade.symbol + ".");
    }

    private JsonNode optionChain(String expiry, Map<String, JsonNode> chainCache) {
        return chainCache.computeIfAbsent(expiry, date -> {
            JsonNode chain = optionChains.getNiftyOptionChain(date);
            if ("HISTORICAL_SNAPSHOT".equals(chain.path("dataSource").asText())) {
                String timestamp = chain.path("snapshotTimestamp").asText("");
                Instant capturedAt;
                try {
                    capturedAt = Instant.parse(timestamp);
                } catch (RuntimeException exception) {
                    throw new MarketDataUnavailableException("Historical option-chain snapshot has no valid timestamp.");
                }
                Duration age = Duration.between(capturedAt, Instant.now());
                if (age.isNegative() || age.compareTo(MAX_OPTION_SNAPSHOT_AGE) > 0) {
                    throw new MarketDataUnavailableException("The latest saved option-chain quote is older than 15 minutes.");
                }
            }
            if (!chain.path("data").isArray() || chain.path("data").isEmpty()) {
                throw new MarketDataUnavailableException("No current option-chain prices are available for expiry " + date + ".");
            }
            return chain;
        });
    }

    private static void validateRequest(TradeRequest request) {
        if (request == null) throw new IllegalArgumentException("Trade details are required.");
        if (request.instrumentType() == null || request.instrumentType().isBlank()) {
            throw new IllegalArgumentException("Instrument type is required.");
        }
        if (request.quantity() < 1) throw new IllegalArgumentException("Quantity must be at least 1.");
    }

    private static void requirePrice(BigDecimal price, String symbol) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalStateException("A valid market price is unavailable for " + symbol + ".");
        }
    }

    private static BigDecimal grossPnl(ManualTradeEntity trade, BigDecimal price) {
        return price.subtract(trade.entryPrice)
                .multiply(BigDecimal.valueOf(trade.quantity));
    }

    private static BigDecimal netOpenPnl(ManualTradeEntity trade, BigDecimal price) {
        return grossPnl(trade, price).subtract(trade.entryCharges).setScale(2, RoundingMode.HALF_UP);
    }

    private static Trade toDto(ManualTradeEntity trade) {
        return new Trade(
                trade.id,
                trade.instrumentType,
                trade.instrumentKey,
                trade.symbol,
                trade.exchange,
                trade.expiry,
                trade.optionType,
                trade.strike,
                trade.quantity,
                trade.entryPrice,
                trade.currentPrice,
                trade.exitPrice,
                trade.entryCharges,
                trade.exitCharges,
                trade.unrealizedPnl,
                trade.realizedPnl,
                trade.status,
                trade.openedAt,
                trade.closedAt,
                trade.priceUpdatedAt
        );
    }
}
