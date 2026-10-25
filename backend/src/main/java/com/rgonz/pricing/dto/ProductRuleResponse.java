package com.rgonz.pricing.dto;

import com.rgonz.pricing.model.PreferredProvider;
import com.rgonz.pricing.model.ProductRule;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductRuleResponse(
        Long id,
        String sku,
        String name,
        BigDecimal marginPercentage,
        PreferredProvider preferredProvider,
        boolean active,
        Instant updatedAt) {

    public static ProductRuleResponse from(ProductRule rule) {
        return new ProductRuleResponse(
                rule.getId(),
                rule.getSku(),
                rule.getName(),
                rule.getMarginPercentage(),
                rule.getPreferredProvider(),
                rule.isActive(),
                rule.getUpdatedAt());
    }
}
