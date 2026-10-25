package com.rgonz.pricing.client.dto;

import java.math.BigDecimal;

public record ProviderBCost(String itemCode, BigDecimal cost, String curr, Long timestamp) {}
