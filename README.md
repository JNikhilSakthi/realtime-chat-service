# realtime-chat-service

Real-time chat rooms over STOMP-over-WebSocket, with MySQL-backed history and in-memory presence — built to make WebSocket, not a message broker, the star of the show.

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-brightgreen) ![WebSocket](https://img.shields.io/badge/WebSocket-STOMP%20%2F%20SockJS-blue) ![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1) ![React](https://img.shields.io/badge/React-18%20%2B%20Vite-61DAFB) ![Docker](https://img.shields.io/badge/Docker-Compose-2496ED) ![Tests](https://img.shields.io/badge/tests-27%2F27%20passing-success)

**Learning Track:** `springboot-websocket-chat-demo` (Project 11 of 17)
**Real-World Service Name:** `realtime-chat-service`

---

## 1. Project Overview

### The problem

HTTP is fundamentally request/response: a client asks, a server answers, the connection is done. Chat, live dashboards, collaborative editors, multiplayer games and trading tickers all need the opposite — the **server** must be able to push data to the client the instant something happens, without the client polling every second and without a response ever "finishing." This project builds the canonical example of that need: a real-time, multi-room chat application.

### Why WebSocket

WebSocket upgrades a single HTTP connection into a full-duplex, persistent TCP-like channel. Once the handshake succeeds, either side can send frames at any time. On top of raw WebSocket, this project layers **STOMP** (Simple/Streaming Text Oriented Messaging Protocol), which gives WebSocket the vocabulary a normal application actually needs — `SUBSCRIBE`, `SEND`, `MESSAGE`, `CONNECT`, `DISCONNECT` — instead of hand-rolling a custom framing format over raw bytes. Spring's `spring-boot-starter-websocket` has first-class STOMP support (`@EnableWebSocketMessageBroker`, `@MessageMapping`, `SimpMessagingTemplate`), which is why it's the natural technology choice for teaching this pattern in the Spring ecosystem. **SockJS** is layered on top of STOMP purely as a fallback transport: it detects when a corporate proxy, ancient browser, or restrictive network blocks raw WebSocket upgrades, and transparently falls back to HTTP streaming/polling — so the same client code keeps working almost everywhere.

Deliberately, this demo uses Spring's **simple in-memory broker** (`registry.enableSimpleBroker("/topic", "/queue")`) rather than wiring in RabbitMQ or ActiveMQ. That's a scope decision, not an oversight: pulling in an external broker would teach message-broker concepts, not WebSocket concepts. The trade-off is explicit and worth understanding for interviews — the simple broker only works within a single JVM instance, so a production, horizontally-scaled version of this service would need to swap it for `enableStompBrokerRelay(...)` pointed at a real broker (or use Redis pub/sub) so that a message published on server pod A reaches a client connected to server pod B.

### Where this pattern shows up in real companies

- **Slack / Microsoft Teams / Discord** — real-time message delivery, typing indicators, presence ("online now").
- **Google Docs / Figma** — collaborative editing over a persistent socket instead of polling for diffs.
- **Trading platforms (Bloomberg terminals, Robinhood, crypto exchanges)** — live price ticks pushed to thousands of open dashboards.
- **Uber / food delivery apps** — live driver location updates streamed to the rider's map.
- **Multiplayer games and live sports scoreboards** — low-latency bidirectional state sync.
- **Customer support widgets (Intercom, Zendesk chat)** — the exact JOIN/CHAT/LEAVE/TYPING event model this project implements.

---

## 2. Architecture

### High-Level Design (HLD)

```
                         ┌─────────────────────────────┐
                         │        Browser Client        │
                         │  (React + Vite, or any       │
                         │   STOMP/SockJS client)       │
                         └───────────────┬──────────────┘
                                         │
                         HTTP  /api/**   │   WS  /ws (SockJS)
                                         │
                         ┌───────────────▼──────────────┐
                         │      nginx (chat-client)      │   <- Docker only
                         │  reverse proxies /api, /ws     │
                         └───────────────┬──────────────┘
                                         │
                    ┌────────────────────▼───────────────────────┐
                    │         Spring Boot (chat-service)           │
                    │                                              │
                    │   REST layer            STOMP/WebSocket layer│
                    │  ChatRoomController     ChatWebSocketController│
                    │  ChatMessageController  WebSocketEventListener │
                    │        │                        │            │
                    │        ▼                        ▼            │
                    │  ChatRoomService         PresenceService       │
                    │  ChatMessageService      (in-memory,           │
                    │        │                 ConcurrentHashMap)    │
                    │        ▼                                      │
                    │  Spring Data JPA repositories                 │
                    └───────────────────┬──────────────────────────┘
                                         │
                                  ┌──────▼──────┐
                                  │    MySQL     │
                                  │ chat_room /  │
                                  │ chat_message │
                                  └──────────────┘
```

### Low-Level Design (LLD) — one chat round-trip

```
1. Client opens WebSocket:  new SockJS('/ws')  ->  STOMP CONNECT frame
   WebSocketConfig registers endpoint /ws (SockJS fallback enabled)

2. Client subscribes:       SUBSCRIBE /topic/room/{roomCode}
   (simple broker keeps this subscription in memory)

3. Client publishes JOIN:   SEND /app/chat.addUser/{roomCode}  { type: JOIN, sender }
        │
        ▼
   ChatWebSocketController.addUser()
        │  1. chatRoomService.findRoomOrThrow(roomCode)      -> 404-equivalent if missing
        │  2. stash username + roomCode as STOMP session attributes
        │  3. presenceService.join(roomCode, username)        -> in-memory only
        │  4. chatMessageService.saveMessage(... JOIN ...)    -> persisted to MySQL
        │  5. messagingTemplate.convertAndSend("/topic/room/{roomCode}", event)
        ▼
   All subscribers on /topic/room/{roomCode} receive the JOIN event

4. Client publishes CHAT:   SEND /app/chat.sendMessage/{roomCode}  { type: CHAT, sender, content }
        │
        ▼
   ChatWebSocketController.sendMessage() -> persist -> broadcast to /topic/room/{roomCode}

5. Client publishes TYPING: SEND /app/chat.typing/{roomCode}
        │
        ▼
   ChatWebSocketController.typing() -> broadcast ONLY (never persisted)

6. Browser tab closes / connection drops
        │
        ▼
   SessionDisconnectEvent  ->  WebSocketEventListener.handleSessionDisconnect()
        │  reads session attributes stashed at JOIN time (no client action needed)
        │  presenceService.leave(roomCode, username)
        │  saveMessage(... LEAVE ...) + broadcast to /topic/room/{roomCode}
```

### Domain model / DB design

```
┌────────────────────────┐        1        *  ┌──────────────────────────────┐
│         ChatRoom        │──────────────────▶│          ChatMessage          │
├────────────────────────┤   room_id (FK)      ├──────────────────────────────┤
│ id          BIGINT PK   │                     │ id        BIGINT PK           │
│ roomCode    VARCHAR(12) │  unique, indexed    │ room_id   BIGINT FK           │
│                         │  (public join-code) │ sender    VARCHAR(50)         │
│ name        VARCHAR(100)│                     │ content   VARCHAR(2000)       │
│ description VARCHAR(255)│                     │ type      ENUM as STRING      │
│ status      ENUM (STRING)│                    │           (CHAT/JOIN/LEAVE)   │
│ createdAt   TIMESTAMP    │                     │ sentAt    TIMESTAMP           │
└────────────────────────┘                     │  idx_chat_message_room_ts     │
                                                 │  (room_id, sentAt)            │
                                                 └──────────────────────────────┘

Presence (who's online per room) is NOT a table. It lives entirely in
PresenceService: ConcurrentHashMap<String roomCode, Set<String> usernames>.
Rationale: presence is connection-scoped and ephemeral (it should vanish the
instant a socket disconnects); chat history is durable and must survive
restarts. Mixing the two into one persistence model would force awkward
cleanup logic (e.g. "delete presence rows on every disconnect") for data that
never needed to touch a disk in the first place. TYPING events follow the
same ephemeral philosophy and are rejected by ChatMessageService.saveMessage()
with an IllegalArgumentException if anyone tries to persist one.
```

`roomCode` (not the surrogate `id`) is the identifier used everywhere a client-facing reference is needed: REST paths (`/api/rooms/{roomCode}`), STOMP subscriptions (`/topic/room/{roomCode}`), and STOMP send destinations (`/app/chat.sendMessage/{roomCode}`). This keeps the numeric primary key an internal implementation detail and gives rooms a short, shareable, unguessable-enough code (6 chars from a 32-symbol alphabet that excludes `0/O/1/I` to avoid visual ambiguity).

### Folder structure

```
realtime-chat-service/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── src/main/java/com/medha/realtimechatservice/
│   ├── RealtimeChatServiceApplication.java
│   ├── config/
│   │   ├── WebSocketConfig.java        # STOMP endpoint + simple broker
│   │   └── WebConfig.java              # CORS for the /api/** REST layer
│   ├── domain/                         # JPA entities + enums
│   │   ├── ChatRoom.java
│   │   ├── ChatMessage.java
│   │   └── MessageType.java
│   ├── repository/                     # Spring Data JPA
│   ├── dto/                            # REST + STOMP payloads
│   ├── exception/                      # RoomNotFoundException, DuplicateRoomException,
│   │                                   #   GlobalExceptionHandler (@RestControllerAdvice)
│   ├── service/
│   │   ├── ChatRoomService.java
│   │   ├── ChatMessageService.java
│   │   └── PresenceService.java        # in-memory presence tracker
│   ├── controller/                     # plain REST: rooms + message history
│   └── websocket/
│       ├── ChatWebSocketController.java  # @MessageMapping handlers
│       └── WebSocketEventListener.java   # SessionDisconnectEvent -> auto LEAVE
├── src/main/resources/application.yml
├── src/test/...                        # unit + full STOMP integration tests
└── react-client/                       # optional Vite/React demo UI
    ├── src/hooks/useChatSocket.js      # @stomp/stompjs + sockjs-client
    ├── src/components/{JoinForm,ChatWindow}.jsx
    ├── Dockerfile + nginx.conf         # serves the built SPA, proxies /api & /ws
    └── vite.config.js                 # dev-server proxy mirrors nginx's prod behavior
```

---

## 3. Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Language / runtime | Java 21 (Docker always builds/runs on the JDK 21 toolchain) | LTS, required by Spring Boot 3.3.x |
| Framework | Spring Boot 3.3.2 | `spring-boot-starter-web` + `spring-boot-starter-websocket` |
| Real-time transport | WebSocket + STOMP sub-protocol + SockJS fallback | Full-duplex push; STOMP gives structured pub/sub semantics; SockJS covers proxies/browsers that block raw WS |
| Message broker | Spring's simple in-memory broker (`/topic`, `/queue`) | Keeps WebSocket itself the star; no RabbitMQ/ActiveMQ dependency |
| Persistence | Spring Data JPA + MySQL 8.0 (Hibernate) | Durable chat history (`ChatRoom`, `ChatMessage`) |
| Presence tracking | Plain Java (`ConcurrentHashMap`) inside `PresenceService` | Ephemeral, connection-scoped data does not belong in a relational table |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) | `@Valid` on both REST DTOs and STOMP `@Payload` objects |
| Boilerplate reduction | Lombok | `@Getter/@Setter/@Builder/@RequiredArgsConstructor` on entities/DTOs/services |
| Ops | Spring Boot Actuator | `/actuator/health` used by Docker healthchecks |
| Testing | JUnit 5, Mockito, AssertJ, Spring Boot Test, H2, `spring-websocket` STOMP test client | Unit tests for services/controllers + one full STOMP round-trip integration test |
| Frontend (optional) | React 18 + Vite, `@stomp/stompjs`, `sockjs-client` | Demonstrates a real STOMP/SockJS browser client against the backend |
| Containerization | Docker multi-stage builds, Docker Compose, nginx | `mysql` + `chat-service` + `chat-client` composed together |

