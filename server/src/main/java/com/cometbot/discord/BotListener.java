package com.cometbot.discord;

import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class BotListener extends ListenerAdapter {

    private final CommandHandler commandHandler;
    // Commands can wait on the AI service for a minute; never block JDA's event thread.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public BotListener(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (!event.isFromGuild()) {
            return;
        }
        if (!event.getMessage().getContentRaw().stripLeading().startsWith("!")) {
            return;
        }
        executor.execute(() -> commandHandler.handle(event));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
