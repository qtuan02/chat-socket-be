# Refactor Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dedupe and relocate helpers across the chat-socket Spring Boot service, remove dead code, fix one Redis bug, and put a unit + controller test suite around it — without changing any URL, JSON shape, or HTTP status.

**Architecture:** Keep the existing layering (`controller` → `service/impl` returning `BaseResponse<T>` → `repository`). Phase 1 pins current behaviour with characterization tests (Mockito, no Spring context). Phase 2 introduces tiny single-purpose helpers on the entity/repository/dto that own the data, then swaps callers. Phase 3 cleans security/socket and deletes `RedisUtils`. Phase 4 adds standalone-MockMvc controller tests.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Spring Data JPA, Spring Security, Spring WebSocket (STOMP), MapStruct, Lombok, JUnit 6.0.3, Mockito 5.20, AssertJ 3.27, spring-test MockMvc (standalone). Formatting: Spotless + palantir-java-format (runs in `compile` phase — unformatted code fails the build).

**Spec:** `docs/superpowers/specs/2026-09-20-refactor-cleanup-design.md`

## Global Constraints

- No new Maven dependencies. Test deps available: `spring-boot-starter-webmvc-test` (JUnit 6, Mockito, AssertJ, spring-test, spring-boot-test). There is **no** `spring-security-test` and **no** Testcontainers.
- No behaviour change visible to clients: same URLs, same JSON field names, same HTTP status codes. Error message text is kept verbatim except two cases, both same-status 404 text-only changes from consolidating direct-conversation lookup into `ConversationService.findOrCreateDirectConversation` (Task 12): `POST /v1/conversation` DIRECT with unknown member ("Member not found." → "User not found."), and `POST /v1/message/direct` with unknown recipient and no `conversationId` ("Recipient not found." → "User not found.", introduced when Task 13 made `MessageServiceImpl` delegate to the same method). Final review ratified the second case; see ledger.
- Keep `BaseResponse<T>` returned from services and keep `service/X` + `service/impl/XImpl`.
- Keep `constant/*` as interfaces.
- Do **not** touch the four uncommitted files: `src/main/java/com/chat_socket/ApplicationYaml.java`, `config/SecurityServerConfig.java`, `config/WebSocketConfig.java`, `src/main/resources/application.yaml`. Never `git add -A`; add files by name.
- Branch: `refactor/cleanup` (already exists, based on `dev`).
- Every commit must pass: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"` (PowerShell; on bash use `./mvnw`). `ChatSocketApplicationTests` needs Postgres + Redis and is excluded.
- Run tests for a single class with: `.\mvnw.cmd -q spotless:apply test "-Dtest=NormalizeTest" "-Dsurefire.failIfNoSpecifiedTests=false"`.
- Mockito runs with **strict stubs** (`@ExtendWith(MockitoExtension.class)`). An unused `when(...)` fails the test with `UnnecessaryStubbingException` — delete the stub rather than adding `lenient()`.
- Jackson on the classpath is Jackson 2 (`com.fasterxml.jackson`) **without** `jackson-datatype-jsr310`. In controller tests never put a `LocalDateTime`-bearing DTO in `BaseResponse.data`; use `null` or date-free DTOs.
- A helper is only created when it does one thing and has ≥ 2 call sites. Do not add helpers the plan does not name.
- The spec lists 4 commits (one per phase); this plan commits once per task (17 commits) so each task is independently reviewable. Same four phases, finer grain.
- Commit messages end with `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>` (append it to every `git commit -m` shown below).
- Ponytail: prefer deleting to adding. Mark any deliberate ceiling with a `// ponytail:` comment naming the upgrade path.

## Review Focus

Inputs the spec implies but that were not covered by an obvious test; each has been pinned to the owning task below:

1. `UserPair.of(a, b)` must give the same order regardless of argument order — otherwise friendship lookups silently miss (pinned in Task 10 `UserPairTest`).
2. `SecurityFilter` must **skip** `/api/v1/auth/**`, `/api/health-check`, `/api/ws` and OPTIONS, but **not** `/api/v1/user/...` — a wrong `shouldNotFilter` locks everyone out or opens everything (Task 8 `SecurityFilterTest`).
3. Pagination `limit` above 100 is capped, `limit` ≤ 0 is a 400, an unparsable `cursor` is a 400 with message "Cursor is invalid." (Task 1 `PaginationUtilsTest`).
4. `SocketPublisher` must **defer** emits until `afterCommit` when a transaction is active and emit **immediately** when none is (Task 9 `SocketPublisherTest`).
5. `clearOnlineUsers` at startup must actually delete the per-user session keys (glob bug) (Task 16 `UserOnlineRegistryTest`).

## File Structure

**Created (tests):**
- `src/test/java/com/chat_socket/TestFixtures.java` — builders for `UserEntity`, `ConversationEntity`, `ParticipantEntity`, `MessageEntity`, `FriendEntity`, `FriendRequestEntity`, plus `authenticateAs(UUID)`.
- `src/test/java/com/chat_socket/utils/{NormalizeTest,PaginationUtilsTest,SecurityTest}.java`
- `src/test/java/com/chat_socket/dto/UserPairTest.java`
- `src/test/java/com/chat_socket/repository/{FriendRepositoryDefaultsTest,ParticipantRepositoryDefaultsTest}.java`
- `src/test/java/com/chat_socket/service/impl/{Auth,Conversation,Friend,Jwt,Message,User}ServiceImplTest.java`
- `src/test/java/com/chat_socket/security/{SecurityFilter,SocketChannelInterceptor,GroupPermission,MessageDirectPermission,MessageGroupPermission}Test.java`
- `src/test/java/com/chat_socket/socket/{SocketPublisher,UserOnlineRegistry}Test.java`
- `src/test/java/com/chat_socket/controller/{Auth,Conversation,Friend,Message,User}ControllerTest.java`

**Modified (main):**
- `entity/ParticipantEntity.java` (+`isActive()`), `entity/FriendEntity.java` (+`otherUser(UUID)`), `dto/UserPair.java` (+`of(UUID,UUID)`)
- `repository/FriendRepository.java` (+`existsFriendship`), `repository/ParticipantRepository.java` (+`findActiveParticipant`)
- `utils/Normalize.java` (−`normalizeUserPair`), `utils/Security.java` (+`extractBearerToken`, −`getUserIdFromAccessToken`), `utils/PaginationUtils.java` (tidy)
- `service/JwtService.java`, `service/impl/JwtServiceImpl.java` (`Optional<UUID>`), `service/ConversationService.java` (+`findOrCreateDirectConversation`)
- `service/impl/{Auth,Conversation,Friend,Message,User}ServiceImpl.java`
- `security/{SecurityFilter,SocketChannelInterceptor,GroupPermission,MessageDirectPermission,MessageGroupPermission}.java`
- `socket/{UserOnlineRegistry,SocketEventListener,SocketController}.java`, `constant/SocketChannel.java`
- `controller/MessageController.java` (−no-op `@PreAuthorize`)

**Deleted (main):** `utils/RedisUtils.java`

---

### Task 1: Test fixtures + utils characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/TestFixtures.java`
- Create: `src/test/java/com/chat_socket/utils/NormalizeTest.java`
- Create: `src/test/java/com/chat_socket/utils/PaginationUtilsTest.java`

**Interfaces:**
- Produces: `TestFixtures.authenticateAs(UUID) → UserSecurity`, `TestFixtures.user(UUID) → UserEntity`, `TestFixtures.conversation(UUID, ConversationType) → ConversationEntity`, `TestFixtures.participant(ConversationEntity, UserEntity, ParticipantRole) → ParticipantEntity`, `TestFixtures.message(UUID, ConversationEntity, UserEntity) → MessageEntity`, `TestFixtures.friendship(UserEntity, UserEntity) → FriendEntity`, `TestFixtures.friendRequest(UUID, UserEntity, UserEntity, FriendRequestStatus) → FriendRequestEntity`. Every later test uses these.

- [ ] **Step 1: Write `TestFixtures`**

```java
package com.chat_socket;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.FriendEntity;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.enums.ParticipantRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Builders for entities and the security context. Tests that call authenticateAs must clearContext in @AfterEach. */
public final class TestFixtures {
    public static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

    private TestFixtures() {}

    public static UserSecurity authenticateAs(UUID userId) {
        UserSecurity user = new UserSecurity(userId, "user-" + userId, "First", "Last", userId + "@example.com", null);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return user;
    }

    public static UserEntity user(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setFirstName("First");
        user.setLastName("Last");
        user.setEmail(id + "@example.com");
        user.setHashedPassword("hashed");
        return user;
    }

    public static ConversationEntity conversation(UUID id, ConversationType type) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(id);
        conversation.setType(type);
        conversation.setLastMessageAt(FIXED_TIME);
        return conversation;
    }

    public static ParticipantEntity participant(ConversationEntity conversation, UserEntity user, ParticipantRole role) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(new ParticipantIdEntity(conversation.getId(), user.getId()));
        participant.setConversation(conversation);
        participant.setUser(user);
        participant.setRole(role);
        return participant;
    }

    public static MessageEntity message(UUID id, ConversationEntity conversation, UserEntity sender) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent("hello");
        message.setCreatedAt(FIXED_TIME);
        return message;
    }

    public static FriendEntity friendship(UserEntity userA, UserEntity userB) {
        FriendEntity friendship = new FriendEntity();
        friendship.setId(UUID.randomUUID());
        friendship.setUserA(userA);
        friendship.setUserB(userB);
        return friendship;
    }

    public static FriendRequestEntity friendRequest(
            UUID id, UserEntity fromUser, UserEntity toUser, FriendRequestStatus status) {
        FriendRequestEntity request = new FriendRequestEntity();
        request.setId(id);
        request.setFromUser(fromUser);
        request.setToUser(toUser);
        request.setStatus(status);
        return request;
    }
}
```

- [ ] **Step 2: Write `NormalizeTest`**

```java
package com.chat_socket.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.chat_socket.dto.UserPair;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NormalizeTest {

    @Test
    void normalizeUserPair_ordersBySmallerUuidStringFirst() {
        UUID small = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID big = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(Normalize.normalizeUserPair(big, small)).isEqualTo(new UserPair(small, big));
        assertThat(Normalize.normalizeUserPair(small, big)).isEqualTo(new UserPair(small, big));
    }

    @Test
    void normalizeSearchText_stripsAccentsSpacesAndLowercases() {
        assertThat(Normalize.normalizeSearchText("  Nguyễn Văn Đức ")).isEqualTo("nguyenvanduc");
        assertThat(Normalize.normalizeSearchText("Đình")).isEqualTo("dinh");
    }

    @Test
    void normalizeSearchText_nullOrBlank_returnsEmpty() {
        assertThat(Normalize.normalizeSearchText(null)).isEmpty();
        assertThat(Normalize.normalizeSearchText("   ")).isEmpty();
    }

    @Test
    void normalizeFullName_joinsAndNormalizes() {
        assertThat(Normalize.normalizeFullName("Trần", " Thị  Hoa ")).isEqualTo("tranthihoa");
        assertThat(Normalize.normalizeFullName(null, "Hoa")).isEqualTo("hoa");
    }

    @Test
    void normalizeTextPattern_escapesLikeWildcardsAndWraps() {
        assertThat(Normalize.normalizeTextPattern("a%b_c\\d")).isEqualTo("%a\\%b\\_c\\\\d%");
    }

    @Test
    void normalizeTextPattern_blank_returnsNull() {
        assertThat(Normalize.normalizeTextPattern(null)).isNull();
        assertThat(Normalize.normalizeTextPattern("  ")).isNull();
    }

    @Test
    void normalizeUsernamePattern_lowercasesTrimsAndKeepsSpaces() {
        assertThat(Normalize.normalizeUsernamePattern("  AbC d ")).isEqualTo("%abc d%");
        assertThat(Normalize.normalizeUsernamePattern("  ")).isNull();
    }
}
```

- [ ] **Step 3: Write `PaginationUtilsTest`**

```java
package com.chat_socket.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.exception.BadRequestException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PaginationUtilsTest {

    @Test
    void resolveCursorPage_nullRequest_usesDefaultLimitAndFetchesOneExtra() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(null);

        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.cursor()).isNull();
        assertThat(page.pageRequest().getPageSize()).isEqualTo(51);
    }

    @Test
    void resolveCursorPage_limitAboveMax_isCappedAt100() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(new PaginationRequest(500, null, null));

        assertThat(page.limit()).isEqualTo(100);
    }

    @Test
    void resolveCursorPage_limitZero_throwsBadRequest() {
        assertThatThrownBy(() -> PaginationUtils.resolveCursorPage(new PaginationRequest(0, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Limit must be greater than 0.");
    }

    @Test
    void resolveCursorPage_parsesLocalAndOffsetCursor() {
        assertThat(PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T12:00:00", null))
                        .cursor())
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
        assertThat(PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T12:00:00Z", null))
                        .cursor())
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
    }

    @Test
    void resolveCursorPage_invalidCursor_throwsBadRequest() {
        assertThatThrownBy(() -> PaginationUtils.resolveCursorPage(new PaginationRequest(10, "yesterday", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cursor is invalid.");
    }

    @Test
    void toCursorResponse_moreThanLimit_trimsAndSetsNextCursorFromLastKeptItem() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(new PaginationRequest(2, null, null));
        List<LocalDateTime> fetched = List.of(
                LocalDateTime.of(2026, 1, 3, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0));

        PaginationResponse<String> response =
                PaginationUtils.toCursorResponse(fetched, page, LocalDateTime::toString, Function.identity(), false);

        assertThat(response.messages()).containsExactly("2026-01-03T00:00", "2026-01-02T00:00");
        assertThat(response.nextCursor()).isEqualTo("2026-01-02T00:00:00");
        assertThat(response.nextOffset()).isNull();
    }

    @Test
    void toCursorResponse_reverseItems_reversesOrderAndKeepsCursorFromNewestFetchOrder() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(new PaginationRequest(5, null, null));
        List<LocalDateTime> fetched = List.of(LocalDateTime.of(2026, 1, 2, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));

        PaginationResponse<String> response =
                PaginationUtils.toCursorResponse(fetched, page, LocalDateTime::toString, Function.identity(), true);

        assertThat(response.messages()).containsExactly("2026-01-01T00:00", "2026-01-02T00:00");
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void resolveOffsetPage_negativeOffset_throwsBadRequest() {
        assertThatThrownBy(() -> PaginationUtils.resolveOffsetPage(new PaginationRequest(10, null, -1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Offset must be greater than or equal to 0.");
    }

    @Test
    void resolveOffsetPage_buildsPageableWithOffsetAndLimitPlusOne() {
        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(new PaginationRequest(10, null, 20));

        assertThat(page.offset()).isEqualTo(20);
        assertThat(page.limit()).isEqualTo(10);
        assertThat(page.pageRequest().getOffset()).isEqualTo(20);
        assertThat(page.pageRequest().getPageSize()).isEqualTo(11);
    }

    @Test
    void toOffsetResponse_moreThanLimit_setsNextOffset() {
        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(new PaginationRequest(2, null, 4));

        PaginationResponse<Integer> response = PaginationUtils.toOffsetResponse(List.of(1, 2, 3), page, i -> i * 10);

        assertThat(response.messages()).containsExactly(10, 20);
        assertThat(response.nextOffset()).isEqualTo(6);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void toOffsetResponse_exactlyLimit_noNextOffset() {
        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(new PaginationRequest(2, null, 0));

        PaginationResponse<Integer> response = PaginationUtils.toOffsetResponse(List.of(1, 2), page, i -> i);

        assertThat(response.nextOffset()).isNull();
    }
}
```

- [ ] **Step 4: Run the two test classes**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=NormalizeTest,PaginationUtilsTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: BUILD SUCCESS, 18 tests pass. If `normalizeTextPattern_escapesLikeWildcardsAndWraps` fails, the expected string is wrong in the test, not the code — fix the test to match what `Normalize` produces (this is a characterization test).

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/chat_socket/TestFixtures.java src/test/java/com/chat_socket/utils/NormalizeTest.java src/test/java/com/chat_socket/utils/PaginationUtilsTest.java
git commit -m "test: characterization tests for Normalize and PaginationUtils"
```

### Task 2: JwtServiceImpl + Security utils characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/service/impl/JwtServiceImplTest.java`
- Create: `src/test/java/com/chat_socket/utils/SecurityTest.java`

**Interfaces:**
- Consumes: `JwtServiceImpl(ApplicationYaml)`, `JwtService.verifyAccessToken(String) → UUID` (throws `io.jsonwebtoken.JwtException` on bad token — Task 15 changes this to `Optional<UUID>` and updates this test).
- Consumes: `TestFixtures.authenticateAs`, `TestFixtures.user`.

- [ ] **Step 1: Write `JwtServiceImplTest`**

```java
package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.ApplicationYaml;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceImplTest {
    // HS256 needs >= 32 bytes; 64 hex chars = 64 bytes as UTF-8
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private final JwtServiceImpl service = new JwtServiceImpl(new ApplicationYaml(SECRET, 15, 14, List.of()));

    @Test
    void generateToken_thenVerify_returnsSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = service.generateToken(userId);

        assertThat(service.verifyAccessToken(token)).isEqualTo(userId);
    }

    @Test
    void verifyAccessToken_tamperedToken_throwsJwtException() {
        String token = service.generateToken(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.verifyAccessToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void verifyAccessToken_garbage_throws() {
        assertThatThrownBy(() -> service.verifyAccessToken("not-a-jwt"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    void generateRefreshToken_is128HexCharsAndRandom() {
        String first = service.generateRefreshToken();
        String second = service.generateRefreshToken();

        assertThat(first).hasSize(128).matches("[0-9a-f]+");
        assertThat(first).isNotEqualTo(second);
    }
}
```

- [ ] **Step 2: Write `SecurityTest`**

