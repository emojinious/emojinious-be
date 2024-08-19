package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.KeywordRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${openai.api-key}")
    private String API_KEY;

    public RandomWordGenerator(RestTemplate restTemplate, RedisTemplate<String, String> redisTemplate) {
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, String> getKeywordsFromTheme(List<Player> players, String theme, String difficulty, int numberOfKeywords) {
        String cacheKey = "keywords:" + theme + ":" + difficulty;
        List<String> cachedKeywords = getCachedKeywords(cacheKey);

        if (cachedKeywords.isEmpty()) {
            cachedKeywords = generateAndCacheKeywords(theme, difficulty);
        }

        return assignKeywordsToPlayers(players, cachedKeywords, numberOfKeywords);
    }

    private List<String> getCachedKeywords(String cacheKey) {
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            return Arrays.asList(cachedValue.split(","));
        }
        return new ArrayList<>();
    }

    private List<String> generateAndCacheKeywords(String theme, String difficulty) {
        KeywordRequest request = new KeywordRequest(theme, 100);
        String response = callOpenAI(request, difficulty);
        List<String> keywords = parseResponse(response);

        if (!keywords.isEmpty()) {
            String cacheKey = "keywords:" + theme + ":" + difficulty;
            redisTemplate.opsForValue().set(cacheKey, String.join(",", keywords));
            redisTemplate.expire(cacheKey, 24 * 60 * 60 * 7, java.util.concurrent.TimeUnit.SECONDS); // 일주일
        }

        return keywords;
    }

    private Map<String, String> assignKeywordsToPlayers(List<Player> players, List<String> keywords, int numberOfKeywords) {
        Collections.shuffle(keywords);
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < players.size() && i < numberOfKeywords && i < keywords.size(); i++) {
            result.put(players.get(i).getId(), keywords.get(i));
        }
        return result;
    }

    private String callOpenAI(KeywordRequest request, String difficulty) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + API_KEY);
        headers.set("Content-Type", "application/json");

        String[] variations = {"Generate", "Create", "Produce", "Come up with", "Devise"};
        Random random = new Random();
        String actionWord = variations[random.nextInt(variations.length)];
        int seed = random.nextInt(1_000_000);
        String prompt = "Current time(seed): " + System.currentTimeMillis() + " " + actionWord +
                " 100 Korean keywords for the theme [ " +
                request.getTheme() + " ] with " + difficulty + " difficulty, where each keyword is a set of up to " +
                1 + " words. Response format: Answer the keywords without any other phrases, separated by commas.";

        String requestBody = String.format(
                "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 2000, \"temperature\": 2, \"seed\": %d}",
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

    private List<String> parseResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            String content = rootNode.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            if (content != null && !content.isEmpty()) {
                return Arrays.stream(content.split(","))
                        .map(String::trim)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}