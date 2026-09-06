package com.tradingplatform.controller;
import com.tradingplatform.persistence.PredictionEntity; import com.tradingplatform.prediction.NiftyPredictionService; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/prediction") public class PredictionController {
 private final NiftyPredictionService predictions; public PredictionController(NiftyPredictionService predictions){this.predictions=predictions;}
 @GetMapping("/nifty") public Map<String,Object> nifty(@RequestParam(defaultValue="15")int horizonMinutes,@RequestParam(defaultValue="0.20")double movementThresholdPercent){return predictions.predict(horizonMinutes,movementThresholdPercent);}
 @GetMapping("/history") public List<PredictionEntity> history(){return predictions.history();}
 @GetMapping("/model/status") public Map<String,Object> status(){return predictions.modelStatus();}
}
