package com.cometbot.dto;

import java.util.List;

public record ExtractResponse(List<ExtractItem> items) {

    public static ExtractResponse empty() {
        return new ExtractResponse(List.of());
    }
}