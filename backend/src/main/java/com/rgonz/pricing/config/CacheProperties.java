package com.rgonz.pricing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pricing.cache")
public record CacheProperties(Duration freshTtl, Duration retention) {}
