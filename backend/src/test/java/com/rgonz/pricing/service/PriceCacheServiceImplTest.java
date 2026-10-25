package com.rgonz.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rgonz.pricing.MutableClock;
import com.rgonz.pricing.config.CacheProperties;
import com.rgonz.pricing.exception.ProvidersUnavailableException;
import com.rgonz.pricing.model.CachedQuote;
import com.rgonz.pricing.model.Provider;
import com.rgonz.pricing.model.ProviderQuote;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PriceCacheServiceImplTest {

    private static final String SKU = "ABC-123";
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final int CONCURRENT_REQUESTS = 50;

    private final MutableClock clock = new MutableClock(NOW);
    private final PriceCacheServiceImpl cache = new PriceCacheServiceImpl(
            new CacheProperties(Duration.ofSeconds(30), Duration.ofMinutes(30)),
            () -> TimeUnit.MILLISECONDS.toNanos(clock.millis()),
            clock);

    @Test
    void concurrentMissesShareASingleLoad() throws Exception {
        var loads = new AtomicInteger();
        var loadStarted = new CountDownLatch(1);
        var releaseLoad = new CountDownLatch(1);
        var results = new ArrayList<Future<CachedQuote>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                results.add(executor.submit(() -> cache.loadOnce(SKU, () -> {
                    loads.incrementAndGet();
                    loadStarted.countDown();
                    awaitQuietly(releaseLoad);
                    return quote(Provider.PROVIDER_A, NOW);
                })));
            }
            assertThat(loadStarted.await(1, TimeUnit.SECONDS)).isTrue();
            releaseLoad.countDown();

            for (var result : results) {
                assertThat(result.get(1, TimeUnit.SECONDS).quote().provider()).isEqualTo(Provider.PROVIDER_A);
            }
        }
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void missArrivingRightAfterALoadReusesItsResult() {
        var loads = new AtomicInteger();
        cache.loadOnce(SKU, () -> {
            loads.incrementAndGet();
            return quote(Provider.PROVIDER_A, NOW);
        });

        var reused = cache.loadOnce(SKU, () -> {
            loads.incrementAndGet();
            return quote(Provider.PROVIDER_B, NOW);
        });

        assertThat(loads.get()).isEqualTo(1);
        assertThat(reused.quote().provider()).isEqualTo(Provider.PROVIDER_A);
    }

    @Test
    void reloadsOnceTheCachedQuoteIsNoLongerFresh() {
        var loads = new AtomicInteger();
        cache.loadOnce(SKU, () -> {
            loads.incrementAndGet();
            return quote(Provider.PROVIDER_A, NOW);
        });
        clock.advance(Duration.ofSeconds(30));

        cache.loadOnce(SKU, () -> {
            loads.incrementAndGet();
            return quote(Provider.PROVIDER_B, NOW);
        });

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void failedLoadDoesNotLeaveAnEntryBehind() {
        assertThatThrownBy(() -> cache.loadOnce(SKU, () -> {
                    throw new ProvidersUnavailableException(SKU);
                }))
                .isInstanceOf(ProvidersUnavailableException.class);

        assertThat(cache.find(SKU)).isEmpty();
        assertThat(cache.loadOnce(SKU, () -> quote(Provider.PROVIDER_A, NOW)).quote())
                .isNotNull();
    }

    @Test
    void keepsTheMostRecentQuote() {
        cache.loadOnce(SKU, () -> quote(Provider.PROVIDER_A, NOW.minus(Duration.ofMinutes(20))));

        cache.keepMostRecent(quote(Provider.PROVIDER_B, NOW.minusSeconds(30)));

        assertThat(cache.find(SKU).orElseThrow().quote().provider()).isEqualTo(Provider.PROVIDER_B);
    }

    @Test
    void ignoresAnOlderQuote() {
        cache.loadOnce(SKU, () -> quote(Provider.PROVIDER_A, NOW));

        cache.keepMostRecent(quote(Provider.PROVIDER_B, NOW.minusSeconds(60)));

        assertThat(cache.find(SKU).orElseThrow().quote().provider()).isEqualTo(Provider.PROVIDER_A);
    }

    @Test
    void ignoresAQuoteInAnotherCurrency() {
        cache.loadOnce(SKU, () -> quote(Provider.PROVIDER_A, NOW.minus(Duration.ofMinutes(20))));

        cache.keepMostRecent(new ProviderQuote(SKU, BigDecimal.ONE, "EUR", NOW, Provider.PROVIDER_B));

        assertThat(cache.find(SKU).orElseThrow().quote().currency()).isEqualTo("USD");
    }

    @Test
    void dropsEntryAfterRetention() {
        cache.loadOnce(SKU, () -> quote(Provider.PROVIDER_A, NOW));

        clock.advance(Duration.ofMinutes(30));

        assertThat(cache.find(SKU)).isEmpty();
    }

    @Test
    void evictRemovesEntry() {
        cache.loadOnce(SKU, () -> quote(Provider.PROVIDER_A, NOW));

        cache.evict(SKU);

        assertThat(cache.find(SKU)).isEmpty();
    }

    private static ProviderQuote quote(Provider provider, Instant quotedAt) {
        return new ProviderQuote(SKU, new BigDecimal("100.00"), "USD", quotedAt, provider);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
