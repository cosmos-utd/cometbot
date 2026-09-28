package com.cometbot.discord;

import java.util.ArrayList;
import java.util.List;

public final class DiscordMessages {

    /** Discord's per-message character limit. */
    public static final int MAX_LENGTH = 2000;

    private DiscordMessages() {
    }

    /** Splits text into chunks of at most {@code max} chars, breaking on newlines where possible. */
    public static List<String> split(String text, int max) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            while (line.length() > max) {
                flush(chunks, current);
                chunks.add(line.substring(0, max));
                line = line.substring(max);
            }
            int needed = current.isEmpty() ? line.length() : current.length() + 1 + line.length();
            if (needed > max) {
                flush(chunks, current);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        flush(chunks, current);
        return chunks;
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.toString().isBlank()) {
            chunks.add(current.toString());
        }
        current.setLength(0);
    }
}
