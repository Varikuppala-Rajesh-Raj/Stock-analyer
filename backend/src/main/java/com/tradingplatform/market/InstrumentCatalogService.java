package com.tradingplatform.market;
import com.tradingplatform.persistence.*; import org.springframework.stereotype.Service;
@Service public class InstrumentCatalogService {
 private final InstrumentCatalogRepository instruments; public InstrumentCatalogService(InstrumentCatalogRepository instruments){this.instruments=instruments;}
 public Instrument resolveSymbol(String symbol){var rows=instruments.findBySymbolIgnoreCaseAndActiveTrueOrderByExchangeAscSegmentAsc(symbol.trim()); if(rows.isEmpty())throw new IllegalArgumentException("Unknown instrument: "+symbol+". Import the official Upstox instrument master."); var i=rows.get(0);return new Instrument(i.instrumentKey,i.symbol,i.companyName,i.exchange,i.segment,i.isin,i.active);}
 public String resolveInstrumentKey(String symbol){return resolveSymbol(symbol).instrumentKey();} public java.util.List<Instrument> active(){return instruments.findByActiveTrueOrderBySymbolAsc().stream().map(i->new Instrument(i.instrumentKey,i.symbol,i.companyName,i.exchange,i.segment,i.isin,i.active)).toList();}
}
