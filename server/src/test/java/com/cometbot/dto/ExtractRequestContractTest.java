package com.cometbot.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The AI service (ai/main.py) rejects any other field names with a 422. */
class ExtractRequestContractTest {

    @Test
    void serializesWithTheFieldNamesTheAiServiceExpects() {
        JsonNode json = JsonMapper.builder().build()
                .valueToTree(new ExtractRequest("HW 1 due Sep 10", "123", "2026-08-20"));

        Set<String> fields = new HashSet<>(json.propertyNames());
        assertThat(fields).containsExactlyInAnyOrder("syllabus", "guild_id", "today");
        assertThat(json.get("guild_id").asString()).isEqualTo("123");
        assertThat(json.get("today").asString()).isEqualTo("2026-08-20");
    }
}
