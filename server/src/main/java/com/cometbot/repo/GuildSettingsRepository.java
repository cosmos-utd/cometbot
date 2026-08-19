package com.cometbot.repo;

import com.cometbot.model.GuildSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildSettingsRepository extends JpaRepository<GuildSettings, String> {
}