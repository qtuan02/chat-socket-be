# Refactor cleanup — design

Ngày: 2026-09-20. Branch: `refactor/cleanup` từ `dev`.

## Mục tiêu

Refactor thuần (không đổi URL / JSON shape / status code), theo tiêu chí:

- Dễ đọc; không gom nhiều việc vào một "helper thần thánh".
- Helper chỉ khi: làm 1 việc, có ≥ 2 chỗ dùng, là tiện ích thuần (normalize / check / format).
- Đặt đúng chỗ: entity method cạnh dữ liệu, repository default method cạnh query, constant trong `constant/*`.
- Test đầy đủ ở mức unit + controller, không cần Docker, không thêm dependency.
- Ponytail: xoá > thêm, stdlib trước, không abstraction thừa.

## Quyết định đã chốt

| Câu hỏi | Chọn |
|---|---|
| Khung kiến trúc | **Giữ** `BaseResponse<T>` từ service, giữ `service/X` + `service/impl/XImpl`. |
| Phạm vi test | Unit (Mockito) + Controller (`@WebMvcTest`). Không repository test. |
| `RedisUtils` | **Xoá**, dùng thẳng `StringRedisTemplate`. |
| `constant/*` là interface | **Giữ nguyên**. |
| Bug phát hiện tiện tay | **Sửa luôn**, liệt kê trong báo cáo. |
| 4 file config đang sửa chưa commit | **Không đụng**. |

## 1. Helper mới

| Helper | Đặt ở | Thay cho | Chỗ dùng |
|---|---|---|---|
| `ParticipantEntity.isActive()` → `leftAt == null && deletedAt == null` | entity | `leftAt != null \|\| deletedAt != null` | 6 |
| `FriendEntity.otherUser(UUID me)` | entity | `getFriendUser()` ở `FriendServiceImpl`, `UserServiceImpl` | 2 |
| `UserPair.of(a, b)` static factory | dto | `Normalize.normalizeUserPair` | 4 |
| `FriendRepository.existsFriendship(a, b)` default method | repository | `UserPair` + `existsByUserAIdAndUserBId` | 4 |
| `ParticipantRepository.findActiveParticipant(convId, userId)` default → `Optional` | repository | `findByIdConversationIdAndIdUserId` + check active | 3 |
| `JwtService.verifyAccessToken` trả `Optional<UUID>` (nuốt `JwtException`/`IllegalArgumentException`) | service | `Security.getUserIdFromAccessToken(jwtService, token)` | 2 |
| `Security.extractBearerToken(String header)` → token hoặc `null` | utils | header→token ở `SecurityFilter`, `SocketChannelInterceptor` | 2 |
| `SocketChannel.ONLINE_USERS = "/online-users"` | constant | string hardcode ở `SocketEventListener`, `SocketController` | 2 |
| `AuthServiceImpl.refreshTokenCookie(String value, Duration maxAge)` private | service | `addRefreshTokenCookie` + `clearRefreshTokenCookie` builder trùng | 2 |
| `ConversationServiceImpl.publishConversationUpdated(ConversationEntity)` private | service | 4 khối `lastMessage == null ? null : toDto` + `publishConversationUpdatedAfterCommit` | 4 |

Không tạo dù lặp:

- `findById(id).orElseThrow(() -> new NotFoundException("..."))` ×8 — message khác nhau, 1 dòng đã rõ.
- `ConversationEntity.isGroup()` — `type != ConversationType.GROUP` đủ đọc.

## 2. Xoá / dọn / đặt lại chỗ

