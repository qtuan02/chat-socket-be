# Chat Socket Backend Documentation

Tài liệu này mô tả backend `chat-socket` dựa trên source code, file cấu hình, Flyway migration SQL và dependency hiện có trong repository. Những thông tin chưa thể xác định từ source hiện tại được ghi rõ là `Chưa xác định trong source hiện tại`.

## 1. Tổng quan dự án

`chat-socket` là backend Java Spring Boot cho ứng dụng chat realtime. Dự án cung cấp REST API cho authentication, user profile, friend management, conversation, message, upload file và realtime event qua WebSocket/STOMP.

Các chức năng chính đang có trong source:

| Nhóm chức năng  | Mô tả                                                                                                  | File/path liên quan                                                                                                                 |
| --------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| Đăng ký         | Tạo user mới, hash password bằng BCrypt, lưu vào bảng `users`.                                         | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`, `src/main/java/com/chat_socket/entity/UserEntity.java`           |
| Đăng nhập       | Kiểm tra username/password, phát access token JWT, tạo refresh token và lưu session.                   | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`, `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java` |
| Refresh token   | Đọc refresh token từ cookie `refreshToken`, kiểm tra bảng `sessions`, phát access token mới.           | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                                   |
| Đăng xuất       | Xóa session theo refresh token và clear cookie.                                                        | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                                   |
| Quản lý profile | Lấy/cập nhật thông tin user hiện tại, đổi mật khẩu, lấy thông tin user khác kèm trạng thái quan hệ.    | `src/main/java/com/chat_socket/service/impl/UserServiceImpl.java`                                                                   |
| Kết bạn         | Gửi, nhận, chấp nhận, từ chối, hủy lời mời kết bạn và xóa bạn bè.                                      | `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`                                                                 |
| Conversation    | Tạo conversation trực tiếp/group, lấy danh sách/1 conversation, lấy message theo cursor, mark seen, quản lý group (đổi tên, thêm/xóa/rời member, xóa group). | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                           |
| Message         | Gửi direct/group message, sửa (chỉ TEXT), xóa mềm (`is_deleted`).                                      | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`                                                                |
| Upload file     | Upload file qua multipart, lưu local disk, serve lại qua static resource endpoint.                     | `src/main/java/com/chat_socket/controller/UploadController.java`, `src/main/java/com/chat_socket/service/FileStorage.java`         |
| Realtime        | Online users, typing indicator, message created/updated/deleted, conversation updated/removed, seen — qua STOMP. | `src/main/java/com/chat_socket/socket/*`, `src/main/java/com/chat_socket/config/WebSocketConfig.java`                               |

Base REST API khi chạy với cấu hình hiện tại là `/api/v1`, vì `spring.mvc.servlet.path=/api` trong `src/main/resources/application.yaml` và route version `/v1` trong `src/main/java/com/chat_socket/constant/RouteApi.java`. Hai route không nằm dưới `/v1`: `RouteApi.HEALTH_API` (`/health-check`) và `RouteApi.FILES` (`/files`), nên URL thực tế của chúng là `/api/health-check` và `/api/files/**`.

## 2. Tech stack và dependency chính

| Công nghệ/thư viện              | Vai trò trong dự án                                                               | File hoặc package liên quan                                                                                                             |
| -------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Java 25                         | Runtime và source compatibility theo Maven property.                              | `pom.xml`                                                                                                                               |
| Spring Boot 4.0.6               | Framework chính của backend.                                                      | `pom.xml`, `src/main/java/com/chat_socket/ChatSocketApplication.java`                                                                   |
| Spring Web MVC                  | REST API controller và request handling.                                          | `pom.xml`, `src/main/java/com/chat_socket/controller/*`                                                                                 |
| Spring Security                 | HTTP authentication, authorization, method-level security.                        | `pom.xml`, `src/main/java/com/chat_socket/config/SecurityServerConfig.java`, `src/main/java/com/chat_socket/security/*`                 |
| JWT `jjwt` 0.13.0                | Sinh và verify access token JWT, sinh refresh token dạng random hex.              | `pom.xml`, `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`                                                             |
| Spring Data JPA/Hibernate       | Entity mapping, repository, transaction, persistence. `ConversationEntity.participants` dùng Hibernate `@SQLRestriction`. | `pom.xml`, `src/main/java/com/chat_socket/entity/*`, `src/main/java/com/chat_socket/repository/*`                                       |
| PostgreSQL                      | Database chính cho user, friendship, conversation, message, participant, session. | `pom.xml`, `deployment/docker-compose/infra.yml`, `src/main/resources/db/migration/*`                                                   |
| Flyway 12.6.0                   | Database migration, hiện có `V1`–`V9`.                                            | `pom.xml`, `src/main/resources/db/migration/V1__users_table.sql` đến `V9__drop_unused_participant_columns.sql`                          |
| Redis                           | Lưu online user registry theo WebSocket session (Redis set + Lua script cleanup). | `pom.xml`, `src/main/java/com/chat_socket/config/RedisCacheConfig.java`, `src/main/java/com/chat_socket/socket/UserOnlineRegistry.java`, `src/main/java/com/chat_socket/constant/Redis.java` |
| Spring WebSocket/STOMP          | Realtime communication, STOMP endpoint, broker topic/queue.                       | `pom.xml`, `src/main/java/com/chat_socket/config/WebSocketConfig.java`, `src/main/java/com/chat_socket/socket/*`                        |
| MapStruct 1.6.3                 | Mapping DTO/entity.                                                               | `pom.xml`, `src/main/java/com/chat_socket/mapper/*`, `src/main/java/com/chat_socket/config/GlobalMapperConfig.java`                     |
| Lombok                          | Sinh getter/setter/constructor cho entity và embeddable.                          | `pom.xml`, `src/main/java/com/chat_socket/entity/*`                                                                                     |
| Maven Wrapper                   | Chạy Maven qua `mvnw` hoặc `mvnw.cmd`.                                            | `mvnw`, `mvnw.cmd`, `Taskfile.yml`                                                                                                       |
| Spotless + Palantir Java Format | Check/apply format Java trong Maven build.                                        | `pom.xml`, `Taskfile.yml`                                                                                                               |
| uuid-creator 6.1.1              | Sinh UUIDv7 cho id entity (Hibernate generator) và tên file upload (`UuidCreator.getTimeOrderedEpoch()`). | `pom.xml`, `src/main/java/com/chat_socket/utils/UUIDv7.java`, `src/main/java/com/chat_socket/utils/UUIDv7Generator.java`, `src/main/java/com/chat_socket/service/FileStorage.java` |
| Bean Validation                 | Validate request DTO.                                                             | `pom.xml`, `src/main/java/com/chat_socket/dto/*Request.java`                                                                            |
| Jackson (`jackson-databind` 2.21.3 + Jackson 3 `tools.jackson` từ Spring Boot 4) | `JacksonConfig` khai báo `ObjectMapper` (dùng trong `SecurityFilter`). Mọi `Instant` trả về API được serialize theo format UTC cố định (`uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'`) qua `InstantJsonSerializer` (`@JacksonComponent`, Jackson 3), áp dụng cho cả JSON response lẫn payload STOMP. | `src/main/java/com/chat_socket/config/JacksonConfig.java`, `src/main/java/com/chat_socket/config/InstantJsonSerializer.java`, `src/main/java/com/chat_socket/constant/TimeFormat.java` |

## 3. Cấu trúc thư mục source

| Package/thư mục          | Vai trò                                                                                      | File/path liên quan                                                                                              |
| ------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `com.chat_socket`        | Entry point và cấu hình properties.                                                          | `src/main/java/com/chat_socket/ChatSocketApplication.java`, `src/main/java/com/chat_socket/ApplicationYaml.java` |
| `controller`             | REST API layer. Controller nhận request, gọi service và trả `BaseResponse`.                  | `src/main/java/com/chat_socket/controller/*`                                                                     |
| `service`                | Interface service cho auth, user, friend, conversation, message, JWT; và `FileStorage` (class, không phải interface) cho upload. | `src/main/java/com/chat_socket/service/*`                                                                        |
| `service.impl`           | Business logic implementation.                                                               | `src/main/java/com/chat_socket/service/impl/*`                                                                   |
| `repository`             | Data access layer bằng Spring Data JPA và JPQL query.                                        | `src/main/java/com/chat_socket/repository/*`                                                                     |
| `entity`                 | JPA entities ánh xạ các bảng database.                                                       | `src/main/java/com/chat_socket/entity/*`                                                                         |
| `dto`                    | Request/response payload, pagination, socket event payload.                                  | `src/main/java/com/chat_socket/dto/*`                                                                            |
| `mapper`                 | MapStruct mapper.                                                                             | `src/main/java/com/chat_socket/mapper/*`                                                                         |
| `security`               | HTTP filter, WebSocket auth, method permission checks, upload download-header filter.        | `src/main/java/com/chat_socket/security/*`                                                                       |
| `socket`                 | Realtime controller, emitter, publisher, online registry, transaction synchronization.       | `src/main/java/com/chat_socket/socket/*`                                                                         |
| `config`                 | Spring configuration cho security, WebSocket, Redis, Jackson, MapStruct, static files, exception handling. | `src/main/java/com/chat_socket/config/*`                                                                         |
| `constant`               | Route, table name, Redis key, socket channel, time-format constants.                         | `src/main/java/com/chat_socket/constant/*`                                                                       |
| `enums`                  | Domain enum.                                                                                  | `src/main/java/com/chat_socket/enums/*`                                                                          |
| `exception`              | Custom runtime exception.                                                                     | `src/main/java/com/chat_socket/exception/*`                                                                      |
| `utils`                  | Helper cho normalize, pagination, Redis, security, UUIDv7.                                   | `src/main/java/com/chat_socket/utils/*`                                                                          |
| `resources/db/migration` | Flyway migration SQL.                                                                        | `src/main/resources/db/migration/*`                                                                              |

## 4. Cách chạy dự án local

### Yêu cầu môi trường

| Thành phần | Yêu cầu                                                                    |
| ---------- | --------------------------------------------------------------------------- |
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
| ---------- | --------------------- | --------: | ------------------------------------------------------ |
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

Config nằm trong `src/main/resources/application.yaml`, mỗi key đọc từ biến môi trường kèm giá trị default (không còn file `application-template.yaml`).

| Config                                              | Giá trị default trong `application.yaml`          | Ý nghĩa                                                                                    |
| ----------------------------------------------------- | ---------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `spring.mvc.servlet.path` (env `SPRING_MVC_SERVLET_PATH`) | `/api`                                            | Prefix servlet path cho REST API. Kết hợp route `/v1` → base REST API `/api/v1`.           |
| `spring.application.name`                           | `chat-socket`                                     | Tên Spring application.                                                                    |
| `spring.datasource.url`                             | `jdbc:postgresql://localhost:5432/chat-socket-db` | JDBC URL PostgreSQL.                                                                       |
| `spring.datasource.username`                        | `admin`                                           | Username database local.                                                                   |
| `spring.datasource.password`                        | `123456`                                          | Password database local.                                                                   |
| `spring.data.redis.url`                             | `redis://localhost:6379`                          | Redis connection URL (không còn tách `host`/`port`).                                       |
| `spring.jpa.hibernate.ddl-auto`                     | `none`                                            | Hibernate không tự sinh schema. Schema do Flyway quản lý.                                  |
| `spring.jpa.show-sql`                                | `true`                                            | Log SQL ra console.                                                                        |
| `spring.jpa.properties.hibernate.jdbc.time_zone`    | `UTC`                                             | JDBC time zone cố định UTC (đi cùng cột `TIMESTAMPTZ`, xem mục 11).                        |
| `spring.servlet.multipart.max-file-size`            | `10MB`                                            | Giới hạn dung lượng 1 file upload.                                                          |
| `spring.servlet.multipart.max-request-size`         | `10MB`                                            | Giới hạn dung lượng request multipart.                                                      |
| `server.port` (env `PORT`)                          | `8089`                                            | Port backend.                                                                              |
| `chat-socket.access-token-secret`                   | hex string dài trong `application.yaml`           | Secret ký JWT access token (`ApplicationYaml.accessTokenSecret`).                          |
| `chat-socket.access-token-ttl`                      | `15`                                              | TTL access token theo phút (`ApplicationYaml.accessTokenTtl`, `long`).                     |
| `chat-socket.refresh-token-ttl`                     | `14`                                              | TTL refresh token theo ngày (`ApplicationYaml.refreshTokenTtl`, `long`).                   |
| `chat-socket.client-url` (env `CHAT_SOCKET_CLIENT_URL`) | `http://localhost:3000,http://localhost:3007`     | Danh sách origin frontend, phân tách bởi dấu phẩy. Map vào `ApplicationYaml.clientUrl(): List<String>`. Dùng cho cả CORS và WebSocket allowed origin. |
| `chat-socket.upload-dir` (env `CHAT_SOCKET_UPLOAD_DIR`) | `./uploads`                                       | Thư mục local lưu file upload (`ApplicationYaml.uploadDir`).                               |
| `chat-socket.public-url` (env `CHAT_SOCKET_PUBLIC_URL`) | `http://localhost:8089/api`                       | Base URL public để build link file trả về client, đã bao gồm servlet path (`ApplicationYaml.publicUrl`). |

`ApplicationYaml` (record, `@ConfigurationProperties(prefix = "chat-socket")`): `accessTokenSecret`, `accessTokenTtl`, `refreshTokenTtl`, `clientUrl: List<String>`, `uploadDir`, `publicUrl`.

CORS **không hard-code**: `SecurityServerConfig.corsConfigurationSource()` set `allowedOrigins` trực tiếp từ `applicationYaml.clientUrl()` (cùng list dùng cho WebSocket allowed origin), `allowCredentials=true`, headers `*`, methods `GET,POST,PUT,PATCH,DELETE,OPTIONS`.

Các giá trị không nên hard-code khi deploy production:

| Config                            | Lý do                                                        |
| ----------------------------------- | --------------------------------------------------------------- |
| `spring.datasource.password`      | Credential database phải externalize qua env/secret manager. |
| `spring.datasource.username`      | Nên externalize theo môi trường.                             |
| `spring.datasource.url`           | Phụ thuộc môi trường deploy.                                 |
| `chat-socket.access-token-secret` | Secret ký JWT phải đủ mạnh và không commit vào source (hiện có default trong `application.yaml`). |
| `chat-socket.client-url`          | Phải cấu hình đúng origin frontend production (ảnh hưởng cả CORS lẫn WebSocket). |
| `chat-socket.public-url`          | Phải trỏ đúng domain public khi build link file trả về client. |
| `spring.jpa.show-sql`             | Production thường không bật SQL log mặc định.                |

## 6. Authentication và session

### Sign up

| Bước | Mô tả                                                                                               | File/path                                                                                                                |
| ---- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| 1    | Client gọi `POST /api/v1/auth/sign-up` với `SignUpRequest`.                                         | `src/main/java/com/chat_socket/controller/AuthController.java`                                                           |
| 2    | Validate `username`, `email`, `password`, `firstName`, `lastName` không blank; `email` đúng format. | `src/main/java/com/chat_socket/dto/SignUpRequest.java`                                                                   |
| 3    | Service kiểm tra username đã tồn tại bằng `UserRepository.existsByUsername`.                        | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                        |
| 4    | Map request sang `UserEntity`, hash password bằng `PasswordEncoder`.                                | `src/main/java/com/chat_socket/mapper/UserMapper.java`, `src/main/java/com/chat_socket/config/SecurityServerConfig.java` |
| 5    | Lưu user vào bảng `users`. `UserEntity` tự cập nhật `normalizedName` trước persist/update.          | `src/main/java/com/chat_socket/entity/UserEntity.java`                                                                   |
| 6    | Trả `204 NO_CONTENT` nếu tạo thành công.                                                            | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                        |

Nếu username đã tồn tại, service trả `409 CONFLICT` với message `User already exists`.

### Sign in

| Bước | Mô tả                                                                                            | File/path                                                                                                           |
| ---- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| 1    | Client gọi `POST /api/v1/auth/sign-in` với `SignInRequest`.                                      | `src/main/java/com/chat_socket/controller/AuthController.java`                                                      |
| 2    | Service tìm user theo username. Nếu không có hoặc password không match, throw `SignInException`. | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                   |
| 3    | Access token JWT được sinh bằng user id ở JWT subject.                                           | `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`                                                    |
| 4    | Refresh token được sinh bằng 64 random bytes và encode hex, kết quả dài 128 ký tự.               | `src/main/java/com/chat_socket/service/impl/JwtServiceImpl.java`                                                    |
| 5    | Session lưu vào bảng `sessions` theo `user_id`, `refresh_token`, `expires_at` (`Instant`).       | `src/main/java/com/chat_socket/entity/SessionEntity.java`, `src/main/resources/db/migration/V7__sessions_table.sql` |
| 6    | Refresh token được set vào cookie `refreshToken`.                                                | `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`                                                   |
| 7    | Response body trả `AuthResponse(accessToken)`.                                                   | `src/main/java/com/chat_socket/dto/AuthResponse.java`                                                               |

Cookie refresh token: name `refreshToken`, `httpOnly=true`, `secure=true`, `sameSite=none`, `maxAge = chat-socket.refresh-token-ttl` ngày.

### Access token

| Thuộc tính | Mô tả                             |
| ---------- | ------------------------------------ |
| Subject    | `userId.toString()`               |
| Issued at  | `Instant.now()`                   |
| Expiration | `now + access-token-ttl` phút     |
| Signature  | HMAC SHA-256 (`Jwts.SIG.HS256`)   |
| Secret     | `chat-socket.access-token-secret` |

### Refresh token

Không phải JWT. Sinh bằng `SecureRandom`, 64 bytes, encode hex (128 ký tự). Lưu ở bảng `sessions`, gửi qua cookie `refreshToken`.

### Sign out

Đọc cookie `refreshToken` → nếu thiếu, throw `UnAuthorizedException("Token not found.")` (`401`) → `SessionRepository.deleteByRefreshToken` → clear cookie (`maxAge=Duration.ZERO`) → `200 OK`, message `Logout successful.`

### Refresh access token

Đọc cookie `refreshToken` → tìm session; không có → `ForbiddenException("Token expired or invalid.")` (`403`) → nếu `expiresAt` (`Instant`) đã qua, xóa session + clear cookie + throw cùng exception → sinh access token mới theo `session.userId` → trả `AuthResponse(accessToken)`.

### Đổi mật khẩu

`PATCH /api/v1/user/me/password`, body `ChangePasswordRequest(currentPassword, newPassword)`.

| Bước | Mô tả                                                                                  |
| ---- | ----------------------------------------------------------------------------------------- |
| 1    | Validate `currentPassword` not blank; `newPassword` not blank, 8–72 ký tự.              |
| 2    | Service kiểm tra `currentPassword` khớp hash hiện tại, nếu sai → `BadRequestException("Current password is incorrect.")` |
| 3    | Nếu `newPassword` trùng `currentPassword` → `BadRequestException("New password must differ from current password.")` |
| 4    | Hash `newPassword` bằng `PasswordEncoder`, lưu user, trả `204 NO_CONTENT`.               |

File: `src/main/java/com/chat_socket/controller/UserController.java`, `src/main/java/com/chat_socket/service/impl/UserServiceImpl.java`.

### SecurityFilter

`SecurityFilter` (`src/main/java/com/chat_socket/security/SecurityFilter.java`) xử lý HTTP authentication:

| Hành vi               | Mô tả                                                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------------------------------- |
| Header đọc token      | `Authorization: Bearer <token>`                                                                           |
| Bypass filter (`shouldNotFilter`) | `OPTIONS`; URI khớp regex `/api/ws`; URI `equals("/api/health-check")`; URI `startsWith("/api/v1/auth")`; URI `startsWith("/api/files/")`. |
| Token thiếu           | Trả `401 UNAUTHORIZED`, message `Token not found.`                                                        |
| Token invalid/expired | Trả `403 FORBIDDEN`, message `Token expired or invalid.`                                                  |
| User không tồn tại    | Trả `404 NOT_FOUND`, message `User does not exist.`                                                       |
| Authenticated user    | Được đưa vào `SecurityContextHolder` dưới dạng `UsernamePasswordAuthenticationToken` chứa `UserSecurity`. |

Password encoder: `BCryptPasswordEncoder(10)` (`src/main/java/com/chat_socket/config/SecurityServerConfig.java`).

## 7. REST API documentation

Base URL REST API: `/api/v1` (trừ `/api/health-check` và `/api/files/**`, xem mục 1/18).

Response chung dùng `BaseResponse<T>`:

```json
{
  "data": {},
  "message": "string",
  "status": 200
}
```

### Auth API — base `/api/v1/auth`

| Method | URL                     | Controller/service                                  | Auth                                                       | Request               | Response                                                | Mô tả                                             | Validation chính                                                                      | Error có thể xảy ra                                                   |
| ------ | ------------------------- | ------------------------------------------------------- | ------------------------------------------------------------ | ------------------------ | ---------------------------------------------------------- | ---------------------------------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `POST` | `/api/v1/auth/sign-up`  | `AuthController.signUp`, `AuthServiceImpl.signUp`   | Public, bypass `SecurityFilter`                            | Body `SignUpRequest`  | `BaseResponse<String>`                                  | Tạo user mới.                                     | `username`, `email`, `password`, `firstName`, `lastName` không blank; `email` hợp lệ. | `400 Validation failed`, `409 User already exists`, `500`             |
| `POST` | `/api/v1/auth/sign-in`  | `AuthController.signIn`, `AuthServiceImpl.signIn`   | Public, bypass `SecurityFilter`                            | Body `SignInRequest`  | `BaseResponse<AuthResponse>`; set cookie `refreshToken` | Đăng nhập, trả access token, lưu refresh session. | `username`, `password` không blank.                                                   | `400 Username or password incorrect!`, `400 Validation failed`, `500` |
| `POST` | `/api/v1/auth/sign-out` | `AuthController.signOut`, `AuthServiceImpl.signOut` | Public theo filter; nghiệp vụ yêu cầu cookie refresh token | Cookie `refreshToken` | `BaseResponse<String>`                                  | Đăng xuất, xóa session và clear cookie.           | Cookie refresh token phải tồn tại.                                                    | `401 Token not found.`, `500`                                         |
| `POST` | `/api/v1/auth/refresh`  | `AuthController.refresh`, `AuthServiceImpl.refresh` | Public theo filter; nghiệp vụ yêu cầu cookie refresh token | Cookie `refreshToken` | `BaseResponse<AuthResponse>`                            | Refresh access token.                             | Cookie refresh token phải tồn tại và session chưa hết hạn.                            | `401 Token not found.`, `403 Token expired or invalid.`, `500`        |

### User API — base `/api/v1/user`

| Method  | URL                            | Controller/service                                              | Auth     | Request                  | Response                            | Mô tả                                                                          | Validation chính                                                                | Error có thể xảy ra                                                                                             |
| ------- | -------------------------------- | ------------------------------------------------------------------ | -------- | --------------------------- | -------------------------------------- | ------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `GET`   | `/api/v1/user/me`              | `UserController.getMe`, `UserServiceImpl.getUserProfile`       | Required | Không có                 | `BaseResponse<UserProfileDto>`      | Lấy profile user hiện tại.                                                     | Bearer token hợp lệ.                                                            | `401`, `403`, `404 User not found`, `500`                                                                       |
| `PATCH` | `/api/v1/user/me`              | `UserController.updateMe`, `UserServiceImpl.updateUserProfile` | Required | Body `UpdateUserRequest` | `BaseResponse<UserProfileDto>`      | Cập nhật profile user hiện tại.                                                | Size/email theo DTO; field required (username/firstName/lastName/email) gửi chuỗi rỗng thì service reject. | `400 Validation failed`, `400 Username already exists`, `400 Email already exists`, `404 User not found`, `500` |
| `PATCH` | `/api/v1/user/me/password`     | `UserController.changePassword`, `UserServiceImpl.changePassword` | Required | Body `ChangePasswordRequest` | `BaseResponse<Void>` (`204`)        | Đổi mật khẩu (xem mục 6).                                                      | `currentPassword` not blank; `newPassword` not blank, 8–72 ký tự.               | `400 Current password is incorrect.`, `400 New password must differ from current password.`, `404`, `500`      |
| `GET`   | `/api/v1/user?search=...`      | `UserController.searchUsers`, `UserServiceImpl.searchUsers`    | Required | Query `search`, `offset`, `limit` optional | `BaseResponse<PaginationResponse<UserSearchDto>>` | Search user theo username/tên chuẩn hóa (offset pagination), kèm trạng thái quan hệ. | Search rỗng/không match trả list rỗng; `offset >= 0`; `limit > 0`.               | `400 Offset must be greater than or equal to 0.`, `400 Limit must be greater than 0.`, `401`, `403`, `500`      |
| `GET`   | `/api/v1/user/{userId}`        | `UserController.getInfo`, `UserServiceImpl.getUserInfo`        | Required | Path `userId: UUID`      | `BaseResponse<UserInfoDto>`         | Lấy thông tin user theo id, trả kèm trạng thái quan hệ. **Không yêu cầu là bạn bè** để xem. | `userId` phải parse được UUID.                                                  | `404 User not found`, `401`, `403`, `500`                                                                       |

### Friend API — base `/api/v1/friend`

| Method   | URL                                     | Controller/service                                                                | Auth     | Request                           | Response                                      | Mô tả                                                                                      | Validation chính                                 | Error có thể xảy ra                                                                                                    |
| -------- | ------------------------------------------ | ------------------------------------------------------------------------------------- | -------- | ------------------------------------ | -------------------------------------------------- | ------------------------------------------------------------------------------------------- | --------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/api/v1/friend`                        | `FriendController.getListFriend`, `FriendServiceImpl.getListFriend`               | Required | Query `search`, `offset`, `limit` | `BaseResponse<PaginationResponse<FriendDto>>` | Search bạn bè theo username/tên chuẩn hóa bằng offset pagination.                          | `offset >= 0`; `limit > 0`.                      | `400 Offset must be greater than or equal to 0.`, `400 Limit must be greater than 0.`, `401`, `403`, `500`             |
| `GET`    | `/api/v1/friend/request`                | `FriendController.getListFriendRequest`, `FriendServiceImpl.getListFriendRequest` | Required | Không có                          | `BaseResponse<FriendRequestResponse>`         | Lấy pending sent/received friend requests của user hiện tại.                               | Bearer token hợp lệ.                             | `401`, `403`, `500`                                                                                                    |
| `POST`   | `/api/v1/friend/request`                | `FriendController.sendFriendRequest`, `FriendServiceImpl.sendFriendRequest`       | Required | Body `FriendSendRequest`          | `BaseResponse<String>`                        | Gửi friend request.                                                                        | `toUserId` required; `message` tối đa 300 ký tự. | `400 self request`, `404 User not found.`, `409 already friends`, `409 pending exists`, `400 Validation failed`, `500` |
| `POST`   | `/api/v1/friend/request/{requestId}/accept`  | `FriendController.acceptFriendRequest`, `FriendServiceImpl.acceptFriendRequest`   | Required | Path `requestId: UUID`            | `BaseResponse<UserSummaryDto>` (`201`)        | Chấp nhận pending request gửi tới current user, tạo row `friends`, đổi status `ACCEPTED`. | Chỉ recipient của request mới accept được; request phải `PENDING`. | `403 not authorized to accept` (khi sai người/không còn pending), `404 Friend request not found.`, `500`               |
| `POST`   | `/api/v1/friend/request/{requestId}/decline` | `FriendController.declineFriendRequest`, `FriendServiceImpl.declineFriendRequest` | Required | Path `requestId: UUID`            | `BaseResponse<String>` (`204`)                | Từ chối pending request gửi tới current user, đổi status `REJECTED`.                      | Chỉ recipient mới decline được; request phải `PENDING`. | `403 not authorized to decline`, `404 Friend request not found.`, `500`                                                |
| `DELETE` | `/api/v1/friend/request/{requestId}`    | `FriendController.cancelFriendRequest`, `FriendServiceImpl.cancelFriendRequest`   | Required | Path `requestId: UUID`            | `BaseResponse<String>` (`204`)                | Hủy (xóa) pending request do current user gửi.                                             | Chỉ sender mới cancel được; request phải `PENDING`. | `403 not authorized to cancel`, `404 Friend request not found.`, `500`                                                 |
| `DELETE` | `/api/v1/friend/{friendId}`             | `FriendController.deleteFriend`, `FriendServiceImpl.deleteFriend`                 | Required | Path `friendId: UUID`             | `BaseResponse<String>` (`204`)                | Xóa quan hệ bạn bè giữa current user và `friendId`.                                        | `friendId` phải là UUID, khác chính mình.        | `404 Friend not found.`, `401`, `403`, `500`                                                                           |

Không còn `FriendActionRequest`/`AcceptFriendResponse` — id request lấy từ path variable, accept trả thẳng `UserSummaryDto` của người vừa kết bạn.

### Conversation API — base `/api/v1/conversation`

| Method   | URL                                                        | Controller/service                                                                        | Auth                                                                          | Request                                           | Response                                            | Mô tả                                                                                                                             | Validation chính                                                                                    | Error có thể xảy ra                                                                                                                                                                                                              |
| -------- | ------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/api/v1/conversation`                                     | `ConversationController.getConversations`, `ConversationServiceImpl.getConversations`     | Required                                                                      | Query `limit`, `cursor`, `type` optional          | `BaseResponse<PaginationResponse<ConversationDto>>` | Lấy active conversations của current user, sort theo `COALESCE(lastMessageAt, updatedAt)` giảm dần.                               | `limit > 0`; `cursor` là chuỗi ISO-8601 Instant; `type` là `DIRECT` hoặc `GROUP` nếu có.           | `400 Cursor is invalid.`, `400 Limit must be greater than 0.`, `401`, `403`, `500`                                                                                                                                               |
| `POST`   | `/api/v1/conversation`                                     | `ConversationController.createConversation`, `ConversationServiceImpl.createConversation` | Required, `@PreAuthorize("@messageDirectPermission.canCreateConversation(#request)")` | Body `ConversationRequest`                        | `BaseResponse<ConversationDto>`                     | Tạo direct hoặc group conversation.                                                                                               | `type` required; `name` not blank (kể cả DIRECT — annotation không phân biệt type); `memberIds` not empty; DIRECT cần đúng 1 member. | `400`, `403 FriendPermissionException` (chỉ áp dụng cho GROUP, xem mục 10), `404 User/Member not found`, `500`                                                                                                                    |
| `GET`    | `/api/v1/conversation/{conversationId}`                    | `ConversationController.getConversation`, `ConversationServiceImpl.getConversation`       | Required                                                                      | Path `conversationId`                             | `BaseResponse<ConversationDto>`                     | Lấy 1 conversation theo id.                                                                                                        | Current user phải là active participant.                                                            | `404 Conversation not found.`, `403 not participant`, `500`                                                                                                                                                                       |
| `PATCH`  | `/api/v1/conversation/{conversationId}`                    | `ConversationController.updateGroup`, `ConversationServiceImpl.updateGroup`               | Required, `@PreAuthorize("@groupPermission.canManageGroup(#conversationId)")` | Body `UpdateGroupRequest`                         | `BaseResponse<ConversationDto>`                     | Cập nhật tên nhóm (chỉ `name`) cho group conversation.                                                                            | Current user phải là active participant role `ADMIN`; `name` không blank; chỉ áp dụng GROUP.        | `404 Group conversation not found.`, `403`, `400 Validation`                                                                                                                                                                     |
| `DELETE` | `/api/v1/conversation/{conversationId}`                    | `ConversationController.deleteGroup`, `ConversationServiceImpl.deleteGroup`               | Required, `@PreAuthorize("@groupPermission.canManageGroup(#conversationId)")` | Path `conversationId`                             | `BaseResponse<Void>` (`200`)                        | Ẩn group conversation khỏi danh sách của current user (`participants.deleted_at`); không xóa dữ liệu của participant khác.       | Current user phải là active participant role `ADMIN`.                                               | `404 Group conversation not found.`, `403 not participant/not admin`, `500`                                                                                                                                                      |
| `GET`    | `/api/v1/conversation/{conversationId}/messages`           | `ConversationController.getMessages`, `ConversationServiceImpl.getMessages`               | Required                                                                      | Path `conversationId`; query `limit`, `cursor`    | `BaseResponse<PaginationResponse<MessageDto>>`      | Lấy messages của conversation theo cursor.                                                                                        | Current user phải là active participant.                                                            | `404 Conversation not found.`, `403 not participant`, `400 cursor/limit`, `500`                                                                                                                                                  |
| `PATCH`  | `/api/v1/conversation/{conversationId}/seen`               | `ConversationController.markAsSeen`, `ConversationServiceImpl.markAsSeen`                 | Required                                                                      | Path `conversationId`                             | `BaseResponse<Void>`                                | Đánh dấu last message của conversation là đã đọc cho current user.                                                                | Current user phải là active participant.                                                            | `404 Conversation not found.`, `403 not participant`, `200 no messages`, `200 already marked`, `500`                                                                                                                             |
| `POST`   | `/api/v1/conversation/{conversationId}/members`            | `ConversationController.addGroupMembers`, `ConversationServiceImpl.addGroupMembers`       | Required                                                                      | Path `conversationId`, body `GroupMembersRequest` | `BaseResponse<ConversationDto>`                     | Thêm member vào nhóm (hoặc khôi phục member đã left/deleted).                                                                     | Active participant trong group (ADMIN/MEMBER đều được); mỗi memberId phải là bạn bè của current user. | `404 Group conversation not found.`, `403`, `403 FriendPermissionException`, `404 One or more members were not found.`, `400 memberIds required`                                                                                |
| `DELETE` | `/api/v1/conversation/{conversationId}/members/{memberId}` | `ConversationController.removeGroupMember`, `ConversationServiceImpl.removeGroupMember`   | Required                                                                      | Path `conversationId`, `memberId`                 | `BaseResponse<ConversationDto>`                     | Bỏ một member ra khỏi nhóm (set `leftAt`).                                                                                        | Chỉ ADMIN mới được remove; chỉ remove MEMBER; không dùng để tự leave.                               | `404 Group conversation not found.`, `404 Participant not found.`, `403 Only admins can manage group members.`, `400 You cannot remove an admin from this group.`, `400 You cannot remove yourself. Use leave endpoint instead.` |
| `POST`   | `/api/v1/conversation/{conversationId}/leave`              | `ConversationController.leaveGroup`, `ConversationServiceImpl.leaveGroup`                 | Required                                                                      | Path `conversationId`                             | `BaseResponse<Void>`                                | User tự rời nhóm.                                                                                                                 | Active participant; nếu là ADMIN phải còn ít nhất 1 ADMIN khác active.                              | `404 Group conversation not found.`, `400 You are the only admin.You can't leaving.` (nguyên văn message trong source)                                                                                                            |

### Message API — base `/api/v1/message`

| Method   | URL                          | Controller/service                                                            | Auth                                                                                         | Request                       | Response                          | Mô tả                                                                                                  | Validation chính                                                                                                                                                    | Error có thể xảy ra                                                                                                        |
| -------- | ------------------------------ | --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ | -------------------------------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `POST`   | `/api/v1/message/direct`   | `MessageController.sendDirectMessage`, `MessageServiceImpl.sendDirectMessage` | Required, `@PreAuthorize("@messageDirectPermission.canSendDirect(List.of(#request.recipientId()))")` | Body `DirectMessageRequest`   | `BaseResponse<MessageDto>` (`201`) | Gửi direct message; tìm hoặc tạo direct conversation với `recipientId`.                               | `recipientId` `@NotNull`; không gửi cho chính mình; `recipientId` phải là bạn bè; content hoặc attachmentUrl bắt buộc có ít nhất 1; `type` phải khớp attachment (xem mục 10). | `400 You cannot send a direct message to yourself.`, `400 Content or attachment is required.`, `403 not friend`, `404 User not found.`, `500` |
| `POST`   | `/api/v1/message/group`    | `MessageController.sendGroupMessage`, `MessageServiceImpl.sendGroupMessage`   | Required, `@PreAuthorize("@messageGroupPermission.canSendGroup(#request.conversationId())")` | Body `GroupMessageRequest`    | `BaseResponse<MessageDto>` (`201`) | Gửi message vào group conversation.                                                                    | `conversationId` `@NotNull`; conversation phải là GROUP; user phải là active participant; content/attachment/type như trên.                                       | `404 Group conversation not found.`, `403 not participant`, `400 Content or attachment is required.`, `500`               |
| `PATCH`  | `/api/v1/message/{messageId}` | `MessageController.updateMessage`, `MessageServiceImpl.updateMessage`         | Required                                                                                          | Path `messageId`, body `UpdateMessageRequest` | `BaseResponse<MessageDto>`         | Sửa nội dung message (chỉ message của chính mình, chỉ type `TEXT`).                                    | `content` `@NotBlank`.                                                                                                                                              | `403 You can only edit your own messages.`, `400 Only text messages can be edited.`, `404 Message not found.`, `500`      |
| `DELETE` | `/api/v1/message/{messageId}` | `MessageController.deleteMessage`, `MessageServiceImpl.deleteMessage`         | Required                                                                                          | Path `messageId`              | `BaseResponse<Void>` (`204`)       | Xóa mềm message của chính mình (`is_deleted=true`); nếu là last message, conversation tính lại last message. | Không có body.                                                                                                                                                       | `403 You can only delete your own messages.`, `404 Message not found.`, `500`                                             |

Không còn `MessageRequest` chung — tách thành `DirectMessageRequest(recipientId, content, type, attachmentUrl)` và `GroupMessageRequest(conversationId, content, type, attachmentUrl)`.

### Upload API — base `/api/v1/upload`

| Method | URL                | Controller/service                              | Auth     | Request                                | Response                                | Mô tả                                                                                                   | Validation chính                                                                     | Error có thể xảy ra                                       |
| ------ | -------------------- | -------------------------------------------------- | -------- | ------------------------------------------ | ------------------------------------------- | ----------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `POST` | `/api/v1/upload`   | `UploadController.upload`, `FileStorage.store` | Required | Multipart, field `file`                | `BaseResponse<UploadResponse>` (`201`) | Upload 1 file, lưu local disk dưới `chat-socket.upload-dir`, sinh tên file UUIDv7 + extension gốc (đã sanitize). | File không rỗng; extension chỉ giữ nếu alphanumeric ≤10 ký tự, ngược lại bỏ extension. | `400 File is required.`, `413 File is too large (max 10MB).` |

`FileStorage.store`: tên file = `UuidCreator.getTimeOrderedEpoch()` (+ `.` + extension nếu hợp lệ); `url` trả về = `chat-socket.public-url` + `/files/` + tên file. Class có comment `ponytail: local disk; swap for S3/Cloudinary when running more than one instance` — chỉ chạy đúng khi backend là 1 instance duy nhất (không share disk).

### Static file serving

| Route            | Auth      | Mô tả                                                                                                                    | File/path                                                                              |
| ------------------ | ----------- | ---------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `GET /api/files/{name}` | Public (`permitAll`) | Serve file tĩnh từ `chat-socket.upload-dir` qua Spring resource handler mapped ở `/files/**`.                          | `src/main/java/com/chat_socket/config/StaticFilesConfig.java`, `src/main/java/com/chat_socket/constant/RouteApi.java` (`FILES = "/files"`) |

Mọi response cho request có URI bắt đầu `/api/files/` bị `FileDownloadHeadersFilter` gắn thêm header `Content-Disposition: attachment` và `X-Content-Type-Options: nosniff` để chặn stored-XSS khi mở trực tiếp file HTML/SVG do user upload (không ảnh hưởng nhúng qua `<img>`/`<video>`). File: `src/main/java/com/chat_socket/security/FileDownloadHeadersFilter.java`.

### Health API

| Method | URL                  | Controller           | Auth   | Response                       | Mô tả                                       |
| ------ | ---------------------- | ----------------------- | -------- | --------------------------------- | ---------------------------------------------- |
| `GET`  | `/api/health-check` | `HealthController`   | Public | `BaseResponse<String>` `"OK"`, message `"Server is healthy"`, status `200` | Health check. **Lưu ý:** `RouteApi.HEALTH_API = "/health-check"` không nằm dưới `/v1`, nên URL thực tế là `/api/health-check`, không phải `/api/v1/health-check`. |

## 8. DTO request/response

### DTO chung

| DTO                     | Field        | Type      | Required/optional | Validation                                                    | Ý nghĩa                                                                            |
| ------------------------- | -------------- | ----------- | -------------------- | ------------------------------------------------------------------ | --------------------------------------------------------------------------------------- |
| `BaseResponse<T>`       | `data`       | `T`       | Optional          | Không có                                                      | Payload response.                                                                  |
| `BaseResponse<T>`       | `message`    | `String`  | Optional          | Không có                                                      | Message nghiệp vụ hoặc lỗi.                                                        |
| `BaseResponse<T>`       | `status`     | `int`     | Required          | Không có                                                      | HTTP status code được service/handler set.                                         |
| `PaginationRequest`     | `limit`      | `Integer` | Optional          | Service yêu cầu `> 0`; default `50`, max `100`.               | Số item muốn lấy.                                                                  |
| `PaginationRequest`     | `cursor`     | `String`  | Optional          | Parse bằng `Instant.parse(cursor.trim())` — bắt buộc có `Z`/offset, không có zone → `400`. | Cursor ISO-8601 Instant.                                                           |
| `PaginationRequest`     | `offset`     | `Integer` | Optional          | Service yêu cầu `>= 0`; default `0`.                          | Vị trí bắt đầu cho search user/friend.                                             |
| `PaginationResponse<T>` | `items`      | `List<T>` | Required          | Không có                                                      | Danh sách item (field tên `items`, không phải `messages`).                        |
| `PaginationResponse<T>` | `nextCursor` | `String`  | Optional          | Format `TimeFormat.UTC_MICROS` (`uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'`). | Cursor cho page tiếp theo ở cursor pagination. `@JsonInclude(NON_NULL)` nên field bị ẩn nếu null. |
| `PaginationResponse<T>` | `nextOffset` | `Integer` | Optional          | Không có                                                      | Offset cho page tiếp theo ở offset pagination. Ẩn nếu null.                        |

### Auth DTO

| DTO             | Field         | Type     | Required/optional | Validation            | Ý nghĩa                                |
| ----------------- | --------------- | ---------- | -------------------- | ------------------------ | ------------------------------------------ |
| `AuthResponse`  | `accessToken` | `String` | Required          | Không có              | JWT access token.                      |
| `SignUpRequest` | `username`    | `String` | Required          | `@NotBlank`           | Username đăng ký.                      |
| `SignUpRequest` | `email`       | `String` | Required          | `@NotBlank`, `@Email` | Email đăng ký.                         |
| `SignUpRequest` | `password`    | `String` | Required          | `@NotBlank`           | Password thô, được hash trước khi lưu. |
| `SignUpRequest` | `firstName`   | `String` | Required          | `@NotBlank`           | Tên.                                   |
| `SignUpRequest` | `lastName`    | `String` | Required          | `@NotBlank`           | Họ.                                    |
| `SignInRequest` | `username`    | `String` | Required          | `@NotBlank`           | Username đăng nhập.                    |
| `SignInRequest` | `password`    | `String` | Required          | `@NotBlank`           | Password đăng nhập.                    |

### User DTO

| DTO                     | Field          | Type            | Required/optional | Validation                                                                      | Ý nghĩa                                        |
| ------------------------- | ---------------- | ----------------- | -------------------- | ------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| `UpdateUserRequest`     | `username`     | `String`        | Optional          | `@Size(max=50)`; service trim và không cho blank nếu field được gửi.            | Username mới.                                  |
| `UpdateUserRequest`     | `email`        | `String`        | Optional          | `@Email`, `@Size(max=255)`; service trim và không cho blank nếu field được gửi. | Email mới.                                     |
| `UpdateUserRequest`     | `firstName`    | `String`        | Optional          | `@Size(max=70)`; service trim và không cho blank nếu field được gửi.            | Tên mới.                                       |
| `UpdateUserRequest`     | `lastName`     | `String`        | Optional          | `@Size(max=30)`; service trim và không cho blank nếu field được gửi.            | Họ mới.                                        |
| `UpdateUserRequest`     | `avatarUrl`    | `String`        | Optional          | `@Size(max=500)`                                                                | URL avatar. Blank → `null`.                    |
| `UpdateUserRequest`     | `avatarId`     | `String`        | Optional          | `@Size(max=100)`                                                                | ID avatar. Blank → `null`.                     |
| `UpdateUserRequest`     | `bio`          | `String`        | Optional          | Không có                                                                        | Bio. Blank → `null`.                           |
| `UpdateUserRequest`     | `phone`        | `String`        | Optional          | `@Size(max=20)`                                                                 | Số điện thoại. Blank → `null`.                 |
| `ChangePasswordRequest` | `currentPassword` | `String`     | Required          | `@NotBlank`                                                                     | Mật khẩu hiện tại.                              |
| `ChangePasswordRequest` | `newPassword`  | `String`        | Required          | `@NotBlank`, `@Size(min=8, max=72)`                                             | Mật khẩu mới.                                  |
| `UserProfileDto`        | `id`           | `UUID`          | Required          | Không có                                                                        | User id.                                       |
| `UserProfileDto`        | `username`     | `String`        | Required          | Không có                                                                        | Username.                                      |
| `UserProfileDto`        | `firstName`    | `String`        | Required          | Không có                                                                        | Tên.                                           |
| `UserProfileDto`        | `lastName`     | `String`        | Required          | Không có                                                                        | Họ.                                            |
| `UserProfileDto`        | `email`        | `String`        | Required          | Không có                                                                        | Email.                                         |
| `UserProfileDto`        | `avatarUrl`    | `String`        | Optional          | Không có                                                                        | URL avatar.                                    |
| `UserProfileDto`        | `bio`          | `String`        | Optional          | Không có                                                                        | Bio.                                           |
| `UserProfileDto`        | `phone`        | `String`        | Optional          | Không có                                                                        | Phone.                                         |
| `UserInfoDto`           | `id`, `username`, `firstName`, `lastName`, `email`, `avatarUrl`, `bio`, `phone` | tương ứng | Required/Optional | Không có                                                                        | Giống `UserProfileDto`.                        |
| `UserInfoDto`           | `joinedAt`     | `Instant`       | Required          | Không có                                                                        | Thời điểm tạo user.                            |
| `UserInfoDto`           | `statusFriend` | `FriendStatus`  | Required          | Không có                                                                        | `NONE`, `SELF`, `FRIEND`, `SENT`, `RECEIVED`.  |
| `UserInfoDto`           | `requestId`    | `UUID`          | Optional          | Không có                                                                        | Pending friend request id nếu `statusFriend` là `SENT`/`RECEIVED`. |
| `UserSearchDto`         | `id`, `username`, `firstName`, `lastName`, `avatarUrl` | tương ứng | Required/Optional | Không có                                                                        | Kết quả search user.                           |
| `UserSearchDto`         | `joinedAt`     | `Instant`       | Required          | Không có                                                                        | User created time.                             |
| `UserSearchDto`         | `statusFriend` | `FriendStatus`  | Required          | Không có                                                                        | `NONE`, `SELF`, `FRIEND`, `SENT`, `RECEIVED`.  |
| `UserSearchDto`         | `requestId`    | `UUID`          | Optional          | Không có                                                                        | Pending friend request id nếu có.              |
| `UserSummaryDto`        | `id`, `username`, `firstName`, `lastName`, `avatarUrl` | tương ứng | Required/Optional | Không có                                                                        | Thông tin rút gọn của user, dùng cho response accept friend request và `FriendRequestDto.user`. |

### Friend DTO

| DTO                     | Field              | Type                | Required/optional | Validation       | Ý nghĩa                                                           |
| ------------------------- | -------------------- | --------------------- | -------------------- | ------------------- | ----------------------------------------------------------------- |
| `FriendSendRequest`     | `toUserId`         | `UUID`              | Required          | `@NotNull`       | User nhận lời mời.                                                |
| `FriendSendRequest`     | `message`          | `String`             | Optional          | `@Size(max=300)` | Message lời mời. Entity trim trước persist/update.                |
| `FriendDto`             | `id`               | `UUID`              | Required          | Không có         | Id của friend user, không phải id row `friends`.                  |
| `FriendDto`             | `username`, `firstName`, `lastName`, `avatarUrl` | tương ứng | Required/Optional | Không có         | Thông tin friend.                                                 |
| `FriendDto`             | `joinedAt`         | `Instant`           | Required          | Không có         | `friendUser.createdAt`, không phải thời điểm kết bạn.             |
| `FriendRequestDto`      | `id`               | `UUID`              | Required          | Không có         | Id row `friend_requests`.                                         |
| `FriendRequestDto`      | `user`             | `UserSummaryDto`    | Required          | Không có         | Với sent request: recipient (`toUser`); với received request: sender (`fromUser`). |
| `FriendRequestDto`      | `message`          | `String`             | Optional          | Không có         | Message kèm request.                                              |
| `FriendRequestDto`      | `createdAt`        | `Instant`           | Required          | Không có         | Thời điểm gửi request.                                            |
| `FriendRequestResponse` | `sentRequests`     | `List<FriendRequestDto>` | Required      | Không có         | Pending requests do current user gửi.                             |
| `FriendRequestResponse` | `receivedRequests` | `List<FriendRequestDto>` | Required      | Không có         | Pending requests current user nhận.                               |

### Conversation DTO

| DTO                          | Field               | Type                               | Required/optional | Validation                                                   | Ý nghĩa                                                                |
| ------------------------------ | --------------------- | ------------------------------------- | -------------------- | ------------------------------------------------------------- | --------------------------------------------------------------------- |
| `ConversationRequest`        | `type`              | `ConversationType`                 | Required          | `@NotNull`                                                   | `DIRECT` hoặc `GROUP`.                                                 |
| `ConversationRequest`        | `name`              | `String`                           | Required theo DTO | `@NotBlank`                                                  | Group name. DIRECT vẫn phải gửi `name` non-blank (annotation không phân biệt type). |
| `ConversationRequest`        | `memberIds`         | `List<UUID>`                       | Required          | `@NotEmpty`; friend check chỉ áp dụng khi `type=GROUP` (mục 10). | Member được thêm vào conversation.                                     |
| `UpdateGroupRequest`         | `name`              | `String`                           | Required          | `@NotBlank`                                                  | Tên mới cho group.                                                     |
| `GroupMembersRequest`        | `memberIds`         | `List<UUID>`                       | Required          | `@NotEmpty`                                                  | Danh sách userId thêm vào nhóm.                                        |
| `ConversationDto`            | `id`                | `UUID`                             | Required          | Không có                                                     | Conversation id.                                                       |
| `ConversationDto`            | `type`              | `ConversationType`                 | Required          | Không có                                                     | Loại conversation.                                                     |
| `ConversationDto`            | `groupName`         | `String`                           | Optional          | Không có                                                     | Tên group. Null với direct.                                            |
| `ConversationDto`            | `lastMessage`       | `MessageDto`                       | Optional          | Không có                                                     | Last message payload.                                                  |
| `ConversationDto`            | `lastMessageAt`     | `Instant`                          | Optional          | Không có                                                     | Thời điểm last message hoặc thời điểm tạo conversation.                |
| `ConversationDto`            | `unreadCount`       | `long`                             | Required          | Không có                                                     | Số message chưa đọc của current user.                                  |
| `ConversationDto`            | `participants`      | `List<ConversationParticipantDto>` | Required          | Không có                                                     | Chỉ gồm active participant (`leftAt IS NULL AND deletedAt IS NULL`, xem mục 10). |
| `ConversationDto`            | *(đã bỏ)*           | —                                   | —                  | —                                                             | Không còn `createdById`, `directUserAId`, `directUserBId`, `lastMessageId`, `createdAt`, `updatedAt`. |
| `ConversationParticipantDto` | `userId`            | `UUID`                             | Required          | Không có                                                     | Participant user id.                                                   |
| `ConversationParticipantDto` | `username`          | `String`                           | Required          | Không có                                                     | Username participant.                                                  |
| `ConversationParticipantDto` | `firstName`, `lastName`, `avatarUrl` | tương ứng          | Required/Optional | Không có                                                     | Thông tin participant.                                                 |
| `ConversationParticipantDto` | `role`              | `ParticipantRole`                  | Required          | Không có                                                     | `ADMIN` hoặc `MEMBER`.                                                 |
| `ConversationParticipantDto` | `joinedAt`          | `Instant`                          | Required          | Không có                                                     | Thời điểm join.                                                        |
| `ConversationParticipantDto` | `lastReadMessageId` | `UUID`                             | Optional          | Không có                                                     | Message cuối cùng participant đã đọc.                                  |
| `ConversationParticipantDto` | `lastReadAt`        | `Instant`                          | Optional          | Không có                                                     | Thời điểm đọc cuối.                                                    |

### Message và realtime DTO

| DTO                        | Field               | Type            | Required/optional | Validation                                                                | Ý nghĩa                            |
| ----------------------------- | --------------------- | ----------------- | -------------------- | ------------------------------------------------------------------------- | ------------------------------------- |
| `DirectMessageRequest`     | `recipientId`       | `UUID`          | Required          | `@NotNull`                                                                | Recipient direct message.          |
| `DirectMessageRequest`     | `content`           | `String`        | Optional (xem mục 10) | Content hoặc attachmentUrl cần ít nhất 1.                              | Nội dung message.                  |
| `DirectMessageRequest`     | `type`              | `MessageType`   | Optional          | Null → default `TEXT`; phải là `IMAGE`/`FILE` nếu có attachment, `TEXT`/null nếu không. | Message type.                      |
| `DirectMessageRequest`     | `attachmentUrl`     | `String`        | Optional          | Không có                                                                  | URL attachment.                    |
| `GroupMessageRequest`      | `conversationId`    | `UUID`          | Required          | `@NotNull`                                                                | Conversation target.               |
| `GroupMessageRequest`      | `content`, `type`, `attachmentUrl` | tương ứng | tương ứng | Giống `DirectMessageRequest`.                                              | Nội dung/loại/attachment.          |
| `UpdateMessageRequest`     | `content`           | `String`        | Required          | `@NotBlank`                                                               | Nội dung mới.                      |
| `MessageDto`                | `id`, `conversationId`, `senderId` | `UUID` | Required          | Map từ entity.                                                            | Định danh.                         |
| `MessageDto`                | `content`           | `String`        | Optional          | Entity trim trước persist/update.                                        | Nội dung.                          |
| `MessageDto`                | `attachmentUrl`     | `String`        | Optional          | Không có                                                                  | Attachment URL.                    |
| `MessageDto`                | `type`              | `MessageType`   | Required          | Không có                                                                  | `TEXT`, `IMAGE`, `FILE`, `SYSTEM`. |
| `MessageDto`                | `createdAt`, `updatedAt` | `Instant`  | Required          | Không có                                                                  | Thời điểm tạo/cập nhật.            |
| `MessageEvent`              | `eventType`         | `String`        | Required          | Static factory: `message.created`, `message.updated`, `message.deleted`. | Loại event, wrap `MessageDto`.     |
| `MessageEvent`              | `message`           | `MessageDto`    | Required          | Không có                                                                  | Payload message.                   |
| `ConversationUpdatedEvent`  | `eventType`         | `String`        | Required          | Static factory set `conversation.updated`.                               | Loại event.                        |
| `ConversationUpdatedEvent`  | `conversation`      | `ConversationDto` | Required        | `unreadCount` được tính riêng cho user nhận event.                        | Full conversation row cho client upsert. |
| `ConversationRemovedEvent`  | `eventType`         | `String`        | Required          | Static factory set `conversation.removed`.                               | Conversation không còn trong list của user này (bị kick/rời/xóa). |
| `ConversationRemovedEvent`  | `conversationId`    | `UUID`          | Required          | Không có                                                                  | Conversation bị remove.            |
| `ConversationSeenEvent`     | `eventType`         | `String`        | Required          | Static factory set `conversation.seen`.                                  | Loại event.                        |
| `ConversationSeenEvent`     | `conversationId`, `seenByUserId`, `lastReadMessageId` | `UUID` | Required | Không có                                                              | Ai đã xem, xem đến message nào.    |
| `ConversationSeenEvent`     | `lastReadAt`        | `Instant`       | Required          | Không có                                                                  | Thời điểm seen.                    |
| `TypingEvent`               | `eventType`         | `String`        | Required          | Static factory set `typing`.                                             | Loại event.                        |
| `TypingEvent`               | `conversationId`, `userId` | `UUID`   | Required          | Không có                                                                  | Conversation và user đang gõ.      |

Không còn kiểu `ConversationEvent` chung như README cũ mô tả — thay bằng 3 record riêng: `ConversationUpdatedEvent`, `ConversationRemovedEvent`, `ConversationSeenEvent` (xem mục 9).

## 9. WebSocket/STOMP documentation

### Endpoint và broker

| Thành phần                     | Giá trị                  | File/path                                                                                                |
| --------------------------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------ |
| STOMP endpoint khai báo        | `/ws`                    | `src/main/java/com/chat_socket/config/WebSocketConfig.java`                                              |
| Servlet path config            | `/api`                   | `src/main/resources/application.yaml`                                                                    |
| URL thực tế theo source/filter | `/api/ws`                | `src/main/java/com/chat_socket/security/SecurityFilter.java` bypass `requestUri.matches("/api/ws")`      |
| Application destination prefix | `/app`                   | `src/main/java/com/chat_socket/constant/SocketChannel.java`                                              |
| Broker prefixes                | `/topic`, `/queue`       | `src/main/java/com/chat_socket/config/WebSocketConfig.java`                                              |
| Allowed origin cho endpoint    | `chat-socket.client-url` (List, không hard-code) | `src/main/java/com/chat_socket/config/WebSocketConfig.java`                             |

Với cấu hình local, client kết nối WebSocket tới `ws://<host>:8089/api/ws`.

Lưu ý: `SecurityServerConfig` permit matcher khai báo `"/ws*"` trong khi `SecurityFilter.shouldNotFilter` bypass chính xác `"/api/ws"` — vẫn là 2 cơ chế khác nhau (xem mục 17).

### Connect với JWT

Client gửi native STOMP header `Authorization: Bearer <accessToken>` trong frame `CONNECT`. `SocketChannelInterceptor` xử lý: đọc header → check prefix `Bearer ` → verify access token bằng `JwtService` → load user từ `UserRepository` → `accessor.setUser(authentication)`. File: `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java`.

### Subscribe authorization

| Destination prefix                          | Authorization                                                                                  |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `/topic/conversations/{conversationId}/**`  | User phải là active participant của conversation (`leftAt IS NULL AND deletedAt IS NULL`). Áp dụng cho **mọi** sub-destination — `messages`, `seen`, `typing` — không chỉ `messages` như trước. |

Destination không khớp prefix trên (vd. `/topic/online-users`, `/user/queue/conversations`) không bị interceptor kiểm tra participant. File: `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java`.

### Online users

| Mục               | Giá trị                                                                                                                       |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Subscribe mapping | `/app/online-users` (`@SubscribeMapping(SocketChannel.ONLINE_USERS)`)                                                          |
| Topic broadcast   | `/topic/online-users`                                                                                                         |
| Payload           | `Set<UUID>`                                                                                                                   |
| Khi broadcast     | User connect (`SessionConnectEvent`), disconnect (`SessionDisconnectEvent`).                                                  |
| Source            | `src/main/java/com/chat_socket/socket/SocketController.java`, `src/main/java/com/chat_socket/socket/SocketEventListener.java` |

Registry lưu ở Redis (`UserOnlineRegistry`), được clear toàn bộ 1 lần khi app khởi động (`ApplicationReadyEvent`) vì mọi session cũ đã chết cùng process trước.

### Typing indicator

| Mục         | Giá trị                                                                                          |
| -------------- | ----------------------------------------------------------------------------------------------------- |
| Client gửi   | STOMP `MESSAGE` tới `/app/conversations/{conversationId}/typing` (`@MessageMapping(SocketChannel.TYPING_MAPPING)`). |
| Server check | Caller phải là active participant của `conversationId`; nếu không, request bị bỏ qua (không lỗi).    |
| Server phát  | `TypingEvent(eventType="typing", conversationId, userId)` tới `/topic/conversations/{conversationId}/typing`. |
| Stop typing  | Không có event "stopped typing" — client tự hết hạn indicator vài giây sau event cuối (comment trong source). |

File: `src/main/java/com/chat_socket/socket/SocketController.java`.

### Message events

| Destination                                      | Payload      | Khi publish                                             |
| ---------------------------------------------------- | -------------- | ---------------------------------------------------------- |
| `/topic/conversations/{conversationId}/messages` | `MessageEvent` (`eventType` + `message: MessageDto`) | Sau khi gửi message (`message.created`), sửa (`message.updated`), xóa mềm (`message.deleted`). |

### Conversation và seen events

Tất cả các event này gửi **per-user** qua `/user/queue/conversations` (destination `SocketChannel.CONVERSATION_QUEUE = /queue/conversations`, gửi bằng `SimpMessagingTemplate.convertAndSendToUser`), không broadcast qua topic:

| Payload                   | Khi publish                                                                                                                          | Người nhận                                        |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| `ConversationUpdatedEvent` | Sau khi: gửi message mới; sửa/xóa message là last message của conversation; tạo/update group; add members; tạo/khôi phục direct conversation. | Mọi active participant của conversation, `unreadCount` tính riêng từng người. |
| `ConversationRemovedEvent` | Sau khi: current user tự xóa/ẩn group (`deleteGroup`); admin remove 1 member; user tự leave group.                                  | User bị remove khỏi conversation (chỉ người đó, hoặc chỉ current user với `deleteGroup`). |
| `ConversationSeenEvent`   | Sau khi mark seen thành công.                                                                                                        | **Mọi** active participant của conversation (không chỉ người vừa seen) — dùng để hiện read-receipt cho tất cả. |

Không còn topic `/topic/conversations/{id}/seen` như README cũ — `ConversationSeenEvent` đi qua queue riêng từng user như trên.

File: `src/main/java/com/chat_socket/socket/SocketPublisher.java`, `src/main/java/com/chat_socket/socket/SocketEmitter.java`.

### Publish after transaction commit

`SocketPublisher` build payload ngay trong transaction của caller, nhưng chỉ thực sự emit sau `afterCommit()` nếu có transaction synchronization đang active (qua `SocketSynchronization`); nếu không có transaction, emit ngay lập tức.

## 10. Business rules chính

| Rule                                                                                 | Mô tả                                                                                                                              | File/path                                                                                                                                  |
| --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Direct message qua `recipientId` yêu cầu là bạn bè.                                  | `MessageDirectPermission.canSendDirect(List.of(recipientId))` kiểm tra `friends`.                                                | `src/main/java/com/chat_socket/security/MessageDirectPermission.java`                                                                      |
| Tạo GROUP conversation yêu cầu toàn bộ `memberIds` là bạn bè.                        | `canCreateConversation` gọi `canSendDirect(memberIds)` khi `type=GROUP`.                                                          | `src/main/java/com/chat_socket/security/MessageDirectPermission.java`                                                                      |
| Tạo DIRECT conversation qua `POST /api/v1/conversation` **không** check bạn bè.      | `canCreateConversation` return `true` ngay khi `type=DIRECT`; service cũng không check friendship trong `createDirectConversation`. Khác với gửi direct message qua `recipientId` (có check). | `src/main/java/com/chat_socket/security/MessageDirectPermission.java`, `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java` |
| Gửi group message yêu cầu user là active participant của group.                      | Kiểm tra ở cả `MessageGroupPermission` và service.                                                                                | `src/main/java/com/chat_socket/security/MessageGroupPermission.java`, `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java` |
| Subscribe mọi destination `/topic/conversations/{id}/**` yêu cầu active participant. | Không chỉ riêng `messages` như trước — áp dụng cho `messages`, `seen`, `typing`.                                                  | `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java`                                                                     |
| Mọi path load conversation chỉ thấy active participant.                              | `ConversationEntity.participants` có `@SQLRestriction("left_at IS NULL AND deleted_at IS NULL")` ở tầng Hibernate — áp dụng cho **mọi** query load entity (không riêng socket event), mạnh hơn rule "subscribe cần active participant". | `src/main/java/com/chat_socket/entity/ConversationEntity.java`                                                                              |
| Message chỉ được sửa bởi chính sender, và chỉ khi type `TEXT`.                       | `updateMessage` throw `ForbiddenException`/`BadRequestException` tương ứng.                                                      | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`                                                                       |
| Message chỉ được xóa (mềm) bởi chính sender.                                          | `deleteMessage` set `is_deleted=true`; nếu là last message, `conversations.last_message_id`/`last_message_at` được tính lại từ message chưa xóa gần nhất và bắn `conversation.updated`. | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`                                                                       |
| Content/attachment message: cần ít nhất 1 trong 2; `type` phải khớp attachment.      | Không có attachment → `type` phải là `TEXT`/null; có attachment → `type` phải là `IMAGE`/`FILE`.                                 | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java` (`validateContent`)                                                   |
| Gửi message mới tự động khôi phục participant đã bị ẩn (soft-deleted) của conversation. | `createMessage` gọi `restoreDeletedParticipantsByConversationId` trước khi insert — user từng xóa group/direct sẽ thấy lại nó khi có tin nhắn mới. | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`, `src/main/java/com/chat_socket/repository/ParticipantRepository.java` |
| Direct conversation chuẩn hóa cặp user để tránh trùng, và được tái sử dụng/khôi phục thay vì tạo mới. | `UserPair`/entity `PrePersist` normalize; `findOrCreateDirectConversation` + `restoreDeletedParticipant` khi tạo qua REST.        | `src/main/java/com/chat_socket/entity/ConversationEntity.java`, `src/main/java/com/chat_socket/dto/UserPair.java`, `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java` |
| Friend relationship chuẩn hóa `user_a`/`user_b` theo thứ tự UUID.                    | `FriendEntity` normalize trước persist/update; DB check `user_a_id < user_b_id`.                                                 | `src/main/java/com/chat_socket/entity/FriendEntity.java`, `src/main/resources/db/migration/V3__friends_table.sql`                          |
| Friend request pending không được trùng giữa cùng một cặp user.                      | Service check pending hai chiều; DB unique index `uq_friend_requests_pending_pair`.                                              | `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`, `src/main/resources/db/migration/V2__friend_requests_table.sql`       |
| Chỉ recipient mới accept/decline được friend request; chỉ sender mới cancel được.    | Service so `toUser.id`/`fromUser.id` với current user + status phải `PENDING`.                                                   | `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`                                                                        |
| Xem thông tin user khác (`GET /api/v1/user/{userId}`) **không** yêu cầu là bạn bè.   | `getUserInfo` chỉ load user + tính `statusFriend`, không throw nếu không phải bạn bè.                                            | `src/main/java/com/chat_socket/service/impl/UserServiceImpl.java`                                                                          |
| Conversation type chỉ gồm `DIRECT`/`GROUP`; message type `TEXT`/`IMAGE`/`FILE`/`SYSTEM`; participant role `ADMIN`/`MEMBER`. | Enum, DB check constraint cho conversation type (không có check constraint cho message type/participant role). | `src/main/java/com/chat_socket/enums/*`, `src/main/resources/db/migration/V4__conversations_table.sql`                                     |
| Direct conversation cần đúng một member; không tạo với chính mình.                   | `createDirectConversation` reject nếu khác 1 hoặc trùng current user.                                                            | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                                  |
| Group creator là `ADMIN`, member còn lại là `MEMBER`.                                | Service tạo participant role tương ứng.                                                                                          | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                                  |
| Rời/remove khỏi group ADMIN cuối cùng bị chặn; remove chỉ do ADMIN, không remove ADMIN khác, không tự remove bản thân. | Xem bảng endpoint mục 7.                                                                                                          | `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java`                                                                  |
| Sender được mark read ngay sau khi gửi message.                                       | `markSenderAsRead` cập nhật `lastReadMessage`/`lastReadAt`.                                                                       | `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`                                                                       |

## 11. Database schema

Schema định nghĩa bởi Flyway migration `V1`–`V9` trong `src/main/resources/db/migration`. `V8__utc_timestamps.sql` đổi toàn bộ cột thời gian nghiệp vụ (trừ `sessions.expires_at`, đã là `TIMESTAMPTZ` từ `V7`) từ `TIMESTAMP` sang `TIMESTAMPTZ` (dữ liệu cũ được coi là UTC). `V9__drop_unused_participant_columns.sql` xóa `archived_at`, `muted_until` khỏi `participants`.

### `users`

Source: `V1__users_table.sql`, `V8__utc_timestamps.sql`.

| Column            | Type            | Nullable      | Default             | Constraint/index             | Ý nghĩa nghiệp vụ                                                |
| ------------------- | ----------------- | --------------- | --------------------- | ------------------------------- | -------------------------------------------------------------------- |
| `id`              | `UUID`          | No            | `gen_random_uuid()` | Primary key                  | User id. Entity dùng UUIDv7 generator khi persist qua Hibernate. |
| `username`        | `VARCHAR(50)`   | No            | Không có            | Unique                       | Username đăng nhập/search.                                       |
| `hashed_password` | `VARCHAR(255)`  | No            | Không có            | Không có                     | Password đã hash bằng BCrypt.                                    |
| `first_name`      | `VARCHAR(70)`   | No            | Không có            | Không có                     | Tên.                                                             |
| `last_name`       | `VARCHAR(30)`   | No            | Không có            | Không có                     | Họ.                                                              |
| `normalized_name` | `VARCHAR(120)`  | No            | Không có            | `idx_users_normalized_name`  | Tên đã normalize để search.                                      |
| `email`           | `VARCHAR(255)`  | No            | Không có            | Unique                       | Email.                                                           |
| `avatar_url`      | `VARCHAR(500)`  | Yes           | Không có            | Không có                     | URL avatar.                                                      |
| `avatar_id`       | `VARCHAR(100)`  | Yes           | Không có            | Không có                     | Avatar id.                                                       |
| `bio`             | `TEXT`          | Yes           | Không có            | Không có                     | Bio.                                                             |
| `phone`           | `VARCHAR(20)`   | Yes           | Không có            | Không có                     | Phone.                                                           |
| `created_at`      | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có     | Thời điểm tạo. Entity khai báo non-null.                         |
| `updated_at`      | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có     | Thời điểm cập nhật. Entity khai báo non-null.                    |

Index `idx_users_username_lower` trên `LOWER(username)` (case-insensitive lookup) — tên khác với `@Index(name="idx_users_username", ...)` khai báo trên `UserEntity`; do `ddl-auto=none`, index thật sự tồn tại là index Flyway tạo, annotation JPA chỉ mang tính tài liệu.

### `friend_requests`

Source: `V2__friend_requests_table.sql`, `V8__utc_timestamps.sql`.

| Column         | Type            | Nullable      | Default             | Constraint/index                                                                   | Ý nghĩa nghiệp vụ                                                     |
| ---------------- | ----------------- | --------------- | --------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `id`           | `UUID`          | No            | `gen_random_uuid()` | Primary key                                                                        | Friend request id.                                                    |
| `from_user_id` | `UUID`          | No            | Không có            | FK `users(id)` ON DELETE CASCADE, indexes                                          | User gửi request.                                                     |
| `to_user_id`   | `UUID`          | No            | Không có            | FK `users(id)` ON DELETE CASCADE, indexes                                          | User nhận request.                                                    |
| `message`      | `VARCHAR(300)`  | Yes           | Không có            | Không có                                                                           | Message gửi kèm.                                                      |
| `status`       | `VARCHAR(20)`   | No            | `'PENDING'`          | Check `PENDING`, `ACCEPTED`, `REJECTED`; unique `(from_user_id,to_user_id,status)` | Trạng thái request.                                                   |
| `responded_at` | `TIMESTAMPTZ`   | Yes           | Không có            | Không có                                                                           | Cột tồn tại nhưng service accept/decline hiện chưa set giá trị này. |
| `created_at`   | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Index theo from/to/status/created_at                                | Thời điểm tạo.                                                        |
| `updated_at`   | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Index theo from/to/status/created_at                                | Thời điểm cập nhật.                                                   |

Constraints: `chk_friend_request_users_distinct` (`from_user_id <> to_user_id`); `uq_friend_requests_pending_pair` (unique pending theo cặp user bất kể chiều gửi).

### `friends`

Source: `V3__friends_table.sql`, `V8__utc_timestamps.sql`.

| Column       | Type          | Nullable      | Default             | Constraint/index                   | Ý nghĩa nghiệp vụ         |
| -------------- | --------------- | --------------- | --------------------- | ------------------------------------- | --------------------------- |
| `id`         | `UUID`        | No            | `gen_random_uuid()` | Primary key                        | Friendship row id.        |
| `user_a_id`  | `UUID`        | No            | Không có            | FK `users(id)`, index, unique pair | User A đã normalize.      |
| `user_b_id`  | `UUID`        | No            | Không có            | FK `users(id)`, index, unique pair | User B đã normalize.      |
| `created_at` | `TIMESTAMPTZ` | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có       | Thời điểm tạo friendship. |
| `updated_at` | `TIMESTAMPTZ` | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có       | Thời điểm cập nhật.       |

Constraints: `chk_user_distinct` (`user_a_id <> user_b_id`); `chk_user_order` (`user_a_id < user_b_id`); `uq_friends` (unique `(user_a_id, user_b_id)`).

### `conversations`

Source: `V4__conversations_table.sql`, `V5__messages_table.sql`, `V8__utc_timestamps.sql`.

| Column             | Type            | Nullable      | Default             | Constraint/index                     | Ý nghĩa nghiệp vụ                                                    |
| -------------------- | ----------------- | --------------- | --------------------- | --------------------------------------- | -------------------------------------------------------------------- |
| `id`               | `UUID`          | No            | `gen_random_uuid()` | Primary key                          | Conversation id.                                                     |
| `type`             | `VARCHAR(20)`   | No            | Không có            | Check `DIRECT`, `GROUP`              | Loại conversation.                                                   |
| `group_name`       | `VARCHAR(255)`  | Yes           | Không có            | Không có                             | Tên group.                                                           |
| `created_by`       | `UUID`          | Yes           | Không có            | FK `users(id)` ON DELETE SET NULL    | User tạo conversation.                                               |
| `direct_user_a_id` | `UUID`          | Yes           | Không có            | FK `users(id)` ON DELETE CASCADE     | Direct user A đã normalize.                                          |
| `direct_user_b_id` | `UUID`          | Yes           | Không có            | FK `users(id)` ON DELETE CASCADE     | Direct user B đã normalize.                                          |
| `last_message_id`  | `UUID`          | Yes           | Không có            | FK `messages(id)` ON DELETE SET NULL | Message mới nhất (constraint thêm trong `V5`).                       |
| `last_message_at`  | `TIMESTAMPTZ`   | Yes           | Không có            | `idx_conversations_last_message_at`  | Thời điểm last message hoặc thời điểm tạo conversation theo service. |
| `created_at`       | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có                 | Thời điểm tạo.                                                       |
| `updated_at`       | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có                 | Thời điểm cập nhật.                                                  |

Constraints: `chk_conversation_users_for_type` (DIRECT phải có đủ 2 direct user và `a < b`; GROUP không có direct users); `chk_conversation_direct_users_distinct`; `uq_direct_conversations_pair` (unique direct conversation theo cặp).

### `messages`

Source: `V5__messages_table.sql`, `V8__utc_timestamps.sql`.

| Column            | Type            | Nullable      | Default             | Constraint/index                                                                              | Ý nghĩa nghiệp vụ                                      |
| ------------------- | ----------------- | --------------- | --------------------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| `id`              | `UUID`          | No            | `gen_random_uuid()` | Primary key                                                                                   | Message id.                                            |
| `conversation_id` | `UUID`          | No            | Không có            | FK `conversations(id)` ON DELETE CASCADE; index `(conversation_id, created_at DESC, id DESC)` | Conversation chứa message.                             |
| `sender_id`       | `UUID`          | No            | Không có            | FK `users(id)` ON DELETE CASCADE                                                              | User gửi.                                              |
| `content`         | `TEXT`          | Yes           | Không có            | Không có                                                                                      | Nội dung message.                                      |
| `type`            | `VARCHAR(20)`   | No            | `'TEXT'`            | Không có check constraint trong SQL                                                          | Message type.                                          |
| `attachment_url`  | `VARCHAR(500)`  | Yes           | Không có            | Không có                                                                                      | Attachment URL.                                        |
| `is_deleted`      | `BOOLEAN`       | No            | `false`             | Không có                                                                                      | Soft delete flag. Source query loại `is_deleted=true`. |
| `created_at`      | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Index message pagination                                                 | Thời điểm tạo.                                         |
| `updated_at`      | `TIMESTAMPTZ`   | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có                                                                  | Thời điểm cập nhật.                                    |

### `participants`

Source: `V6__participants_table.sql`, `V8__utc_timestamps.sql`, `V9__drop_unused_participant_columns.sql`.

| Column                 | Type          | Nullable      | Default             | Constraint/index                                    | Ý nghĩa nghiệp vụ                              |
| ------------------------ | --------------- | --------------- | --------------------- | ------------------------------------------------------ | ---------------------------------------------- |
| `conversation_id`      | `UUID`        | No            | Không có            | PK, FK `conversations(id)` ON DELETE CASCADE, index | Conversation id.                               |
| `user_id`              | `UUID`        | No            | Không có            | PK, FK `users(id)` ON DELETE CASCADE, index         | Participant user id.                           |
| `role`                 | `VARCHAR(20)` | Yes trong SQL | `'MEMBER'`           | Không có check constraint trong SQL                 | Participant role. Entity khai báo non-null.    |
| `last_read_message_id` | `UUID`        | Yes           | Không có            | FK `messages(id)` ON DELETE SET NULL, index         | Message cuối đã đọc.                           |
| `last_read_at`         | `TIMESTAMPTZ` | Yes           | Không có            | Không có                                            | Thời điểm đọc cuối.                            |
| `joined_at`            | `TIMESTAMPTZ` | Yes trong SQL | `CURRENT_TIMESTAMP` (trước khi đổi type) | Không có                       | Thời điểm join.                                |
| `left_at`              | `TIMESTAMPTZ` | Yes           | Không có            | Active participant yêu cầu null.                    | Thời điểm rời/bị remove khỏi group.            |
| `deleted_at`           | `TIMESTAMPTZ` | Yes           | Không có            | Partial index active participant (`idx_participants_user_active`) | Active participant yêu cầu null; user tự ẩn conversation. |

`archived_at`, `muted_until` **đã bị xóa** bởi `V9`. `ParticipantEntity` hiện chỉ còn `leftAt`, `deletedAt` (không còn field archive/mute). Primary key `(conversation_id, user_id)`.

### `sessions`

Source: `V7__sessions_table.sql`.

| Column          | Type           | Nullable | Default  | Constraint/index                              | Ý nghĩa nghiệp vụ                                                     |
| ----------------- | ---------------- | ---------- | ---------- | -------------------------------------------------- | --------------------------------------------------------------------- |
| `user_id`       | `UUID`         | No       | Không có | Primary key, FK `users(id)` ON DELETE CASCADE | Mỗi user có một session refresh token hiện hành theo schema hiện tại. |
| `refresh_token` | `VARCHAR(128)` | No       | Không có | Unique                                        | Refresh token random hex.                                             |
| `expires_at`    | `TIMESTAMPTZ`  | No       | Không có | `idx_sessions_expires_at`                     | Thời điểm hết hạn refresh token.                                      |

### Relationships

| Relationship                      | Mô tả                                                                                                        |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| User với session                  | `sessions.user_id` là primary key và foreign key tới `users.id`; mỗi user có tối đa một session theo schema. |
| User với friend request           | `friend_requests.from_user_id`/`to_user_id` đều FK tới `users.id`.                                          |
| User với friends                  | `friends.user_a_id`/`user_b_id` đều FK tới `users.id`; cặp được normalize.                                  |
| Conversation với participants     | `participants.conversation_id` FK tới `conversations.id`.                                                    |
| Conversation với messages         | `messages.conversation_id` FK tới `conversations.id`.                                                        |
| Conversation với last message     | `conversations.last_message_id` FK tới `messages.id`; `last_message_at` lưu thời điểm message mới nhất.    |
| Participant với last read message | `participants.last_read_message_id` FK tới `messages.id`, dùng để mark seen và unread count.                 |

## 12. Repository/query behavior

| Repository                | Query/hành vi chính                                                                                                                                                                     | File/path                                                                |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `UserRepository`          | `existsByUsername`, `existsByUsernameAndIdNot`, `existsByEmailAndIdNot`, `findByUsername`, `searchUsers` (LIKE trên `LOWER(username)` hoặc `normalizedName`, sort `username ASC, id ASC`). | `src/main/java/com/chat_socket/repository/UserRepository.java`          |
| `FriendRepository`        | `existsFriendship`/`existsByUserAIdAndUserBId`, `deleteByUserAIdAndUserBId`, `findFriendship`, `findFriendshipsOfUser` (2 overload: không/có search+cursor), `findFriendshipsBetweenUserAndUsers`, `findFriendshipsOfUserBeforeCursor`. | `src/main/java/com/chat_socket/repository/FriendRepository.java`        |
| `FriendRequestRepository` | `existsBetweenUsersWithStatus` (2 chiều), `findFriendRequestsSentOfUser`, `findFriendRequestsReceivedOfUser`, `findFriendRequestsBetweenUserAndUsers`.                                     | `src/main/java/com/chat_socket/repository/FriendRequestRepository.java` |
| `ConversationRepository`  | `findDirectConversation` (theo normalized pair), `findActiveConversationIdsForUser`(`BeforeCursor`) (cursor theo `COALESCE(lastMessageAt, updatedAt)`), `findConversationsWithDetails`/`findWithDetails` (join fetch participants+user, lastMessage+sender). | `src/main/java/com/chat_socket/repository/ConversationRepository.java`  |
| `MessageRepository`       | `countUnreadMessagesByConversation`, `findLatestMessages`, `findMessagesBeforeCursor`, `findByIdAndDeletedFalse`, `findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc`.      | `src/main/java/com/chat_socket/repository/MessageRepository.java`       |
| `ParticipantRepository`   | `existsByIdConversationIdAndIdUserId`(`AndLeftAtIsNullAndDeletedAtIsNull`), `findByIdConversationIdAndIdUserId`/`findActiveParticipant`, `findActiveUserIdsByConversationId`/`findActiveParticipantsByConversationId`, `findByConversationIdAndIdUserIdIn`, `restoreDeletedParticipant`(`sByConversationId`), `countActiveByConversationIdAndRoleAndIdUserIdNot`. | `src/main/java/com/chat_socket/repository/ParticipantRepository.java`   |
| `SessionRepository`       | `deleteByRefreshToken`, `findByRefreshToken`.                                                                                                                                              | `src/main/java/com/chat_socket/repository/SessionRepository.java`       |

### Cursor pagination

| Use case      | Cursor field (kiểu `Instant`)        | Sort                                                                         | File/path                                                                                                                                    |
| --------------- | --------------------------------------- | --------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Friends (offset, không dùng cursor field) | `FriendEntity.createdAt`             | `createdAt DESC, id DESC`                                                    | `src/main/java/com/chat_socket/repository/FriendRepository.java`                                                                             |
| Conversations | `COALESCE(lastMessageAt, updatedAt)` | `COALESCE(...) DESC, id DESC`                                                | `src/main/java/com/chat_socket/repository/ConversationRepository.java`                                                                       |
| Messages      | `MessageEntity.createdAt`            | `createdAt DESC, id DESC`, response reverse thành thứ tự tăng dần trong page | `src/main/java/com/chat_socket/repository/MessageRepository.java`, `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java` |

`PaginationUtils` fetch `limit + 1` item để xác định `nextCursor`/`nextOffset`. Cursor input parse bằng `Instant.parse`; cursor output format bằng `TimeFormat.UTC_MICROS`.

### Unread count

`MessageRepository.countUnreadMessagesByConversation`: `m.conversation.id IN :conversationIds`, `m.deleted = false`, `m.sender.id <> :userId`, `(p.lastReadAt IS NULL OR m.createdAt > p.lastReadAt)`.

## 13. Error handling

`GlobalExceptionHandler` (`src/main/java/com/chat_socket/config/GlobalExceptionHandler.java`):

| Exception                                          | HTTP status | Response `data`           | Response `message`      |
| ----------------------------------------------------- | ------------: | ---------------------------- | --------------------------- |
| Validation error `MethodArgumentNotValidException` |       `400` | Map field → message         | `Validation failed`     |
| `SignInException`                                  |       `400` | `null`                    | Exception message       |
| `NotFoundException`                                |       `404` | `null`                    | Exception message       |
| `UnAuthorizedException`                            |       `401` | `null`                    | Exception message       |
| `ForbiddenException`                               |       `403` | `null`                    | Exception message       |
| `FriendPermissionException`                        |       `403` | `{ "notFriends": [...] }` | Exception message       |
| `BadRequestException`                              |       `400` | `null`                    | Exception message       |
| `MaxUploadSizeExceededException`                   |       `413` | `null`                    | `File is too large (max 10MB).` |
| Unhandled `Exception`                              |       `500` | `ex.getMessage()`         | `Internal server error` |

`SecurityFilter` tự ghi JSON error response bằng `BaseResponse<Object>`, không đi qua `GlobalExceptionHandler`.

## 14. Security notes

| Chủ đề                            | Ghi nhận theo source hiện tại                                                                                                                                                     |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Stateless session                 | `SecurityServerConfig` dùng `SessionCreationPolicy.STATELESS`.                                                                                                                    |
| JWT Bearer token                  | HTTP endpoint private yêu cầu `Authorization: Bearer <token>`.                                                                                                                    |
| Password hashing                  | BCrypt strength `10`.                                                                                                                                                             |
| CORS                              | **Không hard-code.** `SecurityServerConfig.corsConfigurationSource()` set `allowedOrigins` trực tiếp từ `applicationYaml.clientUrl()` (List, cùng nguồn với WebSocket allowed origin); `allowCredentials=true`, headers `*`, methods `GET,POST,PUT,PATCH,DELETE,OPTIONS`. |
| WebSocket allowed origin          | `WebSocketConfig` dùng `chat-socket.client-url` (cùng list với CORS).                                                                                                             |
| Method-level security             | `@EnableMethodSecurity`; `@PreAuthorize` dùng ở create conversation, send message, update/delete group.                                                                          |
| Public HTTP endpoints             | `SecurityServerConfig` permit `"/v1/auth/**"`, `RouteApi.HEALTH_API` (`/health-check`), `RouteApi.FILES + "/**"` (`/files/**`), `"/ws*"`, và mọi `OPTIONS`. Với servlet path thực tế: `/api/v1/auth/**`, `/api/health-check`, `/api/files/**`, `/api/ws*`. `SecurityFilter.shouldNotFilter` bypass tương ứng (xem mục 6). |
| Private HTTP endpoints            | Các endpoint khác yêu cầu authenticated user.                                                                                                                                     |
| WebSocket CONNECT auth            | STOMP `CONNECT` cần native header `Authorization: Bearer <token>`.                                                                                                                |
| WebSocket SUBSCRIBE authorization | Kiểm tra participant permission cho mọi destination `/topic/conversations/{id}/**` (messages, seen, typing).                                                                     |
| Upload/file download header      | `FileDownloadHeadersFilter` gắn `Content-Disposition: attachment` + `X-Content-Type-Options: nosniff` cho mọi response dưới `/api/files/` để chặn stored-XSS từ file HTML/SVG upload. | 
| Refresh token storage             | Refresh token lưu DB `sessions`, gửi qua cookie HTTP-only, secure, SameSite=none.                                                                                                 |
| Role/authority                    | `Security.getUserAuthentication` tạo authentication với `Collections.emptyList()` — chưa có role/authority cho HTTP auth.                                                        |

## 15. Realtime architecture bằng mô tả chữ

1. Client mở kết nối STOMP tới `/api/ws` (local).
2. Client gửi native header `Authorization: Bearer <accessToken>` trong frame `CONNECT`.
3. `SocketChannelInterceptor` verify JWT, load user, set authenticated principal cho WebSocket session.
4. Khi `SessionConnectEvent` phát sinh, `SocketEventListener` lấy `UserSecurity` từ principal, lưu session id vào Redis (`UserOnlineRegistry.markOnline`) và broadcast danh sách online users qua `/topic/online-users`.
5. Client subscribe snapshot online users qua `/app/online-users` (trả `Set<UUID>` ngay) hoặc lắng nghe broadcast `/topic/online-users`.
6. Client subscribe `/topic/conversations/{conversationId}/messages` (và tương tự `/seen`, `/typing`) — `SocketChannelInterceptor` kiểm tra user là active participant cho mọi sub-destination này.
7. Khi client gửi message qua REST thành công, service lưu `messages`, cập nhật `conversations.last_message_id`/`last_message_at`, khôi phục participant đã ẩn, mark sender as read.
8. Sau transaction commit, `SocketPublisher` publish `MessageEvent.created(messageDto)` tới `/topic/conversations/{conversationId}/messages`.
9. Cùng lúc, `SocketPublisher` gửi `ConversationUpdatedEvent` riêng tới từng active participant qua `/user/queue/conversations`, với `unreadCount` tính riêng từng user.
10. Khi client sửa/xóa message (PATCH/DELETE `/api/v1/message/{messageId}`), server publish `MessageEvent.updated`/`.deleted` tới cùng topic messages; nếu message đó là last message của conversation, server tính lại last message và bắn thêm `ConversationUpdatedEvent` qua queue.
11. Client gõ phím gửi STOMP `MESSAGE` tới `/app/conversations/{conversationId}/typing`; server verify active participant rồi broadcast ngay `TypingEvent` tới `/topic/conversations/{conversationId}/typing` (không qua transaction/commit vì không đụng DB).
12. Khi client gọi mark seen thành công, service cập nhật `participants.last_read_message_id`/`last_read_at`; sau commit, server gửi `ConversationSeenEvent` tới **mọi** active participant qua `/user/queue/conversations` (không phải một topic riêng).
13. Khi user rời/bị remove/tự ẩn group, server gửi `ConversationRemovedEvent` (không phải `ConversationUpdatedEvent`) tới đúng user bị ảnh hưởng qua `/user/queue/conversations`.
14. Khi user disconnect, `UserOnlineRegistry` xóa session id khỏi Redis (dọn set nếu hết session) và server broadcast lại `/topic/online-users`.

## 16. Quy ước response và pagination

### `BaseResponse<T>`

| Field     | Type     | Ý nghĩa                                                                   |
| ----------- | ---------- | ------------------------------------------------------------------------- |
| `data`    | Generic  | Payload thành công hoặc payload lỗi tùy handler.                          |
| `message` | `String` | Message nghiệp vụ hoặc message lỗi. Có thể `null`.                        |
| `status`  | `int`    | HTTP status code. Controller dùng `ResponseEntity.status(body.status())`. |

### `PaginationRequest(limit, cursor, offset)`

| Field    | Type      | Default |           Max | Ý nghĩa                                                   |
| ---------- | ----------- | --------: | --------------: | ------------------------------------------------------------ |
| `limit`  | `Integer` |    `50` |         `100` | Số item trả về. Nếu lớn hơn `100`, source clamp về `100`. |
| `cursor` | `String`  |  `null` | Không áp dụng | Cursor ISO-8601 Instant cho conversation/message pagination. |
| `offset` | `Integer` |     `0` | Không áp dụng | Offset cho search user/friend.                            |

Nếu `limit < 1` → `BadRequestException("Limit must be greater than 0.")`. Nếu `offset < 0` → `BadRequestException("Offset must be greater than or equal to 0.")`.

Cursor parsing (`PaginationUtils.parseDateTimeCursor`): `Instant.parse(cursor.trim())` — chấp nhận `Z` hoặc offset, **không** chấp nhận chuỗi không có zone (khác `ISO_LOCAL_DATE_TIME`/`ISO_OFFSET_DATE_TIME` như README cũ mô tả); parse lỗi → `BadRequestException("Cursor is invalid.")`.

### `PaginationResponse<T>(items, nextCursor, nextOffset)`

| Field        | Type      | Ý nghĩa                                                                 |
| -------------- | ----------- | ----------------------------------------------------------------------- |
| `items`      | `List<T>` | Danh sách item hiện tại. Field tên `items` (đổi từ `messages`).       |
| `nextCursor` | `String`  | Cursor cho page tiếp theo nếu còn dữ liệu, format `TimeFormat.UTC_MICROS`; ẩn (không serialize) nếu `null`. |
| `nextOffset` | `Integer` | Offset cho page tiếp theo nếu còn dữ liệu; ẩn nếu `null`.              |

`PaginationUtils.toCursorResponse`/`toOffsetResponse` fetch `limit + 1`; nếu vượt limit thì cắt về đúng limit và set `nextCursor`/`nextOffset` tương ứng.

## 17. Những điểm cần lưu ý/khoảng trống hiện tại

| Chủ đề                        | Ghi nhận theo source hiện tại                                                                                                                                                                     |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Test coverage                 | ~31 file test trong `src/test/java/com/chat_socket/**`, trải khắp `config`, `controller` (6 file), `dto`, `repository` (2), `security` (6), `service`+`service.impl` (7), `socket` (3), `utils` (3), cộng `ChatSocketApplicationTests` và `TestFixtures` (helper). Không còn chỉ có `contextLoads()`. |
| Swagger/OpenAPI               | Chưa xác định trong source hiện tại. `pom.xml` không có dependency Springdoc/OpenAPI/Swagger.                                                                                                     |
| Logging/monitoring            | Chưa xác định trong source hiện tại. Không thấy cấu hình logging/metrics/tracing riêng ngoài mặc định Spring Boot.                                                                                |
| Production secrets            | `application.yaml` có default cho JWT secret, DB password, upload dir, public URL — tất cả nên externalize qua env khi deploy.                                                                    |
| WebSocket security matcher    | `SecurityServerConfig` permit `"/ws*"` nhưng `SecurityFilter` bypass đúng bằng `"/api/ws"` (regex full-match) — vẫn là 2 cơ chế lệch nhau, cần lưu ý khi thêm sub-path SockJS.                     |
| Refresh token/cookie          | Cookie refresh token `secure(true)` + `sameSite(none)` — local HTTP frontend/backend có thể cần HTTPS hoặc proxy phù hợp để browser gửi cookie đúng kỳ vọng.                                      |
| Multiple sessions             | Bảng `sessions` dùng `user_id` làm primary key, nên mỗi user chỉ có một refresh token row hiện hành.                                                                                              |
| Upload file không có cleanup  | `FileStorage` chỉ lưu, không có job xóa file mồ côi khi message chứa `attachmentUrl` bị xóa, và class tự ghi chú `ponytail: local disk; swap for S3/Cloudinary when running more than one instance` — chỉ đúng khi chạy 1 instance/không share disk. |
| Message deletion              | Không có time window giới hạn để sửa/xóa; dữ liệu soft-delete (`is_deleted=true`) không có job purge cứng.                                                                                        |
| Participant lifecycle         | `archived_at`, `muted_until` đã bị drop ở `V9`; entity chỉ còn `leftAt`, `deletedAt`; chưa có API archive/mute conversation.                                                                       |
| Friend request `responded_at` | Cột tồn tại nhưng service accept/decline hiện chưa set `respondedAt`.                                                                                                                             |
| DB enum constraints           | `messages.type` và `participants.role` có comment enum trong SQL nhưng chưa có check constraint.                                                                                                  |
| Tạo DIRECT conversation không check bạn bè | `POST /api/v1/conversation` với `type=DIRECT` không friend-check (khác với gửi direct message qua `recipientId`, có check) — xem mục 10.                                                    |

## 18. Phụ lục

### Danh sách enum

| Enum                  | Values                                       | File/path                                                      |
| ------------------------ | ----------------------------------------------- | ------------------------------------------------------------------ |
| `ConversationType`    | `DIRECT`, `GROUP`                            | `src/main/java/com/chat_socket/enums/ConversationType.java`    |
| `FriendRequestStatus` | `PENDING`, `ACCEPTED`, `REJECTED`            | `src/main/java/com/chat_socket/enums/FriendRequestStatus.java` |
| `FriendStatus`        | `NONE`, `SELF`, `FRIEND`, `SENT`, `RECEIVED` | `src/main/java/com/chat_socket/enums/FriendStatus.java`        |
| `MessageType`         | `TEXT`, `IMAGE`, `FILE`, `SYSTEM`            | `src/main/java/com/chat_socket/enums/MessageType.java`         |
| `ParticipantRole`     | `ADMIN`, `MEMBER`                            | `src/main/java/com/chat_socket/enums/ParticipantRole.java`     |

### Danh sách route constants

Source: `src/main/java/com/chat_socket/constant/RouteApi.java`.

| Constant           | Value              | Ghi chú                                          |
| -------------------- | -------------------- | ---------------------------------------------------- |
| `API_V1`           | `/v1`              |                                                    |
| `AUTH_API`         | `/v1/auth`         |                                                    |
| `USER_API`         | `/v1/user`         |                                                    |
| `FRIEND_API`       | `/v1/friend`       |                                                    |
| `MESSAGE_API`      | `/v1/message`      |                                                    |
| `CONVERSATION_API` | `/v1/conversation` |                                                    |
| `UPLOAD_API`       | `/v1/upload`       |                                                    |
| `FILES`            | `/files`           | **Không** nằm dưới `API_V1`; URL thực `/api/files/**`. |
| `HEALTH_API`       | `/health-check`    | **Không** nằm dưới `API_V1`; URL thực `/api/health-check`. |

Khi kết hợp với `spring.mvc.servlet.path=/api`, URL REST có prefix `/api/v1` (trừ `FILES`, `HEALTH_API`).

### Danh sách socket channels

Source: `src/main/java/com/chat_socket/constant/SocketChannel.java`.

| Constant             | Value                       | Ý nghĩa                                                                                                         |
| ----------------------- | ----------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `APP`                | `/app`                      | Application destination prefix.                                                                                 |
| `TOPIC`              | `/topic`                    | Simple broker topic prefix.                                                                                     |
| `QUEUE`              | `/queue`                    | Simple broker queue prefix.                                                                                     |
| `CONVERSATION`       | `/conversations`            | Segment conversation.                                                                                            |
| `MESSAGE`            | `/messages`                 | Segment messages.                                                                                               |
| `TYPING`             | `/typing`                   | Segment typing.                                                                                                  |
| `ONLINE_USERS`       | `/online-users`             | Segment online users, dùng cho `@SubscribeMapping` và broadcast topic.                                          |
| `CONVERSATION_QUEUE` | `/queue/conversations`      | User-specific queue destination; qua `convertAndSendToUser`, client subscribe `/user/queue/conversations`.       |
| `MESSAGE_TOPIC`      | `/conversations/%s/messages`| `SocketEmitter.emit` tự thêm `/topic` → `/topic/conversations/{id}/messages`.                                   |
| `TYPING_TOPIC`       | `/conversations/%s/typing`  | `SocketEmitter.emit` tự thêm `/topic` → `/topic/conversations/{id}/typing`.                                     |
| `TYPING_MAPPING`     | `/conversations/{conversationId}/typing` | Pattern cho `@MessageMapping`, tương đối so với `APP` → client gửi `/app/conversations/{id}/typing`. |

Không còn `SEEN`/`CONVERSATION_SEEN_TOPIC` — `ConversationSeenEvent` hiện đi qua `CONVERSATION_QUEUE` per-user (xem mục 9).

### Danh sách Redis keys

Source: `src/main/java/com/chat_socket/constant/Redis.java`.

| Constant                   | Value                               | Ý nghĩa                                          |
| ----------------------------- | -------------------------------------- | ------------------------------------------------------ |
| `ONLINE_USERS_KEY`         | `chat-socket:online-users`          | Redis set chứa user ids đang online.             |
| `USER_SESSIONS_KEY_PREFIX` | `chat-socket:online-user-sessions:` | Prefix Redis set chứa session ids của từng user. |
| `REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT` | Lua script (`RedisScript<Long>`) | Xóa session id khỏi set của user; nếu set rỗng thì xóa key và bỏ user khỏi `ONLINE_USERS_KEY` — chạy atomic khi disconnect. |

### Danh sách task command

Source: `Taskfile.yml`.

| Task                   | Command chính                                                 | Ý nghĩa                          |
| ------------------------ | ------------------------------------------------------------------ | ------------------------------------ |
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
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `pom.xml`                                                                 | Version Java, Spring Boot, dependency, plugin format.         |
| `Taskfile.yml`                                                            | Command vận hành local.                                       |
| `deployment/docker-compose/infra.yml`                                     | PostgreSQL/Redis local infra.                                 |
| `src/main/resources/application.yaml`                                     | Toàn bộ config runtime (đọc từ env var + default).             |
| `src/main/resources/db/migration/*`                                       | Database schema chuẩn (`V1`–`V9`).                             |
| `src/main/java/com/chat_socket/config/SecurityServerConfig.java`          | HTTP security, CORS, stateless session, password encoder.     |
| `src/main/java/com/chat_socket/security/SecurityFilter.java`              | JWT Bearer authentication cho REST + bypass list.              |
| `src/main/java/com/chat_socket/security/SocketChannelInterceptor.java`    | JWT và authorization cho WebSocket/STOMP.                     |
| `src/main/java/com/chat_socket/config/WebSocketConfig.java`               | STOMP endpoint, broker prefixes, inbound channel interceptor. |
| `src/main/java/com/chat_socket/service/impl/AuthServiceImpl.java`         | Auth/session/refresh token behavior.                          |
| `src/main/java/com/chat_socket/service/impl/FriendServiceImpl.java`       | Friend request business rules.                                |
| `src/main/java/com/chat_socket/service/impl/ConversationServiceImpl.java` | Conversation, messages pagination, mark seen, group lifecycle. |
| `src/main/java/com/chat_socket/service/impl/MessageServiceImpl.java`      | Direct/group message send/update/delete flow.                 |
| `src/main/java/com/chat_socket/service/FileStorage.java`                  | Upload file storage behavior.                                 |
| `src/main/java/com/chat_socket/socket/SocketPublisher.java`               | Realtime publish behavior after transaction commit.           |
| `src/main/java/com/chat_socket/utils/PaginationUtils.java`                | Cursor pagination rules.                                       |
