package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.KeywordRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RandomWordGenerator {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${openai.api-key}")
    private String API_KEY;
    public RandomWordGenerator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, String> getKeywordsFromTheme(List<Player> players, String theme, int numberOfKeywords) {
        // API 요청 데이터 생성
        KeywordRequest request = new KeywordRequest(theme, numberOfKeywords);
        // API 호출
        String response = callOpenAI(request);
        // 응답 처리
        return parseResponse(players, response);
    }

    private String callOpenAI(KeywordRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + API_KEY);
        headers.set("Content-Type", "application/json");

        String[] variations = {"Generate", "Create", "Produce", "Come up with", "Devise"};
        Random random = new Random();
        String actionWord = variations[random.nextInt(variations.length)];
        int seed = random.nextInt(1_000_000);
        String prompt = "Current time(seed): " + System.currentTimeMillis() + actionWord +
                request.getNumberOfKeywords() + " Korean keywords for the theme [ " +
                request.getTheme() + " ], where each keyword is a set of up to " +
                1 + " words. Response format: Answer the keywords without any other phrases, separated by commas.";
        // OpenAI API 요청 본문 생성
        String requestBody = String.format(
                "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 1000, \"temperature\": 2, \"seed\": %d}",
                prompt, seed
        );

        HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(API_URL, HttpMethod.POST, httpEntity, String.class);
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        } else {
            throw new RuntimeException("Failed to call OpenAI API: " + responseEntity.getStatusCode());
        }
    }


    public Map<String, String> parseResponse(List<Player> players, String jsonResponse) {
        String content;
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            content = rootNode.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }

        List<String> words = new ArrayList<>();
        Map<String, String> result = new HashMap<>();

        System.out.println("content = " + content);
        if (content != null && !content.isEmpty()) {
            words = Arrays.stream(content.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }

        for (int i = 0; i < players.size(); i++) {
            if (i < words.size()) {
                result.put(players.get(i).getId(), words.get(i));
            } else {
                result.put(players.get(i).getId(), "");
            }
        }

        return result;
    }

}