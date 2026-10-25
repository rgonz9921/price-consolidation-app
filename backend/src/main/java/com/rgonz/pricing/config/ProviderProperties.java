package com.rgonz.pricing.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pricing.providers")
public record ProviderProperties(Endpoint a, Endpoint b) {

    public record Endpoint(URI baseUrl, Duration connectTimeout, Duration readTimeout, Duration backgroundReadTimeout) {

        public Endpoint {
            if (backgroundReadTimeout == null) {
                backgroundReadTimeout = readTimeout;
            }
        }
    }
}
