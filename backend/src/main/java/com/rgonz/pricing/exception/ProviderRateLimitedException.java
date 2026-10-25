package com.rgonz.pricing.exception;

import com.rgonz.pricing.model.Provider;
import java.time.Duration;
import java.util.Optional;

public class ProviderRateLimitedException extends ProviderException {

    private final Duration retryAfter;

    public ProviderRateLimitedException(Provider provider, Optional<Duration> retryAfter) {
        super(provider, "rate limited", null);
        this.retryAfter = retryAfter.orElse(null);
    }

    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
