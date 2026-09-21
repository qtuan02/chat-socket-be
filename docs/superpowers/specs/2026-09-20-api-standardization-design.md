# API standardization — design

Ngày: 2026-09-20. Branch: `refactor/cleanup`. Consumer: `D:\Personal\monorepo\apps\chat` (types ở `packages/types/src/chat-*.ts`, service ở `packages/api/src/chat/*.ts`).

## Mục tiêu

Chuẩn hóa contract REST + STOMP của `chat-socket` để FE không phải "đoán" (đổi `messages`→`items`, bỏ field dư, bổ sung field thiếu), sửa 4 bug thật (giờ lệch múi, seen không real-time, bị kick không biết, conversation mới không hiện), và thêm 6 API còn thiếu cho một app chat. Mọi thay đổi là **breaking** với FE — FE cập nhật theo `docs/api-contract-changes.md` (Phase 9).

## Quyết định đã chốt

| Câu hỏi | Chọn |
|---|---|
| Kiểu thời gian | `java.time.Instant` toàn bộ (entity, DTO, repo, service). Cột DB `TIMESTAMPTZ`. |
| Data cũ trong DB | Dev data — migrate `USING col AT TIME ZONE 'UTC'` (chấp nhận lệch 7h với data cũ). |
| Format JSON | Cố định 6 chữ số micro: `2026-09-20T10:00:00.123456Z` (khớp precision Postgres, fixed-width để FE so chuỗi vẫn đúng). |
| Tên list | `PaginationResponse.items` (thay `messages`). |
| Route đổi tên | Có (bảng §4). |
| Upload | Đĩa local (`chat-socket.upload-dir`), serve qua `/api/files/**` public. `ponytail:` chuyển S3 khi chạy nhiều instance. |
| Socket | Một event `conversation.updated` mang **full `ConversationDto`** (FE upsert) thay cho patch từng field; `conversation.seen` gửi vào queue mọi participant; thêm `conversation.removed`. Message topic bọc `{eventType, message}`. |
| Xóa message | Soft delete (`is_deleted`), list bỏ qua như hiện tại; socket báo `message.deleted` để FE xóa khỏi cache. |
| Typing | Client `SEND /app/conversations/{id}/typing` (không body) → broadcast `/topic/conversations/{id}/typing`. Không có "stop typing" — FE tự hết hạn sau ~3s. |

## 1. Thời gian (UTC)

Hiện trạng: `LocalDateTime` + `TIMESTAMP` không zone + `LocalDateTime.now()`/`@CreationTimestamp` lấy giờ JVM (Windows dev = +07) → FE (`dayjs.utc`) hiển thị lệch 7h.

- Entity/DTO/repo/service/`PaginationUtils`/`SocketPublisher`/`TestFixtures`: `LocalDateTime` → `Instant`; `LocalDateTime.now()` → `Instant.now()`.
- Migration `V8__utc_timestamps.sql`: mọi cột `TIMESTAMP` → `TIMESTAMPTZ USING col AT TIME ZONE 'UTC'` (users, friend_requests, friends, conversations, messages, participants). `sessions.expires_at` đã là `TIMESTAMPTZ`.
- `constant/TimeFormat.UTC_MICROS` = `DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(UTC)`.
- `config/InstantJsonSerializer` (`@JacksonComponent`, Jackson 3 `ValueSerializer<Instant>`) → dùng cho cả REST và STOMP (Spring Boot 4 đưa cùng `JsonMapper` vào broker converter).
- Cursor: parse `Instant.parse(cursor)` (nhận `Z` hoặc offset; thiếu zone → 400 "Cursor is invalid."), format bằng `UTC_MICROS`.
- Giữ `hibernate.jdbc.time_zone: UTC`.

Edge được chấp nhận: response ngay sau insert dùng giá trị in-memory (nano, cắt còn 6 số) còn DB làm tròn micro → có thể lệch 1µs giữa POST response và GET sau đó. FE dedupe theo `id`, vô hại.

## 2. DTO cuối cùng

