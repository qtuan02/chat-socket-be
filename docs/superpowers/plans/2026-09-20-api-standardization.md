# API Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đưa contract REST + STOMP của `chat-socket` về dạng chuẩn (UTC `Instant`, DTO gọn/đủ, route REST, socket event mang full record), thêm 6 API còn thiếu (chi tiết conversation, đổi mật khẩu, upload, sửa/xóa message, typing) và viết tài liệu để FE (`monorepo/apps/chat`) cập nhật theo.

**Architecture:** Spring Boot 4.0.6 / Java 25 / Jackson 3 / JPA + Flyway + Postgres / STOMP simple broker. Mỗi service trả `BaseResponse<T>`; controller chỉ map status. Socket publish luôn qua `SocketPublisher` với `SocketSynchronization` (build payload trong tx, emit sau commit). Test: Mockito unit cho service/publisher, `MockMvcBuilders.standaloneSetup` cho controller — không cần Docker.

**Tech Stack:** Spring Boot 4.0.6, Hibernate 7, Flyway, MapStruct, Jackson 3 (`tools.jackson`), JUnit 5 + Mockito + AssertJ, Spotless (palantir).

**Spec:** `docs/superpowers/specs/2026-09-20-api-standardization-design.md`

## Global Constraints

- Java 25, Spring Boot `4.0.6` — Jackson 3 (`tools.jackson.*`), `org.springframework.boot.jackson.JacksonComponent`.
- Không thêm dependency mới. Upload dùng `MultipartFile` + `java.nio.file` có sẵn.
- Mọi timestamp API emit: `uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'` (UTC, 6 chữ số).
- Mọi list: `PaginationResponse.items`.
- Message text chuẩn (dùng đúng chuỗi): `"Content or attachment is required."`, `"Type must be IMAGE or FILE when attaching a file."`, `"Type must be TEXT without an attachment."`, `"Current password is incorrect."`, `"New password must differ from current password."`, `"File is required."`, `"File is too large (max 10MB)."`, `"Message not found."`, `"You can only edit your own messages."`, `"You can only delete your own messages."`, `"Only text messages can be edited."`, `"Conversation retrieved successfully."`, `"Cursor is invalid."`.
- Lệnh: Windows `cmd /c mvnw.cmd`, POSIX `./mvnw`. Test 1 class: `./mvnw -q test -Dtest=ClassName`. Toàn bộ: `./mvnw -q test`. Format trước commit: `./mvnw -q spotless:apply`.
- Commit message kết thúc bằng dòng: `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`.
- Không đụng 4 file đang staged (`ApplicationYaml`, `SecurityServerConfig`, `WebSocketConfig`, `application.yaml`) ngoài các edit plan này ghi rõ.

## Review Focus

1. **Cursor thiếu zone** (`cursor=2026-01-01T00:00:00`): phải 400 `"Cursor is invalid."`, không 500 — pin ở Task 3 (`PaginationUtilsTest.resolveCursorPage_localCursorWithoutZone_throwsBadRequest`).
2. **Tin nhắn chỉ có attachment, `type` = `TEXT`**: phải 400, không lưu message `TEXT` kèm file — pin ở Task 7 (`MessageServiceImplTest.sendGroupMessage_attachmentWithTextType_throwsBadRequest`).
3. **Người bị kick vẫn nhận `conversation.updated`**: publisher tính recipients *sau* khi `leftAt` đã set → người bị kick chỉ nhận `conversation.removed` — pin ở Task 10 (`ConversationServiceImplTest.removeGroupMember_publishesRemovedToTargetAndUpdatedToRest`).
4. **Xóa message đang là `lastMessage`** khi không còn tin nào khác: `lastMessage` về `null`, không NPE — pin ở Task 16 (`MessageServiceImplTest.deleteMessage_lastAndOnlyMessage_clearsConversationLastMessage`).
5. **Upload file tên `../../evil.sh`**: tên lưu là UUID + ext đã lọc, không bao giờ ghi ra ngoài `upload-dir` — pin ở Task 14 (`FileStorageTest.store_ignoresClientPathAndKeepsOnlySafeExtension`).

---

## Phase 1 — UTC (`Instant`)

### Task 1: `TimeFormat` + `InstantJsonSerializer`

**Files:**
- Create: `src/main/java/com/chat_socket/constant/TimeFormat.java`
- Create: `src/main/java/com/chat_socket/config/InstantJsonSerializer.java`
- Test: `src/test/java/com/chat_socket/config/InstantJsonSerializerTest.java`

**Interfaces:**
- Produces: `TimeFormat.UTC_MICROS` (`DateTimeFormatter`), dùng bởi Task 3 (`PaginationUtils`) và serializer.

- [ ] **Step 1: Viết test fail**

```java
package com.chat_socket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class InstantJsonSerializerTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new SimpleModule().addSerializer(Instant.class, new InstantJsonSerializer()))
            .build();

    @Test
    void writesUtcWithSixFractionDigits() {
        assertThat(mapper.writeValueAsString(Instant.parse("2026-01-01T12:00:00Z")))
                .isEqualTo("\"2026-01-01T12:00:00.000000Z\"");
        assertThat(mapper.writeValueAsString(Instant.parse("2026-01-01T12:00:00.5Z")))
                .isEqualTo("\"2026-01-01T12:00:00.500000Z\"");
        assertThat(mapper.writeValueAsString(Instant.parse("2026-01-01T12:00:00.123456789Z")))
                .isEqualTo("\"2026-01-01T12:00:00.123456Z\"");
    }

    @Test
    void nullStaysNull() {
        record Holder(Instant at) {}
        assertThat(mapper.writeValueAsString(new Holder(null))).isEqualTo("{\"at\":null}");
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `./mvnw -q test -Dtest=InstantJsonSerializerTest`
Expected: COMPILE ERROR — `InstantJsonSerializer` chưa tồn tại.

- [ ] **Step 3: Viết `TimeFormat` và serializer**

`src/main/java/com/chat_socket/constant/TimeFormat.java`:

```java
package com.chat_socket.constant;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public interface TimeFormat {
    /** Every timestamp the API emits: UTC, 6 fraction digits (Postgres timestamptz precision), fixed width. */
    DateTimeFormatter UTC_MICROS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);
}
```

`src/main/java/com/chat_socket/config/InstantJsonSerializer.java`:

```java
package com.chat_socket.config;

import com.chat_socket.constant.TimeFormat;
import java.time.Instant;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Registered globally by Spring Boot; the STOMP broker converter shares the same JsonMapper. */
@JacksonComponent
public class InstantJsonSerializer extends ValueSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(TimeFormat.UTC_MICROS.format(value));
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `./mvnw -q test -Dtest=InstantJsonSerializerTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
./mvnw -q spotless:apply
git add src/main/java/com/chat_socket/constant/TimeFormat.java src/main/java/com/chat_socket/config/InstantJsonSerializer.java src/test/java/com/chat_socket/config/InstantJsonSerializerTest.java
git commit -m "feat: fixed-precision UTC Instant JSON serializer

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 2: Migration `V8__utc_timestamps.sql`

**Files:**
- Create: `src/main/resources/db/migration/V8__utc_timestamps.sql`

- [ ] **Step 1: Viết migration**

```sql
-- Existing rows were written as JVM wall-clock; dev data, treated as UTC (see spec §1).
ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE friend_requests
    ALTER COLUMN responded_at TYPE TIMESTAMPTZ USING responded_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE friends
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE conversations
    ALTER COLUMN last_message_at TYPE TIMESTAMPTZ USING last_message_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE messages
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE participants
    ALTER COLUMN last_read_at TYPE TIMESTAMPTZ USING last_read_at AT TIME ZONE 'UTC',
    ALTER COLUMN joined_at TYPE TIMESTAMPTZ USING joined_at AT TIME ZONE 'UTC',
    ALTER COLUMN left_at TYPE TIMESTAMPTZ USING left_at AT TIME ZONE 'UTC',
    ALTER COLUMN archived_at TYPE TIMESTAMPTZ USING archived_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN muted_until TYPE TIMESTAMPTZ USING muted_until AT TIME ZONE 'UTC';
```

- [ ] **Step 2: Xác minh migration chạy được (cần infra)**

Run: `task start_infra` rồi `./mvnw -q spring-boot:run` (dừng bằng Ctrl+C sau khi thấy log `Successfully applied 1 migration`).
Expected: log Flyway `Migrating schema "public" to version "8 - utc timestamps"` không lỗi. Nếu không có Docker: bỏ qua bước này, ghi rõ trong commit là chưa chạy thật.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V8__utc_timestamps.sql
git commit -m "feat: migrate timestamp columns to timestamptz

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 3: `LocalDateTime` → `Instant` toàn codebase

Compile-driven: kiểu đổi ở entity kéo theo mọi lớp; làm trong một task, test xanh ở cuối.

**Files:**
- Modify: `src/main/java/com/chat_socket/entity/{ConversationEntity,FriendEntity,FriendRequestEntity,MessageEntity,ParticipantEntity,UserEntity}.java`
- Modify: `src/main/java/com/chat_socket/dto/{ConversationDto,ConversationEvent,ConversationParticipantDto,ConversationSeenEvent,FriendDto,FriendRequestReceviedDto,FriendRequestSentDto,MessageDto,UserInfoDto,UserSearchDto}.java`
- Modify: `src/main/java/com/chat_socket/repository/{ConversationRepository,FriendRepository,MessageRepository}.java`
- Modify: `src/main/java/com/chat_socket/service/impl/{ConversationServiceImpl,MessageServiceImpl}.java`
- Modify: `src/main/java/com/chat_socket/socket/SocketPublisher.java`
- Modify: `src/main/java/com/chat_socket/utils/PaginationUtils.java`
- Test: `src/test/java/com/chat_socket/TestFixtures.java`, `utils/PaginationUtilsTest.java`, `socket/SocketPublisherTest.java`, `service/impl/ConversationServiceImplTest.java`

**Interfaces:**
- Produces: mọi field thời gian là `java.time.Instant`; `PaginationUtils.CursorPage.cursor()` là `Instant`; `toCursorResponse(..., Function<T, Instant> cursorExtractor, ...)`.

- [ ] **Step 1: Sửa test `PaginationUtilsTest` trước (định nghĩa contract mới)**

Thay `import java.time.LocalDateTime;` bằng `import java.time.Instant;`. Thay 2 test cursor bằng:

```java
    @Test
    void resolveCursorPage_parsesUtcAndOffsetCursor() {
        assertThat(PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T12:00:00Z", null))
                        .cursor())
                .isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
        assertThat(PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T19:00:00+07:00", null))
                        .cursor())
                .isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
        assertThat(PaginationUtils.resolveCursorPage(
                                new PaginationRequest(10, "2026-01-01T12:00:00.123456Z", null))
                        .cursor())
                .isEqualTo(Instant.parse("2026-01-01T12:00:00.123456Z"));
    }

    @Test
    void resolveCursorPage_localCursorWithoutZone_throwsBadRequest() {
        assertThatThrownBy(() ->
                        PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T12:00:00", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cursor is invalid.");
    }
