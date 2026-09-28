package com.cometbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** "Today" for reminders, listings and AI extraction is always in the reminder time zone. */
    @Bean
    public Clock clock(@Value("${reminder.tz:America/Chicago}") String tz) {
        return Clock.system(ZoneId.of(tz));
    }
}
