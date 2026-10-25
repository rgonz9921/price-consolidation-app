package com.rgonz.pricing.config;

import com.rgonz.pricing.client.ProviderAClient;
import com.rgonz.pricing.client.ProviderBClient;
import com.rgonz.pricing.client.ProviderClient;
import com.rgonz.pricing.client.ResilientProviderClient;
import com.rgonz.pricing.config.ProviderProperties.Endpoint;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ProviderProperties.class, ResilienceProperties.class})
public class ProviderClientConfig {

    private static final String PROVIDER_A = "providerA";
    private static final String PROVIDER_B = "providerB";

    @Bean
    public ProviderClient providerA(
            ProviderProperties providers,
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            BulkheadRegistry bulkheads,
            ResilienceProperties resilience,
            Clock clock) {
        var endpoint = providers.a();
        var httpClient = new ProviderAClient(
                restClient(endpoint, endpoint.readTimeout()), restClient(endpoint, endpoint.backgroundReadTimeout()));
        return new ResilientProviderClient(
                httpClient,
                circuitBreakers.circuitBreaker(PROVIDER_A),
                retries.retry(PROVIDER_A),
                bulkheads.bulkhead(PROVIDER_A),
                Optional.empty(),
                resilience,
                clock);
    }

    @Bean
    public ProviderClient providerB(
            ProviderProperties providers,
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            BulkheadRegistry bulkheads,
            RateLimiterRegistry rateLimiters,
            ResilienceProperties resilience,
            Clock clock) {
        var endpoint = providers.b();
        var httpClient = new ProviderBClient(
                restClient(endpoint, endpoint.readTimeout()), restClient(endpoint, endpoint.backgroundReadTimeout()));
        return new ResilientProviderClient(
                httpClient,
                circuitBreakers.circuitBreaker(PROVIDER_B),
                retries.retry(PROVIDER_B),
                bulkheads.bulkhead(PROVIDER_B),
                Optional.of(rateLimiters.rateLimiter(PROVIDER_B)),
                resilience,
                clock);
    }

    public static RestClient restClient(Endpoint endpoint, Duration readTimeout) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(endpoint.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(endpoint.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
