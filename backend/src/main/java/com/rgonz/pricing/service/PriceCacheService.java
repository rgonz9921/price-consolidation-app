package com.rgonz.pricing.service;

public interface PriceCacheService {

    void evict(String sku);
}