```java
package com.chat_socket.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsPrincipalFromContext() {
        UUID userId = UUID.randomUUID();
        UserSecurity expected = TestFixtures.authenticateAs(userId);

        assertThat(Security.getCurrentUser()).isEqualTo(expected);
    }

    @Test
    void getCurrentUser_noAuthentication_throwsIllegalState() {
        assertThatThrownBy(Security::getCurrentUser)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Current user is not authenticated.");
    }

    @Test
    void getUserAuthentication_wrapsUserFieldsIntoUserSecurity() {
        UserEntity user = TestFixtures.user(UUID.randomUUID());
        user.setAvatarUrl("http://avatar");

        UsernamePasswordAuthenticationToken authentication = Security.getUserAuthentication(user);

        assertThat(authentication.getPrincipal())
                .isEqualTo(new UserSecurity(
                        user.getId(), user.getUsername(), "First", "Last", user.getEmail(), "http://avatar"));
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void getUserSecurityFromPrincipal_returnsUserForAuthenticationWithUserSecurityPrincipal() {
        UserEntity user = TestFixtures.user(UUID.randomUUID());
        Principal principal = Security.getUserAuthentication(user);

        UserSecurity result = Security.getUserSecurityFromPrincipal(principal);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(user.getId());
    }

    @Test
    void getUserSecurityFromPrincipal_otherPrincipal_returnsNull() {
        Principal plain = () -> "someone";

        assertThat(Security.getUserSecurityFromPrincipal(plain)).isNull();
        assertThat(Security.getUserSecurityFromPrincipal(null)).isNull();
    }
}
```

- [ ] **Step 3: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=JwtServiceImplTest,SecurityTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 9 tests pass.

- [ ] **Step 4: Commit**

```powershell
git add src/test/java/com/chat_socket/service/impl/JwtServiceImplTest.java src/test/java/com/chat_socket/utils/SecurityTest.java
git commit -m "test: characterization tests for JwtServiceImpl and Security utils"
```

---

### Task 3: AuthServiceImpl characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/service/impl/AuthServiceImplTest.java`

**Interfaces:**
- Consumes: `AuthServiceImpl(UserRepository, SessionRepository, PasswordEncoder, UserMapper, JwtService, ApplicationYaml)`; cookie name `refreshToken`.

- [ ] **Step 1: Write the test**

```java
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
    private static final ApplicationYaml CONFIG = new ApplicationYaml("secret", 15, 14, List.of());

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
        service = new AuthServiceImpl(userRepository, sessionRepository, passwordEncoder, userMapper, jwtService, CONFIG);
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
                .contains("SameSite=None")
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
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("refreshToken=;").contains("Max-Age=0");
    }

    @Test
    void refresh_withoutCookie_throwsUnauthorized() {
        assertThatThrownBy(() -> service.refresh(new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(UnAuthorizedException.class);
    }

    @Test
    void refresh_unknownSession_throwsForbidden() {
        when(sessionRepository.findByRefreshToken("refresh-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.refresh(requestWithRefreshCookie("refresh-token"), new MockHttpServletResponse()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Token expired or invalid.");
    }

    @Test
    void refresh_expiredSession_deletesSessionClearsCookieAndThrowsForbidden() {
        UUID userId = UUID.randomUUID();
        SessionEntity session = new SessionEntity(userId, "refresh-token", Instant.now().minusSeconds(60));
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
        SessionEntity session = new SessionEntity(userId, "refresh-token", Instant.now().plusSeconds(3600));
        when(sessionRepository.findByRefreshToken("refresh-token")).thenReturn(Optional.of(session));
        when(jwtService.generateToken(userId)).thenReturn("new-access");

        BaseResponse<AuthResponse> result =
                service.refresh(requestWithRefreshCookie("refresh-token"), new MockHttpServletResponse());

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data().accessToken()).isEqualTo("new-access");
    }
}
```

- [ ] **Step 2: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=AuthServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 11 tests pass.

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/chat_socket/service/impl/AuthServiceImplTest.java
git commit -m "test: characterization tests for AuthServiceImpl"
```

---

### Task 4: FriendServiceImpl characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/service/impl/FriendServiceImplTest.java`

**Interfaces:**
- Consumes: `FriendServiceImpl(UserRepository, FriendRepository, FriendRequestRepository, FriendMapper, FriendRequestMapper)`. Stubs `friendRepository.existsByUserAIdAndUserBId(min, max)` and `deleteByUserAIdAndUserBId(min, max)` — Task 11 switches these stubs to `existsFriendship`/`UserPair.of`.

- [ ] **Step 1: Write the test**

```java
package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendDto;
import com.chat_socket.dto.FriendRequestReceviedDto;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendRequestSentDto;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.entity.FriendEntity;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.FriendMapper;
import com.chat_socket.mapper.FriendRequestMapper;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.FriendRequestRepository;
import com.chat_socket.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {
    // Fixed ids so that SMALL < BIG as strings; UserPair ordering is deterministic in tests
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    UserRepository userRepository;

    @Mock
    FriendRepository friendRepository;

    @Mock
    FriendRequestRepository friendRequestRepository;

    @Mock
    FriendMapper friendMapper;

    @Mock
    FriendRequestMapper friendRequestMapper;

    FriendServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FriendServiceImpl(
                userRepository, friendRepository, friendRequestRepository, friendMapper, friendRequestMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getListFriend_mapsTheOtherUserOfEachFriendship() {
        TestFixtures.authenticateAs(BIG);
        UserEntity me = TestFixtures.user(BIG);
        UserEntity friend = TestFixtures.user(SMALL);
        FriendEntity friendship = TestFixtures.friendship(friend, me);
        FriendDto dto = new FriendDto(SMALL, "u", "F", "L", null, null);
        when(friendRepository.findFriendshipsOfUser(eq(BIG), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(friendship));
        when(friendMapper.toFriendDto(friend)).thenReturn(dto);

        BaseResponse<PaginationResponse<FriendDto>> response = service.getListFriend(null, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).containsExactly(dto);
        assertThat(response.data().nextOffset()).isNull();
    }

    @Test
    void getListFriendRequest_splitsSentAndReceived() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity sent = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        FriendRequestEntity received = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(SMALL), TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        FriendRequestSentDto sentDto = new FriendRequestSentDto(sent.getId(), BIG, null, null, null, null);
        FriendRequestReceviedDto receivedDto =
                new FriendRequestReceviedDto(received.getId(), BIG, null, null, null, null);
        when(friendRequestRepository.findFriendRequestsSentOfUser(BIG, FriendRequestStatus.PENDING))
                .thenReturn(List.of(sent));
        when(friendRequestRepository.findFriendRequestsReceivedOfUser(BIG, FriendRequestStatus.PENDING))
                .thenReturn(List.of(received));
        when(friendRequestMapper.toSentDto(sent)).thenReturn(sentDto);
        when(friendRequestMapper.toReceivedDto(received)).thenReturn(receivedDto);

        BaseResponse<FriendRequestResponse> response = service.getListFriendRequest();

        assertThat(response.data().sentRequests()).containsExactly(sentDto);
        assertThat(response.data().receivedRequests()).containsExactly(receivedDto);
    }

    @Test
    void sendFriendRequest_toSelf_returns400() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<String> response = service.sendFriendRequest(new FriendSendRequest(BIG, null));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("You cannot send a friend request to yourself.");
    }

    @Test
    void sendFriendRequest_alreadyFriends_returns409() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);

        BaseResponse<String> response = service.sendFriendRequest(new FriendSendRequest(SMALL, null));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.message()).isEqualTo("You are already friends.");
    }

    @Test
    void sendFriendRequest_pendingExists_returns409() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);
        when(friendRequestRepository.existsBetweenUsersWithStatus(BIG, SMALL, FriendRequestStatus.PENDING))
                .thenReturn(true);

        BaseResponse<String> response = service.sendFriendRequest(new FriendSendRequest(SMALL, null));

        assertThat(response.status()).isEqualTo(409);
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    void sendFriendRequest_success_savesPendingRequestAndReturns201() {
        TestFixtures.authenticateAs(BIG);
        UserEntity from = TestFixtures.user(BIG);
        UserEntity to = TestFixtures.user(SMALL);
        FriendSendRequest request = new FriendSendRequest(SMALL, "hi");
        FriendRequestEntity entity = new FriendRequestEntity();
        when(userRepository.findById(BIG)).thenReturn(Optional.of(from));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(to));
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);
        when(friendRequestRepository.existsBetweenUsersWithStatus(BIG, SMALL, FriendRequestStatus.PENDING))
                .thenReturn(false);
        when(friendRequestMapper.toEntity(request)).thenReturn(entity);

        BaseResponse<String> response = service.sendFriendRequest(request);

        assertThat(response.status()).isEqualTo(201);
        assertThat(entity.getFromUser()).isSameAs(from);
        assertThat(entity.getToUser()).isSameAs(to);
        assertThat(entity.getStatus()).isEqualTo(FriendRequestStatus.PENDING);
        verify(friendRequestRepository).save(entity);
    }

    @Test
    void acceptFriendRequest_unknownRequest_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        UUID requestId = UUID.randomUUID();
        when(friendRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptFriendRequest(new FriendActionRequest(requestId)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Friend request not found.");
    }

    @Test
    void acceptFriendRequest_notRecipient_returns403() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<AcceptFriendResponse> response =
                service.acceptFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(403);
        verify(friendRepository, never()).save(any());
    }

    @Test
    void acceptFriendRequest_success_createsFriendshipMarksAcceptedAndReturns201() {
        TestFixtures.authenticateAs(BIG);
        UserEntity from = TestFixtures.user(SMALL);
        UserEntity to = TestFixtures.user(BIG);
        FriendRequestEntity request =
                TestFixtures.friendRequest(UUID.randomUUID(), from, to, FriendRequestStatus.PENDING);
        AcceptFriendResponse dto = new AcceptFriendResponse(SMALL, "F", "L", null);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(from));
        when(friendMapper.toAcceptFriendResponse(from)).thenReturn(dto);

        BaseResponse<AcceptFriendResponse> response =
                service.acceptFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        ArgumentCaptor<FriendEntity> captor = ArgumentCaptor.forClass(FriendEntity.class);
        verify(friendRepository).save(captor.capture());
        assertThat(captor.getValue().getUserA()).isSameAs(from);
        assertThat(captor.getValue().getUserB()).isSameAs(to);
        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        verify(friendRequestRepository).save(request);
    }

    @Test
    void declineFriendRequest_notRecipient_returns403() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.declineFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void declineFriendRequest_success_marksRejectedAndReturns204() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(SMALL), TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.declineFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(204);
        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.REJECTED);
        verify(friendRequestRepository).save(request);
    }

    @Test
    void cancelFriendRequest_notSender_returns403() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(SMALL), TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.cancelFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(403);
        verify(friendRequestRepository, never()).delete(any());
    }

    @Test
    void cancelFriendRequest_success_deletesAndReturns204() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.cancelFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(204);
        verify(friendRequestRepository).delete(request);
    }

    @Test
    void deleteFriend_self_returns404() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<String> response = service.deleteFriend(BIG);

        assertThat(response.status()).isEqualTo(404);
        verify(friendRepository, never()).deleteByUserAIdAndUserBId(any(), any());
    }

    @Test
    void deleteFriend_nothingDeleted_returns404() {
        TestFixtures.authenticateAs(BIG);
        when(friendRepository.deleteByUserAIdAndUserBId(SMALL, BIG)).thenReturn(0L);

        BaseResponse<String> response = service.deleteFriend(SMALL);

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void deleteFriend_deleted_returns204UsingNormalizedPair() {
        TestFixtures.authenticateAs(BIG);
        when(friendRepository.deleteByUserAIdAndUserBId(SMALL, BIG)).thenReturn(1L);

        BaseResponse<String> response = service.deleteFriend(SMALL);

        assertThat(response.status()).isEqualTo(204);
    }
}
```

- [ ] **Step 2: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=FriendServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 16 tests pass.

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/chat_socket/service/impl/FriendServiceImplTest.java
git commit -m "test: characterization tests for FriendServiceImpl"
```

---

### Task 5: UserServiceImpl characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/service/impl/UserServiceImplTest.java`

**Interfaces:**
- Consumes: `UserServiceImpl(UserRepository, FriendRepository, FriendRequestRepository, UserMapper)`. Stubs `friendRepository.existsByUserAIdAndUserBId(min, max)` — Task 11 switches to `existsFriendship`.

- [ ] **Step 1: Write the test**

```java
package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserInfoDto;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.dto.UserSearchDto;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.enums.FriendStatus;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.UserMapper;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.FriendRequestRepository;
import com.chat_socket.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UpdateUserRequest EMPTY_UPDATE =
            new UpdateUserRequest(null, null, null, null, null, null, null, null);

    @Mock
    UserRepository userRepository;

    @Mock
    FriendRepository friendRepository;

    @Mock
    FriendRequestRepository friendRequestRepository;

    @Mock
    UserMapper userMapper;

    UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, friendRepository, friendRequestRepository, userMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserProfile_unknownUser_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.empty());

        assertThatThrownBy(service::getUserProfile).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getUserProfile_returnsMappedProfile() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        UserProfileDto dto = new UserProfileDto(BIG, "u", "F", "L", "e", null, null, null);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userMapper.toUserProfileDto(user)).thenReturn(dto);

        BaseResponse<UserProfileDto> response = service.getUserProfile();

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data()).isEqualTo(dto);
    }

    @Test
    void updateUserProfile_usernameTaken_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameAndIdNot("taken", BIG)).thenReturn(true);

        assertThatThrownBy(() -> service.updateUserProfile(
                        new UpdateUserRequest(" taken ", null, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void updateUserProfile_blankFirstName_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));

        assertThatThrownBy(() -> service.updateUserProfile(
                        new UpdateUserRequest(null, null, "   ", null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("First name is required");
    }

    @Test
    void updateUserProfile_trimsTextAndBlankOptionalBecomesNull() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        user.setBio("old bio");
        UserProfileDto dto = new UserProfileDto(BIG, "u", "F", "L", "e", null, null, null);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toUserProfileDto(user)).thenReturn(dto);

        BaseResponse<UserProfileDto> response = service.updateUserProfile(
                new UpdateUserRequest(null, null, " Anna ", null, null, null, "   ", " 0123 "));

        assertThat(response.status()).isEqualTo(200);
        assertThat(user.getFirstName()).isEqualTo("Anna");
        assertThat(user.getBio()).isNull();
        assertThat(user.getPhone()).isEqualTo("0123");
        assertThat(user.getUsername()).isEqualTo("user-" + BIG);
    }

    @Test
    void updateUserProfile_emptyRequest_savesUnchangedUser() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toUserProfileDto(user)).thenReturn(null);

        service.updateUserProfile(EMPTY_UPDATE);

        verify(userRepository).save(user);
    }

    @Test
    void getUserInfo_self_returnsStatusSelf() {
        TestFixtures.authenticateAs(BIG);
        UserEntity me = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));

        service.getUserInfo(BIG);

        verify(userMapper).toUserInfoDto(me, FriendStatus.SELF);
    }

    @Test
    void getUserInfo_friend_returnsStatusFriend() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of());
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.FRIEND);
    }

    @Test
    void getUserInfo_pendingRequestSentByMe_returnsStatusSent() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        FriendRequestEntity pending = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), other, FriendRequestStatus.PENDING);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.SENT);
    }

    @Test
    void getUserInfo_pendingRequestReceived_returnsStatusReceived() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        FriendRequestEntity pending = TestFixtures.friendRequest(
                UUID.randomUUID(), other, TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.RECEIVED);
    }

    @Test
    void getUserInfo_noRelation_returnsStatusNone() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of());
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.NONE);
    }

    @Test
    void searchUsers_blankSearch_returnsEmptyWithoutQuerying() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<PaginationResponse<UserSearchDto>> response = service.searchUsers(null, "   ");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).isEmpty();
        assertThat(response.data().nextOffset()).isNull();
    }

    @Test
    void searchUsers_resolvesStatusAndPendingRequestIdPerUser() {
        TestFixtures.authenticateAs(BIG);
        UserEntity friend = TestFixtures.user(SMALL);
        UUID strangerId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UserEntity stranger = TestFixtures.user(strangerId);
        FriendRequestEntity pending =
                TestFixtures.friendRequest(UUID.randomUUID(), stranger, TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(userRepository.searchUsers(eq("%bob%"), eq("%bob%"), any(Pageable.class)))
                .thenReturn(List.of(friend, stranger));
        when(friendRepository.findFriendshipsBetweenUserAndUsers(BIG, List.of(SMALL, strangerId)))
                .thenReturn(List.of(TestFixtures.friendship(friend, TestFixtures.user(BIG))));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL, strangerId), FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));
        when(userMapper.toUserSearchDto(any(UserEntity.class), any(FriendStatus.class), any()))
                .thenReturn(null);

        BaseResponse<PaginationResponse<UserSearchDto>> response =
                service.searchUsers(new PaginationRequest(10, null, 0), "bob");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).hasSize(2);
        verify(userMapper).toUserSearchDto(friend, FriendStatus.FRIEND, null);
        verify(userMapper).toUserSearchDto(stranger, FriendStatus.RECEIVED, pending.getId());
    }
}
```

- [ ] **Step 2: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=UserServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 13 tests pass.

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/chat_socket/service/impl/UserServiceImplTest.java
git commit -m "test: characterization tests for UserServiceImpl"
```

---

### Task 6: MessageServiceImpl characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/service/impl/MessageServiceImplTest.java`

**Interfaces:**
- Consumes: `MessageServiceImpl(ConversationRepository, MessageRepository, ParticipantRepository, UserRepository, MessageMapper, SocketPublisher)`. Task 13 adds a `ConversationService` constructor parameter and rewrites the "new direct conversation" test.

- [ ] **Step 1: Write the test**