```java
record PaginationResponse<T>(List<T> items, String nextCursor, Integer nextOffset)   // NON_NULL
record UserSummaryDto(UUID id, String username, String firstName, String lastName, String avatarUrl)
        // thay UserDto + AcceptFriendResponse
record FriendDto(UUID id, String username, String firstName, String lastName, String avatarUrl, Instant joinedAt)
record FriendRequestDto(UUID id, UserSummaryDto user, String message, Instant createdAt)
        // thay FriendRequestSentDto + FriendRequestReceviedDto; `user` = người còn lại
record FriendRequestResponse(List<FriendRequestDto> sentRequests, List<FriendRequestDto> receivedRequests)
record ConversationParticipantDto(UUID userId, String username, String firstName, String lastName, String avatarUrl,
        ParticipantRole role, Instant joinedAt, UUID lastReadMessageId, Instant lastReadAt)   // +username
record ConversationDto(UUID id, ConversationType type, String groupName, MessageDto lastMessage,
        Instant lastMessageAt, long unreadCount, List<ConversationParticipantDto> participants)
        // bỏ createdById, directUserAId, directUserBId, lastMessageId, createdAt, updatedAt
record MessageDto(UUID id, UUID conversationId, UUID senderId, String content, String attachmentUrl,
        MessageType type, Instant createdAt, Instant updatedAt)   // không đổi ngoài kiểu thời gian
record UserInfoDto(UUID id, String username, String firstName, String lastName, String email, String avatarUrl,
        String bio, String phone, Instant joinedAt, FriendStatus statusFriend, UUID requestId)   // +requestId
record UserSearchDto(... Instant joinedAt, FriendStatus statusFriend, UUID requestId)   // không đổi
record UserProfileDto(...)   // không đổi
record DirectMessageRequest(@NotNull UUID recipientId, String content, MessageType type, String attachmentUrl)
record GroupMessageRequest(@NotNull UUID conversationId, String content, MessageType type, String attachmentUrl)
        // thay MessageRequest. Validation ở service:
        //   content trống && attachmentUrl null      → 400 "Content or attachment is required."
        //   attachmentUrl có && type ∉ {IMAGE, FILE}   → 400 "Type must be IMAGE or FILE when attaching a file."
        //   attachmentUrl null && type ∉ {null, TEXT}  → 400 "Type must be TEXT without an attachment."
record UpdateMessageRequest(@NotBlank String content)
record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword)
record UploadResponse(String url, String name, long size, String contentType)
```

Xóa: `UserDto`, `AcceptFriendResponse`, `FriendRequestSentDto`, `FriendRequestReceviedDto`, `FriendActionRequest`, `MessageRequest`, `ConversationEvent`, `ConversationDelivery`, `FriendMapper` (gộp `toFriendDto` vào `UserMapper`).

Cột chết: `participants.archived_at`, `participants.muted_until` — xóa (migration `V9`) cùng field entity. `deleted_at` **giữ** (dùng bởi `deleteGroup` / restore).

## 3. Socket contract cuối cùng

| Destination | Payload | Khi nào |
|---|---|---|
| `SUBSCRIBE /app/online-users` (1 lần) · `/topic/online-users` | `string[]` | không đổi |
| `/user/queue/conversations` | `{eventType:"conversation.updated", conversation: ConversationDto}` (`unreadCount` của **người nhận**) | tin mới, đổi tên nhóm, thêm/bớt thành viên, tạo nhóm, tạo direct qua `POST /conversation`, người "đã xóa" conversation được restore khi có tin mới. FE **upsert**. |
| `/user/queue/conversations` | `{eventType:"conversation.removed", conversationId}` | bị kick, tự rời, admin xóa nhóm (cho chính người bị ảnh hưởng). FE xóa khỏi list. |
| `/user/queue/conversations` | `{eventType:"conversation.seen", conversationId, seenByUserId, lastReadMessageId, lastReadAt}` | gửi tới **mọi** active participant (kể cả người seen). |
| `/topic/conversations/{id}/messages` | `{eventType:"message.created"\|"message.updated"\|"message.deleted", message: MessageDto}` | FE upsert theo `message.id`; `deleted` → xóa khỏi cache. |
| `/topic/conversations/{id}/typing` | `{eventType:"typing", conversationId, userId}` | mỗi lần client `SEND /app/conversations/{id}/typing`. |

Bỏ: `/topic/conversations/{id}/seen` (không ai subscribe), `group.deleted`.

`SocketChannelInterceptor`: SUBSCRIBE bất kỳ `/topic/conversations/{id}/…` đều cần là active participant (hiện chỉ chặn `/messages`).

`SocketPublisher` API:

```java
void publishMessageCreatedAfterCommit(ConversationEntity conversation, MessageDto message)   // topic + conversation.updated per user
void publishMessageUpdatedAfterCommit(UUID conversationId, MessageDto message)
void publishMessageDeletedAfterCommit(UUID conversationId, MessageDto message)
void publishConversationUpdatedAfterCommit(ConversationEntity conversation)                   // per active user, unread riêng
void publishConversationRemovedAfterCommit(UUID conversationId, Collection<UUID> userIds)
void publishConversationSeenAfterCommit(UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, Instant seenAt)
```

