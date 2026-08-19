package com.cometbot.controller;

import com.cometbot.dto.ChannelRequest;
import com.cometbot.dto.GuildSettingsDto;
import com.cometbot.model.GuildSettings;
import com.cometbot.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guilds/{guildId}/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public GuildSettingsDto get(@PathVariable String guildId) {
        return GuildSettingsDto.from(settingsService.get(guildId));
    }

    @PutMapping("/channel")
    public GuildSettingsDto setReminderChannel(@PathVariable String guildId,
                                               @Valid @RequestBody ChannelRequest request) {
        GuildSettings settings = settingsService.setReminderChannel(guildId, request.channelId());
        return GuildSettingsDto.from(settings);
    }
}