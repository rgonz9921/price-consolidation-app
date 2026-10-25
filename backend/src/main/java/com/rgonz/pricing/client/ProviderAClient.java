package com.rgonz.pricing.client;

import com.rgonz.pricing.client.dto.ProviderAPrice;
import com.rgonz.pricing.model.Provider;
import com.rgonz.pricing.model.ProviderQuote;
import org.springframework.web.client.RestClient;

public class ProviderAClient extends RestProviderClient {

    public ProviderAClient(RestClient interactiveClient, RestClient backgroundClient) {
        super(interactiveClient, backgroundClient);
    }

    @Override
    public Provider provider() {
        return Provider.PROVIDER_A;
    }

    @Override
    public ProviderQuote quote(String sku, QuoteMode mode) {
        var price = fetch(mode, ProviderAPrice.class, "/prices/{sku}", sku);
        if (!sku.equals(price.sku())
                || price.basePrice() == null
                || price.currency() == null
                || price.lastUpdated() == null) {
            throw incompleteBody();
        }
        return new ProviderQuote(sku, price.basePrice(), price.currency(), price.lastUpdated(), provider());
    }
}