`conversation` truyền vào phải đã load details (participants + user, lastMessage) — dùng `ConversationRepository.findWithDetails(UUID)` (default method mới bọc `findConversationsWithDetails`). DTO được build **trước** commit (trong tx), emit **sau** commit (giữ pattern `SocketSynchronization`).

## 4. Route

| Cũ | Mới | Ghi chú |
|---|---|---|
| `GET /v1/user/info?userId=` | `GET /v1/user/{userId}` | `/me` literal vẫn ưu tiên |
| — | `PATCH /v1/user/me/password` | body `ChangePasswordRequest`, 204 |
| `POST /v1/friend/accept {requestId}` | `POST /v1/friend/request/{requestId}/accept` | trả `UserSummaryDto`, 201 |
| `POST /v1/friend/decline {requestId}` | `POST /v1/friend/request/{requestId}/decline` | 204 |
| `POST /v1/friend/cancel {requestId}` | `DELETE /v1/friend/request/{requestId}` | 204 |
| `PATCH /v1/conversation/{id}/group` | `PATCH /v1/conversation/{id}` | `@groupPermission.canManageGroup` giữ |
| `DELETE /v1/conversation/{id}/group` | `DELETE /v1/conversation/{id}` | |
| — | `GET /v1/conversation/{id}` | `ConversationDto` (unread của mình); 404 không tồn tại, 403 không phải active participant |
| `POST /v1/message/direct` | giữ, body `DirectMessageRequest` | luôn `findOrCreateDirectConversation(recipientId)`; bỏ nhánh `conversationId` |
| `POST /v1/message/group` | giữ, body `GroupMessageRequest` | |
| — | `PATCH /v1/message/{messageId}` | `UpdateMessageRequest`; chỉ sender, chỉ `TEXT`, chưa xóa; 200 `MessageDto` |
| — | `DELETE /v1/message/{messageId}` | chỉ sender; soft delete; nếu là `lastMessage` của conversation → trỏ về tin chưa xóa gần nhất; 204 |
| — | `POST /v1/upload` (multipart `file`) | 201 `UploadResponse`; max 10MB (413 nếu quá) |
| — | `GET /api/files/{name}` | static, public, không qua `SecurityFilter` |

Không đổi: auth, `GET/PATCH /user/me`, `GET /user?search`, `GET /friend`, `GET/POST /friend/request`, `DELETE /friend/{id}`, `GET /conversation`, `POST /conversation`, `GET /conversation/{id}/messages`, `PATCH /conversation/{id}/seen`, `POST /conversation/{id}/members`, `DELETE /conversation/{id}/members/{memberId}`, `POST /conversation/{id}/leave`, `/health-check`.

## 5. Upload

- Config `chat-socket.upload-dir` (default `./uploads`), `chat-socket.public-url` (default `http://localhost:8089/api` — **gồm** servlet path), `spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 10MB`.
- `FileStorage.store(MultipartFile)`: từ chối file rỗng (400); tên file = UUIDv7 + `.` + ext (ext lấy từ tên gốc, chỉ giữ `[A-Za-z0-9]{1,10}`, không có thì bỏ); không bao giờ dùng tên gốc làm path. `url = publicUrl + "/files/" + name`.
- `WebMvcConfigurer.addResourceHandlers("/files/**" → "file:<upload-dir>/")`; `SecurityFilter.shouldNotFilter` + `permitAll("/files/**")`.
- `MaxUploadSizeExceededException` → 413 `BaseResponse(null, "File is too large (max 10MB).", 413)`.
- FE: upload trước, rồi gửi message với `attachmentUrl` + `type` = `IMAGE` nếu `contentType` bắt đầu `image/`, ngược lại `FILE`.

## 6. Ngoài phạm vi

Mutual friends, last-seen, forgot-password, group avatar, mute/archive, `GET /conversation/direct/{userId}` (FE draft + `POST /message/direct` đã đủ), unread-count tổng (FE đếm từ list), hard delete message, edit message có attachment.

## 7. Tài liệu cho FE

`docs/api-contract-changes.md` (Phase 9): bảng đổi route/DTO, shape JSON mẫu mỗi event, hướng dẫn: parse/so sánh thời gian bằng `dayjs` (không so chuỗi nếu format đổi), upsert `conversation.updated`, xử lý `conversation.removed`, `message.*`, typing timeout, flow upload.
