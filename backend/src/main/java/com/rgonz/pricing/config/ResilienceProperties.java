package com.rgonz.pricing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pricing.resilience")
public record ResilienceProperties(Duration rateLimitedPause, Duration backgroundPermitWait) {}
