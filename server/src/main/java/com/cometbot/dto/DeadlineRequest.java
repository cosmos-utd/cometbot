package com.cometbot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DeadlineRequest(
        @Size(max = 255, message = "title too long") String title,
        @Size(max = 16, message = "type too long") String type,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dueDate
) {
}