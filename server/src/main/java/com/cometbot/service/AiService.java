package com.cometbot.service;

import com.cometbot.dto.ExtractRequest;
import com.cometbot.dto.ExtractResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final RestClient restClient;

    public AiService(@Value("${app.ai.url}") String aiUrl,
                     @Value("${app.ai.extract-timeout-seconds:60}") int extractTimeoutSeconds) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
        );
        factory.setReadTimeout(Duration.ofSeconds(extractTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(aiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }

    public ExtractResponse extract(String syllabus, String guildId) {
        try {
            ExtractResponse response = restClient.post()
                    .uri("/extract")
                    .body(new ExtractRequest(syllabus, guildId))
                    .retrieve()
                    .body(ExtractResponse.class);
            return response == null ? ExtractResponse.empty() : response;
        } catch (RestClientException e) {
            log.error("AI extraction failed for guild {}: {}", guildId, e.getMessage());
            throw new AiServiceException("AI extraction service unavailable", e);
        }
    }
}