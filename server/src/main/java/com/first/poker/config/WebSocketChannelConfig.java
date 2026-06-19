package com.first.poker.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
public class WebSocketChannelConfig implements WebSocketMessageBrokerConfigurer {

    private final ApplicationContext applicationContext;

    public WebSocketChannelConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        var interceptor = new SessionRegistrationInterceptor();
        interceptor.setApplicationContext(applicationContext);
        registration.interceptors(interceptor);
    }
}
