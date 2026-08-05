package com.finpilot.importengine.service;

import com.finpilot.importengine.dto.CategorizationBatchRequest;
import com.finpilot.importengine.dto.CategorizationBatchResponse;
import com.finpilot.importengine.dto.CategorizationRequestItem;
import com.finpilot.importengine.dto.CategorizationResultItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

// Talks to the FastAPI AI service. Only this class knows the URL and the
// internal API key header — nothing else should call RestTemplate directly.
@Component
public class CategorizationClient {

    private final RestTemplate restTemplate;

    @Value("${finpilot.ai-service.base-url}")
    private String baseUrl;

    @Value("${finpilot.ai-service.api-key}")
    private String apiKey;

    public CategorizationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<CategorizationResultItem> categorize(List<CategorizationRequestItem> items) {
        CategorizationBatchRequest requestBody = new CategorizationBatchRequest(items);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", apiKey);
        headers.set("Content-Type", "application/json");

        HttpEntity<CategorizationBatchRequest> request = new HttpEntity<>(requestBody, headers);
        String url = baseUrl + "/api/v1/categorize/batch";

        CategorizationBatchResponse response;
        try {
            response = restTemplate.postForObject(url, request, CategorizationBatchResponse.class);
        } catch (RestClientException e) {
            // Covers: connection refused, timeout, 4xx/5xx from the AI
            // service. One wrapped exception type instead of every caller
            // needing to know Spring's whole RestClientException hierarchy.
            throw new CategorizationServiceUnavailableException(
                    "AI categorization service call failed: " + e.getMessage(), e);
        }

        return response != null ? response.getResults() : List.of();
    }
}