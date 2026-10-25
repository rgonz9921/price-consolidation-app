package com.rgonz.pricing.client;

import com.rgonz.pricing.config.ResilienceProperties;
import com.rgonz.pricing.exception.ProviderRateLimitedException;
import com.rgonz.pricing.exception.ProviderRejectedException;
import com.rgonz.pricing.model.Provider;
import com.rgonz.pricing.model.ProviderQuote;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResilientProviderClient implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientProviderClient.class);

    private final ProviderClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final Optional<RateLimiter> rateLimiter;
    private final ResilienceProperties properties;
    private final Clock clock;
    private volatile Instant pausedUntil = Instant.MIN;

    public ResilientProviderClient(
            ProviderClient delegate,
            CircuitBreaker circuitBreaker,
            Retry retry,
            Bulkhead bulkhead,
            Optional<RateLimiter> rateLimiter,
            ResilienceProperties properties,
            Clock clock) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.bulkhead = bulkhead;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "circuit transition provider={} transition={}", provider(), event.getStateTransition()));
    }

    @Override
    public Provider provider() {
        return delegate.provider();
    }

    @Override
    public ProviderQuote quote(String sku, QuoteMode mode) {
        Supplier<ProviderQuote> guarded = () -> callWithinLimits(sku, mode);
        var decorated = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, guarded));
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            throw new ProviderRejectedException(provider(), "circuit open");
        }
    }

    private ProviderQuote callWithinLimits(String sku, QuoteMode mode) {
        if (clock.instant().isBefore(pausedUntil)) {
            throw new ProviderRejectedException(provider(), "paused after rate limit");
        }
        rateLimiter.ifPresent(limiter -> acquirePermit(limiter, mode));
        if (!bulkhead.tryAcquirePermission()) {
            throw new ProviderRejectedException(provider(), "bulkhead full");
        }
        try {
            return delegate.quote(sku, mode);
        } catch (ProviderRateLimitedException e) {
            pauseFor(e.getRetryAfter().orElse(properties.rateLimitedPause()));
            throw e;
        } finally {
            bulkhead.onComplete();
        }
    }

    private void acquirePermit(RateLimiter limiter, QuoteMode mode) {
        var refreshPeriod = limiter.getRateLimiterConfig().getLimitRefreshPeriod();
        var attempts = mode == QuoteMode.BACKGROUND
                ? 1 + properties.backgroundPermitWait().dividedBy(refreshPeriod)
                : 1;
        for (long attempt = 1; attempt <= attempts; attempt++) {
            if (limiter.acquirePermission()) {
                return;
            }
            if (attempt < attempts) {
                waitFor(refreshPeriod);
            }
        }
        throw new ProviderRejectedException(provider(), "no rate limit permit");
    }

    private void pauseFor(Duration pause) {
        pausedUntil = clock.instant().plus(pause);
        log.warn("provider paused provider={} pause={}", provider(), pause);
    }

    private void waitFor(Duration period) {
        try {
            Thread.sleep(period);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderRejectedException(provider(), "interrupted while waiting for permit");
        }
    }
}
