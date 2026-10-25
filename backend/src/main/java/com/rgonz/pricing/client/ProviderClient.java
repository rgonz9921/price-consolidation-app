package com.rgonz.pricing.client;

import com.rgonz.pricing.model.Provider;
import com.rgonz.pricing.model.ProviderQuote;

public interface ProviderClient {

    Provider provider();

    ProviderQuote quote(String sku, QuoteMode mode);
}
