package com.rgonz.pricing.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record ProviderQuote(String sku, BigDecimal price, String currency, Instant quotedAt, Provider provider) {

    public boolean isStale(Instant now, Duration staleAfter) {
        return Duration.between(quotedAt, now).compareTo(staleAfter) > 0;
    }
}
