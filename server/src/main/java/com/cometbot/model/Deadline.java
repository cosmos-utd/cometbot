package com.cometbot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "deadlines", indexes = {
        @Index(name = "idx_deadlines_guild_date", columnList = "guild_id, due_date")
})
public class Deadline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", length = 64, nullable = false)
    private String guildId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "deadline_type", length = 16, nullable = false)
    private String deadlineType;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "notified_3day", nullable = false)
    private boolean notified3Day;

    @Column(name = "notified_today", nullable = false)
    private boolean notifiedToday;

    /** True when added by a person; rescans only replace AI-extracted (non-manual) deadlines. */
    @Column(name = "manual", nullable = false)
    private boolean manual;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Deadline() {
    }

    public Deadline(String guildId, String title, String deadlineType, LocalDate dueDate) {
        this.guildId = guildId;
        this.title = title;
        this.deadlineType = deadlineType;
        this.dueDate = dueDate;
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

    public Long getId() {
        return id;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDeadlineType() {
        return deadlineType;
    }

    public void setDeadlineType(String deadlineType) {
        this.deadlineType = deadlineType;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isNotified3Day() {
        return notified3Day;
    }

    public void setNotified3Day(boolean notified3Day) {
        this.notified3Day = notified3Day;
    }

    public boolean isNotifiedToday() {
        return notifiedToday;
    }

    public void setNotifiedToday(boolean notifiedToday) {
        this.notifiedToday = notifiedToday;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}