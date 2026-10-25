package com.rgonz.pricing.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderQuote(String sku, BigDecimal price, String currency, Instant quotedAt, Provider provider) {}
