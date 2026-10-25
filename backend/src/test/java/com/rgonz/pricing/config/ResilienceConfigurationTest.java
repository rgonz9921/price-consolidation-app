package com.rgonz.pricing.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rgonz.pricing.TestcontainersConfig;
import com.rgonz.pricing.client.QuoteMode;
import com.rgonz.pricing.exception.ProviderTimeoutException;
import com.rgonz.pricing.model.Provider;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ResilienceConfigurationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @Autowired
    private RetryRegistry retries;

    @Autowired
    private RateLimiterRegistry rateLimiters;

    @Autowired
    private BulkheadRegistry bulkheads;

    @Autowired
    private ResilienceProperties resilience;

    @Test
    void circuitBreakersMatchDocumentedValues() {
        var providerA = circuitBreakers.circuitBreaker("providerA").getCircuitBreakerConfig();
        var providerB = circuitBreakers.circuitBreaker("providerB").getCircuitBreakerConfig();

        assertThat(providerA.getSlidingWindowType()).isEqualTo(SlidingWindowType.COUNT_BASED);
        assertThat(providerA.getSlidingWindowSize()).isEqualTo(20);
        assertThat(providerA.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(providerA.getFailureRateThreshold()).isEqualTo(50);
        assertThat(providerA.getSlowCallRateThreshold()).isEqualTo(80);
        assertThat(providerA.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(250));
        assertThat(providerA.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(providerB.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(900));
    }

    @Test
    void onlyProviderBIgnoresItsOwnInteractiveTimeouts() {
        var interactiveTimeout = new ProviderTimeoutException(Provider.PROVIDER_B, QuoteMode.INTERACTIVE, null);
        var backgroundTimeout = new ProviderTimeoutException(Provider.PROVIDER_B, QuoteMode.BACKGROUND, null);
        var providerA = circuitBreakers.circuitBreaker("providerA").getCircuitBreakerConfig();
        var providerB = circuitBreakers.circuitBreaker("providerB").getCircuitBreakerConfig();

        assertThat(providerB.getIgnoreExceptionPredicate().test(interactiveTimeout))
                .isTrue();
        assertThat(providerB.getIgnoreExceptionPredicate().test(backgroundTimeout))
                .isFalse();
        assertThat(providerB.getRecordExceptionPredicate().test(backgroundTimeout))
                .isTrue();
        assertThat(providerA.getIgnoreExceptionPredicate().test(interactiveTimeout))
                .isFalse();
        assertThat(providerA.getRecordExceptionPredicate().test(interactiveTimeout))
                .isTrue();
    }

    @Test
    void onlyProviderARetries() {
        assertThat(retries.retry("providerA").getRetryConfig().getMaxAttempts()).isEqualTo(2);
        assertThat(retries.retry("providerB").getRetryConfig().getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void providerBGetsOnePermitEvery250Millis() {
        var config = rateLimiters.rateLimiter("providerB").getRateLimiterConfig();

        assertThat(config.getLimitForPeriod()).isEqualTo(1);
        assertThat(config.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(250));
        assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ZERO);
        assertThat(resilience.backgroundPermitWait()).isEqualTo(Duration.ofSeconds(1));
        assertThat(resilience.rateLimitedPause()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void bulkheadsIsolateProviders() {
        assertThat(bulkheads.bulkhead("providerA").getBulkheadConfig().getMaxConcurrentCalls())
                .isEqualTo(50);
        assertThat(bulkheads.bulkhead("providerB").getBulkheadConfig().getMaxConcurrentCalls())
                .isEqualTo(10);
    }
}
