package com.rgonz.pricing.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rgonz.pricing.MutableClock;
import com.rgonz.pricing.config.ResilienceProperties;
import com.rgonz.pricing.exception.ProviderRateLimitedException;
import com.rgonz.pricing.exception.ProviderRejectedException;
import com.rgonz.pricing.exception.ProviderTimeoutException;
import com.rgonz.pricing.exception.ProviderUnavailableException;
import com.rgonz.pricing.model.Provider;
import com.rgonz.pricing.model.ProviderQuote;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

class ResilientProviderClientTest {

    private static final String SKU = "ABC-123";
    private static final Instant START = Instant.parse("2026-10-25T12:00:00Z");
    private static final Duration DEFAULT_PAUSE = Duration.ofSeconds(1);
    private static final ProviderQuote QUOTE =
            new ProviderQuote(SKU, BigDecimal.TEN, "USD", START, Provider.PROVIDER_B);

    private final MutableClock clock = new MutableClock(START);

    @Test
    void opensCircuitWhenHalfOfTheWindowFails() {
        var provider = new ScriptedProvider(call -> {
            throw new ProviderUnavailableException(Provider.PROVIDER_B, "status 503");
        });
        var circuitBreaker = circuitBreaker();
        var client = resilient(provider, circuitBreaker, noRetry(), roomyBulkhead(), Optional.empty());

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                    .isInstanceOf(ProviderUnavailableException.class);
        }

        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessageContaining("circuit open");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(provider.calls()).isEqualTo(4);
    }

    @Test
    void ignoresInteractiveTimeoutsButCountsBackgroundTimeouts() {
        var provider = new ScriptedProvider(call -> {
            throw new ProviderTimeoutException(
                    Provider.PROVIDER_B, call <= 20 ? QuoteMode.INTERACTIVE : QuoteMode.BACKGROUND, null);
        });
        var circuitBreaker = CircuitBreaker.of(
                "ignores-interactive-timeouts",
                CircuitBreakerConfig.from(circuitBreaker().getCircuitBreakerConfig())
                        .ignoreException(new InteractiveTimeoutPredicate())
                        .build());
        var client = resilient(provider, circuitBreaker, noRetry(), roomyBulkhead(), Optional.empty());

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                    .isInstanceOf(ProviderTimeoutException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.quote(SKU, QuoteMode.BACKGROUND))
                    .isInstanceOf(ProviderTimeoutException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void keepsCircuitClosedWhenProviderOnlyRateLimits() {
        var provider = new ScriptedProvider(call -> {
            throw new ProviderRateLimitedException(Provider.PROVIDER_B, Optional.empty());
        });
        var circuitBreaker = circuitBreaker();
        var client = resilient(provider, circuitBreaker, noRetry(), roomyBulkhead(), Optional.empty());

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                    .isInstanceOf(ProviderRateLimitedException.class);
            clock.advance(DEFAULT_PAUSE);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(provider.calls()).isEqualTo(6);
    }

    @Test
    void pausesProviderUntilRetryAfterElapses() {
        var provider = new ScriptedProvider(call -> {
            if (call == 1) {
                throw new ProviderRateLimitedException(Provider.PROVIDER_B, Optional.of(Duration.ofSeconds(2)));
            }
            return QUOTE;
        });
        var client = resilient(provider, circuitBreaker(), noRetry(), roomyBulkhead(), Optional.empty());

        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderRateLimitedException.class);
        clock.advance(Duration.ofMillis(1999));
        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessageContaining("paused");

        clock.advance(Duration.ofMillis(1));
        assertThat(client.quote(SKU, QuoteMode.INTERACTIVE)).isEqualTo(QUOTE);
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    void pausesForDefaultDurationWhenRetryAfterIsMissing() {
        var provider = new ScriptedProvider(call -> {
            if (call == 1) {
                throw new ProviderRateLimitedException(Provider.PROVIDER_B, Optional.empty());
            }
            return QUOTE;
        });
        var client = resilient(provider, circuitBreaker(), noRetry(), roomyBulkhead(), Optional.empty());

        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderRateLimitedException.class);
        clock.advance(DEFAULT_PAUSE.minusMillis(1));
        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderRejectedException.class);

        clock.advance(Duration.ofMillis(1));
        assertThat(client.quote(SKU, QuoteMode.INTERACTIVE)).isEqualTo(QUOTE);
    }

    @Test
    void rejectsInteractiveCallWhenNoPermitIsLeft() {
        var provider = new ScriptedProvider(call -> QUOTE);
        var oneCallPerTenSeconds = rateLimiter(Duration.ofSeconds(10));
        var client =
                resilient(provider, circuitBreaker(), noRetry(), roomyBulkhead(), Optional.of(oneCallPerTenSeconds));

        assertThat(client.quote(SKU, QuoteMode.INTERACTIVE)).isEqualTo(QUOTE);
        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessageContaining("no rate limit permit");
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void waitsForNextPermitInBackgroundMode() {
        var provider = new ScriptedProvider(call -> QUOTE);
        var oneCallPer50Millis = rateLimiter(Duration.ofMillis(50));
        var client = resilient(provider, circuitBreaker(), noRetry(), roomyBulkhead(), Optional.of(oneCallPer50Millis));

        assertThat(client.quote(SKU, QuoteMode.INTERACTIVE)).isEqualTo(QUOTE);
        assertThat(client.quote(SKU, QuoteMode.BACKGROUND)).isEqualTo(QUOTE);
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    void retriesFastFailureOnce() {
        var provider = new ScriptedProvider(call -> {
            if (call == 1) {
                throw new ProviderUnavailableException(Provider.PROVIDER_A, "connection failed");
            }
            return QUOTE;
        });
        var client = resilient(provider, circuitBreaker(), retryOnce(), roomyBulkhead(), Optional.empty());

        assertThat(client.quote(SKU, QuoteMode.INTERACTIVE)).isEqualTo(QUOTE);
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    void doesNotRetryTimeouts() {
        var provider = new ScriptedProvider(call -> {
            throw new ProviderTimeoutException(Provider.PROVIDER_A, QuoteMode.INTERACTIVE, null);
        });
        var client = resilient(provider, circuitBreaker(), retryOnce(), roomyBulkhead(), Optional.empty());

        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE)).isInstanceOf(ProviderTimeoutException.class);
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void rejectsCallWhenBulkheadIsFull() throws Exception {
        var inFlight = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var provider = new ScriptedProvider(call -> {
            inFlight.countDown();
            await(release);
            return QUOTE;
        });
        var singleSlot = Bulkhead.of(
                "single-slot",
                BulkheadConfig.custom()
                        .maxConcurrentCalls(1)
                        .maxWaitDuration(Duration.ZERO)
                        .build());
        var client = resilient(provider, circuitBreaker(), noRetry(), singleSlot, Optional.empty());

        var firstCall = CompletableFuture.supplyAsync(() -> client.quote(SKU, QuoteMode.INTERACTIVE));
        assertThat(inFlight.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> client.quote(SKU, QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessageContaining("bulkhead full");
        release.countDown();
        assertThat(firstCall.get(1, TimeUnit.SECONDS)).isEqualTo(QUOTE);
        assertThat(provider.calls()).isEqualTo(1);
    }

    private ResilientProviderClient resilient(
            ProviderClient provider,
            CircuitBreaker circuitBreaker,
            Retry retry,
            Bulkhead bulkhead,
            Optional<RateLimiter> rateLimiter) {
        var properties = new ResilienceProperties(DEFAULT_PAUSE, Duration.ofMillis(200));
        return new ResilientProviderClient(provider, circuitBreaker, retry, bulkhead, rateLimiter, properties, clock);
    }

    private static CircuitBreaker circuitBreaker() {
        return CircuitBreaker.of(
                "test",
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .recordExceptions(ProviderTimeoutException.class, ProviderUnavailableException.class)
                        .ignoreExceptions(ProviderRateLimitedException.class, ProviderRejectedException.class)
                        .build());
    }

    private static Retry noRetry() {
        return Retry.of("no-retry", RetryConfig.custom().maxAttempts(1).build());
    }

    private static Retry retryOnce() {
        return Retry.of(
                "retry-once",
                RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(Duration.ofMillis(1))
                        .retryExceptions(ProviderUnavailableException.class)
                        .build());
    }

    private static Bulkhead roomyBulkhead() {
        return Bulkhead.of(
                "roomy", BulkheadConfig.custom().maxConcurrentCalls(10).build());
    }

    private static RateLimiter rateLimiter(Duration refreshPeriod) {
        return RateLimiter.of(
                "one-permit",
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(refreshPeriod)
                        .timeoutDuration(Duration.ZERO)
                        .build());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ScriptedProvider implements ProviderClient {

        private final AtomicInteger calls = new AtomicInteger();
        private final IntFunction<ProviderQuote> behaviour;

        ScriptedProvider(IntFunction<ProviderQuote> behaviour) {
            this.behaviour = behaviour;
        }

        int calls() {
            return calls.get();
        }

        @Override
        public Provider provider() {
            return Provider.PROVIDER_B;
        }

        @Override
        public ProviderQuote quote(String sku, QuoteMode mode) {
            return behaviour.apply(calls.incrementAndGet());
        }
    }
}