```

Trong `toCursorResponse_moreThanLimit_trimsAndSetsNextCursorFromLastKeptItem`:

```java
        List<Instant> fetched = List.of(
                Instant.parse("2026-01-03T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));

        PaginationResponse<String> response =
                PaginationUtils.toCursorResponse(fetched, page, Instant::toString, Function.identity(), false);

        assertThat(response.messages()).containsExactly("2026-01-03T00:00:00Z", "2026-01-02T00:00:00Z");
        assertThat(response.nextCursor()).isEqualTo("2026-01-02T00:00:00.000000Z");
        assertThat(response.nextOffset()).isNull();
```

Trong `toCursorResponse_reverseItems_...`:

```java
        List<Instant> fetched = List.of(Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        PaginationResponse<String> response =
                PaginationUtils.toCursorResponse(fetched, page, Instant::toString, Function.identity(), true);

        assertThat(response.messages()).containsExactly("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z");
```

- [ ] **Step 2: Sửa `PaginationUtils`**

Import: bỏ `LocalDateTime`, `OffsetDateTime`, `DateTimeFormatter`; thêm `java.time.Instant`, `com.chat_socket.constant.TimeFormat`. Đổi:

```java
    public record CursorPage(int limit, Instant cursor, PageRequest pageRequest) { ... }   // giữ body

    // trong resolveCursorPage:
            Instant cursor = parseDateTimeCursor(request == null ? null : request.cursor());

    private static Instant parseDateTimeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        return Instant.parse(cursor.trim()); // accepts Z or an offset; no zone -> DateTimeParseException -> 400
    }

    private static String formatDateTimeCursor(Instant cursor) {
        return TimeFormat.UTC_MICROS.format(cursor);
    }

    public static <T, R> PaginationResponse<R> toCursorResponse(
            List<T> fetchedItems,
            CursorPage page,
            Function<T, R> mapper,
            Function<T, Instant> cursorExtractor,
            boolean reverseItems) { ... }   // giữ body
```

- [ ] **Step 3: Chạy `PaginationUtilsTest`**

Run: `./mvnw -q test -Dtest=PaginationUtilsTest`
Expected: PASS. (Compile lỗi ở chỗ khác → tiếp bước 4 rồi quay lại.)

- [ ] **Step 4: Entities**

Trong 6 entity: `import java.time.LocalDateTime;` → `import java.time.Instant;`, mọi `private LocalDateTime x;` → `private Instant x;` (giữ annotation `@CreationTimestamp`/`@UpdateTimestamp`). Danh sách field: `ConversationEntity` (lastMessageAt, createdAt, updatedAt); `FriendEntity` (createdAt, updatedAt); `FriendRequestEntity` (respondedAt, createdAt, updatedAt); `MessageEntity` (createdAt, updatedAt); `ParticipantEntity` (lastReadAt, joinedAt, leftAt, archivedAt, deletedAt, mutedUntil); `UserEntity` (createdAt, updatedAt).

- [ ] **Step 5: DTOs**

Trong 10 DTO liệt kê ở **Files**: `LocalDateTime` → `Instant` (import + kiểu field). `ConversationEvent`: cả 2 factory. `ConversationSeenEvent`: field `lastReadAt` và factory `seen(...)`.

- [ ] **Step 6: Repositories**

`ConversationRepository.findActiveConversationIdsForUserBeforeCursor(..., @Param("cursor") Instant cursor, ...)`; `FriendRepository.findFriendshipsOfUserBeforeCursor(..., @Param("cursor") Instant cursor, ...)`; `MessageRepository.findMessagesBeforeCursor(..., @Param("cursor") Instant cursor, ...)`. Đổi import.

- [ ] **Step 7: Services + SocketPublisher**

`ConversationServiceImpl`: import `Instant`; `LocalDateTime.now()` → `Instant.now()` (7 chỗ: markAsSeen, deleteGroup, addGroupMembers, removeGroupMember, leaveGroup, createGroupConversation, createDirectConversation); biến `LocalDateTime seenAt/deletedAt/now` → `Instant`.
`MessageServiceImpl`: `LocalDateTime messageCreatedAt = message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt();` → kiểu `Instant`; `markSenderAsRead(..., Instant readAt)`.
`SocketPublisher`: 3 tham số `LocalDateTime lastMessageAt`/`seenAt` → `Instant`.

- [ ] **Step 8: Tests còn lại**

`TestFixtures`: `public static final Instant FIXED_TIME = Instant.parse("2026-01-01T12:00:00Z");` (import `java.time.Instant`).
`SocketPublisherTest`: `private static final Instant AT = Instant.parse("2026-01-01T12:00:00Z");`; `AT.plusMinutes(1)` → `AT.plusSeconds(60)` (2 chỗ).
`ConversationServiceImplTest`: import `Instant` thay `LocalDateTime`; `any(LocalDateTime.class)` → `any(Instant.class)`.

- [ ] **Step 9: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS, 0 failures. `grep -rn "LocalDateTime" src/` phải trả về rỗng.

- [ ] **Step 10: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "refactor: store and emit every timestamp as UTC Instant

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 2 — DTO + route

### Task 4: `PaginationResponse.items`

**Files:**
- Modify: `src/main/java/com/chat_socket/dto/PaginationResponse.java`
- Test: `src/test/java/com/chat_socket/utils/PaginationUtilsTest.java`, `controller/ConversationControllerTest.java:69`, `controller/UserControllerTest.java:107`, `controller/FriendControllerTest.java` (dòng có `$.data.messages` nếu có)

- [ ] **Step 1: Sửa test**

Trong `PaginationUtilsTest` mọi `response.messages()` → `response.items()`. Trong 3 controller test mọi `jsonPath("$.data.messages")` → `jsonPath("$.data.items")`.

- [ ] **Step 2: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=PaginationUtilsTest`
Expected: COMPILE ERROR `cannot find symbol items()`.

- [ ] **Step 3: Đổi DTO**

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginationResponse<T>(List<T> items, String nextCursor, Integer nextOffset) {
    public PaginationResponse(List<T> items, String nextCursor) {
        this(items, nextCursor, null);
    }

    public static <T> PaginationResponse<T> offset(List<T> items, Integer nextOffset) {
        return new PaginationResponse<>(items, null, nextOffset);
    }
}
```

- [ ] **Step 4: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "refactor: rename PaginationResponse.messages to items

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 5: `UserSummaryDto`, `FriendRequestDto`, friend request routes

**Files:**
- Create: `src/main/java/com/chat_socket/dto/UserSummaryDto.java`, `dto/FriendRequestDto.java`
- Delete: `dto/UserDto.java`, `dto/AcceptFriendResponse.java`, `dto/FriendRequestSentDto.java`, `dto/FriendRequestReceviedDto.java`, `dto/FriendActionRequest.java`, `mapper/FriendMapper.java`
- Modify: `dto/FriendRequestResponse.java`, `mapper/UserMapper.java`, `mapper/FriendRequestMapper.java`, `service/FriendService.java`, `service/impl/FriendServiceImpl.java`, `controller/FriendController.java`
- Test: `service/impl/FriendServiceImplTest.java`, `controller/FriendControllerTest.java`

**Interfaces:**
- Produces: `UserSummaryDto(UUID id, String username, String firstName, String lastName, String avatarUrl)`; `UserMapper.toSummaryDto(UserEntity)`, `UserMapper.toFriendDto(UserEntity)`; `FriendService.acceptFriendRequest(UUID requestId)`, `declineFriendRequest(UUID)`, `cancelFriendRequest(UUID)`.

- [ ] **Step 1: Tạo DTO mới, xóa DTO cũ**

```java
// dto/UserSummaryDto.java
package com.chat_socket.dto;

import java.util.UUID;

public record UserSummaryDto(UUID id, String username, String firstName, String lastName, String avatarUrl) {}
```

```java
// dto/FriendRequestDto.java
package com.chat_socket.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code user} is the other side of the request: the recipient on a sent request, the sender on a received one. */
public record FriendRequestDto(UUID id, UserSummaryDto user, String message, Instant createdAt) {}
```

```java
// dto/FriendRequestResponse.java
public record FriendRequestResponse(List<FriendRequestDto> sentRequests, List<FriendRequestDto> receivedRequests) {}
```

`git rm` 5 DTO + `FriendMapper`.

- [ ] **Step 2: Mapper**

`UserMapper`: bỏ `UserDto toDto`, thêm:

```java
    UserSummaryDto toSummaryDto(UserEntity user);

    @Mapping(target = "joinedAt", source = "createdAt")
    FriendDto toFriendDto(UserEntity user);
```

`FriendRequestMapper`:

```java
@Mapper(config = GlobalMapperConfig.class, uses = UserMapper.class)
public interface FriendRequestMapper {
    FriendRequestEntity toEntity(FriendSendRequest request);

    @Mapping(target = "user", source = "toUser")
    FriendRequestDto toSentDto(FriendRequestEntity request);

    @Mapping(target = "user", source = "fromUser")
    FriendRequestDto toReceivedDto(FriendRequestEntity request);
}
```

- [ ] **Step 3: Service**

`FriendService`:

```java
    BaseResponse<UserSummaryDto> acceptFriendRequest(UUID requestId);
    BaseResponse<String> declineFriendRequest(UUID requestId);
    BaseResponse<String> cancelFriendRequest(UUID requestId);
```

`FriendServiceImpl`: constructor nhận `UserMapper userMapper` thay `FriendMapper friendMapper` (field đổi tên); `friendMapper.toFriendDto(...)` → `userMapper.toFriendDto(...)`; 3 method nhận `UUID requestId` trực tiếp (xóa dòng `UUID requestId = request.requestId();`); accept: `UserSummaryDto response = userMapper.toSummaryDto(user);` trả `BaseResponse<UserSummaryDto>`.

- [ ] **Step 4: Controller**

```java
    @PostMapping("/request/{requestId}/accept")
    public ResponseEntity<BaseResponse<UserSummaryDto>> acceptFriendRequest(@PathVariable UUID requestId) {
        BaseResponse<UserSummaryDto> body = friendService.acceptFriendRequest(requestId);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/request/{requestId}/decline")
    public ResponseEntity<BaseResponse<String>> declineFriendRequest(@PathVariable UUID requestId) {
        BaseResponse<String> body = friendService.declineFriendRequest(requestId);
        return ResponseEntity.status(body.status()).body(body);
    }

    @DeleteMapping("/request/{requestId}")
    public ResponseEntity<BaseResponse<String>> cancelFriendRequest(@PathVariable UUID requestId) {
        BaseResponse<String> body = friendService.cancelFriendRequest(requestId);
        return ResponseEntity.status(body.status()).body(body);
    }
```

Xóa 3 mapping cũ `/accept`, `/decline`, `/cancel`; bỏ import `FriendActionRequest`, `AcceptFriendResponse`.

- [ ] **Step 5: Test service**

`FriendServiceImplTest`: import `UserSummaryDto`, `FriendRequestDto`, `UserMapper` (bỏ 4 import cũ + `FriendMapper`); `@Mock FriendMapper friendMapper` → `@Mock UserMapper userMapper`; constructor `new FriendServiceImpl(userRepository, friendRepository, friendRequestRepository, userMapper, friendRequestMapper)`.
Dòng 105-107:

```java
        FriendRequestDto sentDto = new FriendRequestDto(sent.getId(), null, null, null);
        FriendRequestDto receivedDto = new FriendRequestDto(received.getId(), null, null, null);
```

Dòng 213-220:

```java
        UserSummaryDto dto = new UserSummaryDto(SMALL, "user-" + SMALL, "F", "L", null);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(from));
        when(userMapper.toSummaryDto(from)).thenReturn(dto);

        BaseResponse<UserSummaryDto> response = service.acceptFriendRequest(request.getId());
```

Mọi `service.xxxFriendRequest(new FriendActionRequest(X))` → `service.xxxFriendRequest(X)` (dòng 187, 200, 238, 250, 264, 277). Dòng 199 `BaseResponse<AcceptFriendResponse>` → `BaseResponse<UserSummaryDto>`. Nếu có test `getListFriend` stub `friendMapper.toFriendDto` → `userMapper.toFriendDto`.

- [ ] **Step 6: Test controller**

`FriendControllerTest`: bỏ import `FriendActionRequest`. Dòng 92-113:

```java
    @Test
    void acceptFriendRequest_returns201WithUser() throws Exception {
        when(friendService.acceptFriendRequest(ID))
                .thenReturn(new BaseResponse<>(new UserSummaryDto(ID, "bob", "B", "L", null), "ok", 201));

        mockMvc.perform(post("/v1/friend/request/{requestId}/accept", ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("bob"));
    }

    @Test
    void declineAndCancel_return204() throws Exception {
        when(friendService.declineFriendRequest(ID)).thenReturn(new BaseResponse<>(null, null, 204));
        when(friendService.cancelFriendRequest(ID)).thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(post("/v1/friend/request/{requestId}/decline", ID)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/v1/friend/request/{requestId}", ID)).andExpect(status().isNoContent());
    }
```

(Giữ tên test cũ nếu file đặt khác; nội dung như trên.)

- [ ] **Step 7: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "refactor: UserSummaryDto, single FriendRequestDto, RESTful friend request routes

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 6: `ConversationDto` gọn, participant `username`, `UserInfoDto.requestId`, route user/conversation

**Files:**
- Modify: `dto/ConversationDto.java`, `dto/ConversationParticipantDto.java`, `dto/UserInfoDto.java`, `mapper/ConversationMapper.java`, `mapper/UserMapper.java`, `repository/ConversationRepository.java`, `service/impl/UserServiceImpl.java`, `controller/UserController.java`, `controller/ConversationController.java`
- Test: `service/impl/ConversationServiceImplTest.java:63`, `service/impl/UserServiceImplTest.java` (các test `getUserInfo_*`), `controller/UserControllerTest.java:114`, `controller/ConversationControllerTest.java:113,138`

**Interfaces:**
- Produces: `ConversationDto(UUID id, ConversationType type, String groupName, MessageDto lastMessage, Instant lastMessageAt, long unreadCount, List<ConversationParticipantDto> participants)`; `UserMapper.toUserInfoDto(UserEntity user, FriendStatus statusFriend, UUID requestId)`.

- [ ] **Step 1: Sửa test trước**

`ConversationServiceImplTest:63`: `new ConversationDto(C, null, null, null, null, 0, List.of())`.
`UserServiceImplTest`: mọi `userMapper.toUserInfoDto(user, status)` stub/verify → `toUserInfoDto(eq(user), eq(status), any())`; thêm test:

```java
    @Test
    void getUserInfo_pendingRequest_passesRequestIdToMapper() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        FriendRequestEntity pending = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), other, FriendRequestStatus.PENDING);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(false);
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.SENT, pending.getId());
    }