```java
package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageRequest;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.socket.SocketPublisher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    MessageMapper messageMapper;

    @Mock
    SocketPublisher socketPublisher;

    MessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageServiceImpl(
                conversationRepository,
                messageRepository,
                participantRepository,
                userRepository,
                messageMapper,
                socketPublisher);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Stubs everything createMessage() touches: save message, update conversation, mark sender as read, publish. */
    private MessageDto stubMessagePersistence(ConversationEntity conversation, UserEntity sender) {
        ParticipantEntity senderParticipant = TestFixtures.participant(conversation, sender, ParticipantRole.MEMBER);
        MessageDto dto = new MessageDto(MESSAGE_ID, conversation.getId(), sender.getId(), "hello", null, MessageType.TEXT, null, null);
        when(messageRepository.saveAndFlush(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity message = invocation.getArgument(0);
            message.setId(MESSAGE_ID);
            message.setCreatedAt(TestFixtures.FIXED_TIME);
            return message;
        });
        when(participantRepository.findByIdConversationIdAndIdUserId(conversation.getId(), sender.getId()))
                .thenReturn(Optional.of(senderParticipant));
        when(messageMapper.toDto(any(MessageEntity.class))).thenReturn(dto);
        return dto;
    }

    @Test
    void sendDirectMessage_blankContent_returns400() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<MessageDto> response =
                service.sendDirectMessage(new MessageRequest(SMALL, "  ", null, null, null));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Content is required.");
    }

    @Test
    void sendDirectMessage_noRecipientAndNoConversation_returns400() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<MessageDto> response = service.sendDirectMessage(new MessageRequest(null, "hi", null, null, null));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Recipient is required.");
    }

    @Test
    void sendDirectMessage_toSelf_returns400() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<MessageDto> response = service.sendDirectMessage(new MessageRequest(BIG, "hi", null, null, null));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("You cannot send a direct message to yourself.");
    }

    @Test
    void sendDirectMessage_conversationIsGroup_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP)));

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Direct conversation not found.");
    }

    @Test
    void sendDirectMessage_notParticipant_throwsForbidden() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, BIG))
                .thenReturn(false);

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendDirectMessage_existingConversation_savesMessageUpdatesConversationAndPublishes() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, BIG))
                .thenReturn(true);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response =
                service.sendDirectMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        assertThat(conversation.getLastMessage().getId()).isEqualTo(MESSAGE_ID);
        assertThat(conversation.getLastMessageAt()).isEqualTo(TestFixtures.FIXED_TIME);
        assertThat(conversation.getLastMessage().getType()).isEqualTo(MessageType.TEXT);
        verify(participantRepository).restoreDeletedParticipantsByConversationId(CONVERSATION_ID);
        verify(conversationRepository).save(conversation);
        verify(participantRepository).save(any(ParticipantEntity.class));
        verify(socketPublisher).publishMessageAfterCommit(CONVERSATION_ID, dto, TestFixtures.FIXED_TIME);
    }

    @Test
    void sendDirectMessage_byRecipientWithoutConversation_createsDirectConversationWithTwoParticipants() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        UserEntity recipient = TestFixtures.user(SMALL);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(recipient));
        when(conversationRepository.findDirectConversation(ConversationType.DIRECT, SMALL, BIG))
                .thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity conversation = invocation.getArgument(0);
            conversation.setId(CONVERSATION_ID);
            return conversation;
        });
        when(participantRepository.save(any(ParticipantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.saveAndFlush(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity message = invocation.getArgument(0);
            message.setId(MESSAGE_ID);
            message.setCreatedAt(TestFixtures.FIXED_TIME);
            return message;
        });
        when(participantRepository.findByIdConversationIdAndIdUserId(CONVERSATION_ID, BIG))
                .thenReturn(Optional.of(TestFixtures.participant(
                        TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT), sender, ParticipantRole.MEMBER)));
        when(messageMapper.toDto(any(MessageEntity.class))).thenReturn(null);

        BaseResponse<MessageDto> response = service.sendDirectMessage(new MessageRequest(SMALL, "hi", null, null, null));

        assertThat(response.status()).isEqualTo(201);
        // 2 participants created for the new conversation + 1 save when marking sender as read
        verify(participantRepository, times(3)).save(any(ParticipantEntity.class));
        verify(socketPublisher).publishMessageAfterCommit(CONVERSATION_ID, null, TestFixtures.FIXED_TIME);
    }

    @Test
    void sendGroupMessage_blankContent_returns400() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<MessageDto> response =
                service.sendGroupMessage(new MessageRequest(null, "", null, CONVERSATION_ID, null));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Content is required.");
    }

    @Test
    void sendGroupMessage_noConversation_returns400() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<MessageDto> response = service.sendGroupMessage(new MessageRequest(null, "hi", null, null, null));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Conversation is required.");
    }

    @Test
    void sendGroupMessage_conversationIsDirect_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT)));

        assertThatThrownBy(() -> service.sendGroupMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendGroupMessage_success_returns201AndPublishes() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, BIG))
                .thenReturn(true);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response =
                service.sendGroupMessage(new MessageRequest(null, "hi", "http://file", CONVERSATION_ID, MessageType.IMAGE));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        assertThat(conversation.getLastMessage().getType()).isEqualTo(MessageType.IMAGE);
        assertThat(conversation.getLastMessage().getAttachmentUrl()).isEqualTo("http://file");
        verify(socketPublisher).publishMessageAfterCommit(CONVERSATION_ID, dto, TestFixtures.FIXED_TIME);
    }
}
```

- [ ] **Step 2: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=MessageServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 11 tests pass.

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/chat_socket/service/impl/MessageServiceImplTest.java
git commit -m "test: characterization tests for MessageServiceImpl"
```

---

### Task 7: ConversationServiceImpl characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/service/impl/ConversationServiceImplTest.java`

**Interfaces:**
- Consumes: `ConversationServiceImpl(ConversationRepository, MessageRepository, ParticipantRepository, UserRepository, FriendRepository, ConversationMapper, MessageMapper, SocketPublisher)`.
- Stubs that Task 11 will rename: `participantRepository.findByIdConversationIdAndIdUserId` → `findActiveParticipant`; `friendRepository.existsByUserAIdAndUserBId(min, max)` → `existsFriendship(me, other)`. Tests that Task 12 will change from `status()==400` to `BadRequestException` are marked `// 400-as-return`.

- [ ] **Step 1: Write the test**

```java
package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.GroupMembersRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateGroupRequest;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.ConversationMapper;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.socket.SocketPublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final ConversationDto DTO =
            new ConversationDto(C, null, null, null, null, null, null, null, null, null, null, 0, List.of());

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    FriendRepository friendRepository;

    @Mock
    ConversationMapper conversationMapper;

    @Mock
    MessageMapper messageMapper;

    @Mock
    SocketPublisher socketPublisher;

    ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(BIG);
        service = new ConversationServiceImpl(
                conversationRepository,
                messageRepository,
                participantRepository,
                userRepository,
                friendRepository,
                conversationMapper,
                messageMapper,
                socketPublisher);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** A GROUP conversation C where the current user (BIG) is a participant with the given role. */
    private ParticipantEntity stubGroupWithMe(ParticipantRole role) {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        ParticipantEntity me = TestFixtures.participant(conversation, TestFixtures.user(BIG), role);
        when(conversationRepository.findById(C)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByIdConversationIdAndIdUserId(C, BIG)).thenReturn(Optional.of(me));
        return me;
    }

    /** findConversationsWithDetails(List.of(C)) returns the given conversation and the mapper turns it into DTO. */
    private void stubDetailsAndMapper(ConversationEntity conversation) {
        when(conversationRepository.findConversationsWithDetails(List.of(C))).thenReturn(List.of(conversation));
        when(conversationMapper.toDto(conversation)).thenReturn(DTO);
    }

    // ---------- getConversations ----------

    @Test
    void getConversations_noIds_returnsEmptyPage() {
        when(conversationRepository.findActiveConversationIdsForUser(eq(BIG), isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        BaseResponse<PaginationResponse<ConversationDto>> response = service.getConversations(null, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).isEmpty();
        assertThat(response.data().nextCursor()).isNull();
        verify(conversationRepository, never()).findConversationsWithDetails(any());
    }

    @Test
    void getConversations_mapsEachConversationWithItsUnreadCount() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        MessageRepository.UnreadCountProjection unread = mock(MessageRepository.UnreadCountProjection.class);
        when(unread.getConversationId()).thenReturn(C);
        when(unread.getUnreadCount()).thenReturn(3L);
        when(conversationRepository.findActiveConversationIdsForUser(eq(BIG), eq(ConversationType.GROUP), any(Pageable.class)))
                .thenReturn(List.of(C));
        when(conversationRepository.findConversationsWithDetails(List.of(C))).thenReturn(List.of(conversation));
        when(messageRepository.countUnreadMessagesByConversation(BIG, List.of(C))).thenReturn(List.of(unread));
        when(conversationMapper.toDto(conversation, 3L)).thenReturn(DTO);

        BaseResponse<PaginationResponse<ConversationDto>> response =
                service.getConversations(null, ConversationType.GROUP);

        assertThat(response.data().messages()).containsExactly(DTO);
        assertThat(response.data().nextCursor()).isNull();
    }

    // ---------- createConversation ----------

    @Test
    void createConversation_unknownType_returns400() { // 400-as-return
        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(null, "x", List.of(SMALL)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Conversation type is invalid.");
    }

    @Test
    void createDirect_moreThanOneMember_returns400() { // 400-as-return
        BaseResponse<ConversationDto> response = service.createConversation(
                new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL, THIRD)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Direct conversation requires exactly one member.");
    }

    @Test
    void createDirect_withSelf_returns400() { // 400-as-return
        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.DIRECT, null, List.of(BIG)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("You cannot create a direct conversation with yourself.");
    }

    @Test
    void createDirect_existingConversation_restoresParticipantAndReturns201() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.DIRECT);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(conversationRepository.findDirectConversation(ConversationType.DIRECT, SMALL, BIG))
                .thenReturn(Optional.of(conversation));
        stubDetailsAndMapper(conversation);

        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL)));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(DTO);
        verify(participantRepository).restoreDeletedParticipant(C, BIG);
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDirect_newConversation_ordersDirectUsersAndCreatesTwoParticipants() {
        UserEntity me = TestFixtures.user(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(conversationRepository.findDirectConversation(ConversationType.DIRECT, SMALL, BIG))
                .thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity conversation = invocation.getArgument(0);
            conversation.setId(C);
            return conversation;
        });
        when(participantRepository.save(any(ParticipantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findConversationsWithDetails(List.of(C)))
                .thenReturn(List.of(TestFixtures.conversation(C, ConversationType.DIRECT)));
        when(conversationMapper.toDto(any(ConversationEntity.class))).thenReturn(DTO);

        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL)));

        assertThat(response.status()).isEqualTo(201);
        ArgumentCaptor<ConversationEntity> saved = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ConversationType.DIRECT);
        assertThat(saved.getValue().getDirectUserA()).isSameAs(other);
        assertThat(saved.getValue().getDirectUserB()).isSameAs(me);
        assertThat(saved.getValue().getCreatedBy()).isSameAs(me);
        verify(participantRepository, times(2)).save(any(ParticipantEntity.class));
        verify(participantRepository).restoreDeletedParticipant(C, BIG);
    }

    @Test
    void createGroup_blankName_returns400() { // 400-as-return
        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.GROUP, "  ", List.of(SMALL)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Group name is required.");
    }

    @Test
    void createGroup_missingMember_throwsNotFound() {
        UserEntity me = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(userRepository.findAllById(Set.of(BIG, SMALL))).thenReturn(List.of(me));

        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.GROUP, "Team", List.of(SMALL))))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("One or more members were not found.");
    }

    @Test
    void createGroup_success_makesCreatorAdminAndOthersMembers() {
        UserEntity me = TestFixtures.user(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(userRepository.findAllById(Set.of(BIG, SMALL))).thenReturn(List.of(me, other));
        when(conversationRepository.saveAndFlush(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity conversation = invocation.getArgument(0);
            conversation.setId(C);
            return conversation;
        });
        when(participantRepository.save(any(ParticipantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationMapper.toDto(any(ConversationEntity.class))).thenReturn(DTO);

        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.GROUP, " Team ", List.of(SMALL)));

        assertThat(response.status()).isEqualTo(201);
        ArgumentCaptor<ConversationEntity> saved = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getGroupName()).isEqualTo("Team");
        ArgumentCaptor<ParticipantEntity> participants = ArgumentCaptor.forClass(ParticipantEntity.class);
        verify(participantRepository, times(2)).save(participants.capture());
        assertThat(participants.getAllValues())
                .extracting(p -> p.getUser().getId(), ParticipantEntity::getRole)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(BIG, ParticipantRole.ADMIN),
                        org.assertj.core.groups.Tuple.tuple(SMALL, ParticipantRole.MEMBER));
    }

    // ---------- getMessages ----------

    @Test
    void getMessages_unknownConversation_throwsNotFound() {
        when(conversationRepository.existsById(C)).thenReturn(false);

        assertThatThrownBy(() -> service.getMessages(C, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMessages_notParticipant_throwsForbidden() {
        when(conversationRepository.existsById(C)).thenReturn(true);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, BIG))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getMessages(C, null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMessages_returnsOldestFirst() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        UserEntity me = TestFixtures.user(BIG);
        MessageEntity newer = TestFixtures.message(UUID.randomUUID(), conversation, me);
        MessageEntity older = TestFixtures.message(UUID.randomUUID(), conversation, me);
        older.setCreatedAt(TestFixtures.FIXED_TIME.minusMinutes(1));
        MessageDto newerDto = new MessageDto(newer.getId(), C, BIG, "n", null, MessageType.TEXT, null, null);
        MessageDto olderDto = new MessageDto(older.getId(), C, BIG, "o", null, MessageType.TEXT, null, null);
        when(conversationRepository.existsById(C)).thenReturn(true);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, BIG))
                .thenReturn(true);
        when(messageRepository.findLatestMessages(eq(C), any(Pageable.class))).thenReturn(List.of(newer, older));
        when(messageMapper.toDto(newer)).thenReturn(newerDto);
        when(messageMapper.toDto(older)).thenReturn(olderDto);

        BaseResponse<PaginationResponse<MessageDto>> response = service.getMessages(C, null);

        assertThat(response.data().messages()).containsExactly(olderDto, newerDto);
        assertThat(response.data().nextCursor()).isNull();
    }

    // ---------- markAsSeen ----------

    @Test
    void markAsSeen_unknownConversation_throwsNotFound() {
        when(conversationRepository.findById(C)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsSeen(C)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAsSeen_notParticipant_throwsForbidden() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.findByIdConversationIdAndIdUserId(C, BIG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsSeen(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markAsSeen_leftParticipant_throwsForbidden() { // moves to ParticipantRepositoryDefaultsTest in Task 11
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        me.setLeftAt(TestFixtures.FIXED_TIME);

        assertThatThrownBy(() -> service.markAsSeen(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markAsSeen_noMessages_returns200WithoutSaving() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        BaseResponse<Void> response = service.markAsSeen(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.message()).isEqualTo("No messages to mark as seen.");
        verify(participantRepository, never()).save(any());
    }

    @Test
    void markAsSeen_alreadyRead_returns200WithoutSaving() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        MessageEntity last = TestFixtures.message(UUID.randomUUID(), me.getConversation(), TestFixtures.user(SMALL));
        me.getConversation().setLastMessage(last);
        me.setLastReadMessage(last);

        BaseResponse<Void> response = service.markAsSeen(C);

        assertThat(response.message()).isEqualTo("Messages already marked as seen.");
        verify(participantRepository, never()).save(any());
    }

    @Test
    void markAsSeen_success_updatesParticipantAndPublishesSeenEvent() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        MessageEntity last = TestFixtures.message(UUID.randomUUID(), me.getConversation(), TestFixtures.user(SMALL));
        me.getConversation().setLastMessage(last);
        MessageDto lastDto = new MessageDto(last.getId(), C, SMALL, "hello", null, MessageType.TEXT, null, null);
        when(messageMapper.toDto(last)).thenReturn(lastDto);

        BaseResponse<Void> response = service.markAsSeen(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(me.getLastReadMessage()).isSameAs(last);
        assertThat(me.getLastReadAt()).isNotNull();
        verify(participantRepository).save(me);
        verify(socketPublisher)
                .publishConversationSeenAfterCommit(
                        eq(C), eq(BIG), eq(lastDto), eq(TestFixtures.FIXED_TIME), any(LocalDateTime.class));
    }

    // ---------- deleteGroup ----------

    @Test
    void deleteGroup_directConversation_throwsNotFound() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.DIRECT)));

        assertThatThrownBy(() -> service.deleteGroup(C))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
    }

    @Test
    void deleteGroup_member_throwsForbidden() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        assertThatThrownBy(() -> service.deleteGroup(C))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only admins can manage this group.");
    }

    @Test
    void deleteGroup_admin_softDeletesOwnParticipationAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        MessageEntity last = TestFixtures.message(UUID.randomUUID(), me.getConversation(), TestFixtures.user(SMALL));
        me.getConversation().setLastMessage(last);

        BaseResponse<Void> response = service.deleteGroup(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(me.getDeletedAt()).isNotNull();
        assertThat(me.getLastReadMessage()).isSameAs(last);
        verify(participantRepository).save(me);
        verify(socketPublisher).publishGroupDeletedAfterCommit(C, BIG);
    }

    // ---------- updateGroup ----------

    @Test
    void updateGroup_blankName_throwsBadRequest() {
        assertThatThrownBy(() -> service.updateGroup(C, new UpdateGroupRequest(" ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Group name is required.");
    }

    @Test
    void updateGroup_admin_trimsNameSavesAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        ConversationEntity conversation = me.getConversation();
        stubDetailsAndMapper(conversation);

        BaseResponse<ConversationDto> response = service.updateGroup(C, new UpdateGroupRequest("  New name "));

        assertThat(response.status()).isEqualTo(200);
        assertThat(conversation.getGroupName()).isEqualTo("New name");
        verify(conversationRepository).save(conversation);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }

    // ---------- addGroupMembers ----------

    @Test
    void addGroupMembers_emptyIds_throwsBadRequest() {
        assertThatThrownBy(() -> service.addGroupMembers(C, new GroupMembersRequest(List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Member ids are required.");
    }

    @Test
    void addGroupMembers_onlySelf_returnsCurrentStateWithoutChanges() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        stubDetailsAndMapper(me.getConversation());

        BaseResponse<ConversationDto> response = service.addGroupMembers(C, new GroupMembersRequest(List.of(BIG)));

        assertThat(response.message()).isEqualTo("Members already in group.");
        verify(friendRepository, never()).existsByUserAIdAndUserBId(any(), any());
    }

    @Test
    void addGroupMembers_notFriend_throwsFriendPermissionWithOffendingIds() {
        stubGroupWithMe(ParticipantRole.MEMBER);
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);

        assertThatThrownBy(() -> service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL))))
                .isInstanceOfSatisfying(FriendPermissionException.class, ex -> assertThat(ex.getNotFriends())
                        .containsExactly(SMALL));
    }

    @Test
    void addGroupMembers_unknownUser_throwsNotFound() {
        stubGroupWithMe(ParticipantRole.MEMBER);
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);
        when(userRepository.findAllById(Set.of(SMALL))).thenReturn(List.of());

        assertThatThrownBy(() -> service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addGroupMembers_newMember_createsParticipantAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        UserEntity other = TestFixtures.user(SMALL);
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);
        when(userRepository.findAllById(Set.of(SMALL))).thenReturn(List.of(other));
        when(participantRepository.findByConversationIdAndIdUserIdIn(C, Set.of(SMALL))).thenReturn(List.of());
        when(participantRepository.save(any(ParticipantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailsAndMapper(me.getConversation());

        BaseResponse<ConversationDto> response = service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL)));

        assertThat(response.status()).isEqualTo(200);
        ArgumentCaptor<ParticipantEntity> saved = ArgumentCaptor.forClass(ParticipantEntity.class);
        verify(participantRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(other);
        assertThat(saved.getValue().getRole()).isEqualTo(ParticipantRole.MEMBER);
        verify(conversationRepository).save(me.getConversation());
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }

    @Test
    void addGroupMembers_memberWhoLeft_isRejoinedAsMember() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        UserEntity other = TestFixtures.user(SMALL);
        ParticipantEntity left = TestFixtures.participant(me.getConversation(), other, ParticipantRole.ADMIN);
        left.setLeftAt(TestFixtures.FIXED_TIME);
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);
        when(userRepository.findAllById(Set.of(SMALL))).thenReturn(List.of(other));
        when(participantRepository.findByConversationIdAndIdUserIdIn(C, Set.of(SMALL))).thenReturn(List.of(left));
        stubDetailsAndMapper(me.getConversation());

        service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL)));

        assertThat(left.getLeftAt()).isNull();
        assertThat(left.getDeletedAt()).isNull();
        assertThat(left.getRole()).isEqualTo(ParticipantRole.MEMBER);
        verify(participantRepository).save(left);
    }

    // ---------- removeGroupMember ----------

    @Test
    void removeGroupMember_nullId_throwsBadRequest() {
        assertThatThrownBy(() -> service.removeGroupMember(C, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void removeGroupMember_notAdmin_throwsForbidden() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        assertThatThrownBy(() -> service.removeGroupMember(C, SMALL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only admins can manage group members.");
    }

    @Test
    void removeGroupMember_self_throwsBadRequest() {
        stubGroupWithMe(ParticipantRole.ADMIN);

        assertThatThrownBy(() -> service.removeGroupMember(C, BIG))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot remove yourself. Use leave endpoint instead.");
    }

    @Test
    void removeGroupMember_targetNotInGroup_throwsNotFound() {
        stubGroupWithMe(ParticipantRole.ADMIN);
        when(participantRepository.findByIdConversationIdAndIdUserId(C, SMALL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeGroupMember(C, SMALL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Participant not found.");
    }

    @Test
    void removeGroupMember_targetIsAdmin_throwsBadRequest() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        ParticipantEntity target =
                TestFixtures.participant(me.getConversation(), TestFixtures.user(SMALL), ParticipantRole.ADMIN);
        when(participantRepository.findByIdConversationIdAndIdUserId(C, SMALL)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.removeGroupMember(C, SMALL))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot remove an admin from this group.");
    }

    @Test
    void removeGroupMember_success_marksTargetLeftAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        ParticipantEntity target =
                TestFixtures.participant(me.getConversation(), TestFixtures.user(SMALL), ParticipantRole.MEMBER);
        when(participantRepository.findByIdConversationIdAndIdUserId(C, SMALL)).thenReturn(Optional.of(target));
        stubDetailsAndMapper(me.getConversation());

        BaseResponse<ConversationDto> response = service.removeGroupMember(C, SMALL);

        assertThat(response.status()).isEqualTo(200);
        assertThat(target.getLeftAt()).isNotNull();
        verify(participantRepository).save(target);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }

    // ---------- leaveGroup ----------

    @Test
    void leaveGroup_lastAdmin_throwsBadRequest() {
        stubGroupWithMe(ParticipantRole.ADMIN);
        when(participantRepository.countActiveByConversationIdAndRoleAndIdUserIdNot(C, ParticipantRole.ADMIN, BIG))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.leaveGroup(C)).isInstanceOf(BadRequestException.class);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void leaveGroup_member_marksLeftAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);

        BaseResponse<Void> response = service.leaveGroup(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(me.getLeftAt()).isNotNull();
        verify(participantRepository).save(me);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }
}
```

