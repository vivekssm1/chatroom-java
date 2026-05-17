package com.chatroom.controller;

import com.chatroom.config.OnlineTracker;
import com.chatroom.config.OnlineTracker.UserInfo;
import com.chatroom.dto.Dtos.*;
import com.chatroom.model.Message;
import com.chatroom.model.Room;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.RoomRepository;
import com.chatroom.repository.UserRepository;
import com.chatroom.util.AvatarUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class replaces the entire Socket.io block in server.js:
 *
 *   io.on("connection", socket => {
 *     socket.on("join_room", ...)        →  @MessageMapping("/room/{code}/join")
 *     socket.on("send_message", ...)     →  @MessageMapping("/room/{code}/message")
 *     socket.on("delete_message", ...)   →  @MessageMapping("/room/{code}/delete")
 *     socket.on("send_media", ...)       →  @MessageMapping("/room/{code}/media")
 *     socket.on("toggle_reaction", ...)  →  @MessageMapping("/room/{code}/react")
 *     socket.on("typing_start", ...)     →  @MessageMapping("/room/{code}/typing")
 *     socket.on("typing_stop", ...)      →  @MessageMapping("/room/{code}/typing-stop")
 *     socket.on("disconnect", ...)       →  @EventListener(SessionDisconnectEvent)
 *   })
 *
 * Frontend uses STOMP over SockJS. Instead of:
 *   socket.emit("join_room", data)
 * Frontend does:
 *   stompClient.send("/app/room/123456/join", {}, JSON.stringify(data))
 *
 * Instead of:
 *   socket.on("new_message", handler)
 * Frontend does:
 *   stompClient.subscribe("/topic/room/123456/messages", handler)
 */
@Controller
public class ChatWebSocketController {

    @Autowired private SimpMessagingTemplate ws;
    @Autowired private MessageRepository    msgRepo;
    @Autowired private RoomRepository       roomRepo;
    @Autowired private UserRepository       userRepo;
    @Autowired private OnlineTracker        onlineTracker;
    @Autowired private AvatarUtil           avatarUtil;

    // ══ JOIN ROOM ═════════════════════════════════════════════════════════════
    /**
     * Equivalent to: socket.on("join_room", async ({ roomCode }) => { ... })
     *
     * Client sends:  stompClient.send("/app/room/ROOMCODE/join", {}, "{}")
     * Server sends back on "/topic/room/ROOMCODE/history"
     */
    @MessageMapping("/room/{roomCode}/join")
    public void joinRoom(@DestinationVariable String roomCode,
                         SimpMessageHeaderAccessor headerAccessor,
                         Principal principal) {

        String userId = principal.getName();
        Room room = roomRepo.findByRoomCode(roomCode).orElse(null);
        if (room == null) {
            ws.convertAndSendToUser(userId, "/queue/errors", Map.of("error", "Room not found"));
            return;
        }
        if (!room.getMemberIds().contains(userId)) {
            ws.convertAndSendToUser(userId, "/queue/errors", Map.of("error", "You are not a member of this room"));
            return;
        }

        // Store user in online tracker (equivalent to socket.data.roomCode = roomCode)
        String sessionId = headerAccessor.getSessionId();
        var userInfo = new UserInfo(
            sessionId, userId,
            getUserName(userId), getInitials(userId), getAvatarColor(userId),
            roomCode
        );
        onlineTracker.addUser(roomCode, sessionId, userInfo);

        // Send history to joining user (socket.emit("chat_history", history))
        var pageable = PageRequest.of(0, 200);
        List<Message> history = msgRepo.findByRoomIdOrderByTimestampAsc(roomCode, pageable);
        ws.convertAndSendToUser(userId, "/queue/history-" + roomCode, history);

        // Send pinned message if any
        if (room.getPinnedMessageId() != null) {
            msgRepo.findById(room.getPinnedMessageId()).ifPresent(pinned -> {
                if (!pinned.isDeleted()) {
                    ws.convertAndSend("/topic/room/" + roomCode + "/events",
                        Map.of("type", "pin_updated", "pinnedMessage", pinned));
                }
            });
        }

        // Broadcast updated user list (io.to(roomCode).emit("room_update", ...))
        broadcastRoomUsers(roomCode);

        // System join message
        saveAndBroadcastSystem(roomCode, getUserName(userId) + " joined the room");
    }

