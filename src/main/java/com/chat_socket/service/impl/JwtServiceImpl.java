package com.chat_socket.service.impl;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtServiceImpl implements JwtService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey accessTokenKey;
    private final long accessTokenTtl;

    public JwtServiceImpl(ApplicationYaml config) {
        this.accessTokenKey = Keys.hmacShaKeyFor(config.accessTokenSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = config.accessTokenTtl();
    }

    @Override
    public String generateToken(UUID userId) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(accessTokenTtl))))
                .signWith(accessTokenKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public String generateRefreshToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public UUID verifyAccessToken(String accessToken) {
        String subject = Jwts.parser()
                .verifyWith(accessTokenKey)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload()
                .getSubject();

        return UUID.fromString(subject);
    }
}
