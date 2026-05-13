package com.chat_socket;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat-socket")
public record ApplicationYaml(String accessTokenSecret, long accessTokenTtl, long refreshTokenTtl) {}
