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
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"([^\"]*)\"");

    private final RestClient restClient;

    public AiService(@Value("${app.ai.url}") String aiUrl,
                     @Value("${app.ai.extract-timeout-seconds:60}") int extractTimeoutSeconds) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        // The JDK client's default h2c upgrade breaks uvicorn requests over plain HTTP.
                        .version(HttpClient.Version.HTTP_1_1)
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

    public ExtractResponse extract(String syllabus, String guildId, LocalDate today) {
        try {
            ExtractResponse response = restClient.post()
                    .uri("/extract")
                    .body(new ExtractRequest(syllabus, guildId, today.toString()))
                    .retrieve()
                    .body(ExtractResponse.class);
            return response == null ? ExtractResponse.empty() : response;
        } catch (RestClientResponseException e) {
            log.error("AI extraction failed for guild {}: {} {}", guildId,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new AiServiceException("AI extraction failed: " + detail(e), e);
        } catch (RestClientException e) {
            log.error("AI extraction failed for guild {}: {}", guildId, e.getMessage());
            throw new AiServiceException("AI extraction service unavailable", e);
        }
    }

    /** FastAPI errors look like {"detail": "..."}; surface that text to the user. */
    private static String detail(RestClientResponseException e) {
        Matcher m = DETAIL.matcher(e.getResponseBodyAsString());
        return m.find() ? m.group(1) : "HTTP " + e.getStatusCode().value();
    }
}
