package com.rgonz.pricing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductRuleTest {

    @ParameterizedTest
    @CsvSource({
        "100.00, 15.00, 115.00",
        "385.75, 22.50, 472.54",
        "0.15, 10.00, 0.17",
        "102.00, 0.00, 102.00",
        "99.99, 100.00, 199.98"
    })
    void appliesMarginRoundingHalfUpToCents(String providerPrice, String margin, String expected) {
        var rule = new ProductRule(
                "ABC-123", "Auriculares BT", new BigDecimal(margin), PreferredProvider.AUTO, true, Instant.EPOCH);

        assertThat(rule.applyMargin(new BigDecimal(providerPrice))).isEqualTo(new BigDecimal(expected));
    }
}
