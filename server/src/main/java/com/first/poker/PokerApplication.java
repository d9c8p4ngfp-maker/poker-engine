package com.first.poker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PokerApplication {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("[FATAL] Uncaught exception in thread " + t.getName() + ": " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.flush();
        });
        SpringApplication.run(PokerApplication.class, args);
    }
}