---

## 4. Configuration Explained

### `src/main/resources/application.yml`

```yaml
server:
  port: 8080
```
Standard HTTP port for both the REST API and the `/ws` STOMP endpoint (they share the same embedded Tomcat).

```yaml
spring:
  application:
    name: realtime-chat-service
```
Used in logs/Actuator `/actuator/info` and by any service-discovery tooling later in the roadmap.

```yaml
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:chatdb}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
    username: ${DB_USERNAME:chatuser}
    password: ${DB_PASSWORD:chatpass}
    driver-class-name: com.mysql.cj.jdbc.Driver
```
- `${DB_HOST:localhost}` / `${DB_PORT:3306}` / `${DB_NAME:chatdb}` — all overridable by environment variables so the exact same jar runs unmodified locally (`localhost`) and inside `docker-compose` (`mysql` service name).
- `createDatabaseIfNotExist=true` — the database schema itself is created automatically on first connect; combined with `ddl-auto: update` below, this gives **zero-friction local startup**: `docker compose up` and the schema exists, no manual `CREATE DATABASE` or migration step required. This is a deliberate scope trade-off (see below), not a production recommendation.
- `useSSL=false&allowPublicKeyRetrieval=true` — avoids MySQL 8's default `caching_sha2_password` handshake friction in a local/Docker network that isn't using TLS.
- `serverTimezone=UTC` — pins the JDBC driver's timezone so timestamps aren't silently shifted by the host machine's local timezone.

