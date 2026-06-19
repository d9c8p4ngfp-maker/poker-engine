package com.first.poker.config;

import com.first.poker.service.GameDisconnectHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;

public class SessionRegistrationInterceptor implements ChannelInterceptor {

    private ApplicationContext applicationContext;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && accessor.getCommand() != null
                && accessor.getCommand().name().equals("CONNECT")) {
            Principal user = accessor.getUser();
            if (user != null && applicationContext != null) {
                var handler = applicationContext.getBean(GameDisconnectHandler.class);
                handler.registerSession(accessor.getSessionId(), user.getName());
            }
        }
        return message;
    }

    public void setApplicationContext(ApplicationContext ctx) {
        this.applicationContext = ctx;
    }
}
