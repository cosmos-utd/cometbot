package com.cometbot.dto;

import com.cometbot.model.Deadline;

import java.time.LocalDate;

public record DeadlineDto(
        Long id,
        String guildId,
        String title,
        String type,
        LocalDate dueDate,
        boolean notified3Day,
        boolean notifiedToday
) {
    public static DeadlineDto from(Deadline d) {
        return new DeadlineDto(
                d.getId(),
                d.getGuildId(),
                d.getTitle(),
                d.getDeadlineType(),
                d.getDueDate(),
                d.isNotified3Day(),
                d.isNotifiedToday()
        );
    }
}