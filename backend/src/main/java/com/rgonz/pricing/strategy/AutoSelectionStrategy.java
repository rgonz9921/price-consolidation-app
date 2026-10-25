package com.rgonz.pricing.strategy;

import com.rgonz.pricing.model.PreferredProvider;
import com.rgonz.pricing.model.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AutoSelectionStrategy implements ProviderSelectionStrategy {

    @Override
    public PreferredProvider appliesTo() {
        return PreferredProvider.AUTO;
    }

    @Override
    public List<Provider> consultationOrder() {
        return List.of(Provider.PROVIDER_A, Provider.PROVIDER_B);
    }

    @Override
    public Optional<Provider> staleQuoteRefreshSource() {
        return Optional.of(Provider.PROVIDER_B);
    }
}
