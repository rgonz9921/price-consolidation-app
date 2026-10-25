package com.rgonz.pricing.strategy;

import com.rgonz.pricing.model.PreferredProvider;
import com.rgonz.pricing.model.Provider;
import java.util.List;
import java.util.Optional;

public interface ProviderSelectionStrategy {

    PreferredProvider appliesTo();

    List<Provider> consultationOrder();

    Optional<Provider> staleQuoteRefreshSource();
}
