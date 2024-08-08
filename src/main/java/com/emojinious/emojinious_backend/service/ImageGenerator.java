package com.emojinious.emojinious_backend.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ImageGenerator {
    private final String STABLE_DIFFUSION_API_URL = "";
    private final String API_KEY = "";
    // yml에 숨기기
    public String generateImage(String prompt) {
        RestTemplate restTemplate = new RestTemplate();
        String requestBody = "{\"text_prompts\": [{\"text\": \"" + prompt + "\"}]}";

        String response = restTemplate.postForObject(STABLE_DIFFUSION_API_URL, requestBody, String.class);
        // 어케 쓰는지 몰름 걍 대충

        // imgur api나 올려서 링크만 전달

        return "예시예시.com/" + System.currentTimeMillis();
    }
}