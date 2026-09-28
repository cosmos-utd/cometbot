package com.cometbot.config;

import com.cometbot.discord.BotListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;

@Configuration
public class JdaConfig {

    private static final Logger log = LoggerFactory.getLogger(JdaConfig.class);

    @Bean(destroyMethod = "shutdownNow")
    public JDA jda(@Value("${discord.token:}") String token, BotListener listener) {
        if (token == null || token.isBlank()) {
            log.warn("discord.token is not set - running in REST-only mode (no Discord bot)");
            return null;
        }

        try {
            return JDABuilder.createDefault(token)
                    .setEnabledIntents(EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    ))
                    .setActivity(net.dv8tion.jda.api.entities.Activity.playing("UT Dallas deadlines"))
                    .addEventListeners(listener)
                    .build();
        } catch (Exception e) {
            log.error("Failed to build JDA client: {}", e.getMessage());
            return null;
        }
    }
}