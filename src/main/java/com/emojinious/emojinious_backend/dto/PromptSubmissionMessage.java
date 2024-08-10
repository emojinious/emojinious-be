package com.emojinious.emojinious_backend.dto;

import lombok.Data;

@Data
public class PromptSubmissionMessage {
    private String prompt;

    //Jackson -> PromptSubmissionMessage 객체 생성할 수 있도록
    public PromptSubmissionMessage() {
    }

    public PromptSubmissionMessage(String prompt) {
        this.prompt = prompt;
    }
}