- [ ] **Step 2: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=ConversationServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 38 tests pass. If a test fails with `UnnecessaryStubbingException`, delete the unused stub named in the message. If it fails with `PotentialStubbingProblem`/"strict stubbing argument mismatch", the code calls the method with different arguments than stubbed — read the failure, fix the stub's arguments (this is characterization: the code is the source of truth).

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/chat_socket/service/impl/ConversationServiceImplTest.java
git commit -m "test: characterization tests for ConversationServiceImpl"
```

---

### Task 8: Security layer characterization tests

**Files:**
- Create: `src/test/java/com/chat_socket/security/SecurityFilterTest.java`
- Create: `src/test/java/com/chat_socket/security/SocketChannelInterceptorTest.java`
- Create: `src/test/java/com/chat_socket/security/GroupPermissionTest.java`
- Create: `src/test/java/com/chat_socket/security/MessageDirectPermissionTest.java`
- Create: `src/test/java/com/chat_socket/security/MessageGroupPermissionTest.java`

**Interfaces:**
- Consumes: `SecurityFilter(ObjectMapper, JwtService, UserRepository)`, `SocketChannelInterceptor(JwtService, UserRepository, ParticipantRepository)`, `GroupPermission(ConversationRepository, ParticipantRepository)`, `MessageDirectPermission(FriendRepository)`, `MessageGroupPermission(ConversationRepository, ParticipantRepository)`.
- `jwtService.verifyAccessToken(token)` currently returns `UUID` and throws `JwtException` for a bad token. Task 15 switches these stubs to `Optional`.

- [ ] **Step 1: Write `SecurityFilterTest`**

```java
package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {
    @Mock
    JwtService jwtService;

    @Mock
    UserRepository userRepository;

    SecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityFilter(new ObjectMapper(), jwtService, userRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void shouldNotFilter_publicPathsAndOptions() {
        assertThat(filter.shouldNotFilter(request("OPTIONS", "/api/v1/user/me"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/ws"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/health-check"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/sign-in"))).isTrue();
    }

    @Test
    void shouldNotFilter_protectedPath_isFalse() {
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/user/me"))).isFalse();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/conversation"))).isFalse();
    }

    @Test
    void missingAuthorizationHeader_writes401Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/api/v1/user/me"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"message\":\"Token not found.\"").contains("\"status\":401");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void bearerWithoutToken_writes401() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidToken_writes403() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.verifyAccessToken("bad")).thenThrow(new JwtException("bad"));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Token expired or invalid.");
    }

    @Test
    void unknownUser_writes404() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.verifyAccessToken("good")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("User does not exist.");
    }

    @Test
    void validToken_setsSecurityContextAndContinuesChain() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.verifyAccessToken("good")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(TestFixtures.user(userId)));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).isInstanceOf(UserSecurity.class);
        assertThat(((UserSecurity) principal).id()).isEqualTo(userId);
    }
}
```

- [ ] **Step 2: Write `SocketChannelInterceptorTest`**

```java
package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.chat_socket.utils.Security;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class SocketChannelInterceptorTest {
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock
    JwtService jwtService;

    @Mock
    UserRepository userRepository;

    @Mock
    ParticipantRepository participantRepository;

    MessageChannel channel = mock(MessageChannel.class);
    SocketChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SocketChannelInterceptor(jwtService, userRepository, participantRepository);
    }

    /** Builds a STOMP frame whose header accessor stays mutable so the interceptor's setUser() is observable. */
    private static StompHeaderAccessor accessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_withoutAuthorization_throwsNotFound() {
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Token not found.");
    }

    @Test
    void connect_invalidToken_throwsForbidden() {
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad");
        when(jwtService.verifyAccessToken("bad")).thenThrow(new JwtException("bad"));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Token expired or invalid.");
    }

    @Test
    void connect_validToken_setsUserOnAccessor() {
        UUID userId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(userId);
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer good");
        when(jwtService.verifyAccessToken("good")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        interceptor.preSend(message(accessor), channel);

        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        UserSecurity principal = Security.getUserSecurityFromPrincipal(accessor.getUser());
        assertThat(principal).isNotNull();
        assertThat(principal.id()).isEqualTo(userId);
    }

    @Test
    void subscribe_nonConversationDestination_passesThrough() {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/online-users");

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_conversationMessages_withoutUser_throwsForbidden() {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + CONVERSATION_ID + "/messages");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Socket user is not authenticated.");
    }

    @Test
    void subscribe_conversationMessages_invalidUuid_throwsForbidden() {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/not-a-uuid/messages");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Conversation destination is invalid.");
    }

    @Test
    void subscribe_conversationMessages_notParticipant_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + CONVERSATION_ID + "/messages");
        accessor.setUser(Security.getUserAuthentication(TestFixtures.user(userId)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, userId))
                .thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a participant of this conversation.");
    }

    @Test
    void subscribe_conversationMessages_participant_passes() {
        UUID userId = UUID.randomUUID();
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + CONVERSATION_ID + "/messages");
        accessor.setUser(Security.getUserAuthentication(TestFixtures.user(userId)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, userId))
                .thenReturn(true);

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNotNull();
    }
}
```

- [ ] **Step 3: Write `GroupPermissionTest`**

```java
package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class GroupPermissionTest {
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    ParticipantRepository participantRepository;

    GroupPermission permission;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(ME);
        permission = new GroupPermission(conversationRepository, participantRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ParticipantEntity stubGroupWithMe(ParticipantRole role) {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        ParticipantEntity me = TestFixtures.participant(conversation, TestFixtures.user(ME), role);
        when(conversationRepository.findById(C)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByIdConversationIdAndIdUserId(C, ME)).thenReturn(Optional.of(me));
        return me;
    }

    @Test
    void nullConversationId_isAllowed() {
        assertThat(permission.canManageGroup(null)).isTrue();
    }

    @Test
    void unknownConversation_throwsNotFound() {
        when(conversationRepository.findById(C)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permission.canManageGroup(C)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void directConversation_throwsNotFound() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.DIRECT)));

        assertThatThrownBy(() -> permission.canManageGroup(C))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
    }

    @Test
    void notParticipant_throwsForbidden() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.findByIdConversationIdAndIdUserId(C, ME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permission.canManageGroup(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void leftParticipant_throwsForbidden() { // moves to ParticipantRepositoryDefaultsTest in Task 11
        stubGroupWithMe(ParticipantRole.ADMIN).setLeftAt(TestFixtures.FIXED_TIME);

        assertThatThrownBy(() -> permission.canManageGroup(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void member_throwsForbidden() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        assertThatThrownBy(() -> permission.canManageGroup(C))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only admins can manage this group.");
    }

    @Test
    void admin_isAllowed() {
        stubGroupWithMe(ParticipantRole.ADMIN);

        assertThat(permission.canManageGroup(C)).isTrue();
    }
}
```

- [ ] **Step 4: Write `MessageDirectPermissionTest`**

```java
package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.repository.FriendRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MessageDirectPermissionTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    FriendRepository friendRepository;

    MessageDirectPermission permission;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(BIG);
        permission = new MessageDirectPermission(friendRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canSendDirect_singleRecipient_alwaysTrue() { // deleted in Task 16 together with the no-op @PreAuthorize
        assertThat(permission.canSendDirect(SMALL)).isTrue();
        assertThat(permission.canSendDirect((UUID) null)).isTrue();
    }

    @Test
    void canCreateConversation_nullOrDirect_isAllowedWithoutChecks() {
        assertThat(permission.canCreateConversation(null)).isTrue();
        assertThat(permission.canCreateConversation(new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL))))
                .isTrue();
    }

    @Test
    void canCreateConversation_group_allFriends_isAllowed() {
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);
        when(friendRepository.existsByUserAIdAndUserBId(BIG, THIRD)).thenReturn(true);

        assertThat(permission.canCreateConversation(
                        new ConversationRequest(ConversationType.GROUP, "g", List.of(SMALL, THIRD))))
                .isTrue();
    }

    @Test
    void canCreateConversation_group_someNotFriends_throwsWithOffenders() {
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);
        when(friendRepository.existsByUserAIdAndUserBId(BIG, THIRD)).thenReturn(false);

        assertThatThrownBy(() -> permission.canCreateConversation(
                        new ConversationRequest(ConversationType.GROUP, "g", List.of(SMALL, THIRD))))
                .isInstanceOfSatisfying(FriendPermissionException.class, ex -> assertThat(ex.getNotFriends())
                        .containsExactly(THIRD));
    }

    @Test
    void canSendDirect_emptyMemberList_throwsIllegalArgument() {
        assertThatThrownBy(() -> permission.canSendDirect(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 5: Write `MessageGroupPermissionTest`**

```java
package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MessageGroupPermissionTest {
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    ParticipantRepository participantRepository;

    MessageGroupPermission permission;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(ME);
        permission = new MessageGroupPermission(conversationRepository, participantRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nullConversationId_isAllowed() {
        assertThat(permission.canSendGroup(null)).isTrue();
    }

    @Test
    void unknownConversation_throwsNotFound() {
        when(conversationRepository.findById(C)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permission.canSendGroup(C)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void directConversation_throwsNotFound() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.DIRECT)));

        assertThatThrownBy(() -> permission.canSendGroup(C))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
    }

    @Test
    void notActiveParticipant_throwsForbidden() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, ME))
                .thenReturn(false);

        assertThatThrownBy(() -> permission.canSendGroup(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void activeParticipant_isAllowed() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, ME))
                .thenReturn(true);

        assertThat(permission.canSendGroup(C)).isTrue();
    }
}
```

- [ ] **Step 6: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=SecurityFilterTest,SocketChannelInterceptorTest,GroupPermissionTest,MessageDirectPermissionTest,MessageGroupPermissionTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 32 tests pass.

- [ ] **Step 7: Commit**

```powershell
git add src/test/java/com/chat_socket/security
git commit -m "test: characterization tests for security filter, socket interceptor, permissions"
```

---

### Task 9: SocketPublisher characterization test

**Files:**
- Create: `src/test/java/com/chat_socket/socket/SocketPublisherTest.java`

**Interfaces:**
- Consumes: `SocketPublisher(ParticipantRepository, SocketEmitter, MessageRepository)` (package-private constructor → test lives in `com.chat_socket.socket`), `SocketEmitter.emit(String destination, Object payload)`, `SocketEmitter.emitTo(String destination, Object payload, UUID userId)`.

- [ ] **Step 1: Write the test**

```java
package com.chat_socket.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chat_socket.dto.ConversationEvent;
import com.chat_socket.dto.ConversationSeenEvent;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.enums.MessageType;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SocketPublisherTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final MessageDto MESSAGE = new MessageDto(UUID.randomUUID(), C, U1, "hi", null, MessageType.TEXT, AT, AT);

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    SocketEmitter socketEmitter;

    @Mock
    MessageRepository messageRepository;

    SocketPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SocketPublisher(participantRepository, socketEmitter, messageRepository);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MessageRepository.UnreadCountProjection unread(long count) {
        MessageRepository.UnreadCountProjection projection = mock(MessageRepository.UnreadCountProjection.class);
        when(projection.getUnreadCount()).thenReturn(count);
        return projection;
    }

    @Test
    void publishMessage_noTransaction_emitsTopicAndPerUserQueueWithUnreadCount() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C))).thenReturn(List.of(unread(0)));
        when(messageRepository.countUnreadMessagesByConversation(U2, List.of(C))).thenReturn(List.of(unread(4)));

        publisher.publishMessageAfterCommit(C, MESSAGE, AT);

        verify(socketEmitter).emit("/conversations/" + C + "/messages", MESSAGE);
        ArgumentCaptor<ConversationEvent> events = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U2));
        assertThat(events.getAllValues())
                .extracting(ConversationEvent::eventType, ConversationEvent::unreadCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 0L),
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 4L));
    }

    @Test
    void publishMessage_userWithoutUnreadRow_getsZero() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C))).thenReturn(List.of());

        publisher.publishMessageAfterCommit(C, MESSAGE, AT);

        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        assertThat(event.getValue().unreadCount()).isZero();
    }

    @Test
    void publishMessage_insideTransaction_defersUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C))).thenReturn(List.of());

        publisher.publishMessageAfterCommit(C, MESSAGE, AT);

        verifyNoInteractions(socketEmitter);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();
        verify(socketEmitter).emit("/conversations/" + C + "/messages", MESSAGE);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationEvent.class), eq(U1));
    }

    @Test
    void publishConversationUpdated_emitsToEachActiveUserWithZeroUnread() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));

        publisher.publishConversationUpdatedAfterCommit(C, null, AT);

        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationEvent.class), eq(U2));
        assertThat(event.getValue().lastMessage()).isNull();
        assertThat(event.getValue().lastMessageAt()).isEqualTo(AT);
        verify(socketEmitter, never()).emit(any(), any());
    }

    @Test
    void publishGroupDeleted_emitsOnlyToTheDeletingUser() {
        publisher.publishGroupDeletedAfterCommit(C, U1);

        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        assertThat(event.getValue().eventType()).isEqualTo("group.deleted");
        assertThat(event.getValue().conversationId()).isEqualTo(C);
    }

    @Test
    void publishConversationSeen_emitsSeenTopicAndUpdateQueue() {
        publisher.publishConversationSeenAfterCommit(C, U1, MESSAGE, AT, AT.plusMinutes(1));

        ArgumentCaptor<ConversationSeenEvent> seen = ArgumentCaptor.forClass(ConversationSeenEvent.class);
        verify(socketEmitter).emit(eq("/conversations/" + C + "/seen"), seen.capture());
        assertThat(seen.getValue().eventType()).isEqualTo("conversation.seen");
        assertThat(seen.getValue().seenByUserId()).isEqualTo(U1);
        assertThat(seen.getValue().lastReadMessageId()).isEqualTo(MESSAGE.id());
        assertThat(seen.getValue().lastReadAt()).isEqualTo(AT.plusMinutes(1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationEvent.class), eq(U1));
    }
}
```

- [ ] **Step 2: Run**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=SocketPublisherTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 6 tests pass.

- [ ] **Step 3: Run the whole suite once, then commit**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS. This is the Phase 1 safety net; every later task must keep it green.

```powershell
git add src/test/java/com/chat_socket/socket/SocketPublisherTest.java
git commit -m "test: characterization test for SocketPublisher"
```

---

## Phase 2 — Refactor (tests stay green)

### Task 10: Add single-purpose helpers on entity / dto / repository

**Files:**
- Modify: `src/main/java/com/chat_socket/entity/ParticipantEntity.java`
- Modify: `src/main/java/com/chat_socket/entity/FriendEntity.java`
- Modify: `src/main/java/com/chat_socket/dto/UserPair.java`
- Modify: `src/main/java/com/chat_socket/repository/FriendRepository.java`
- Modify: `src/main/java/com/chat_socket/repository/ParticipantRepository.java`
- Create: `src/test/java/com/chat_socket/dto/UserPairTest.java`
- Create: `src/test/java/com/chat_socket/repository/FriendRepositoryDefaultsTest.java`
- Create: `src/test/java/com/chat_socket/repository/ParticipantRepositoryDefaultsTest.java`

**Interfaces:**
- Produces:
  - `boolean ParticipantEntity.isActive()` — `leftAt == null && deletedAt == null`
  - `UserEntity FriendEntity.otherUser(UUID userId)` — the friend that is not `userId`
  - `static UserPair UserPair.of(UUID first, UUID second)` — smaller `toString()` first
  - `default boolean FriendRepository.existsFriendship(UUID userId, UUID otherUserId)`
  - `default Optional<ParticipantEntity> ParticipantRepository.findActiveParticipant(UUID conversationId, UUID userId)`
- No callers change in this task (Task 11 does that), so all existing tests stay untouched.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/chat_socket/dto/UserPairTest.java`:

```java
package com.chat_socket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserPairTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void of_isOrderIndependent() {
        assertThat(UserPair.of(SMALL, BIG)).isEqualTo(new UserPair(SMALL, BIG));
        assertThat(UserPair.of(BIG, SMALL)).isEqualTo(new UserPair(SMALL, BIG));
    }

    @Test
    void of_sameIdTwice_keepsBoth() {
        assertThat(UserPair.of(SMALL, SMALL)).isEqualTo(new UserPair(SMALL, SMALL));
    }
}
```

`src/test/java/com/chat_socket/repository/FriendRepositoryDefaultsTest.java`:

```java
package com.chat_socket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The default method is real; only the derived query underneath is mocked. */
class FriendRepositoryDefaultsTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void existsFriendship_normalizesArgumentOrderBeforeQuerying() {
        FriendRepository repository = mock(FriendRepository.class);
        when(repository.existsFriendship(any(), any())).thenCallRealMethod();
        when(repository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);

        assertThat(repository.existsFriendship(BIG, SMALL)).isTrue();
        assertThat(repository.existsFriendship(SMALL, BIG)).isTrue();
    }
}
```

`src/test/java/com/chat_socket/repository/ParticipantRepositoryDefaultsTest.java`:

```java
package com.chat_socket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantRepositoryDefaultsTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID U = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static ParticipantRepository repositoryReturning(ParticipantEntity participant) {
        ParticipantRepository repository = mock(ParticipantRepository.class);
        when(repository.findActiveParticipant(any(), any())).thenCallRealMethod();
        when(repository.findByIdConversationIdAndIdUserId(C, U)).thenReturn(Optional.ofNullable(participant));
        return repository;
    }

    private static ParticipantEntity participant() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        return TestFixtures.participant(conversation, TestFixtures.user(U), ParticipantRole.MEMBER);
    }

    @Test
    void findActiveParticipant_activeParticipant_isReturned() {
        ParticipantEntity participant = participant();

        assertThat(repositoryReturning(participant).findActiveParticipant(C, U)).contains(participant);
    }

    @Test
    void findActiveParticipant_leftParticipant_isEmpty() {
        ParticipantEntity participant = participant();
        participant.setLeftAt(TestFixtures.FIXED_TIME);

        assertThat(repositoryReturning(participant).findActiveParticipant(C, U)).isEmpty();
    }

    @Test
    void findActiveParticipant_deletedParticipant_isEmpty() {
        ParticipantEntity participant = participant();
        participant.setDeletedAt(TestFixtures.FIXED_TIME);

        assertThat(repositoryReturning(participant).findActiveParticipant(C, U)).isEmpty();
    }

    @Test
    void findActiveParticipant_missing_isEmpty() {
        assertThat(repositoryReturning(null).findActiveParticipant(C, U)).isEmpty();
    }
}
```

- [ ] **Step 2: Run to verify compilation fails**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=UserPairTest,FriendRepositoryDefaultsTest,ParticipantRepositoryDefaultsTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: COMPILATION ERROR — `cannot find symbol: method of`, `existsFriendship`, `findActiveParticipant`.

- [ ] **Step 3: Add the helpers**

`ParticipantEntity.java` — add after the last field (`mutedUntil`):

```java
    public boolean isActive() {
        return leftAt == null && deletedAt == null;
    }
