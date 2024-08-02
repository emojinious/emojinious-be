package com.emojinious.emojinious_backend.dto;

import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
@Data
public class PlayerCreateRequest {
    @Size(max = 7, message = "Nickname must be at most 7 characters long")
    private String nickname;

    @Min(1) @Max(8)
    private int characterId;
}