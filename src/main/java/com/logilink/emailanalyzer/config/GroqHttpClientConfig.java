package com.logilink.emailanalyzer.config;

import com.logilink.emailanalyzer.client.GroqHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class GroqHttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GroqHttpClientConfig.class);

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1}")
    private String groqApiBaseUrl;

    @Bean
    public GroqHttpClient groqHttpClient(AppSecretsDebugProperties secretsDebug) {
        if (secretsDebug.isDebugLogSecrets()) {
            log.warn(
                    "DEBUG_LOG_SECRETS: groq.api.key=[{}], groq.api.url=[{}]",
                    apiKey,
                    groqApiBaseUrl
            );
        }

        String base = groqApiBaseUrl.endsWith("/")
                ? groqApiBaseUrl.substring(0, groqApiBaseUrl.length() - 1)
                : groqApiBaseUrl;

        WebClient webClient = WebClient.builder()
                .baseUrl(base)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient))
                .build();

        return factory.createClient(GroqHttpClient.class);
    }
}