```

`FriendEntity.java` — add before `normalizeUserOrder()`:

```java
    /** The friend on the other side of this friendship from {@code userId}. */
    public UserEntity otherUser(UUID userId) {
        return userA.getId().equals(userId) ? userB : userA;
    }
```

`UserPair.java` — replace the whole file:

```java
package com.chat_socket.dto;

import java.util.UUID;

/** Two user ids in canonical order (smaller {@code toString()} first) so (a,b) and (b,a) look up the same row. */
public record UserPair(UUID userAId, UUID userBId) {
    public static UserPair of(UUID first, UUID second) {
        return first.toString().compareTo(second.toString()) <= 0
                ? new UserPair(first, second)
                : new UserPair(second, first);
    }
}
```

`FriendRepository.java` — add right after `existsByUserAIdAndUserBId`:

```java
    default boolean existsFriendship(UUID userId, UUID otherUserId) {
        UserPair pair = UserPair.of(userId, otherUserId);
        return existsByUserAIdAndUserBId(pair.userAId(), pair.userBId());
    }
```

and add `import com.chat_socket.dto.UserPair;`.

`ParticipantRepository.java` — add right after `findByIdConversationIdAndIdUserId`:

```java
    default Optional<ParticipantEntity> findActiveParticipant(UUID conversationId, UUID userId) {
        return findByIdConversationIdAndIdUserId(conversationId, userId).filter(ParticipantEntity::isActive);
    }
```

- [ ] **Step 4: Run the new tests and the full suite**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS; 7 new tests pass, all Phase 1 tests still pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/chat_socket/entity/ParticipantEntity.java src/main/java/com/chat_socket/entity/FriendEntity.java src/main/java/com/chat_socket/dto/UserPair.java src/main/java/com/chat_socket/repository/FriendRepository.java src/main/java/com/chat_socket/repository/ParticipantRepository.java src/test/java/com/chat_socket/dto/UserPairTest.java src/test/java/com/chat_socket/repository
git commit -m "refactor: add isActive, otherUser, UserPair.of, existsFriendship, findActiveParticipant helpers"
```

---

### Task 11: Switch all callers to the new helpers; delete `Normalize.normalizeUserPair`

**Files:**
- Modify: `src/main/java/com/chat_socket/utils/Normalize.java` (delete `normalizeUserPair` + unused imports)
- Modify: `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`
- Modify: `src/main/java/com/chat_socket/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`
- Modify: `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`
- Modify: `src/main/java/com/chat_socket/security/GroupPermission.java`
- Modify: `src/main/java/com/chat_socket/security/MessageDirectPermission.java`
- Modify tests: `NormalizeTest`, `FriendServiceImplTest`, `UserServiceImplTest`, `ConversationServiceImplTest`, `GroupPermissionTest`, `MessageDirectPermissionTest`

**Interfaces:**
- Consumes: everything Task 10 produced.
- After this task `Normalize` has no `UserPair` dependency and nobody outside repositories calls `existsByUserAIdAndUserBId`.

- [ ] **Step 1: Update tests first (they will fail to compile / fail until Step 2)**

`NormalizeTest.java`: delete `normalizeUserPair_ordersBySmallerUuidStringFirst` and the imports `com.chat_socket.dto.UserPair`, `java.util.UUID`.

`FriendServiceImplTest.java`: replace every `friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)` with `friendRepository.existsFriendship(BIG, SMALL)` (3 occurrences). Keep `deleteByUserAIdAndUserBId(SMALL, BIG)` as is — deletion still calls the derived query directly.

`UserServiceImplTest.java`: replace every `friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)` with `friendRepository.existsFriendship(BIG, SMALL)` (4 occurrences).

`ConversationServiceImplTest.java`:
- In `stubGroupWithMe`: `participantRepository.findByIdConversationIdAndIdUserId(C, BIG)` → `participantRepository.findActiveParticipant(C, BIG)`.
- `markAsSeen_notParticipant_throwsForbidden`: `findByIdConversationIdAndIdUserId(C, BIG)` → `findActiveParticipant(C, BIG)`.
- Delete `markAsSeen_leftParticipant_throwsForbidden` (now covered by `ParticipantRepositoryDefaultsTest`).
- `removeGroupMember_targetNotInGroup_throwsNotFound`, `removeGroupMember_targetIsAdmin_throwsBadRequest`, `removeGroupMember_success_marksTargetLeftAndPublishes`: `findByIdConversationIdAndIdUserId(C, SMALL)` → `findActiveParticipant(C, SMALL)`.
- Every `friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)` → `friendRepository.existsFriendship(BIG, SMALL)` (4 occurrences); in `addGroupMembers_onlySelf_returnsCurrentStateWithoutChanges` the `never()` line becomes `verify(friendRepository, never()).existsFriendship(any(), any());`.

`GroupPermissionTest.java`: in `stubGroupWithMe` and `notParticipant_throwsForbidden`, `findByIdConversationIdAndIdUserId(C, ME)` → `findActiveParticipant(C, ME)`. Delete `leftParticipant_throwsForbidden`.

`MessageDirectPermissionTest.java`: `existsByUserAIdAndUserBId(SMALL, BIG)` → `existsFriendship(BIG, SMALL)`; `existsByUserAIdAndUserBId(BIG, THIRD)` → `existsFriendship(BIG, THIRD)` (2 occurrences each).

- [ ] **Step 2: Swap the callers in main code**

`Normalize.java`: delete the `normalizeUserPair` method and the imports `com.chat_socket.dto.UserPair` and `java.util.UUID`.

`FriendServiceImpl.java`:
- `sendFriendRequest`: replace
  ```java
  UserPair pair = Normalize.normalizeUserPair(fromUserId, toUserId);
  if (friendRepository.existsByUserAIdAndUserBId(pair.userAId(), pair.userBId()))
  ```
  with
  ```java
  if (friendRepository.existsFriendship(fromUserId, toUserId))
  ```
- `deleteFriend`: `UserPair pair = Normalize.normalizeUserPair(currentUser.id(), friendId);` → `UserPair pair = UserPair.of(currentUser.id(), friendId);`
- `getListFriend`: `friendMapper.toFriendDto(getFriendUser(friendship, userId))` → `friendMapper.toFriendDto(friendship.otherUser(userId))`; delete the private `getFriendUser` method.
- Remove the now-unused `import com.chat_socket.utils.Normalize;` only if `Normalize.normalizeUsernamePattern/normalizeTextPattern` are no longer referenced (they still are in `getListFriend` — keep the import).

`UserServiceImpl.java`:
- `hasFriendship`: body becomes
  ```java
  if (currentUserId.equals(userId)) return false;
  return friendRepository.existsFriendship(currentUserId, userId);
  ```
- `searchUsers`: `getFriendUser(friendship, currentUser.id()).getId()` → `friendship.otherUser(currentUser.id()).getId()`; delete the private `getFriendUser` method.
- Remove `import com.chat_socket.dto.UserPair;`.

`MessageServiceImpl.java`:
- `findOrCreateDirectConversation`: `UserPair pair = Normalize.normalizeUserPair(senderId, recipientId);` → `UserPair pair = UserPair.of(senderId, recipientId);`; remove `import com.chat_socket.utils.Normalize;`.

`ConversationServiceImpl.java`:
- `markAsSeen`: replace the participant lookup + `leftAt/deletedAt` check (the `ParticipantEntity participant = participantRepository.findByIdConversationIdAndIdUserId(...)...orElseThrow(...)` plus the following `if (participant.getLeftAt() != null || ...) throw ...`) with `ParticipantEntity participant = getActiveParticipantOrThrow(conversationId, currentUser.id());`
- `addGroupMembers` loop: `if (participant.getLeftAt() != null || participant.getDeletedAt() != null) {` → `if (!participant.isActive()) {`
- `removeGroupMember`: replace
  ```java
  ParticipantEntity targetParticipant = participantRepository
          .findByIdConversationIdAndIdUserId(conversationId, memberId)
          .orElseThrow(() -> new NotFoundException("Participant not found."));
  if (targetParticipant.getLeftAt() != null || targetParticipant.getDeletedAt() != null)
      throw new NotFoundException("Participant not found.");
  ```
  with
  ```java
  ParticipantEntity targetParticipant = participantRepository
          .findActiveParticipant(conversationId, memberId)
          .orElseThrow(() -> new NotFoundException("Participant not found."));
  ```
- `getActiveParticipantOrThrow`: body becomes
  ```java
  return participantRepository
          .findActiveParticipant(conversationId, userId)
          .orElseThrow(() -> new ForbiddenException("You are not a participant of this conversation."));
  ```
- `ensureFriendWithAllMembers`: replace
  ```java
  UserPair pair = Normalize.normalizeUserPair(currentUserId, memberId);
  if (!friendRepository.existsByUserAIdAndUserBId(pair.userAId(), pair.userBId())) {
  ```
  with
  ```java
  if (!friendRepository.existsFriendship(currentUserId, memberId)) {
  ```
- `createDirectConversation(UUID, List<UUID>)`: `UserPair pair = Normalize.normalizeUserPair(currentUserId, participantId);` → `UserPair pair = UserPair.of(currentUserId, participantId);`
- Remove `import com.chat_socket.utils.Normalize;`.

`GroupPermission.java`: replace
```java
ParticipantEntity participant = participantRepository
        .findByIdConversationIdAndIdUserId(conversationId, currentUser.id())
        .orElseThrow(() -> new ForbiddenException("You are not a participant of this conversation."));
if (participant.getLeftAt() != null || participant.getDeletedAt() != null)
    throw new ForbiddenException("You are not a participant of this conversation.");
```
with
```java
ParticipantEntity participant = participantRepository
        .findActiveParticipant(conversationId, currentUser.id())
        .orElseThrow(() -> new ForbiddenException("You are not a participant of this conversation."));
```

`MessageDirectPermission.java`: in `canSendDirect(List<UUID>)`, `!hasFriendship(currentUser.id(), memberId)` → `!friendRepository.existsFriendship(currentUser.id(), memberId)`; delete the private `hasFriendship` method and the imports `com.chat_socket.dto.UserPair`, `com.chat_socket.utils.Normalize`.

- [ ] **Step 3: Run the full suite**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS. Spotless `removeUnusedImports` will drop any import you forgot; if compilation complains about `UserPair`/`Normalize` symbols, you missed a replacement listed above.

- [ ] **Step 4: Confirm no stray callers remain**

Run: `git grep -n "normalizeUserPair\|getFriendUser\|getLeftAt() != null" -- src/main`
Expected: no output.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/chat_socket/utils/Normalize.java src/main/java/com/chat_socket/service/impl src/main/java/com/chat_socket/security/GroupPermission.java src/main/java/com/chat_socket/security/MessageDirectPermission.java src/test/java/com/chat_socket/utils/NormalizeTest.java src/test/java/com/chat_socket/service/impl src/test/java/com/chat_socket/security
git commit -m "refactor: use isActive/otherUser/UserPair.of/existsFriendship/findActiveParticipant at call sites"
```

---

### Task 12: ConversationServiceImpl — publish helper, `findOrCreateDirectConversation`, throw 400s

**Files:**
- Modify: `src/main/java/com/chat_socket/service/ConversationService.java`
- Modify: `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java` (replace whole file)
- Modify tests: `ConversationServiceImplTest`

**Interfaces:**
- Produces: `ConversationEntity ConversationService.findOrCreateDirectConversation(UUID currentUserId, UUID otherUserId)` — returns the existing DIRECT conversation between the two users or creates it (with both participants). Throws `NotFoundException("User not found.")` if either user is missing. Task 13 consumes it.
- Behaviour change (approved in spec): `createConversation` DIRECT path used to answer `"Member not found."` for a missing other user; it now answers `"User not found."` (same 404).
- `400`-as-return → `throw new BadRequestException(...)` in `createConversation`, `createDirectConversation`, `createGroupConversation`. Client sees the same status and message via `GlobalExceptionHandler`.

- [ ] **Step 1: Update the tests**

In `ConversationServiceImplTest.java` change the four `// 400-as-return` tests to expect the exception:

```java
    @Test
    void createConversation_unknownType_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(new ConversationRequest(null, "x", List.of(SMALL))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Conversation type is invalid.");
    }

    @Test
    void createDirect_moreThanOneMember_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL, THIRD))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Direct conversation requires exactly one member.");
    }

    @Test
    void createDirect_withSelf_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.DIRECT, null, List.of(BIG))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot create a direct conversation with yourself.");
    }

    @Test
    void createGroup_blankName_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.GROUP, "  ", List.of(SMALL))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Group name is required.");
    }
```

Add two tests for the new public method:

```java
    @Test
    void findOrCreateDirectConversation_missingOtherUser_throwsNotFound() {
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrCreateDirectConversation(BIG, SMALL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void findOrCreateDirectConversation_existing_returnsItWithoutSaving() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.DIRECT);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(conversationRepository.findDirectConversation(ConversationType.DIRECT, SMALL, BIG))
                .thenReturn(Optional.of(conversation));

        assertThat(service.findOrCreateDirectConversation(BIG, SMALL)).isSameAs(conversation);
        verify(conversationRepository, never()).saveAndFlush(any());
    }
```

- [ ] **Step 2: Add the method to the interface**

`ConversationService.java` — add:

```java
    /** Existing DIRECT conversation between the two users, or a new one with both as participants. */
    ConversationEntity findOrCreateDirectConversation(UUID currentUserId, UUID otherUserId);
```

and `import com.chat_socket.entity.ConversationEntity;`.

- [ ] **Step 3: Replace `ConversationServiceImpl.java` with this file**

