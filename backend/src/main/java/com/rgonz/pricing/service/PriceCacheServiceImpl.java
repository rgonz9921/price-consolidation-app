package com.rgonz.pricing.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rgonz.pricing.model.ProviderQuote;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PriceCacheServiceImpl implements PriceCacheService {

    private final Cache<String, ProviderQuote> quotes;

    public PriceCacheServiceImpl(@Value("${pricing.cache.retention}") Duration retention) {
        this.quotes = Caffeine.newBuilder().expireAfterWrite(retention).build();
    }

    @Override
    public void evict(String sku) {
        quotes.invalidate(sku);
    }
}