```

`UserControllerTest:114`: `mockMvc.perform(get("/v1/user/{userId}", ID))`.
`ConversationControllerTest`: `patch("/v1/conversation/{id}/group", C)` → `patch("/v1/conversation/{id}", C)`; `delete("/v1/conversation/{id}/group", C)` → `delete("/v1/conversation/{id}", C)`.

- [ ] **Step 2: DTO + mapper**

```java
public record ConversationDto(
        UUID id,
        ConversationType type,
        String groupName,
        MessageDto lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        List<ConversationParticipantDto> participants) {}

public record ConversationParticipantDto(
        UUID userId,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        ParticipantRole role,
        Instant joinedAt,
        UUID lastReadMessageId,
        Instant lastReadAt) {}

public record UserInfoDto(
        UUID id, String username, String firstName, String lastName, String email, String avatarUrl,
        String bio, String phone, Instant joinedAt, FriendStatus statusFriend, UUID requestId) {}
```

`ConversationMapper.toDto(ConversationEntity, long)`: xóa 4 `@Mapping` `createdById/directUserAId/directUserBId/lastMessageId`; giữ `lastMessage`, `unreadCount`. `toParticipantDto`: thêm `@Mapping(target = "username", source = "user.username")`.
`UserMapper`:

```java
    @Mapping(target = "joinedAt", source = "user.createdAt")
    @Mapping(target = "statusFriend", source = "statusFriend")
    @Mapping(target = "requestId", source = "requestId")
    UserInfoDto toUserInfoDto(UserEntity user, FriendStatus statusFriend, UUID requestId);