```java
package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.GroupMembersRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateGroupRequest;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.ConversationMapper;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.ConversationService;
import com.chat_socket.socket.SocketPublisher;
import com.chat_socket.utils.PaginationUtils;
import com.chat_socket.utils.Security;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationServiceImpl implements ConversationService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final SocketPublisher socketPublisher;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            FriendRepository friendRepository,
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            SocketPublisher socketPublisher) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.friendRepository = friendRepository;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.socketPublisher = socketPublisher;
    }

    @Override
    public BaseResponse<PaginationResponse<ConversationDto>> getConversations(
            PaginationRequest request, ConversationType type) {
        UserSecurity currentUser = Security.getCurrentUser();

        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(request);

        List<UUID> fetchedConversationIds = page.cursor() == null
                ? conversationRepository.findActiveConversationIdsForUser(currentUser.id(), type, page.pageRequest())
                : conversationRepository.findActiveConversationIdsForUserBeforeCursor(
                        currentUser.id(), type, page.cursor(), page.pageRequest());

        if (fetchedConversationIds.isEmpty())
            return new BaseResponse<>(
                    new PaginationResponse<>(List.of(), null),
                    "Conversations retrieved successfully.",
                    HttpStatus.OK.value());

        List<UUID> conversationIds = page.items(fetchedConversationIds);
        Map<UUID, ConversationEntity> conversationsById =
                conversationRepository.findConversationsWithDetails(conversationIds).stream()
                        .collect(Collectors.toMap(ConversationEntity::getId, Function.identity()));
        Map<UUID, Long> unreadCounts =
                messageRepository.countUnreadMessagesByConversation(currentUser.id(), conversationIds).stream()
                        .collect(Collectors.toMap(
                                MessageRepository.UnreadCountProjection::getConversationId,
                                MessageRepository.UnreadCountProjection::getUnreadCount));

        PaginationResponse<ConversationDto> body = PaginationUtils.toCursorResponse(
                fetchedConversationIds,
                page,
                conversationId -> conversationMapper.toDto(
                        conversationsById.get(conversationId), unreadCounts.getOrDefault(conversationId, 0L)),
                conversationId -> {
                    ConversationEntity conversation = conversationsById.get(conversationId);
                    return conversation.getLastMessageAt() == null
                            ? conversation.getUpdatedAt()
                            : conversation.getLastMessageAt();
                },
                false);

        return new BaseResponse<>(body, "Conversations retrieved successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> createConversation(ConversationRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();

        if (request.type() == ConversationType.DIRECT)
            return createDirectConversation(currentUser.id(), request.memberIds());

        if (request.type() == ConversationType.GROUP)
            return createGroupConversation(currentUser.id(), request.name(), request.memberIds());

        throw new BadRequestException("Conversation type is invalid.");
    }

    @Override
    @Transactional
    public ConversationEntity findOrCreateDirectConversation(UUID currentUserId, UUID otherUserId) {
        UserEntity currentUser =
                userRepository.findById(currentUserId).orElseThrow(() -> new NotFoundException("User not found."));
        UserEntity otherUser =
                userRepository.findById(otherUserId).orElseThrow(() -> new NotFoundException("User not found."));

        UserPair pair = UserPair.of(currentUserId, otherUserId);
        return conversationRepository
                .findDirectConversation(ConversationType.DIRECT, pair.userAId(), pair.userBId())
                .orElseGet(() -> createDirectConversation(currentUser, otherUser, pair));
    }

    @Override
    public BaseResponse<PaginationResponse<MessageDto>> getMessages(UUID conversationId, PaginationRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();

        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(request);

        ensureCanReadConversation(conversationId, currentUser.id());

        List<MessageEntity> fetchedMessages = page.cursor() == null
                ? messageRepository.findLatestMessages(conversationId, page.pageRequest())
                : messageRepository.findMessagesBeforeCursor(conversationId, page.cursor(), page.pageRequest());

        PaginationResponse<MessageDto> body = PaginationUtils.toCursorResponse(
                fetchedMessages, page, messageMapper::toDto, MessageEntity::getCreatedAt, true);
        return new BaseResponse<>(body, "Messages retrieved successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<Void> markAsSeen(UUID conversationId) {
        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        ParticipantEntity participant = getActiveParticipantOrThrow(conversationId, currentUser.id());

        MessageEntity lastMessage = conversation.getLastMessage();
        if (lastMessage == null) return new BaseResponse<>(null, "No messages to mark as seen.", HttpStatus.OK.value());

        if (participant.getLastReadMessage() != null
                && participant.getLastReadMessage().getId().equals(lastMessage.getId()))
            return new BaseResponse<>(null, "Messages already marked as seen.", HttpStatus.OK.value());

        LocalDateTime seenAt = LocalDateTime.now();

        participant.setLastReadMessage(lastMessage);
        participant.setLastReadAt(seenAt);
        participantRepository.save(participant);

        socketPublisher.publishConversationSeenAfterCommit(
                conversationId,
                currentUser.id(),
                messageMapper.toDto(lastMessage),
                conversation.getLastMessageAt(),
                seenAt);

        return new BaseResponse<>(null, "Messages marked as seen successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<Void> deleteGroup(UUID conversationId) {
        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        ParticipantEntity participant = getActiveAdminParticipantOrThrow(conversationId, currentUser.id());

        LocalDateTime deletedAt = LocalDateTime.now();
        MessageEntity lastMessage = conversation.getLastMessage();
        participant.setDeletedAt(deletedAt);
        if (lastMessage != null) {
            participant.setLastReadMessage(lastMessage);
            participant.setLastReadAt(deletedAt);
        }
        participantRepository.save(participant);

        socketPublisher.publishGroupDeletedAfterCommit(conversationId, currentUser.id());

        return new BaseResponse<>(null, "Group deleted successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> updateGroup(UUID conversationId, UpdateGroupRequest request) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new BadRequestException("Group name is required.");

        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        getActiveAdminParticipantOrThrow(conversationId, currentUser.id());

        conversation.setGroupName(request.name().trim());
        conversationRepository.save(conversation);

        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);
        publishConversationUpdated(conversation);

        return new BaseResponse<>(
                conversationMapper.toDto(updatedConversation), "Group updated successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> addGroupMembers(UUID conversationId, GroupMembersRequest request) {
        if (request == null
                || request.memberIds() == null
                || request.memberIds().isEmpty()) throw new BadRequestException("Member ids are required.");

        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        getActiveParticipantOrThrow(conversationId, currentUser.id());

        LinkedHashSet<UUID> uniqueMemberIds = new LinkedHashSet<>(request.memberIds());
        uniqueMemberIds.remove(currentUser.id());

        if (uniqueMemberIds.isEmpty()) {
            ConversationEntity updatedConversation = findConversationWithDetails(conversationId);
            return new BaseResponse<>(
                    conversationMapper.toDto(updatedConversation), "Members already in group.", HttpStatus.OK.value());
        }

        ensureFriendWithAllMembers(currentUser.id(), new ArrayList<>(uniqueMemberIds));

        Map<UUID, UserEntity> usersById = userRepository.findAllById(uniqueMemberIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        if (usersById.size() != uniqueMemberIds.size())
            throw new NotFoundException("One or more members were not found.");

        Map<UUID, ParticipantEntity> existingParticipants =
                participantRepository.findByConversationIdAndIdUserIdIn(conversationId, uniqueMemberIds).stream()
                        .collect(Collectors.toMap(
                                participant -> participant.getId().getUserId(), Function.identity()));

        boolean hasChanges = false;
        LocalDateTime now = LocalDateTime.now();
        for (UUID memberId : uniqueMemberIds) {
            ParticipantEntity participant = existingParticipants.get(memberId);
            if (participant == null) {
                createParticipant(conversation, usersById.get(memberId), ParticipantRole.MEMBER);
                hasChanges = true;
                continue;
            }

            if (!participant.isActive()) {
                participant.setLeftAt(null);
                participant.setDeletedAt(null);
                participant.setRole(ParticipantRole.MEMBER);
                participant.setJoinedAt(now);
                participantRepository.save(participant);
                hasChanges = true;
            }
        }

        if (hasChanges) conversationRepository.save(conversation);

        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);
        publishConversationUpdated(conversation);

        return new BaseResponse<>(
                conversationMapper.toDto(updatedConversation), "Members added successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> removeGroupMember(UUID conversationId, UUID memberId) {
        if (memberId == null) throw new BadRequestException("Member id is required.");

        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        ParticipantEntity currentParticipant = getActiveParticipantOrThrow(conversationId, currentUser.id());
        if (currentParticipant.getRole() != ParticipantRole.ADMIN)
            throw new ForbiddenException("Only admins can manage group members.");

        if (currentUser.id().equals(memberId))
            throw new BadRequestException("You cannot remove yourself. Use leave endpoint instead.");

        ParticipantEntity targetParticipant = participantRepository
                .findActiveParticipant(conversationId, memberId)
                .orElseThrow(() -> new NotFoundException("Participant not found."));
        if (targetParticipant.getRole() == ParticipantRole.ADMIN)
            throw new BadRequestException("You cannot remove an admin from this group.");

        targetParticipant.setLeftAt(LocalDateTime.now());
        participantRepository.save(targetParticipant);
        conversationRepository.save(conversation);

        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);
        publishConversationUpdated(conversation);

        return new BaseResponse<>(
                conversationMapper.toDto(updatedConversation),
                "Member removed from group successfully.",
                HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<Void> leaveGroup(UUID conversationId) {
        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        ParticipantEntity currentParticipant = getActiveParticipantOrThrow(conversationId, currentUser.id());

        if (currentParticipant.getRole() == ParticipantRole.ADMIN) {
            long activeAdmins = participantRepository.countActiveByConversationIdAndRoleAndIdUserIdNot(
                    conversationId, ParticipantRole.ADMIN, currentUser.id());
            if (activeAdmins == 0) throw new BadRequestException("You are the only admin.You can't leaving.");
        }

        currentParticipant.setLeftAt(LocalDateTime.now());
        participantRepository.save(currentParticipant);
        conversationRepository.save(conversation);

        publishConversationUpdated(conversation);

        return new BaseResponse<>(null, "Left group successfully.", HttpStatus.OK.value());
    }

    private ConversationEntity getGroupConversationOrThrow(UUID conversationId) {
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));

        if (conversation.getType() != ConversationType.GROUP)
            throw new NotFoundException("Group conversation not found.");

        return conversation;
    }

    private ParticipantEntity getActiveParticipantOrThrow(UUID conversationId, UUID userId) {
        return participantRepository
                .findActiveParticipant(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a participant of this conversation."));
    }

    private ParticipantEntity getActiveAdminParticipantOrThrow(UUID conversationId, UUID userId) {
        ParticipantEntity participant = getActiveParticipantOrThrow(conversationId, userId);
        if (participant.getRole() != ParticipantRole.ADMIN)
            throw new ForbiddenException("Only admins can manage this group.");

        return participant;
    }

    private void ensureFriendWithAllMembers(UUID currentUserId, List<UUID> memberIds) {
        List<UUID> notFriends = new ArrayList<>();
        for (UUID memberId : memberIds) {
            if (memberId == null) continue;

            if (!friendRepository.existsFriendship(currentUserId, memberId)) {
                notFriends.add(memberId);
            }
        }

        if (!notFriends.isEmpty())
            throw new FriendPermissionException("You can only add friends to a group.", notFriends);
    }

    private ConversationEntity findConversationWithDetails(UUID conversationId) {
        return conversationRepository.findConversationsWithDetails(List.of(conversationId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
    }

    private void ensureCanReadConversation(UUID conversationId, UUID userId) {
        if (!conversationRepository.existsById(conversationId)) throw new NotFoundException("Conversation not found.");

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, userId))
            throw new ForbiddenException("You are not a participant of this conversation.");
    }

    /** Tells every active participant that the conversation changed (name, members, last message). */
    private void publishConversationUpdated(ConversationEntity conversation) {
        MessageDto lastMessage =
                conversation.getLastMessage() == null ? null : messageMapper.toDto(conversation.getLastMessage());
        socketPublisher.publishConversationUpdatedAfterCommit(
                conversation.getId(), lastMessage, conversation.getLastMessageAt());
    }

    private BaseResponse<ConversationDto> createDirectConversation(UUID currentUserId, List<UUID> memberIds) {
        if (memberIds.size() != 1) throw new BadRequestException("Direct conversation requires exactly one member.");

        UUID participantId = memberIds.getFirst();
        if (currentUserId.equals(participantId))
            throw new BadRequestException("You cannot create a direct conversation with yourself.");

        ConversationEntity conversation = findOrCreateDirectConversation(currentUserId, participantId);
        participantRepository.restoreDeletedParticipant(conversation.getId(), currentUserId);
        conversation = findConversationWithDetails(conversation.getId());

        return new BaseResponse<>(
                conversationMapper.toDto(conversation),
                "Conversation created successfully.",
                HttpStatus.CREATED.value());
    }

    private BaseResponse<ConversationDto> createGroupConversation(
            UUID currentUserId, String name, List<UUID> memberIds) {
        if (name == null || name.isBlank()) throw new BadRequestException("Group name is required.");

        UserEntity currentUser =
                userRepository.findById(currentUserId).orElseThrow(() -> new NotFoundException("User not found."));

        LinkedHashSet<UUID> participantIds = new LinkedHashSet<>();
        participantIds.add(currentUserId);
        participantIds.addAll(memberIds);

        Map<UUID, UserEntity> usersById = userRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        if (usersById.size() != participantIds.size())
            throw new NotFoundException("One or more members were not found.");

        ConversationEntity conversation = new ConversationEntity();
        conversation.setType(ConversationType.GROUP);
        conversation.setGroupName(name.trim());
        conversation.setCreatedBy(currentUser);
        conversation.setLastMessageAt(LocalDateTime.now());

        conversation = conversationRepository.saveAndFlush(conversation);

        List<ParticipantEntity> participants = new ArrayList<>();
        participants.add(createParticipant(conversation, currentUser, ParticipantRole.ADMIN));
        for (UUID participantId : participantIds) {
            if (!participantId.equals(currentUserId))
                participants.add(createParticipant(conversation, usersById.get(participantId), ParticipantRole.MEMBER));
        }
        participantRepository.flush();
        conversation.setParticipants(participants);

        return new BaseResponse<>(
                conversationMapper.toDto(conversation),
                "Conversation created successfully.",
                HttpStatus.CREATED.value());
    }

    private ConversationEntity createDirectConversation(UserEntity currentUser, UserEntity participant, UserPair pair) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setType(ConversationType.DIRECT);
        conversation.setCreatedBy(currentUser);
        conversation.setLastMessageAt(LocalDateTime.now());

        if (pair.userAId().equals(currentUser.getId())) {
            conversation.setDirectUserA(currentUser);
            conversation.setDirectUserB(participant);
        } else {
            conversation.setDirectUserA(participant);
            conversation.setDirectUserB(currentUser);
        }

        conversation = conversationRepository.saveAndFlush(conversation);

        List<ParticipantEntity> participants = List.of(
                createParticipant(conversation, currentUser, ParticipantRole.MEMBER),
                createParticipant(conversation, participant, ParticipantRole.MEMBER));
        participantRepository.flush();
        conversation.setParticipants(participants);
        return conversation;
    }

    private ParticipantEntity createParticipant(
            ConversationEntity conversation, UserEntity user, ParticipantRole participantRole) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(new ParticipantIdEntity(conversation.getId(), user.getId()));
        participant.setConversation(conversation);
        participant.setUser(user);
        participant.setRole(participantRole);
        return participantRepository.save(participant);
    }
}
```

- [ ] **Step 4: Run the full suite**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS, `ConversationServiceImplTest` now has 39 tests (37 kept + 2 new; the left-participant test was removed in Task 11).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/chat_socket/service/ConversationService.java src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java src/test/java/com/chat_socket/service/impl/ConversationServiceImplTest.java
git commit -m "refactor: ConversationServiceImpl publish helper, findOrCreateDirectConversation, throw 400s"
```

---

### Task 13: MessageServiceImpl — delegate direct-conversation creation, single `toDto`, throw 400s

**Files:**
- Modify: `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java` (replace whole file)
- Modify tests: `MessageServiceImplTest`

**Interfaces:**
- Consumes: `ConversationService.findOrCreateDirectConversation(UUID, UUID)` from Task 12.
- Constructor becomes `MessageServiceImpl(ConversationRepository, MessageRepository, ParticipantRepository, UserRepository, MessageMapper, SocketPublisher, ConversationService)`.
- Deleted private methods: `findOrCreateDirectConversation`, `createDirectConversation`, `createParticipant`.

- [ ] **Step 1: Update the test**

In `MessageServiceImplTest.java`:

1. Add `@Mock ConversationService conversationService;` (import `com.chat_socket.service.ConversationService`) and pass it as the last constructor argument in `setUp()`.
2. Change the three `returns400` tests to expect `BadRequestException` (import `com.chat_socket.exception.BadRequestException`):

```java
    @Test
    void sendDirectMessage_blankContent_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(SMALL, "  ", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content is required.");
    }

    @Test
    void sendDirectMessage_noRecipientAndNoConversation_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(null, "hi", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Recipient is required.");
    }

    @Test
    void sendDirectMessage_toSelf_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(BIG, "hi", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot send a direct message to yourself.");
    }

    @Test
    void sendGroupMessage_blankContent_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendGroupMessage(new MessageRequest(null, "", null, CONVERSATION_ID, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content is required.");
    }

    @Test
    void sendGroupMessage_noConversation_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendGroupMessage(new MessageRequest(null, "hi", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Conversation is required.");
    }
```

3. Replace `sendDirectMessage_byRecipientWithoutConversation_createsDirectConversationWithTwoParticipants` with:

```java
    @Test
    void sendDirectMessage_byRecipientWithoutConversation_delegatesToConversationService() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT);
        when(conversationService.findOrCreateDirectConversation(BIG, SMALL)).thenReturn(conversation);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response = service.sendDirectMessage(new MessageRequest(SMALL, "hi", null, null, null));

        assertThat(response.status()).isEqualTo(201);
        verify(conversationRepository, never()).saveAndFlush(any());
        verify(socketPublisher).publishMessageAfterCommit(CONVERSATION_ID, dto, TestFixtures.FIXED_TIME);
    }
