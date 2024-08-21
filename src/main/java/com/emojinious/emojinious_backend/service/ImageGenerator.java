package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.dto.PromptSubmissionMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Component
public class ImageGenerator {

    private final RestTemplate restTemplate;
    private static final String API_URL = "https://api.openai.com/v1/images/generations";

    @Value("${openai.api-key}")
    private String API_KEY;

    public ImageGenerator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public CompletableFuture<String> getImagesFromMessageAsync(String message) {
        return CompletableFuture.supplyAsync(() -> {
            PromptSubmissionMessage prompt = new PromptSubmissionMessage(message);
            String response = callImageAI(prompt);
            return parseResponse(response);
        });
    }

    private String callImageAI(PromptSubmissionMessage prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + API_KEY);
        headers.set("Content-Type", "application/json");

        String requestBody = String.format(
                "{\"model\": \"dall-e-3\",\"prompt\": \"%s\", \"n\": 1, \"size\": \"1024x1024\"}",
                "Create an cute emoji style illustration of the following: " + prompt
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
                System.out.println("imageUrlNode.asText() = " + imageUrlNode.asText());
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