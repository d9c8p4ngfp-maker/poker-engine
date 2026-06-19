package com.first.poker.config;

import com.first.poker.engine.GameAction;
import com.first.poker.service.GameDisconnectHandler;
import com.first.poker.service.GameSessionService;
import com.first.poker.service.GameTimeoutScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfiguration {

    @Bean
    public GameTimeoutScheduler gameTimeoutScheduler(GameSessionService gameSession) {
        return new GameTimeoutScheduler((roomId, playerId) -> {
            try {
                gameSession.applyAction(roomId, playerId, GameAction.FOLD, 0);
            } catch (Exception ignored) {
                // Timeout fold may fail if game is already over
            }
        });
    }
}
