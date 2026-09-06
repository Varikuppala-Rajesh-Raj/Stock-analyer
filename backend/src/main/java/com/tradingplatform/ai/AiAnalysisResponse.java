package com.tradingplatform.ai;
import com.tradingplatform.signal.SignalResult; import java.util.List;
public record AiAnalysisResponse(boolean aiAvailable, String message, String summary, List<String> why, List<String> supportingFactors, List<String> risks, List<String> invalidationConditions, SignalResult quantitativeAnalysis) {
 public static AiAnalysisResponse unavailable(SignalResult analysis) { return new AiAnalysisResponse(false,"AI explanation is temporarily unavailable",null,List.of(),List.of(),List.of(),List.of(),analysis); }
}
