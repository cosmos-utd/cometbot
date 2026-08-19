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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");

    private final DeadlineRepository deadlineRepository;
    private final SettingsService settingsService;
    private final ObjectProvider<JDA> jdaProvider;
    private final ZoneId zoneId;
    private final int beforeDays;

    public ReminderService(DeadlineRepository deadlineRepository,
                           SettingsService settingsService,
                           ObjectProvider<JDA> jdaProvider,
                           @Value("${reminder.tz:America/Chicago}") String tz,
                           @Value("${reminder.before-days:3}") int beforeDays) {
        this.deadlineRepository = deadlineRepository;
        this.settingsService = settingsService;
        this.jdaProvider = jdaProvider;
        this.zoneId = ZoneId.of(tz);
        this.beforeDays = beforeDays;
    }

    @Scheduled(fixedDelayString = "${reminder.interval-ms:1800000}", initialDelay = 30000)
    public void checkDeadlines() {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            return;
        }

        LocalDate today = LocalDate.now(zoneId);
        LocalDate inDays = today.plusDays(beforeDays);

        sendBatch(jda, deadlineRepository.findByDueDateAndNotifiedTodayFalse(today), true, today);
        sendBatch(jda, deadlineRepository.findByDueDateAndNotified3DayFalse(inDays), false, inDays);
    }

    private void sendBatch(JDA jda, List<Deadline> deadlines, boolean dueToday, LocalDate date) {
        for (Deadline deadline : deadlines) {
            if (send(jda, deadline, dueToday)) {
                if (dueToday) {
                    deadline.setNotifiedToday(true);
                } else {
                    deadline.setNotified3Day(true);
                }
                deadlineRepository.save(deadline);
            }
        }
    }

    private boolean send(JDA jda, Deadline deadline, boolean dueToday) {
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
            if (channel == null) {
                return false;
            }

            channel.sendMessage(format(deadline, dueToday)).queue();
            return true;
        } catch (Exception e) {
            log.warn("Failed to send reminder for deadline {}: {}", deadline.getId(), e.getMessage());
            return false;
        }
    }

    private String format(Deadline deadline, boolean dueToday) {
        String label = deadline.getTitle() + " (" + deadline.getDeadlineType() + ")";
        String date = deadline.getDueDate().format(DATE_FMT);
        return dueToday
                ? "\u26A0\uFE0F **Due today:** `" + label + "` \u2014 " + date
                : "\uD83D\uDCDA **Reminder:** `" + label + "` is due in " + beforeDays
                + " days (" + date + ")";
    }
}