```

Remove the now-unused `times` static import if the compiler/Spotless flags it.

- [ ] **Step 2: Replace `MessageServiceImpl.java` with this file**

```java
package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageRequest;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.ConversationService;
import com.chat_socket.service.MessageService;
import com.chat_socket.socket.SocketPublisher;
import com.chat_socket.utils.Security;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageServiceImpl implements MessageService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;
    private final SocketPublisher socketPublisher;
    private final ConversationService conversationService;

    public MessageServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            MessageMapper messageMapper,
            SocketPublisher socketPublisher,
            ConversationService conversationService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.messageMapper = messageMapper;
        this.socketPublisher = socketPublisher;
        this.conversationService = conversationService;
    }

    @Override
    @Transactional
    public BaseResponse<MessageDto> sendDirectMessage(MessageRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID senderId = currentUser.id();

        if (request.content() == null || request.content().isBlank())
            throw new BadRequestException("Content is required.");

        if (request.conversationId() == null && request.recipientId() == null)
            throw new BadRequestException("Recipient is required.");

        if (request.conversationId() == null && senderId.equals(request.recipientId()))
            throw new BadRequestException("You cannot send a direct message to yourself.");

        ConversationEntity conversation = request.conversationId() != null
                ? getDirectConversationForSender(request.conversationId(), senderId)
                : conversationService.findOrCreateDirectConversation(senderId, request.recipientId());

        UserEntity sender =
                userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));

        MessageEntity message = createMessage(conversation, sender, request);
        MessageDto messageDto = messageMapper.toDto(message);

        socketPublisher.publishMessageAfterCommit(conversation.getId(), messageDto, conversation.getLastMessageAt());

        return new BaseResponse<>(messageDto, "Message sent successfully.", HttpStatus.CREATED.value());
    }

    @Override
    @Transactional
    public BaseResponse<MessageDto> sendGroupMessage(MessageRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID senderId = currentUser.id();

        if (request.content() == null || request.content().isBlank())
            throw new BadRequestException("Content is required.");

        if (request.conversationId() == null) throw new BadRequestException("Conversation is required.");

        ConversationEntity conversation = getGroupConversationForSender(request.conversationId(), senderId);
        UserEntity sender =
                userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));

        MessageEntity message = createMessage(conversation, sender, request);
        MessageDto messageDto = messageMapper.toDto(message);

        socketPublisher.publishMessageAfterCommit(conversation.getId(), messageDto, conversation.getLastMessageAt());

        return new BaseResponse<>(messageDto, "Message sent successfully.", HttpStatus.CREATED.value());
    }

    private MessageEntity createMessage(ConversationEntity conversation, UserEntity sender, MessageRequest request) {
        participantRepository.restoreDeletedParticipantsByConversationId(conversation.getId());

        MessageEntity message = new MessageEntity();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());
        message.setAttachmentUrl(request.attachmentUrl());
        message.setType(request.type() == null ? MessageType.TEXT : request.type());

        message = messageRepository.saveAndFlush(message);

        LocalDateTime messageCreatedAt = message.getCreatedAt() == null ? LocalDateTime.now() : message.getCreatedAt();
        conversation.setLastMessage(message);
        conversation.setLastMessageAt(messageCreatedAt);
        conversationRepository.save(conversation);

        markSenderAsRead(conversation, sender, message, messageCreatedAt);

        return message;
    }

    private ConversationEntity getDirectConversationForSender(UUID conversationId, UUID senderId) {
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));

        if (conversation.getType() != ConversationType.DIRECT)
            throw new NotFoundException("Direct conversation not found.");

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, senderId))
            throw new ForbiddenException("You are not a participant of this conversation.");

        return conversation;
    }

    private ConversationEntity getGroupConversationForSender(UUID conversationId, UUID senderId) {
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));

        if (conversation.getType() != ConversationType.GROUP)
            throw new NotFoundException("Group conversation not found.");

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, senderId))
            throw new ForbiddenException("You are not a participant of this conversation.");

        return conversation;
    }

    private void markSenderAsRead(
            ConversationEntity conversation, UserEntity sender, MessageEntity message, LocalDateTime readAt) {
        ParticipantEntity participant = participantRepository
                .findByIdConversationIdAndIdUserId(conversation.getId(), sender.getId())
                .orElseThrow(() -> new NotFoundException("Participant not found."));
        participant.setLastReadMessage(message);
        participant.setLastReadAt(readAt);
        participantRepository.save(participant);
    }
}
```

Note: `markSenderAsRead` keeps `findByIdConversationIdAndIdUserId` (not `findActiveParticipant`) on purpose — `restoreDeletedParticipantsByConversationId` runs as a bulk JPQL update just before, so the in-memory entity may still carry a stale `deletedAt`; filtering on it would wrongly throw.

- [ ] **Step 3: Run the full suite**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java src/test/java/com/chat_socket/service/impl/MessageServiceImplTest.java
git commit -m "refactor: MessageServiceImpl delegates direct-conversation creation, throws 400s"
```

---

### Task 14: FriendServiceImpl 400, AuthServiceImpl cookie helper + `WebUtils.getCookie`, PaginationUtils tidy

**Files:**
- Modify: `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`
- Modify: `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`
- Modify: `src/main/java/com/chat_socket/utils/PaginationUtils.java`
- Modify tests: `FriendServiceImplTest`

**Interfaces:**
- `AuthServiceImpl` gains `private ResponseCookie refreshTokenCookie(String value, Duration maxAge)`; `getCookieValue` is replaced by Spring's `org.springframework.web.util.WebUtils.getCookie(HttpServletRequest, String)` (stdlib rung of the ponytail ladder).
- No public signature changes.

- [ ] **Step 1: Update `FriendServiceImplTest`**

Replace `sendFriendRequest_toSelf_returns400` with (import `com.chat_socket.exception.BadRequestException`):

```java
    @Test
    void sendFriendRequest_toSelf_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendFriendRequest(new FriendSendRequest(BIG, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot send a friend request to yourself.");
    }
```

- [ ] **Step 2: FriendServiceImpl**

In `sendFriendRequest`, replace

```java
        if (fromUserId.equals(toUserId))
            return new BaseResponse<>(
                    null, "You cannot send a friend request to yourself.", HttpStatus.BAD_REQUEST.value());
```

with

```java
        if (fromUserId.equals(toUserId)) throw new BadRequestException("You cannot send a friend request to yourself.");
```

and add `import com.chat_socket.exception.BadRequestException;`.

- [ ] **Step 3: AuthServiceImpl**

Replace the three private methods `addRefreshTokenCookie`, `clearRefreshTokenCookie`, `getCookieValue` with:

```java
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie(refreshToken, Duration.ofDays(config.refreshTokenTtl())).toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie("", Duration.ZERO).toString());
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
        return Optional.ofNullable(WebUtils.getCookie(request, REFRESH_TOKEN_COOKIE_NAME)).map(Cookie::getValue);
    }
```

In `signOut` and `refresh`, replace `getCookieValue(request, REFRESH_TOKEN_COOKIE_NAME)` with `getRefreshTokenCookie(request)`. Add `import org.springframework.web.util.WebUtils;`. The `jakarta.servlet.http.Cookie` import stays.

- [ ] **Step 4: PaginationUtils.resolveCursorPage**

Replace the method body so it returns from inside the `try` instead of assigning a pre-declared variable:

```java
    public static CursorPage resolveCursorPage(PaginationRequest request) {
        try {
            int limit = request == null || request.limit() == null ? DEFAULT_LIMIT : request.limit();
            if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0.");

            limit = Math.min(limit, MAX_LIMIT);
            LocalDateTime cursor = parseDateTimeCursor(request == null ? null : request.cursor());
            return new CursorPage(limit, cursor, PageRequest.of(0, limit + 1));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Cursor is invalid.");
        }
    }
```

- [ ] **Step 5: Run the full suite**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS. `AuthServiceImplTest` cookie assertions (`refreshToken=refresh-token`, `Max-Age=0`, `SameSite=None`) still pass unchanged — that is the proof the helper is behaviour-neutral.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java src/main/java/com/chat_socket/utils/PaginationUtils.java src/test/java/com/chat_socket/service/impl/FriendServiceImplTest.java
git commit -m "refactor: unify 400 handling in FriendServiceImpl, cookie helper + WebUtils in AuthServiceImpl"
```

---

## Phase 3 — Security & socket cleanup

### Task 15: `JwtService.verifyAccessToken → Optional<UUID>`, `Security.extractBearerToken`

**Files:**
- Modify: `src/main/java/com/chat_socket/service/JwtService.java`
- Modify: `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`
- Modify: `src/main/java/com/chat_socket/utils/Security.java`
- Modify: `src/main/java/com/chat_socket/security/SecurityFilter.java`
- Modify: `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java`
- Modify tests: `JwtServiceImplTest`, `SecurityTest`, `SecurityFilterTest`, `SocketChannelInterceptorTest`

**Interfaces:**
- Produces: `Optional<UUID> JwtService.verifyAccessToken(String accessToken)` — empty for expired/tampered/garbage tokens (never throws).
- Produces: `static String Security.extractBearerToken(String authorizationHeader)` — the token after `"Bearer "`, trimmed; `null` when header is missing, has another scheme, or the token is blank.
- Deleted: `Security.getUserIdFromAccessToken(JwtService, String)`; constant `BEARER_PREFIX` moves from the two callers into `Security`.

- [ ] **Step 1: Update tests first**

`JwtServiceImplTest.java` — replace the three verify tests:

```java
    @Test
    void generateToken_thenVerify_returnsSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = service.generateToken(userId);

        assertThat(service.verifyAccessToken(token)).contains(userId);
    }

    @Test
    void verifyAccessToken_tamperedToken_isEmpty() {
        String token = service.generateToken(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.verifyAccessToken(tampered)).isEmpty();
    }

    @Test
    void verifyAccessToken_garbage_isEmpty() {
        assertThat(service.verifyAccessToken("not-a-jwt")).isEmpty();
        assertThat(service.verifyAccessToken("")).isEmpty();
    }
```

Remove the `JwtException` and `assertThatThrownBy` imports.

`SecurityTest.java` — add:

```java
    @Test
    void extractBearerToken_returnsTrimmedToken() {
        assertThat(Security.extractBearerToken("Bearer abc.def ")).isEqualTo("abc.def");
    }

    @Test
    void extractBearerToken_missingOrWrongSchemeOrBlank_returnsNull() {
        assertThat(Security.extractBearerToken(null)).isNull();
        assertThat(Security.extractBearerToken("Basic abc")).isNull();
        assertThat(Security.extractBearerToken("Bearer ")).isNull();
        assertThat(Security.extractBearerToken("Bearer    ")).isNull();
    }
```

`SecurityFilterTest.java`: `when(jwtService.verifyAccessToken("bad")).thenThrow(new JwtException("bad"));` → `when(jwtService.verifyAccessToken("bad")).thenReturn(Optional.empty());` and every `thenReturn(userId)` on `verifyAccessToken` → `thenReturn(Optional.of(userId))`. Remove the `JwtException` import.

`SocketChannelInterceptorTest.java`: same two substitutions; remove the `JwtException` import.

- [ ] **Step 2: Run to see the failures**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=JwtServiceImplTest,SecurityTest,SecurityFilterTest,SocketChannelInterceptorTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: COMPILATION ERROR (`extractBearerToken` missing; `Optional` vs `UUID` mismatch).

- [ ] **Step 3: JwtService + JwtServiceImpl**

`JwtService.java`:

```java
    /** User id from a valid access token; empty when the token is expired, tampered with, or malformed. */
    Optional<UUID> verifyAccessToken(String accessToken);
```

`JwtServiceImpl.java` — replace `verifyAccessToken`:

```java
    @Override
    public Optional<UUID> verifyAccessToken(String accessToken) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(accessTokenKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload()
                    .getSubject();
            return Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
```

Add `import io.jsonwebtoken.JwtException;` and `import java.util.Optional;` to both files as needed.

- [ ] **Step 4: Security utils**

Replace `Security.java` with:

```java
package com.chat_socket.utils;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import java.security.Principal;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class Security {
    private static final String BEARER_PREFIX = "Bearer ";

    public static UserSecurity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserSecurity currentUser))
            throw new IllegalStateException("Current user is not authenticated.");
        return currentUser;
    }

    /** Token part of an {@code Authorization: Bearer <token>} header, or null when absent/blank/other scheme. */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) return null;

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    public static UsernamePasswordAuthenticationToken getUserAuthentication(UserEntity user) {
        UserSecurity userSecurity = new UserSecurity(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAvatarUrl());
        return new UsernamePasswordAuthenticationToken(userSecurity, null, Collections.emptyList());
    }

    public static UserSecurity getUserSecurityFromPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserSecurity user) {
            return user;
        }
        return null;
    }
}
```

- [ ] **Step 5: SecurityFilter**

Delete the `BEARER_PREFIX` constant. Replace the start of `doFilterInternal` (everything up to and including the `userId == null` block) with:

```java
        String accessToken = Security.extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (accessToken == null) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Token not found.");
            return;
        }

        UUID userId = jwtService.verifyAccessToken(accessToken).orElse(null);
        if (userId == null) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "Token expired or invalid.");
            return;
        }
```

The rest of the method (find user → 404, set context, `filterChain.doFilter`) stays.

- [ ] **Step 6: SocketChannelInterceptor**

Delete the `BEARER_PREFIX` constant. Replace the start of `authenticateConnect` up to the `userId == null` check with:

```java
        String accessToken = Security.extractBearerToken(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION));
        if (accessToken == null) throw new NotFoundException("Token not found.");

        UUID userId = jwtService
                .verifyAccessToken(accessToken)
                .orElseThrow(() -> new ForbiddenException("Token expired or invalid."));
```

The rest (`userRepository.findById` → `accessor.setUser`) stays.

- [ ] **Step 7: Run the full suite**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/chat_socket/service/JwtService.java src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java src/main/java/com/chat_socket/utils/Security.java src/main/java/com/chat_socket/security/SecurityFilter.java src/main/java/com/chat_socket/security/SocketChannelInterceptor.java src/test/java/com/chat_socket/service/impl/JwtServiceImplTest.java src/test/java/com/chat_socket/utils/SecurityTest.java src/test/java/com/chat_socket/security/SecurityFilterTest.java src/test/java/com/chat_socket/security/SocketChannelInterceptorTest.java
git commit -m "refactor: JwtService returns Optional, Security.extractBearerToken shared by filter and interceptor"
```

---

### Task 16: Remove `RedisUtils`, fix `clearOnlineUsers`, `SocketChannel.ONLINE_USERS`, drop no-op direct-message guard

**Files:**
- Delete: `src/main/java/com/chat_socket/utils/RedisUtils.java`
- Modify: `src/main/java/com/chat_socket/socket/UserOnlineRegistry.java` (replace whole file)
- Modify: `src/main/java/com/chat_socket/constant/SocketChannel.java`
- Modify: `src/main/java/com/chat_socket/socket/SocketEventListener.java`
- Modify: `src/main/java/com/chat_socket/socket/SocketController.java`
- Modify: `src/main/java/com/chat_socket/security/MessageDirectPermission.java`
- Modify: `src/main/java/com/chat_socket/controller/MessageController.java`
- Create: `src/test/java/com/chat_socket/socket/UserOnlineRegistryTest.java`
- Modify tests: `MessageDirectPermissionTest`

**Interfaces:**
- `UserOnlineRegistry(StringRedisTemplate)` — public API unchanged: `markOnline(UUID, String)`, `markOffline(UUID, String)`, `onlineUserIds() → Set<UUID>`, `clearOnlineUsers()`.
- Produces: `SocketChannel.ONLINE_USERS = "/online-users"`.
- Deleted: `MessageDirectPermission.canSendDirect(UUID)` (always returned `true`) and the `@PreAuthorize("@messageDirectPermission.canSendDirect(#request.recipientId())")` on `MessageController.sendDirectMessage` that called it. Net behaviour: identical (the guard never denied).
- Bug fix: `clearOnlineUsers` previously called `DEL "chat-socket:online-user-sessions:*"` literally (no glob in `DEL`) so session keys survived restarts.

- [ ] **Step 1: Write the failing `UserOnlineRegistryTest`**

```java
package com.chat_socket.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.constant.Redis;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class UserOnlineRegistryTest {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SESSION_KEY = Redis.USER_SESSIONS_KEY_PREFIX + USER;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    SetOperations<String, String> setOperations;

    UserOnlineRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new UserOnlineRegistry(redisTemplate);
    }

    @Test
    void markOnline_addsSessionAndUserToSets() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        registry.markOnline(USER, "session-1");

        verify(setOperations).add(SESSION_KEY, "session-1");
        verify(setOperations).add(Redis.ONLINE_USERS_KEY, USER.toString());
    }

    @Test
    void markOffline_runsCleanupScriptWithBothKeys() {
        registry.markOffline(USER, "session-1");

        verify(redisTemplate)
                .execute(
                        Redis.REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT,
                        List.of(SESSION_KEY, Redis.ONLINE_USERS_KEY),
                        "session-1",
                        USER.toString());
    }

    @Test
    void onlineUserIds_parsesMembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(Redis.ONLINE_USERS_KEY)).thenReturn(Set.of(USER.toString()));

        assertThat(registry.onlineUserIds()).containsExactly(USER);
    }

    @Test
    void onlineUserIds_nullOrEmptyMembers_isEmptySet() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(Redis.ONLINE_USERS_KEY)).thenReturn(null);

        assertThat(registry.onlineUserIds()).isEmpty();
    }

    @Test
    void clearOnlineUsers_deletesEverySessionKeyAndTheOnlineSet() {
        Set<String> sessionKeys = Set.of(SESSION_KEY, Redis.USER_SESSIONS_KEY_PREFIX + "other");
        when(redisTemplate.keys(Redis.USER_SESSIONS_KEY_PREFIX + "*")).thenReturn(sessionKeys);

        registry.clearOnlineUsers();

        verify(redisTemplate).delete(sessionKeys);
        verify(redisTemplate).delete(Redis.ONLINE_USERS_KEY);
    }

    @Test
    void clearOnlineUsers_noSessionKeys_onlyDeletesOnlineSet() {
        when(redisTemplate.keys(Redis.USER_SESSIONS_KEY_PREFIX + "*")).thenReturn(Set.of());

        registry.clearOnlineUsers();

        verify(redisTemplate, never()).delete(Set.of());
        verify(redisTemplate).delete(Redis.ONLINE_USERS_KEY);
    }
}
```

- [ ] **Step 2: Run to see compilation fail**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=UserOnlineRegistryTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: COMPILATION ERROR — `UserOnlineRegistry(StringRedisTemplate)` does not exist.

- [ ] **Step 3: Replace `UserOnlineRegistry.java`**

```java
package com.chat_socket.socket;

import com.chat_socket.constant.Redis;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserOnlineRegistry {
    private final StringRedisTemplate redisTemplate;

    UserOnlineRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void markOnline(UUID userId, String sessionId) {
        redisTemplate.opsForSet().add(Redis.USER_SESSIONS_KEY_PREFIX + userId, sessionId);
        redisTemplate.opsForSet().add(Redis.ONLINE_USERS_KEY, userId.toString());
    }

    public void markOffline(UUID userId, String sessionId) {
        redisTemplate.execute(
                Redis.REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT,
                List.of(Redis.USER_SESSIONS_KEY_PREFIX + userId, Redis.ONLINE_USERS_KEY),
                sessionId,
                userId.toString());
    }

    public Set<UUID> onlineUserIds() {
        Set<String> userIds = redisTemplate.opsForSet().members(Redis.ONLINE_USERS_KEY);
        if (userIds == null || userIds.isEmpty()) return Set.of();

        return userIds.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }

    /** Runs once at startup: every socket session died with the previous process. */
    public void clearOnlineUsers() {
        // ponytail: KEYS scans the whole keyspace; fine once at boot, switch to SCAN if the key count grows large.
        Set<String> sessionKeys = redisTemplate.keys(Redis.USER_SESSIONS_KEY_PREFIX + "*");
        if (sessionKeys != null && !sessionKeys.isEmpty()) redisTemplate.delete(sessionKeys);
        redisTemplate.delete(Redis.ONLINE_USERS_KEY);
    }
}
```

