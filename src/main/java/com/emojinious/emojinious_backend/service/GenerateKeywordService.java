package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.dto.KeywordRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GenerateKeywordService {
    private final RestTemplate restTemplate;
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "";
    public GenerateKeywordService(RestTemplate restTemplate) {
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

        String prompt = "Generate a list of " + request.getTheme() + " keywords related to the theme: " + request.getNumberOfKeywords();
        // OpenAI API 요청 본문 생성
        String requestBody = String.format(
                "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": %d}",
                prompt, request.getNumberOfKeywords()
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
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

