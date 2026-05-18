# Chat Socket Backend Documentation

Tài liệu này mô tả backend `chat-socket` dựa trên source code, file cấu hình, Flyway migration SQL và dependency hiện có trong repository. Những thông tin chưa thể xác định từ source hiện tại được ghi rõ là `Chưa xác định trong source hiện tại`.

## 1. Tổng quan dự án

`chat-socket` là backend Java Spring Boot cho ứng dụng chat realtime. Dự án cung cấp REST API cho authentication, user profile, friend management, conversation, message và realtime event qua WebSocket/STOMP.

Các chức năng chính đang có trong source:

| Nhóm chức năng  | Mô tả                                                                                                  | File/path liên quan                                                                                                                 |
| --------------- | ------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| Đăng ký         | Tạo user mới, hash password bằng BCrypt, lưu vào bảng `users`.                                         | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`, `src/main/java/com/chat_socket/entity/UserEntity.java`           |
| Đăng nhập       | Kiểm tra username/password, phát access token JWT, tạo refresh token và lưu session.                   | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`, `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java` |
| Refresh token   | Đọc refresh token từ cookie `refreshToken`, kiểm tra bảng `sessions`, phát access token mới.           | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                                   |
| Đăng xuất       | Xóa session theo refresh token và clear cookie.                                                        | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                                   |
| Quản lý profile | Lấy và cập nhật thông tin user hiện tại, lấy thông tin user khác nếu là bạn bè.                        | `src/main/java/com/chat_socket/service/impl/UserServiceImpl.java`                                                                   |
| Kết bạn         | Gửi, nhận, chấp nhận, từ chối, hủy lời mời kết bạn và xóa bạn bè.                                      | `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`                                                                 |
| Conversation    | Tạo conversation trực tiếp hoặc group, lấy danh sách conversation, lấy message theo cursor, mark seen. | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                           |
| Message         | Gửi direct message hoặc group message.                                                                 | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`                                                                |
| Realtime        | Online users, message events, conversation update events, seen events qua STOMP.                       | `src/main/java/com/chat_socket/socket/*`, `src/main/java/com/chat_socket/config/WebSocketConfig.java`                               |

Base REST API khi chạy với cấu hình hiện tại là `/api/v1`, vì `spring.mvc.servlet.path=/api` trong `src/main/resources/application-template.yaml` và route version `/v1` trong `src/main/java/com/chat_socket/constant/RouteApi.java`.

## 2. Tech stack và dependency chính

| Công nghệ/thư viện              | Vai trò trong dự án                                                               | File hoặc package liên quan                                                                                                             |
| ------------------------------- | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Java 25                         | Runtime và source compatibility theo Maven property.                              | `pom.xml`                                                                                                                               |
| Spring Boot 4.0.6               | Framework chính của backend.                                                      | `pom.xml`, `src/main/java/com/chat_socket/ChatSocketApplication.java`                                                                   |
| Spring Web MVC                  | REST API controller và request handling.                                          | `pom.xml`, `src/main/java/com/chat_socket/controller/*`                                                                                 |
| Spring Security                 | HTTP authentication, authorization, method-level security.                        | `pom.xml`, `src/main/java/com/chat_socket/config/SecurityServerConfig.java`, `src/main/java/com/chat_socket/security/*`                 |
| JWT `jjwt`                      | Sinh và verify access token JWT, sinh refresh token dạng random hex.              | `pom.xml`, `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`                                                             |
| Spring Data JPA/Hibernate       | Entity mapping, repository, transaction, persistence.                             | `pom.xml`, `src/main/java/com/chat_socket/entity/*`, `src/main/java/com/chat_socket/repository/*`                                       |
| PostgreSQL                      | Database chính cho user, friendship, conversation, message, participant, session. | `pom.xml`, `deployment/docker-compose/infra.yml`, `src/main/resources/db/migration/*`                                                   |
| Flyway                          | Database migration.                                                               | `pom.xml`, `src/main/resources/db/migration/V1__users_table.sql` đến `V7__sessions_table.sql`                                           |
| Redis                           | Lưu online user registry theo WebSocket session.                                  | `pom.xml`, `src/main/java/com/chat_socket/config/RedisCacheConfig.java`, `src/main/java/com/chat_socket/socket/UserOnlineRegistry.java` |
| Spring WebSocket/STOMP          | Realtime communication, STOMP endpoint, broker topic/queue.                       | `pom.xml`, `src/main/java/com/chat_socket/config/WebSocketConfig.java`, `src/main/java/com/chat_socket/socket/*`                        |
| MapStruct                       | Mapping DTO/entity.                                                               | `pom.xml`, `src/main/java/com/chat_socket/mapper/*`, `src/main/java/com/chat_socket/config/GlobalMapperConfig.java`                     |
| Lombok                          | Sinh getter/setter/constructor cho entity và embeddable.                          | `pom.xml`, `src/main/java/com/chat_socket/entity/*`                                                                                     |
| Maven Wrapper                   | Chạy Maven qua `mvnw` hoặc `mvnw.cmd`.                                            | `mvnw`, `mvnw.cmd`, `Taskfile.yml`                                                                                                      |
| Spotless + Palantir Java Format | Check/apply format Java trong Maven build.                                        | `pom.xml`, `Taskfile.yml`                                                                                                               |
| uuid-creator                    | Sinh UUIDv7 thông qua custom Hibernate generator.                                 | `pom.xml`, `src/main/java/com/chat_socket/utils/UUIDv7.java`, `src/main/java/com/chat_socket/utils/UUIDv7Generator.java`                |
| Bean Validation                 | Validate request DTO.                                                             | `pom.xml`, `src/main/java/com/chat_socket/dto/*Request.java`                                                                            |
| Jackson Databind                | Serialize error response trong security filter và ObjectMapper bean.              | `pom.xml`, `src/main/java/com/chat_socket/config/JacksonConfig.java`, `src/main/java/com/chat_socket/security/SecurityFilter.java`      |

## 3. Cấu trúc thư mục source

| Package/thư mục          | Vai trò                                                                                      | File/path liên quan                                                                                              |
| ------------------------ | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `com.chat_socket`        | Entry point và cấu hình properties.                                                          | `src/main/java/com/chat_socket/ChatSocketApplication.java`, `src/main/java/com/chat_socket/ApplicationYaml.java` |
| `controller`             | REST API layer. Controller nhận request, gọi service và trả `BaseResponse`.                  | `src/main/java/com/chat_socket/controller/*`                                                                     |
| `service`                | Interface service cho các nghiệp vụ auth, user, friend, conversation, message, JWT.          | `src/main/java/com/chat_socket/service/*`                                                                        |
| `service.impl`           | Business logic implementation.                                                               | `src/main/java/com/chat_socket/service/impl/*`                                                                   |
| `repository`             | Data access layer bằng Spring Data JPA và JPQL query.                                        | `src/main/java/com/chat_socket/repository/*`                                                                     |
| `entity`                 | JPA entities ánh xạ các bảng database.                                                       | `src/main/java/com/chat_socket/entity/*`                                                                         |
| `dto`                    | Request/response payload, pagination, socket payload.                                        | `src/main/java/com/chat_socket/dto/*`                                                                            |
| `mapper`                 | MapStruct mapper.                                                                            | `src/main/java/com/chat_socket/mapper/*`                                                                         |
| `security`               | HTTP filter, WebSocket auth, method permission checks.                                       | `src/main/java/com/chat_socket/security/*`                                                                       |
| `socket`                 | Realtime controller, emitter, publisher, online registry, transaction synchronization.       | `src/main/java/com/chat_socket/socket/*`                                                                         |
| `config`                 | Spring configuration cho security, WebSocket, Redis, Jackson, MapStruct, exception handling. | `src/main/java/com/chat_socket/config/*`                                                                         |
| `constant`               | Route, table name, Redis key, socket channel constants.                                      | `src/main/java/com/chat_socket/constant/*`                                                                       |
| `enums`                  | Domain enum.                                                                                 | `src/main/java/com/chat_socket/enums/*`                                                                          |
| `exception`              | Custom runtime exception.                                                                    | `src/main/java/com/chat_socket/exception/*`                                                                      |
| `utils`                  | Helper cho normalize, pagination, Redis, security, UUIDv7.                                   | `src/main/java/com/chat_socket/utils/*`                                                                          |
| `resources/db/migration` | Flyway migration SQL.                                                                        | `src/main/resources/db/migration/*`                                                                              |

## 4. Cách chạy dự án local

### Yêu cầu môi trường

| Thành phần | Yêu cầu                                                                    |
| ---------- | -------------------------------------------------------------------------- |
| JDK        | Java 25 theo `pom.xml`                                                     |
| Maven      | Có thể dùng Maven wrapper `mvnw` hoặc `mvnw.cmd`                           |
| Docker     | Cần để chạy PostgreSQL và Redis theo `deployment/docker-compose/infra.yml` |
| Task       | Cần nếu dùng command trong `Taskfile.yml`                                  |

### Chạy infra

```bash
task start_infra
```

Hoặc:

```bash
docker compose -f deployment/docker-compose/infra.yml up -d
```

Infra trong `deployment/docker-compose/infra.yml` gồm:

| Service    | Image                | Port host | Ghi chú                                              |
| ---------- | -------------------- | --------: | ---------------------------------------------------- |
| PostgreSQL | `postgres:18-alpine` |    `5432` | DB `chat-socket-db`, user `admin`, password `123456` |
| Redis      | `redis:7-alpine`     |    `6379` | Dùng cho online user registry                        |

### Chạy backend

```bash
task run
```

Hoặc:

```bash
mvn spring-boot:run
```

### Compile

```bash
task compile_without_test
```

Command thực tế trong `Taskfile.yml`:

```bash
./mvnw -q -DskipTests clean compile
```

Trên Windows, `Taskfile.yml` dùng `cmd /c mvnw.cmd`.

### Format/check

```bash
task format
```

```bash
task format_fix
```

`task format` chạy `spotless:check`. `task format_fix` chạy `spotless:apply`.

### Repair Flyway

```bash
task flyway_repair
```

Command dùng URL PostgreSQL local `jdbc:postgresql://localhost:5432/chat-socket-db`, user `admin`, password `123456`.

### Các port

| Thành phần |   Port |
| ---------- | -----: |
| Backend    | `8089` |
| PostgreSQL | `5432` |
| Redis      | `6379` |

## 5. Cấu hình ứng dụng

Cấu hình mẫu nằm trong `src/main/resources/application-template.yaml`. Source hiện tại cũng có `src/main/resources/application.yaml` với giá trị local.

| Config                            | Giá trị mẫu                                       | Ý nghĩa                                                                                    |
| --------------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `spring.mvc.servlet.path`         | `/api`                                            | Prefix servlet path cho REST API. Khi kết hợp với route `/v1`, base REST API là `/api/v1`. |
| `spring.application.name`         | `chat-socket`                                     | Tên Spring application.                                                                    |
| `spring.datasource.url`           | `jdbc:postgresql://localhost:5432/chat-socket-db` | JDBC URL PostgreSQL.                                                                       |
| `spring.datasource.username`      | `admin`                                           | Username database local.                                                                   |
| `spring.datasource.password`      | `123456`                                          | Password database local.                                                                   |
| `spring.data.redis.host`          | `localhost`                                       | Redis host.                                                                                |
| `spring.data.redis.port`          | `6379`                                            | Redis port.                                                                                |
| `spring.jpa.hibernate.ddl-auto`   | `none`                                            | Hibernate không tự sinh schema. Schema do Flyway quản lý.                                  |
| `spring.jpa.show-sql`             | `true`                                            | Log SQL ra console.                                                                        |
| `server.port`                     | `8089`                                            | Port backend.                                                                              |
| `chat-socket.access-token-secret` | `secret-key` trong template                       | Secret ký JWT access token.                                                                |
| `chat-socket.access-token-ttl`    | `15`                                              | TTL access token theo phút.                                                                |
| `chat-socket.refresh-token-ttl`   | `14`                                              | TTL refresh token theo ngày.                                                               |
| `chat-socket.client-url`          | `http://localhost:3000`                           | Origin frontend được dùng cho WebSocket allowed origin.                                    |

Các giá trị không nên hard-code khi deploy production:

| Config                            | Lý do                                                        |
| --------------------------------- | ------------------------------------------------------------ |
| `spring.datasource.password`      | Credential database phải externalize qua env/secret manager. |
| `spring.datasource.username`      | Nên externalize theo môi trường.                             |
| `spring.datasource.url`           | Phụ thuộc môi trường deploy.                                 |
| `chat-socket.access-token-secret` | Secret ký JWT phải đủ mạnh và không commit vào source.       |
| `chat-socket.client-url`          | Phải cấu hình đúng origin frontend production.               |
| `spring.jpa.show-sql`             | Production thường không bật SQL log mặc định.                |

## 6. Authentication và session

### Sign up

Flow từ source:

| Bước | Mô tả                                                                                               | File/path                                                                                                                |
| ---- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| 1    | Client gọi `POST /api/v1/auth/sign-up` với `SignUpRequest`.                                         | `src/main/java/com/chat_socket/controller/AuthController.java`                                                           |
| 2    | Validate `username`, `email`, `password`, `firstName`, `lastName` không blank; `email` đúng format. | `src/main/java/com/chat_socket/dto/SignUpRequest.java`                                                                   |
| 3    | Service kiểm tra username đã tồn tại bằng `UserRepository.existsByUsername`.                        | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                        |
| 4    | Map request sang `UserEntity`, hash password bằng `PasswordEncoder`.                                | `src/main/java/com/chat_socket/mapper/UserMapper.java`, `src/main/java/com/chat_socket/config/SecurityServerConfig.java` |
| 5    | Lưu user vào bảng `users`. `UserEntity` tự cập nhật `normalizedName` trước persist/update.          | `src/main/java/com/chat_socket/entity/UserEntity.java`                                                                   |
| 6    | Trả `204 NO_CONTENT` nếu tạo thành công.                                                            | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                        |

Nếu username đã tồn tại, service trả `409 CONFLICT` với message `User already exists`.

### Sign in

Flow từ source:

| Bước | Mô tả                                                                                            | File/path                                                                                                           |
| ---- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| 1    | Client gọi `POST /api/v1/auth/sign-in` với `SignInRequest`.                                      | `src/main/java/com/chat_socket/controller/AuthController.java`                                                      |
| 2    | Service tìm user theo username. Nếu không có hoặc password không match, throw `SignInException`. | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                   |
| 3    | Access token JWT được sinh bằng user id ở JWT subject.                                           | `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`                                                    |
| 4    | Refresh token được sinh bằng 64 random bytes và encode hex, kết quả dài 128 ký tự.               | `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`                                                    |
| 5    | Session được lưu vào bảng `sessions` theo `user_id`, `refresh_token`, `expires_at`.              | `src/main/java/com/chat_socket/entity/SessionEntity.java`, `src/main/resources/db/migration/V7__sessions_table.sql` |
| 6    | Refresh token được set vào cookie `refreshToken`.                                                | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                   |
| 7    | Response body trả `AuthResponse(accessToken)`.                                                   | `src/main/java/com/chat_socket/dto/AuthResponse.java`                                                               |

Cookie refresh token theo source:

| Thuộc tính | Giá trị                              |
| ---------- | ------------------------------------ |
| Name       | `refreshToken`                       |
| `httpOnly` | `true`                               |
| `secure`   | `true`                               |
| `sameSite` | `none`                               |
| `maxAge`   | `chat-socket.refresh-token-ttl` ngày |

### Access token

Access token được sinh trong `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`:

| Thuộc tính | Mô tả                             |
| ---------- | --------------------------------- |
| Subject    | `userId.toString()`               |
| Issued at  | `Instant.now()`                   |
| Expiration | `now + access-token-ttl` phút     |
| Signature  | HMAC SHA-256 (`Jwts.SIG.HS256`)   |
| Secret     | `chat-socket.access-token-secret` |

### Refresh token

Refresh token không phải JWT. Source sinh token bằng `SecureRandom`, 64 bytes, encode hex. Refresh token được lưu vào bảng `sessions` và gửi qua cookie `refreshToken`.

### Sign out

Flow từ source:

| Bước | Mô tả                                                                    |
| ---- | ------------------------------------------------------------------------ |
| 1    | Client gọi `POST /api/v1/auth/sign-out`.                                 |
| 2    | Service đọc cookie `refreshToken`.                                       |
| 3    | Nếu không có cookie, throw `UnAuthorizedException("Token not found.")`.  |
| 4    | Xóa session bằng `SessionRepository.deleteByRefreshToken(refreshToken)`. |
| 5    | Clear cookie `refreshToken` bằng `maxAge(Duration.ZERO)`.                |
| 6    | Trả `200 OK`, message `Logout successful.`                               |

### Refresh access token

Flow từ source:

| Bước | Mô tả                                                                                                  |
| ---- | ------------------------------------------------------------------------------------------------------ |
| 1    | Client gọi `POST /api/v1/auth/refresh`.                                                                |
| 2    | Service đọc cookie `refreshToken`.                                                                     |
| 3    | Tìm session theo refresh token. Nếu không có, throw `ForbiddenException("Token expired or invalid.")`. |
| 4    | Nếu `expiresAt` trước thời điểm hiện tại, xóa session, clear cookie, throw `ForbiddenException`.       |
| 5    | Sinh access token mới theo `session.userId`.                                                           |
| 6    | Trả `AuthResponse(accessToken)`.                                                                       |

### SecurityFilter

`SecurityFilter` trong `src/main/java/com/chat_socket/security/SecurityFilter.java` xử lý HTTP authentication:

| Hành vi               | Mô tả                                                                                                     |
| --------------------- | --------------------------------------------------------------------------------------------------------- |
| Header đọc token      | `Authorization: Bearer <token>`                                                                           |
| Bypass filter         | `OPTIONS`, `/api/ws`, các route bắt đầu `/api/v1/auth`                                                    |
| Token thiếu           | Trả `401 UNAUTHORIZED`, message `Token not found.`                                                        |
| Token invalid/expired | Trả `403 FORBIDDEN`, message `Token expired or invalid.`                                                  |
| User không tồn tại    | Trả `404 NOT_FOUND`, message `User does not exist.`                                                       |
| Authenticated user    | Được đưa vào `SecurityContextHolder` dưới dạng `UsernamePasswordAuthenticationToken` chứa `UserSecurity`. |

Password encoder được khai báo là `BCryptPasswordEncoder(10)` trong `src/main/java/com/chat_socket/config/SecurityServerConfig.java`.

## 7. REST API documentation

Base URL REST API: `/api/v1`.

Response chung dùng `BaseResponse<T>`:

```json
{
  "data": {},
  "message": "string",
  "status": 200
}
```

### Auth API

Base: `/api/v1/auth`

| Method | URL                     | Controller/service                                  | Auth                                                       | Request               | Response                                                | Mô tả                                             | Validation chính                                                                      | Error có thể xảy ra                                                   |
| ------ | ----------------------- | --------------------------------------------------- | ---------------------------------------------------------- | --------------------- | ------------------------------------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `POST` | `/api/v1/auth/sign-up`  | `AuthController.signUp`, `AuthServiceImpl.signUp`   | Public, bypass `SecurityFilter`                            | Body `SignUpRequest`  | `BaseResponse<String>`                                  | Tạo user mới.                                     | `username`, `email`, `password`, `firstName`, `lastName` không blank; `email` hợp lệ. | `400 Validation failed`, `409 User already exists`, `500`             |
| `POST` | `/api/v1/auth/sign-in`  | `AuthController.signIn`, `AuthServiceImpl.signIn`   | Public, bypass `SecurityFilter`                            | Body `SignInRequest`  | `BaseResponse<AuthResponse>`; set cookie `refreshToken` | Đăng nhập, trả access token, lưu refresh session. | `username`, `password` không blank.                                                   | `400 Username or password incorrect!`, `400 Validation failed`, `500` |
| `POST` | `/api/v1/auth/sign-out` | `AuthController.signOut`, `AuthServiceImpl.signOut` | Public theo filter; nghiệp vụ yêu cầu cookie refresh token | Cookie `refreshToken` | `BaseResponse<String>`                                  | Đăng xuất, xóa session và clear cookie.           | Cookie refresh token phải tồn tại.                                                    | `401 Token not found.`, `500`                                         |
| `POST` | `/api/v1/auth/refresh`  | `AuthController.refresh`, `AuthServiceImpl.refresh` | Public theo filter; nghiệp vụ yêu cầu cookie refresh token | Cookie `refreshToken` | `BaseResponse<AuthResponse>`                            | Refresh access token.                             | Cookie refresh token phải tồn tại và session chưa hết hạn.                            | `401 Token not found.`, `403 Token expired or invalid.`, `500`        |

### User API

Base: `/api/v1/user`

| Method  | URL                            | Controller/service                                             | Auth     | Request                  | Response                            | Mô tả                                                                          | Validation chính                                                                | Error có thể xảy ra                                                                                             |
| ------- | ------------------------------ | -------------------------------------------------------------- | -------- | ------------------------ | ----------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `GET`   | `/api/v1/user/me`              | `UserController.getMe`, `UserServiceImpl.getUserProfile`       | Required | Không có                 | `BaseResponse<UserProfileDto>`      | Lấy profile user hiện tại.                                                     | Bearer token hợp lệ.                                                            | `401`, `403`, `404 User not found`, `500`                                                                       |
| `PATCH` | `/api/v1/user/me`              | `UserController.updateMe`, `UserServiceImpl.updateUserProfile` | Required | Body `UpdateUserRequest` | `BaseResponse<UserProfileDto>`      | Cập nhật profile user hiện tại.                                                | Size/email theo DTO; nếu field required được gửi chuỗi rỗng thì service reject. | `400 Validation failed`, `400 Username already exists`, `400 Email already exists`, `404 User not found`, `500` |
| `GET`   | `/api/v1/user?search=...`      | `UserController.searchUsers`, `UserServiceImpl.searchUsers`    | Required | Query `search`, `offset`, `limit` optional | `BaseResponse<PaginationResponse<UserSearchDto>>` | Search tất cả user theo username và tên chuẩn hóa, trả kèm trạng thái quan hệ. | Search rỗng trả list rỗng; `offset >= 0`; `limit > 0`.                           | `400 Offset must be greater than or equal to 0.`, `400 Limit must be greater than 0.`, `401`, `403`, `500`      |
| `GET`   | `/api/v1/user/info?userId=...` | `UserController.getInfo`, `UserServiceImpl.getUserInfo`        | Required | Query `userId: UUID`     | `BaseResponse<UserInfoDto>`         | Lấy thông tin user theo id, trả kèm trạng thái quan hệ.                        | `userId` phải parse được UUID.                                                  | `404 User not found`, `401`, `403`, `500`                                                                       |

### Friend API

Base: `/api/v1/friend`

| Method   | URL                         | Controller/service                                                                | Auth     | Request                           | Response                                      | Mô tả                                                                                      | Validation chính                                 | Error có thể xảy ra                                                                                                    |
| -------- | --------------------------- | --------------------------------------------------------------------------------- | -------- | --------------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/api/v1/friend`            | `FriendController.getListFriend`, `FriendServiceImpl.getListFriend`               | Required | Query `search`, `offset`, `limit` | `BaseResponse<PaginationResponse<FriendDto>>` | Search bạn bè theo username và tên chuẩn hóa bằng offset pagination.                   | `offset >= 0`; `limit > 0`.                      | `400 Offset must be greater than or equal to 0.`, `400 Limit must be greater than 0.`, `401`, `403`, `500`             |
| `GET`    | `/api/v1/friend/request`    | `FriendController.getListFriendRequest`, `FriendServiceImpl.getListFriendRequest` | Required | Không có                          | `BaseResponse<FriendRequestResponse>`         | Lấy pending sent và received friend requests của user hiện tại.                            | Bearer token hợp lệ.                             | `401`, `403`, `500`                                                                                                    |
| `POST`   | `/api/v1/friend/request`    | `FriendController.sendFriendRequest`, `FriendServiceImpl.sendFriendRequest`       | Required | Body `FriendSendRequest`          | `BaseResponse<String>`                        | Gửi friend request.                                                                        | `toUserId` required; `message` tối đa 300 ký tự. | `400 self request`, `404 User not found.`, `409 already friends`, `409 pending exists`, `400 Validation failed`, `500` |
| `POST`   | `/api/v1/friend/accept`     | `FriendController.acceptFriendRequest`, `FriendServiceImpl.acceptFriendRequest`   | Required | Body `FriendActionRequest`        | `BaseResponse<AcceptFriendResponse>`          | Chấp nhận pending request gửi tới current user, tạo row `friends`.                         | `requestId` required.                            | `404 Friend request not found.`, `403 not authorized`, `400 Validation failed`, `500`                                  |
| `POST`   | `/api/v1/friend/decline`    | `FriendController.declineFriendRequest`, `FriendServiceImpl.declineFriendRequest` | Required | Body `FriendActionRequest`        | `BaseResponse<String>`                        | Từ chối pending request gửi tới current user.                                              | `requestId` required.                            | `404 Friend request not found.`, `403 not authorized`, `400 Validation failed`, `500`                                  |
| `POST`   | `/api/v1/friend/cancel`     | `FriendController.cancelFriendRequest`, `FriendServiceImpl.cancelFriendRequest`   | Required | Body `FriendActionRequest`        | `BaseResponse<String>`                        | Hủy pending request do current user gửi.                                                   | `requestId` required.                            | `404 Friend request not found.`, `403 not authorized`, `400 Validation failed`, `500`                                  |
| `DELETE` | `/api/v1/friend/{friendId}` | `FriendController.deleteFriend`, `FriendServiceImpl.deleteFriend`                 | Required | Path `friendId: UUID`             | `BaseResponse<String>`                        | Xóa quan hệ bạn bè giữa current user và `friendId`.                                        | `friendId` phải là UUID.                         | `404 Friend not found.`, `401`, `403`, `500`                                                                           |

### Conversation API

Base: `/api/v1/conversation`

| Method   | URL                                                        | Controller/service                                                                        | Auth                                                                          | Request                                           | Response                                            | Mô tả                                                                                                                             | Validation chính                                                                                    | Error có thể xảy ra                                                                                                                                                                                                              |
| -------- | ---------------------------------------------------------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ------------------------------------------------- | --------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/api/v1/conversation`                                     | `ConversationController.getConversations`, `ConversationServiceImpl.getConversations`     | Required                                                                      | Query `limit`, `cursor`, `type` optional          | `BaseResponse<PaginationResponse<ConversationDto>>` | Lấy active conversations của current user, sort theo `COALESCE(lastMessageAt, updatedAt)` giảm dần.                               | `limit > 0`; cursor ISO datetime; `type` là `DIRECT` hoặc `GROUP` nếu có.                           | `400 Cursor is invalid.`, `400 Limit must be greater than 0.`, `401`, `403`, `500`                                                                                                                                               |
| `POST`   | `/api/v1/conversation`                                     | `ConversationController.createConversation`, `ConversationServiceImpl.createConversation` | Required, `@PreAuthorize` kiểm tra member là bạn bè                           | Body `ConversationRequest`                        | `BaseResponse<ConversationDto>`                     | Tạo direct hoặc group conversation.                                                                                               | `type` required; `name` not blank; `memberIds` not empty; direct cần đúng 1 member; group cần name. | `400`, `403 FriendPermissionException`, `404 User/Member not found`, `500`                                                                                                                                                       |
| `GET`    | `/api/v1/conversation/{conversationId}/messages`           | `ConversationController.getMessages`, `ConversationServiceImpl.getMessages`               | Required                                                                      | Path `conversationId`; query `limit`, `cursor`    | `BaseResponse<PaginationResponse<MessageDto>>`      | Lấy messages của conversation theo cursor.                                                                                        | Current user phải là active participant.                                                            | `404 Conversation not found.`, `403 not participant`, `400 cursor/limit`, `500`                                                                                                                                                  |
| `PATCH`  | `/api/v1/conversation/{conversationId}/seen`               | `ConversationController.markAsSeen`, `ConversationServiceImpl.markAsSeen`                 | Required                                                                      | Path `conversationId`                             | `BaseResponse<Void>`                                | Đánh dấu last message của conversation là đã đọc.                                                                                 | Current user phải là active participant.                                                            | `404 Conversation not found.`, `403 not participant`, `200 no messages`, `200 already marked`, `500`                                                                                                                             |
| `PATCH`  | `/api/v1/conversation/{conversationId}/group`              | `ConversationController.updateGroup`, `ConversationServiceImpl.updateGroup`               | Required, `@PreAuthorize("@groupPermission.canManageGroup(#conversationId)")` | Path `conversationId`, body `UpdateGroupRequest`  | `BaseResponse<ConversationDto>`                     | Cập nhật thông tin nhóm (chỉ `name`) cho group conversation.                                                                      | Current user phải là active participant role `ADMIN`; `name` không blank; không áp dụng cho DIRECT. | `404 Group conversation not found.`, `403`, `400 Validation`                                                                                                                                                                     |
| `DELETE` | `/api/v1/conversation/{conversationId}/group`              | `ConversationController.deleteGroup`, `ConversationServiceImpl.deleteGroup`               | Required, `@PreAuthorize("@groupPermission.canManageGroup(#conversationId)")` | Path `conversationId`                             | `BaseResponse<Void>`                                | Xóa/ẩn group conversation khỏi danh sách của current user bằng `participants.deleted_at`; không xóa dữ liệu của participant khác. | Current user phải là active participant role `ADMIN`.                                               | `404 Group conversation not found.`, `403 not participant/not admin`, `500`                                                                                                                                                      |
| `POST`   | `/api/v1/conversation/{conversationId}/members`            | `ConversationController.addGroupMembers`, `ConversationServiceImpl.addGroupMembers`       | Required                                                                      | Path `conversationId`, body `GroupMembersRequest` | `BaseResponse<ConversationDto>`                     | Thêm member vào nhóm.                                                                                                             | Active participant trong group (ADMIN/MEMBER đều được), target user phải là bạn bè theo từng user.  | `404 Group conversation not found.`, `403`, `403 FriendPermissionException`, `404 User not found.`, `400 memberIds invalid`                                                                                                      |
| `DELETE` | `/api/v1/conversation/{conversationId}/members/{memberId}` | `ConversationController.removeGroupMember`, `ConversationServiceImpl.removeGroupMember`   | Required                                                                      | Path `conversationId`, `memberId`                 | `BaseResponse<ConversationDto>`                     | Bỏ một member ra khỏi nhóm (set `leftAt`).                                                                                        | Chỉ ADMIN mới được remove; chỉ remove MEMBER; không dùng để tự leave.                               | `404 Group conversation not found.`, `404 Participant not found.`, `403 Only admins can manage group members.`, `400 You cannot remove an admin from this group.`, `400 You cannot remove yourself. Use leave endpoint instead.` |
| `POST`   | `/api/v1/conversation/{conversationId}/leave`              | `ConversationController.leaveGroup`, `ConversationServiceImpl.leaveGroup`                 | Required                                                                      | Path `conversationId`                             | `BaseResponse<Void>`                                | User tự rời nhóm.                                                                                                                 | Active participant; nếu là ADMIN phải còn ít nhất 1 ADMIN khác active.                              | `404 Group conversation not found.`, `400 You are the only admin. Assign another admin before leaving.`                                                                                                                          |

### Message API

Base: `/api/v1/message`

| Method | URL                      | Controller/service                                                            | Auth                                                                                         | Request               | Response                   | Mô tả                                                                                                  | Validation chính                                                                                                                                           | Error có thể xảy ra                                                                                                        |
| ------ | ------------------------ | ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | --------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/api/v1/message/direct` | `MessageController.sendDirectMessage`, `MessageServiceImpl.sendDirectMessage` | Required, `@PreAuthorize("@messageDirectPermission.canSendDirect(#request.recipientId())")`  | Body `MessageRequest` | `BaseResponse<MessageDto>` | Gửi direct message. Nếu không có `conversationId`, tìm hoặc tạo direct conversation với `recipientId`. | `content` not blank; nếu không có `conversationId` thì cần `recipientId`; không gửi cho chính mình; recipient phải là bạn bè nếu `recipientId` có giá trị. | `400 Content/Recipient required`, `400 self message`, `403 not friend`, `404 Conversation/User/Recipient not found`, `500` |
| `POST` | `/api/v1/message/group`  | `MessageController.sendGroupMessage`, `MessageServiceImpl.sendGroupMessage`   | Required, `@PreAuthorize("@messageGroupPermission.canSendGroup(#request.conversationId())")` | Body `MessageRequest` | `BaseResponse<MessageDto>` | Gửi message vào group conversation.                                                                    | `content` not blank; `conversationId` required; conversation phải là group; user phải là active participant.                                               | `400 Content/Conversation required`, `404 Group conversation not found.`, `403 not participant`, `500`                     |

## 8. DTO request/response

### DTO chung

| DTO                     | Field        | Type      | Required/optional | Validation                                                    | Ý nghĩa                                                                            |
| ----------------------- | ------------ | --------- | ----------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `BaseResponse<T>`       | `data`       | `T`       | Optional          | Không có                                                      | Payload response.                                                                  |
| `BaseResponse<T>`       | `message`    | `String`  | Optional          | Không có                                                      | Message nghiệp vụ hoặc lỗi.                                                        |
| `BaseResponse<T>`       | `status`     | `int`     | Required          | Không có                                                      | HTTP status code được service/handler set.                                         |
| `PaginationRequest`     | `limit`      | `Integer` | Optional          | Service yêu cầu `> 0`; default `50`, max `100`.               | Số item muốn lấy.                                                                  |
| `PaginationRequest`     | `cursor`     | `String`  | Optional          | Parse theo `ISO_LOCAL_DATE_TIME` hoặc `ISO_OFFSET_DATE_TIME`. | Cursor datetime.                                                                   |
| `PaginationRequest`     | `offset`     | `Integer` | Optional          | Service yêu cầu `>= 0`; default `0`.                          | Vị trí bắt đầu cho search user/friend.                                             |
| `PaginationResponse<T>` | `messages`   | `List<T>` | Required          | Không có                                                      | Danh sách item. Tên field cố định là `messages` dù dùng cho conversations/friends. |
| `PaginationResponse<T>` | `nextCursor` | `String`  | Optional          | Không có                                                      | Cursor cho page tiếp theo ở cursor pagination.                                      |
| `PaginationResponse<T>` | `nextOffset` | `Integer` | Optional          | Không có                                                      | Offset cho page tiếp theo ở offset pagination.                                      |

### Auth DTO

| DTO             | Field         | Type     | Required/optional | Validation            | Ý nghĩa                                |
| --------------- | ------------- | -------- | ----------------- | --------------------- | -------------------------------------- |
| `AuthResponse`  | `accessToken` | `String` | Required          | Không có              | JWT access token.                      |
| `SignUpRequest` | `username`    | `String` | Required          | `@NotBlank`           | Username đăng ký.                      |
| `SignUpRequest` | `email`       | `String` | Required          | `@NotBlank`, `@Email` | Email đăng ký.                         |
| `SignUpRequest` | `password`    | `String` | Required          | `@NotBlank`           | Password thô, được hash trước khi lưu. |
| `SignUpRequest` | `firstName`   | `String` | Required          | `@NotBlank`           | Tên.                                   |
| `SignUpRequest` | `lastName`    | `String` | Required          | `@NotBlank`           | Họ.                                    |
| `SignInRequest` | `username`    | `String` | Required          | `@NotBlank`           | Username đăng nhập.                    |
| `SignInRequest` | `password`    | `String` | Required          | `@NotBlank`           | Password đăng nhập.                    |

### User DTO

| DTO                 | Field          | Type            | Required/optional | Validation                                                                      | Ý nghĩa                                        |
| ------------------- | -------------- | --------------- | ----------------- | ------------------------------------------------------------------------------- | ---------------------------------------------- |
| `UpdateUserRequest` | `username`     | `String`        | Optional          | `@Size(max=50)`; service trim và không cho blank nếu field được gửi.            | Username mới.                                  |
| `UpdateUserRequest` | `email`        | `String`        | Optional          | `@Email`, `@Size(max=255)`; service trim và không cho blank nếu field được gửi. | Email mới.                                     |
| `UpdateUserRequest` | `firstName`    | `String`        | Optional          | `@Size(max=70)`; service trim và không cho blank nếu field được gửi.            | Tên mới.                                       |
| `UpdateUserRequest` | `lastName`     | `String`        | Optional          | `@Size(max=30)`; service trim và không cho blank nếu field được gửi.            | Họ mới.                                        |
| `UpdateUserRequest` | `avatarUrl`    | `String`        | Optional          | `@Size(max=500)`                                                                | URL avatar. Blank được chuyển thành `null`.    |
| `UpdateUserRequest` | `avatarId`     | `String`        | Optional          | `@Size(max=100)`                                                                | ID avatar. Blank được chuyển thành `null`.     |
| `UpdateUserRequest` | `bio`          | `String`        | Optional          | Không có                                                                        | Bio. Blank được chuyển thành `null`.           |
| `UpdateUserRequest` | `phone`        | `String`        | Optional          | `@Size(max=20)`                                                                 | Số điện thoại. Blank được chuyển thành `null`. |
| `UserProfileDto`    | `id`           | `UUID`          | Required          | Không có                                                                        | User id.                                       |
| `UserProfileDto`    | `username`     | `String`        | Required          | Không có                                                                        | Username.                                      |
| `UserProfileDto`    | `firstName`    | `String`        | Required          | Không có                                                                        | Tên.                                           |
| `UserProfileDto`    | `lastName`     | `String`        | Required          | Không có                                                                        | Họ.                                            |
| `UserProfileDto`    | `email`        | `String`        | Required          | Không có                                                                        | Email.                                         |
| `UserProfileDto`    | `avatarUrl`    | `String`        | Optional          | Không có                                                                        | URL avatar.                                    |
| `UserProfileDto`    | `bio`          | `String`        | Optional          | Không có                                                                        | Bio.                                           |
| `UserProfileDto`    | `phone`        | `String`        | Optional          | Không có                                                                        | Phone.                                         |
| `UserInfoDto`       | `id`           | `UUID`          | Required          | Không có                                                                        | User id.                                       |
| `UserInfoDto`       | `username`     | `String`        | Required          | Không có                                                                        | Username.                                      |
| `UserInfoDto`       | `firstName`    | `String`        | Required          | Không có                                                                        | Tên.                                           |
| `UserInfoDto`       | `lastName`     | `String`        | Required          | Không có                                                                        | Họ.                                            |
| `UserInfoDto`       | `email`        | `String`        | Required          | Không có                                                                        | Email.                                         |
| `UserInfoDto`       | `avatarUrl`    | `String`        | Optional          | Không có                                                                        | URL avatar.                                    |
| `UserInfoDto`       | `bio`          | `String`        | Optional          | Không có                                                                        | Bio.                                           |
| `UserInfoDto`       | `phone`        | `String`        | Optional          | Không có                                                                        | Phone.                                         |
| `UserInfoDto`       | `joinedAt`     | `LocalDateTime` | Required          | Không có                                                                        | Thời điểm tạo user.                            |
| `UserInfoDto`       | `statusFriend` | `FriendStatus`  | Required          | Không có                                                                        | `NONE`, `SELF`, `FRIEND`, `SENT`, `RECEIVED`.  |
| `UserSearchDto`     | `id`           | `UUID`          | Required          | Không có                                                                        | User id.                                       |
| `UserSearchDto`     | `username`     | `String`        | Required          | Không có                                                                        | Username.                                      |
| `UserSearchDto`     | `firstName`    | `String`        | Required          | Không có                                                                        | Tên.                                           |
| `UserSearchDto`     | `lastName`     | `String`        | Required          | Không có                                                                        | Họ.                                            |
| `UserSearchDto`     | `avatarUrl`    | `String`        | Optional          | Không có                                                                        | Avatar.                                        |
| `UserSearchDto`     | `joinedAt`     | `LocalDateTime` | Required          | Không có                                                                        | User created time.                             |
| `UserSearchDto`     | `statusFriend` | `FriendStatus`  | Required          | Không có                                                                        | `NONE`, `SELF`, `FRIEND`, `SENT`, `RECEIVED`.  |
| `UserSearchDto`     | `requestId`    | `UUID`          | Optional          | Không có                                                                        | Pending friend request id nếu có.              |

### Friend DTO

| DTO                     | Field              | Type                             | Required/optional | Validation       | Ý nghĩa                                                           |
| ----------------------- | ------------------ | -------------------------------- | ----------------- | ---------------- | ----------------------------------------------------------------- |
| `FriendSendRequest`     | `toUserId`         | `UUID`                           | Required          | `@NotNull`       | User nhận lời mời.                                                |
| `FriendSendRequest`     | `message`          | `String`                         | Optional          | `@Size(max=300)` | Message lời mời. Entity trim trước persist/update.                |
| `FriendActionRequest`   | `requestId`        | `UUID`                           | Required          | `@NotNull`       | Friend request id.                                                |
| `FriendDto`             | `id`               | `UUID`                           | Required          | Không có         | Id của friend user, không phải id row `friends`.                  |
| `FriendDto`             | `username`         | `String`                         | Required          | Không có         | Username friend.                                                  |
| `FriendDto`             | `firstName`        | `String`                         | Required          | Không có         | Tên friend.                                                       |
| `FriendDto`             | `lastName`         | `String`                         | Required          | Không có         | Họ friend.                                                        |
| `FriendDto`             | `avatarUrl`        | `String`                         | Optional          | Không có         | Avatar friend.                                                    |
| `FriendDto`             | `joinedAt`         | `LocalDateTime`                  | Required          | Không có         | Source dùng `friendUser.createdAt`, không phải thời điểm kết bạn. |
| `FriendRequestResponse` | `sentRequests`     | `List<FriendRequestSentDto>`     | Required          | Không có         | Pending requests do current user gửi.                             |
| `FriendRequestResponse` | `receivedRequests` | `List<FriendRequestReceviedDto>` | Required          | Không có         | Pending requests current user nhận.                               |

### Conversation DTO

| DTO                          | Field               | Type                               | Required/optional | Validation                                                   | Ý nghĩa                                                                |
| ---------------------------- | ------------------- | ---------------------------------- | ----------------- | ------------------------------------------------------------ | ---------------------------------------------------------------------- |
| `ConversationRequest`        | `type`              | `ConversationType`                 | Required          | `@NotNull`                                                   | `DIRECT` hoặc `GROUP`.                                                 |
| `ConversationRequest`        | `name`              | `String`                           | Required theo DTO | `@NotBlank`                                                  | Group name. Với direct, DTO vẫn yêu cầu `name` vì annotation hiện tại. |
| `ConversationRequest`        | `memberIds`         | `List<UUID>`                       | Required          | `@NotEmpty`; permission check yêu cầu từng member là bạn bè. | Member được thêm vào conversation.                                     |
| `UpdateGroupRequest`         | `name`              | `String`                           | Required          | `@NotBlank`                                                  | Tên mới cho group.                                                     |
| `GroupMembersRequest`        | `memberIds`         | `List<UUID>`                       | Required          | `@NotEmpty`                                                  | Danh sách userId thêm vào nhóm.                                        |
| `ConversationDto`            | `id`                | `UUID`                             | Required          | Không có                                                     | Conversation id.                                                       |
| `ConversationDto`            | `type`              | `ConversationType`                 | Required          | Không có                                                     | Loại conversation.                                                     |
| `ConversationDto`            | `groupName`         | `String`                           | Optional          | Không có                                                     | Tên group. Null với direct.                                            |
| `ConversationDto`            | `createdById`       | `UUID`                             | Optional          | Không có                                                     | User tạo conversation.                                                 |
| `ConversationDto`            | `directUserAId`     | `UUID`                             | Optional          | Không có                                                     | User A trong direct conversation đã normalize.                         |
| `ConversationDto`            | `directUserBId`     | `UUID`                             | Optional          | Không có                                                     | User B trong direct conversation đã normalize.                         |
| `ConversationDto`            | `lastMessageId`     | `UUID`                             | Optional          | Không có                                                     | Last message id.                                                       |
| `ConversationDto`            | `lastMessage`       | `MessageDto`                       | Optional          | Không có                                                     | Last message payload.                                                  |
| `ConversationDto`            | `lastMessageAt`     | `LocalDateTime`                    | Optional          | Không có                                                     | Thời điểm last message hoặc thời điểm tạo conversation theo service.   |
| `ConversationDto`            | `createdAt`         | `LocalDateTime`                    | Required          | Không có                                                     | Thời điểm tạo conversation.                                            |
| `ConversationDto`            | `updatedAt`         | `LocalDateTime`                    | Required          | Không có                                                     | Thời điểm cập nhật conversation.                                       |
| `ConversationDto`            | `unreadCount`       | `long`                             | Required          | Không có                                                     | Số message chưa đọc của current user.                                  |
| `ConversationDto`            | `participants`      | `List<ConversationParticipantDto>` | Required          | Không có                                                     | Participant info.                                                      |
| `ConversationParticipantDto` | `userId`            | `UUID`                             | Required          | Không có                                                     | Participant user id.                                                   |
| `ConversationParticipantDto` | `firstName`         | `String`                           | Required          | Không có                                                     | Tên participant.                                                       |
| `ConversationParticipantDto` | `lastName`          | `String`                           | Required          | Không có                                                     | Họ participant.                                                        |
| `ConversationParticipantDto` | `avatarUrl`         | `String`                           | Optional          | Không có                                                     | Avatar participant.                                                    |
| `ConversationParticipantDto` | `role`              | `ParticipantRole`                  | Required          | Không có                                                     | `ADMIN` hoặc `MEMBER`.                                                 |
| `ConversationParticipantDto` | `joinedAt`          | `LocalDateTime`                    | Required          | Không có                                                     | Thời điểm join.                                                        |
| `ConversationParticipantDto` | `lastReadMessageId` | `UUID`                             | Optional          | Không có                                                     | Message cuối cùng participant đã đọc.                                  |
| `ConversationParticipantDto` | `lastReadAt`        | `LocalDateTime`                    | Optional          | Không có                                                     | Thời điểm đọc cuối.                                                    |

### Message và realtime DTO

| DTO                     | Field               | Type            | Required/optional | Validation                                                                | Ý nghĩa                            |
| ----------------------- | ------------------- | --------------- | ----------------- | ------------------------------------------------------------------------- | ---------------------------------- |
| `MessageRequest`        | `recipientId`       | `UUID`          | Optional          | Permission check nếu có; direct cần khi không có `conversationId`.        | Recipient direct message.          |
| `MessageRequest`        | `content`           | `String`        | Required          | `@NotBlank`; service cũng check blank.                                    | Nội dung message.                  |
| `MessageRequest`        | `attachmentUrl`     | `String`        | Optional          | Không có                                                                  | URL attachment.                    |
| `MessageRequest`        | `conversationId`    | `UUID`          | Optional          | Group message yêu cầu; direct có thể dùng để gửi vào conversation có sẵn. | Conversation target.               |
| `MessageRequest`        | `type`              | `MessageType`   | Optional          | Null được default `TEXT`.                                                 | Message type.                      |
| `MessageDto`            | `id`                | `UUID`          | Required          | Không có                                                                  | Message id.                        |
| `MessageDto`            | `conversationId`    | `UUID`          | Required          | Map từ `message.conversation.id`.                                         | Conversation chứa message.         |
| `MessageDto`            | `senderId`          | `UUID`          | Required          | Map từ `message.sender.id`.                                               | User gửi.                          |
| `MessageDto`            | `content`           | `String`        | Optional          | Entity trim trước persist/update.                                         | Nội dung.                          |
| `MessageDto`            | `attachmentUrl`     | `String`        | Optional          | Không có                                                                  | Attachment URL.                    |
| `MessageDto`            | `type`              | `MessageType`   | Required          | Không có                                                                  | `TEXT`, `IMAGE`, `FILE`, `SYSTEM`. |
| `MessageDto`            | `createdAt`         | `LocalDateTime` | Required          | Không có                                                                  | Thời điểm tạo.                     |
| `MessageDto`            | `updatedAt`         | `LocalDateTime` | Required          | Không có                                                                  | Thời điểm cập nhật.                |
| `ConversationEvent`     | `eventType`         | `String`        | Required          | Static factory set `conversation.updated` hoặc `group.deleted`.           | Loại event.                        |
| `ConversationEvent`     | `conversationId`    | `UUID`          | Required          | Không có                                                                  | Conversation được update.          |
| `ConversationEvent`     | `lastMessage`       | `MessageDto`    | Optional          | Không có                                                                  | Last message.                      |
| `ConversationEvent`     | `lastMessageAt`     | `LocalDateTime` | Optional          | Không có                                                                  | Last message time.                 |
| `ConversationEvent`     | `unreadCount`       | `long`          | Required          | Không có                                                                  | Unread count theo user nhận event. |
| `ConversationSeenEvent` | `eventType`         | `String`        | Required          | Static factory set `conversation.seen`.                                   | Loại event.                        |
| `ConversationSeenEvent` | `conversationId`    | `UUID`          | Required          | Không có                                                                  | Conversation được mark seen.       |
| `ConversationSeenEvent` | `seenByUserId`      | `UUID`          | Required          | Không có                                                                  | User đã xem.                       |
| `ConversationSeenEvent` | `lastReadMessageId` | `UUID`          | Required          | Không có                                                                  | Message cuối đã đọc.               |
| `ConversationSeenEvent` | `lastReadAt`        | `LocalDateTime` | Required          | Không có                                                                  | Thời điểm seen.                    |

## 9. WebSocket/STOMP documentation

### Endpoint và broker

| Thành phần                     | Giá trị                  | File/path                                                                                                |
| ------------------------------ | ------------------------ | -------------------------------------------------------------------------------------------------------- |
| STOMP endpoint khai báo        | `/ws`                    | `src/main/java/com/chat_socket/config/WebSocketConfig.java`                                              |
| Servlet path config            | `/api`                   | `src/main/resources/application-template.yaml`                                                           |
| URL thực tế theo source/filter | `/api/ws`                | `src/main/java/com_chat_socket/security/SecurityFilter.java` dùng bypass `requestUri.matches("/api/ws")` |
| Application destination prefix | `/app`                   | `src/main/java/com/chat_socket/constant/SocketChannel.java`                                              |
| Broker prefixes                | `/topic`, `/queue`       | `src/main/java/com/chat_socket/config/WebSocketConfig.java`                                              |
| Allowed origin cho endpoint    | `chat-socket.client-url` | `src/main/java/com/chat_socket/config/WebSocketConfig.java`                                              |

Với cấu hình hiện tại, client nên kết nối WebSocket tới `ws://<host>:8089/api/ws` hoặc URL tương đương của STOMP client, vì servlet path là `/api` và `SecurityFilter` bypass handshake tại `/api/ws`.

Lưu ý theo source hiện tại: `SecurityServerConfig` permit matcher khai báo `"/ws*"` trong khi `SecurityFilter.shouldNotFilter` bypass `"/api/ws"`. Đây là điểm cần chú ý khi maintain security rule cho WebSocket handshake.

### Connect với JWT

Client gửi JWT qua native STOMP header:

| Header          | Giá trị                |
| --------------- | ---------------------- |
| `Authorization` | `Bearer <accessToken>` |

`SocketChannelInterceptor` xử lý `CONNECT`:

| Bước | Mô tả                                                                            | File/path                                                              |
| ---- | -------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| 1    | Đọc native header `Authorization`.                                               | `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java` |
| 2    | Kiểm tra prefix `Bearer `.                                                       | `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java` |
| 3    | Verify access token bằng `JwtService`.                                           | `src/main/java/com/chat_socket/utils/Security.java`                    |
| 4    | Load user từ `UserRepository`.                                                   | `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java` |
| 5    | Set `Authentication` vào STOMP accessor bằng `accessor.setUser(authentication)`. | `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java` |

### Subscribe authorization

`SocketChannelInterceptor` xử lý `SUBSCRIBE` cho message topic của conversation:

| Destination                                      | Authorization                                                                                  |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| `/topic/conversations/{conversationId}/messages` | User phải là active participant của conversation, tức `leftAt IS NULL` và `deletedAt IS NULL`. |

Nếu destination không phải message topic của conversation, source hiện tại không áp thêm kiểm tra participant trong interceptor.

### Online users

| Mục               | Giá trị                                                                                                                       |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Subscribe mapping | `/app/online-users`                                                                                                           |
| Topic broadcast   | `/topic/online-users`                                                                                                         |
| Payload           | `Set<UUID>`                                                                                                                   |
| Source            | `src/main/java/com/chat_socket/socket/SocketController.java`, `src/main/java/com/chat_socket/socket/SocketEventListener.java` |

`@SubscribeMapping("/online-users")` kết hợp với application prefix `/app`, nên client subscribe tới `/app/online-users` để nhận snapshot hiện tại. Khi user connect/disconnect, server broadcast danh sách online users qua `/topic/online-users`.

### Message, seen và conversation events

| Destination                                      | Payload                 | Khi publish                                                          | Source                                                                                                                 |
| ------------------------------------------------ | ----------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `/topic/conversations/{conversationId}/messages` | `MessageDto`            | Sau khi gửi message thành công.                                      | `src/main/java/com/chat_socket/socket/SocketPublisher.java`                                                            |
| `/topic/conversations/{conversationId}/seen`     | `ConversationSeenEvent` | Sau khi mark seen thành công.                                        | `src/main/java/com/chat_socket/socket/SocketPublisher.java`                                                            |
| `/user/queue/conversations`                      | `ConversationEvent`     | Sau khi gửi message, mark seen, update hoặc delete group thành công. | `src/main/java/com/chat_socket/socket/SocketEmitter.java`, `src/main/java/com/chat_socket/socket/SocketPublisher.java` |
| `/topic/online-users`                            | `Set<UUID>`             | Sau user connect/disconnect.                                         | `src/main/java/com/chat_socket/socket/SocketEventListener.java`                                                        |

### Publish after transaction commit

`SocketPublisher` đăng ký `SocketSynchronization` nếu transaction synchronization đang active. Event được publish trong `afterCommit()`. Nếu không có transaction active, event được publish ngay.

File liên quan:

| File                                                              | Vai trò                                           |
| ----------------------------------------------------------------- | ------------------------------------------------- |
| `src/main/java/com/chat_socket/socket/SocketPublisher.java`       | Chuẩn bị payload và đăng ký publish after commit. |
| `src/main/java/com/chat_socket/socket/SocketSynchronization.java` | Gọi `publish.run()` trong `afterCommit`.          |

## 10. Business rules chính

| Rule                                                                                 | Mô tả                                                                                                                              | File/path                                                                                                                                  |
| ------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Không thể gửi direct message cho người không phải bạn bè khi gửi theo `recipientId`. | `MessageDirectPermission.canSendDirect(UUID)` kiểm tra relationship trong bảng `friends`.                                          | `src/main/java/com/chat_socket/security/MessageDirectPermission.java`                                                                      |
| Direct message có `conversationId` không đi qua friend check theo `recipientId`.     | PreAuthorize chỉ check `request.recipientId()`. Service vẫn kiểm tra conversation là direct và current user là active participant. | `src/main/java/com/chat_socket/controller/MessageController.java`, `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`    |
| Tạo group chỉ cho phép thêm bạn bè.                                                  | `MessageDirectPermission.canSendDirect(List<UUID>)` kiểm tra toàn bộ `memberIds`.                                                  | `src/main/java/com/chat_socket/security/MessageDirectPermission.java`                                                                      |
| Gửi group message yêu cầu user là active participant của group.                      | Kiểm tra trong `MessageGroupPermission` và trong service.                                                                          | `src/main/java/com/chat_socket/security/MessageGroupPermission.java`, `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java` |
| Subscribe message topic của conversation yêu cầu user là active participant.         | Interceptor kiểm tra destination `/topic/conversations/{id}/messages`.                                                             | `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java`                                                                     |
| Direct conversation chuẩn hóa cặp user để tránh trùng.                               | User A/B được sort theo UUID string trong entity và service.                                                                       | `src/main/java/com/chat_socket/entity/ConversationEntity.java`, `src/main/java/com/chat_socket/utils/Normalize.java`                       |
| Friend relationship chuẩn hóa `user_a`/`user_b` theo thứ tự UUID.                    | `FriendEntity` normalize trước persist/update; DB có check `user_a_id < user_b_id`.                                                | `src/main/java/com/chat_socket/entity/FriendEntity.java`, `src/main/resources/db/migration/V3__friends_table.sql`                          |
| Friend request pending không được trùng giữa cùng một cặp user.                      | Service check pending hai chiều; DB có unique index `uq_friend_requests_pending_pair`.                                             | `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`, `src/main/resources/db/migration/V2__friend_requests_table.sql`       |
| Conversation type chỉ gồm `DIRECT` hoặc `GROUP`.                                     | Enum và DB check constraint.                                                                                                       | `src/main/java/com/chat_socket/enums/ConversationType.java`, `src/main/resources/db/migration/V4__conversations_table.sql`                 |
| Message type gồm `TEXT`, `IMAGE`, `FILE`, `SYSTEM`.                                  | Enum. DB migration comment nêu enum nhưng không có check constraint cho message type.                                              | `src/main/java/com/chat_socket/enums/MessageType.java`, `src/main/resources/db/migration/V5__messages_table.sql`                           |
| Participant role gồm `ADMIN`, `MEMBER`.                                              | Enum. DB migration comment nêu enum nhưng không có check constraint cho role.                                                      | `src/main/java/com/chat_socket/enums/ParticipantRole.java`, `src/main/resources/db/migration/V6__participants_table.sql`                   |
| Direct conversation cần đúng một member.                                             | `createDirectConversation` reject nếu `memberIds.size() != 1`.                                                                     | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                                  |
| Không tạo direct conversation với chính mình.                                        | Service trả `400`.                                                                                                                 | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                                  |
| Group creator là `ADMIN`, member còn lại là `MEMBER`.                                | Service tạo participant role tương ứng.                                                                                            | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                                  |
| Sender được mark read sau khi gửi message.                                           | `markSenderAsRead` cập nhật `lastReadMessage` và `lastReadAt`.                                                                     | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`                                                                       |
| Lấy user info của người khác yêu cầu là bạn bè.                                      | Nếu không phải current user và không có friendship, service throw `NotFoundException`.                                             | `src/main/java/com/chat_socket/service/impl/UserServiceImpl.java`                                                                          |

## 11. Database schema

Schema được định nghĩa bởi Flyway migrations trong `src/main/resources/db/migration`.

### `users`

Source: `src/main/resources/db/migration/V1__users_table.sql`.

| Column            | Type           | Nullable      | Default             | Constraint/index             | Ý nghĩa nghiệp vụ                                                |
| ----------------- | -------------- | ------------- | ------------------- | ---------------------------- | ---------------------------------------------------------------- |
| `id`              | `UUID`         | No            | `gen_random_uuid()` | Primary key                  | User id. Entity dùng UUIDv7 generator khi persist qua Hibernate. |
| `username`        | `VARCHAR(50)`  | No            | Không có            | Unique, index lower username | Username đăng nhập/search.                                       |
| `hashed_password` | `VARCHAR(255)` | No            | Không có            | Không có                     | Password đã hash bằng BCrypt.                                    |
| `first_name`      | `VARCHAR(70)`  | No            | Không có            | Không có                     | Tên.                                                             |
| `last_name`       | `VARCHAR(30)`  | No            | Không có            | Không có                     | Họ.                                                              |
| `normalized_name` | `VARCHAR(120)` | No            | Không có            | `idx_users_normalized_name`  | Tên đã normalize để search.                                      |
| `email`           | `VARCHAR(255)` | No            | Không có            | Unique                       | Email.                                                           |
| `avatar_url`      | `VARCHAR(500)` | Yes           | Không có            | Không có                     | URL avatar.                                                      |
| `avatar_id`       | `VARCHAR(100)` | Yes           | Không có            | Không có                     | Avatar id.                                                       |
| `bio`             | `TEXT`         | Yes           | Không có            | Không có                     | Bio.                                                             |
| `phone`           | `VARCHAR(20)`  | Yes           | Không có            | Không có                     | Phone.                                                           |
| `created_at`      | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                     | Thời điểm tạo. Entity khai báo non-null.                         |
| `updated_at`      | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                     | Thời điểm cập nhật. Entity khai báo non-null.                    |

### `friend_requests`

Source: `src/main/resources/db/migration/V2__friend_requests_table.sql`.

| Column         | Type           | Nullable      | Default             | Constraint/index                                                                   | Ý nghĩa nghiệp vụ                                                     |
| -------------- | -------------- | ------------- | ------------------- | ---------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `id`           | `UUID`         | No            | `gen_random_uuid()` | Primary key                                                                        | Friend request id.                                                    |
| `from_user_id` | `UUID`         | No            | Không có            | FK `users(id)` ON DELETE CASCADE, indexes                                          | User gửi request.                                                     |
| `to_user_id`   | `UUID`         | No            | Không có            | FK `users(id)` ON DELETE CASCADE, indexes                                          | User nhận request.                                                    |
| `message`      | `VARCHAR(300)` | Yes           | Không có            | Không có                                                                           | Message gửi kèm.                                                      |
| `status`       | `VARCHAR(20)`  | No            | `'PENDING'`         | Check `PENDING`, `ACCEPTED`, `REJECTED`; unique `(from_user_id,to_user_id,status)` | Trạng thái request.                                                   |
| `responded_at` | `TIMESTAMP`    | Yes           | Không có            | Không có                                                                           | Thời điểm phản hồi. Source hiện tại chưa set field này trong service. |
| `created_at`   | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Index theo from/to/status/created_at                                               | Thời điểm tạo.                                                        |
| `updated_at`   | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Index theo from/to/status/created_at                                               | Thời điểm cập nhật.                                                   |

Constraints/index đáng chú ý:

| Tên                                 | Mô tả                                                  |
| ----------------------------------- | ------------------------------------------------------ |
| `chk_friend_request_users_distinct` | `from_user_id <> to_user_id`.                          |
| `uq_friend_requests_pending_pair`   | Unique pending request theo cặp user bất kể chiều gửi. |

### `friends`

Source: `src/main/resources/db/migration/V3__friends_table.sql`.

| Column       | Type        | Nullable      | Default             | Constraint/index                   | Ý nghĩa nghiệp vụ         |
| ------------ | ----------- | ------------- | ------------------- | ---------------------------------- | ------------------------- |
| `id`         | `UUID`      | No            | `gen_random_uuid()` | Primary key                        | Friendship row id.        |
| `user_a_id`  | `UUID`      | No            | Không có            | FK `users(id)`, index, unique pair | User A đã normalize.      |
| `user_b_id`  | `UUID`      | No            | Không có            | FK `users(id)`, index, unique pair | User B đã normalize.      |
| `created_at` | `TIMESTAMP` | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                           | Thời điểm tạo friendship. |
| `updated_at` | `TIMESTAMP` | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                           | Thời điểm cập nhật.       |

Constraints:

| Tên                 | Mô tả                            |
| ------------------- | -------------------------------- |
| `chk_user_distinct` | `user_a_id <> user_b_id`.        |
| `chk_user_order`    | `user_a_id < user_b_id`.         |
| `uq_friends`        | Unique `(user_a_id, user_b_id)`. |

### `conversations`

Source: `src/main/resources/db/migration/V4__conversations_table.sql`, `src/main/resources/db/migration/V5__messages_table.sql`.

| Column             | Type           | Nullable      | Default             | Constraint/index                     | Ý nghĩa nghiệp vụ                                                    |
| ------------------ | -------------- | ------------- | ------------------- | ------------------------------------ | -------------------------------------------------------------------- |
| `id`               | `UUID`         | No            | `gen_random_uuid()` | Primary key                          | Conversation id.                                                     |
| `type`             | `VARCHAR(20)`  | No            | Không có            | Check `DIRECT`, `GROUP`              | Loại conversation.                                                   |
| `group_name`       | `VARCHAR(255)` | Yes           | Không có            | Không có                             | Tên group.                                                           |
| `created_by`       | `UUID`         | Yes           | Không có            | FK `users(id)` ON DELETE SET NULL    | User tạo conversation.                                               |
| `direct_user_a_id` | `UUID`         | Yes           | Không có            | FK `users(id)` ON DELETE CASCADE     | Direct user A đã normalize.                                          |
| `direct_user_b_id` | `UUID`         | Yes           | Không có            | FK `users(id)` ON DELETE CASCADE     | Direct user B đã normalize.                                          |
| `last_message_id`  | `UUID`         | Yes           | Không có            | FK `messages(id)` ON DELETE SET NULL | Message mới nhất. Constraint được thêm trong V5.                     |
| `last_message_at`  | `TIMESTAMP`    | Yes           | Không có            | `idx_conversations_last_message_at`  | Thời điểm last message hoặc thời điểm tạo conversation theo service. |
| `created_at`       | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                             | Thời điểm tạo.                                                       |
| `updated_at`       | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                             | Thời điểm cập nhật.                                                  |

Constraints/index đáng chú ý:

| Tên                                      | Mô tả                                                                                                                            |
| ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `chk_conversation_users_for_type`        | `DIRECT` phải có `direct_user_a_id`, `direct_user_b_id` và `direct_user_a_id < direct_user_b_id`; `GROUP` không có direct users. |
| `chk_conversation_direct_users_distinct` | Direct users khác nhau.                                                                                                          |
| `uq_direct_conversations_pair`           | Unique direct conversation theo cặp direct users.                                                                                |

### `messages`

Source: `src/main/resources/db/migration/V5__messages_table.sql`.

| Column            | Type           | Nullable      | Default             | Constraint/index                                                                              | Ý nghĩa nghiệp vụ                                      |
| ----------------- | -------------- | ------------- | ------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| `id`              | `UUID`         | No            | `gen_random_uuid()` | Primary key                                                                                   | Message id.                                            |
| `conversation_id` | `UUID`         | No            | Không có            | FK `conversations(id)` ON DELETE CASCADE; index `(conversation_id, created_at DESC, id DESC)` | Conversation chứa message.                             |
| `sender_id`       | `UUID`         | No            | Không có            | FK `users(id)` ON DELETE CASCADE                                                              | User gửi.                                              |
| `content`         | `TEXT`         | Yes           | Không có            | Không có                                                                                      | Nội dung message.                                      |
| `type`            | `VARCHAR(20)`  | No            | `'TEXT'`            | Không có check constraint trong SQL hiện tại                                                  | Message type.                                          |
| `attachment_url`  | `VARCHAR(500)` | Yes           | Không có            | Không có                                                                                      | Attachment URL.                                        |
| `is_deleted`      | `BOOLEAN`      | No            | `false`             | Không có                                                                                      | Soft delete flag. Source query loại `is_deleted=true`. |
| `created_at`      | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Index message pagination                                                                      | Thời điểm tạo.                                         |
| `updated_at`      | `TIMESTAMP`    | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                                                                                      | Thời điểm cập nhật.                                    |

### `participants`

Source: `src/main/resources/db/migration/V6__participants_table.sql`.

| Column                 | Type          | Nullable      | Default             | Constraint/index                                    | Ý nghĩa nghiệp vụ                              |
| ---------------------- | ------------- | ------------- | ------------------- | --------------------------------------------------- | ---------------------------------------------- |
| `conversation_id`      | `UUID`        | No            | Không có            | PK, FK `conversations(id)` ON DELETE CASCADE, index | Conversation id.                               |
| `user_id`              | `UUID`        | No            | Không có            | PK, FK `users(id)` ON DELETE CASCADE, index         | Participant user id.                           |
| `role`                 | `VARCHAR(20)` | Yes trong SQL | `'MEMBER'`          | Không có check constraint trong SQL hiện tại        | Participant role. Entity khai báo non-null.    |
| `last_read_message_id` | `UUID`        | Yes           | Không có            | FK `messages(id)` ON DELETE SET NULL, index         | Message cuối đã đọc.                           |
| `last_read_at`         | `TIMESTAMP`   | Yes           | Không có            | Không có                                            | Thời điểm đọc cuối.                            |
| `joined_at`            | `TIMESTAMP`   | Yes trong SQL | `CURRENT_TIMESTAMP` | Không có                                            | Thời điểm join.                                |
| `left_at`              | `TIMESTAMP`   | Yes           | Không có            | Active participant yêu cầu null.                    |
| `archived_at`          | `TIMESTAMP`   | Yes           | Không có            | Không có                                            | Thời điểm archive. Source hiện tại chưa dùng.  |
| `deleted_at`           | `TIMESTAMP`   | Yes           | Không có            | Partial index active participant                    | Active participant yêu cầu null.               |
| `muted_until`          | `TIMESTAMP`   | Yes           | Không có            | Không có                                            | Mute đến thời điểm. Source hiện tại chưa dùng. |

Primary key là `(conversation_id, user_id)`.

### `sessions`

Source: `src/main/resources/db/migration/V7__sessions_table.sql`.

| Column          | Type           | Nullable | Default  | Constraint/index                              | Ý nghĩa nghiệp vụ                                                     |
| --------------- | -------------- | -------- | -------- | --------------------------------------------- | --------------------------------------------------------------------- |
| `user_id`       | `UUID`         | No       | Không có | Primary key, FK `users(id)` ON DELETE CASCADE | Mỗi user có một session refresh token hiện hành theo schema hiện tại. |
| `refresh_token` | `VARCHAR(128)` | No       | Không có | Unique                                        | Refresh token random hex.                                             |
| `expires_at`    | `TIMESTAMPTZ`  | No       | Không có | `idx_sessions_expires_at`                     | Thời điểm hết hạn refresh token.                                      |

### Relationships

| Relationship                      | Mô tả                                                                                                        |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| User với session                  | `sessions.user_id` là primary key và foreign key tới `users.id`; mỗi user có tối đa một session theo schema. |
| User với friend request           | `friend_requests.from_user_id` và `to_user_id` đều FK tới `users.id`.                                        |
| User với friends                  | `friends.user_a_id` và `user_b_id` đều FK tới `users.id`; cặp được normalize.                                |
| Conversation với participants     | `participants.conversation_id` FK tới `conversations.id`.                                                    |
| Conversation với messages         | `messages.conversation_id` FK tới `conversations.id`.                                                        |
| Conversation với last message     | `conversations.last_message_id` FK tới `messages.id` và `last_message_at` lưu thời điểm message mới nhất.    |
| Participant với last read message | `participants.last_read_message_id` FK tới `messages.id`, dùng để mark seen và unread count.                 |

## 12. Repository/query behavior

| Repository                | Query/hành vi chính                                                                                                                                                  | File/path                                                               |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `UserRepository`          | Kiểm tra tồn tại username/email, tìm user theo username, search username bằng `LOWER(u.username) LIKE :username` giới hạn 20 trong service.                          | `src/main/java/com/chat_socket/repository/UserRepository.java`          |
| `FriendRepository`        | Kiểm tra friendship theo normalized pair, delete friendship theo pair, lấy friendship của user, lấy danh sách friend có search và cursor theo `createdAt`.           | `src/main/java/com/chat_socket/repository/FriendRepository.java`        |
| `FriendRequestRepository` | Check pending request hai chiều, lấy sent/received pending requests, lấy pending requests giữa current user và nhiều users.                                          | `src/main/java/com/chat_socket/repository/FriendRequestRepository.java` |
| `ConversationRepository`  | Tìm direct conversation theo normalized pair, lấy active conversation ids của user, cursor theo `COALESCE(lastMessageAt, updatedAt)`, fetch details bằng join fetch. | `src/main/java/com/chat_socket/repository/ConversationRepository.java`  |
| `MessageRepository`       | Count unread messages, lấy latest messages, lấy messages trước cursor theo `createdAt`.                                                                              | `src/main/java/com/chat_socket/repository/MessageRepository.java`       |
| `ParticipantRepository`   | Kiểm tra participant tồn tại, kiểm tra active participant, lấy active user ids theo conversation.                                                                    | `src/main/java/com/chat_socket/repository/ParticipantRepository.java`   |
| `SessionRepository`       | Xóa session theo refresh token, tìm session theo refresh token.                                                                                                      | `src/main/java/com/chat_socket/repository/SessionRepository.java`       |

### Cursor pagination

| Use case      | Cursor field                         | Sort                                                                         | File/path                                                                                                                                    |
| ------------- | ------------------------------------ | ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Friends       | `FriendEntity.createdAt`             | `createdAt DESC, id DESC`                                                    | `src/main/java/com/chat_socket/repository/FriendRepository.java`                                                                             |
| Conversations | `COALESCE(lastMessageAt, updatedAt)` | `COALESCE(...) DESC, id DESC`                                                | `src/main/java/com/chat_socket/repository/ConversationRepository.java`                                                                       |
| Messages      | `MessageEntity.createdAt`            | `createdAt DESC, id DESC`, response reverse thành thứ tự tăng dần trong page | `src/main/java/com/chat_socket/repository/MessageRepository.java`, `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java` |

`PaginationUtils` fetch `limit + 1` item để xác định `nextCursor`. Cursor được format bằng `DateTimeFormatter.ISO_LOCAL_DATE_TIME`.

### Unread count

`MessageRepository.countUnreadMessagesByConversation` đếm message:

| Điều kiện                                            | Ý nghĩa                              |
| ---------------------------------------------------- | ------------------------------------ |
| `m.conversation.id IN :conversationIds`              | Chỉ đếm các conversation đang xét.   |
| `m.deleted = false`                                  | Không đếm message đã delete.         |
| `m.sender.id <> :userId`                             | Không đếm message do chính user gửi. |
| `p.lastReadAt IS NULL OR m.createdAt > p.lastReadAt` | Message sau thời điểm user đọc cuối. |

## 13. Error handling

`GlobalExceptionHandler` nằm tại `src/main/java/com/chat_socket/config/GlobalExceptionHandler.java`.

| Exception                                          | HTTP status | Response `data`           | Response `message`      |
| -------------------------------------------------- | ----------: | ------------------------- | ----------------------- |
| Validation error `MethodArgumentNotValidException` |       `400` | Map field to message      | `Validation failed`     |
| `SignInException`                                  |       `400` | `null`                    | Exception message       |
| `NotFoundException`                                |       `404` | `null`                    | Exception message       |
| `UnAuthorizedException`                            |       `401` | `null`                    | Exception message       |
| `ForbiddenException`                               |       `403` | `null`                    | Exception message       |
| `FriendPermissionException`                        |       `403` | `{ "notFriends": [...] }` | Exception message       |
| `BadRequestException`                              |       `400` | `null`                    | Exception message       |
| Unhandled `Exception`                              |       `500` | `ex.getMessage()`         | `Internal server error` |

Security filter tự ghi JSON error response bằng `BaseResponse<Object>` trong `src/main/java/com/chat_socket/security/SecurityFilter.java`, không đi qua `GlobalExceptionHandler`.

## 14. Security notes

| Chủ đề                            | Ghi nhận theo source hiện tại                                                                                                                                                     |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Stateless session                 | `SecurityServerConfig` dùng `SessionCreationPolicy.STATELESS`.                                                                                                                    |
| JWT Bearer token                  | HTTP endpoint private yêu cầu `Authorization: Bearer <token>`.                                                                                                                    |
| Password hashing                  | BCrypt strength `10`.                                                                                                                                                             |
| CORS                              | `SecurityServerConfig` hard-code allowed origin `http://localhost:3000`, allowed credentials `true`, headers `*`, methods `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.     |
| WebSocket allowed origin          | `WebSocketConfig` dùng `chat-socket.client-url`.                                                                                                                                  |
| Method-level security             | `@EnableMethodSecurity` bật trong `SecurityServerConfig`; `@PreAuthorize` dùng ở create conversation và send message endpoints.                                                   |
| Public HTTP endpoints             | Auth routes bypass `SecurityFilter`; `SecurityServerConfig` permit `"/v1/auth/**"` và `"/ws*"`. Với servlet path, `SecurityFilter` bypass thực tế `/api/v1/auth/**` và `/api/ws`. |
| Private HTTP endpoints            | Các endpoint khác yêu cầu authenticated user.                                                                                                                                     |
| WebSocket CONNECT auth            | STOMP `CONNECT` cần native header `Authorization: Bearer <token>`.                                                                                                                |
| WebSocket SUBSCRIBE authorization | Chỉ kiểm tra participant permission cho destination `/topic/conversations/{conversationId}/messages`.                                                                             |
| Refresh token storage             | Refresh token được lưu DB trong `sessions` và gửi qua HTTP-only secure SameSite none cookie.                                                                                      |
| Role/authority                    | `Security.getUserAuthentication` tạo authentication với `Collections.emptyList()`, source hiện tại chưa có role/authority cho HTTP auth.                                          |

## 15. Realtime architecture bằng mô tả chữ

1. Client mở kết nối STOMP tới endpoint WebSocket thực tế theo cấu hình local là `/api/ws`.

2. Client gửi native header `Authorization: Bearer <accessToken>` trong frame `CONNECT`.

3. `SocketChannelInterceptor` verify JWT, load user từ database và set authenticated principal cho WebSocket session.

4. Khi `SessionConnectEvent` phát sinh, `SocketEventListener` lấy `UserSecurity` từ principal.

5. `UserOnlineRegistry` lưu session id vào Redis key `chat-socket:online-user-sessions:{userId}` và thêm user id vào Redis set `chat-socket:online-users`.

6. Server broadcast danh sách online users qua `/topic/online-users`.

7. Client subscribe snapshot online users qua `/app/online-users` hoặc listen broadcast `/topic/online-users`.

8. Client subscribe conversation message topic `/topic/conversations/{conversationId}/messages`.

9. Với message topic, `SocketChannelInterceptor` kiểm tra user là active participant của conversation.

10. Khi client gửi message qua REST API thành công, service lưu `messages`, cập nhật `conversations.last_message_id`, `conversations.last_message_at`, và mark sender as read.

11. Sau transaction commit, `SocketPublisher` publish `MessageDto` tới `/topic/conversations/{conversationId}/messages`.

12. Sau transaction commit, `SocketPublisher` gửi `ConversationEvent` tới từng participant qua `/user/queue/conversations`, với unread count tính riêng từng user.

13. Khi client gọi mark seen thành công, service cập nhật `participants.last_read_message_id` và `last_read_at`.

14. Sau transaction commit, server publish `ConversationSeenEvent` tới `/topic/conversations/{conversationId}/seen`.

15. Sau mark seen, server gửi `ConversationEvent` với `unreadCount=0` tới user-specific queue của user vừa mark seen.

16. Khi user disconnect, `UserOnlineRegistry` xóa session id khỏi Redis set của user; nếu user không còn session nào, xóa user khỏi set online users.

17. Sau disconnect, server broadcast lại danh sách online users qua `/topic/online-users`.

## 16. Quy ước response và pagination

### `BaseResponse<T>`

Mọi controller trong source hiện tại trả `ResponseEntity<BaseResponse<...>>`. Format:

| Field     | Type     | Ý nghĩa                                                                   |
| --------- | -------- | ------------------------------------------------------------------------- |
| `data`    | Generic  | Payload thành công hoặc payload lỗi tùy handler.                          |
| `message` | `String` | Message nghiệp vụ hoặc message lỗi. Có thể `null`.                        |
| `status`  | `int`    | HTTP status code. Controller dùng `ResponseEntity.status(body.status())`. |

### `PaginationRequest(limit, cursor, offset)`

`PaginationRequest` gồm:

| Field    | Type      | Default |           Max | Ý nghĩa                                                   |
| -------- | --------- | ------: | ------------: | --------------------------------------------------------- |
| `limit`  | `Integer` |    `50` |         `100` | Số item trả về. Nếu lớn hơn `100`, source clamp về `100`. |
| `cursor` | `String`  |  `null` | Không áp dụng | Cursor datetime cho conversation/message pagination.      |
| `offset` | `Integer` |     `0` | Không áp dụng | Offset cho search user/friend.                            |

Nếu `limit < 1`, service throw `BadRequestException("Limit must be greater than 0.")`.
Nếu `offset < 0`, service throw `BadRequestException("Offset must be greater than or equal to 0.")`.

Cursor parsing trong `PaginationUtils`:

| Format thử parse                         | Kết quả                                                 |
| ---------------------------------------- | ------------------------------------------------------- |
| `DateTimeFormatter.ISO_LOCAL_DATE_TIME`  | Trả `LocalDateTime`.                                    |
| `DateTimeFormatter.ISO_OFFSET_DATE_TIME` | Parse offset datetime rồi convert sang `LocalDateTime`. |
| Không parse được                         | Throw `BadRequestException("Cursor is invalid.")`.      |

### `PaginationResponse<T>(messages, nextCursor, nextOffset)`

| Field        | Type      | Ý nghĩa                                                                 |
| ------------ | --------- | ----------------------------------------------------------------------- |
| `messages`   | `List<T>` | Danh sách item hiện tại. Field name là `messages` cho mọi generic type. |
| `nextCursor` | `String`  | Cursor cho page tiếp theo nếu còn dữ liệu; `null` nếu hết.              |
| `nextOffset` | `Integer` | Offset cho page tiếp theo nếu còn dữ liệu; `null` nếu hết.              |

`PaginationUtils.toCursorResponse` fetch `limit + 1`. Nếu số item fetch lớn hơn limit, lấy item cuối trong page làm `nextCursor`.

`PaginationUtils.toOffsetResponse` fetch `limit + 1`. Nếu số item fetch lớn hơn limit, `nextOffset = offset + limit`.
`PaginationResponse` bỏ qua field null khi serialize, nên cursor response không trả `nextOffset` và offset response không trả `nextCursor`.

## 17. Những điểm cần lưu ý/khoảng trống hiện tại

| Chủ đề                        | Ghi nhận theo source hiện tại                                                                                                                                                                     |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Test coverage                 | Chỉ thấy `src/test/java/com/chat_socket/ChatSocketApplicationTests.java` với test `contextLoads()`. Chưa thấy unit/integration test cho service, repository, security, WebSocket hoặc controller. |
| Swagger/OpenAPI               | Chưa xác định trong source hiện tại. `pom.xml` không có dependency Springdoc/OpenAPI/Swagger.                                                                                                     |
| Logging/monitoring            | Chưa xác định trong source hiện tại. Không thấy cấu hình logging/metrics/tracing riêng ngoài mặc định Spring Boot.                                                                                |
| Production secrets            | `application.yaml` hiện có secret JWT và database password local. Khi deploy production cần externalize secret/password/client URL.                                                               |
| CORS config                   | `SecurityServerConfig` hard-code `http://localhost:3000`; `WebSocketConfig` dùng `chat-socket.client-url`. Cần đồng bộ khi deploy.                                                                |
| WebSocket security matcher    | `SecurityServerConfig` permit `"/ws*"` nhưng `SecurityFilter` bypass `"/api/ws"`. Cần kiểm tra lại khi thay đổi servlet path hoặc security matcher.                                               |
| Refresh token/cookie          | Cookie refresh token có `secure(true)` và `sameSite("none")`; local HTTP frontend/backend có thể cần HTTPS hoặc proxy phù hợp để browser gửi cookie đúng kỳ vọng.                                 |
| Multiple sessions             | Bảng `sessions` dùng `user_id` làm primary key, nên mỗi user chỉ có một refresh token row hiện hành theo schema.                                                                                  |
| File upload                   | Source hiện tại chỉ có `attachmentUrl` trong message DTO/entity, chưa thấy endpoint upload file.                                                                                                  |
| Message deletion              | Entity có `is_deleted`, repository filter deleted message, nhưng chưa thấy endpoint delete/restore message.                                                                                       |
| Participant lifecycle         | Entity có `leftAt`, `archivedAt`, `deletedAt`, `mutedUntil`; hiện có API rời nhóm và delete group, chưa thấy API archive/mute.                                                                    |
| Friend request `responded_at` | Cột tồn tại nhưng service accept/decline hiện chưa set `respondedAt`.                                                                                                                             |
| DB enum constraints           | `messages.type` và `participants.role` có comment enum trong SQL nhưng chưa có check constraint trong migrations hiện tại.                                                                        |

## 18. Phụ lục

### Danh sách enum

| Enum                  | Values                                       | File/path                                                      |
| --------------------- | -------------------------------------------- | -------------------------------------------------------------- |
| `ConversationType`    | `DIRECT`, `GROUP`                            | `src/main/java/com/chat_socket/enums/ConversationType.java`    |
| `FriendRequestStatus` | `PENDING`, `ACCEPTED`, `REJECTED`            | `src/main/java/com/chat_socket/enums/FriendRequestStatus.java` |
| `FriendStatus`        | `NONE`, `SELF`, `FRIEND`, `SENT`, `RECEIVED` | `src/main/java/com/chat_socket/enums/FriendStatus.java`        |
| `MessageType`         | `TEXT`, `IMAGE`, `FILE`, `SYSTEM`            | `src/main/java/com/chat_socket/enums/MessageType.java`         |
| `ParticipantRole`     | `ADMIN`, `MEMBER`                            | `src/main/java/com/chat_socket/enums/ParticipantRole.java`     |

### Danh sách route constants

Source: `src/main/java/com/chat_socket/constant/RouteApi.java`.

| Constant           | Value              |
| ------------------ | ------------------ |
| `API_V1`           | `/v1`              |
| `AUTH_API`         | `/v1/auth`         |
| `USER_API`         | `/v1/user`         |
| `FRIEND_API`       | `/v1/friend`       |
| `MESSAGE_API`      | `/v1/message`      |
| `CONVERSATION_API` | `/v1/conversation` |

Khi kết hợp với `spring.mvc.servlet.path=/api`, URL REST đầy đủ có prefix `/api/v1`.

### Danh sách socket channels

Source: `src/main/java/com/chat_socket/constant/SocketChannel.java`.

| Constant                  | Value                        | Ý nghĩa                                                                                                         |
| ------------------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `APP`                     | `/app`                       | Application destination prefix.                                                                                 |
| `TOPIC`                   | `/topic`                     | Simple broker topic prefix.                                                                                     |
| `QUEUE`                   | `/queue`                     | Simple broker queue prefix.                                                                                     |
| `CONVERSATION`            | `/conversations`             | Segment conversation.                                                                                           |
| `MESSAGE`                 | `/messages`                  | Segment messages.                                                                                               |
| `SEEN`                    | `/seen`                      | Segment seen.                                                                                                   |
| `CONVERSATION_QUEUE`      | `/queue/conversations`       | User-specific queue destination. Khi dùng `convertAndSendToUser`, client subscribe `/user/queue/conversations`. |
| `MESSAGE_TOPIC`           | `/conversations/%s/messages` | `SocketEmitter.emit` tự thêm `/topic`, thành `/topic/conversations/{id}/messages`.                              |
| `CONVERSATION_SEEN_TOPIC` | `/conversations/%s/seen`     | `SocketEmitter.emit` tự thêm `/topic`, thành `/topic/conversations/{id}/seen`.                                  |

### Danh sách Redis keys

Source: `src/main/java/com/chat_socket/constant/Redis.java`.

| Constant                   | Value                               | Ý nghĩa                                          |
| -------------------------- | ----------------------------------- | ------------------------------------------------ |
| `ONLINE_USERS_KEY`         | `chat-socket:online-users`          | Redis set chứa user ids đang online.             |
| `USER_SESSIONS_KEY_PREFIX` | `chat-socket:online-user-sessions:` | Prefix Redis set chứa session ids của từng user. |

### Danh sách task command

Source: `Taskfile.yml`.

| Task                   | Command chính                                                 | Ý nghĩa                          |
| ---------------------- | ------------------------------------------------------------- | -------------------------------- |
| `format`               | `mvnw spotless:check`                                         | Check format.                    |
| `format_fix`           | `mvnw spotless:apply`                                         | Apply format.                    |
| `start_infra`          | `docker compose -f deployment/docker-compose/infra.yml up -d` | Start PostgreSQL và Redis.       |
| `stop_infra`           | `docker compose ... stop` và `rm -f`                          | Stop và remove infra containers. |
| `restart_infra`        | `stop_infra`, `sleep`, `start_infra`                          | Restart infra.                   |
| `sleep`                | `timeout` trên Windows hoặc `sleep` trên OS khác              | Delay helper.                    |
| `run`                  | `mvnw spring-boot:run`                                        | Chạy backend local.              |
| `flyway_repair`        | `mvnw flyway:repair ...`                                      | Repair Flyway metadata local.    |
| `compile_without_test` | `mvnw -q -DskipTests clean compile`                           | Compile bỏ qua test.             |

### Danh sách file quan trọng nên đọc khi maintain dự án

| File/path                                                                 | Lý do                                                         |
| ------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `pom.xml`                                                                 | Version Java, Spring Boot, dependency, plugin format.         |
| `Taskfile.yml`                                                            | Command vận hành local.                                       |
| `deployment/docker-compose/infra.yml`                                     | PostgreSQL/Redis local infra.                                 |
| `src/main/resources/application-template.yaml`                            | Config mẫu cho môi trường.                                    |
| `src/main/resources/db/migration/*`                                       | Database schema chuẩn.                                        |
| `src/main/java/com/chat_socket/config/SecurityServerConfig.java`          | HTTP security, CORS, stateless session, password encoder.     |
| `src/main/java/com/chat_socket/security/SecurityFilter.java`              | JWT Bearer authentication cho REST.                           |
| `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java`    | JWT và authorization cho WebSocket/STOMP.                     |
| `src/main/java/com/chat_socket/config/WebSocketConfig.java`               | STOMP endpoint, broker prefixes, inbound channel interceptor. |
| `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`         | Auth/session/refresh token behavior.                          |
| `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`       | Friend request business rules.                                |
| `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java` | Conversation, messages pagination, mark seen.                 |
| `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`      | Direct/group message sending flow.                            |
| `src/main/java/com/chat_socket/socket/SocketPublisher.java`               | Realtime publish behavior after transaction commit.           |
| `src/main/java/com/chat_socket/utils/PaginationUtils.java`                | Cursor pagination rules.                                      |