Delete `src/main/java/com/chat_socket/utils/RedisUtils.java`:

```powershell
git rm src/main/java/com/chat_socket/utils/RedisUtils.java
```

- [ ] **Step 4: `SocketChannel.ONLINE_USERS` and its two callers**

`SocketChannel.java` — add after `SEEN`:

```java
    String ONLINE_USERS = "/online-users";
```

`SocketEventListener.java`: both `socketEmitter.emit("/online-users", ...)` → `socketEmitter.emit(SocketChannel.ONLINE_USERS, ...)`; add `import com.chat_socket.constant.SocketChannel;`.

`SocketController.java`: `@SubscribeMapping("/online-users")` → `@SubscribeMapping(SocketChannel.ONLINE_USERS)`; add the same import.

- [ ] **Step 5: Drop the no-op direct-message guard**

`MessageDirectPermission.java`: delete

```java
    public boolean canSendDirect(UUID recipientId) {
        return true;
    }
```

`MessageController.java`: delete the line `@PreAuthorize("@messageDirectPermission.canSendDirect(#request.recipientId())")` above `sendDirectMessage`. Keep the `@PreAuthorize` on `sendGroupMessage`. If `PreAuthorize` is still imported and used by `sendGroupMessage`, the import stays.

`MessageDirectPermissionTest.java`: delete `canSendDirect_singleRecipient_alwaysTrue`.

- [ ] **Step 6: Run the full suite and grep for leftovers**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS.

Run: `git grep -n "RedisUtils\|\"/online-users\"\|canSendDirect(#request" -- src`
Expected: no output.

- [ ] **Step 7: Commit (the bug fix is called out in the message body)**

```powershell
git add src/main/java/com/chat_socket/constant/SocketChannel.java src/main/java/com/chat_socket/socket src/main/java/com/chat_socket/security/MessageDirectPermission.java src/main/java/com/chat_socket/controller/MessageController.java src/test/java/com/chat_socket/socket/UserOnlineRegistryTest.java src/test/java/com/chat_socket/security/MessageDirectPermissionTest.java
git commit -m "refactor: remove RedisUtils wrapper, SocketChannel.ONLINE_USERS, drop no-op direct-message guard

clearOnlineUsers now deletes the per-user session keys: DEL with a glob
pattern was a no-op, so stale session sets survived restarts."
```

(`git rm` already staged the deletion; it goes into this commit.)

---

## Phase 4 — Controller tests

### Task 17: Standalone MockMvc controller tests

**Files:**
- Create: `src/test/java/com/chat_socket/controller/AuthControllerTest.java`
- Create: `src/test/java/com/chat_socket/controller/UserControllerTest.java`
- Create: `src/test/java/com/chat_socket/controller/FriendControllerTest.java`
- Create: `src/test/java/com/chat_socket/controller/MessageControllerTest.java`
- Create: `src/test/java/com/chat_socket/controller/ConversationControllerTest.java`

**Interfaces:**
- Uses `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())` — no Spring context, no security filter chain. Security wiring is covered by Task 8/15 tests; the `@PreAuthorize` expressions are pinned by reflection here so a deleted annotation fails a test.
- Every test asserts: the URL reaches the right service method, the HTTP status equals `BaseResponse.status()`, validation errors become `400` with `$.data.<field>`, and thrown exceptions map through `GlobalExceptionHandler`.
- Request URIs have no `/api` prefix: `spring.mvc.servlet.path` is applied by the servlet container, not by MockMvc.

- [ ] **Step 1: `AuthControllerTest`**

```java
package com.chat_socket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.AuthResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.SignInRequest;
import com.chat_socket.dto.SignUpRequest;
import com.chat_socket.exception.SignInException;
import com.chat_socket.exception.UnAuthorizedException;
import com.chat_socket.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock
    AuthService authService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signUp_validBody_returnsServiceStatus() throws Exception {
        when(authService.signUp(new SignUpRequest("alice", "a@example.com", "pw", "A", "L")))
                .thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(post("/v1/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"username":"alice","email":"a@example.com","password":"pw","firstName":"A","lastName":"L"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void signUp_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/v1/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.username").value("Username is required"))
                .andExpect(jsonPath("$.data.email").value("Email is invalid"))
                .andExpect(jsonPath("$.data.password").value("Password is required"));
    }

    @Test
    void signIn_success_returnsAccessToken() throws Exception {
        when(authService.signIn(eq(new SignInRequest("alice", "pw")), any(HttpServletResponse.class)))
                .thenReturn(new BaseResponse<>(new AuthResponse("token"), "Login successful.", 200));

        mockMvc.perform(post("/v1/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("token"))
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void signIn_signInException_returns400() throws Exception {
        when(authService.signIn(any(), any(HttpServletResponse.class)))
                .thenThrow(new SignInException("Username or password incorrect!"));

        mockMvc.perform(post("/v1/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username or password incorrect!"));
    }

    @Test
    void signOut_unauthorizedException_returns401() throws Exception {
        when(authService.signOut(any(HttpServletRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new UnAuthorizedException("Token not found."));

        mockMvc.perform(post("/v1/auth/sign-out"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token not found."));
    }

    @Test
    void refresh_returnsServiceBody() throws Exception {
        when(authService.refresh(any(HttpServletRequest.class), any(HttpServletResponse.class)))
                .thenReturn(new BaseResponse<>(new AuthResponse("new"), "Token refreshed successfully.", 200));

        mockMvc.perform(post("/v1/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new"));
    }
}
```

- [ ] **Step 2: `UserControllerTest`**

```java
package com.chat_socket.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.service.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UserProfileDto PROFILE = new UserProfileDto(ID, "alice", "A", "L", "a@example.com", null, null, null);

    @Mock
    UserService userService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMe_returnsProfile() throws Exception {
        when(userService.getUserProfile()).thenReturn(new BaseResponse<>(PROFILE, null, 200));

        mockMvc.perform(get("/v1/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void getMe_notFound_returns404() throws Exception {
        when(userService.getUserProfile()).thenThrow(new NotFoundException("User not found"));

        mockMvc.perform(get("/v1/user/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void updateMe_bindsBodyAndReturnsServiceStatus() throws Exception {
        UpdateUserRequest expected = new UpdateUserRequest(null, null, "Anna", null, null, null, null, null);
        when(userService.updateUserProfile(expected)).thenReturn(new BaseResponse<>(PROFILE, "ok", 200));

        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Anna\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateMe_badRequestException_returns400() throws Exception {
        when(userService.updateUserProfile(new UpdateUserRequest("taken", null, null, null, null, null, null, null)))
                .thenThrow(new BadRequestException("Username already exists"));

        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"taken\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void updateMe_tooLongUsername_returns400Validation() throws Exception {
        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.username").value("Username must be at most 50 characters"));
    }

    @Test
    void searchUsers_bindsQueryParamsIntoPaginationRequest() throws Exception {
        when(userService.searchUsers(new PaginationRequest(5, null, 10), "bob"))
                .thenReturn(new BaseResponse<>(PaginationResponse.offset(List.of(), null), "Success.", 200));

        mockMvc.perform(get("/v1/user").param("limit", "5").param("offset", "10").param("search", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    void getInfo_passesUserIdParam() throws Exception {
        when(userService.getUserInfo(ID)).thenReturn(new BaseResponse<>(null, "Success.", 200));

        mockMvc.perform(get("/v1/user/info").param("userId", ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success."));
    }
}
```

- [ ] **Step 3: `FriendControllerTest`**

```java
package com.chat_socket.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.service.FriendService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FriendControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    FriendService friendService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FriendController(friendService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getListFriend_bindsPaginationAndSearch() throws Exception {
        when(friendService.getListFriend(new PaginationRequest(20, null, 0), "an"))
                .thenReturn(new BaseResponse<>(PaginationResponse.offset(List.of(), 20), "Success.", 200));

        mockMvc.perform(get("/v1/friend").param("limit", "20").param("offset", "0").param("search", "an"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextOffset").value(20));
    }

    @Test
    void getListFriendRequest_returnsBothLists() throws Exception {
        when(friendService.getListFriendRequest())
                .thenReturn(new BaseResponse<>(new FriendRequestResponse(List.of(), List.of()), "Success.", 200));

        mockMvc.perform(get("/v1/friend/request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentRequests").isEmpty())
                .andExpect(jsonPath("$.data.receivedRequests").isEmpty());
    }

    @Test
    void sendFriendRequest_validBody_returnsServiceStatus() throws Exception {
        when(friendService.sendFriendRequest(new FriendSendRequest(ID, "hi")))
                .thenReturn(new BaseResponse<>(null, "Friend request sent successfully.", 201));

        mockMvc.perform(post("/v1/friend/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + ID + "\",\"message\":\"hi\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void sendFriendRequest_missingToUserId_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/friend/request").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.toUserId").value("To User is required"));
    }

    @Test
    void acceptFriendRequest_notFound_returns404() throws Exception {
        when(friendService.acceptFriendRequest(new FriendActionRequest(ID)))
                .thenThrow(new NotFoundException("Friend request not found."));

        mockMvc.perform(post("/v1/friend/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Friend request not found."));
    }

    @Test
    void declineAndCancel_returnServiceStatus() throws Exception {
        when(friendService.declineFriendRequest(new FriendActionRequest(ID)))
                .thenReturn(new BaseResponse<>(null, null, 204));
        when(friendService.cancelFriendRequest(new FriendActionRequest(ID)))
                .thenReturn(new BaseResponse<>(null, "You are not authorized to cancel this request.", 403));

        mockMvc.perform(post("/v1/friend/decline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/friend/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteFriend_passesPathVariable() throws Exception {
        when(friendService.deleteFriend(ID)).thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(delete("/v1/friend/{friendId}", ID)).andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 4: `MessageControllerTest`**

```java
package com.chat_socket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.MessageRequest;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.service.MessageService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    MessageService messageService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MessageController(messageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendDirect_validBody_returns201() throws Exception {
        when(messageService.sendDirectMessage(new MessageRequest(ID, "hi", null, null, null)))
                .thenReturn(new BaseResponse<>(null, "Message sent successfully.", 201));

        mockMvc.perform(post("/v1/message/direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":\"" + ID + "\",\"content\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Message sent successfully."));
    }

    @Test
    void sendDirect_blankContent_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/message/direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":\"" + ID + "\",\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.content").value("Content is required"));
    }

    @Test
    void sendGroup_forbiddenException_returns403() throws Exception {
        when(messageService.sendGroupMessage(new MessageRequest(null, "hi", null, ID, null)))
                .thenThrow(new ForbiddenException("You are not a participant of this conversation."));

        mockMvc.perform(post("/v1/message/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + ID + "\",\"content\":\"hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a participant of this conversation."));
    }

    @Test
    void sendGroup_isGuardedByMessageGroupPermission() throws Exception {
        PreAuthorize guard = MessageController.class
                .getMethod("sendGroupMessage", MessageRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).isEqualTo("@messageGroupPermission.canSendGroup(#request.conversationId())");
    }

    @Test
    void sendDirect_hasNoPreAuthorizeGuard() throws Exception {
        assertThat(MessageController.class
                        .getMethod("sendDirectMessage", MessageRequest.class)
                        .getAnnotation(PreAuthorize.class))
                .isNull();
    }
}
```

- [ ] **Step 5: `ConversationControllerTest`**

```java
package com.chat_socket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.GroupMembersRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateGroupRequest;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.service.ConversationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID M = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    ConversationService conversationService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String preAuthorizeOf(String method, Class<?>... params) throws NoSuchMethodException {
        PreAuthorize guard = ConversationController.class.getMethod(method, params).getAnnotation(PreAuthorize.class);
        return guard == null ? null : guard.value();
    }

    @Test
    void getConversations_bindsCursorLimitAndType() throws Exception {
        when(conversationService.getConversations(
                        new PaginationRequest(10, "2026-01-01T00:00:00", null), ConversationType.GROUP))
                .thenReturn(new BaseResponse<>(new PaginationResponse<>(List.of(), null), "ok", 200));

        mockMvc.perform(get("/v1/conversation")
                        .param("limit", "10")
                        .param("cursor", "2026-01-01T00:00:00")
                        .param("type", "GROUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    void createConversation_validBody_returns201() throws Exception {
        when(conversationService.createConversation(new ConversationRequest(ConversationType.GROUP, "Team", List.of(M))))
                .thenReturn(new BaseResponse<>(null, "Conversation created successfully.", 201));

        mockMvc.perform(post("/v1/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GROUP\",\"name\":\"Team\",\"memberIds\":[\"" + M + "\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createConversation_missingFields_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/conversation").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.type").value("Type is required"))
                .andExpect(jsonPath("$.data.name").value("Name is required"))
                .andExpect(jsonPath("$.data.memberIds").value("Member ids are required"));
    }

    @Test
    void createConversation_friendPermissionException_returns403WithNotFriends() throws Exception {
        when(conversationService.createConversation(new ConversationRequest(ConversationType.GROUP, "Team", List.of(M))))
                .thenThrow(new FriendPermissionException("You can only add friends to a group.", List.of(M)));

        mockMvc.perform(post("/v1/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GROUP\",\"name\":\"Team\",\"memberIds\":[\"" + M + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.notFriends[0]").value(M.toString()));
    }

    @Test
    void updateGroup_badRequestException_returns400() throws Exception {
        when(conversationService.updateGroup(C, new UpdateGroupRequest("x")))
                .thenThrow(new BadRequestException("Group name is required."));

        mockMvc.perform(patch("/v1/conversation/{id}/group", C)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Group name is required."));
    }

    @Test
    void getMessages_bindsPathAndPagination() throws Exception {
        when(conversationService.getMessages(C, new PaginationRequest(null, null, null)))
                .thenReturn(new BaseResponse<>(new PaginationResponse<>(List.of(), "next"), "ok", 200));

        mockMvc.perform(get("/v1/conversation/{id}/messages", C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value("next"));
    }

    @Test
    void markAsSeen_deleteGroup_leaveGroup_returnServiceStatus() throws Exception {
        when(conversationService.markAsSeen(C)).thenReturn(new BaseResponse<>(null, "seen", 200));
        when(conversationService.deleteGroup(C)).thenReturn(new BaseResponse<>(null, "deleted", 200));
        when(conversationService.leaveGroup(C)).thenReturn(new BaseResponse<>(null, "left", 200));

        mockMvc.perform(patch("/v1/conversation/{id}/seen", C)).andExpect(jsonPath("$.message").value("seen"));
        mockMvc.perform(delete("/v1/conversation/{id}/group", C)).andExpect(jsonPath("$.message").value("deleted"));
        mockMvc.perform(post("/v1/conversation/{id}/leave", C)).andExpect(jsonPath("$.message").value("left"));
    }

    @Test
    void addGroupMembers_emptyList_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/conversation/{id}/members", C)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.memberIds").value("Member ids are required"));
    }

    @Test
    void addGroupMembers_and_removeGroupMember_bindArguments() throws Exception {
        when(conversationService.addGroupMembers(C, new GroupMembersRequest(List.of(M))))
                .thenReturn(new BaseResponse<>(null, "added", 200));
        when(conversationService.removeGroupMember(C, M)).thenReturn(new BaseResponse<>(null, "removed", 200));

        mockMvc.perform(post("/v1/conversation/{id}/members", C)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[\"" + M + "\"]}"))
                .andExpect(jsonPath("$.message").value("added"));
        mockMvc.perform(delete("/v1/conversation/{id}/members/{memberId}", C, M))
                .andExpect(jsonPath("$.message").value("removed"));
    }

    @Test
    void preAuthorizeGuards_arePresentOnManagementEndpoints() throws Exception {
        assertThat(preAuthorizeOf("createConversation", ConversationRequest.class))
                .isEqualTo("@messageDirectPermission.canCreateConversation(#request)");
        assertThat(preAuthorizeOf("updateGroup", UUID.class, UpdateGroupRequest.class))
                .isEqualTo("@groupPermission.canManageGroup(#conversationId)");
        assertThat(preAuthorizeOf("deleteGroup", UUID.class)).isEqualTo("@groupPermission.canManageGroup(#conversationId)");
    }
}
```

- [ ] **Step 6: Run the controller tests, then the full suite**

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=*ControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: 35 tests pass. Known pitfalls and their fixes:
- `415 Unsupported Media Type` or `No converter found`: the standalone builder did not pick up Jackson 2. Add `.setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())` to every `standaloneSetup(...)` chain.
- `$.data.<field>` missing on validation tests: the message converter serialised `Map` fine but validation did not run — make sure `spring-boot-starter-validation` is on the classpath (it is in `pom.xml`); standalone MockMvc picks up `jakarta.validation` automatically.
- A `PaginationRequest` binding failure (`null` record) means constructor binding did not apply; assert with `any(PaginationRequest.class)` for that one test instead and note it in the commit message.

Run: `.\mvnw.cmd -q spotless:apply test "-Dtest=!ChatSocketApplicationTests"`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```powershell
git add src/test/java/com/chat_socket/controller
git commit -m "test: standalone MockMvc controller tests with PreAuthorize wiring pinned"
```

---

## Final check (after Task 17)

- [ ] `git status --short` shows only the four pre-existing uncommitted config files plus untracked `.agents/`, `.claude/`, `skills-lock.json`.
- [ ] `git grep -n "RedisUtils\|normalizeUserPair\|getUserIdFromAccessToken\|getCookieValue\|toResponseDto" -- src` → no output.
- [ ] `.\mvnw.cmd -q spotless:check test "-Dtest=!ChatSocketApplicationTests"` → BUILD SUCCESS.
- [ ] Report to the user: list of commits, the one message-text change ("Member not found." → "User not found." on `POST /v1/conversation` DIRECT with unknown member), the `clearOnlineUsers` bug fix, and the two `// ponytail:` ceilings left behind (KEYS in `clearOnlineUsers`; none other).

