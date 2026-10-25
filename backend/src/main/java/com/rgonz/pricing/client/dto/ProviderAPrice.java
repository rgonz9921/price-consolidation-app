package com.rgonz.pricing.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderAPrice(String sku, BigDecimal basePrice, String currency, Instant lastUpdated) {}
