package com.first.poker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PokerApplication {
    private static final Logger log = LoggerFactory.getLogger(PokerApplication.class);

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("[FATAL] Uncaught exception in thread {}: {} - {}", t.getName(), e.getClass().getName(), e.getMessage(), e);
        });
        SpringApplication.run(PokerApplication.class, args);
    }
}
