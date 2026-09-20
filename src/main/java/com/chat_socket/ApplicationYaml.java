package com.chat_socket;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat-socket")
public record ApplicationYaml(
        String accessTokenSecret,
        long accessTokenTtl,
        long refreshTokenTtl,
        List<String> clientUrl,
        String uploadDir,
        String publicUrl) {}