```

`ConversationRepository.findConversationsWithDetails`: xóa 3 dòng `LEFT JOIN FETCH c.createdBy / c.directUserA / c.directUserB`.

- [ ] **Step 3: Service + controller**

`UserServiceImpl.getUserInfo`: `userMapper.toUserInfoDto(user, status, pendingRequest == null ? null : pendingRequest.getId())`.
`UserController`: `@GetMapping("/{userId}") getInfo(@PathVariable UUID userId)` (bỏ `@RequestParam`).
`ConversationController`: `@PatchMapping("/{conversationId}")` cho `updateGroup`, `@DeleteMapping("/{conversationId}")` cho `deleteGroup` (giữ `@PreAuthorize`).

- [ ] **Step 4: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "refactor: trim ConversationDto, add participant username and UserInfo requestId, RESTful user/conversation routes

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 7: `DirectMessageRequest` / `GroupMessageRequest` + validation

**Files:**
- Create: `dto/DirectMessageRequest.java`, `dto/GroupMessageRequest.java`
- Delete: `dto/MessageRequest.java`
- Modify: `service/MessageService.java`, `service/impl/MessageServiceImpl.java`, `controller/MessageController.java`
- Test: `service/impl/MessageServiceImplTest.java`, `controller/MessageControllerTest.java`

**Interfaces:**
- Produces: `MessageService.sendDirectMessage(DirectMessageRequest)`, `sendGroupMessage(GroupMessageRequest)`; private `MessageServiceImpl.validateContent(String content, MessageType type, String attachmentUrl)` (dùng lại ở Task 15).

- [ ] **Step 1: Test service**

Thay import `MessageRequest` bằng 2 DTO mới. Mapping constructor cũ `new MessageRequest(recipientId, content, attachmentUrl, conversationId, type)`:
- direct: `new DirectMessageRequest(recipientId, content, type, attachmentUrl)`
- group: `new GroupMessageRequest(conversationId, content, type, attachmentUrl)`

Xóa 3 test direct-qua-conversationId (dòng 138, 152, 169 — nhánh này không còn). Test dòng 118 (`recipientId null`) chuyển sang controller test (validation `@NotNull`) — xóa ở service. Thêm:

```java
    @Test
    void sendGroupMessage_attachmentWithTextType_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);

        assertThatThrownBy(() -> service.sendGroupMessage(
                        new GroupMessageRequest(CONVERSATION_ID, null, MessageType.TEXT, "http://file")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Type must be IMAGE or FILE when attaching a file.");
    }

    @Test
    void sendGroupMessage_noAttachmentWithImageType_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);

        assertThatThrownBy(() -> service.sendGroupMessage(
                        new GroupMessageRequest(CONVERSATION_ID, "hi", MessageType.IMAGE, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Type must be TEXT without an attachment.");
    }

    @Test
    void sendGroupMessage_blankContentWithoutAttachment_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);

        assertThatThrownBy(() -> service.sendGroupMessage(new GroupMessageRequest(CONVERSATION_ID, " ", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content or attachment is required.");
    }
```

Sửa test cũ dòng 109/217 (content blank) sang message `"Content or attachment is required."`. Test dòng 256 (IMAGE + `http://file`) giữ nguyên — hợp lệ.

- [ ] **Step 2: Test controller**

`MessageControllerTest`: `new MessageRequest(ID, "hi", null, null, null)` → `new DirectMessageRequest(ID, "hi", null, null)`; `new MessageRequest(null, "hi", null, ID, null)` → `new GroupMessageRequest(ID, "hi", null, null)`; `getMethod("sendGroupMessage", GroupMessageRequest.class)`, `getMethod("sendDirectMessage", DirectMessageRequest.class)`. Test dòng 55 (body thiếu recipientId → 400) giữ.

- [ ] **Step 3: DTO**

```java
public record DirectMessageRequest(
        @NotNull(message = "Recipient is required") UUID recipientId,
        String content,
        MessageType type,
        String attachmentUrl) {}

public record GroupMessageRequest(
        @NotNull(message = "Conversation is required") UUID conversationId,
        String content,
        MessageType type,
        String attachmentUrl) {}
```

- [ ] **Step 4: Service**

`MessageService`:

```java
    BaseResponse<MessageDto> sendDirectMessage(DirectMessageRequest request);
    BaseResponse<MessageDto> sendGroupMessage(GroupMessageRequest request);
```

`MessageServiceImpl.sendDirectMessage`:

```java
        UUID senderId = Security.getCurrentUser().id();
        validateContent(request.content(), request.type(), request.attachmentUrl());
        if (senderId.equals(request.recipientId()))
            throw new BadRequestException("You cannot send a direct message to yourself.");

        ConversationEntity conversation =
                conversationService.findOrCreateDirectConversation(senderId, request.recipientId());
        UserEntity sender = userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));
        MessageEntity message = createMessage(conversation, sender, request.content(), request.type(), request.attachmentUrl());
        ...
```

`sendGroupMessage`: `validateContent(...)` thay 2 check cũ; gọi `createMessage(conversation, sender, request.content(), request.type(), request.attachmentUrl())`.
`createMessage(ConversationEntity, UserEntity, String content, MessageType type, String attachmentUrl)`: `message.setContent(content); message.setAttachmentUrl(attachmentUrl); message.setType(type == null ? MessageType.TEXT : type);`.
Xóa `getDirectConversationForSender` (không còn dùng). Thêm:

```java
    private static void validateContent(String content, MessageType type, String attachmentUrl) {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasAttachment = attachmentUrl != null && !attachmentUrl.isBlank();
        if (!hasContent && !hasAttachment) throw new BadRequestException("Content or attachment is required.");
        if (hasAttachment && type != MessageType.IMAGE && type != MessageType.FILE)
            throw new BadRequestException("Type must be IMAGE or FILE when attaching a file.");
        if (!hasAttachment && type != null && type != MessageType.TEXT)
            throw new BadRequestException("Type must be TEXT without an attachment.");
    }
```

- [ ] **Step 5: Controller**

```java
    @PostMapping("/direct")
    @PreAuthorize("@messageDirectPermission.canSendDirect(#request.recipientId())")
    public ResponseEntity<BaseResponse<MessageDto>> sendDirectMessage(@Valid @RequestBody DirectMessageRequest request) { ... }

    @PostMapping("/group")
    @PreAuthorize("@messageGroupPermission.canSendGroup(#request.conversationId())")
    public ResponseEntity<BaseResponse<MessageDto>> sendGroupMessage(@Valid @RequestBody GroupMessageRequest request) { ... }
```

- [ ] **Step 6: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "refactor: split MessageRequest per endpoint, validate content/type/attachment together

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 8: Bỏ cột chết `archived_at`, `muted_until`

**Files:**
- Create: `src/main/resources/db/migration/V9__drop_unused_participant_columns.sql`
- Modify: `entity/ParticipantEntity.java`

- [ ] **Step 1: Migration**

```sql
ALTER TABLE participants
    DROP COLUMN archived_at,
    DROP COLUMN muted_until;
```

- [ ] **Step 2: Entity**

Xóa 2 field `archivedAt`, `mutedUntil` (và `@Column` của chúng) trong `ParticipantEntity`.

- [ ] **Step 3: Chạy test + grep**

Run: `./mvnw -q test` và `grep -rn "archivedAt\|mutedUntil\|archived_at\|muted_until" src/main/java`
Expected: PASS; grep rỗng.

- [ ] **Step 4: Commit**

```bash
git add -A src/main
git commit -m "refactor: drop unused participants.archived_at and muted_until

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 3 — Socket events

### Task 9: Event DTO mới + `SocketPublisher` mới

**Files:**
- Create: `dto/ConversationUpdatedEvent.java`, `dto/ConversationRemovedEvent.java`, `dto/MessageEvent.java`
- Delete: `dto/ConversationEvent.java`, `dto/ConversationDelivery.java`
- Modify: `dto/ConversationSeenEvent.java` (giữ, không đổi shape), `constant/SocketChannel.java`, `socket/SocketPublisher.java`
- Test: `socket/SocketPublisherTest.java` (viết lại)

**Interfaces:**
- Produces (dùng ở Task 10, 15, 16):

```java
void publishMessageCreatedAfterCommit(ConversationEntity conversation, MessageDto message)
void publishMessageUpdatedAfterCommit(UUID conversationId, MessageDto message)
void publishMessageDeletedAfterCommit(UUID conversationId, MessageDto message)
void publishConversationUpdatedAfterCommit(ConversationEntity conversation)
void publishConversationRemovedAfterCommit(UUID conversationId, Collection<UUID> userIds)
void publishConversationSeenAfterCommit(UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, Instant seenAt)
```

- [ ] **Step 1: Viết lại `SocketPublisherTest`**

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

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRemovedEvent;
import com.chat_socket.dto.ConversationSeenEvent;
import com.chat_socket.dto.ConversationUpdatedEvent;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageEvent;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.mapper.ConversationMapper;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.time.Instant;
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
    private static final Instant AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final MessageDto MESSAGE =
            new MessageDto(UUID.randomUUID(), C, U1, "hi", null, MessageType.TEXT, AT, AT);

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    ConversationMapper conversationMapper;

    @Mock
    SocketEmitter socketEmitter;

    ConversationEntity conversation;
    SocketPublisher publisher;

    @BeforeEach
    void setUp() {
        conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        publisher = new SocketPublisher(participantRepository, messageRepository, conversationMapper, socketEmitter);
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

    private ConversationDto dto(long unread) {
        return new ConversationDto(C, ConversationType.GROUP, "g", null, AT, unread, List.of());
    }

    @Test
    void publishMessageCreated_emitsTopicEventAndPerUserConversationWithOwnUnread() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C))).thenReturn(List.of(unread(0)));
        when(messageRepository.countUnreadMessagesByConversation(U2, List.of(C))).thenReturn(List.of(unread(4)));
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));
        when(conversationMapper.toDto(conversation, 4L)).thenReturn(dto(4));

        publisher.publishMessageCreatedAfterCommit(conversation, MESSAGE);

        ArgumentCaptor<MessageEvent> topic = ArgumentCaptor.forClass(MessageEvent.class);
        verify(socketEmitter).emit(eq("/conversations/" + C + "/messages"), topic.capture());
        assertThat(topic.getValue().eventType()).isEqualTo("message.created");
        assertThat(topic.getValue().message()).isEqualTo(MESSAGE);

        ArgumentCaptor<ConversationUpdatedEvent> events = ArgumentCaptor.forClass(ConversationUpdatedEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U2));
        assertThat(events.getAllValues())
                .extracting(e -> e.eventType(), e -> e.conversation().unreadCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 0L),
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 4L));
    }

    @Test
    void publishMessageCreated_userWithoutUnreadRow_getsZero() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C))).thenReturn(List.of());
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));

        publisher.publishMessageCreatedAfterCommit(conversation, MESSAGE);

        ArgumentCaptor<ConversationUpdatedEvent> event = ArgumentCaptor.forClass(ConversationUpdatedEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        assertThat(event.getValue().conversation().unreadCount()).isZero();
    }

    @Test
    void publishMessageCreated_insideTransaction_buildsNowEmitsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C))).thenReturn(List.of());
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));

        publisher.publishMessageCreatedAfterCommit(conversation, MESSAGE);

        verify(conversationMapper).toDto(conversation, 0L);
        verifyNoInteractions(socketEmitter);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();
        verify(socketEmitter).emit(eq("/conversations/" + C + "/messages"), any(MessageEvent.class));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationUpdatedEvent.class), eq(U1));
    }

    @Test
    void publishMessageUpdatedAndDeleted_emitTopicOnly() {
        publisher.publishMessageUpdatedAfterCommit(C, MESSAGE);
        publisher.publishMessageDeletedAfterCommit(C, MESSAGE);

        ArgumentCaptor<MessageEvent> events = ArgumentCaptor.forClass(MessageEvent.class);
        verify(socketEmitter, org.mockito.Mockito.times(2)).emit(eq("/conversations/" + C + "/messages"), events.capture());
        assertThat(events.getAllValues()).extracting(MessageEvent::eventType)
                .containsExactly("message.updated", "message.deleted");
        verify(socketEmitter, never()).emitTo(any(), any(), any());
    }

    @Test
    void publishConversationUpdated_emitsFullDtoToEachActiveUser() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));
        when(messageRepository.countUnreadMessagesByConversation(any(), eq(List.of(C)))).thenReturn(List.of());
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));

        publisher.publishConversationUpdatedAfterCommit(conversation);

        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationUpdatedEvent.class), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationUpdatedEvent.class), eq(U2));
        verify(socketEmitter, never()).emit(any(), any());
    }

    @Test
    void publishConversationRemoved_emitsOnlyToGivenUsers() {
        publisher.publishConversationRemovedAfterCommit(C, List.of(U2));

        ArgumentCaptor<ConversationRemovedEvent> event = ArgumentCaptor.forClass(ConversationRemovedEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U2));
        verify(socketEmitter, never()).emitTo(any(), any(), eq(U1));
        assertThat(event.getValue().eventType()).isEqualTo("conversation.removed");
        assertThat(event.getValue().conversationId()).isEqualTo(C);
        verifyNoInteractions(participantRepository);
    }

    @Test
    void publishConversationSeen_emitsToEveryActiveUserIncludingSeer() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));

        publisher.publishConversationSeenAfterCommit(C, U1, MESSAGE.id(), AT.plusSeconds(60));

        ArgumentCaptor<ConversationSeenEvent> seen = ArgumentCaptor.forClass(ConversationSeenEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), seen.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationSeenEvent.class), eq(U2));
        assertThat(seen.getValue().eventType()).isEqualTo("conversation.seen");
        assertThat(seen.getValue().seenByUserId()).isEqualTo(U1);
        assertThat(seen.getValue().lastReadMessageId()).isEqualTo(MESSAGE.id());
        assertThat(seen.getValue().lastReadAt()).isEqualTo(AT.plusSeconds(60));
        verify(socketEmitter, never()).emit(any(), any());
    }
}
```

- [ ] **Step 2: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=SocketPublisherTest`
Expected: COMPILE ERROR (DTO/constructor chưa có).

- [ ] **Step 3: DTO events**

```java
// dto/ConversationUpdatedEvent.java
package com.chat_socket.dto;

/** Full row for the receiving user (their own unreadCount) — the client upserts it into its list. */
public record ConversationUpdatedEvent(String eventType, ConversationDto conversation) {
    public static ConversationUpdatedEvent of(ConversationDto conversation) {
        return new ConversationUpdatedEvent("conversation.updated", conversation);
    }
}
```

```java
// dto/ConversationRemovedEvent.java
package com.chat_socket.dto;

import java.util.UUID;

/** The conversation is gone from this user's list: kicked, left, or deleted. */
public record ConversationRemovedEvent(String eventType, UUID conversationId) {
    public static ConversationRemovedEvent of(UUID conversationId) {
        return new ConversationRemovedEvent("conversation.removed", conversationId);
    }
}
```

```java
// dto/MessageEvent.java
package com.chat_socket.dto;

public record MessageEvent(String eventType, MessageDto message) {
    public static MessageEvent created(MessageDto message) {
        return new MessageEvent("message.created", message);
    }

    public static MessageEvent updated(MessageDto message) {
        return new MessageEvent("message.updated", message);
    }

    public static MessageEvent deleted(MessageDto message) {
        return new MessageEvent("message.deleted", message);
    }
}
```

`git rm dto/ConversationEvent.java dto/ConversationDelivery.java`.

- [ ] **Step 4: `SocketChannel`**

```java
public interface SocketChannel {
    String APP = "/app";
    String TOPIC = "/topic";
    String QUEUE = "/queue";
    String CONVERSATION = "/conversations";
    String MESSAGE = "/messages";
    String TYPING = "/typing";
    String ONLINE_USERS = "/online-users";
    String CONVERSATION_QUEUE = QUEUE + CONVERSATION;
    String MESSAGE_TOPIC = CONVERSATION + "/%s" + MESSAGE;
    String TYPING_TOPIC = CONVERSATION + "/%s" + TYPING;
    /** {@code @MessageMapping} pattern (relative to {@link #APP}). */
    String TYPING_MAPPING = CONVERSATION + "/{conversationId}" + TYPING;
}
```

(Bỏ `SEEN`, `CONVERSATION_SEEN_TOPIC`.)

- [ ] **Step 5: `SocketPublisher`**

```java
package com.chat_socket.socket;

import com.chat_socket.constant.SocketChannel;
import com.chat_socket.dto.ConversationRemovedEvent;
import com.chat_socket.dto.ConversationSeenEvent;
import com.chat_socket.dto.ConversationUpdatedEvent;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageEvent;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.mapper.ConversationMapper;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Payloads are built immediately (inside the caller's transaction); emits run after commit. */
@Component
public class SocketPublisher {
    private final ParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ConversationMapper conversationMapper;
    private final SocketEmitter socketEmitter;

    SocketPublisher(
            ParticipantRepository participantRepository,
            MessageRepository messageRepository,
            ConversationMapper conversationMapper,
            SocketEmitter socketEmitter) {
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.conversationMapper = conversationMapper;
        this.socketEmitter = socketEmitter;
    }

    /** A queue payload addressed to one user. */
    private record Delivery(UUID userId, Object event) {}

    public void publishMessageCreatedAfterCommit(ConversationEntity conversation, MessageDto message) {
        String topic = SocketChannel.MESSAGE_TOPIC.formatted(conversation.getId());
        MessageEvent event = MessageEvent.created(message);
        List<Delivery> deliveries = conversationUpdatedDeliveries(conversation);
        publishAfterCommit(() -> {
            socketEmitter.emit(topic, event);
            emitAll(deliveries);
        });
    }

    public void publishMessageUpdatedAfterCommit(UUID conversationId, MessageDto message) {
        String topic = SocketChannel.MESSAGE_TOPIC.formatted(conversationId);
        MessageEvent event = MessageEvent.updated(message);
        publishAfterCommit(() -> socketEmitter.emit(topic, event));
    }

    public void publishMessageDeletedAfterCommit(UUID conversationId, MessageDto message) {
        String topic = SocketChannel.MESSAGE_TOPIC.formatted(conversationId);
        MessageEvent event = MessageEvent.deleted(message);
        publishAfterCommit(() -> socketEmitter.emit(topic, event));
    }

    /** {@code conversation} must have participants (with users) and lastMessage loaded. */
    public void publishConversationUpdatedAfterCommit(ConversationEntity conversation) {
        List<Delivery> deliveries = conversationUpdatedDeliveries(conversation);
        publishAfterCommit(() -> emitAll(deliveries));
    }

    public void publishConversationRemovedAfterCommit(UUID conversationId, Collection<UUID> userIds) {
        ConversationRemovedEvent event = ConversationRemovedEvent.of(conversationId);
        List<Delivery> deliveries = userIds.stream().map(userId -> new Delivery(userId, event)).toList();
        publishAfterCommit(() -> emitAll(deliveries));
    }

    public void publishConversationSeenAfterCommit(
            UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, Instant seenAt) {
        ConversationSeenEvent event = ConversationSeenEvent.seen(conversationId, seenByUserId, lastReadMessageId, seenAt);
        List<Delivery> deliveries = participantRepository.findActiveUserIdsByConversationId(conversationId).stream()
                .map(userId -> new Delivery(userId, event))
                .toList();
        publishAfterCommit(() -> emitAll(deliveries));
    }

    // ponytail: one unread-count query per participant; batch when groups grow past a few dozen members.
    private List<Delivery> conversationUpdatedDeliveries(ConversationEntity conversation) {
        UUID conversationId = conversation.getId();
        return participantRepository.findActiveUserIdsByConversationId(conversationId).stream()
                .map(userId -> new Delivery(
                        userId,
                        ConversationUpdatedEvent.of(
                                conversationMapper.toDto(conversation, unreadCount(conversationId, userId)))))
                .toList();
    }

    private void emitAll(List<Delivery> deliveries) {
        deliveries.forEach(
                delivery -> socketEmitter.emitTo(SocketChannel.CONVERSATION_QUEUE, delivery.event(), delivery.userId()));
    }

    private void publishAfterCommit(Runnable publish) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new SocketSynchronization(publish));
    }

    private long unreadCount(UUID conversationId, UUID userId) {
        return messageRepository.countUnreadMessagesByConversation(userId, List.of(conversationId)).stream()
                .findFirst()
                .map(MessageRepository.UnreadCountProjection::getUnreadCount)
                .orElse(0L);
    }
}
```

- [ ] **Step 6: Chạy `SocketPublisherTest`**

Run: `./mvnw -q test -Dtest=SocketPublisherTest`
Expected: PASS (7 tests). Service compile lỗi là bình thường ở bước này → Task 10 sửa. **Không commit** cho tới khi Task 10 xanh (cùng một commit).

### Task 10: Nối service vào publisher mới

**Files:**
- Modify: `repository/ConversationRepository.java`, `service/impl/ConversationServiceImpl.java`, `service/impl/MessageServiceImpl.java`
- Test: `service/impl/ConversationServiceImplTest.java`, `service/impl/MessageServiceImplTest.java`

**Interfaces:**
- Produces: `ConversationRepository.findWithDetails(UUID) → Optional<ConversationEntity>` (dùng ở Task 12, 15, 16).

- [ ] **Step 1: Repository default method**

```java
    default Optional<ConversationEntity> findWithDetails(UUID conversationId) {
        return findConversationsWithDetails(List.of(conversationId)).stream().findFirst();
    }
```

(import `java.util.List`, `java.util.Optional` nếu chưa có.)

- [ ] **Step 2: `ConversationServiceImpl`**

- `findConversationWithDetails(id)` private → thân thành `conversationRepository.findWithDetails(id).orElseThrow(() -> new NotFoundException("Conversation not found."))`.
- Xóa private `publishConversationUpdated(ConversationEntity)`. Ở `updateGroup`, `addGroupMembers`: thay `publishConversationUpdated(conversation)` bằng `socketPublisher.publishConversationUpdatedAfterCommit(updatedConversation)`.
- `removeGroupMember`: sau `conversationRepository.save(conversation)`:

```java
        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);
        socketPublisher.publishConversationRemovedAfterCommit(conversationId, List.of(memberId));
        socketPublisher.publishConversationUpdatedAfterCommit(updatedConversation);
```

- `leaveGroup`: sau save:

```java
        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);
        socketPublisher.publishConversationRemovedAfterCommit(conversationId, List.of(currentUser.id()));
        socketPublisher.publishConversationUpdatedAfterCommit(updatedConversation);
