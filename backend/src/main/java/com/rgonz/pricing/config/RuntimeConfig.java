package com.rgonz.pricing.config;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({CacheProperties.class, PriceProperties.class})
public class RuntimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Ticker ticker() {
        return Ticker.systemTicker();
    }

    @Bean(destroyMethod = "close")
    public ExecutorService quoteRefreshExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
