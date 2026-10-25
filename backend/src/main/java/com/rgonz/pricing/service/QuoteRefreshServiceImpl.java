package com.rgonz.pricing.service;

import com.rgonz.pricing.client.ProviderClient;
import com.rgonz.pricing.client.QuoteMode;
import com.rgonz.pricing.exception.ProviderException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuoteRefreshServiceImpl implements QuoteRefreshService {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefreshServiceImpl.class);

    private final PriceCacheService priceCache;
    private final Executor quoteRefreshExecutor;
    private final Set<String> refreshesInFlight = ConcurrentHashMap.newKeySet();

    public QuoteRefreshServiceImpl(PriceCacheService priceCache, Executor quoteRefreshExecutor) {
        this.priceCache = priceCache;
        this.quoteRefreshExecutor = quoteRefreshExecutor;
    }

    @Override
    public void refreshInBackground(String sku, ProviderClient provider) {
        if (!refreshesInFlight.add(sku)) {
            return;
        }
        quoteRefreshExecutor.execute(() -> {
            try {
                priceCache.keepMostRecent(provider.quote(sku, QuoteMode.BACKGROUND));
            } catch (ProviderException e) {
                log.warn("background refresh skipped sku={} reason={}", sku, e.getMessage());
            } finally {
                refreshesInFlight.remove(sku);
            }
        });
    }
}