```

- `deleteGroup`: `socketPublisher.publishGroupDeletedAfterCommit(conversationId, currentUser.id())` → `socketPublisher.publishConversationRemovedAfterCommit(conversationId, List.of(currentUser.id()))`.
- `markAsSeen`: `socketPublisher.publishConversationSeenAfterCommit(conversationId, currentUser.id(), lastMessage.getId(), seenAt)`; xóa `messageMapper.toDto(lastMessage)` nếu không còn dùng ở đây.
- `createDirectConversation(UUID, List<UUID>)` (nhánh `POST /conversation`): sau `conversation = findConversationWithDetails(...)` thêm `socketPublisher.publishConversationUpdatedAfterCommit(conversation);`.
- `createGroupConversation`: trước `return` thêm `socketPublisher.publishConversationUpdatedAfterCommit(conversation);` (conversation đã có `participants` với `user` set in-memory).

- [ ] **Step 3: `MessageServiceImpl`**

Cả 2 `send*`: thay `socketPublisher.publishMessageAfterCommit(conversation.getId(), messageDto, conversation.getLastMessageAt())` bằng:

```java
        ConversationEntity updatedConversation = conversationRepository
                .findWithDetails(conversation.getId())
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        socketPublisher.publishMessageCreatedAfterCommit(updatedConversation, messageDto);
```

- [ ] **Step 4: Sửa test service**

`MessageServiceImplTest`: các test success (dòng ~179, 196, 262): stub `when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));` và `verify(socketPublisher).publishMessageCreatedAfterCommit(conversation, dto);` (thay `publishMessageAfterCommit(...)`). Nếu `conversationRepository` là mock của interface, `findWithDetails` là default method → phải stub tường minh như trên (Mockito mock không chạy default method).

`ConversationServiceImplTest`:
- Dòng 408-410: `verify(socketPublisher).publishConversationSeenAfterCommit(eq(C), eq(BIG), eq(last.getId()), any(Instant.class));`
- Dòng 446: `verify(socketPublisher).publishConversationRemovedAfterCommit(C, List.of(BIG));`
- Dòng 469, 533 (updateGroup / addGroupMembers): `verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);` với `conversation` là entity mà `findConversationsWithDetails(List.of(C))` stub trả về (helper dòng 119-121). Stub thêm `when(conversationRepository.findWithDetails(C)).thenReturn(Optional.of(conversation));` trong helper đó (mock không chạy default method).
- Dòng 616 (removeGroupMember) → đổi test thành:

```java
    @Test
    void removeGroupMember_publishesRemovedToTargetAndUpdatedToRest() {
        // ... phần stub cũ giữ nguyên ...
        service.removeGroupMember(C, SMALL);

        verify(socketPublisher).publishConversationRemovedAfterCommit(C, List.of(SMALL));
        verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);
    }
```

- Dòng 640 (leaveGroup): `verify(socketPublisher).publishConversationRemovedAfterCommit(C, List.of(BIG)); verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);`.
- Test `createConversation` GROUP/DIRECT: thêm `verify(socketPublisher).publishConversationUpdatedAfterCommit(any(ConversationEntity.class));`.

- [ ] **Step 5: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit (gồm Task 9)**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "feat: socket events carry full conversation record; seen/removed delivered per user

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 11: Interceptor chặn SUBSCRIBE mọi `/topic/conversations/{id}/…`

**Files:**
- Modify: `security/SocketChannelInterceptor.java`
- Test: `security/SocketChannelInterceptorTest.java`

- [ ] **Step 1: Thêm test**

Xem test hiện có cho destination `/topic/conversations/{id}/messages` (non-participant → `ForbiddenException`) và viết bản sao cho typing:

```java
    @Test
    void subscribe_typingTopic_nonParticipant_isForbidden() {
        // copy setup của test messages-topic non-participant trong file, đổi destination:
        // "/topic/conversations/" + C + "/typing"
        // expect: assertThatThrownBy(...).isInstanceOf(ForbiddenException.class)
    }

    @Test
    void subscribe_conversationTopic_malformedId_isForbidden() {
        // destination "/topic/conversations/not-a-uuid/typing" với principal hợp lệ → ForbiddenException("Conversation destination is invalid.")
    }
```

(Viết đầy đủ theo đúng cách file dựng `StompHeaderAccessor` + principal ở các test hiện có; hai test này khác biệt chỉ ở destination.)

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `./mvnw -q test -Dtest=SocketChannelInterceptorTest`
Expected: FAIL — typing topic hiện không bị chặn.

- [ ] **Step 3: Sửa interceptor**

Thay 2 hằng và 3 method cuối:

```java
    private static final String CONVERSATION_TOPIC_PREFIX = SocketChannel.TOPIC + SocketChannel.CONVERSATION + "/";

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(CONVERSATION_TOPIC_PREFIX)) return;

        UUID conversationId = conversationIdFromDestination(destination);
        if (principal == null) throw new ForbiddenException("Socket user is not authenticated.");
        UserSecurity userSecurity = Security.getUserSecurityFromPrincipal(principal);
        if (userSecurity == null) throw new ForbiddenException("Socket user is invalid.");
        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, userSecurity.id()))
            throw new ForbiddenException("You are not a participant of this conversation.");
    }

    /** {@code /topic/conversations/<id>/<anything>} → id. */
    private UUID conversationIdFromDestination(String destination) {
        String rest = destination.substring(CONVERSATION_TOPIC_PREFIX.length());
        int slash = rest.indexOf('/');
        String conversationId = slash < 0 ? rest : rest.substring(0, slash);
        try {
            return UUID.fromString(conversationId);
        } catch (IllegalArgumentException exception) {
            throw new ForbiddenException("Conversation destination is invalid.");
        }
    }
```

Xóa `isConversationMessageDestination` và 2 hằng cũ.

- [ ] **Step 4: Chạy test**

Run: `./mvnw -q test -Dtest=SocketChannelInterceptorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "fix: require participant for any conversation topic subscription

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 4 — `GET /conversation/{id}`

### Task 12: Chi tiết một conversation

**Files:**
- Modify: `service/ConversationService.java`, `service/impl/ConversationServiceImpl.java`, `controller/ConversationController.java`
- Test: `service/impl/ConversationServiceImplTest.java`, `controller/ConversationControllerTest.java`

- [ ] **Step 1: Test service**

