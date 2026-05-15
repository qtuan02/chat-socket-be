package com.chat_socket.config;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.security.SocketChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ApplicationYaml applicationYaml;
    private final SocketChannelInterceptor socketChannelInterceptor;

    WebSocketConfig(ApplicationYaml applicationYaml, SocketChannelInterceptor socketChannelInterceptor) {
        this.applicationYaml = applicationYaml;
        this.socketChannelInterceptor = socketChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(applicationYaml.clientUrl());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(socketChannelInterceptor);
    }
}
