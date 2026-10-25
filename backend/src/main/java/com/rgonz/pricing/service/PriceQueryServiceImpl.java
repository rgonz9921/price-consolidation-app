package com.rgonz.pricing.service;

import com.github.benmanes.caffeine.cache.Ticker;
import com.rgonz.pricing.client.ProviderClient;
import com.rgonz.pricing.client.QuoteMode;
import com.rgonz.pricing.config.CacheProperties;
import com.rgonz.pricing.config.PriceProperties;
import com.rgonz.pricing.dto.PriceResponse;
import com.rgonz.pricing.exception.ProductRuleNotFoundException;
import com.rgonz.pricing.exception.ProviderException;
import com.rgonz.pricing.exception.ProvidersUnavailableException;
import com.rgonz.pricing.exception.SkuInactiveException;
import com.rgonz.pricing.model.CachedQuote;
import com.rgonz.pricing.model.PreferredProvider;
import com.rgonz.pricing.model.PriceSource;
import com.rgonz.pricing.model.ProductRule;
import com.rgonz.pricing.model.Provider;
import com.rgonz.pricing.model.ProviderQuote;
import com.rgonz.pricing.repository.ProductRuleRepository;
import com.rgonz.pricing.strategy.ProviderSelectionStrategy;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PriceQueryServiceImpl implements PriceQueryService {

    private static final Logger log = LoggerFactory.getLogger(PriceQueryServiceImpl.class);

    private final ProductRuleRepository rules;
    private final PriceCacheService priceCache;
    private final QuoteRefreshService quoteRefresh;
    private final Map<Provider, ProviderClient> providers = new EnumMap<>(Provider.class);
    private final Map<PreferredProvider, ProviderSelectionStrategy> strategies = new EnumMap<>(PreferredProvider.class);
    private final CacheProperties cacheProperties;
    private final PriceProperties priceProperties;
    private final Clock clock;
    private final Ticker ticker;

    public PriceQueryServiceImpl(
            ProductRuleRepository rules,
            PriceCacheService priceCache,
            QuoteRefreshService quoteRefresh,
            List<ProviderClient> providerClients,
            List<ProviderSelectionStrategy> selectionStrategies,
            CacheProperties cacheProperties,
            PriceProperties priceProperties,
            Clock clock,
            Ticker ticker) {
        this.rules = rules;
        this.priceCache = priceCache;
        this.quoteRefresh = quoteRefresh;
        providerClients.forEach(client -> providers.put(client.provider(), client));
        selectionStrategies.forEach(strategy -> strategies.put(strategy.appliesTo(), strategy));
        this.cacheProperties = cacheProperties;
        this.priceProperties = priceProperties;
        this.clock = clock;
        this.ticker = ticker;
    }

    @Override
    public PriceResponse getPrice(String sku) {
        var startedAt = ticker.read();
        var rule = activeRule(sku);
        var cached = priceCache.find(sku);
        if (cached.isPresent() && cached.get().isFresh(clock.instant(), cacheProperties.freshTtl())) {
            return respond(rule, cached.get().quote(), PriceSource.CACHE, startedAt);
        }
        try {
            var loaded = priceCache.loadOnce(sku, () -> quoteFromProviders(rule));
            refreshIfStale(rule, loaded.quote());
            return respond(rule, loaded.quote(), PriceSource.of(loaded.quote().provider()), startedAt);
        } catch (ProvidersUnavailableException e) {
            var fallback = cached.map(CachedQuote::quote).orElseThrow(() -> e);
            log.warn("serving fallback cache sku={} quotedAt={}", sku, fallback.quotedAt());
            return respond(rule, fallback, PriceSource.FALLBACK_CACHE, startedAt);
        }
    }

    private ProductRule activeRule(String sku) {
        var rule = rules.findBySku(sku).orElseThrow(() -> new ProductRuleNotFoundException(sku));
        if (!rule.isActive()) {
            throw new SkuInactiveException(sku);
        }
        return rule;
    }

    private ProviderQuote quoteFromProviders(ProductRule rule) {
        for (var provider : strategyFor(rule).consultationOrder()) {
            try {
                return providers.get(provider).quote(rule.getSku(), QuoteMode.INTERACTIVE);
            } catch (ProviderException e) {
                log.warn("provider failed sku={} provider={} reason={}", rule.getSku(), provider, e.getMessage());
            }
        }
        throw new ProvidersUnavailableException(rule.getSku());
    }

    private void refreshIfStale(ProductRule rule, ProviderQuote quote) {
        if (!isStale(quote)) {
            return;
        }
        strategyFor(rule)
                .staleQuoteRefreshSource()
                .filter(source -> source != quote.provider())
                .ifPresent(source -> quoteRefresh.refreshInBackground(rule.getSku(), providers.get(source)));
    }

    private PriceResponse respond(ProductRule rule, ProviderQuote quote, PriceSource source, long startedAt) {
        return new PriceResponse(
                rule.getSku(),
                rule.getName(),
                quote.price(),
                rule.getMarginPercentage(),
                rule.applyMargin(quote.price()),
                quote.currency(),
                source,
                quote.provider(),
                source == PriceSource.FALLBACK_CACHE || isStale(quote),
                TimeUnit.NANOSECONDS.toMillis(ticker.read() - startedAt));
    }

    private boolean isStale(ProviderQuote quote) {
        return quote.isStale(clock.instant(), priceProperties.staleAfter());
    }

    private ProviderSelectionStrategy strategyFor(ProductRule rule) {
        return strategies.get(rule.getPreferredProvider());
    }
}
