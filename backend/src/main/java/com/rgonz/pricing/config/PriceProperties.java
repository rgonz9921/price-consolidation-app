package com.rgonz.pricing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pricing.prices")
public record PriceProperties(Duration staleAfter, Duration unavailableRetryAfter) {}
