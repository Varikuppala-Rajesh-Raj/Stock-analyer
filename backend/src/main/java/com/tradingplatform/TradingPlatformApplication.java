package com.tradingplatform;

import io.github.cdimascio.dotenv.Dotenv;
import com.tradingplatform.market.context.UpstoxGlobalInstrumentProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(UpstoxGlobalInstrumentProperties.class)
public class TradingPlatformApplication {
    public static void main(String[] args) {
        Path dotenvDirectory = Files.exists(Path.of(".env")) ? Path.of(".") : Path.of("..");
        Dotenv.configure().directory(dotenvDirectory.toString()).ignoreIfMissing().systemProperties().load();
        SpringApplication.run(TradingPlatformApplication.class, args);
    }
}
