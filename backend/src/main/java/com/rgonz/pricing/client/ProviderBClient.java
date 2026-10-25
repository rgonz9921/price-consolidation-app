package com.rgonz.pricing.client;

import com.rgonz.pricing.client.dto.ProviderBCost;
import com.rgonz.pricing.model.Provider;
import com.rgonz.pricing.model.ProviderQuote;
import java.time.Instant;
import org.springframework.web.client.RestClient;

public class ProviderBClient extends RestProviderClient {

    public ProviderBClient(RestClient interactiveClient, RestClient backgroundClient) {
        super(interactiveClient, backgroundClient);
    }

    @Override
    public Provider provider() {
        return Provider.PROVIDER_B;
    }

    @Override
    public ProviderQuote quote(String sku, QuoteMode mode) {
        var cost = fetch(mode, ProviderBCost.class, "/items/{itemCode}/cost", sku);
        if (!sku.equals(cost.itemCode()) || cost.cost() == null || cost.curr() == null || cost.timestamp() == null) {
            throw incompleteBody();
        }
        return new ProviderQuote(sku, cost.cost(), cost.curr(), Instant.ofEpochSecond(cost.timestamp()), provider());
    }
}