```java
    @Test
    void getConversation_nonParticipant_throwsForbidden() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.existsById(C)).thenReturn(true);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, BIG))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getConversation(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getConversation_returnsDtoWithOwnUnreadCount() {
        TestFixtures.authenticateAs(BIG);
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        MessageRepository.UnreadCountProjection three = mock(MessageRepository.UnreadCountProjection.class);
        when(three.getUnreadCount()).thenReturn(3L);
        when(conversationRepository.existsById(C)).thenReturn(true);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, BIG))
                .thenReturn(true);
        when(conversationRepository.findWithDetails(C)).thenReturn(Optional.of(conversation));
        when(messageRepository.countUnreadMessagesByConversation(BIG, List.of(C))).thenReturn(List.of(three));
        when(conversationMapper.toDto(conversation, 3L)).thenReturn(DTO);

        BaseResponse<ConversationDto> response = service.getConversation(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data()).isSameAs(DTO);
    }
```

(`DTO` là hằng `ConversationDto` dòng 63 của file.)

- [ ] **Step 2: Test controller**

```java
    @Test
    void getConversation_returnsDto() throws Exception {
        when(conversationService.getConversation(C))
                .thenReturn(new BaseResponse<>(
                        new ConversationDto(C, ConversationType.GROUP, "Team", null, null, 2, List.of()), "ok", 200));

        mockMvc.perform(get("/v1/conversation/{id}", C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupName").value("Team"))
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }
```

- [ ] **Step 3: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=ConversationServiceImplTest`
Expected: COMPILE ERROR `getConversation`.

- [ ] **Step 4: Implement**

`ConversationService`: `BaseResponse<ConversationDto> getConversation(UUID conversationId);`

`ConversationServiceImpl`:

```java
    @Override
    public BaseResponse<ConversationDto> getConversation(UUID conversationId) {
        UserSecurity currentUser = Security.getCurrentUser();
        ensureCanReadConversation(conversationId, currentUser.id());

        ConversationEntity conversation = findConversationWithDetails(conversationId);
        long unreadCount = messageRepository.countUnreadMessagesByConversation(currentUser.id(), List.of(conversationId))
                .stream()
                .findFirst()
                .map(MessageRepository.UnreadCountProjection::getUnreadCount)
                .orElse(0L);

        return new BaseResponse<>(
                conversationMapper.toDto(conversation, unreadCount),
                "Conversation retrieved successfully.",
                HttpStatus.OK.value());
    }
```

`ConversationController` (đặt trước `getMessages`):

```java
    @GetMapping("/{conversationId}")
    public ResponseEntity<BaseResponse<ConversationDto>> getConversation(@PathVariable UUID conversationId) {
        BaseResponse<ConversationDto> body = conversationService.getConversation(conversationId);
        return ResponseEntity.status(body.status()).body(body);
    }
```

- [ ] **Step 5: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "feat: GET /v1/conversation/{id}

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 5 — Đổi mật khẩu

### Task 13: `PATCH /user/me/password`

**Files:**
- Create: `dto/ChangePasswordRequest.java`
- Modify: `service/UserService.java`, `service/impl/UserServiceImpl.java`, `controller/UserController.java`
- Test: `service/impl/UserServiceImplTest.java`, `controller/UserControllerTest.java`

- [ ] **Step 1: Test service**

Trong `UserServiceImplTest` thêm `@Mock PasswordEncoder passwordEncoder;` và constructor `new UserServiceImpl(userRepository, friendRepository, friendRequestRepository, userMapper, passwordEncoder)`. Thêm:

```java
    @Test
    void changePassword_wrongCurrent_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);
        UserEntity me = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("old", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("old", "newpassword")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current password is incorrect.");
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_sameAsCurrent_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);
        UserEntity me = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("samepass", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("samepass", "samepass")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("New password must differ from current password.");
    }

    @Test
    void changePassword_success_storesEncodedAndReturns204() {
        TestFixtures.authenticateAs(BIG);
        UserEntity me = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("hashed-new");

        BaseResponse<Void> response = service.changePassword(new ChangePasswordRequest("old", "newpassword"));

        assertThat(response.status()).isEqualTo(204);
        assertThat(me.getHashedPassword()).isEqualTo("hashed-new");
        verify(userRepository).save(me);
    }
```

- [ ] **Step 2: Test controller**

```java
    @Test
    void changePassword_shortNewPassword_returns400() throws Exception {
        mockMvc.perform(patch("/v1/user/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.newPassword").exists());
    }

    @Test
    void changePassword_valid_returns204() throws Exception {
        when(userService.changePassword(new ChangePasswordRequest("old", "newpassword")))
                .thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(patch("/v1/user/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpassword\"}"))
                .andExpect(status().isNoContent());
    }
```

- [ ] **Step 3: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=UserServiceImplTest`
Expected: COMPILE ERROR.

- [ ] **Step 4: Implement**

```java
// dto/ChangePasswordRequest.java
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required") String currentPassword,
        @NotBlank(message = "New password is required")
                @Size(min = 8, max = 72, message = "New password must be 8-72 characters")
                String newPassword) {}
```

`UserService`: `BaseResponse<Void> changePassword(ChangePasswordRequest request);`

`UserServiceImpl`: thêm field + constructor param `PasswordEncoder passwordEncoder` (import `org.springframework.security.crypto.password.PasswordEncoder`), và:

```java
    @Override
    @Transactional
    public BaseResponse<Void> changePassword(ChangePasswordRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UserEntity user =
                userRepository.findById(currentUser.id()).orElseThrow(() -> new NotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getHashedPassword()))
            throw new BadRequestException("Current password is incorrect.");
        if (request.currentPassword().equals(request.newPassword()))
            throw new BadRequestException("New password must differ from current password.");

        user.setHashedPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return new BaseResponse<>(null, null, HttpStatus.NO_CONTENT.value());
    }
```

`UserController`:

```java
    @PatchMapping("/me/password")
    public ResponseEntity<BaseResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        BaseResponse<Void> body = userService.changePassword(request);
        return ResponseEntity.status(body.status()).body(body);
    }
```

- [ ] **Step 5: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "feat: PATCH /v1/user/me/password

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 6 — Upload

### Task 14: `POST /upload` + serve `/files/**`

**Files:**
- Create: `dto/UploadResponse.java`, `service/FileStorage.java`, `controller/UploadController.java`, `config/StaticFilesConfig.java`
- Modify: `ApplicationYaml.java`, `src/main/resources/application.yaml`, `constant/RouteApi.java`, `security/SecurityFilter.java`, `config/SecurityServerConfig.java`, `config/GlobalExceptionHandler.java`
- Test: `src/test/java/com/chat_socket/service/FileStorageTest.java`, `controller/UploadControllerTest.java`

**Interfaces:**
- Produces: `FileStorage.store(MultipartFile) → UploadResponse`; `RouteApi.UPLOAD_API = "/v1/upload"`, `RouteApi.FILES = "/files"`; `ApplicationYaml(..., String uploadDir, String publicUrl)`.

- [ ] **Step 1: Test `FileStorage`**

```java
package com.chat_socket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.dto.UploadResponse;
import com.chat_socket.exception.BadRequestException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageTest {
    @TempDir
    Path dir;

    private FileStorage storage() {
        return new FileStorage(new ApplicationYaml("s", 1, 1, List.of(), dir.toString(), "http://host/api"));
    }

    @Test
    void store_emptyFile_throwsBadRequest() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage().store(empty))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("File is required.");
    }

    @Test
    void store_writesFileAndReturnsPublicUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG", "image/png", new byte[] {1, 2, 3});

        UploadResponse response = storage().store(file);

        assertThat(response.name()).matches("[0-9a-f-]{36}\\.png");
        assertThat(response.url()).isEqualTo("http://host/api/files/" + response.name());
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(dir.resolve(response.name()))).containsExactly(1, 2, 3);
    }

    @Test
    void store_ignoresClientPathAndKeepsOnlySafeExtension() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "../../evil.sh", "text/plain", new byte[] {1});
        MockMultipartFile noExt = new MockMultipartFile("file", "..", "text/plain", new byte[] {1});

        UploadResponse a = storage().store(file);
        UploadResponse b = storage().store(noExt);

        assertThat(a.name()).matches("[0-9a-f-]{36}\\.sh");
        assertThat(b.name()).matches("[0-9a-f-]{36}");
        assertThat(Files.list(dir)).hasSize(2);
        assertThat(Files.exists(dir.getParent().resolve("evil.sh"))).isFalse();
    }
}
```

- [ ] **Step 2: Test controller**

```java
package com.chat_socket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.UploadResponse;
import com.chat_socket.service.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {
    @Mock
    FileStorage fileStorage;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(fileStorage))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upload_returns201WithUrl() throws Exception {
        when(fileStorage.store(any())).thenReturn(new UploadResponse("http://host/api/files/x.png", "x.png", 3, "image/png"));

        mockMvc.perform(multipart("/v1/upload")
                        .file(new MockMultipartFile("file", "x.png", "image/png", new byte[] {1, 2, 3})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("http://host/api/files/x.png"));
    }

    @Test
    void upload_missingFilePart_returns400() throws Exception {
        mockMvc.perform(multipart("/v1/upload")).andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 3: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=FileStorageTest`
Expected: COMPILE ERROR.

- [ ] **Step 4: Config**

`ApplicationYaml`:

```java
@ConfigurationProperties(prefix = "chat-socket")
public record ApplicationYaml(
        String accessTokenSecret,
        long accessTokenTtl,
        long refreshTokenTtl,
        List<String> clientUrl,
        String uploadDir,
        String publicUrl) {}
```

(Nếu file staged đã có field khác, thêm 2 field mới vào cuối, giữ phần còn lại.)

`application.yaml` — thêm vào `spring:`:

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

và vào `chat-socket:`:

```yaml
  upload-dir: ${CHAT_SOCKET_UPLOAD_DIR:./uploads}
  public-url: ${CHAT_SOCKET_PUBLIC_URL:http://localhost:8089/api} # includes the servlet path
```

`RouteApi`: `String UPLOAD_API = API_V1 + "/upload"; String FILES = "/files";`

- [ ] **Step 5: `FileStorage`, DTO, controller, static, security**

```java
// dto/UploadResponse.java
public record UploadResponse(String url, String name, long size, String contentType) {}
```

```java
// service/FileStorage.java
package com.chat_socket.service;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.UploadResponse;
import com.chat_socket.exception.BadRequestException;
import com.github.f4b6a3.uuid.UuidCreator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** ponytail: local disk; swap for S3/Cloudinary when running more than one instance. */
@Component
public class FileStorage {
    private static final Pattern SAFE_EXTENSION = Pattern.compile("[A-Za-z0-9]{1,10}");

    private final Path directory;
    private final String publicUrl;

    public FileStorage(ApplicationYaml config) {
        this.directory = Path.of(config.uploadDir()).toAbsolutePath().normalize();
        this.publicUrl = config.publicUrl();
    }

    public UploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("File is required.");

        String extension = safeExtension(file.getOriginalFilename());
        String name = UuidCreator.getTimeOrderedEpoch() + (extension.isEmpty() ? "" : "." + extension);
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(name));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return new UploadResponse(publicUrl + RouteApi.FILES + "/" + name, name, file.getSize(), file.getContentType());
    }

    /** Only the part after the last dot, only if it is plain alphanumerics; the client name is never used as a path. */
    private static String safeExtension(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) return "";
        String extension = originalName.substring(dot + 1).toLowerCase();
        return SAFE_EXTENSION.matcher(extension).matches() ? extension : "";
    }
}
```

