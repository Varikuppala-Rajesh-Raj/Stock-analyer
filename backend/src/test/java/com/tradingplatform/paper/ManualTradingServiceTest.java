package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.market.Instrument;
import com.tradingplatform.market.MarketDataService;
import com.tradingplatform.market.Quote;
import com.tradingplatform.market.options.NiftyOptionChainService;
import com.tradingplatform.persistence.ManualTradeEntity;
import com.tradingplatform.persistence.ManualTradeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManualTradingServiceTest {
    @Mock private ManualTradeRepository trades;
    @Mock private MarketDataService market;
    @Mock private NiftyOptionChainService optionChains;
    @Mock private ChargesCalculator charges;
    @InjectMocks private ManualTradingService service;

    private final Instrument stock = new Instrument(
            "NSE_EQ|ABC", "ABC", "ABC Limited", "NSE", "NSE_EQ", "ISIN", true);

    private void saveTradesAsPassed() {
        when(trades.save(any(ManualTradeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(charges.charge(any(BigDecimal.class))).thenAnswer(invocation ->
                invocation.<BigDecimal>getArgument(0).multiply(new BigDecimal("0.01")));
    }

    @Test
    void stockTradeTracksMarkToMarketAndRealizesNetPnlOnSell() {
        saveTradesAsPassed();
        when(market.instrument("ABC")).thenReturn(stock);
        when(market.instrument(stock.instrumentKey())).thenReturn(stock);
        when(market.quote(stock))
                .thenReturn(quote("100"), quote("105"), quote("110"));

        var opened = service.buy(new ManualTradeDtos.TradeRequest("STOCK", "ABC", null, null, null, 2));
        assertEquals(ManualTradeStatus.OPEN, opened.status());
        assertEquals(new BigDecimal("2.00"), opened.entryCharges());

        when(trades.findAllByOrderByOpenedAtDesc()).thenReturn(List.of(entityFrom(opened)));
        var portfolio = service.portfolio();
        assertEquals(new BigDecimal("8.00"), portfolio.unrealizedPnl());
        assertEquals(1, portfolio.openTrades());

        ManualTradeEntity entity = entityFrom(opened);
        when(trades.findById(opened.id())).thenReturn(Optional.of(entity));
        var closed = service.sell(opened.id());
        assertEquals(ManualTradeStatus.CLOSED, closed.status());
        assertEquals(new BigDecimal("15.80"), closed.realizedPnl());
        assertEquals(0, closed.unrealizedPnl().compareTo(BigDecimal.ZERO));
    }

    @Test
    void optionTradeUsesTheSelectedContractRatherThanAnotherChainRow() throws Exception {
        saveTradesAsPassed();
        String expiry = LocalDate.now().plusWeeks(1).toString();
        var chain = new ObjectMapper().readTree("""
                {"data":[
                  {"strike_price":20000,"call_options":{"instrument_key":"NSE_FO|CALL1","market_data":{"ltp":100}}},
                  {"strike_price":20100,"call_options":{"instrument_key":"NSE_FO|CALL2","market_data":{"ltp":150}}}
                ]}
                """);
        when(optionChains.getNiftyOptionChain(expiry)).thenReturn(chain);

        var trade = service.buy(new ManualTradeDtos.TradeRequest(
                "OPTION", null, "NSE_FO|CALL2", expiry, "CE", 25));

        assertEquals("NSE_FO|CALL2", trade.instrumentKey());
        assertEquals("NIFTY 20100 CE", trade.symbol());
        assertEquals(new BigDecimal("150"), trade.entryPrice());
    }

    @Test
    void rejectsInvalidQuantitiesBeforeRequestingPrices() {
        assertThrows(IllegalArgumentException.class, () ->
                service.buy(new ManualTradeDtos.TradeRequest("STOCK", "ABC", null, null, null, 0)));
    }

    private static Quote quote(String price) {
        BigDecimal value = new BigDecimal(price);
        return new Quote("ABC", "NSE_EQ|ABC", value, value, value, value, value,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, Instant.now());
    }

    private static ManualTradeEntity entityFrom(ManualTradeDtos.Trade trade) {
        ManualTradeEntity entity = new ManualTradeEntity();
        entity.id = trade.id();
        entity.instrumentType = trade.instrumentType();
        entity.instrumentKey = trade.instrumentKey();
        entity.symbol = trade.symbol();
        entity.exchange = trade.exchange();
        entity.quantity = trade.quantity();
        entity.entryPrice = trade.entryPrice();
        entity.currentPrice = trade.currentPrice();
        entity.entryCharges = trade.entryCharges();
        entity.exitCharges = trade.exitCharges();
        entity.unrealizedPnl = trade.unrealizedPnl();
        entity.realizedPnl = trade.realizedPnl();
        entity.status = trade.status();
        entity.openedAt = trade.openedAt();
        entity.priceUpdatedAt = trade.priceUpdatedAt();
        entity.expiry = trade.expiry();
        entity.optionType = trade.optionType();
        entity.strike = trade.strike();
        return entity;
    }
}
