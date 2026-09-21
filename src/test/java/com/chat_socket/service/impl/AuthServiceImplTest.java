package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.TestFixtures;
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
import com.chat_socket.service.JwtService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    private static final ApplicationYaml CONFIG = new ApplicationYaml("secret", 15, 14, List.of(), null, null);

    @Mock
    UserRepository userRepository;

    @Mock
    SessionRepository sessionRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    UserMapper userMapper;

    @Mock
    JwtService jwtService;

    AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new AuthServiceImpl(userRepository, sessionRepository, passwordEncoder, userMapper, jwtService, CONFIG);
    }

    private static MockHttpServletRequest requestWithRefreshCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", value));
        return request;
    }

    @Test
    void signUp_existingUsername_returns409WithoutSaving() {
        SignUpRequest request = new SignUpRequest("alice", "a@example.com", "pw", "A", "L");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        BaseResponse<String> response = service.signUp(request);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.message()).isEqualTo("User already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void signUp_newUser_hashesPasswordAndReturns204() {
        SignUpRequest request = new SignUpRequest("alice", "a@example.com", "pw", "A", "L");
        UserEntity entity = new UserEntity();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("pw")).thenReturn("hashed-pw");

        BaseResponse<String> response = service.signUp(request);

        assertThat(response.status()).isEqualTo(204);
        assertThat(entity.getHashedPassword()).isEqualTo("hashed-pw");
        verify(userRepository).save(entity);
    }

    @Test
    void signIn_unknownUsername_throwsSignInException() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signIn(new SignInRequest("alice", "pw"), new MockHttpServletResponse()))
                .isInstanceOf(SignInException.class)
                .hasMessage("Username or password incorrect!");
    }

    @Test
    void signIn_wrongPassword_throwsSignInException() {
        UserEntity user = TestFixtures.user(UUID.randomUUID());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.signIn(new SignInRequest("alice", "pw"), new MockHttpServletResponse()))
                .isInstanceOf(SignInException.class);
    }

    @Test
    void signIn_success_returnsAccessTokenStoresSessionAndSetsCookie() {
        UUID userId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(userId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);
        when(jwtService.generateToken(userId)).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");

        BaseResponse<AuthResponse> result = service.signIn(new SignInRequest("alice", "pw"), response);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data().accessToken()).isEqualTo("access-token");
        ArgumentCaptor<SessionEntity> captor = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("refresh-token");
        assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(13 * 24 * 3600));
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .contains("refreshToken=refresh-token")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=none")
                .contains("Max-Age=1209600");
    }

    @Test
    void signOut_withoutCookie_throwsUnauthorized() {
        assertThatThrownBy(() -> service.signOut(new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(UnAuthorizedException.class)
                .hasMessage("Token not found.");
    }

    @Test
    void signOut_withCookie_deletesSessionAndExpiresCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        BaseResponse<String> result = service.signOut(requestWithRefreshCookie("refresh-token"), response);

        assertThat(result.status()).isEqualTo(200);
        verify(sessionRepository).deleteByRefreshToken("refresh-token");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("refreshToken=;")
                .contains("Max-Age=0");
    }

    @Test
    void refresh_withoutCookie_throwsUnauthorized() {
        assertThatThrownBy(() -> service.refresh(new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(UnAuthorizedException.class);
    }

    @Test
    void refresh_unknownSession_throwsForbidden() {
        when(sessionRepository.findByRefreshToken("refresh-token")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.refresh(requestWithRefreshCookie("refresh-token"), new MockHttpServletResponse()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Token expired or invalid.");
    }

    @Test
    void refresh_expiredSession_deletesSessionClearsCookieAndThrowsForbidden() {
        UUID userId = UUID.randomUUID();
        SessionEntity session =
                new SessionEntity(userId, "refresh-token", Instant.now().minusSeconds(60));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sessionRepository.findByRefreshToken("refresh-token")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.refresh(requestWithRefreshCookie("refresh-token"), response))
                .isInstanceOf(ForbiddenException.class);
        verify(sessionRepository).deleteByRefreshToken("refresh-token");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void refresh_validSession_returnsNewAccessToken() {
        UUID userId = UUID.randomUUID();
        SessionEntity session =
                new SessionEntity(userId, "refresh-token", Instant.now().plusSeconds(3600));
        when(sessionRepository.findByRefreshToken("refresh-token")).thenReturn(Optional.of(session));
        when(jwtService.generateToken(userId)).thenReturn("new-access");

        BaseResponse<AuthResponse> result =
                service.refresh(requestWithRefreshCookie("refresh-token"), new MockHttpServletResponse());

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data().accessToken()).isEqualTo("new-access");
    }
}
