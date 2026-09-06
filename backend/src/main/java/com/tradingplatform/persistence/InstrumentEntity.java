package com.tradingplatform.persistence;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="instruments") public class InstrumentEntity {
 @Id @Column(name="instrument_key") public String instrumentKey; public String exchange; public String segment; public String symbol;
 @Column(name="company_name") public String companyName; public String isin; @Column(name="instrument_type") public String instrumentType; @Column(name="trading_symbol") public String tradingSymbol; public boolean active; @Column(name="last_updated") public Instant lastUpdated;
}
