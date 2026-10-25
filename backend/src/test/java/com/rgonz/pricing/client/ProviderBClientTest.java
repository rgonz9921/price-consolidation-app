package com.rgonz.pricing.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rgonz.pricing.config.ProviderClientConfig;
import com.rgonz.pricing.config.ProviderProperties.Endpoint;
import com.rgonz.pricing.exception.ProviderRateLimitedException;
import com.rgonz.pricing.exception.ProviderTimeoutException;
import com.rgonz.pricing.model.Provider;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProviderBClientTest {

    private static final Duration INTERACTIVE_READ_TIMEOUT = Duration.ofMillis(200);
    private static final Duration BACKGROUND_READ_TIMEOUT = Duration.ofMillis(600);
    private static final int DELAY_BETWEEN_TIMEOUTS_MS = 400;
    private static final String COST_BODY = """
            {"itemCode": "ABC-123", "cost": 102.00, "curr": "USD", "timestamp": 1792663200}
            """;

    @RegisterExtension
    static WireMockExtension providerB = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ProviderBClient client;

    @BeforeEach
    void createClient() {
        var endpoint = new Endpoint(
                URI.create(providerB.baseUrl()),
                Duration.ofMillis(100),
                INTERACTIVE_READ_TIMEOUT,
                BACKGROUND_READ_TIMEOUT);
        client = new ProviderBClient(
                ProviderClientConfig.restClient(endpoint, INTERACTIVE_READ_TIMEOUT),
                ProviderClientConfig.restClient(endpoint, BACKGROUND_READ_TIMEOUT));
    }

    @Test
    void mapsProviderBContractAndEpochSecondsToQuote() {
        providerB.stubFor(get("/items/ABC-123/cost").willReturn(okJson(COST_BODY)));

        var quote = client.quote("ABC-123", QuoteMode.INTERACTIVE);

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("102.00"));
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.quotedAt()).isEqualTo(Instant.parse("2026-10-22T10:00:00Z"));
        assertThat(quote.provider()).isEqualTo(Provider.PROVIDER_B);
    }

    @Test
    void reportsRateLimitWithRetryAfter() {
        providerB.stubFor(get("/items/ABC-123/cost")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2")));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOfSatisfying(
                        ProviderRateLimitedException.class,
                        e -> assertThat(e.getRetryAfter()).contains(Duration.ofSeconds(2)));
    }

    @Test
    void reportsRateLimitWithoutRetryAfterAsUnknownWait() {
        providerB.stubFor(get("/items/ABC-123/cost").willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOfSatisfying(
                        ProviderRateLimitedException.class,
                        e -> assertThat(e.getRetryAfter()).isEmpty());
    }

    @Test
    void cutsSlowResponseInInteractiveMode() {
        providerB.stubFor(
                get("/items/ABC-123/cost").willReturn(okJson(COST_BODY).withFixedDelay(DELAY_BETWEEN_TIMEOUTS_MS)));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderTimeoutException.class);
    }

    @Test
    void waitsForSlowResponseInBackgroundMode() {
        providerB.stubFor(
                get("/items/ABC-123/cost").willReturn(okJson(COST_BODY).withFixedDelay(DELAY_BETWEEN_TIMEOUTS_MS)));

        var quote = client.quote("ABC-123", QuoteMode.BACKGROUND);

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("102.00"));
    }
}
