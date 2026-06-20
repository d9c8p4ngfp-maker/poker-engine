package com.first.poker.config;

import com.first.poker.service.GameBroadcastHelper;
import com.first.poker.service.GameTimeoutScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfiguration {

    @Bean
    public GameTimeoutScheduler gameTimeoutScheduler(GameBroadcastHelper helper) {
        return new GameTimeoutScheduler((roomId, playerId) -> {
            helper.handleTimeout(roomId, playerId);
        });
    }
}