```java
// controller/UploadController.java
@RestController
@RequestMapping(RouteApi.UPLOAD_API)
public class UploadController {
    private final FileStorage fileStorage;

    public UploadController(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<UploadResponse>> upload(@RequestParam("file") MultipartFile file) {
        BaseResponse<UploadResponse> body =
                new BaseResponse<>(fileStorage.store(file), "File uploaded successfully.", HttpStatus.CREATED.value());
        return ResponseEntity.status(body.status()).body(body);
    }
}
```

```java
// config/StaticFilesConfig.java
@Configuration
public class StaticFilesConfig implements WebMvcConfigurer {
    private final ApplicationYaml config;

    StaticFilesConfig(ApplicationYaml config) {
        this.config = config;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(config.uploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(RouteApi.FILES + "/**").addResourceLocations(location);
    }
}
```

`SecurityFilter.shouldNotFilter`: thêm `|| requestUri.startsWith("/api" + RouteApi.FILES + "/")`.
`SecurityServerConfig`: `.requestMatchers(RouteApi.AUTH_API + "/**", RouteApi.HEALTH_API, RouteApi.FILES + "/**", "/ws*").permitAll()`.
`GlobalExceptionHandler`: thêm override

```java
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new BaseResponse<>(null, "File is too large (max 10MB).", HttpStatus.PAYLOAD_TOO_LARGE.value()));
    }
```

(import `org.springframework.web.multipart.MaxUploadSizeExceededException`.) `MissingServletRequestPartException` đã được `ResponseEntityExceptionHandler` trả 400 → test `upload_missingFilePart_returns400` pass.

- [ ] **Step 6: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS. Nếu `ApplicationYaml` đổi arity làm test khác (`AuthServiceImplTest`) không compile → sửa constructor call ở đó thêm `null, null`.

- [ ] **Step 7: Xác minh thủ công (cần infra)**

`./mvnw -q spring-boot:run`; `curl -F file=@README.md -H "Authorization: Bearer <token>" localhost:8089/api/v1/upload` → 201 với `url`; `curl -I <url>` → 200 không cần token.

- [ ] **Step 8: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "feat: POST /v1/upload with local file storage served at /files

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 7 — Sửa / xóa message

### Task 15: `PATCH /message/{messageId}`

**Files:**
- Create: `dto/UpdateMessageRequest.java`
- Modify: `repository/MessageRepository.java`, `service/MessageService.java`, `service/impl/MessageServiceImpl.java`, `controller/MessageController.java`
- Test: `service/impl/MessageServiceImplTest.java`, `controller/MessageControllerTest.java`

**Interfaces:**
- Produces: `MessageRepository.findByIdAndDeletedFalse(UUID) → Optional<MessageEntity>`; `MessageService.updateMessage(UUID messageId, UpdateMessageRequest)`.

- [ ] **Step 1: Test service**

```java
    @Test
    void updateMessage_notSender_throwsForbidden() {
        TestFixtures.authenticateAs(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.updateMessage(message.getId(), new UpdateMessageRequest("edited")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only edit your own messages.");
    }

    @Test
    void updateMessage_imageMessage_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        message.setType(MessageType.IMAGE);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.updateMessage(message.getId(), new UpdateMessageRequest("edited")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only text messages can be edited.");
    }

    @Test
    void updateMessage_success_savesPublishesAndRefreshesConversationWhenLast() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(message);
        MessageDto dto = new MessageDto(message.getId(), CONVERSATION_ID, SMALL, "edited", null, MessageType.TEXT, null, null);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message)).thenReturn(dto);
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        BaseResponse<MessageDto> response = service.updateMessage(message.getId(), new UpdateMessageRequest(" edited "));

        assertThat(response.status()).isEqualTo(200);
        assertThat(message.getContent()).isEqualTo("edited");
        verify(socketPublisher).publishMessageUpdatedAfterCommit(CONVERSATION_ID, dto);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);
    }

    @Test
    void updateMessage_notLast_doesNotRefreshConversation() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL)));
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message)).thenReturn(new MessageDto(message.getId(), CONVERSATION_ID, SMALL, "x", null, MessageType.TEXT, null, null));

        service.updateMessage(message.getId(), new UpdateMessageRequest("x"));

        verify(socketPublisher, never()).publishConversationUpdatedAfterCommit(any());
    }
```

- [ ] **Step 2: Test controller**

```java
    @Test
    void updateMessage_blankContent_returns400() throws Exception {
        mockMvc.perform(patch("/v1/message/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMessage_returns200() throws Exception {
        when(messageService.updateMessage(ID, new UpdateMessageRequest("edited")))
                .thenReturn(new BaseResponse<>(null, "Message updated successfully.", 200));

        mockMvc.perform(patch("/v1/message/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"edited\"}"))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 3: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=MessageServiceImplTest`
Expected: COMPILE ERROR.

- [ ] **Step 4: Implement**

```java
// dto/UpdateMessageRequest.java
public record UpdateMessageRequest(@NotBlank(message = "Content is required") String content) {}
```

`MessageRepository`: `Optional<MessageEntity> findByIdAndDeletedFalse(UUID id);`

`MessageService`: `BaseResponse<MessageDto> updateMessage(UUID messageId, UpdateMessageRequest request);`

`MessageServiceImpl`:

```java
    @Override
    @Transactional
    public BaseResponse<MessageDto> updateMessage(UUID messageId, UpdateMessageRequest request) {
        UUID currentUserId = Security.getCurrentUser().id();
        MessageEntity message = getOwnMessageOrThrow(messageId, currentUserId, "You can only edit your own messages.");
        if (message.getType() != MessageType.TEXT) throw new BadRequestException("Only text messages can be edited.");

        message.setContent(request.content().trim());
        message = messageRepository.save(message);
        MessageDto messageDto = messageMapper.toDto(message);

        UUID conversationId = message.getConversation().getId();
        socketPublisher.publishMessageUpdatedAfterCommit(conversationId, messageDto);
        if (isLastMessage(message)) publishConversationUpdated(conversationId);

        return new BaseResponse<>(messageDto, "Message updated successfully.", HttpStatus.OK.value());
    }

    private MessageEntity getOwnMessageOrThrow(UUID messageId, UUID userId, String forbiddenMessage) {
        MessageEntity message = messageRepository
                .findByIdAndDeletedFalse(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));
        if (!message.getSender().getId().equals(userId)) throw new ForbiddenException(forbiddenMessage);
        return message;
    }

    private static boolean isLastMessage(MessageEntity message) {
        MessageEntity last = message.getConversation().getLastMessage();
        return last != null && last.getId().equals(message.getId());
    }

    private void publishConversationUpdated(UUID conversationId) {
        ConversationEntity conversation = conversationRepository
                .findWithDetails(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        socketPublisher.publishConversationUpdatedAfterCommit(conversation);
    }
```

(Dùng lại `publishConversationUpdated(UUID)` cho 2 method `send*` thay khối 3 dòng ở Task 10 nếu muốn gọn — tùy.)

`MessageController`:

```java
    @PatchMapping("/{messageId}")
    public ResponseEntity<BaseResponse<MessageDto>> updateMessage(
            @PathVariable UUID messageId, @Valid @RequestBody UpdateMessageRequest request) {
        BaseResponse<MessageDto> body = messageService.updateMessage(messageId, request);
        return ResponseEntity.status(body.status()).body(body);
    }
```

- [ ] **Step 5: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "feat: PATCH /v1/message/{id} for the sender's own text messages

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

### Task 16: `DELETE /message/{messageId}`

**Files:**
- Modify: `repository/MessageRepository.java`, `service/MessageService.java`, `service/impl/MessageServiceImpl.java`, `controller/MessageController.java`
- Test: `service/impl/MessageServiceImplTest.java`, `controller/MessageControllerTest.java`

**Interfaces:**
- Produces: `MessageRepository.findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(UUID)`; `MessageService.deleteMessage(UUID)`.

- [ ] **Step 1: Test service**

```java
    @Test
    void deleteMessage_notSender_throwsForbidden() {
        TestFixtures.authenticateAs(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.deleteMessage(message.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only delete your own messages.");
    }

    @Test
    void deleteMessage_notLast_softDeletesAndPublishesOnlyMessageEvent() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL)));
        MessageDto dto = new MessageDto(message.getId(), CONVERSATION_ID, SMALL, "hello", null, MessageType.TEXT, null, null);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message)).thenReturn(dto);

        BaseResponse<Void> response = service.deleteMessage(message.getId());

        assertThat(response.status()).isEqualTo(204);
        assertThat(message.isDeleted()).isTrue();
        verify(socketPublisher).publishMessageDeletedAfterCommit(CONVERSATION_ID, dto);
        verify(socketPublisher, never()).publishConversationUpdatedAfterCommit(any());
    }

    @Test
    void deleteMessage_last_pointsConversationToPreviousMessage() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity previous = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        previous.setCreatedAt(TestFixtures.FIXED_TIME.minusSeconds(60));
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(message);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message)).thenReturn(new MessageDto(message.getId(), CONVERSATION_ID, SMALL, "hello", null, MessageType.TEXT, null, null));
        when(messageRepository.findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(CONVERSATION_ID))
                .thenReturn(Optional.of(previous));
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        service.deleteMessage(message.getId());

        assertThat(conversation.getLastMessage()).isSameAs(previous);
        assertThat(conversation.getLastMessageAt()).isEqualTo(previous.getCreatedAt());
        verify(conversationRepository).save(conversation);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);
    }

    @Test
    void deleteMessage_lastAndOnlyMessage_clearsConversationLastMessage() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(message);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message)).thenReturn(new MessageDto(message.getId(), CONVERSATION_ID, SMALL, "hello", null, MessageType.TEXT, null, null));
        when(messageRepository.findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(CONVERSATION_ID))
                .thenReturn(Optional.empty());
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        service.deleteMessage(message.getId());

        assertThat(conversation.getLastMessage()).isNull();
        assertThat(conversation.getLastMessageAt()).isEqualTo(TestFixtures.FIXED_TIME); // unchanged, keeps list order
        verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);
    }
```

- [ ] **Step 2: Test controller**

```java
    @Test
    void deleteMessage_returns204() throws Exception {
        when(messageService.deleteMessage(ID)).thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(delete("/v1/message/{id}", ID)).andExpect(status().isNoContent());
    }
```

- [ ] **Step 3: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=MessageServiceImplTest`
Expected: COMPILE ERROR.

- [ ] **Step 4: Implement**

`MessageRepository`: `Optional<MessageEntity> findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(UUID conversationId);`

`MessageService`: `BaseResponse<Void> deleteMessage(UUID messageId);`

`MessageServiceImpl`:

```java
    @Override
    @Transactional
    public BaseResponse<Void> deleteMessage(UUID messageId) {
        UUID currentUserId = Security.getCurrentUser().id();
        MessageEntity message =
                getOwnMessageOrThrow(messageId, currentUserId, "You can only delete your own messages.");
        boolean wasLast = isLastMessage(message);

        message.setDeleted(true);
        message = messageRepository.save(message);
        MessageDto messageDto = messageMapper.toDto(message);

        ConversationEntity conversation = message.getConversation();
        socketPublisher.publishMessageDeletedAfterCommit(conversation.getId(), messageDto);

        if (wasLast) {
            MessageEntity previous = messageRepository
                    .findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(conversation.getId())
                    .orElse(null);
            conversation.setLastMessage(previous);
            if (previous != null) conversation.setLastMessageAt(previous.getCreatedAt());
            conversationRepository.save(conversation);
            publishConversationUpdated(conversation.getId());
        }

        return new BaseResponse<>(null, null, HttpStatus.NO_CONTENT.value());
    }
