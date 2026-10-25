package com.rgonz.pricing.model;

import java.time.Duration;
import java.time.Instant;

public record CachedQuote(ProviderQuote quote, Instant fetchedAt) {

    public boolean isFresh(Instant now, Duration freshTtl) {
        return Duration.between(fetchedAt, now).compareTo(freshTtl) < 0;
    }
}
