package com.rgonz.pricing.service;

import com.rgonz.pricing.model.CachedQuote;
import com.rgonz.pricing.model.ProviderQuote;
import java.util.Optional;
import java.util.function.Supplier;

public interface PriceCacheService {

    Optional<CachedQuote> find(String sku);

    CachedQuote loadOnce(String sku, Supplier<ProviderQuote> loader);

    void keepMostRecent(ProviderQuote quote);

    void evict(String sku);
}
