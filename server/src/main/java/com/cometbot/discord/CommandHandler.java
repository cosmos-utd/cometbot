package com.cometbot.discord;

import com.cometbot.dto.DeadlineRequest;
import com.cometbot.model.Deadline;
import com.cometbot.service.DeadlineService;
import com.cometbot.service.SettingsService;
import com.cometbot.service.SyllabusService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class CommandHandler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy");

    private final SyllabusService syllabusService;
    private final DeadlineService deadlineService;
    private final SettingsService settingsService;
    private final String discordOwnerId;

    public CommandHandler(SyllabusService syllabusService,
                          DeadlineService deadlineService,
                          SettingsService settingsService,
                          @Value("${discord.owner-id:}") String discordOwnerId) {
        this.syllabusService = syllabusService;
        this.deadlineService = deadlineService;
        this.settingsService = settingsService;
        this.discordOwnerId = discordOwnerId;
    }

    public void handle(MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw().trim();
        if (content.isEmpty() || !content.startsWith("!")) {
            return;
        }

        String[] parts = content.split("\\s+", 2);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "!setsyllabus" -> handleSetSyllabus(event, parts);
            case "!scan" -> handleScan(event);
            case "!deadlines" -> handleDeadlines(event);
            case "!adddeadline" -> handleAddDeadline(event, parts);
            case "!editdeadline" -> handleEditDeadline(event, parts);
            case "!deletedeadline" -> handleDeleteDeadline(event, parts);
            case "!setchannel" -> handleSetChannel(event, parts);
            default -> { }
        }
    }

    // ---------- !setsyllabus ----------
    private void handleSetSyllabus(MessageReceivedEvent event, String[] parts) {
        if (!isModerator(event)) {
            reply(event, "❌ Only server owners and moderators can set the syllabus.");
            return;
        }
        String content = parts.length > 1 ? parts[1].trim() : "";
        if (content.isEmpty()) {
            reply(event, "❌ Usage: `!setsyllabus <paste your syllabus text>`");
            return;
        }

        reply(event, "📥 Saving syllabus and extracting deadlines...");
        try {
            List<Deadline> deadlines = syllabusService.setSyllabus(event.getGuild().getId(), content);
            reply(event, summary(deadlines));
        } catch (Exception e) {
            reply(event, "❌ Failed to process syllabus: " + e.getMessage());
        }
    }

    // ---------- !scan ----------
    private void handleScan(MessageReceivedEvent event) {
        if (!isModerator(event)) {
            reply(event, "❌ Only server owners and moderators can rescan the syllabus.");
            return;
        }
        try {
            List<Deadline> deadlines = syllabusService.rescan(event.getGuild().getId());
            reply(event, summary(deadlines));
        } catch (Exception e) {
            reply(event, "❌ Failed to rescan: " + e.getMessage());
        }
    }

    // ---------- !deadlines ----------
    private void handleDeadlines(MessageReceivedEvent event) {
        List<Deadline> deadlines = deadlineService.list(event.getGuild().getId());
        if (deadlines.isEmpty()) {
            reply(event, "📭 No deadlines yet. Use `!setsyllabus` to load them.");
            return;
        }
        StringBuilder sb = new StringBuilder("**Upcoming deadlines:**\n");
        for (Deadline d : deadlines) {
            sb.append("`#").append(d.getId()).append("` ")
                    .append(d.getTitle()).append(" (").append(d.getDeadlineType()).append(") ")
                    .append("\u2014 ").append(d.getDueDate().format(DATE_FMT)).append("\n");
        }
        reply(event, sb.toString());
    }

    // ---------- !adddeadline ----------
    private void handleAddDeadline(MessageReceivedEvent event, String[] parts) {
        if (!isOwner(event)) {
            reply(event, "❌ Only the server owner can add deadlines.");
            return;
        }
        String[] segments = (parts.length > 1 ? parts[1] : "").split("\\|");
        if (segments.length < 2) {
            reply(event, "❌ Usage: `!adddeadline Title | YYYY-MM-DD [| type]`");
            return;
        }
        String title = segments[0].trim();
        LocalDate dueDate = parseDate(segments[1].trim());
        String type = segments.length > 2 && !segments[2].isBlank()
                ? segments[2].trim().toLowerCase()
                : "assignment";
        if (title.isEmpty() || dueDate == null) {
            reply(event, "❌ Invalid title or date. Use `YYYY-MM-DD`.");
            return;
        }
        Deadline d = deadlineService.create(
                event.getGuild().getId(), title, type, dueDate, event.getAuthor().getId());
        reply(event, "✅ Added `#" + d.getId() + "` " + d.getTitle()
                + " (" + d.getDeadlineType() + ") \u2014 " + d.getDueDate().format(DATE_FMT));
    }

    // ---------- !editdeadline ----------
    private void handleEditDeadline(MessageReceivedEvent event, String[] parts) {
        if (!isOwner(event)) {
            reply(event, "❌ Only the server owner can edit deadlines.");
            return;
        }
        String[] tokens = (parts.length > 1 ? parts[1] : "").trim().split("\\s+", 3);
        if (tokens.length < 2) {
            reply(event, "❌ Usage: `!editdeadline <id> <title|type|date> <new value>`");
            return;
        }
        Long id = parseId(tokens[0]);
        String field = tokens[1].toLowerCase();
        String value = tokens.length > 2 ? tokens[2] : "";
        if (id == null || value.isBlank()) {
            reply(event, "❌ Invalid id or missing new value.");
            return;
        }

        String guildId = event.getGuild().getId();
        DeadlineRequest request;
        switch (field) {
            case "title" -> request = new DeadlineRequest(value, null, null);
            case "type" -> request = new DeadlineRequest(null, value, null);
            case "date" -> {
                LocalDate date = parseDate(value);
                if (date == null) {
                    reply(event, "❌ Invalid date. Use `YYYY-MM-DD`.");
                    return;
                }
                request = new DeadlineRequest(null, null, date);
            }
            default -> {
                reply(event, "❌ Field must be `title`, `type`, or `date`.");
                return;
            }
        }

        try {
            Deadline updated = deadlineService.update(
                    guildId, id, request, event.getAuthor().getId());
            reply(event, "✅ Updated `#" + updated.getId() + "` \u2014 "
                    + updated.getTitle() + " (" + updated.getDeadlineType() + ") \u2014 "
                    + updated.getDueDate().format(DATE_FMT));
        } catch (Exception e) {
            reply(event, "❌ " + e.getMessage());
        }
    }

    // ---------- !deletedeadline ----------
    private void handleDeleteDeadline(MessageReceivedEvent event, String[] parts) {
        if (!isOwner(event)) {
            reply(event, "❌ Only the server owner can delete deadlines.");
            return;
        }
        Long id = parseId(parts.length > 1 ? parts[1].trim() : "");
        if (id == null) {
            reply(event, "❌ Usage: `!deletedeadline <id>`");
            return;
        }
        try {
            deadlineService.delete(event.getGuild().getId(), id);
            reply(event, "🗑️ Deleted deadline `#" + id + "`.");
        } catch (Exception e) {
            reply(event, "❌ " + e.getMessage());
        }
    }

    // ---------- !setchannel ----------
    private void handleSetChannel(MessageReceivedEvent event, String[] parts) {
        if (!isOwner(event)) {
            reply(event, "❌ Only the server owner can set the reminder channel.");
            return;
        }
        GuildChannel channel = event.getMessage().getMentions().getChannels().stream().findFirst().orElse(null);
        if (channel == null) {
            reply(event, "❌ Mention a channel: `!setchannel #reminders`");
            return;
        }
        settingsService.setReminderChannel(event.getGuild().getId(), channel.getId());
        reply(event, "✅ Reminders will post to " + channel.getAsMention());
    }

    // ---------- helpers ----------
    private boolean isModerator(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        return member.isOwner()
                || member.hasPermission(Permission.MESSAGE_MANAGE)
                || member.hasPermission(Permission.MANAGE_CHANNEL);
    }

    private boolean isOwner(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        return member.isOwner() || event.getAuthor().getId().equals(discordOwnerId);
    }

    private String summary(List<Deadline> deadlines) {
        if (deadlines.isEmpty()) {
            return "✅ Saved syllabus. No deadlines detected (check the text or add them manually).";
        }
        StringBuilder sb = new StringBuilder("✅ Saved syllabus \u2014 extracted **")
                .append(deadlines.size()).append("** deadlines:\n");
        for (Deadline d : deadlines) {
            sb.append("`#").append(d.getId()).append("` ")
                    .append(d.getTitle()).append(" (").append(d.getDeadlineType()).append(") \u2014 ")
                    .append(d.getDueDate().format(DATE_FMT)).append("\n");
        }
        return sb.toString();
    }

    private LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void reply(MessageReceivedEvent event, String text) {
        Message message = event.getMessage();
        if (message.isFromGuild()) {
            message.reply(text).queue();
        } else {
            event.getChannel().sendMessage(text).queue();
        }
    }
}