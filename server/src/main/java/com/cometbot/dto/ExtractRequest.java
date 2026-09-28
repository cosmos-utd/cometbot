package com.cometbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /extract on the AI service. Field names must match
 * ai/main.py ExtractRequest (checked by ExtractRequestContractTest and ai/test_main.py).
 *
 * @param today ISO date (YYYY-MM-DD) in the reminder time zone, used to resolve missing years
 */
public record ExtractRequest(
        String syllabus,
        @JsonProperty("guild_id") String guildId,
        String today
) {
}
