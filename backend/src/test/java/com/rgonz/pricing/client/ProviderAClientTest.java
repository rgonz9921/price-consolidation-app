package com.rgonz.pricing.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rgonz.pricing.config.ProviderClientConfig;
import com.rgonz.pricing.config.ProviderProperties.Endpoint;
import com.rgonz.pricing.exception.ProviderTimeoutException;
import com.rgonz.pricing.exception.ProviderUnavailableException;
import com.rgonz.pricing.model.Provider;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProviderAClientTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(200);

    @RegisterExtension
    static WireMockExtension providerA = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ProviderAClient client;

    @BeforeEach
    void createClient() {
        var endpoint =
                new Endpoint(URI.create(providerA.baseUrl()), Duration.ofMillis(100), READ_TIMEOUT, READ_TIMEOUT);
        var restClient = ProviderClientConfig.restClient(endpoint, READ_TIMEOUT);
        client = new ProviderAClient(restClient, restClient);
    }

    @Test
    void mapsProviderAContractToQuote() {
        providerA.stubFor(get("/prices/ABC-123").willReturn(okJson("""
                {"sku": "ABC-123", "basePrice": 100.00, "currency": "USD", "lastUpdated": "2026-10-22T10:00:00Z"}
                """)));

        var quote = client.quote("ABC-123", QuoteMode.INTERACTIVE);

        assertThat(quote.sku()).isEqualTo("ABC-123");
        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.quotedAt()).isEqualTo(Instant.parse("2026-10-22T10:00:00Z"));
        assertThat(quote.provider()).isEqualTo(Provider.PROVIDER_A);
    }

    @Test
    void timesOutWhenResponseExceedsReadTimeout() {
        providerA.stubFor(
                get("/prices/ABC-123").willReturn(okJson("{}").withFixedDelay((int) READ_TIMEOUT.toMillis() * 2)));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderTimeoutException.class);
    }

    @Test
    void reportsServerErrorAsUnavailable() {
        providerA.stubFor(get("/prices/ABC-123").willReturn(serviceUnavailable()));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("status 503");
    }

    @Test
    void reportsDroppedConnectionAsUnavailable() {
        providerA.stubFor(get("/prices/ABC-123").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("connection failed");
    }

    @Test
    void rejectsBodyWithoutPrice() {
        providerA.stubFor(get("/prices/ABC-123").willReturn(okJson("""
                {"sku": "ABC-123", "currency": "USD", "lastUpdated": "2026-10-22T10:00:00Z"}
                """)));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("incomplete body");
    }

    @Test
    void rejectsQuoteForAnotherSku() {
        providerA.stubFor(get("/prices/ABC-123").willReturn(okJson("""
                {"sku": "XYZ-789", "basePrice": 1, "currency": "USD", "lastUpdated": "2026-10-22T10:00:00Z"}
                """)));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void rejectsBodyThatIsNotJson() {
        providerA.stubFor(get("/prices/ABC-123")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("<html>")));

        assertThatThrownBy(() -> client.quote("ABC-123", QuoteMode.INTERACTIVE))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("unreadable body");
    }
}
