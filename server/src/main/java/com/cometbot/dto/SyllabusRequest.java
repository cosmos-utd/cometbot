package com.cometbot.dto;

import jakarta.validation.constraints.NotBlank;

public record SyllabusRequest(
        @NotBlank(message = "syllabus content is required") String content
) {
}