package com.cometbot.discord;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordMessagesTest {

    @Test
    void shortTextIsOneChunk() {
        assertThat(DiscordMessages.split("hello\nworld", 2000)).containsExactly("hello\nworld");
    }

    @Test
    void splitsOnLineBoundariesWithinTheLimit() {
        String text = IntStream.range(0, 100)
                .mapToObj(i -> "`#" + i + "` Homework " + i + " (assignment) - Mon, Sep 1 2026")
                .collect(Collectors.joining("\n"));

        List<String> chunks = DiscordMessages.split(text, 2000);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(2000));
        assertThat(String.join("\n", chunks)).isEqualTo(text);
    }

    @Test
    void hardSplitsLinesLongerThanTheLimit() {
        List<String> chunks = DiscordMessages.split("x".repeat(25), 10);
        assertThat(chunks).containsExactly("x".repeat(10), "x".repeat(10), "x".repeat(5));
    }
}