```yaml
  hikari:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout: 20000
```
Conservative connection-pool sizing appropriate for a single-instance demo service; `connection-timeout` fails fast (20s) rather than hanging indefinitely if MySQL is unreachable.

```yaml
  jpa:
    hibernate:
      ddl-auto: update
```
Hibernate auto-updates the schema to match the `@Entity` classes on startup. **Deliberately no Flyway/Liquibase** — introducing a migration tool would be its own lesson; this project's scope is WebSocket, and `ddl-auto: update` keeps local setup to a single `docker compose up`.

```yaml
    open-in-view: false
```
Disables the Open Session In View anti-pattern: no lazy-loading surprises leaking into the web layer, and DB connections aren't held open for the whole request/response cycle (important since REST responses here map entities to DTOs inside `@Transactional` service methods, not in the controller).

```yaml
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
        jdbc:
          time_zone: UTC
```
`format_sql` only matters when `show-sql` is flipped on for debugging. `jdbc.time_zone: UTC` keeps Hibernate's reads/writes of `Instant` columns timezone-consistent with the JDBC URL's `serverTimezone=UTC`.

```yaml
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
```
- `non_null` — omits null fields (e.g. a room's optional `description`) from JSON responses instead of emitting `"description": null`.
- `write-dates-as-timestamps: false` — serializes `Instant` fields (like `ChatMessage.sentAt`) as ISO-8601 strings (`2026-07-22T10:15:30Z`) rather than epoch-millisecond numbers, which is what both the STOMP integration test and the React client's `formatTime()` expect.

```yaml
chat:
  websocket:
    allowed-origin-patterns: ${CHAT_ALLOWED_ORIGINS:*}
  cors:
    allowed-origins: ${CHAT_ALLOWED_ORIGINS:*}
```
Custom `chat.*` properties (not Spring-standard) bound with plain `@Value` in `WebSocketConfig` and `WebConfig`. A single `CHAT_ALLOWED_ORIGINS` env var controls both the STOMP endpoint's allowed origin patterns (`registry.addEndpoint("/ws").setAllowedOriginPatterns(...)`) and the plain REST CORS mapping (`registry.addMapping("/api/**").allowedOriginPatterns(...)`) — defaulting to `*` for frictionless local development, meant to be locked down to the real client origin(s) in production.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```
Only `health` and `info` are exposed over HTTP (not the full Actuator surface). `/actuator/health` is what the Dockerfile's `HEALTHCHECK` and docker-compose's service healthchecks poll before marking `chat-service` ready and letting `chat-client`/dependents start.

```yaml
logging:
  level:
    root: INFO
    com.medha.realtimechatservice: INFO
    org.springframework.web.socket: INFO
    org.springframework.messaging: INFO
```
Explicit `INFO` logging on the WebSocket/messaging packages, useful while learning to actually *see* STOMP CONNECT/SUBSCRIBE/SEND frames flow through the server during development.

### `src/test/resources/application.yml`

Tests run against **H2 in MySQL-compatibility mode** (`jdbc:h2:mem:chatdb-test;MODE=MySQL;DB_CLOSE_DELAY=-1`) instead of a real MySQL instance — fast, in-memory, no Docker dependency for `mvn test`. `ddl-auto: create-drop` builds a fresh schema per test run. `allowed-origin-patterns`/`allowed-origins` are both hardcoded to `"*"` since origin restrictions aren't what's under test. `logging.level.root: WARN` keeps test output quiet.

---

## 5. Project Structure Explained

| Path | Purpose |
|---|---|
| `pom.xml` | Maven build: Spring Boot 3.3.2 parent, web + websocket + data-jpa + validation + actuator starters, MySQL connector, Lombok, and a test scope with `spring-boot-starter-test` + H2 (the STOMP test client classes come transitively from `spring-boot-starter-websocket`, already compile-scoped). |
| `Dockerfile` | Multi-stage build: `maven:3.9-eclipse-temurin-21` compiles the jar, `eclipse-temurin:21-jre-alpine` runs it as a non-root `chatapp` user, with a container `HEALTHCHECK` hitting `/actuator/health`. |
| `.dockerignore` / `.gitignore` | Keep `target/`, IDE files, and the separate `react-client/` build artifacts out of the backend's Docker build context / git history where irrelevant. |
| `.env.example` | Documents every environment variable `docker-compose.yml` consumes (`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `CHAT_ALLOWED_ORIGINS`) with sane local defaults. |
| `docker-compose.yml` | Wires three services: `mysql` (with a healthcheck-gated startup), `chat-service` (the Spring Boot app, waiting on MySQL's healthcheck), `chat-client` (nginx serving the built React app, waiting on the backend's healthcheck). |
| `src/main/java/.../RealtimeChatServiceApplication.java` | `@SpringBootApplication` entry point. |
| `config/WebSocketConfig.java` | Registers the single STOMP endpoint `/ws` (with SockJS), enables the simple in-memory broker on `/topic` + `/queue`, sets `/app` as the application-destination prefix and `/user` as the user-destination prefix. |
| `config/WebConfig.java` | CORS configuration for the plain `/api/**` REST surface (separate from the WebSocket endpoint's own origin config). |
| `domain/ChatRoom.java`, `domain/ChatMessage.java`, `domain/MessageType.java` | JPA entities and the `CHAT/JOIN/LEAVE/TYPING` enum. |
| `repository/ChatRoomRepository.java`, `repository/ChatMessageRepository.java` | Spring Data JPA interfaces (`findByRoomCode`, `existsByRoomCode`, `findByRoomIdOrderBySentAtDesc`, `countByRoomId`). |
| `dto/*` | `ChatMessageDto` (shared STOMP + REST-history wire format), `ChatRoomCreateRequest`/`ChatRoomResponse` (REST), `PagedMessageResponse`, `RoomParticipantsResponse`, `ErrorResponse`. |
| `exception/*` | `RoomNotFoundException`, `DuplicateRoomException`, and `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping them (plus validation/`IllegalArgumentException`/generic errors) to a consistent `ErrorResponse` JSON body for the REST layer only. |
| `service/PresenceService.java` | In-memory `ConcurrentHashMap<String, Set<String>>` tracking who's online per room — never touches the database. |
| `service/ChatRoomService.java` | Room CRUD/lookup, plus unique room-code generation (6-char code from a 32-symbol alphabet, retried up to 20 times on collision). |
| `service/ChatMessageService.java` | Persists CHAT/JOIN/LEAVE messages; throws `IllegalArgumentException` if asked to persist a `TYPING` event; provides paginated history. |
| `controller/ChatRoomController.java` | REST: create/list/get rooms, get live participants. |
| `controller/ChatMessageController.java` | REST: paginated message history for a room. |
| `websocket/ChatWebSocketController.java` | `@MessageMapping` handlers for `chat.addUser`, `chat.sendMessage`, `chat.typing`, plus a `@MessageExceptionHandler` for STOMP errors. |
| `websocket/WebSocketEventListener.java` | Listens for `SessionDisconnectEvent` to auto-broadcast a LEAVE and clear presence when a client disconnects without saying goodbye. |
| `src/main/resources/application.yml` | Runtime configuration (see section 4). |
| `src/test/...` | Unit tests (`PresenceServiceTest`, `ChatRoomServiceTest`, `ChatMessageServiceTest`, `ChatRoomControllerTest`, `WebSocketEventListenerTest`) + a full STOMP round-trip integration test (`ChatWebSocketIntegrationTest`) + a context-load smoke test. |
| `react-client/` | Optional Vite/React demo client: `JoinForm`/`ChatWindow` components, `useChatSocket` hook wrapping `@stomp/stompjs` + `sockjs-client`, a `roomApi.js` REST wrapper, and its own `Dockerfile`/`nginx.conf` to serve the built SPA and reverse-proxy `/api` + `/ws` (with WebSocket `Upgrade`/`Connection` headers) to `chat-service`. |

---

## 6. Getting Started

### Prerequisites

- Docker + Docker Compose
- (For local, non-Docker development) JDK 21 and Maven 3.9+
- (Optional, to run the React client outside Docker) Node.js 20+

### Run everything with Docker Compose

```bash
git clone https://github.com/JNikhilSakthi/realtime-chat-service.git
cd realtime-chat-service

# optional: copy and tweak the env file (defaults work out of the box)
cp .env.example .env

docker compose up --build
```

This starts three containers:

| Service | URL | Notes |
|---|---|---|
| `mysql` | `localhost:3306` | Schema auto-created on first boot (`createDatabaseIfNotExist=true`) |
| `chat-service` | `http://localhost:8080` | Spring Boot backend: REST API + `/ws` STOMP endpoint |
| `chat-client` | `http://localhost:3000` | nginx serving the built React app, proxying `/api` and `/ws` to `chat-service` |

Open **http://localhost:3000**, create a room, pick a display name, and open the same URL in a second tab/browser to chat with yourself in real time.

### Run the backend locally without Docker

```bash
# start only MySQL via compose, or point at your own instance
docker compose up mysql -d

export DB_HOST=localhost DB_PORT=3306 DB_NAME=chatdb DB_USERNAME=chatuser DB_PASSWORD=chatpass
mvn spring-boot:run
```

### Run the React client locally without Docker

```bash
cd react-client
npm install
npm run dev   # http://localhost:5173, proxies /api and /ws to http://localhost:8080
```

---

## 7. API Documentation

Base URL: `http://localhost:8080`

### REST — Room management (`ChatRoomController`)

**Create a room**
```
POST /api/rooms
Content-Type: application/json

{ "name": "General", "description": "General discussion" }
```
```
201 Created
{
  "id": 1,
  "roomCode": "K7M2QX",
  "name": "General",
  "description": "General discussion",
  "status": "ACTIVE",
  "createdAt": "2026-07-22T10:00:00Z",
  "onlineUsers": 0
}
```

**List rooms**
```
GET /api/rooms
```
```
200 OK
[
  { "id": 1, "roomCode": "K7M2QX", "name": "General", "description": "General discussion",
    "status": "ACTIVE", "createdAt": "2026-07-22T10:00:00Z", "onlineUsers": 2 }
]
```

**Get a room**
```
GET /api/rooms/{roomCode}
```
`404 Not Found` (via `GlobalExceptionHandler`) if the room doesn't exist:
```json
{
  "timestamp": "2026-07-22T10:05:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Chat room not found: MISSING",
  "path": "/api/rooms/MISSING",
  "details": []
}
```

**Get live participants**
```
GET /api/rooms/{roomCode}/participants
```
```
200 OK
{ "roomCode": "K7M2QX", "count": 2, "usernames": ["alice", "bob"] }
```
Backed entirely by `PresenceService` (in-memory) — restarting the app clears this, by design.

### REST — Message history (`ChatMessageController`)

```
GET /api/rooms/{roomCode}/messages?page=0&size=20
```
`size` is clamped to `[1, 100]`.
```
200 OK
{
  "messages": [
    { "type": "CHAT", "sender": "alice", "content": "hello world", "sentAt": "2026-07-22T10:01:00Z" },
    { "type": "JOIN", "sender": "alice", "content": "alice joined the room", "sentAt": "2026-07-22T10:00:05Z" }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1, "last": true
}
```
Note: `TYPING` events never appear here — they are never persisted.

### WebSocket / STOMP (`ChatWebSocketController`)

Endpoint: `ws://localhost:8080/ws` (SockJS) or `ws://localhost:8080/ws/websocket` (raw WebSocket, used directly by the integration test).

| Direction | Destination | Payload (`ChatMessageDto`) | Effect |
|---|---|---|---|
| Client → Server | `/app/chat.addUser/{roomCode}` | `{ "type": "JOIN", "sender": "alice", "content": "" }` | Registers presence, persists + broadcasts a JOIN event |
| Client → Server | `/app/chat.sendMessage/{roomCode}` | `{ "type": "CHAT", "sender": "alice", "content": "hi" }` | Persists + broadcasts a CHAT event |
| Client → Server | `/app/chat.typing/{roomCode}` | `{ "type": "TYPING", "sender": "alice", "content": "" }` | Broadcasts only — never persisted |
| Server → Client | `/topic/room/{roomCode}` | `ChatMessageDto` (JOIN / CHAT / LEAVE / TYPING) | Subscribe here to receive all live events for a room |
| Server → Client (errors) | `/user/queue/errors` (or session-scoped equivalent) | plain string | Delivered via `@MessageExceptionHandler`; see below |

`ChatMessageDto` validation (`@Valid` on the `@Payload`): `type` is required; `sender` is required, max 50 chars; `content` max 2000 chars (not required — JOIN/LEAVE/TYPING events may carry empty content).

**STOMP error handling.** Because WebSocket has no HTTP status codes, `ChatWebSocketController.handleException(...)` is a `@MessageExceptionHandler` that catches exceptions thrown from any `@MessageMapping` method (e.g. `RoomNotFoundException` from `addUser`) and routes a plain-text error back to the sender only. Since this demo has no authenticated `Principal`, it falls back to the documented "session-id-as-user" convention: `messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", message, headersWithThatSessionId)`, which STOMP's user-destination resolution matches back to the one session that sent the offending frame — nobody else in the room sees the error.

---

## 8. Testing

**27/27 tests passing** (verified locally with a JDK 21 toolchain — see note below).

```bash
mvn test
```

| Test class | What it covers |
|---|---|
| `RealtimeChatServiceApplicationTests` | Full application context loads (WebSocket config, JPA, controllers, exception handling) against H2. |
| `PresenceServiceTest` | Join/leave semantics, idempotent joins, multi-user rooms, room isolation, empty-room lookups — pure unit tests, no Spring context. |
| `ChatRoomServiceTest` | Room creation + unique room-code generation (including a forced collision-then-retry case), 404 on missing room, online-user-count enrichment on `getRoom`/`listRooms` — Mockito mocks for the repository and `PresenceService`. |
| `ChatMessageServiceTest` | Persisting CHAT/JOIN messages, rejecting `TYPING` with `IllegalArgumentException`, null-content defaulting to `""`, paginated history retrieval — Mockito mocks. |
| `ChatRoomControllerTest` | `@WebMvcTest` slice: 201 on create, 400 on blank name (bean validation), 404 on missing room, participants endpoint shape — `MockMvc` + `@MockBean` services. |
| `WebSocketEventListenerTest` | Simulates a `SessionDisconnectEvent` with stashed session attributes: asserts a LEAVE broadcast + presence cleanup when a joined session drops, and asserts **no** broadcast when the session never joined a room or the user wasn't tracked as present. |
| `ChatWebSocketIntegrationTest` | **The test that matters most for this project.** `@SpringBootTest(webEnvironment = RANDOM_PORT)` boots the real embedded server, connects a real `WebSocketStompClient` over `ws://localhost:{port}/ws/websocket`, subscribes to `/topic/room/{roomCode}`, sends `chat.addUser` and `chat.sendMessage` frames, and asserts the JOIN/CHAT broadcasts actually arrive. A second test sends a `chat.typing` frame, asserts it's broadcast, then asserts `GET /api/rooms/{roomCode}/messages` comes back empty — proving TYPING truly never touches the database. |

Note from the code agent: the sandbox's default JDK (25/26) is too new for Lombok 1.18.34's annotation processor, so verification used a JDK 21 toolchain — the same version Docker always builds/runs with per the `Dockerfile`.

---

## 9. Docker

### `Dockerfile` (backend)

Two-stage build:
1. **Build stage** — `maven:3.9-eclipse-temurin-21`; copies `pom.xml` first and runs `mvn dependency:go-offline` so dependency downloads are cached in their own Docker layer before source changes invalidate anything; then copies `src/` and runs `mvn -DskipTests package`, renaming the jar to a predictable `app.jar`.
2. **Runtime stage** — `eclipse-temurin:21-jre-alpine` (small JRE-only image); creates a non-root `chatapp` user and runs the jar as that user (no root in the container); exposes `8080`; a `HEALTHCHECK` polls `/actuator/health` and requires the response to contain `"status":"UP"`.

### `docker-compose.yml`

- **`mysql`** — official `mysql:8.0` image, database/user/password from `.env` (with defaults), a named volume `chat-mysql-data` for persistence across restarts, and a `mysqladmin ping` healthcheck other services wait on.
- **`chat-service`** — built from the root `Dockerfile`; receives `DB_HOST=mysql` (the Compose service name, resolved via Docker's internal DNS) plus the other DB/CORS env vars; `depends_on: mysql: condition: service_healthy` so it never races MySQL's startup; its own healthcheck hits `/actuator/health`; `JAVA_TOOL_OPTIONS: -Xms256m -Xmx512m` caps heap for a demo-sized container.
- **`chat-client`** — built from `react-client/Dockerfile`; depends on `chat-service` being healthy; exposes nginx on host port `3000`.
- A dedicated bridge network (`chat-network`) and a named volume (`chat-mysql-data`) tie it together.

### `react-client/Dockerfile` + `nginx.conf`

Node 20 builds the Vite app (`npm run build` → static `dist/`), then an `nginx:1.27-alpine` stage serves those static files and reverse-proxies:
- `/api/` → `chat-service:8080/api/` (plain HTTP proxy)
- `/ws/` → `chat-service:8080/ws/` — critically, this location block sets `proxy_http_version 1.1` and forwards `Upgrade`/`Connection` headers via the `$connection_upgrade` map, which is what turns a plain nginx reverse proxy into a **WebSocket-capable** one. Without those two headers, the STOMP `CONNECT` handshake would fail behind the proxy even though it works fine hitting `chat-service` directly.
- `/actuator/health` → proxied through so the client container's own healthcheck (and browser devtools) can reach it.
- All other paths fall back to `index.html` for the SPA.

---

## 10. Interview Preparation

**Q: Why STOMP instead of raw WebSocket?**
Raw WebSocket gives you only a byte/text pipe — you'd have to invent your own message envelope (what's the "type" of this message? which room/topic is it for?) from scratch. STOMP is a lightweight, text-based protocol that already defines `CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, `DISCONNECT` frames with headers and destinations, which maps naturally onto pub/sub. Spring's `@EnableWebSocketMessageBroker` + `@MessageMapping` gives you Spring MVC-style routing (`@DestinationVariable`, `@Payload`, bean validation) for messages the same way `@RequestMapping` does for HTTP.

**Q: Why SockJS on top of STOMP?**
Some corporate proxies, load balancers, or older browsers block or mishandle raw WebSocket upgrade requests. SockJS negotiates the best available transport (WebSocket first, then HTTP streaming, then long-polling) transparently, so the app degrades gracefully instead of failing outright in restrictive networks.

**Q: Why a simple in-memory broker instead of RabbitMQ/ActiveMQ?**
Scope control for a *WebSocket* teaching project — plus it's genuinely enough for a single-instance app. The trade-off: the simple broker only broadcasts to subscribers connected to *that JVM*. If you scale to multiple instances behind a load balancer, a client on instance A publishing a message won't reach a subscriber connected to instance B unless you swap in `enableStompBrokerRelay(...)` against a real broker (RabbitMQ/ActiveMQ with STOMP plugin) or add a Redis pub/sub relay layer. This is the single most common "gotcha" people miss when they copy a simple-broker demo straight into production.

**Q: Why is presence in-memory instead of a database table?**
Presence answers "who is connected *right now*" — a fact scoped to a live TCP/WebSocket connection. Persisting it would require synchronizing DB rows with connection lifecycles (crash-safety, stale-row cleanup on ungraceful shutdowns, etc.) for data with zero durability value: if the server restarts, "who was online" should reset to empty, not be replayed from disk. It also avoids a DB round-trip on every join/leave, which matters when those happen far more often than durable chat sends.

**Q: Why does TYPING throw instead of just being ignored/filtered silently?**
`ChatMessageService.saveMessage()` throwing `IllegalArgumentException` on a `TYPING` event is a **fail-fast contract**, not an oversight: it makes the "TYPING is never persisted" rule impossible to violate silently if someone refactors `ChatWebSocketController` later and accidentally routes a typing event through the persistence path. The controller's own `typing()` handler never calls `saveMessage` in the first place — the exception is a safety net, not the primary enforcement mechanism.

**Q: How do you know when a user disconnects if they just close the tab (no explicit "leave")?**
Spring publishes a `SessionDisconnectEvent` for every closed STOMP session, TCP drop or clean disconnect alike. `WebSocketEventListener` listens for it, reads the `username`/`roomCode` that `ChatWebSocketController.addUser()` stashed into the session's attribute map at JOIN time, and uses that to fire the same LEAVE-broadcast + presence-cleanup path a graceful disconnect would use. This is the standard pattern for "reliable" presence in a system where clients cannot be trusted to always say goodbye.

**Q: How are STOMP errors surfaced, given there's no HTTP status code in WebSocket?**
A `@MessageExceptionHandler` method in `ChatWebSocketController` catches exceptions from any `@MessageMapping`. Since this demo has no authenticated `Principal`, it uses the well-documented workaround: treat the STOMP session ID itself as the "user" for `convertAndSendToUser(sessionId, "/queue/errors", ...)`, so the error is delivered privately back to the exact session that triggered it and not broadcast to the whole room.

**Q: Common mistakes to avoid when building this kind of system:**
- Forgetting the simple broker doesn't scale horizontally (see above).
- Persisting high-frequency ephemeral events (typing, presence, cursor positions) and drowning the database in writes that have no long-term value.
- Not validating STOMP payloads — unlike REST, there's no framework-level 400 response for a bad WebSocket frame unless you wire up `@MessageExceptionHandler` yourself.
- Trusting the client-supplied `sentAt`/timestamp instead of stamping it server-side (this project always overwrites it with `Instant.now()` on the server).
- Leaving CORS/allowed-origins wide open (`*`) in production — fine for local dev, a real security gap for a public deployment.
- Not handling `SessionDisconnectEvent` at all, leaving "ghost" users shown as online forever after a tab close.

**Q: Production considerations / performance notes:**
- **Horizontal scaling**: swap the simple broker for a real broker relay (RabbitMQ/ActiveMQ over STOMP) or a Redis-backed pub/sub bridge so broadcasts reach clients on any server instance.
- **Authentication**: this demo has no `Principal` (usernames are self-declared, unauthenticated) — a real system would authenticate the initial HTTP handshake (e.g. a JWT-validating `HandshakeInterceptor`) and use the resulting `Principal` for both authorization and the `/user/**` destination convention, instead of session-ID-as-user.
- **Backpressure / slow consumers**: a client that stops reading (weak network, tab backgrounded) can build up an unbounded outbound queue server-side; production STOMP relays let you cap per-session send buffers.
- **Message ordering & delivery guarantees**: the simple broker offers no persistence or redelivery — a broker-backed queue would be needed for "guaranteed delivery" semantics (e.g. exactly-once, offline message queuing).
- **Load balancer configuration**: sticky sessions (or a broker relay that doesn't need them) are required so a client's WebSocket upgrade and all subsequent frames land on the same backend instance when using the in-memory broker.
- **Database write volume**: CHAT/JOIN/LEAVE persistence is fine at demo scale; a high-traffic room-based chat product would eventually need write batching, partitioning `chat_message` by room/time, or an append-only log store.

---

## License

MIT — see [LICENSE](LICENSE).
