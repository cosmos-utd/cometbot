package com.cometbot.model;

import java.util.List;

public final class DeadlineTypes {

    public static final String DEFAULT = "assignment";
    public static final List<String> ALL = List.of("assignment", "quiz", "test", "exam", "final", "other");

    private DeadlineTypes() {
    }

    /** For user input: blank means the default, anything unknown is rejected. */
    public static String require(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String type = raw.trim().toLowerCase();
        if (!ALL.contains(type)) {
            throw new IllegalArgumentException(
                    "type must be one of: " + String.join(", ", ALL));
        }
        return type;
    }

    /** For AI output: anything unknown becomes "other". */
    public static String normalize(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase();
        return ALL.contains(type) ? type : "other";
    }
}
