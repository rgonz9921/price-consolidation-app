package com.rgonz.pricing.config;

import com.rgonz.pricing.client.ProviderAClient;
import com.rgonz.pricing.client.ProviderBClient;
import com.rgonz.pricing.config.ProviderProperties.Endpoint;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderClientConfig {

    @Bean
    public ProviderAClient providerAClient(ProviderProperties properties) {
        var endpoint = properties.a();
        return new ProviderAClient(
                restClient(endpoint, endpoint.readTimeout()), restClient(endpoint, endpoint.backgroundReadTimeout()));
    }

    @Bean
    public ProviderBClient providerBClient(ProviderProperties properties) {
        var endpoint = properties.b();
        return new ProviderBClient(
                restClient(endpoint, endpoint.readTimeout()), restClient(endpoint, endpoint.backgroundReadTimeout()));
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
