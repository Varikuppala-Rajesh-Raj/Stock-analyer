package com.tradingplatform.market;

import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component;

@Component
public class InstrumentCatalog {
  private final Map<String, Instrument> instruments = new HashMap<>();
  public InstrumentCatalog(@Value("${trading.instrument-catalog:classpath:instruments.csv}") String location) {
    try (InputStream stream = "classpath:instruments.csv".equals(location) ? getClass().getClassLoader().getResourceAsStream("instruments.csv") : new FileInputStream(location)) {
      if (stream == null) return;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        reader.readLine(); String line;
        while ((line = reader.readLine()) != null) { String[] p = line.split(",", -1); if (p.length >= 7) { Instrument i = new Instrument(p[0], p[1], p[2], p[3], p[4], p[5], Boolean.parseBoolean(p[6])); if (i.active()) instruments.putIfAbsent(i.symbol().toUpperCase(Locale.ROOT), i); } }
      }
    } catch (IOException ignored) { /* unavailable catalog means symbols cannot be resolved */ }
  }
  public Instrument resolve(String symbol) { Instrument i = instruments.get(symbol.toUpperCase(Locale.ROOT)); if (i == null) throw new IllegalArgumentException("Unknown instrument: " + symbol + ". Load the official Upstox instrument master first."); return i; }
  public List<Instrument> active() { return instruments.values().stream().filter(Instrument::active).toList(); }
}
