# API contract changes — hướng dẫn cho FE (`monorepo/apps/chat`)

Ngày: 2026-09-20. Backend: `chat-socket` branch `refactor/cleanup`. Mọi mục đều **breaking**.

## 1. Thời gian

- Mọi timestamp là UTC, format cố định `2026-09-20T10:00:00.123456Z` (6 chữ số). `dayjs.utc(value)` vẫn đọc được.
- Cursor (`nextCursor`) truyền lại nguyên chuỗi. Cursor tự tạo phải có `Z`/offset, không gửi local time — thiếu zone sẽ nhận 400 `"Cursor is invalid."`.
- Response fixed-width nên so chuỗi vẫn đúng thứ tự; khuyến nghị đổi sang `dayjs(a).isSameOrAfter(b)` cho rõ ràng hơn là dựa vào so chuỗi.

## 2. Đổi tên field / DTO

| Chỗ | Cũ | Mới |
|---|---|---|
| Mọi list (`/user`, `/friend`, `/conversation`, `/conversation/{id}/messages`) | `data.messages` | `data.items` |
| `ChatConversationRecord` | có `createdById, directUserAId, directUserBId, lastMessageId, createdAt, updatedAt` | bỏ; còn `id, type, groupName, lastMessage, lastMessageAt, unreadCount, participants` |
| `ChatConversationParticipant` | `username?` | `username` luôn có (bắt buộc) |
| `ChatUserInfo` | — | `+ requestId?: string` (khi `statusFriend` là `SENT`/`RECEIVED`, `null` khi không có) |
| Friend request | `ChatSentFriendRequest{toUser}`, `ChatReceivedFriendRequest{fromUser}` | một `ChatFriendRequest{id, user, message, createdAt}` — `user` = người còn lại (recipient trên sent, sender trên received) |
| `ChatFriendRequestUser` / accept response | không có `username` | `UserSummaryDto{id, username, firstName, lastName, avatarUrl}` |
| Gửi tin trực tiếp | `ChatSendDirectMessageParams{recipientId, content, attachmentUrl, conversationId, type}` | `DirectMessageRequest{recipientId, content, type, attachmentUrl}` — bỏ hẳn nhánh `conversationId` |
| Gửi tin nhóm | cùng `MessageRequest` | `GroupMessageRequest{conversationId, content, type, attachmentUrl}` |

Validation gửi tin (áp dụng cho cả direct/group, service trả 400):
- `content` rỗng và không có `attachmentUrl` → `"Content or attachment is required."`
- có `attachmentUrl` mà `type` không phải `IMAGE`/`FILE` → `"Type must be IMAGE or FILE when attaching a file."`
- không có `attachmentUrl` mà `type` không phải `null`/`TEXT` → `"Type must be TEXT without an attachment."`

## 3. Đổi route

| Cũ | Mới | Ghi chú |
|---|---|---|
| `GET /v1/user/info?userId=` | `GET /v1/user/{userId}` | `/me` vẫn ưu tiên vì là literal path |
| `POST /v1/friend/accept {requestId}` | `POST /v1/friend/request/{requestId}/accept` | trả `UserSummaryDto`, 201 |
| `POST /v1/friend/decline {requestId}` | `POST /v1/friend/request/{requestId}/decline` | 204 |
| `POST /v1/friend/cancel {requestId}` | `DELETE /v1/friend/request/{requestId}` | 204 |
| `PATCH /v1/conversation/{id}/group` | `PATCH /v1/conversation/{id}` | body giữ nguyên |
| `DELETE /v1/conversation/{id}/group` | `DELETE /v1/conversation/{id}` | |

## 4. Route mới

- `GET /v1/conversation/{id}` → `ConversationDto` (unread của người gọi). 404 nếu không tồn tại, 403 nếu không phải active participant. Dùng cho deep-link/reload thay vì tìm trong `conversations` list đã cache.
- `PATCH /v1/user/me/password {currentPassword, newPassword}` (`newPassword` 8-72 ký tự) → 204. 400 nếu sai mật khẩu hiện tại hoặc mật khẩu mới trùng mật khẩu cũ.
- `POST /v1/upload` multipart field `file` (≤10MB, 413 nếu quá) → 201 `{url, name, size, contentType}`. Flow: upload trước → gửi message với `attachmentUrl = url`, `type = contentType.startsWith("image/") ? "IMAGE" : "FILE"`.
- `GET /api/files/{name}` — serve file tĩnh, public, không cần token.
- `PATCH /v1/message/{id} {content}` → 200 `MessageDto` (chỉ sender, chỉ tin `TEXT`, chưa bị xóa). 403 nếu không phải người gửi, 400 `"Only text messages can be edited."` nếu không phải TEXT.
- `DELETE /v1/message/{id}` → 204 (chỉ sender). Soft delete; nếu là `lastMessage` của conversation, server tự trỏ về tin chưa xóa gần nhất (hoặc `null` nếu không còn tin nào).

## 5. Socket

