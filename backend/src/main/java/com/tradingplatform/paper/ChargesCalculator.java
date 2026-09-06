package com.tradingplatform.paper;
import java.math.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component;
@Component public class ChargesCalculator { private final BigDecimal brokerageRate; public ChargesCalculator(@Value("${trading.paper.charges.brokerage-rate:0.0003}") BigDecimal rate){brokerageRate=rate;} public BigDecimal charge(BigDecimal turnover){return turnover.multiply(brokerageRate).setScale(2,RoundingMode.HALF_UP);} }
