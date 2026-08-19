package com.cometbot.dto;

import jakarta.validation.constraints.NotBlank;

public record ChannelRequest(
        @NotBlank(message = "channelId is required") String channelId
) {
}