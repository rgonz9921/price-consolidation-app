package com.rgonz.pricing.support;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.benmanes.caffeine.cache.Ticker;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rgonz.pricing.MutableClock;
import com.rgonz.pricing.TestcontainersConfig;
import com.rgonz.pricing.service.PriceCacheService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, PricingIntegrationTest.ControlledTimeConfig.class})
public abstract class PricingIntegrationTest {

    protected static final List<String> SEEDED_SKUS = List.of("ABC-123", "XYZ-789", "LMN-456", "OLD-001");

    @RegisterExtension
    protected static final WireMockExtension providerA = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    protected static final WireMockExtension providerB = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledTimeConfig {

        @Bean
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-25T12:00:00Z"));
        }

        @Bean
        @Primary
        Clock controlledClock(MutableClock mutableClock) {
            return mutableClock;
        }

        @Bean
        @Primary
        Ticker controlledTicker(MutableClock mutableClock) {
            return () -> TimeUnit.MILLISECONDS.toNanos(mutableClock.millis());
        }

        @Bean
        @Primary
        Executor synchronousRefreshExecutor() {
            return Runnable::run;
        }
    }

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        registry.add("pricing.providers.a.base-url", providerA::baseUrl);
        registry.add("pricing.providers.b.base-url", providerB::baseUrl);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @Autowired
    private PriceCacheService priceCache;

    @BeforeEach
    void startFromKnownState() {
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        SEEDED_SKUS.forEach(priceCache::evict);
    }

    protected void providerAQuotes(String sku, String price, Duration age) {
        providerA.stubFor(priceOfA(sku)
                .willReturn(okJson("""
                {"sku": "%s", "basePrice": %s, "currency": "USD", "lastUpdated": "%s"}
                """.formatted(sku, price, clock.instant().minus(age)))));
    }

    protected void providerBQuotes(String sku, String cost, Duration age) {
        providerB.stubFor(costOfB(sku).willReturn(okJson("""
                {"itemCode": "%s", "cost": %s, "curr": "USD", "timestamp": %d}
                """.formatted(
                        sku, cost, clock.instant().minus(age).getEpochSecond()))));
    }

    protected void providerAIsDown() {
        providerA.stubFor(get(urlPathMatching("/prices/.*")).willReturn(serviceUnavailable()));
    }

    protected void providerBIsDown() {
        providerB.stubFor(get(urlPathMatching("/items/.*/cost")).willReturn(serviceUnavailable()));
    }

    protected static MappingBuilder priceOfA(String sku) {
        return get("/prices/" + sku);
    }

    protected static MappingBuilder costOfB(String sku) {
        return get("/items/" + sku + "/cost");
    }
}
