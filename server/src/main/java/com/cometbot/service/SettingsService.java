package com.cometbot.service;

import com.cometbot.model.GuildSettings;
import com.cometbot.repo.GuildSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final GuildSettingsRepository settingsRepository;

    public SettingsService(GuildSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public GuildSettings get(String guildId) {
        return settingsRepository.findById(guildId)
                .orElseGet(() -> new GuildSettings(guildId));
    }

    @Transactional
    public GuildSettings setReminderChannel(String guildId, String channelId) {
        GuildSettings settings = settingsRepository.findById(guildId)
                .orElseGet(() -> new GuildSettings(guildId));
        settings.setReminderChannelId(channelId);
        return settingsRepository.save(settings);
    }

    public String getReminderChannelId(String guildId) {
        return settingsRepository.findById(guildId)
                .map(GuildSettings::getReminderChannelId)
                .orElse(null);
    }
}