| Destination | Payload | FE làm gì |
|---|---|---|
| `SUBSCRIBE /app/online-users` (1 lần) · nhận trên `/topic/online-users` | `string[]` | không đổi |
| `/user/queue/conversations` | `{eventType:"conversation.updated", conversation: ChatConversationRecord}` | **upsert** vào list theo `conversation.id` (thay `applyConversationUpdateToCache`); nếu `conversation.lastMessage` mới → append vào message cache |
| `/user/queue/conversations` | `{eventType:"conversation.removed", conversationId}` | xóa khỏi list; nếu đang mở → navigate về Home |
| `/user/queue/conversations` | `{eventType:"conversation.seen", conversationId, seenByUserId, lastReadMessageId, lastReadAt}` | như hiện tại (`applyConversationSeenToCache`) — giờ nhận được từ **mọi** active participant kể cả chính người seen |
| `/topic/conversations/{id}/messages` | `{eventType:"message.created"\|"message.updated"\|"message.deleted", message: MessageDto}` | created/updated → upsert theo `message.id` (bỏ early-return `alreadyPresent`); deleted → remove khỏi cache |
| `/topic/conversations/{id}/typing` | `{eventType:"typing", conversationId, userId}` | hiện "đang gõ" cho `userId`, tự tắt sau ~3s không có event mới |
| `SEND /app/conversations/{id}/typing` (không body) | — | gửi throttle ~2s khi người dùng gõ |

Bỏ: `group.deleted` (dùng `conversation.removed` cho mọi trường hợp bị loại khỏi group — kick, tự rời, xóa nhóm), `/topic/conversations/{id}/seen`.

SUBSCRIBE bất kỳ topic `/topic/conversations/{id}/...` nào (kể cả `/typing`) đều cần là active participant của conversation đó, không riêng `/messages`.

## 5b. JSON mẫu

```jsonc
// /user/queue/conversations
{ "eventType": "conversation.updated",
  "conversation": { "id": "…", "type": "GROUP", "groupName": "Team", "lastMessage": { /* MessageDto */ },
                    "lastMessageAt": "2026-09-20T10:00:00.123456Z", "unreadCount": 2,
                    "participants": [ { "userId": "…", "username": "alice", "firstName": "A", "lastName": "L",
                                        "avatarUrl": null, "role": "ADMIN", "joinedAt": "…",
                                        "lastReadMessageId": "…", "lastReadAt": "…" } ] } }
{ "eventType": "conversation.removed", "conversationId": "…" }
{ "eventType": "conversation.seen", "conversationId": "…", "seenByUserId": "…", "lastReadMessageId": "…", "lastReadAt": "…" }

// /topic/conversations/{id}/messages
{ "eventType": "message.created", "message": { "id": "…", "conversationId": "…", "senderId": "…", "content": "hi",
                                              "attachmentUrl": null, "type": "TEXT", "createdAt": "…", "updatedAt": "…" } }

// /topic/conversations/{id}/typing
{ "eventType": "typing", "conversationId": "…", "userId": "…" }

// POST /v1/upload → 201
{ "data": { "url": "http://localhost:8089/api/files/0199….png", "name": "0199….png", "size": 12345, "contentType": "image/png" },
  "message": "File uploaded successfully.", "status": 201 }
```

## 5c. Sửa thêm sau review cuối (commit `1da0b1a`)

- `participants` (trong `GET /conversation*` và `conversation.updated`) **chỉ còn active member** — người đã rời/bị kick/đã xóa không xuất hiện nữa. FE bỏ mọi filter `leftAt` phía client nếu có; `readersOf` không cần lo member cũ.
- `GET /api/files/{name}` trả `Content-Disposition: attachment` + `X-Content-Type-Options: nosniff`. `<img src>`/`<video src>` vẫn hiển thị bình thường; **mở URL trực tiếp (new tab / `<a href>` không `download`) sẽ tải file về chứ không render**. Preview ảnh: dùng `<img>`; file khác: link tải.

## 6. Checklist file FE

- `packages/types/src/chat-*.ts`: cập nhật theo §2, §4, §5.
- `packages/api/src/chat/*-service.ts`: `items`, route §3/§4, method mới (`changePassword`, `getConversation`, `upload`, `updateMessage`, `deleteMessage`).
- `apps/chat/src/libs/socket.ts`: type guard cho 6 payload §5 (bao gồm `conversation.seen` không đổi shape) + `subscribeToTyping`, `sendTyping`.
- `apps/chat/src/hooks/api/conversation.ts`: `applyConversationUpdateToCache` → upsert (không còn patch từng field); thêm `applyConversationRemovedToCache`.
- `apps/chat/src/hooks/api/message.ts`: `appendConversationMessageToCache` → upsert + thêm `removeConversationMessageFromCache`.
- `apps/chat/src/hooks/use-open-direct-conversation.ts`: giữ; `conversation-panel.tsx` dùng `GET /conversation/{id}` khi list chưa có (deep-link).
