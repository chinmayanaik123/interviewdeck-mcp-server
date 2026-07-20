package com.interviewdeck.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Question(
        String id,
        String category,
        String question,
        String answer,
        String difficulty,
        List<String> tags,
        String tip,
        String code,
        String lang,
        String deep,
        String youtube
) {}