```

`MessageController`:

```java
    @DeleteMapping("/{messageId}")
    public ResponseEntity<BaseResponse<Void>> deleteMessage(@PathVariable UUID messageId) {
        BaseResponse<Void> body = messageService.deleteMessage(messageId);
        return ResponseEntity.status(body.status()).body(body);
    }
```

- [ ] **Step 5: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "feat: DELETE /v1/message/{id} soft-deletes and re-points conversation lastMessage

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 8 — Typing

### Task 17: `/app/conversations/{id}/typing` → `/topic/conversations/{id}/typing`

**Files:**
- Create: `dto/TypingEvent.java`
- Modify: `socket/SocketController.java`
- Test: `src/test/java/com/chat_socket/socket/SocketControllerTest.java`

- [ ] **Step 1: Test**

```java
package com.chat_socket.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.TypingEvent;
import com.chat_socket.repository.ParticipantRepository;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SocketControllerTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    UserOnlineRegistry userOnlineRegistry;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    SocketEmitter socketEmitter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Principal principalOf(UUID userId) {
        TestFixtures.authenticateAs(userId);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void typing_participant_broadcastsToConversationTopic() {
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, U1))
                .thenReturn(true);
        SocketController controller = new SocketController(userOnlineRegistry, participantRepository, socketEmitter);

        controller.typing(C, principalOf(U1));

        ArgumentCaptor<TypingEvent> event = ArgumentCaptor.forClass(TypingEvent.class);
        verify(socketEmitter).emit(eq("/conversations/" + C + "/typing"), event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("typing");
        assertThat(event.getValue().conversationId()).isEqualTo(C);
        assertThat(event.getValue().userId()).isEqualTo(U1);
    }

    @Test
    void typing_nonParticipant_isDropped() {
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, U1))
                .thenReturn(false);
        SocketController controller = new SocketController(userOnlineRegistry, participantRepository, socketEmitter);

        controller.typing(C, principalOf(U1));

        verify(socketEmitter, never()).emit(any(), any());
    }

    @Test
    void typing_noPrincipal_isDropped() {
        SocketController controller = new SocketController(userOnlineRegistry, participantRepository, socketEmitter);

        controller.typing(C, null);

        verify(socketEmitter, never()).emit(any(), any());
    }
}
```

- [ ] **Step 2: Chạy, xác nhận compile fail**

Run: `./mvnw -q test -Dtest=SocketControllerTest`
Expected: COMPILE ERROR.

- [ ] **Step 3: Implement**

```java
// dto/TypingEvent.java
public record TypingEvent(String eventType, UUID conversationId, UUID userId) {
    public static TypingEvent of(UUID conversationId, UUID userId) {
        return new TypingEvent("typing", conversationId, userId);
    }
}
```

`SocketController`:

```java
@Controller
public class SocketController {
    private final UserOnlineRegistry userOnlineRegistry;
    private final ParticipantRepository participantRepository;
    private final SocketEmitter socketEmitter;

    SocketController(
            UserOnlineRegistry userOnlineRegistry,
            ParticipantRepository participantRepository,
            SocketEmitter socketEmitter) {
        this.userOnlineRegistry = userOnlineRegistry;
        this.participantRepository = participantRepository;
        this.socketEmitter = socketEmitter;
    }

    @SubscribeMapping(SocketChannel.ONLINE_USERS)
    public Set<UUID> onlineUsers() {
        return userOnlineRegistry.onlineUserIds();
    }

    /** No "stopped typing": the client expires an indicator a few seconds after the last event. */
    @MessageMapping(SocketChannel.TYPING_MAPPING)
    public void typing(@DestinationVariable UUID conversationId, Principal principal) {
        UserSecurity user = Security.getUserSecurityFromPrincipal(principal);
        if (user == null) return;
        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, user.id())) return;

        socketEmitter.emit(SocketChannel.TYPING_TOPIC.formatted(conversationId), TypingEvent.of(conversationId, user.id()));
    }
}
```

(import `org.springframework.messaging.handler.annotation.DestinationVariable`, `MessageMapping`, `java.security.Principal`.)

- [ ] **Step 4: Chạy toàn bộ test**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./mvnw -q spotless:apply
git add -A src/main src/test
git commit -m "feat: typing indicator over STOMP

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Phase 9 — Tài liệu cho FE

### Task 18: `docs/api-contract-changes.md`

**Files:**
- Create: `docs/api-contract-changes.md`

- [ ] **Step 1: Viết tài liệu** (tiếng Việt, nội dung tối thiểu bên dưới — lấy shape thật từ code sau khi các task trên xong)

```markdown
# API contract changes — hướng dẫn cho FE (`monorepo/apps/chat`)

Ngày: <ngày merge>. Backend: `chat-socket` branch `refactor/cleanup`. Mọi mục đều **breaking**.

## 1. Thời gian
- Mọi timestamp là UTC, format cố định `2026-09-20T10:00:00.123456Z` (6 chữ số). `dayjs.utc(value)` vẫn đọc được.
- Cursor (`nextCursor`) truyền lại nguyên chuỗi. Cursor tự tạo phải có `Z`/offset, không gửi local time.
- `readers-of.ts` so chuỗi vẫn đúng vì fixed-width; khuyến nghị đổi sang `dayjs(a).isSameOrAfter(b)`.

## 2. Đổi tên field / DTO
| Chỗ | Cũ | Mới |
|---|---|---|
| Mọi list (`/user`, `/friend`, `/conversation`, `/conversation/{id}/messages`) | `data.messages` | `data.items` |
| `ChatConversationRecord` | có `createdById, directUserAId, directUserBId, lastMessageId, createdAt, updatedAt` | bỏ; còn `id, type, groupName, lastMessage, lastMessageAt, unreadCount, participants` |
| `ChatConversationParticipant` | `username?` | `username` luôn có |
| `ChatUserInfo` | — | `+ requestId?: string` (khi `statusFriend` là SENT/RECEIVED) |
| Friend request | `ChatSentFriendRequest{toUser}`, `ChatReceivedFriendRequest{fromUser}` | một `ChatFriendRequest{id, user, message, createdAt}` — `user` = người còn lại |
| `ChatFriendRequestUser` / accept response | không có `username` | `UserSummaryDto{id, username, firstName, lastName, avatarUrl}` |
| Gửi tin | `ChatSendDirectMessageParams{recipientId, content, type, attachmentUrl}` | giữ shape; `content` được rỗng khi có `attachmentUrl`; `type` phải `IMAGE`/`FILE` khi có attachment, `TEXT` khi không |

## 3. Đổi route
| Cũ | Mới |
|---|---|
| `GET /v1/user/info?userId=` | `GET /v1/user/{userId}` |
| `POST /v1/friend/accept {requestId}` | `POST /v1/friend/request/{requestId}/accept` |
| `POST /v1/friend/decline {requestId}` | `POST /v1/friend/request/{requestId}/decline` |
| `POST /v1/friend/cancel {requestId}` | `DELETE /v1/friend/request/{requestId}` |
| `PATCH /v1/conversation/{id}/group` | `PATCH /v1/conversation/{id}` |
| `DELETE /v1/conversation/{id}/group` | `DELETE /v1/conversation/{id}` |

## 4. Route mới
- `GET /v1/conversation/{id}` → `ChatConversationRecord` (dùng cho deep-link/reload thay vì `conversations.find`).
- `PATCH /v1/user/me/password {currentPassword, newPassword(8-72)}` → 204.
- `POST /v1/upload` multipart `file` (≤10MB) → 201 `{url, name, size, contentType}`; 413 nếu quá. Flow: upload → gửi message với `attachmentUrl=url`, `type = contentType.startsWith("image/") ? "IMAGE" : "FILE"`.
- `PATCH /v1/message/{id} {content}` → 200 `MessageDto` (chỉ tin TEXT của mình).
- `DELETE /v1/message/{id}` → 204 (chỉ tin của mình).

## 5. Socket
| Destination | Payload | FE làm gì |
|---|---|---|
| `/user/queue/conversations` | `{eventType:"conversation.updated", conversation: ChatConversationRecord}` | **upsert** vào list theo `conversation.id` (thay `applyConversationUpdateToCache`); nếu `conversation.lastMessage` mới → append vào message cache |
| `/user/queue/conversations` | `{eventType:"conversation.removed", conversationId}` | xóa khỏi list; nếu đang mở → navigate về Home |
| `/user/queue/conversations` | `{eventType:"conversation.seen", conversationId, seenByUserId, lastReadMessageId, lastReadAt}` | như hiện tại (`applyConversationSeenToCache`) — giờ nhận được từ **người khác** |
| `/topic/conversations/{id}/messages` | `{eventType:"message.created"\|"message.updated"\|"message.deleted", message: MessageDto}` | created/updated → upsert theo `message.id` (bỏ early-return `alreadyPresent`); deleted → remove |
| `/topic/conversations/{id}/typing` | `{eventType:"typing", conversationId, userId}` | hiện "đang gõ" cho `userId`, tự tắt sau 3s không có event mới |
| `SEND /app/conversations/{id}/typing` (không body) | — | gửi throttle ~2s khi người dùng gõ |

Bỏ: `group.deleted`, `/topic/conversations/{id}/seen`.

## 6. Checklist file FE
- `packages/types/src/chat-*.ts`: cập nhật theo §2, §4, §5.
- `packages/api/src/chat/*-service.ts`: `items`, route §3/§4, method mới.
- `apps/chat/src/libs/socket.ts`: type guard cho 5 payload §5 + `subscribeToTyping`, `sendTyping`.
- `apps/chat/src/hooks/api/conversation.ts`: `applyConversationUpdateToCache` → upsert; thêm `applyConversationRemovedToCache`.
- `apps/chat/src/hooks/api/message.ts`: `appendConversationMessageToCache` → upsert + `removeConversationMessageFromCache`.
- `apps/chat/src/hooks/use-open-direct-conversation.ts`: giữ; `conversation-panel.tsx` dùng `GET /conversation/{id}` khi list chưa có.
```

- [ ] **Step 2: Đối chiếu doc với code**

Mở từng DTO/controller đã sửa, so với bảng — sửa doc cho khớp (tên field, status code).

- [ ] **Step 3: Commit**

```bash
git add docs/api-contract-changes.md
git commit -m "docs: API contract changes for the chat frontend

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Self-review (đã chạy)

- **Spec coverage:** §1 → T1-3; §2 DTO → T4-8; §3 socket → T9-11; §4 route → T5, T6, T7, T12-16; §5 upload → T14; §7 doc → T18. Typing (§3 bảng) → T17. Không có mục spec nào thiếu task.
- **Placeholder:** T11 Step 1 để implementer copy setup từ test hiện có (file đã có pattern dựng `StompHeaderAccessor`); các chỗ khác có code đầy đủ.
- **Type consistency:** `findWithDetails(UUID) → Optional` (T10) dùng ở T12, T15, T16; `publishConversationUpdatedAfterCommit(ConversationEntity)` (T9) dùng ở T10, T15, T16; `UserSummaryDto` 5 field (T5) khớp doc T18; `ConversationDto` 7 field (T6) khớp T9 test + T12 test.
- **Review Focus:** 5 dòng đều có test được đặt tên trong task tương ứng (T3, T7, T10, T16, T14).
