package com.rgonz.pricing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProviderQuoteTest {

    private static final Duration STALE_AFTER = Duration.ofMinutes(15);
    private static final Instant QUOTED_AT = Instant.parse("2026-10-22T10:00:00Z");
    private static final ProviderQuote QUOTE =
            new ProviderQuote("ABC-123", BigDecimal.TEN, "USD", QUOTED_AT, Provider.PROVIDER_A);

    @Test
    void isFreshAtExactlyTheThreshold() {
        assertThat(QUOTE.isStale(QUOTED_AT.plus(STALE_AFTER), STALE_AFTER)).isFalse();
    }

    @Test
    void isStaleOneSecondPastTheThreshold() {
        assertThat(QUOTE.isStale(QUOTED_AT.plus(STALE_AFTER).plusSeconds(1), STALE_AFTER))
                .isTrue();
    }
}
