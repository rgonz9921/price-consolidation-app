package com.rgonz.pricing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "product_rule")
public class ProductRule {

    private static final int PRICE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal marginPercentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreferredProvider preferredProvider;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProductRule() {}

    public ProductRule(
            String sku,
            String name,
            BigDecimal marginPercentage,
            PreferredProvider preferredProvider,
            boolean active,
            Instant updatedAt) {
        this.sku = sku;
        update(name, marginPercentage, preferredProvider, active, updatedAt);
    }

    public void update(
            String name,
            BigDecimal marginPercentage,
            PreferredProvider preferredProvider,
            boolean active,
            Instant updatedAt) {
        this.name = name;
        this.marginPercentage = marginPercentage;
        this.preferredProvider = preferredProvider;
        this.active = active;
        this.updatedAt = updatedAt;
    }

    public BigDecimal applyMargin(BigDecimal providerPrice) {
        var factor = BigDecimal.ONE.add(marginPercentage.movePointLeft(2));
        return providerPrice.multiply(factor).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMarginPercentage() {
        return marginPercentage;
    }

    public PreferredProvider getPreferredProvider() {
        return preferredProvider;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
