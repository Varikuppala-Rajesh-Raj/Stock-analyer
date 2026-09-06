package com.tradingplatform.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant;
@Entity @Table(name="market_candles",uniqueConstraints=@UniqueConstraint(name="uq_market_candle",columnNames={"instrument_key","timeframe","timestamp"})) public class CandleEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="instrument_key") public String instrumentKey; public String timeframe; public Instant timestamp; public BigDecimal open,high,low,close,volume; }
