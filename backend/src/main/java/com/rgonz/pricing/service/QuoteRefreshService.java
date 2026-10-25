package com.rgonz.pricing.service;

import com.rgonz.pricing.client.ProviderClient;

public interface QuoteRefreshService {

    void refreshInBackground(String sku, ProviderClient provider);
}
