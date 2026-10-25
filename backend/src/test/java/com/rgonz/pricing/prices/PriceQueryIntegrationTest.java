package com.rgonz.pricing.prices;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rgonz.pricing.support.PricingIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "resilience4j.ratelimiter.instances.providerB.limit-for-period=1000")
class PriceQueryIntegrationTest extends PricingIntegrationTest {

    private static final Duration FRESH = Duration.ofMinutes(1);
    private static final Duration OLDER_THAN_15_MINUTES = Duration.ofMinutes(20);
    private static final Duration FRESH_TTL = Duration.ofSeconds(30);
    private static final int REQUESTS_TO_FILL_THE_WINDOW = 5;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @Test
    void servesRepeatedQueryFromCacheAndRecalculatesAfterMarginUpdate() throws Exception {
        providerAQuotes("ABC-123", "100.00", FRESH);

        price("ABC-123")
                .andExpect(jsonPath("$.source").value("PROVIDER_A"))
                .andExpect(jsonPath("$.finalPrice").value(115.00));
        price("ABC-123")
                .andExpect(jsonPath("$.source").value("CACHE"))
                .andExpect(jsonPath("$.providerSource").value("PROVIDER_A"))
                .andExpect(jsonPath("$.finalPrice").value(115.00));
        providerA.verify(1, getRequestedFor(urlEqualTo("/prices/ABC-123")));
        providerB.verify(0, anyRequestedFor(anyUrl()));

        mockMvc.perform(put("/api/v1/product-rules/ABC-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Auriculares BT", "marginPercentage": 20, "preferredProvider": "AUTO", "active": true}
                                """))
                .andExpect(status().isOk());

        price("ABC-123")
                .andExpect(jsonPath("$.source").value("PROVIDER_A"))
                .andExpect(jsonPath("$.marginPercentage").value(20))
                .andExpect(jsonPath("$.finalPrice").value(120.00));
        providerA.verify(2, getRequestedFor(urlEqualTo("/prices/ABC-123")));
    }

    @Test
    void failsOverToProviderBWhenProviderAIsDown() throws Exception {
        providerAIsDown();
        providerBQuotes("XYZ-789", "102.00", FRESH);

        price("XYZ-789")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PROVIDER_B"))
                .andExpect(jsonPath("$.providerPrice").value(102.00))
                .andExpect(jsonPath("$.finalPrice").value(112.20))
                .andExpect(jsonPath("$.stale").value(false));
        providerB.verify(1, getRequestedFor(urlEqualTo("/items/XYZ-789/cost")));
    }

    @Test
    void answersWithControlledErrorWhenBothProvidersAreDownAndNothingIsCached() throws Exception {
        providerAIsDown();
        providerBIsDown();

        mockMvc.perform(get("/api/v1/prices").param("sku", "ABC-123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("PROVIDERS_UNAVAILABLE"))
                .andExpect(jsonPath("$.instance").value("/api/v1/prices"));
    }

    @Test
    void servesFallbackCacheWhenBothProvidersAreDown() throws Exception {
        providerAQuotes("ABC-123", "100.00", FRESH);
        price("ABC-123").andExpect(jsonPath("$.source").value("PROVIDER_A"));
        clock.advance(FRESH_TTL);
        providerAIsDown();
        providerBIsDown();

        price("ABC-123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK_CACHE"))
                .andExpect(jsonPath("$.providerSource").value("PROVIDER_A"))
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.finalPrice").value(115.00));
    }

    @Test
    void queriesProviderAgainOnceTheTtlExpires() throws Exception {
        providerAQuotes("ABC-123", "100.00", FRESH);

        price("ABC-123").andExpect(jsonPath("$.source").value("PROVIDER_A"));
        clock.advance(FRESH_TTL.minusSeconds(1));
        price("ABC-123").andExpect(jsonPath("$.source").value("CACHE"));
        providerA.verify(1, getRequestedFor(urlEqualTo("/prices/ABC-123")));

        clock.advance(Duration.ofSeconds(1));
        price("ABC-123").andExpect(jsonPath("$.source").value("PROVIDER_A"));
        providerA.verify(2, getRequestedFor(urlEqualTo("/prices/ABC-123")));
    }

    @Test
    void opensProviderACircuitAndStopsCallingIt() throws Exception {
        providerAIsDown();
        providerBQuotes("ABC-123", "102.00", FRESH);
        var circuit = circuitBreakers.circuitBreaker("providerA");

        for (int request = 0; request < REQUESTS_TO_FILL_THE_WINDOW; request++) {
            price("ABC-123").andExpect(jsonPath("$.source").value("PROVIDER_B"));
            clock.advance(FRESH_TTL);
        }
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        price("ABC-123").andExpect(jsonPath("$.source").value("PROVIDER_B"));

        providerA.verify(10, getRequestedFor(urlEqualTo("/prices/ABC-123")));
    }

    @Test
    void refreshesStaleQuoteFromProviderBInTheBackground() throws Exception {
        providerAQuotes("ABC-123", "100.00", OLDER_THAN_15_MINUTES);
        providerBQuotes("ABC-123", "102.00", Duration.ofSeconds(10));

        price("ABC-123")
                .andExpect(jsonPath("$.source").value("PROVIDER_A"))
                .andExpect(jsonPath("$.stale").value(true));
        price("ABC-123")
                .andExpect(jsonPath("$.source").value("CACHE"))
                .andExpect(jsonPath("$.providerSource").value("PROVIDER_B"))
                .andExpect(jsonPath("$.providerPrice").value(102.00))
                .andExpect(jsonPath("$.stale").value(false));
        providerB.verify(1, getRequestedFor(urlEqualTo("/items/ABC-123/cost")));
    }

    @Test
    void rejectsUnknownSkuWithoutCallingProviders() throws Exception {
        mockMvc.perform(get("/api/v1/prices").param("sku", "NOPE-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_RULE_NOT_FOUND"));
        providerA.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void rejectsInactiveSkuWithoutCallingProviders() throws Exception {
        mockMvc.perform(get("/api/v1/prices").param("sku", "OLD-001"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SKU_INACTIVE"));
        providerA.verify(0, anyRequestedFor(anyUrl()));
        providerB.verify(0, anyRequestedFor(anyUrl()));
    }

    private ResultActions price(String sku) throws Exception {
        return mockMvc.perform(get("/api/v1/prices").param("sku", sku)).andExpect(status().isOk());
    }
}
