# 💬 ChatRoom — Java Edition

A full Java rewrite of the Node.js ChatRoom app using **Spring Boot + MongoDB**.

## Technology Mapping

| Node.js Stack | Java Stack |
|---|---|
| Express.js | Spring Boot Web (REST) |
| Socket.io | Spring WebSocket + STOMP over SockJS |
| Mongoose + MongoDB | Spring Data MongoDB |
| bcryptjs | Spring Security BCrypt |
| jsonwebtoken | jjwt 0.12.x |
| cookie-parser | Spring built-in cookie handling |
| nodemon | Spring Boot DevTools |

---

## Project Structure

```
src/main/java/com/chatroom/
├── ChatroomApplication.java       ← entry point (replaces server.js)
├── config/
│   ├── SecurityConfig.java        ← JWT filter + BCrypt bean
│   ├── WebSocketConfig.java       ← STOMP/SockJS setup (replaces Socket.io)
│   └── OnlineTracker.java         ← in-memory online users (replaces onlineRooms{})
├── controller/
│   ├── AuthController.java        ← /api/register, /api/login, /api/me, /api/logout
│   ├── RoomController.java        ← /api/rooms/*, /api/messages/:id/view
│   ├── AdminController.java       ← /api/admin/*
│   ├── ChatWebSocketController.java ← ALL socket.on() handlers
│   └── GlobalExceptionHandler.java  ← central error handling
├── dto/
│   └── Dtos.java                  ← request/response shapes (type-safe)
├── model/
│   ├── User.java                  ← userSchema
│   ├── Room.java                  ← roomSchema
│   ├── Message.java               ← messageSchema
│   └── Activity.java              ← activitySchema
├── repository/
│   ├── UserRepository.java        ← User.findOne(), User.find(), etc.
│   ├── RoomRepository.java
│   ├── MessageRepository.java
│   └── ActivityRepository.java
├── service/
│   ├── AuthService.java           ← register/login business logic
│   ├── RoomService.java           ← room CRUD business logic
│   └── AdminService.java          ← admin panel business logic
└── util/
    ├── JwtUtil.java               ← signToken() / verifyToken()
    └── AvatarUtil.java            ← getInitials() / getAvatarColor()

src/main/resources/
├── application.properties         ← .env equivalent
└── static/
    ├── index.html                 ← SAME as original (with script swap below)
    ├── admin.html                 ← SAME as original
    └── websocket-migration.js     ← reference for socket.io → STOMP changes
```

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- MongoDB running locally (or set `MONGODB_URI` env variable)

### Run locally

```bash
# Clone / unzip the project
cd chatroom-java

# Set environment variables (or edit application.properties)
export MONGODB_URI=mongodb://localhost:27017/chatroom
export JWT_SECRET=your_secret_key_at_least_32_chars
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=admin123

# Run with Maven (like npm run dev)
mvn spring-boot:run

# OR build a JAR and run (like npm start)
mvn package
java -jar target/chatroom-1.0.0.jar
```

Open: http://localhost:3000
Admin: http://localhost:3000/admin

---

## Frontend Changes Required

In `index.html`, make **two changes**:

### 1. Replace the Socket.io script tag
```html
<!-- REMOVE this: -->
<script src="https://cdn.socket.io/4.7.2/socket.io.min.js"></script>

<!-- ADD these two: -->
<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
```

### 2. Replace `connectSocket()` and socket event handlers
Copy the code from `websocket-migration.js` into `index.html`, replacing:
- `connectSocket()` function
- `enterRoom()` function
- `sendMessage()` function
- `toggleReaction()` function
- `confirmSendMedia()` function
- `doLogout()` function

The REST API calls (`fetch("/api/...")`) are **unchanged** — Spring Boot uses the same URL patterns.

---

## Deploy to Render

### Step 1 — Push to GitHub
```bash
git init && git add . && git commit -m "initial commit"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

### Step 2 — Create Render Web Service

| Setting | Value |
|---|---|
| Environment | Java |
| Build Command | `mvn package -DskipTests` |
| Start Command | `java -jar target/chatroom-1.0.0.jar` |
| Instance Type | Free |

### Step 3 — Set Environment Variables in Render
```
MONGODB_URI      = mongodb+srv://...
JWT_SECRET       = your_long_random_secret
ADMIN_USERNAME   = admin
ADMIN_PASSWORD   = your_admin_password
```

---

## Key Concept Mapping for Your Assignment

| Node.js concept | Java / Spring equivalent |
|---|---|
| `async/await` | Blocking calls (repositories return directly) |
| `req.body` | `@RequestBody` annotated parameter |
| `req.params.id` | `@PathVariable String id` |
| `req.query.search` | `@RequestParam String search` |
| `res.json({...})` | `ResponseEntity.ok(Map.of(...))` |
| `res.status(404).json(...)` | `ResponseEntity.status(404).body(...)` |
| `res.cookie(name, value)` | `new Cookie(name, value); res.addCookie(c)` |
| `authMiddleware` | `SecurityConfig` JWT filter + `Authentication auth` param |
| `io.to(room).emit(event, data)` | `ws.convertAndSend("/topic/room/" + code, data)` |
| `socket.emit(event, data)` (to one user) | `ws.convertAndSendToUser(userId, "/queue/...", data)` |
| `socket.on("event", handler)` | `@MessageMapping("/room/{code}/event")` method |
| `mongoose.Schema` | `@Document` class with `@Id`, `@Indexed` |
| `Model.findById(id)` | `repository.findById(id)` → returns `Optional<T>` |
| `Model.create(data)` | `repository.save(new Entity(...))` |
| `Model.deleteMany({roomId})` | `repository.deleteAllByRoomId(roomId)` |
| `bcrypt.hash(pwd, 10)` | `encoder.encode(pwd)` (BCryptPasswordEncoder) |
| `bcrypt.compare(plain, hash)` | `encoder.matches(plain, hash)` |
| `jwt.sign({id}, secret)` | `jwtUtil.generateUserToken(userId)` |
| `jwt.verify(token, secret)` | `jwtUtil.verifyToken(token)` |
| `try { } catch { res.status(500) }` | `@RestControllerAdvice` GlobalExceptionHandler |