    // ══ SEND MESSAGE ══════════════════════════════════════════════════════════
    /**
     * Equivalent to: socket.on("send_message", async ({ roomCode, text }) => { ... })
     *
     * Client sends:  stompClient.send("/app/room/ROOMCODE/message", {}, JSON.stringify({text}))
     * Broadcasts to: /topic/room/ROOMCODE/messages
     */
    @MessageMapping("/room/{roomCode}/message")
    public void sendMessage(@DestinationVariable String roomCode,
                            @Payload WsSendMessage payload,
                            Principal principal) {

        String userId = principal.getName();
        if (payload.getText() == null || payload.getText().isBlank()) return;

        Message msg = new Message();
        msg.setRoomId(roomCode);
        msg.setType("user");
        msg.setSenderId(userId);
        msg.setUsername(getUserName(userId));
        msg.setInitials(getInitials(userId));
        msg.setAvatarColor(getAvatarColor(userId));
        msg.setText(payload.getText().trim());
        msg.setTimestamp(LocalDateTime.now());

        Message saved = msgRepo.save(msg);
        // io.to(roomCode).emit("new_message", msg)
        ws.convertAndSend("/topic/room/" + roomCode + "/messages", saved);
    }

    // ══ DELETE MESSAGE ════════════════════════════════════════════════════════
    /**
     * Equivalent to: socket.on("delete_message", async ({ messageId, roomCode }) => { ... })
     */
    @MessageMapping("/room/{roomCode}/delete")
    public void deleteMessage(@DestinationVariable String roomCode,
                              @Payload WsDeleteMessage payload,
                              Principal principal) {

        String userId = principal.getName();
        msgRepo.findById(payload.getMessageId()).ifPresent(msg -> {
            if (!msg.getSenderId().equals(userId)) return;

            long ageMs = java.time.Duration.between(msg.getTimestamp(), LocalDateTime.now()).toMillis();
            if (ageMs > 5 * 60 * 1000) return; // 5 minute window

            msgRepo.delete(msg);

            // Unpin if this was pinned
            roomRepo.findByRoomCode(roomCode).ifPresent(room -> {
                if (payload.getMessageId().equals(room.getPinnedMessageId())) {
                    room.setPinnedMessageId(null);
                    roomRepo.save(room);
                    ws.convertAndSend("/topic/room/" + roomCode + "/events",
                        Map.of("type", "pin_updated", "pinnedMessage", Map.of()));
                }
            });

            // io.to(roomCode).emit("message_deleted", { messageId })
            ws.convertAndSend("/topic/room/" + roomCode + "/events",
                Map.of("type", "message_deleted", "messageId", payload.getMessageId()));
        });
    }

    // ══ SEND MEDIA ════════════════════════════════════════════════════════════
    /**
     * Equivalent to: socket.on("send_media", async ({ roomCode, mediaData, ... }) => { ... })
     */
    @MessageMapping("/room/{roomCode}/media")
    public void sendMedia(@DestinationVariable String roomCode,
                          @Payload WsSendMedia payload,
                          Principal principal) {

        String userId = principal.getName();
        if (payload.getMediaData() == null || payload.getMediaMime() == null) return;

        // Size guard — 8MB base64 ≈ 11MB string
        if (payload.getMediaData().length() > 11_000_000) {
            ws.convertAndSendToUser(userId, "/queue/errors", Map.of("error", "File too large. Max 8MB."));
            return;
        }

        Message msg = new Message();
        msg.setRoomId(roomCode);
        msg.setType("user");
        msg.setSenderId(userId);
        msg.setUsername(getUserName(userId));
        msg.setInitials(getInitials(userId));
        msg.setAvatarColor(getAvatarColor(userId));
        msg.setText(payload.getCaption() != null && !payload.getCaption().isBlank()
            ? payload.getCaption()
            : ("video".equals(payload.getMediaType()) ? "📹 Video" : "📷 Photo"));
        msg.setMediaData(payload.getMediaData());
        msg.setMediaMime(payload.getMediaMime());
        msg.setMediaType(payload.getMediaType() != null ? payload.getMediaType() : "image");
        msg.setMediaMode(payload.getMediaMode() != null ? payload.getMediaMode() : "permanent");
        msg.setTimestamp(LocalDateTime.now());

        Message saved = msgRepo.save(msg);

        // Broadcast WITHOUT base64 data (too heavy — same as Node)
        Map<String, Object> light = new LinkedHashMap<>();
        light.put("_id",         saved.getId());
        light.put("roomId",      saved.getRoomId());
        light.put("type",        saved.getType());
        light.put("senderId",    saved.getSenderId());
        light.put("username",    saved.getUsername());
        light.put("initials",    saved.getInitials());
        light.put("avatarColor", saved.getAvatarColor());
        light.put("text",        saved.getText());
        light.put("hasMedia",    true);
        light.put("mediaType",   saved.getMediaType());
        light.put("mediaMode",   saved.getMediaMode());
        light.put("timestamp",   saved.getTimestamp().toString());

        ws.convertAndSend("/topic/room/" + roomCode + "/messages", light);
    }

