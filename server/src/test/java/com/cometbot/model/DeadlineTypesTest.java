package com.cometbot.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadlineTypesTest {

    @Test
    void requireDefaultsBlankAndRejectsUnknown() {
        assertThat(DeadlineTypes.require(null)).isEqualTo("assignment");
        assertThat(DeadlineTypes.require(" Quiz ")).isEqualTo("quiz");
        assertThatThrownBy(() -> DeadlineTypes.require("homework"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assignment, quiz");
    }

    @Test
    void normalizeMapsUnknownToOther() {
        assertThat(DeadlineTypes.normalize("EXAM")).isEqualTo("exam");
        assertThat(DeadlineTypes.normalize("project")).isEqualTo("other");
        assertThat(DeadlineTypes.normalize(null)).isEqualTo("other");
    }
}
