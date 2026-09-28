package com.cometbot.service;

import com.cometbot.model.Deadline;
import com.cometbot.repo.DeadlineRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");

    private final DeadlineRepository deadlineRepository;
    private final SettingsService settingsService;
    private final ObjectProvider<JDA> jdaProvider;
    private final Clock clock;
    private final int beforeDays;

    public ReminderService(DeadlineRepository deadlineRepository,
                           SettingsService settingsService,
                           ObjectProvider<JDA> jdaProvider,
                           Clock clock,
                           @Value("${reminder.before-days:3}") int beforeDays) {
        this.deadlineRepository = deadlineRepository;
        this.settingsService = settingsService;
        this.jdaProvider = jdaProvider;
        this.clock = clock;
        this.beforeDays = beforeDays;
    }

    /**
     * Sends a "due today" reminder on the due date and an advance reminder once the
     * deadline is within {@code beforeDays} days. Advance reminders use a window rather
     * than an exact day, so they still go out if the bot was down on that day or the
     * deadline was added late.
     */
    @Scheduled(fixedDelayString = "${reminder.interval-ms:1800000}", initialDelay = 30000)
    public void checkDeadlines() {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            return;
        }

        LocalDate today = LocalDate.now(clock);

        for (Deadline d : deadlineRepository.findByDueDateAndNotifiedTodayFalse(today)) {
            if (send(jda, d, format(d, today))) {
                // The advance reminder is moot once "due today" has gone out.
                d.setNotifiedToday(true);
                d.setNotified3Day(true);
                deadlineRepository.save(d);
            }
        }

        for (Deadline d : deadlineRepository.findByDueDateBetweenAndNotified3DayFalse(
                today.plusDays(1), today.plusDays(beforeDays))) {
            if (send(jda, d, format(d, today))) {
                d.setNotified3Day(true);
                deadlineRepository.save(d);
            }
        }
    }

    private boolean send(JDA jda, Deadline deadline, String text) {
        try {
            Guild guild = jda.getGuildById(deadline.getGuildId());
            if (guild == null) {
                return false;
            }

            String channelId = settingsService.getReminderChannelId(deadline.getGuildId());
            if (channelId == null && guild.getSystemChannel() != null) {
                channelId = guild.getSystemChannel().getId();
            }
            if (channelId == null) {
                return false;
            }

            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel == null || !channel.canTalk()) {
                return false;
            }

            // Wait for Discord to accept the message so a failed send is retried next run.
            channel.sendMessage(text).complete();
            return true;
        } catch (Exception e) {
            log.warn("Failed to send reminder for deadline {}: {}", deadline.getId(), e.getMessage());
            return false;
        }
    }

    String format(Deadline deadline, LocalDate today) {
        String label = deadline.getTitle() + " (" + deadline.getDeadlineType() + ")";
        String date = deadline.getDueDate().format(DATE_FMT);
        long days = ChronoUnit.DAYS.between(today, deadline.getDueDate());
        if (days <= 0) {
            return "⚠️ **Due today:** `" + label + "` — " + date;
        }
        String when = days == 1 ? "tomorrow" : "in " + days + " days";
        return "📚 **Reminder:** `" + label + "` is due " + when + " (" + date + ")";
    }
}
