package com.rgonz.pricing.model;

public enum PriceSource {
    PROVIDER_A,
    PROVIDER_B,
    CACHE,
    FALLBACK_CACHE;

    public static PriceSource of(Provider provider) {
        return switch (provider) {
            case PROVIDER_A -> PROVIDER_A;
            case PROVIDER_B -> PROVIDER_B;
        };
    }
}