    // ══ TOGGLE REACTION ═══════════════════════════════════════════════════════
    /**
     * Equivalent to: socket.on("toggle_reaction", async ({ messageId, roomCode, emoji }) => { ... })
     */
    @MessageMapping("/room/{roomCode}/react")
    public void toggleReaction(@DestinationVariable String roomCode,
                               @Payload WsReaction payload,
                               Principal principal) {

        String userId = principal.getName();
        msgRepo.findById(payload.getMessageId()).ifPresent(msg -> {
            if ("system".equals(msg.getType())) return;

            String username = getUserName(userId);
            // Find existing reaction by same user + same emoji
            var existing = msg.getReactions().stream()
                .filter(r -> r.getUserId().equals(userId) && r.getEmoji().equals(payload.getEmoji()))
                .findFirst();

            if (existing.isPresent()) {
                msg.getReactions().remove(existing.get()); // remove (toggle off)
            } else {
                msg.getReactions().add(new Message.Reaction(payload.getEmoji(), userId, username)); // add
            }

            msgRepo.save(msg);

            // io.to(roomCode).emit("reaction_updated", { messageId, reactions })
            ws.convertAndSend("/topic/room/" + roomCode + "/events", Map.of(
                "type",      "reaction_updated",
                "messageId", msg.getId(),
                "reactions", msg.getReactions()
            ));
        });
    }

    // ══ TYPING ════════════════════════════════════════════════════════════════
    /**
     * Equivalent to:
     *   socket.on("typing_start", ...) → socket.to(roomCode).emit("user_typing", ...)
     *   socket.on("typing_stop",  ...) → socket.to(roomCode).emit("user_stop_typing", ...)
     */
    @MessageMapping("/room/{roomCode}/typing")
    public void typingStart(@DestinationVariable String roomCode, Principal principal) {
        ws.convertAndSend("/topic/room/" + roomCode + "/typing",
            Map.of("username", getUserName(principal.getName()), "typing", true));
    }

    @MessageMapping("/room/{roomCode}/typing-stop")
    public void typingStop(@DestinationVariable String roomCode, Principal principal) {
        ws.convertAndSend("/topic/room/" + roomCode + "/typing",
            Map.of("username", getUserName(principal.getName()), "typing", false));
    }

    // ══ DISCONNECT ════════════════════════════════════════════════════════════
    /**
     * Equivalent to: socket.on("disconnect", async () => { ... })
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        if (sessionId == null) return;

        // Get room from tracker before removing
        onlineTracker.getOnlineUsers("").stream()
            .filter(u -> u.sessionId().equals(sessionId))
            .findFirst()
            .ifPresent(u -> {
                String roomCode = u.roomCode();
                String name     = u.name();
                onlineTracker.removeUser(sessionId);
                broadcastRoomUsers(roomCode);
                saveAndBroadcastSystem(roomCode, name + " left the room");
            });

        // Also just remove by sessionId in case above didn't match
        onlineTracker.removeUser(sessionId);
    }

    // ══ HELPERS ═══════════════════════════════════════════════════════════════

    private void broadcastRoomUsers(String roomCode) {
        List<UserInfo> users = onlineTracker.getOnlineUsers(roomCode);

        List<Map<String, String>> userList = users.stream()
            .map(u -> Map.of(
                "name", u.name(),
                "initials", u.initials(),
                "avatarColor", u.avatarColor()
            )).collect(Collectors.toList());

        // io.to(roomCode).emit("room_update", { userCount, users })
        ws.convertAndSend("/topic/room/" + roomCode + "/presence",
            Map.of("userCount", users.size(), "users", userList));
    }

    private void saveAndBroadcastSystem(String roomCode, String text) {
        Message sys = new Message();
        sys.setRoomId(roomCode);
        sys.setType("system");
        sys.setText(text);
        sys.setTimestamp(LocalDateTime.now());
        Message saved = msgRepo.save(sys);
        ws.convertAndSend("/topic/room/" + roomCode + "/messages", saved);
    }

    private String getUserName(String userId) {
        return userRepo.findById(userId).map(u -> u.getName()).orElse("Unknown");
    }

    private String getInitials(String userId) {
        return userRepo.findById(userId)
            .map(u -> avatarUtil.getInitials(u.getName())).orElse("?");
    }

    private String getAvatarColor(String userId) {
        return avatarUtil.getAvatarColor(userId);
    }
}
