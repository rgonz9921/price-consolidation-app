package com.rgonz.pricing.prices;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.rgonz.pricing.model.PreferredProvider;
import com.rgonz.pricing.model.ProductRule;
import com.rgonz.pricing.repository.ProductRuleRepository;
import com.rgonz.pricing.service.PriceCacheService;
import com.rgonz.pricing.support.PricingIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class ProviderBRateLimitIntegrationTest extends PricingIntegrationTest {

    private static final int QUERIES = 40;
    private static final int CONCURRENT_CLIENTS = 4;
    private static final int PROVIDER_A_DELAY_MS = 150;
    private static final int PROVIDER_B_LIMIT_PER_SECOND = 5;
    private static final long WINDOW_MS = 1_000;

    @Autowired
    private ProductRuleRepository rules;

    @Autowired
    private PriceCacheService priceCache;

    private List<String> skus;

    @BeforeEach
    void createRulesThatPreferProviderB() {
        skus = IntStream.rangeClosed(1, QUERIES)
                .mapToObj(i -> "RL-%03d".formatted(i))
                .toList();
        skus.forEach(sku -> rules.save(new ProductRule(
                sku, "Rate limit " + sku, BigDecimal.TEN, PreferredProvider.PROVIDER_B, true, clock.instant())));
    }

    @AfterEach
    void removeRules() {
        skus.forEach(sku -> {
            rules.findBySku(sku).ifPresent(rules::delete);
            priceCache.evict(sku);
        });
    }

    @Test
    void neverSendsMoreThanFiveRequestsPerSecondToProviderB() throws Exception {
        providerA.stubFor(get(urlPathMatching("/prices/(.*)"))
                .willReturn(okJson("""
                                {"sku": "{{request.pathSegments.[1]}}", "basePrice": 100.00, "currency": "USD", "lastUpdated": "%s"}
                                """.formatted(clock.instant()))
                        .withTransformers("response-template")
                        .withFixedDelay(PROVIDER_A_DELAY_MS)));
        providerB.stubFor(get(urlPathMatching("/items/(.*)/cost"))
                .willReturn(
                        okJson("""
                                {"itemCode": "{{request.pathSegments.[1]}}", "cost": 102.00, "curr": "USD", "timestamp": %d}
                                """.formatted(clock.instant().getEpochSecond())).withTransformers("response-template")));

        warmUpConnectionToProviderA();

        var statuses = new ArrayList<Integer>();
        try (var executor = Executors.newFixedThreadPool(CONCURRENT_CLIENTS)) {
            var responses = new ArrayList<Future<Integer>>();
            for (var sku : skus) {
                responses.add(executor.submit(() -> mockMvc.perform(
                                MockMvcRequestBuilders.get("/api/v1/prices").param("sku", sku))
                        .andReturn()
                        .getResponse()
                        .getStatus()));
            }
            for (var response : responses) {
                statuses.add(response.get());
            }
        }

        var arrivalsAtB = providerB.getAllServeEvents().stream()
                .map(event -> event.getRequest().getLoggedDate().getTime())
                .sorted()
                .toList();
        assertThat(statuses).hasSize(QUERIES).containsOnly(200);
        assertThat(arrivalsAtB).hasSizeGreaterThan(1);
        assertThat(busiestWindow(arrivalsAtB)).isLessThanOrEqualTo(PROVIDER_B_LIMIT_PER_SECOND);
    }

    private void warmUpConnectionToProviderA() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/prices").param("sku", "ABC-123"));
    }

    private static int busiestWindow(List<Long> arrivals) {
        var busiest = 0;
        for (int first = 0; first < arrivals.size(); first++) {
            var windowEnd = arrivals.get(first) + WINDOW_MS;
            var inWindow = 0;
            for (int next = first; next < arrivals.size() && arrivals.get(next) < windowEnd; next++) {
                inWindow++;
            }
            busiest = Math.max(busiest, inWindow);
        }
        return busiest;
    }
}
