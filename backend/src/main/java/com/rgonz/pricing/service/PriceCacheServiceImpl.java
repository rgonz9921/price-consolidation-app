package com.rgonz.pricing.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.rgonz.pricing.config.CacheProperties;
import com.rgonz.pricing.model.CachedQuote;
import com.rgonz.pricing.model.ProviderQuote;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class PriceCacheServiceImpl implements PriceCacheService {

    private final Cache<String, CachedQuote> quotes;
    private final ConcurrentMap<String, CompletableFuture<CachedQuote>> loadsInFlight = new ConcurrentHashMap<>();
    private final Duration freshTtl;
    private final Clock clock;

    public PriceCacheServiceImpl(CacheProperties properties, Ticker ticker, Clock clock) {
        this.quotes = Caffeine.newBuilder()
                .expireAfterWrite(properties.retention())
                .ticker(ticker)
                .build();
        this.freshTtl = properties.freshTtl();
        this.clock = clock;
    }

    @Override
    public Optional<CachedQuote> find(String sku) {
        return Optional.ofNullable(quotes.getIfPresent(sku));
    }

    @Override
    public CachedQuote loadOnce(String sku, Supplier<ProviderQuote> loader) {
        var ownLoad = new CompletableFuture<CachedQuote>();
        var loadInFlight = loadsInFlight.putIfAbsent(sku, ownLoad);
        if (loadInFlight != null) {
            return awaitLoad(loadInFlight);
        }
        try {
            var loadedMeanwhile = find(sku).filter(cached -> cached.isFresh(clock.instant(), freshTtl));
            if (loadedMeanwhile.isPresent()) {
                ownLoad.complete(loadedMeanwhile.get());
                return loadedMeanwhile.get();
            }
            var cached = new CachedQuote(loader.get(), clock.instant());
            quotes.put(sku, cached);
            ownLoad.complete(cached);
            return cached;
        } catch (RuntimeException e) {
            ownLoad.completeExceptionally(e);
            throw e;
        } finally {
            loadsInFlight.remove(sku, ownLoad);
        }
    }

    @Override
    public void keepMostRecent(ProviderQuote quote) {
        quotes.asMap().compute(quote.sku(), (sku, current) -> {
            if (current == null || supersedes(quote, current.quote())) {
                return new CachedQuote(quote, clock.instant());
            }
            return current;
        });
    }

    @Override
    public void evict(String sku) {
        quotes.invalidate(sku);
    }

    private static boolean supersedes(ProviderQuote candidate, ProviderQuote current) {
        return candidate.currency().equals(current.currency())
                && candidate.quotedAt().isAfter(current.quotedAt());
    }

    private static CachedQuote awaitLoad(CompletableFuture<CachedQuote> load) {
        try {
            return load.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }
}
