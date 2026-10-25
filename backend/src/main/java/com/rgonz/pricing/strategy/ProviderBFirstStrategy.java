package com.rgonz.pricing.strategy;

import com.rgonz.pricing.model.PreferredProvider;
import com.rgonz.pricing.model.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProviderBFirstStrategy implements ProviderSelectionStrategy {

    @Override
    public PreferredProvider appliesTo() {
        return PreferredProvider.PROVIDER_B;
    }

    @Override
    public List<Provider> consultationOrder() {
        return List.of(Provider.PROVIDER_B, Provider.PROVIDER_A);
    }

    @Override
    public Optional<Provider> staleQuoteRefreshSource() {
        return Optional.empty();
    }
}
