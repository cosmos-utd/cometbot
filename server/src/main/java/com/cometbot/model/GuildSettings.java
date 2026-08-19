package com.cometbot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "guild_settings")
public class GuildSettings {

    @Id
    @Column(name = "guild_id", length = 64)
    private String guildId;

    @Column(name = "reminder_channel_id", length = 64)
    private String reminderChannelId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected GuildSettings() {
    }

    public GuildSettings(String guildId) {
        this.guildId = guildId;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getGuildId() {
        return guildId;
    }

    public String getReminderChannelId() {
        return reminderChannelId;
    }

    public void setReminderChannelId(String reminderChannelId) {
        this.reminderChannelId = reminderChannelId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}