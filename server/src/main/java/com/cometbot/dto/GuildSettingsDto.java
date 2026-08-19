package com.cometbot.dto;

import com.cometbot.model.GuildSettings;

public record GuildSettingsDto(String guildId, String reminderChannelId) {
    public static GuildSettingsDto from(GuildSettings g) {
        return new GuildSettingsDto(g.getGuildId(), g.getReminderChannelId());
    }
}