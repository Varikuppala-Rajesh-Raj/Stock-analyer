package com.tradingplatform.controller;
import com.tradingplatform.market.MarketDataUnavailableException; import java.util.Map; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler({IllegalArgumentException.class}) ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
 @ExceptionHandler(MarketDataUnavailableException.class) ResponseEntity<Map<String,String>> unavailable(MarketDataUnavailableException e) { return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage())); }
}