- Xoá `utils/RedisUtils`. `UserOnlineRegistry` inject `StringRedisTemplate`. Lua script chỉ ở `constant/Redis`.
- Xoá `MessageDirectPermission.canSendDirect(UUID)` (luôn `true`, không ai gọi).
- Xoá try/catch vô nghĩa trong `Security.getUserSecurityFromPrincipal`.
- Xoá `ConversationServiceImpl.toResponseDto()` ×2 (wrapper 1 dòng) → gọi `conversationMapper.toDto` trực tiếp.
- `MessageServiceImpl.sendDirectMessage`: `messageMapper.toDto(message)` gọi 1 lần.
- Gộp: `MessageServiceImpl.findOrCreateDirectConversation / createDirectConversation / createParticipant` → gọi `ConversationService.findOrCreateDirectConversation(UUID senderId, UUID recipientId)` (method mới trên interface, impl tái dùng code sẵn có trong `ConversationServiceImpl`). Không vòng phụ thuộc: `ConversationService` không dùng `MessageService`. Hệ quả message text: `POST /v1/message/direct` với `recipientId` không tồn tại trả `"User not found."` thay vì `"Recipient not found."` (vẫn 404) — chấp nhận, cùng dạng đổi text với `"Member not found."` ở `createConversation`.
- Thống nhất 400: mọi `return new BaseResponse<>(null, msg, HttpStatus.BAD_REQUEST.value())` → `throw new BadRequestException(msg)`. Handler đã có; client nhận y hệt. `CONFLICT`/`FORBIDDEN`/`NOT_FOUND` vẫn `return` vì chưa có exception tương ứng — không thêm.
- `Normalize` chỉ còn text (`normalizeFullName`, `normalizeSearchText`, `normalizeTextPattern`, `normalizeUsernamePattern`). Tên giữ.
- `PaginationUtils.resolveCursorPage`: bỏ biến `page` khai báo trước, return trực tiếp trong `try`.
- Fix bug `UserOnlineRegistry.clearOnlineUsers`: `delete(prefix + "*")` là no-op (Redis `DEL` không glob). Sửa: `keys(prefix + "*")` → `delete(keys)`; `delete(ONLINE_USERS_KEY)`. Kèm `// ponytail: KEYS là O(N), chỉ chạy 1 lần lúc startup; đổi SCAN nếu số key lớn.`

## 3. Test

Thứ tự: **characterization test trên code hiện tại → refactor → test vẫn xanh → thêm test cho helper mới.**

| Nhóm | File (`src/test/java/com/chat_socket/...`) | Kiểu |
|---|---|---|
| Utils | `utils/NormalizeTest`, `utils/PaginationUtilsTest`, `dto/UserPairTest`, `utils/SecurityTest` | JUnit thuần |
| Service | `service/impl/{Auth,Conversation,Friend,Message,User}ServiceImplTest`, `service/impl/JwtServiceImplTest` | Mockito; Jwt test thật với secret cố định |
| Security | `security/{SecurityFilter,SocketChannelInterceptor,GroupPermission,MessageDirectPermission,MessageGroupPermission}Test` | Mockito |
| Socket | `socket/SocketPublisherTest`, `socket/UserOnlineRegistryTest` | Mockito |
| Controller | `controller/{Auth,Conversation,Friend,Message,User}ControllerTest` | `@WebMvcTest` + MockMvc; service `@MockitoBean`; auth context set qua `SecurityContextHolder` hoặc `RequestPostProcessor` với `UserSecurity` principal |

Controller test kiểm: URL → service method đúng, status trả về lấy từ `BaseResponse.status()`, `@PreAuthorize` bean được gọi.

Không test: MapStruct mapper (code sinh); entity one-liner (`isActive`, `otherUser`) — phủ qua service test. `ChatSocketApplicationTests.contextLoads` giữ nguyên (cần Postgres + Redis).

## 4. Commit

1. `test: characterization tests for services, utils, security, socket`
2. `refactor: entity/repository helpers and dedupe services`
3. `refactor: security/socket cleanup, remove RedisUtils` + `fix: clearOnlineUsers deletes nothing (glob DEL)`
4. `test: controller tests and tests for new helpers`

Mỗi commit: `mvn -q test -Dtest='!ChatSocketApplicationTests'` xanh.

## Ngoài phạm vi

- Bỏ `service/impl` interface, đưa HTTP status ra controller (option B/C) — làm sau khi có test.
- Repository test với Testcontainers.
- Chuyển `constant/*` sang `final class`.
- `PaginationResponse.messages` tên sai nghĩa (là items) — đổi sẽ phá API contract.
