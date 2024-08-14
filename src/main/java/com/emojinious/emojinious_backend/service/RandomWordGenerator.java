package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.KeywordRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RandomWordGenerator {
    private final RestTemplate restTemplate;
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "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";
    public RandomWordGenerator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<String> getKeywordsFromTheme(String theme, int numberOfKeywords) {
        // API 요청 데이터 생성
        KeywordRequest request = new KeywordRequest(theme, numberOfKeywords);
        // API 호출
        String response = callOpenAI(request);
        // 응답 처리
        return parseResponse(response);
    }

    private String callOpenAI(KeywordRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + API_KEY);
        headers.set("Content-Type", "application/json");

        String prompt = "Generate " +
                request.getNumberOfKeywords() + " Korean keywords for the theme [ " +
                request.getTheme() + " ], where each keyword is a set of up to " +
                1 + " words. Response format: Answer the keywords without any other phrases, separated by commas.";
        // OpenAI API 요청 본문 생성
        String requestBody = String.format(
                "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 100}",
                prompt
        );

        HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(API_URL, HttpMethod.POST, httpEntity, String.class);
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        } else {
            throw new RuntimeException("Failed to call OpenAI API: " + responseEntity.getStatusCode());
        }
    }

    private List<String> parseResponse(String response) {
        if (response != null && !response.isEmpty()) {
            return Arrays.stream(response.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}