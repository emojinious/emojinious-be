package com.emojinious.emojinious_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class TurnResultDto {
    private Map<String, PlayerDto> turnResult;
}
