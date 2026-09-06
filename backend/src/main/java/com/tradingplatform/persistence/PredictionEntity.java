package com.tradingplatform.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="predictions") public class PredictionEntity {
 @Id public UUID id; public String symbol; public Instant timestamp; @Column(name="nifty_price") public BigDecimal niftyPrice;
 @Column(name="horizon_minutes") public int horizonMinutes; @Column(name="up_probability") public BigDecimal upProbability; @Column(name="down_probability") public BigDecimal downProbability; @Column(name="sideways_probability") public BigDecimal sidewaysProbability;
 public String prediction,confidence; @Column(name="expected_move_points") public BigDecimal expectedMovePoints;
 @Column(name="predicted_return_percent") public BigDecimal predictedReturnPercent;
 @Column(name="predicted_range_low") public BigDecimal predictedRangeLow;
 @Column(name="predicted_range_high") public BigDecimal predictedRangeHigh;
 public String decision,reason; @Column(name="model_version") public String modelVersion;
 @Column(name="feature_version") public String featureVersion;
 @Column(name="feature_schema_version") public String featureSchemaVersion;
 @Column(name="context_timestamp") public Instant contextTimestamp;
 @Column(name="input_hash") public String inputHash;
 @Column(name="actual_price") public BigDecimal actualPrice;
 @Column(name="actual_return_percent") public BigDecimal actualReturnPercent;
 @Column(name="actual_direction") public String actualDirection;
 @Column(name="outcome_correct") public Boolean outcomeCorrect;
 @Column(name="evaluated_at") public Instant evaluatedAt;
 @Column(name="created_at") public Instant createdAt;
}
