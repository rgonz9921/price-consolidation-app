package com.rgonz.pricing.client;

import com.rgonz.pricing.exception.ProviderRateLimitedException;
import com.rgonz.pricing.exception.ProviderTimeoutException;
import com.rgonz.pricing.exception.ProviderUnavailableException;
import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

abstract class RestProviderClient implements ProviderClient {

    private final RestClient interactiveClient;
    private final RestClient backgroundClient;

    RestProviderClient(RestClient interactiveClient, RestClient backgroundClient) {
        this.interactiveClient = interactiveClient;
        this.backgroundClient = backgroundClient;
    }

    protected <T> T fetch(QuoteMode mode, Class<T> bodyType, String uri, Object... uriVariables) {
        try {
            var body = clientFor(mode)
                    .get()
                    .uri(uri, uriVariables)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> rejectStatus(response))
                    .body(bodyType);
            if (body == null) {
                throw new ProviderUnavailableException(provider(), "empty body");
            }
            return body;
        } catch (ResourceAccessException e) {
            throw translateIoFailure(e, mode);
        } catch (RestClientException e) {
            throw new ProviderUnavailableException(provider(), "unreadable body", e);
        }
    }

    protected ProviderUnavailableException incompleteBody() {
        return new ProviderUnavailableException(provider(), "incomplete body");
    }

    private RestClient clientFor(QuoteMode mode) {
        return mode == QuoteMode.BACKGROUND ? backgroundClient : interactiveClient;
    }

    private void rejectStatus(ClientHttpResponse response) throws IOException {
        var status = response.getStatusCode();
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            throw new ProviderRateLimitedException(provider(), retryAfter(response));
        }
        throw new ProviderUnavailableException(provider(), "status " + status.value());
    }

    private static Optional<Duration> retryAfter(ClientHttpResponse response) {
        return Optional.ofNullable(response.getHeaders().getFirst("Retry-After"))
                .filter(value -> value.matches("\\d+"))
                .map(seconds -> Duration.ofSeconds(Long.parseLong(seconds)));
    }

    private RuntimeException translateIoFailure(ResourceAccessException e, QuoteMode mode) {
        var cause = e.getCause();
        if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
            return new ProviderTimeoutException(provider(), mode, e);
        }
        return new ProviderUnavailableException(provider(), "connection failed", e);
    }
}
