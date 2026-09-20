package com.chat_socket.service.impl;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.dto.AuthResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.SignInRequest;
import com.chat_socket.dto.SignUpRequest;
import com.chat_socket.entity.SessionEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.SignInException;
import com.chat_socket.exception.UnAuthorizedException;
import com.chat_socket.mapper.UserMapper;
import com.chat_socket.repository.SessionRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.AuthService;
import com.chat_socket.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final ApplicationYaml config;

    public AuthServiceImpl(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            JwtService jwtService,
            ApplicationYaml config) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.config = config;
    }

    @Override
    public BaseResponse<String> signUp(SignUpRequest request) {
        if (userRepository.existsByUsername(request.username()))
            return new BaseResponse<>(null, "User already exists", HttpStatus.CONFLICT.value());

        UserEntity newUser = userMapper.toEntity(request);
        newUser.setHashedPassword(passwordEncoder.encode(request.password()));
        userRepository.save(newUser);

        return new BaseResponse<>(null, null, HttpStatus.NO_CONTENT.value());
    }

    @Override
    public BaseResponse<AuthResponse> signIn(SignInRequest request, HttpServletResponse response) {
        UserEntity user = userRepository
                .findByUsername(request.username())
                .orElseThrow(() -> new SignInException("Username or password incorrect!"));

        if (!passwordEncoder.matches(request.password(), user.getHashedPassword()))
            throw new SignInException("Username or password incorrect!");

        String accessToken = jwtService.generateToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken();
        Instant refreshTokenExpiresAt = Instant.now().plus(Duration.ofDays(config.refreshTokenTtl()));
        sessionRepository.save(new SessionEntity(user.getId(), refreshToken, refreshTokenExpiresAt));

        addRefreshTokenCookie(response, refreshToken);

        return new BaseResponse<>(new AuthResponse(accessToken), "Login successful.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<String> signOut(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken =
                getRefreshTokenCookie(request).orElseThrow(() -> new UnAuthorizedException("Token not found."));
        sessionRepository.deleteByRefreshToken(refreshToken);
        clearRefreshTokenCookie(response);

        return new BaseResponse<>(null, "Logout successful.", HttpStatus.OK.value());
    }

    @Override
    public BaseResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken =
                getRefreshTokenCookie(request).orElseThrow(() -> new UnAuthorizedException("Token not found."));

        SessionEntity session = sessionRepository
                .findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ForbiddenException("Token expired or invalid."));

        if (session.getExpiresAt().isBefore(Instant.now())) {
            sessionRepository.deleteByRefreshToken(refreshToken);
            clearRefreshTokenCookie(response);
            throw new ForbiddenException("Token expired or invalid.");
        }

        String accessToken = jwtService.generateToken(session.getUserId());

        return new BaseResponse<>(
                new AuthResponse(accessToken), "Token refreshed successfully.", HttpStatus.OK.value());
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie(refreshToken, Duration.ofDays(config.refreshTokenTtl()))
                        .toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE, refreshTokenCookie("", Duration.ZERO).toString());
    }

    /** HttpOnly + Secure + SameSite=None so the browser sends it cross-site over HTTPS only. */
    private static ResponseCookie refreshTokenCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("none")
                .maxAge(maxAge)
                .build();
    }

    private static Optional<String> getRefreshTokenCookie(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, REFRESH_TOKEN_COOKIE_NAME))
                .map(Cookie::getValue);
    }
}
