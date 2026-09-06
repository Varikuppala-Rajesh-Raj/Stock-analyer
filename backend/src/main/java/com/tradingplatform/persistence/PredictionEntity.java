package com.tradingplatform.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="predictions") public class PredictionEntity {
 @Id public UUID id; public String symbol; public Instant timestamp; @Column(name="nifty_price") public BigDecimal niftyPrice;
 @Column(name="horizon_minutes") public int horizonMinutes; @Column(name="up_probability") public BigDecimal upProbability; @Column(name="down_probability") public BigDecimal downProbability; @Column(name="sideways_probability") public BigDecimal sidewaysProbability;
 public String prediction,confidence; @Column(name="expected_move_points") public BigDecimal expectedMovePoints; public String decision,reason; @Column(name="model_version") public String modelVersion; @Column(name="feature_version") public String featureVersion; @Column(name="created_at") public Instant createdAt;
}
