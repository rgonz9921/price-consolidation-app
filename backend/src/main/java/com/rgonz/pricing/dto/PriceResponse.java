package com.rgonz.pricing.dto;

import com.rgonz.pricing.model.PriceSource;
import com.rgonz.pricing.model.Provider;
import java.math.BigDecimal;

public record PriceResponse(
        String sku,
        String productName,
        BigDecimal providerPrice,
        BigDecimal marginPercentage,
        BigDecimal finalPrice,
        String currency,
        PriceSource source,
        Provider providerSource,
        boolean stale,
        long responseTimeMs) {}
