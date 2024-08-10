package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.dto.PromptSubmissionMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GenerateImageService {

    private final RestTemplate restTemplate;
    private static final String API_URL = "https://api.openai.com/v1/images/generations";
    private static final String API_KEY = "";

    public GenerateImageService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getImagesFromMessage(String message) {
        // API 요청 데이터 생성
        PromptSubmissionMessage prompt = new PromptSubmissionMessage(message);
        // API 호출
        String response = callImageAI(prompt);
        // 응답에서 이미지 URL 추출
        return parseResponse(response);
    }

    private String callImageAI(PromptSubmissionMessage prompt){
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + API_KEY);
        headers.set("Content-Type", "application/json");

        String requestBody = String.format(
                "{\"prompt\": \"%s\", \"n\": 1, \"size\": \"1024x1024\"}",
                prompt
        );

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(API_URL, HttpMethod.POST, request, String.class);

        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        } else {
            throw new RuntimeException("Failed to call OpenAI API: " + responseEntity.getStatusCode());
        }
    }

    private String parseResponse(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response);
            JsonNode imageUrlNode = root.findPath("url");

            if (imageUrlNode != null && !imageUrlNode.isMissingNode()) {
                return imageUrlNode.asText();
            } else {
                throw new RuntimeException("Image URL not found in response.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}




