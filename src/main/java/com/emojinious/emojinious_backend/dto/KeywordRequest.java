package com.emojinious.emojinious_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class KeywordRequest {
    private String theme;
    private int numberOfKeywords;

    public KeywordRequest(String theme, int numberOfKeywords) {
        this.theme = theme;
        this.numberOfKeywords = numberOfKeywords;
    }
}
