package com.rgonz.pricing.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.rgonz.pricing.model.PreferredProvider;
import com.rgonz.pricing.model.Provider;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderSelectionStrategyTest {

    @Test
    void autoQueriesAFirstAndRefreshesStaleQuotesFromB() {
        var strategy = new AutoSelectionStrategy();

        assertThat(strategy.appliesTo()).isEqualTo(PreferredProvider.AUTO);
        assertThat(strategy.consultationOrder()).containsExactly(Provider.PROVIDER_A, Provider.PROVIDER_B);
        assertThat(strategy.staleQuoteRefreshSource()).contains(Provider.PROVIDER_B);
    }

    @Test
    void providerAFirstFallsBackToBWithoutBackgroundRefresh() {
        var strategy = new ProviderAFirstStrategy();

        assertThat(strategy.appliesTo()).isEqualTo(PreferredProvider.PROVIDER_A);
        assertThat(strategy.consultationOrder()).containsExactly(Provider.PROVIDER_A, Provider.PROVIDER_B);
        assertThat(strategy.staleQuoteRefreshSource()).isEmpty();
    }

    @Test
    void providerBFirstFallsBackToA() {
        var strategy = new ProviderBFirstStrategy();

        assertThat(strategy.appliesTo()).isEqualTo(PreferredProvider.PROVIDER_B);
        assertThat(strategy.consultationOrder()).containsExactly(Provider.PROVIDER_B, Provider.PROVIDER_A);
        assertThat(strategy.staleQuoteRefreshSource()).isEmpty();
    }

    @Test
    void everyPreferredProviderHasExactlyOneStrategy() {
        var strategies =
                List.of(new AutoSelectionStrategy(), new ProviderAFirstStrategy(), new ProviderBFirstStrategy());

        assertThat(strategies)
                .extracting(ProviderSelectionStrategy::appliesTo)
                .containsExactlyInAnyOrder(PreferredProvider.values());
    }
}
