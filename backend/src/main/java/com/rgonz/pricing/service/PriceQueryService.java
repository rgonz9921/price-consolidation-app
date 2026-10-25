package com.rgonz.pricing.service;

import com.rgonz.pricing.dto.PriceResponse;

public interface PriceQueryService {

    PriceResponse getPrice(String sku);
}
